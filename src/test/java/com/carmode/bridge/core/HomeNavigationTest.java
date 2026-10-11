package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HomeNavigationTest {
    @Test void homeStaysHome() {
        assertTrue(HomeNavigation.navigation(HomeNavigation.MAIN, true, null));
        assertEquals(HomeNavigation.MAIN, HomeNavigation.forwardAction(HomeNavigation.MAIN, null));
    }
    @Test void allAppsKeepsItsAction() {
        assertTrue(HomeNavigation.navigation(HomeNavigation.ALL_APPS, false, null));
        assertEquals(HomeNavigation.ALL_APPS, HomeNavigation.forwardAction(HomeNavigation.ALL_APPS, null));
    }
    @Test void legacyAllAppsPageMapsToOemAction() {
        assertTrue(HomeNavigation.navigation(HomeNavigation.MAIN, false, "allapps"));
        assertEquals(HomeNavigation.ALL_APPS, HomeNavigation.forwardAction(HomeNavigation.MAIN, "allapps"));
    }
    @Test void normalAppIconIsNotNavigation() {
        assertFalse(HomeNavigation.navigation(HomeNavigation.MAIN, false, null));
        assertFalse(HomeNavigation.navigation(HomeNavigation.MAIN, false, "settings"));
    }
    @Test void unknownActionsAndPagesDoNotBecomeAllApps() {
        assertFalse(HomeNavigation.navigation("android.intent.action.VIEW", false, "allapps"));
        assertFalse(HomeNavigation.navigation(null, false, null));
        assertEquals(HomeNavigation.MAIN, HomeNavigation.forwardAction(null, null));
        assertEquals(HomeNavigation.MAIN, HomeNavigation.forwardAction(HomeNavigation.MAIN, "ALLAPPS"));
    }
    @Test void homeCategoryDoesNotOverrideAllAppsPage() {
        assertEquals(HomeNavigation.ALL_APPS, HomeNavigation.forwardAction(HomeNavigation.MAIN, "allapps"));
        assertTrue(HomeNavigation.navigation(HomeNavigation.MAIN, true, "allapps"));
    }
}
