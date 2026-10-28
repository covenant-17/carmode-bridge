package com.carmode.bridge.core;

import java.util.HashSet;
import java.util.Set;

/** Main-thread request bookkeeping: cancel visibility now, stop only after start commands arrive. */
public final class ForegroundStartQueue {
    private long generation;
    private final Set<Long> pending = new HashSet<>();
    public long request() { long token = ++generation; pending.add(token); return token; }
    public void cancel() { generation++; }
    public boolean delivered(long token) { pending.remove(token); return current(token); }
    public void failed(long token) { pending.remove(token); if (current(token)) cancel(); }
    public boolean current(long token) { return token > 0 && token == generation; }
    public boolean pending() { return !pending.isEmpty(); }
}
