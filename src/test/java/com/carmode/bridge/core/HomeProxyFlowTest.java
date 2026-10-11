package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HomeProxyFlowTest {
    static class Fake implements HomeProxyFlow.Port {
        final List<String> calls = new ArrayList<>();
        boolean runtimeFails, forwardFails;
        public void startRuntime() { calls.add("runtime"); if (runtimeFails) throw new IllegalStateException(); }
        public void forwardOem() { calls.add("oem"); if (forwardFails) throw new IllegalStateException(); }
        public void error(String stage, RuntimeException error) { calls.add("error:" + stage); }
        public void recovery() { calls.add("recovery"); }
        public void retainHome() { calls.add("retain"); }
    }
    @Test void startsRuntimeBeforeForwarding() {
        Fake port = new Fake(); HomeProxyFlow.run(true, false, port);
        assertEquals(Arrays.asList("runtime", "oem", "retain"), port.calls);
    }
    @Test void honorsAutoStartOff() {
        Fake port = new Fake(); HomeProxyFlow.run(false, false, port);
        assertEquals(Arrays.asList("oem", "retain"), port.calls);
    }
    @Test void alreadyRunningServiceIsReusedWithoutRestart() {
        Fake port = new Fake(); HomeProxyFlow.run(true, false, true, port);
        assertEquals(Arrays.asList("oem", "retain"), port.calls);
    }
    @Test void runningRuntimeDoesNotHideForwardingFailure() {
        Fake port = new Fake(); port.forwardFails = true; HomeProxyFlow.run(true, false, true, port);
        assertEquals(Arrays.asList("oem", "error:forward", "recovery"), port.calls);
    }
    @Test void crashGateStillAllowsOriginalHome() {
        Fake port = new Fake(); HomeProxyFlow.run(true, true, port);
        assertEquals(Arrays.asList("oem", "retain"), port.calls);
    }
    @Test void runtimeFailureDoesNotHideHome() {
        Fake port = new Fake(); port.runtimeFails = true; HomeProxyFlow.run(true, false, port);
        assertEquals(Arrays.asList("runtime", "error:runtime", "oem", "retain"), port.calls);
    }
    @Test void missingOemLeavesRecoveryVisibleWithoutRetry() {
        Fake port = new Fake(); port.forwardFails = true; HomeProxyFlow.run(true, false, port);
        assertEquals(Arrays.asList("runtime", "oem", "error:forward", "recovery"), port.calls);
    }
    @Test void recoveryAlsoAvailableWithRuntimeDisabled() {
        Fake port = new Fake(); port.forwardFails = true; HomeProxyFlow.run(false, true, port);
        assertEquals(Arrays.asList("oem", "error:forward", "recovery"), port.calls);
    }
}
