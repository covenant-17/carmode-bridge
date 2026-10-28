package com.carmode.bridge.core;

/** One durable decision per Android boot, confined to its first two minutes. */
public final class CameraBootReturnPolicy {
    public static final long WINDOW_MS = 120000;
    public interface Store { int lastBoot(); boolean saveBoot(int boot); }
    private CameraBootReturnPolicy() {}
    public static boolean pending(boolean enabled, boolean recovery, int boot, long elapsed, int lastBoot) {
        return enabled && !recovery && boot >= 0 && elapsed >= 0 && elapsed < WINDOW_MS && boot != lastBoot;
    }
    public static boolean claim(boolean enabled, boolean recovery, int boot, long elapsed, Store store) {
        return pending(enabled, recovery, boot, elapsed, store.lastBoot()) && store.saveBoot(boot);
    }
}
