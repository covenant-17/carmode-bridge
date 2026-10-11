package com.carmode.bridge.core;

/** Activity visibility is deliberately not an input: closing UI must not stop MODE. */
public final class RuntimePolicy {
    private RuntimePolicy() {}
    public static boolean ready(boolean recovering, boolean enabled, boolean autoStart, boolean manuallyActivated) {
        return !recovering && enabled && (autoStart || manuallyActivated);
    }
    public static boolean keepService(boolean recovering, boolean ready, boolean recording) {
        return !recovering && (ready || recording);
    }
}
