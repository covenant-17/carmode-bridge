package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SidebarVisibilityTest {
    @Test void switchingBetweenHomeAndAppsKeepsASidebarVisible() {
        SidebarVisibility state = new SidebarVisibility(); Object home = new Object(), apps = new Object();
        assertFalse(state.visible()); state.shown(home); state.shown(apps);
        assertFalse(state.hidden(home)); assertTrue(state.visible());
        state.shown(home); assertFalse(state.hidden(apps)); assertTrue(state.visible());
        assertTrue(state.hidden(home)); assertFalse(state.visible());
    }
    @Test void duplicateResumeAndStopCallbacksDoNotCorruptTheCount() {
        SidebarVisibility state = new SidebarVisibility(); Object home = new Object();
        state.shown(home); state.shown(home);
        assertTrue(state.hidden(home)); assertFalse(state.hidden(home)); assertFalse(state.visible());
    }
    @Test void fullscreenDestinationRequestsDockOnlyWhenLastSidebarIsHidden() {
        SidebarVisibility state = new SidebarVisibility(); Object host = new Object();
        state.shown(host);
        assertFalse(DockPolicy.allowed(true, true, false, false, true, state.visible()));
        state.hidden(host);
        assertTrue(DockPolicy.allowed(true, true, false, false, true, state.visible()));
        assertFalse(DockPolicy.allowed(true, false, true, false, true, state.visible()));
        assertFalse(DockPolicy.allowed(true, true, false, true, true, state.visible()));
        assertFalse(DockPolicy.allowed(true, true, false, false, false, state.visible()));
        state.shown(host);
        assertFalse(DockPolicy.allowed(false, true, true, false, true, state.visible()));
    }
}
