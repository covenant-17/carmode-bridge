package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryAppRankTest {
    @Test void factoryAndCarSettingsComeFirst() {
        assertEquals(0, FactoryAppRank.rank("Factory", "vendor.app"));
        assertEquals(0, FactoryAppRank.rank("Car Settings", "vendor.app"));
        assertEquals(0, FactoryAppRank.rank("Vendor", "com.vendor.carsettings"));
    }
    @Test void generalSettingsAreOnlyCandidates() {
        assertEquals(1, FactoryAppRank.rank("Settings", "com.android.settings"));
        assertEquals(1, FactoryAppRank.rank("Настройки", "vendor.app"));
    }
    @Test void unknownAppsRemainAvailableAfterCandidates() {
        assertEquals(2, FactoryAppRank.rank("OEM Control", "vendor.control"));
        assertEquals(2, FactoryAppRank.rank("Music", "vendor.player"));
    }
    @Test void rankingIsCaseInsensitive() {
        assertEquals(0, FactoryAppRank.rank("FACTORY", "VENDOR.APP"));
        assertEquals(1, FactoryAppRank.rank("SETTINGS", "VENDOR.APP"));
    }
}
