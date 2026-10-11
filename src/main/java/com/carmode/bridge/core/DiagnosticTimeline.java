package com.carmode.bridge.core;

/** Monotonic scenario clock. Prompts describe requested actions, never observed presses. */
public final class DiagnosticTimeline {
    public static final long DURATION_MS = 16000;
    public static int stage(long elapsed) {
        if (elapsed < 5000) return 0;
        if (elapsed < 7000) return 1;
        if (elapsed < 9000) return 2;
        if (elapsed < 11000) return 3;
        return elapsed < DURATION_MS ? 4 : 5;
    }
    public static String phase(int stage) {
        return new String[]{"countdown", "prompt_1", "prompt_2", "prompt_3", "observe", "complete"}[stage];
    }
    public static int secondsRemaining(long elapsed, int stage) {
        long end = new long[]{5000, 7000, 9000, 11000, DURATION_MS, DURATION_MS}[stage];
        return (int) Math.max(0, (end - elapsed + 999) / 1000);
    }
}
