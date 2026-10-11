package com.carmode.bridge.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Emits changed node metadata only, so an animated OEM window cannot bury the log. */
public final class SnapshotDelta {
    private String identity;
    private Map<String, String> previous = new LinkedHashMap<>();

    public static final class Change {
        public final boolean reset;
        public final Map<String, String> nodes = new LinkedHashMap<>();
        public final List<String> removed = new ArrayList<>();
        private Change(boolean reset) { this.reset = reset; }
        public boolean isEmpty() { return !reset && nodes.isEmpty() && removed.isEmpty(); }
    }

    public Change update(String window, Map<String, String> current) {
        Change change = new Change(!Objects.equals(identity, window));
        for (Map.Entry<String, String> node : current.entrySet()) {
            if (change.reset || !Objects.equals(previous.get(node.getKey()), node.getValue())) {
                change.nodes.put(node.getKey(), node.getValue());
            }
        }
        if (!change.reset) for (String path : previous.keySet()) {
            if (!current.containsKey(path)) change.removed.add(path);
        }
        identity = window;
        previous = new LinkedHashMap<>(current);
        return change;
    }

    public void clear() { identity = null; previous.clear(); }
}
