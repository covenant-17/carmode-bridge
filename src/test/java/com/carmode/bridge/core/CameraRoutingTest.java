package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraRoutingTest {
    @Test void defaultAndExplicitCameraAssignmentsAreRecognized() {
        assertTrue(CameraRouting.isTarget("ivicar.cn.ivicaravm", ""));
        assertTrue(CameraRouting.isTarget("ivicar.cn.ivicaravm", null));
        assertTrue(CameraRouting.isTarget("custom.camera", " custom.camera/.Main "));
        assertTrue(CameraRouting.isTarget("custom.camera", "custom.camera"));
    }
    @Test void explicitCameraAssignmentReplacesTheDefault() {
        assertFalse(CameraRouting.isTarget("ivicar.cn.ivicaravm", "custom.camera/.Main"));
        assertFalse(CameraRouting.isTarget("com.zjinnova.zlink", ""));
        assertFalse(CameraRouting.isTarget(null, ""));
        assertFalse(CameraRouting.isTarget("", ""));
    }
    @Test void homeAndWindowModeAreRequiredButZlinkHomeOnlyIsIndependent() {
        assertTrue(CameraRouting.window(true, true, false, "ivicar.cn.ivicaravm", ""));
        assertFalse(CameraRouting.window(false, true, false, "ivicar.cn.ivicaravm", ""));
        assertFalse(CameraRouting.window(true, false, false, "ivicar.cn.ivicaravm", ""));
        assertFalse(CameraRouting.window(true, true, true, "ivicar.cn.ivicaravm", ""));
        assertFalse(CameraRouting.window(true, true, false, "other", ""));
    }
}
