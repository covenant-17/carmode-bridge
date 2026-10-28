package com.carmode.bridge.core;

/** Five distinct Back actions in a fixed window; callers discard key repeats. */
public final class BackSequence {
    private long first = -1;
    private int count;
    public boolean press(long now) {
        if (first < 0 || now < first || now - first > 5000) { first = now; count = 0; }
        if (++count < 5) return false;
        reset(); return true;
    }
    public void reset() { first = -1; count = 0; }
}
