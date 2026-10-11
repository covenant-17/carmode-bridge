package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BackgroundLifecycleTest {
    private final String pkg = "com.carmode.bridge", cls = "com.carmode.bridge.ModeService";
    @Test void fullComponentIsRecognizedAmongOtherServices() {
        assertTrue(AccessibilitySetting.contains("other/other.Service:" + pkg + "/" + cls, pkg, cls));
    }
    @Test void shortenedComponentAndWhitespaceAreRecognized() {
        assertTrue(AccessibilitySetting.contains(" " + pkg + "/.ModeService ", pkg, cls));
    }
    @Test void unrelatedOrMalformedComponentsAreNotPermission() {
        for (String value : new String[]{null, "", pkg, "other/.ModeService", pkg + "/.OtherService", pkg + "/" + cls + "/extra"})
            assertFalse(AccessibilitySetting.contains(value, pkg, cls));
    }
    @Test void enabledButUnboundIsDistinctFromRevoked() {
        assertEquals("enabled_disconnected", AccessibilitySetting.status(true, false));
        assertEquals("disabled", AccessibilitySetting.status(false, false));
        assertEquals("enabled_connected", AccessibilitySetting.status(true, true));
        assertEquals("disabled_connection_pending", AccessibilitySetting.status(false, true));
    }
    @Test void coldBootCanBeReadyWithoutAnyActivity() {
        assertTrue(RuntimePolicy.ready(false, true, true, false));
    }
    @Test void manualActivationLastsForProcessNotActivityVisibility() {
        assertTrue(RuntimePolicy.ready(false, true, false, true));
        assertFalse(RuntimePolicy.ready(false, true, false, false));
    }
    @Test void explicitDisableAndCrashRecoveryNeverHandleMode() {
        assertFalse(RuntimePolicy.ready(false, false, true, true));
        assertFalse(RuntimePolicy.ready(true, true, true, true));
    }
    @Test void passiveRecordingCanKeepReceiverWithoutModeInterception() {
        assertTrue(RuntimePolicy.keepService(false, false, true));
        assertTrue(RuntimePolicy.keepService(false, true, false));
        assertFalse(RuntimePolicy.keepService(false, false, false));
        assertFalse(RuntimePolicy.keepService(true, true, true));
    }
}
