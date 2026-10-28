package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForegroundStartQueueTest {
    @Test void hideBeforeServiceCreationWaitsForForegroundCommandAcknowledgement() {
        ForegroundStartQueue queue = new ForegroundStartQueue();
        long token = queue.request(); queue.cancel();
        assertTrue(queue.pending()); assertFalse(queue.current(token));
        assertFalse(queue.delivered(token)); assertFalse(queue.pending());
    }
    @Test void startHideStartDoesNotLetStaleDeliveryCancelTheNewRequest() {
        ForegroundStartQueue queue = new ForegroundStartQueue();
        long first = queue.request(); queue.cancel(); long second = queue.request();
        assertFalse(queue.delivered(first)); assertTrue(queue.pending());
        assertTrue(queue.current(second)); assertTrue(queue.delivered(second));
        assertFalse(queue.pending()); assertTrue(queue.current(second));
    }
    @Test void everyConcurrentStartMustBeAcknowledgedBeforeStopping() {
        ForegroundStartQueue queue = new ForegroundStartQueue();
        long first = queue.request(), second = queue.request(), third = queue.request(); queue.cancel();
        assertFalse(queue.delivered(first)); assertTrue(queue.pending());
        assertFalse(queue.delivered(second)); assertTrue(queue.pending());
        assertFalse(queue.delivered(third)); assertFalse(queue.pending());
    }
    @Test void failedOldStartCannotInvalidateNewSuccessfulRequest() {
        ForegroundStartQueue queue = new ForegroundStartQueue();
        long first = queue.request(), second = queue.request();
        queue.failed(first); assertTrue(queue.pending()); assertTrue(queue.current(second));
        assertTrue(queue.delivered(second)); assertFalse(queue.pending());
    }
    @Test void failedCurrentStartAndNotificationOnlyCommandsLeaveNoPendingDebt() {
        ForegroundStartQueue queue = new ForegroundStartQueue();
        long token = queue.request(); queue.failed(token);
        assertFalse(queue.pending()); assertFalse(queue.current(token));
        assertFalse(queue.delivered(0)); assertFalse(queue.pending());
    }
    @Test void duplicateAcknowledgementDoesNotInvalidateActiveSession() {
        ForegroundStartQueue queue = new ForegroundStartQueue();
        long token = queue.request(); assertTrue(queue.delivered(token));
        assertTrue(queue.delivered(token)); assertTrue(queue.current(token)); assertFalse(queue.pending());
        queue.cancel(); assertFalse(queue.current(token));
    }
}
