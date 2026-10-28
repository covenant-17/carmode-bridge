package com.carmode.bridge.core;

/** Event-driven corrections are bounded; firmware refusing bounds must never cause a launch loop. */
public final class ZlinkWindowGuardPolicy {
    public static final int MAX_ATTEMPTS = 3;
    private int attempts;
    private long last = -1;
    public boolean take(boolean enabled, boolean target, int width, int height, int screenWidth, int screenHeight,
                        int sideMargin, long now) {
        if (!enabled || !target || screenWidth <= 0 || screenHeight <= 0
                || width < screenWidth - sideMargin || height < screenHeight / 2) return false;
        if (attempts >= MAX_ATTEMPTS || (last >= 0 && now - last < 2000)) return false;
        attempts++; last = now; return true;
    }
    public boolean exhausted() { return attempts >= MAX_ATTEMPTS; }
    public void reset() { attempts = 0; last = -1; }
}
