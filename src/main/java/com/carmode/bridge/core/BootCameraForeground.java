package com.carmode.bridge.core;

/** Lifecycle evidence with Activity identity; package-level pauses can belong to an older screen. */
public final class BootCameraForeground {
    public String pkg, activity;
    public long latest;
    public int resumes, pauses;
    public void resumed(String name, String screen, long time) {
        resumes++;
        if (time < latest) return;
        latest = time; pkg = name; activity = screen;
    }
    public void paused(String name, String screen, long time) {
        pauses++;
        if (time < latest) return;
        latest = time;
        if (java.util.Objects.equals(pkg, name) && java.util.Objects.equals(activity, screen)) {
            pkg = null; activity = null;
        }
    }
    /** Aggregates are secondary evidence only: a unique, very recent camera use newer than lifecycle evidence. */
    public static boolean recentCameraUse(long camera, long other, long lifecycle, long now, long elapsed) {
        return camera > 0 && camera > other && camera > lifecycle && camera <= now
                && now - camera <= 2500 && camera >= now - elapsed;
    }
}
