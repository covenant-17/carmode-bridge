package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZlinkWindowGuardPolicyTest {
    private boolean fullscreen(ZlinkWindowGuardPolicy p, long now) {
        return p.take(true, true, 1280, 720, 1280, 720, 32, now);
    }
    @Test void waitsForStableFullscreenInsteadOfFightingConnectionTransitions() {
        ZlinkWindowGuardPolicy p = new ZlinkWindowGuardPolicy();
        assertFalse(fullscreen(p, 0)); assertTrue(p.pending());
        assertFalse(fullscreen(p, 2999)); assertTrue(fullscreen(p, 3000));
        assertFalse(p.pending()); assertTrue(p.exhausted());
    }
    @Test void boundedWindowAndOtherAppsInterruptTheStabilityInterval() {
        ZlinkWindowGuardPolicy p = new ZlinkWindowGuardPolicy();
        assertFalse(fullscreen(p, 0));
        assertFalse(p.take(true, true, 1216, 660, 1280, 720, 32, 2000));
        assertFalse(p.pending()); assertFalse(fullscreen(p, 3000));
        assertFalse(p.take(true, false, 1280, 720, 1280, 720, 32, 5000));
        assertFalse(fullscreen(p, 6000)); assertTrue(fullscreen(p, 9000));
    }
    @Test void changedDimensionsRestartTheStabilityInterval() {
        ZlinkWindowGuardPolicy p = new ZlinkWindowGuardPolicy();
        assertFalse(fullscreen(p, 0));
        assertFalse(p.take(true, true, 1280, 660, 1280, 720, 32, 2900));
        assertFalse(p.take(true, true, 1280, 660, 1280, 720, 32, 5899));
        assertTrue(p.take(true, true, 1280, 660, 1280, 720, 32, 5900));
    }
    @Test void correctionCannotRepeatEvenWhenZlinkKeepsReopeningFullscreen() {
        ZlinkWindowGuardPolicy p = new ZlinkWindowGuardPolicy();
        assertFalse(fullscreen(p, 0)); assertTrue(fullscreen(p, 3000));
        for (int i=0; i<100; i++) {
            p.interrupted();
            assertFalse(fullscreen(p, 6000 + i*3000));
        }
        assertTrue(p.exhausted());
    }
    @Test void disabledLockedOrMissingWindowsDoNotAccumulateStableTime() {
        ZlinkWindowGuardPolicy p = new ZlinkWindowGuardPolicy();
        assertFalse(fullscreen(p, 0)); p.interrupted();
        assertFalse(fullscreen(p, 3000));
        assertFalse(p.take(false, true, 1280, 720, 1280, 720, 32, 5900));
        assertFalse(fullscreen(p, 6000)); assertTrue(fullscreen(p, 9000));
    }
    @Test void explicitSettingsChangeCanResetTheSingleCorrectionBudget() {
        ZlinkWindowGuardPolicy p = new ZlinkWindowGuardPolicy();
        assertFalse(fullscreen(p, 0)); assertTrue(fullscreen(p, 3000));
        p.reset(); assertFalse(p.exhausted());
        assertFalse(fullscreen(p, 6000)); assertTrue(fullscreen(p, 9000));
    }
}
