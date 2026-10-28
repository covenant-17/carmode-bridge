package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraStartupSequenceTest {
    @Test void bothEnabledRunZlinkOnlyAfterTheCameraDelayAndOnlyOnce() {
        CameraStartupSequence sequence = new CameraStartupSequence(1000, true);
        assertFalse(sequence.take(1000)); assertFalse(sequence.take(2499));
        assertTrue(sequence.take(2500)); assertFalse(sequence.take(4000));
    }
    @Test void cameraOnlyDoesNotScheduleZlink() {
        CameraStartupSequence sequence = new CameraStartupSequence(1000, false);
        assertFalse(sequence.take(2500)); assertFalse(sequence.take(60000));
    }
    @Test void userNavigationOrRollbackCancelsTheFollowup() {
        CameraStartupSequence sequence = new CameraStartupSequence(1000, true);
        sequence.cancel(); assertFalse(sequence.take(2500)); assertFalse(sequence.take(60000));
    }
    @Test void delayedDispatchStillProducesAtMostOneHandoff() {
        CameraStartupSequence sequence = new CameraStartupSequence(1000, true);
        assertTrue(sequence.take(60000)); assertFalse(sequence.take(60001));
    }
}
