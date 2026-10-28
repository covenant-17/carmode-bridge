package com.carmode.bridge.core;

/** Android-free routing policy. No debounce: two fast physical clicks must remain two clicks. */
public final class HcnModeRouter {
    public static final String MODE = "android.intent.action.KSRC_BUTTON";
    public static final String AUXILIARY = "com.hcn.autokey";
    public interface Port {
        boolean press();
        boolean abort();
        void cancel();
    }
    public static final class Input {
        public String action;
        public boolean ordered, noAbort, malformed, hasKey, initialSticky;
        public int keyAction, repeatCount, keyCode, deviceId;
        public long downTime;
    }
    public static final class State {
        public boolean enabled, ready, scenario, learning, overlay;
    }
    public static final class Result {
        public final String reason, error;
        public final boolean advanced, aborted;
        private Result(String reason, boolean advanced, boolean aborted, String error) {
            this.reason = reason; this.advanced = advanced; this.aborted = aborted; this.error = error;
        }
    }
    private boolean captured;
    private int keyCode, deviceId;
    private long downTime;
    public void reset() { captured = false; }
    public static boolean useLearnedKey(boolean hcnEnabled) { return !hcnEnabled; }
    private Result pass(String reason) { return new Result(reason, false, false, null); }

    public Result route(Input input, State state, Port port) {
        if (AUXILIARY.equals(input.action)) return pass("auxiliary_only");
        if (!MODE.equals(input.action)) return pass("unknown_action");
        if (input.initialSticky) return pass("initial_sticky_replay");
        if (!state.enabled) { reset(); return pass("disabled"); }
        if (!state.ready) { reset(); return pass("not_ready"); }
        if (state.scenario) { reset(); return pass("diagnostic_scenario"); }
        if (state.learning) { reset(); return pass("learning"); }
        if (input.malformed) return pass("malformed_payload");
        if (!input.ordered) return pass("not_ordered");
        if (input.noAbort) return pass("no_abort_flag");
        if (!state.overlay) { reset(); return pass("overlay_missing"); }
        if (input.hasKey && (input.keyAction != 0 || input.repeatCount != 0)) {
            // AutoSource ignores the payload and reacts to UP/repeats too. Suppress only
            // follow-ups belonging to a DOWN already accepted by our menu, never advance it.
            boolean owned = captured && keyCode == input.keyCode && deviceId == input.deviceId
                    && downTime == input.downTime && (input.keyAction == 1 ||
                    (input.keyAction == 0 && input.repeatCount > 0));
            if (owned && input.keyAction == 1) reset();
            if (owned) return abort(port, false);
            return pass(input.keyAction == 1 ? "key_up" : input.keyAction == 0 ? "key_repeat" : "key_action");
        }
        reset();
        try {
            if (!port.press()) { port.cancel(); return pass("menu_failed"); }
        } catch (RuntimeException e) {
            safeCancel(port);
            return new Result("menu_error", false, false, e.getClass().getSimpleName());
        }
        Result result = abort(port, true);
        if (result.aborted && input.hasKey) {
            captured = true; keyCode = input.keyCode; deviceId = input.deviceId; downTime = input.downTime;
        }
        return result;
    }
    private Result abort(Port port, boolean advanced) {
        try {
            if (port.abort()) return new Result(advanced ? "handled" : "held_event_suppressed", advanced, true, null);
            safeCancel(port); reset();
            return new Result("abort_failed", advanced, false, null);
        } catch (RuntimeException e) {
            safeCancel(port); reset();
            return new Result("abort_failed", advanced, false, e.getClass().getSimpleName());
        }
    }
    private void safeCancel(Port port) {
        try { port.cancel(); } catch (RuntimeException ignored) { /* Keep native delivery alive. */ }
    }
}
