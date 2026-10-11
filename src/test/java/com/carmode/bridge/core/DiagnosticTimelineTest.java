package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticTimelineTest {
    @Test void countdownDoesNotRequestEarlyPress() {
        assertEquals(0, DiagnosticTimeline.stage(0));
        assertEquals(0, DiagnosticTimeline.stage(4999));
        assertEquals(1, DiagnosticTimeline.stage(5000));
    }
    @Test void threeSeparatePromptsHaveTwoSecondWindows() {
        assertEquals(1, DiagnosticTimeline.stage(6999));
        assertEquals(2, DiagnosticTimeline.stage(7000));
        assertEquals(2, DiagnosticTimeline.stage(8999));
        assertEquals(3, DiagnosticTimeline.stage(9000));
        assertEquals(3, DiagnosticTimeline.stage(10999));
    }
    @Test void observationContinuesAfterLastPrompt() {
        assertEquals(4, DiagnosticTimeline.stage(11000));
        assertEquals(4, DiagnosticTimeline.stage(15999));
        assertEquals(5, DiagnosticTimeline.stage(16000));
        assertEquals(5, DiagnosticTimeline.stage(40000));
    }
    @Test void countdownRoundsUpAndNeverDisplaysZeroPrematurely() {
        assertEquals(5, DiagnosticTimeline.secondsRemaining(0, 0));
        assertEquals(5, DiagnosticTimeline.secondsRemaining(999, 0));
        assertEquals(4, DiagnosticTimeline.secondsRemaining(1000, 0));
        assertEquals(1, DiagnosticTimeline.secondsRemaining(4999, 0));
        assertEquals(0, DiagnosticTimeline.secondsRemaining(5000, 0));
        assertEquals(0, DiagnosticTimeline.secondsRemaining(30000, 5));
    }
    @Test void phasesDistinguishExpectedActionsFromObservedEvents() {
        assertEquals("countdown", DiagnosticTimeline.phase(0));
        assertEquals("prompt_1", DiagnosticTimeline.phase(1));
        assertEquals("prompt_2", DiagnosticTimeline.phase(2));
        assertEquals("prompt_3", DiagnosticTimeline.phase(3));
        assertEquals("observe", DiagnosticTimeline.phase(4));
        assertEquals("complete", DiagnosticTimeline.phase(5));
    }
}
