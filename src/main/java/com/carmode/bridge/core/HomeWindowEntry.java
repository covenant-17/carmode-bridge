package com.carmode.bridge.core;

/** One automatic window request for each explicitly opened Home screen. */
public final class HomeWindowEntry {
    private boolean pending;
    public void shown(boolean windowHome, boolean externalLaunch) { pending = windowHome && !externalLaunch; }
    public boolean take(boolean eligible) {
        boolean result = eligible && pending;
        pending = false; return result;
    }
    public void cancel() { pending = false; }
}
