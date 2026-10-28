package com.carmode.bridge.core;

public final class ZlinkRouting {
    private ZlinkRouting() {}
    public static boolean isTarget(String packageName, String selected) {
        if (packageName == null) return false;
        String configured = selected == null ? "" : selected.trim();
        int slash = configured.indexOf('/');
        if (slash >= 0) configured = configured.substring(0, slash);
        return "com.zjinnova.zlink".equals(packageName) || (!configured.isEmpty() && configured.equals(packageName));
    }
    public static boolean homeOnly(boolean home, boolean window, boolean only, boolean recovery, String target, String selected) {
        return home && window && only && !recovery && isTarget(target, selected);
    }
}
