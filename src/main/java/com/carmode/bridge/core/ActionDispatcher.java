package com.carmode.bridge.core;

/** Native selections only; no external automation or duplicate broadcasts. */
public final class ActionDispatcher {
    public enum Action { CAMERAS, MUSIC, NAVIGATION }
    public interface Port { void launch(Action action); }
    private final Port port;
    public ActionDispatcher(Port port) { this.port = port; }
    public void select(Action action) { port.launch(action); }
    /** Explicit user assignments always win over defaults from the supplied macros. */
    public static String target(Action action, String assigned) {
        if (assigned != null && !assigned.trim().isEmpty()) return assigned.trim();
        return action == Action.CAMERAS ? "ivicar.cn.ivicaravm" : "com.zjinnova.zlink";
    }
}
