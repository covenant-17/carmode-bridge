package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraStartupSequenceTest {
    @Test void bothEnabledRunZlinkOnlyAfterTheCameraDelayAndOnlyOnce() {
        CameraStartupSequence sequence = new CameraStartupSequence(1000, true);
        assertFalse(sequence.take(1000)); assertFalse(sequence.take(3499));
        assertTrue(sequence.take(3500)); assertFalse(sequence.take(4000));
    }
    @Test void cameraOnlyDoesNotScheduleZlink() {
        CameraStartupSequence sequence = new CameraStartupSequence(1000, false);
        assertFalse(sequence.take(3500)); assertFalse(sequence.take(60000));
    }
    @Test void userNavigationOrRollbackCancelsTheFollowup() {
        CameraStartupSequence sequence = new CameraStartupSequence(1000, true);
        sequence.cancel(); assertFalse(sequence.take(3500)); assertFalse(sequence.take(60000));
    }
    @Test void delayedDispatchStillProducesAtMostOneHandoff() {
        CameraStartupSequence sequence = new CameraStartupSequence(1000, true);
        assertTrue(sequence.take(60000)); assertFalse(sequence.take(60001));
    }
    @Test void customDelayControlsTheHandoffBoundary() {
        CameraStartupSequence sequence = new CameraStartupSequence(1000, true, 10000);
        assertFalse(sequence.take(3500)); assertFalse(sequence.take(10999));
        assertTrue(sequence.take(11000)); assertFalse(sequence.take(11001));
    }
    @Test void zeroAndMaximumDelayAreSupported() {
        CameraStartupSequence immediate = new CameraStartupSequence(1000, true, 0);
        assertTrue(immediate.take(1000)); assertFalse(immediate.take(1001));
        CameraStartupSequence maximum = new CameraStartupSequence(1000, true, 600000);
        assertFalse(maximum.take(600999)); assertTrue(maximum.take(601000));
    }
    @Test void invalidDelaysAreRejectedInsteadOfSchedulingUnexpectedLaunches() {
        assertThrows(IllegalArgumentException.class, () -> new CameraStartupSequence(1000, true, -1));
        assertThrows(IllegalArgumentException.class, () -> new CameraStartupSequence(1000, true, 600001));
    }
}
