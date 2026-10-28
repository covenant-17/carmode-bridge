package com.carmode.bridge.core;

/** Event-driven corrections are bounded; firmware refusing bounds must never cause a launch loop. */
public final class ZlinkWindowGuardPolicy {
    public static final int MAX_ATTEMPTS = 1;
    public static final long STABLE_MS = 3000;
    private int attempts;
    private long candidateSince = -1;
    private int candidateWidth, candidateHeight;
    public boolean take(boolean enabled, boolean target, int width, int height, int screenWidth, int screenHeight,
                        int sideMargin, long now) {
        if (!enabled || !target || screenWidth <= 0 || screenHeight <= 0
                || width < screenWidth - sideMargin || height < screenHeight / 2) {
            interrupted(); return false;
        }
        if (exhausted()) return false;
        if (candidateSince < 0 || candidateWidth != width || candidateHeight != height || now < candidateSince) {
            candidateSince = now; candidateWidth = width; candidateHeight = height;
        }
        if (now - candidateSince < STABLE_MS) return false;
        attempts++; interrupted(); return true;
    }
    public boolean pending() { return candidateSince >= 0 && !exhausted(); }
    public void interrupted() { candidateSince = -1; }
    public boolean exhausted() { return attempts >= MAX_ATTEMPTS; }
    public void reset() { attempts = 0; interrupted(); }
}
