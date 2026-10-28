package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppWindowRoutingTest {
    @Test void explicitThirdPartyAppsDoNotNeedZlinkOrCameraAssignments() {
        for (String pkg : new String[]{"com.android.chrome", "com.android.contacts", "com.android.settings", "any.app"})
            assertTrue(AppWindowRouting.allowed(true, true, true, false, pkg, "com.carmode.bridge"));
    }
    @Test void defaultOffHomeOffWindowOffAndRecoveryKeepOrdinaryLaunches() {
        assertFalse(AppWindowRouting.allowed(true, true, false, false, "any.app", "com.carmode.bridge"));
        assertFalse(AppWindowRouting.allowed(false, true, true, false, "any.app", "com.carmode.bridge"));
        assertFalse(AppWindowRouting.allowed(true, false, true, false, "any.app", "com.carmode.bridge"));
        assertFalse(AppWindowRouting.allowed(true, true, true, true, "any.app", "com.carmode.bridge"));
    }
    @Test void internalSettingsAndMissingDestinationsCannotCauseRecursiveHomeLaunch() {
        assertFalse(AppWindowRouting.allowed(true, true, true, false, "com.carmode.bridge", "com.carmode.bridge"));
        assertFalse(AppWindowRouting.allowed(true, true, true, false, null, "com.carmode.bridge"));
        assertFalse(AppWindowRouting.allowed(true, true, true, false, "", "com.carmode.bridge"));
    }
}
