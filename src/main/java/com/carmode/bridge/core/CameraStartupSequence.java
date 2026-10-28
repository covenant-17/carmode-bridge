package com.carmode.bridge.core;

/** Configurable one-shot handoff delay, not a camera-ready or phone-connected signal. */
public final class CameraStartupSequence {
    public static final int DEFAULT_DELAY_MS = 2500;
    public static final int MAX_DELAY_MS = 600000;
    private boolean pending;
    private final long due;
    public static boolean validDelay(int value) { return value >= 0 && value <= MAX_DELAY_MS; }
    public CameraStartupSequence(long now, boolean launchZlink) { this(now, launchZlink, DEFAULT_DELAY_MS); }
    public CameraStartupSequence(long now, boolean launchZlink, int delayMs) {
        if (!validDelay(delayMs)) throw new IllegalArgumentException("Invalid startup delay");
        due = now + delayMs; pending = launchZlink;
    }
    public boolean take(long now) {
        if (!pending || now < due) return false;
        pending = false; return true;
    }
    public void cancel() { pending = false; }
}
