package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DockPolicyTest {
    @Test void camryTransitionDoesNotRequireTheManualSessionSwitch() {
        assertTrue(DockPolicy.allowed(true, true, false, false, true));
        assertFalse(DockPolicy.allowed(true, false, true, false, true));
    }
    @Test void manualSessionRequiresItsSwitchIndependentlyOfHome() {
        assertTrue(DockPolicy.allowed(false, false, true, false, true));
        assertFalse(DockPolicy.allowed(false, true, false, false, true));
    }
    @Test void permissionLossOrRecoveryBlocksBothKindsOfSession() {
        for (boolean automatic : new boolean[]{false, true}) {
            assertFalse(DockPolicy.allowed(automatic, true, true, true, true));
            assertFalse(DockPolicy.allowed(automatic, true, true, false, false));
        }
    }
}
