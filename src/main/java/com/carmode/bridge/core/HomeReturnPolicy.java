package com.carmode.bridge.core;

/** A freeform signal is only a short-lived hint, never a background launch trigger. */
public final class HomeReturnPolicy {
    public enum Target { HOME, ALL_APPS, RECOVERY }
    private long freeformAt = -1, lastReturnAt = -1;
    private int rapidReturns;
    public void explicitNavigation() { freeformAt = -1; lastReturnAt = -1; rapidReturns = 0; }
    public void freeformMove(long now) { freeformAt = now; }
    public boolean freshFreeform(long now) {
        return freeformAt >= 0 && now >= freeformAt && now - freeformAt <= 1500;
    }
    public long returnDelayMs(long now) { return freshFreeform(now) ? 0 : 120; }
    public Target returned(long now) {
        rapidReturns = lastReturnAt >= 0 && now - lastReturnAt < 2000 ? rapidReturns + 1 : 1;
        lastReturnAt = now;
        boolean allApps = freshFreeform(now);
        freeformAt = -1;
        if (rapidReturns > 2) return Target.RECOVERY;
        return allApps ? Target.ALL_APPS : Target.HOME;
    }
}
