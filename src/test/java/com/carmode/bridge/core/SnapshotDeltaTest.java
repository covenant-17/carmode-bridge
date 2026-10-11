package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotDeltaTest {
    private Map<String, String> nodes(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }
    @Test void firstSnapshotIsComplete() {
        SnapshotDelta.Change change = new SnapshotDelta().update("82", nodes("0", "root", "0/0", "selected"));
        assertTrue(change.reset); assertEquals(2, change.nodes.size()); assertFalse(change.isEmpty());
    }
    @Test void identicalPollsDoNotDuplicateTrees() {
        SnapshotDelta delta = new SnapshotDelta(); delta.update("82", nodes("0", "root"));
        assertTrue(delta.update("82", nodes("0", "root")).isEmpty());
    }
    @Test void selectionChangesOnlyIncludeAffectedNodes() {
        SnapshotDelta delta = new SnapshotDelta();
        delta.update("82", nodes("0", "root", "0/0", "selected", "0/1", "normal"));
        SnapshotDelta.Change change = delta.update("82", nodes("0", "root", "0/0", "normal", "0/1", "selected"));
        assertFalse(change.reset); assertEquals(nodes("0/0", "normal", "0/1", "selected"), change.nodes);
    }
    @Test void removalIsExplicit() {
        SnapshotDelta delta = new SnapshotDelta(); delta.update("82", nodes("0", "root", "0/0", "card"));
        SnapshotDelta.Change change = delta.update("82", nodes("0", "root"));
        assertEquals(java.util.Collections.singletonList("0/0"), change.removed); assertFalse(change.isEmpty());
    }
    @Test void newWindowOrRestartGetsFullBaseline() {
        SnapshotDelta delta = new SnapshotDelta(); delta.update("82", nodes("0", "root"));
        assertTrue(delta.update("87", nodes("0", "root")).reset);
        delta.clear(); assertTrue(delta.update("87", nodes("0", "root")).reset);
    }
    @Test void callerMutationCannotChangePreviousSnapshot() {
        SnapshotDelta delta = new SnapshotDelta(); Map<String, String> input = nodes("0", "before");
        delta.update("82", input); input.put("0", "after");
        assertEquals("after", delta.update("82", input).nodes.get("0"));
    }
}
