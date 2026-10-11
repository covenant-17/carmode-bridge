package com.carmode.bridge.core;

/** A cold receiver must never also process a broadcast seen by the running receiver. */
public final class ModeWakePolicy {
    private ModeWakePolicy() { }
    public static boolean allowed(boolean runtimeRunning, boolean ready, boolean hcnEnabled,
            boolean ordered, boolean noAbort, boolean overlay, boolean passive,
            boolean malformed, boolean hasKey, int action, int repeatCount) {
        return !runtimeRunning && ready && hcnEnabled && ordered && !noAbort && overlay
                && !passive && !malformed && (!hasKey || (action == 0 && repeatCount == 0));
    }
}
