package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyGateTest {
    @Test void holdNeverAdvancesAgain() {
        KeyGate gate = new KeyGate(); assertTrue(gate.down(0));
        assertFalse(gate.down(1)); assertFalse(gate.down(2)); assertFalse(gate.down(0));
        gate.up(); assertTrue(gate.down(0));
    }
    @Test void orphanedRepeatIsIgnored() { assertFalse(new KeyGate().down(3)); }
    @Test void resetAllowsNextFreshPress() {
        KeyGate gate = new KeyGate(); gate.down(0); gate.reset(); assertTrue(gate.down(0));
    }
}
