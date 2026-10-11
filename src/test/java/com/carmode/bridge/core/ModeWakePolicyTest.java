package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModeWakePolicyTest {
    private boolean allowed(boolean running, boolean ready, boolean enabled, boolean ordered,
            boolean noAbort, boolean overlay, boolean passive, boolean malformed, boolean key, int action, int repeats) {
        return ModeWakePolicy.allowed(running, ready, enabled, ordered, noAbort, overlay, passive, malformed, key, action, repeats);
    }
    @Test void freshColdModeCanWake() {
        assertTrue(allowed(false, true, true, true, false, true, false, false, true, 0, 0));
        assertTrue(allowed(false, true, true, true, false, true, false, false, false, 0, 0));
    }
    @Test void runningReceiverOwnsEventWithoutDuplicate() {
        assertFalse(allowed(true, true, true, true, false, true, false, false, true, 0, 0));
    }
    @Test void disabledOrUnreadyDoesNotWake() {
        assertFalse(allowed(false, false, true, true, false, true, false, false, true, 0, 0));
        assertFalse(allowed(false, true, false, true, false, true, false, false, true, 0, 0));
    }
    @Test void nativeDeliveryMustBeAbortableAndOverlayAvailable() {
        assertFalse(allowed(false, true, true, false, false, true, false, false, true, 0, 0));
        assertFalse(allowed(false, true, true, true, true, true, false, false, true, 0, 0));
        assertFalse(allowed(false, true, true, true, false, false, false, false, true, 0, 0));
    }
    @Test void learningDiagnosticAndMalformedArePassThrough() {
        assertFalse(allowed(false, true, true, true, false, true, true, false, true, 0, 0));
        assertFalse(allowed(false, true, true, true, false, true, false, true, true, 0, 0));
    }
    @Test void releaseAndHoldCannotColdStartOrAdvance() {
        assertFalse(allowed(false, true, true, true, false, true, false, false, true, 1, 0));
        assertFalse(allowed(false, true, true, true, false, true, false, false, true, 0, 1));
        assertFalse(allowed(false, true, true, true, false, true, false, false, true, 0, -1));
    }
}
