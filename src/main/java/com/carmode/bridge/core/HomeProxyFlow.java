package com.carmode.bridge.core;

/** A runtime failure must not hide HOME. A forwarding failure must leave recovery visible. */
public final class HomeProxyFlow {
    public interface Port {
        void startRuntime();
        void forwardOem();
        void error(String stage, RuntimeException error);
        void recovery();
        void retainHome();
    }
    private HomeProxyFlow() {}
    public static void run(boolean autoStart, boolean recovering, Port port) {
        run(autoStart, recovering, false, port);
    }
    public static void run(boolean autoStart, boolean recovering, boolean runtimeRunning, Port port) {
        if (autoStart && !recovering && !runtimeRunning) {
            try { port.startRuntime(); }
            catch (RuntimeException error) { port.error("runtime", error); }
        }
        try { port.forwardOem(); }
        catch (RuntimeException error) { port.error("forward", error); port.recovery(); return; }
        port.retainHome();
    }
}
