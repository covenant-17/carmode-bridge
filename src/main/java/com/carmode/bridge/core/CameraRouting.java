package com.carmode.bridge.core;

/** Applies only to launches initiated by CarMode, never to native reverse-camera activation. */
public final class CameraRouting {
    private CameraRouting() {}
    public static boolean isTarget(String pkg, String selected) {
        if (pkg == null || pkg.isEmpty()) return false;
        String configured = ActionDispatcher.target(ActionDispatcher.Action.CAMERAS, selected);
        int slash = configured.indexOf('/');
        if (slash >= 0) configured = configured.substring(0, slash);
        return pkg.equals(configured);
    }
    public static boolean window(boolean home, boolean window, boolean recovery, String pkg, String selected) {
        return home && window && !recovery && isTarget(pkg, selected);
    }
}
