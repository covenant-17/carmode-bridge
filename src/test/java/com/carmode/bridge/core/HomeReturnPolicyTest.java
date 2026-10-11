package com.carmode.bridge.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HomeReturnPolicyTest {
    @Test void receivedSignalSkipsFallbackWithoutConsumingHint() {
        HomeReturnPolicy policy = new HomeReturnPolicy(); policy.freeformMove(100);
        assertEquals(0, policy.returnDelayMs(120));
        assertEquals(0, policy.returnDelayMs(130));
        assertEquals(HomeReturnPolicy.Target.ALL_APPS, policy.returned(140));
        assertEquals(120, policy.returnDelayMs(150));
    }
    @Test void missingOrExpiredSignalKeepsKnownWorkingFallback() {
        HomeReturnPolicy policy = new HomeReturnPolicy();
        assertEquals(120, policy.returnDelayMs(100));
        policy.freeformMove(100);
        assertEquals(0, policy.returnDelayMs(1600));
        assertEquals(120, policy.returnDelayMs(1601));
        assertEquals(120, policy.returnDelayMs(99));
    }
    @Test void signalArrivingDuringFallbackStillSelectsGrid() {
        HomeReturnPolicy policy = new HomeReturnPolicy();
        assertEquals(120, policy.returnDelayMs(100));
        policy.freeformMove(120);
        assertEquals(0, policy.returnDelayMs(121));
        assertEquals(HomeReturnPolicy.Target.ALL_APPS, policy.returned(121));
    }
    @Test void plainReturnGoesHome() {
        assertEquals(HomeReturnPolicy.Target.HOME, new HomeReturnPolicy().returned(100));
    }
    @Test void freshFreeformReturnsToGridOnce() {
        HomeReturnPolicy policy = new HomeReturnPolicy(); policy.freeformMove(100);
        assertEquals(HomeReturnPolicy.Target.ALL_APPS, policy.returned(220));
        assertEquals(HomeReturnPolicy.Target.HOME, policy.returned(500));
    }
    @Test void staleBroadcastCannotOpenGrid() {
        HomeReturnPolicy policy = new HomeReturnPolicy(); policy.freeformMove(100);
        assertEquals(HomeReturnPolicy.Target.HOME, policy.returned(1700));
    }
    @Test void repeatedBouncesStopInsteadOfTrappingUser() {
        HomeReturnPolicy policy = new HomeReturnPolicy();
        assertEquals(HomeReturnPolicy.Target.HOME, policy.returned(100));
        assertEquals(HomeReturnPolicy.Target.HOME, policy.returned(400));
        assertEquals(HomeReturnPolicy.Target.RECOVERY, policy.returned(700));
    }
    @Test void normalSpacedReturnsRemainAllowed() {
        HomeReturnPolicy policy = new HomeReturnPolicy();
        for (long time = 100; time < 20000; time += 2500)
            assertEquals(HomeReturnPolicy.Target.HOME, policy.returned(time));
    }
    @Test void explicitHomeClearsHintsAndCircuitBreaker() {
        HomeReturnPolicy policy = new HomeReturnPolicy();
        policy.returned(100); policy.returned(200); policy.returned(300); policy.freeformMove(310);
        policy.explicitNavigation();
        assertEquals(HomeReturnPolicy.Target.HOME, policy.returned(320));
    }
}
