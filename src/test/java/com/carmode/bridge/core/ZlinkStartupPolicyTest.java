package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZlinkStartupPolicyTest {
    private static final class Disk implements ZlinkStartupPolicy.Store {
        String value = ""; int writes; boolean writable = true;
        public String last() { return value; }
        public boolean save(String epoch) { writes++; if (writable) value = epoch; return writable; }
    }
    @Test void recreationHomeAndBackCannotRepeatLaunchEvenAfterFailedLaunch() {
        Disk disk = new Disk(); String boot = ZlinkStartupPolicy.epoch(10);
        assertTrue(ZlinkStartupPolicy.claim(true, false, true, boot, disk));
        // A new policy invocation reads persisted state, not a process-local flag.
        for (int i = 0; i < 10; i++) assertFalse(ZlinkStartupPolicy.claim(true, false, true, boot, disk));
        assertEquals(1, disk.writes);
        assertTrue(ZlinkStartupPolicy.claim(true, false, true, ZlinkStartupPolicy.epoch(11), disk));
    }
    @Test void disabledRecoveryAndBackgroundDoNotConsumeBootAttempt() {
        Disk disk = new Disk();
        assertFalse(ZlinkStartupPolicy.claim(false, false, true, "boot:1", disk));
        assertFalse(ZlinkStartupPolicy.claim(true, true, true, "boot:1", disk));
        assertFalse(ZlinkStartupPolicy.claim(true, false, false, "boot:1", disk));
        assertEquals(0, disk.writes);
        assertTrue(ZlinkStartupPolicy.claim(true, false, true, "boot:1", disk));
    }
    @Test void unknownBootDoesNotCreateRetriesOnEveryProcessRestart() {
        Disk disk = new Disk();
        assertTrue(ZlinkStartupPolicy.claim(true, false, true, ZlinkStartupPolicy.epoch(-1), disk));
        assertFalse(ZlinkStartupPolicy.claim(true, false, true, ZlinkStartupPolicy.epoch(-1), disk));
        assertTrue(ZlinkStartupPolicy.claim(true, false, true, ZlinkStartupPolicy.epoch(42), disk));
    }
    @Test void storageFailurePreventsLaunch() {
        Disk disk = new Disk(); disk.writable = false;
        assertFalse(ZlinkStartupPolicy.claim(true, false, true, "boot:1", disk));
        assertEquals("", disk.value);
    }
    @Test void manualReenableDoesNotResetBootClaim() {
        Disk disk = new Disk();
        assertTrue(ZlinkStartupPolicy.claim(true, false, true, "boot:1", disk));
        assertFalse(ZlinkStartupPolicy.claim(false, false, true, "boot:1", disk));
        assertFalse(ZlinkStartupPolicy.claim(true, false, true, "boot:1", disk));
    }
}
