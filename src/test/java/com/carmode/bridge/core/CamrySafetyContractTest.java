package com.carmode.bridge.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.*;
import static org.junit.jupiter.api.Assertions.*;

/** Integration boundaries that JVM policy tests cannot exercise through Android framework stubs. */
class CamrySafetyContractTest {
    private String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/com/carmode/bridge/" + name + ".java")), StandardCharsets.UTF_8);
    }
    private Document xml(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(path);
    }
    @Test void onlyOriginalProxyHandlesHomeAndInternalDockEntryIsPrivate() throws Exception {
        Document doc = xml("src/main/AndroidManifest.xml"); int homeCount = 0; boolean preview = false, dock = false;
        NodeList activities = doc.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            NodeList categories = activity.getElementsByTagName("category");
            for (int j = 0; j < categories.getLength(); j++) {
                if ("android.intent.category.HOME".equals(((Element) categories.item(j)).getAttribute("android:name"))) {
                    homeCount++; assertEquals(".HomeProxyActivity", activity.getAttribute("android:name"));
                }
            }
            if (".CamryPreviewActivity".equals(activity.getAttribute("android:name"))) {
                preview = true; assertEquals("false", activity.getAttribute("android:exported"));
            }
        }
        assertEquals(1, homeCount); assertTrue(preview);
        NodeList services = doc.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            if (".DockService".equals(service.getAttribute("android:name"))) {
                dock = true; assertEquals("false", service.getAttribute("android:exported"));
            }
        }
        assertTrue(dock);
    }
    @Test void ownWindowsNeverHideSystemNavigationOrEnterKiosk() throws Exception {
        for (String name : new String[]{"CamryUi", "CamryActivity", "CamryHome", "CamryControl", "CamryAppWindow", "CamryNavigation", "CamryAppsActivity", "DockController", "DockService"}) {
            String text = source(name);
            for (String forbidden : new String[]{"SYSTEM_UI_FLAG_HIDE_NAVIGATION", "SYSTEM_UI_FLAG_IMMERSIVE", "startLockTask(",
                    "KEYCODE_HOME", "KEYCODE_APP_SWITCH", "FLAG_LAYOUT_NO_LIMITS", "performGlobalAction("})
                assertFalse(text.contains(forbidden), name + ": " + forbidden);
        }
        assertTrue(source("CamryUi").contains("SYSTEM_UI_FLAG_VISIBLE"));
        assertTrue(source("DockController").contains("FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL"));
        assertTrue(source("CamryActivity").contains("getRepeatCount() == 0"));
    }
    @Test void appsUsesAPrivateOrdinaryTaskRatherThanTheHomeTask() throws Exception {
        NodeList activities = xml("src/main/AndroidManifest.xml").getElementsByTagName("activity");
        Element apps = null;
        for (int i = 0; i < activities.getLength(); i++) {
            Element candidate = (Element) activities.item(i);
            if (".CamryAppsActivity".equals(candidate.getAttribute("android:name"))) apps = candidate;
        }
        assertNotNull(apps);
        assertEquals("false", apps.getAttribute("android:exported"));
        assertEquals("com.carmode.bridge.apps", apps.getAttribute("android:taskAffinity"));
        assertEquals("false", apps.getAttribute("android:resizeableActivity"));
        assertEquals(0, apps.getElementsByTagName("intent-filter").getLength());
    }
    @Test void dockHasNoBootReceiverAndFollowsExplicitTransitions() throws Exception {
        assertFalse(source("BootReceiver").contains("DockService"));
        assertFalse(source("CamryHome").contains("startForegroundService"));
        assertTrue(source("DockService").contains("START_NOT_STICKY"));
        assertTrue(source("DockService").contains("DockPolicy.allowed"));
        String actions = source("Actions");
        assertTrue(actions.indexOf("DockService.hide(context)") < actions.indexOf("AppResolver.resolve"));
        assertTrue(actions.indexOf("DockService.transition(context)") > actions.indexOf("else context.startActivity(intent)"));
    }
    @Test void camryLabelsHaveBothLanguagesAndNoDuplicateResourceNames() throws Exception {
        Set<String> en = names("src/main/res/values/strings.xml"), ru = names("src/main/res/values-ru/strings.xml");
        en.removeIf(name -> !name.startsWith("camry_")); ru.removeIf(name -> !name.startsWith("camry_"));
        assertFalse(en.isEmpty()); assertEquals(en, ru);
    }
    private Set<String> names(String file) throws Exception {
        Set<String> names = new HashSet<>(); NodeList strings = xml(file).getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) assertTrue(names.add(((Element) strings.item(i)).getAttribute("name")), file);
        return names;
    }
}
