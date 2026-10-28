package com.carmode.bridge.core;

/** Automatic sessions follow Camry mode; manual sessions follow their separate preference. */
public final class DockPolicy {
    private DockPolicy() {}
    public static boolean allowed(boolean automatic, boolean camry, boolean manual, boolean recovering, boolean overlay, boolean sidebarVisible) {
        return !sidebarVisible && allowed(automatic, camry, manual, recovering, overlay);
    }
    public static boolean allowed(boolean automatic, boolean camry, boolean manual, boolean recovering, boolean overlay) {
        return !recovering && overlay && (automatic ? camry : manual);
    }
}
