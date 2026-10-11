package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

class BootManifestTest {
    private Document manifest() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new File("src/main/AndroidManifest.xml"));
    }
    private Element component(Document doc, String tag, String name) {
        NodeList list = doc.getElementsByTagName(tag);
        for (int i = 0; i < list.getLength(); i++) {
            Element element = (Element) list.item(i);
            if (name.equals(element.getAttribute("android:name"))) return element;
        }
        throw new AssertionError("Missing component: " + name);
    }
    @Test void receivesBothBootStages() throws Exception {
        Element receiver = component(manifest(), "receiver", ".BootReceiver");
        assertEquals("true", receiver.getAttribute("android:directBootAware"));
        java.util.Set<String> actions = new java.util.HashSet<>();
        NodeList list = receiver.getElementsByTagName("action");
        for (int i = 0; i < list.getLength(); i++) actions.add(((Element) list.item(i)).getAttribute("android:name"));
        assertTrue(actions.contains("android.intent.action.LOCKED_BOOT_COMPLETED"));
        assertTrue(actions.contains("android.intent.action.BOOT_COMPLETED"));
        assertTrue(actions.contains("android.intent.action.MY_PACKAGE_REPLACED"));
    }
    @Test void backgroundServiceIsAwareAndIndependent() throws Exception {
        Element service = component(manifest(), "service", ".ModeRuntimeService");
        assertEquals("true", service.getAttribute("android:directBootAware"));
        assertEquals("false", service.getAttribute("android:stopWithTask"));
        assertEquals("false", service.getAttribute("android:exported"));
    }
    @Test void doesNotDowngradeSdkOrRequestSecureSettings() throws Exception {
        Document doc = manifest();
        assertEquals("29", ((Element) doc.getElementsByTagName("uses-sdk").item(0)).getAttribute("android:targetSdkVersion"));
        NodeList permissions = doc.getElementsByTagName("uses-permission");
        for (int i = 0; i < permissions.getLength(); i++)
            assertNotEquals("android.permission.WRITE_SECURE_SETTINGS", ((Element) permissions.item(i)).getAttribute("android:name"));
    }
    @Test void homeProxyIsSeparateFromNormalAppEntry() throws Exception {
        Element activity = component(manifest(), "activity", ".HomeProxyActivity");
        assertEquals("true", activity.getAttribute("android:exported"));
        assertEquals("@style/HomeProxyTheme", activity.getAttribute("android:theme"));
        assertEquals("singleTask", activity.getAttribute("android:launchMode"));
        assertEquals("com.carmode.bridge.home", activity.getAttribute("android:taskAffinity"));
        java.util.Set<String> categories = new java.util.HashSet<>();
        NodeList list = activity.getElementsByTagName("category");
        for (int i = 0; i < list.getLength(); i++) categories.add(((Element) list.item(i)).getAttribute("android:name"));
        assertTrue(categories.contains("android.intent.category.HOME"));
        assertTrue(categories.contains("android.intent.category.DEFAULT"));
        assertFalse(categories.contains("android.intent.category.LAUNCHER"));
        Element normal = component(manifest(), "activity", ".LauncherActivity");
        assertEquals("android.intent.category.LAUNCHER", ((Element) normal.getElementsByTagName("category").item(0)).getAttribute("android:name"));
    }
    @Test void retainedHomeDoesNotExposePreviousAppOrSplash() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document styles = factory.newDocumentBuilder().parse(new File("src/main/res/values/styles.xml"));
        java.util.Map<String, String> items = new java.util.HashMap<>();
        NodeList list = ((Element) styles.getElementsByTagName("style").item(0)).getElementsByTagName("item");
        for (int i = 0; i < list.getLength(); i++) {
            Element item = (Element) list.item(i); items.put(item.getAttribute("name"), item.getTextContent());
        }
        assertEquals("false", items.get("android:windowIsTranslucent"));
        assertEquals("#0F1723", items.get("android:windowBackground"));
        assertEquals("true", items.get("android:windowDisablePreview"));
        assertEquals("false", items.get("android:backgroundDimEnabled"));
        assertEquals("@style/HomeProxyNoAnimation", items.get("android:windowAnimationStyle"));
    }
    @Test void allAppsHasSeparateDefaultFilterOnProxyOnly() throws Exception {
        Element activity = component(manifest(), "activity", ".HomeProxyActivity");
        boolean found = false;
        NodeList filters = activity.getElementsByTagName("intent-filter");
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            NodeList actions = filter.getElementsByTagName("action");
            if (actions.getLength() == 1 && HomeNavigation.ALL_APPS.equals(((Element) actions.item(0)).getAttribute("android:name"))) {
                found = true;
                assertEquals(1, filter.getElementsByTagName("category").getLength());
                assertEquals("android.intent.category.DEFAULT", ((Element) filter.getElementsByTagName("category").item(0)).getAttribute("android:name"));
            }
        }
        assertTrue(found);
        assertEquals(1, component(manifest(), "activity", ".LauncherActivity").getElementsByTagName("action").getLength());
    }
}
