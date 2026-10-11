package com.carmode.bridge.core;

/** A single-threaded state machine; callers supply a monotonic clock. */
public final class MenuSession {
    public static final long TIMEOUT_MS = 2000;
    private int selected = -1;
    private long deadline;

    public void press(long now) {
        selected = selected < 0 ? 0 : (selected + 1) % 3;
        deadline = now + TIMEOUT_MS;
    }

    /** Consumes the pending choice exactly once, or returns -1. */
    public int poll(long now) {
        if (selected < 0 || now < deadline) return -1;
        int result = selected;
        cancel();
        return result;
    }

    public float remaining(long now) {
        return selected < 0 ? 0 : Math.max(0, Math.min(1, (deadline - now) / (float) TIMEOUT_MS));
    }

    public int selected() { return selected; }
    public boolean isOpen() { return selected >= 0; }
    public void cancel() { selected = -1; deadline = 0; }
}
