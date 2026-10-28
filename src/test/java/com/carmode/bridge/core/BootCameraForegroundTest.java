package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BootCameraForegroundTest {
    @Test void pauseOfOldActivityDoesNotEraseTheNewActivityInTheSamePackage() {
        BootCameraForeground f = new BootCameraForeground();
        f.resumed("camera", "First", 100);
        f.resumed("camera", "Second", 200);
        f.paused("camera", "First", 300);
        assertEquals("camera", f.pkg); assertEquals("Second", f.activity);
        assertEquals(300, f.latest);
    }
    @Test void matchingPauseClearsForegroundAndOldEventsDoNotReplaceNewEvidence() {
        BootCameraForeground f = new BootCameraForeground();
        f.resumed("camera", "Main", 200);
        f.paused("camera", "Main", 300); assertNull(f.pkg);
        f.resumed("old", "Main", 100); assertNull(f.pkg);
    }
    @Test void pauseOfAnotherPackageCannotClearTheCamera() {
        BootCameraForeground f = new BootCameraForeground();
        f.resumed("camera", "Main", 200);
        f.paused("home", "Home", 300); assertEquals("camera", f.pkg);
    }
    @Test void missingCameraLifecycleCanUseUniquelyRecentAggregateEvidence() {
        assertTrue(BootCameraForeground.recentCameraUse(9900, 8000, 8000, 10000, 5000));
        assertTrue(BootCameraForeground.recentCameraUse(9900, 0, 0, 10000, 5000));
    }
    @Test void olderAmbiguousOrSupersededCameraUsageCannotTakeHome() {
        assertFalse(BootCameraForeground.recentCameraUse(9900, 9900, 0, 10000, 5000));
        assertFalse(BootCameraForeground.recentCameraUse(9900, 9950, 0, 10000, 5000));
        assertFalse(BootCameraForeground.recentCameraUse(9900, 8000, 9900, 10000, 5000));
        assertFalse(BootCameraForeground.recentCameraUse(7000, 6000, 0, 10000, 5000));
    }
    @Test void previousBootAndFutureOrMissingTimestampsAreRejected() {
        assertFalse(BootCameraForeground.recentCameraUse(9000, 8000, 0, 10000, 500));
        assertFalse(BootCameraForeground.recentCameraUse(11000, 8000, 0, 10000, 5000));
        assertFalse(BootCameraForeground.recentCameraUse(0, 0, 0, 10000, 5000));
    }
}
