package com.carmode.bridge;

import android.content.SharedPreferences;
import com.carmode.bridge.core.ActionDispatcher.Action;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the real Prefs adapter without invoking Android's Context/framework stubs. */
class CamryPreferencesTest {
    @Test void initialFiveCellsKeepTheirPositionsUntilAllAreFilled() {
        Memory disk = new Memory(); Prefs prefs = new Prefs(disk.data);
        assertArrayEquals(new String[]{"", "", "", "", ""}, prefs.pinnedApps());
        prefs.pinApp(4, "last/.Main"); prefs.pinApp(1, "second/.Main");
        prefs.pinApp(1, ""); prefs = disk.restart();
        assertArrayEquals(new String[]{"", "", "", "", "last/.Main"}, prefs.pinnedApps());
        for (int i = 0; i < 4; i++) prefs.pinApp(i, "app" + i + "/.Main");
        assertArrayEquals(new String[]{"app0/.Main", "app1/.Main", "app2/.Main", "app3/.Main", "last/.Main", ""}, prefs.pinnedApps());
    }
    @Test void expandedListCompactsAfterDeletionButNeverBelowFiveCells() {
        Memory disk = new Memory(); Prefs prefs = new Prefs(disk.data);
        for (int i = 0; i < 7; i++) prefs.pinApp(i, "app" + i + "/.Main");
        assertEquals(8, prefs.pinnedApps().length);
        prefs.pinApp(1, ""); prefs = disk.restart();
        assertArrayEquals(new String[]{"app0/.Main", "app2/.Main", "app3/.Main", "app4/.Main", "app5/.Main", "app6/.Main", ""}, prefs.pinnedApps());
        prefs.pinApp(0, ""); prefs.pinApp(4, ""); prefs = disk.restart();
        assertArrayEquals(new String[]{"app2/.Main", "app3/.Main", "app4/.Main", "app5/.Main", ""}, prefs.pinnedApps());
        for (int i = 0; i < 5; i++) prefs.pinApp(i, "");
        assertArrayEquals(new String[]{"", "", "", "", ""}, disk.restart().pinnedApps());
    }
    @Test void legacyExpandedHolesCompactAndObsoleteKeysCannotReturn() {
        Memory disk = new Memory(); disk.values.put("camry_pin_count", 9);
        disk.values.put("camry_pin_0", "one/.Main"); disk.values.put("camry_pin_4", "two/.Main");
        disk.values.put("camry_pin_8", "three/.Main");
        Prefs prefs = new Prefs(disk.data);
        assertArrayEquals(new String[]{"one/.Main", "two/.Main", "three/.Main", "", ""}, prefs.pinnedApps());
        prefs.pinApp(1, ""); prefs = disk.restart();
        assertArrayEquals(new String[]{"one/.Main", "three/.Main", "", "", ""}, prefs.pinnedApps());
        assertFalse(disk.values.containsKey("camry_pin_8"));
        assertEquals("", disk.values.get("camry_pin_4"));
    }
    @Test void expandedReorderingAndChoosingExistingAppKeepAnEmptyTail() {
        Prefs prefs = new Prefs(new Memory().data);
        for (int i = 0; i < 6; i++) prefs.pinApp(i, "app" + i + "/.Main");
        prefs.movePin(0, 1); prefs.movePin(0, 6);
        assertArrayEquals(new String[]{"app0/.Main", "app2/.Main", "app3/.Main", "app4/.Main", "app5/.Main", "app1/.Main", ""}, prefs.pinnedApps());
        prefs.pinApp(6, "app0/.Main");
        assertArrayEquals(new String[]{"app2/.Main", "app3/.Main", "app4/.Main", "app5/.Main", "app1/.Main", "app0/.Main", ""}, prefs.pinnedApps());
    }
    @Test void expandedAssignmentsSurviveRollbackAndInvalidIndices() {
        Memory disk = new Memory(); Prefs prefs = new Prefs(disk.data);
        for (int i = 0; i < 12; i++) prefs.pinApp(i, "app" + i + "/.Main");
        String[] before = prefs.pinnedApps(); prefs.movePin(-1, 2); prefs.pinApp(99, "invalid/.Main");
        assertTrue(prefs.restoreCamry());
        assertArrayEquals(before, disk.restart().pinnedApps());
    }
    private static final class Memory {
        final Map<String, Object> values = new HashMap<>();
        Map<String, Object> persisted = new HashMap<>();
        boolean writable = true;
        final SharedPreferences data = (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (method.getName().equals("edit")) return editor();
                    if (method.getName().equals("getAll")) return new HashMap<>(values);
                    if (method.getName().equals("contains")) return values.containsKey(args[0]);
                    if (method.getName().startsWith("get")) return values.getOrDefault(args[0], args[1]);
                    return null;
                });
        private SharedPreferences.Editor editor() {
            Map<String, Object> edits = new HashMap<>();
            return (SharedPreferences.Editor) Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.startsWith("put")) { edits.put((String) args[0], args[1]); return proxy; }
                        if (name.equals("remove")) { edits.put((String) args[0], null); return proxy; }
                        if (name.equals("commit") || name.equals("apply")) {
                            edits.forEach((key, value) -> { if (value == null) values.remove(key); else values.put(key, value); });
                            if (writable) persisted = new HashMap<>(values);
                            return name.equals("commit") ? writable : null;
                        }
                        throw new UnsupportedOperationException(name);
                    });
        }
        Prefs restart() { values.clear(); values.putAll(persisted); return new Prefs(data); }
    }
    @Test void updateDefaultsPreserveOemAndDoNotStartNewFeatures() {
        Prefs prefs = new Prefs(new Memory().data);
        assertFalse(prefs.camryHome()); assertFalse(prefs.dockEnabled()); assertFalse(prefs.zlinkAuto()); assertFalse(prefs.zlinkWindow());
        assertEquals("com.zjinnova.zlink", prefs.zlinkApp());
        assertEquals("bottom_right", prefs.dockPosition());
        assertFalse(prefs.zlinkHomeOnly());
    }
    @Test void rollbackPersistsAcrossRestartAndPreservesModeAssignmentsAndRuntime() {
        Memory disk = new Memory(); Prefs prefs = new Prefs(disk.data);
        prefs.set("enabled", true); prefs.set("hcn_mode", true); prefs.set("auto_start", false);
        prefs.component(Action.CAMERAS, "camera/.Camera"); prefs.language("ru");
        prefs.set("camry_home", true); prefs.set("camry_dock", true); prefs.set("camry_zlink_auto", true);
        prefs.set("camry_zlink_window", true);
        prefs.set("camry_zlink_home_only", true);
        prefs.camryString("camry_zlink_app", "alternate/.Auto");
        assertTrue(prefs.restoreCamry());
        prefs = disk.restart();
        assertFalse(prefs.camryHome()); assertFalse(prefs.zlinkAuto()); assertFalse(prefs.dockEnabled());
        assertFalse(prefs.zlinkWindow());
        assertFalse(prefs.zlinkHomeOnly());
        assertTrue(prefs.enabled()); assertTrue(prefs.hcnMode()); assertFalse(prefs.autoStart());
        assertEquals("camera/.Camera", prefs.component(Action.CAMERAS)); assertEquals("ru", prefs.language());
        assertEquals("alternate/.Auto", prefs.zlinkApp());
    }
    @Test void failedDurableRollbackIsReported() {
        Memory disk = new Memory(); Prefs prefs = new Prefs(disk.data);
        prefs.set("camry_home", true); disk.writable = false;
        assertFalse(prefs.restoreCamry());
        assertFalse(prefs.camryHome()); // Android updates memory even on a failed commit.
        assertTrue(disk.restart().camryHome()); // Caller must not claim persistence succeeded.
    }
    @Test void opacityStaysReadableEvenWithInvalidStoredValues() {
        Memory disk = new Memory(); Prefs prefs = new Prefs(disk.data);
        prefs.dockOpacity(-100); assertEquals(40, prefs.dockOpacity());
        prefs.dockOpacity(1000); assertEquals(100, prefs.dockOpacity());
    }
    @Test void oldSidePositionsFallBackAndAllCollapsedCornersPersist() {
        Memory disk = new Memory(); Prefs prefs = new Prefs(disk.data);
        for (String legacy : new String[]{"bottom", "left", "right", "invalid"}) {
            prefs.camryString("camry_dock_position", legacy);
            assertEquals("bottom_right", prefs.dockPosition());
        }
        for (String corner : new String[]{"top_left", "top_right", "bottom_left", "bottom_right"}) {
            prefs.camryString("camry_dock_position", corner);
            assertEquals(corner, disk.restart().dockPosition());
        }
    }
}
