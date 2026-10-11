package com.carmode.bridge.core;

/** Parse Android's colon-separated enabled-component setting, including shortened class names. */
public final class AccessibilitySetting {
    private AccessibilitySetting() {}
    public static boolean contains(String setting, String pkg, String cls) {
        if (setting == null) return false;
        for (String entry : setting.split(":")) {
            String[] parts = entry.trim().split("/", -1);
            if (parts.length != 2 || !pkg.equals(parts[0])) continue;
            String expanded = parts[1].startsWith(".") ? parts[0] + parts[1] : parts[1];
            if (cls.equals(expanded)) return true;
        }
        return false;
    }
    public static String status(boolean enabled, boolean connected) {
        return enabled ? connected ? "enabled_connected" : "enabled_disconnected"
                : connected ? "disabled_connection_pending" : "disabled";
    }
}
