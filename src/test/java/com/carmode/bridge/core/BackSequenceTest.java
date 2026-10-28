package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BackSequenceTest {
    @Test void fifthPressTriggersOnceAndStartsFreshAfterwards() {
        BackSequence keys = new BackSequence();
        for (int i = 0; i < 4; i++) assertFalse(keys.press(i * 100));
        assertTrue(keys.press(400));
        assertFalse(keys.press(500));
    }
    @Test void windowIsMeasuredFromFirstPressAndIncludesFiveSecondBoundary() {
        BackSequence keys = new BackSequence();
        for (long now : new long[]{0, 1000, 2000, 4000}) assertFalse(keys.press(now));
        assertTrue(keys.press(5000));
        keys = new BackSequence();
        for (long now : new long[]{0, 2000, 4000, 6000, 8000}) assertFalse(keys.press(now));
    }
    @Test void otherInputOrExternalAppResetsSequence() {
        BackSequence keys = new BackSequence();
        for (int i = 0; i < 4; i++) keys.press(i);
        keys.reset();
        for (int i = 0; i < 4; i++) assertFalse(keys.press(i + 10));
        assertTrue(keys.press(14));
    }
    @Test void clockRestartCannotCompleteOldSequence() {
        BackSequence keys = new BackSequence();
        for (int i = 0; i < 4; i++) keys.press(10000 + i);
        assertFalse(keys.press(0));
    }
}
