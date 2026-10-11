package com.carmode.bridge.core;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import com.carmode.bridge.core.ActionDispatcher.*;

class ActionDispatcherTest {
    @Test void everySelectionLaunchesExactlyOnceWithoutAutomation() {
        List<Action> launches = new ArrayList<>();
        ActionDispatcher dispatcher = new ActionDispatcher(launches::add);
        for (Action action : Action.values()) dispatcher.select(action);
        assertEquals(Arrays.asList(Action.values()), launches);
    }
    @Test void defaultsMatchSuppliedMacros() {
        assertEquals("ivicar.cn.ivicaravm", ActionDispatcher.target(Action.CAMERAS, ""));
        assertEquals("com.zjinnova.zlink", ActionDispatcher.target(Action.MUSIC, ""));
        assertEquals("com.zjinnova.zlink", ActionDispatcher.target(Action.NAVIGATION, ""));
    }
    @Test void explicitAssignmentsArePreservedForEveryCard() {
        for (Action action : Action.values())
            assertEquals("custom.app/.Activity", ActionDispatcher.target(action, "custom.app/.Activity"));
    }
    @Test void emptyAssignmentsUseDefaultAndInputIsTrimmed() {
        assertEquals("ivicar.cn.ivicaravm", ActionDispatcher.target(Action.CAMERAS, null));
        assertEquals("com.zjinnova.zlink", ActionDispatcher.target(Action.MUSIC, "  "));
        assertEquals("custom.app/.Activity", ActionDispatcher.target(Action.MUSIC, " custom.app/.Activity "));
    }
}
