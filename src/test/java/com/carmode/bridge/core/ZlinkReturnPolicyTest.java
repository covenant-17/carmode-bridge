package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZlinkReturnPolicyTest {
    @Test void returningFocusAfterBoundedZlinkAlsoRequestsRestoreOnce() {
        ZlinkReturnPolicy policy = new ZlinkReturnPolicy();
        policy.launched(); policy.returned();
        assertTrue(policy.take(true)); assertFalse(policy.take(true));
        policy.returned(); assertFalse(policy.take(true));
        policy.launched(); policy.returned(); assertTrue(policy.take(true));
    }
    @Test void returningFromFullscreenRestoresOnce() {
        ZlinkReturnPolicy policy = new ZlinkReturnPolicy();
        policy.launched(); policy.covered();
        assertTrue(policy.take(true)); assertFalse(policy.take(true));
    }
    @Test void exitingABoundedWindowDoesNotReopenIt() {
        ZlinkReturnPolicy policy = new ZlinkReturnPolicy();
        policy.launched(); assertFalse(policy.take(true));
    }
    @Test void ignoredBoundsCannotCreateAFullscreenBackLoop() {
        ZlinkReturnPolicy policy = new ZlinkReturnPolicy();
        policy.launched(); policy.covered(); assertTrue(policy.take(true));
        policy.covered(); assertFalse(policy.take(true));
    }
    @Test void explicitLaunchAllowsAnotherFullscreenRoundTrip() {
        ZlinkReturnPolicy policy = new ZlinkReturnPolicy();
        policy.launched(); policy.covered(); assertTrue(policy.take(true));
        policy.launched(); policy.covered(); assertTrue(policy.take(true));
    }
    @Test void connectionFocusChurnCannotRearmConsumedRestoration() {
        ZlinkReturnPolicy policy = new ZlinkReturnPolicy();
        policy.launched(); policy.returned(); assertTrue(policy.take(true));
        for (int i=0; i<100; i++) {
            policy.covered(); policy.returned(); assertFalse(policy.take(true));
        }
    }
    @Test void navigationAndDisablingTheFeatureDiscardPendingReturn() {
        ZlinkReturnPolicy policy = new ZlinkReturnPolicy();
        policy.launched(); policy.covered(); policy.reset(); assertFalse(policy.take(true));
        policy.launched(); policy.covered(); assertFalse(policy.take(false)); assertFalse(policy.take(true));
    }
}
