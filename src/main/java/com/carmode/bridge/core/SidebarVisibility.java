package com.carmode.bridge.core;

import java.util.HashSet;
import java.util.Set;

/** Tracks visible sidebar hosts, including Home behind an unfocused freeform task. */
public final class SidebarVisibility {
    private final Set<Object> hosts = new HashSet<>();
    public void shown(Object host) { hosts.add(host); }
    public boolean hidden(Object host) { return hosts.remove(host) && hosts.isEmpty(); }
    public boolean visible() { return !hosts.isEmpty(); }
}
