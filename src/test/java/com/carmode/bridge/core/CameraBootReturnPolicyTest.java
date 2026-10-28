package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraBootReturnPolicyTest {
    static final class Disk implements CameraBootReturnPolicy.Store {
        int boot = -1; boolean writable = true;
        public int lastBoot() { return boot; }
        public boolean saveBoot(int value) { if (!writable) return false; boot = value; return true; }
    }
    @Test void firstAppearanceClaimsOnceAndSurvivesObserverRecreation() {
        Disk disk = new Disk();
        assertTrue(CameraBootReturnPolicy.claim(true, false, 8, 43000, disk));
        assertFalse(CameraBootReturnPolicy.claim(true, false, 8, 44000, disk));
        assertFalse(CameraBootReturnPolicy.pending(true, false, 8, 50000, disk.lastBoot()));
    }
    @Test void aRealNewBootAllowsOneNewReturnButSleepAndProcessRestartsDoNot() {
        Disk disk = new Disk();
        assertTrue(CameraBootReturnPolicy.claim(true, false, 8, 43000, disk));
        assertFalse(CameraBootReturnPolicy.claim(true, false, 8, 70000, disk));
        assertTrue(CameraBootReturnPolicy.claim(true, false, 9, 1000, disk));
    }
    @Test void aManualCameraSelectionConsumesTheSameBootClaimWithoutRedirecting() {
        Disk disk = new Disk();
        assertTrue(CameraBootReturnPolicy.claim(true, false, 8, 30000, disk));
        assertFalse(CameraBootReturnPolicy.pending(true, false, 8, 43000, disk.lastBoot()));
    }
    @Test void disabledRecoveryAndUnknownBootNeverCauseHomeTakeover() {
        Disk disk = new Disk();
        assertFalse(CameraBootReturnPolicy.claim(false, false, 8, 43000, disk));
        assertFalse(CameraBootReturnPolicy.claim(true, true, 8, 43000, disk));
        assertFalse(CameraBootReturnPolicy.claim(true, false, -1, 43000, disk));
        assertEquals(-1, disk.lastBoot());
    }
    @Test void firstTwoMinutesOnly() {
        Disk disk = new Disk();
        assertFalse(CameraBootReturnPolicy.claim(true, false, 8, -1, disk));
        assertFalse(CameraBootReturnPolicy.claim(true, false, 8, 120000, disk));
        assertFalse(CameraBootReturnPolicy.claim(true, false, 8, 300000, disk));
        assertTrue(CameraBootReturnPolicy.claim(true, false, 8, 119999, disk));
    }
    @Test void failedPersistenceMustNotAuthorizeANavigationRequest() {
        Disk disk = new Disk(); disk.writable = false;
        assertFalse(CameraBootReturnPolicy.claim(true, false, 8, 43000, disk));
        assertEquals(-1, disk.lastBoot());
    }
}
