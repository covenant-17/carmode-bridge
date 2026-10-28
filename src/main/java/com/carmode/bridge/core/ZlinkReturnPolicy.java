package com.carmode.bridge.core;

/** One return-to-window attempt per confirmed ZLink session, never an unbounded relaunch loop. */
public final class ZlinkReturnPolicy {
    private boolean armed, pending;
    public void launched() { armed = true; pending = false; }
    public void covered() { if (armed) pending = true; }
    public void returned() { if (armed) pending = true; }
    public boolean take(boolean eligible) {
        if (!eligible) { reset(); return false; }
        if (!pending) return false;
        armed = false; pending = false; return true;
    }
    public void reset() { armed = false; pending = false; }
}
