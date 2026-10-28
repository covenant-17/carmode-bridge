package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZlinkRoutingTest {
    @Test void defaultZlinkAndExplicitSelectedComponentAreTargets() {
        assertTrue(ZlinkRouting.isTarget("com.zjinnova.zlink", "alternate/.Auto"));
        assertTrue(ZlinkRouting.isTarget("alternate", "alternate/.Auto"));
        assertTrue(ZlinkRouting.isTarget("alternate", "alternate"));
    }
    @Test void unrelatedAppsAreNotRedirected() {
        assertFalse(ZlinkRouting.isTarget("camera", "alternate/.Auto"));
        assertFalse(ZlinkRouting.isTarget(null, "alternate/.Auto"));
        assertFalse(ZlinkRouting.isTarget("alternate", null));
    }
    @Test void allWindowAndHomeConditionsAreRequired() {
        assertTrue(ZlinkRouting.homeOnly(true, true, true, false, "com.zjinnova.zlink", "com.zjinnova.zlink"));
        assertFalse(ZlinkRouting.homeOnly(false, true, true, false, "com.zjinnova.zlink", "com.zjinnova.zlink"));
        assertFalse(ZlinkRouting.homeOnly(true, false, true, false, "com.zjinnova.zlink", "com.zjinnova.zlink"));
        assertFalse(ZlinkRouting.homeOnly(true, true, false, false, "com.zjinnova.zlink", "com.zjinnova.zlink"));
    }
    @Test void recoveryAndOtherAssignmentsKeepTheNormalRoute() {
        assertFalse(ZlinkRouting.homeOnly(true, true, true, true, "com.zjinnova.zlink", "com.zjinnova.zlink"));
        assertFalse(ZlinkRouting.homeOnly(true, true, true, false, "camera", "com.zjinnova.zlink"));
    }
}
