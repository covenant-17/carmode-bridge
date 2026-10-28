package com.carmode.bridge.core;

/** Only explicit external destinations initiated by CarMode can enter its Home content area. */
public final class AppWindowRouting {
    private AppWindowRouting() {}
    public static boolean allowed(boolean home, boolean window, boolean all, boolean recovery, String target, String own) {
        return home && window && all && !recovery && target != null && !target.isEmpty() && !target.equals(own);
    }
}
