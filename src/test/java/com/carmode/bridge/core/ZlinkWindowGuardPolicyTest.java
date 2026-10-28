package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZlinkWindowGuardPolicyTest {
    @Test void externalFullscreenTargetCanBeCorrectedButBoundedWindowIsLeftAlone() {
        ZlinkWindowGuardPolicy policy = new ZlinkWindowGuardPolicy();
        assertFalse(policy.take(true, true, 1216, 648, 1280, 720, 32, 0));
        assertFalse(policy.take(true, true, 1280, 100, 1280, 720, 32, 0));
        assertTrue(policy.take(true, true, 1280, 648, 1280, 720, 32, 0));
    }
    @Test void cameraAndOtherAppsAndDisabledModeNeverTriggerHomeTakeover() {
        ZlinkWindowGuardPolicy policy = new ZlinkWindowGuardPolicy();
        assertFalse(policy.take(false, true, 1280, 720, 1280, 720, 32, 0));
        assertFalse(policy.take(true, false, 1280, 720, 1280, 720, 32, 0));
        assertFalse(policy.take(true, true, 1280, 720, 0, 0, 32, 0));
        assertTrue(policy.take(true, true, 1280, 720, 1280, 720, 32, 0));
    }
    @Test void repeatedEventsDoNotCauseAnUnboundedFullscreenLoop() {
        ZlinkWindowGuardPolicy policy = new ZlinkWindowGuardPolicy();
        assertTrue(policy.take(true, true, 1280, 720, 1280, 720, 32, 0));
        assertFalse(policy.take(true, true, 1280, 720, 1280, 720, 32, 250));
        assertTrue(policy.take(true, true, 1280, 720, 1280, 720, 32, 2000));
        assertTrue(policy.take(true, true, 1280, 720, 1280, 720, 32, 4000));
        assertTrue(policy.exhausted());
        assertFalse(policy.take(true, true, 1280, 720, 1280, 720, 32, 60000));
    }
    @Test void settingsChangeCanResetTheAttemptBudget() {
        ZlinkWindowGuardPolicy policy = new ZlinkWindowGuardPolicy();
        for (int i=0;i<3;i++) assertTrue(policy.take(true, true, 1280, 720, 1280, 720, 32, i*2000));
        policy.reset();
        assertFalse(policy.exhausted());
        assertTrue(policy.take(true, true, 1280, 720, 1280, 720, 32, 6000));
    }
}
