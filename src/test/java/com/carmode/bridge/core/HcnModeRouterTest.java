package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HcnModeRouterTest {
    private final HcnModeRouter router = new HcnModeRouter();
    private final HcnModeRouter.Input input = new HcnModeRouter.Input();
    private final HcnModeRouter.State state = new HcnModeRouter.State();
    private final FakePort port = new FakePort();
    HcnModeRouterTest() {
        input.action = HcnModeRouter.MODE; input.ordered = true;
        state.enabled = state.ready = state.overlay = true;
    }
    private static final class FakePort implements HcnModeRouter.Port {
        int presses, aborts, cancels;
        boolean show = true, abort = true, throwShow, throwAbort, throwCancel;
        final StringBuilder calls = new StringBuilder();
        final MenuSession menu = new MenuSession();
        long now;
        public boolean press() {
            presses++; calls.append('P');
            if (throwShow) throw new IllegalStateException();
            if (show) menu.press(now);
            return show;
        }
        public boolean abort() {
            aborts++; calls.append('A');
            if (throwAbort) throw new IllegalStateException();
            return abort;
        }
        public void cancel() {
            cancels++; menu.cancel(); calls.append('C');
            if (throwCancel) throw new IllegalStateException();
        }
    }
    private HcnModeRouter.Result route() { return router.route(input, state, port); }
    private void pass(String reason) {
        HcnModeRouter.Result result = route();
        assertEquals(reason, result.reason); assertFalse(result.aborted); assertFalse(result.advanced);
        assertEquals(0, port.presses); assertEquals(0, port.aborts);
    }
    private void key() { input.hasKey = true; input.keyCode = 66; input.deviceId = 2; input.downTime = 100; }

    @Test void defaultOffPreservesStock() { state.enabled = false; pass("disabled"); }
    @Test void oldStickyReplayCannotOpenMenuAndTimeOutToCamera() {
        input.initialSticky = true; pass("initial_sticky_replay");
        assertFalse(port.menu.isOpen()); assertEquals(-1, port.menu.poll(10000));
        input.initialSticky = false;
        assertTrue(route().advanced); assertEquals(1, port.presses);
    }
    @Test void disabledOrNotManuallyActivatedPreservesStock() { state.ready = false; pass("not_ready"); }
    @Test void diagnosticsArePassive() { state.scenario = true; pass("diagnostic_scenario"); }
    @Test void learningIsPassive() { state.learning = true; pass("learning"); }
    @Test void missingOverlayPreservesStock() { state.overlay = false; pass("overlay_missing"); }
    @Test void unorderedDeliveryNeverShowsMenu() { input.ordered = false; pass("not_ordered"); }
    @Test void noAbortFlagNeverShowsMenu() { input.noAbort = true; pass("no_abort_flag"); }
    @Test void malformedPayloadPreservesStock() { input.malformed = true; pass("malformed_payload"); }
    @Test void unknownActionCannotDriveMenu() { input.action = "unrelated"; pass("unknown_action"); }
    @Test void auxiliaryIsObservationOnly() { input.action = HcnModeRouter.AUXILIARY; pass("auxiliary_only"); }
    @Test void missingPayloadIsAcceptedAndAbortHappensAfterMenu() {
        HcnModeRouter.Result result = route();
        assertEquals("handled", result.reason); assertTrue(result.advanced); assertTrue(result.aborted);
        assertEquals("PA", port.calls.toString());
    }
    @Test void syntheticEnterIsAcceptedWithoutLearningIt() {
        key(); assertTrue(route().aborted); assertEquals(0, port.menu.selected());
    }
    @Test void payloadKeyCodeIsNotHardcodedToEnter() {
        key(); input.keyCode = 123; assertTrue(route().advanced);
    }
    @Test void orphanedUpAndRepeatsNeverOpenMenu() {
        key(); input.keyAction = 1; pass("key_up");
        input.keyAction = 0; input.repeatCount = 3; pass("key_repeat");
        input.keyAction = 2; pass("key_action");
    }
    @Test void acceptedHoldAndReleaseDoNotAdvanceOrReachStock() {
        key(); route(); input.repeatCount = 1;
        assertEquals("held_event_suppressed", route().reason);
        input.keyAction = 1; input.repeatCount = 0; assertTrue(route().aborted);
        assertEquals(1, port.presses); assertEquals(3, port.aborts);
        assertFalse(route().aborted); // Second orphaned release.
    }
    @Test void unrelatedKeyReleaseIsNotSwallowed() {
        key(); route(); input.keyAction = 1; input.keyCode = 67;
        assertFalse(route().aborted); assertEquals(1, port.aborts);
        input.keyCode = 66; assertTrue(route().aborted);
    }
    @Test void failedMenuNeverAbortsAndCancelsPartialSelection() {
        port.show = false; assertEquals("menu_failed", route().reason);
        assertEquals(0, port.aborts); assertEquals(1, port.cancels);
    }
    @Test void thrownMenuErrorNeverAborts() {
        port.throwShow = true;
        HcnModeRouter.Result result = route();
        assertEquals("menu_error", result.reason); assertEquals("IllegalStateException", result.error);
        assertEquals(0, port.aborts); assertEquals(1, port.cancels);
    }
    @Test void rejectedAbortCancelsSelection() {
        port.abort = false; HcnModeRouter.Result result = route();
        assertEquals("abort_failed", result.reason); assertFalse(result.aborted);
        assertEquals("PAC", port.calls.toString()); assertEquals(-1, port.menu.poll(2000));
    }
    @Test void thrownAbortAndCleanupErrorsStayFailOpen() {
        port.throwAbort = port.throwCancel = true;
        assertEquals("abort_failed", route().reason); assertFalse(port.menu.isOpen());
    }
    @Test void fastClicksAreNotDebouncedAndAuxDoesNotDuplicateThem() {
        key(); route();
        input.action = HcnModeRouter.AUXILIARY; assertFalse(route().advanced);
        input.action = HcnModeRouter.MODE; input.downTime++; port.now = 1; route();
        input.downTime++; port.now = 2; route();
        assertEquals(3, port.presses); assertEquals(3, port.aborts);
        assertEquals(-1, port.menu.poll(2001)); assertEquals(2, port.menu.poll(2002));
        assertEquals(-1, port.menu.poll(2003));
        port.now = 2004; route(); assertEquals(0, port.menu.selected());
    }
    @Test void resetDropsCapturedRepeatOwnership() {
        key(); route(); router.reset(); input.repeatCount = 1;
        assertFalse(route().aborted); assertEquals(1, port.presses);
    }
    @Test void switchingToPassiveDoesNotSwallowCapturedRelease() {
        key(); route(); state.scenario = true; input.keyAction = 1;
        assertFalse(route().aborted); state.scenario = false; assertFalse(route().aborted);
    }
    @Test void learnedKeyAndHcnAreExclusive() {
        assertFalse(HcnModeRouter.useLearnedKey(true)); assertTrue(HcnModeRouter.useLearnedKey(false));
    }
}
