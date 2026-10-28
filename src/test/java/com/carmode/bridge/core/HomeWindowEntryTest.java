package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HomeWindowEntryTest {
    @Test void eachHomeEntryRequestsOneWindowWithoutABootClaim() {
        HomeWindowEntry policy = new HomeWindowEntry();
        policy.shown(true, false); assertTrue(policy.take(true)); assertFalse(policy.take(true));
        policy.shown(true, false); assertTrue(policy.take(true));
    }
    @Test void preparingHomeUnderFullscreenZlinkDoesNotCompeteWithIt() {
        HomeWindowEntry policy = new HomeWindowEntry();
        policy.shown(true, true); assertFalse(policy.take(true));
    }
    @Test void appsPageAndDisabledWindowModeNeverRequestAutoLaunch() {
        HomeWindowEntry policy = new HomeWindowEntry();
        policy.shown(false, false); assertFalse(policy.take(true));
        policy.shown(true, false); assertFalse(policy.take(false)); assertFalse(policy.take(true));
    }
    @Test void backOrUserNavigationCanCancelBeforeDispatch() {
        HomeWindowEntry policy = new HomeWindowEntry();
        policy.shown(true, false); policy.cancel(); assertFalse(policy.take(true));
    }
}
