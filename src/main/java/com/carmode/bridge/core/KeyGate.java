package com.carmode.bridge.core;

/** Tracks the full down/up pair so a held key never advances repeatedly. */
public final class KeyGate {
    private boolean held;
    public boolean down(int repeatCount) {
        if (held || repeatCount != 0) return false;
        held = true;
        return true;
    }
    public void up() { held = false; }
    public void reset() { held = false; }
}
