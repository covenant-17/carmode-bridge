package com.carmode.bridge.core;

/** Fixed one-shot handoff delay, not a camera-ready or phone-connected signal. */
public final class CameraStartupSequence {
    public static final long DELAY_MS = 1500;
    private boolean pending;
    private final long due;
    public CameraStartupSequence(long now, boolean launchZlink) { due = now + DELAY_MS; pending = launchZlink; }
    public boolean take(long now) {
        if (!pending || now < due) return false;
        pending = false; return true;
    }
    public void cancel() { pending = false; }
}
