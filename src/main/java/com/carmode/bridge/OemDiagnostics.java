package com.carmode.bridge;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.carmode.bridge.core.SnapshotDelta;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Passive, bounded observation of the source carousel identified in the user's logs. */
final class OemDiagnostics {
    static final String TARGET = "com.hcn.autosource";
    private static final long POLL_MS = 250, WATCH_MS = 20000;
    private static final int MAX_NODES = 48, MAX_DEPTH = 8;
    private final AccessibilityService service;
    private final EventLog log;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SnapshotDelta delta = new SnapshotDelta();
    private AccessibilityNodeInfo root;
    private int windowId = -1;
    private long watchUntil, lastHeartbeat;
    private boolean unavailableReported;
    private String lastWindowSnapshot = "";
    private long lastWindowSnapshotAt;

    OemDiagnostics(AccessibilityService service, EventLog log) { this.service = service; this.log = log; }

    void event(AccessibilityEvent event) {
        if (!TARGET.contentEquals(String.valueOf(event.getPackageName()))) return;
        long now = SystemClock.uptimeMillis();
        watchUntil = now + WATCH_MS;
        if (windowId != event.getWindowId()) {
            discardRoot(); delta.clear(); unavailableReported = false;
            windowId = event.getWindowId(); lastHeartbeat = 0;
        }
        AccessibilityNodeInfo source = null;
        try {
            source = event.getSource();
            JSONObject metadata = source == null ? null : describe(source);
            log.record("oem_event", "package", TARGET, "event_type", event.getEventType(),
                    "event_name", AccessibilityEvent.eventTypeToString(event.getEventType()),
                    "event_time", event.getEventTime(), "window_id", event.getWindowId(),
                    "class", String.valueOf(event.getClassName()), "content_changes", event.getContentChangeTypes(),
                    "item_count", event.getItemCount(), "current_item_index", event.getCurrentItemIndex(),
                    "from_index", event.getFromIndex(), "to_index", event.getToIndex(),
                    "scroll_x", event.getScrollX(), "scroll_y", event.getScrollY(), "source_available", source != null, "source", metadata);
            if (source != null && TARGET.contentEquals(String.valueOf(source.getPackageName()))) {
                AccessibilityNodeInfo nextRoot = AccessibilityNodeInfo.obtain(source);
                for (int depth = 0; depth < MAX_DEPTH; depth++) {
                    AccessibilityNodeInfo parent = nextRoot.getParent();
                    if (parent == null) break;
                    if (!TARGET.contentEquals(String.valueOf(parent.getPackageName()))) { parent.recycle(); break; }
                    nextRoot.recycle(); nextRoot = parent;
                }
                discardRoot(); root = nextRoot;
            }
        } catch (RuntimeException | org.json.JSONException e) {
            log.record("oem_diagnostic_error", "error", e.getClass().getSimpleName());
        } finally { if (source != null) source.recycle(); }
        // Query inside the callback even if querying the event source failed.
        delayedSample("event_callback");
        handler.removeCallbacks(delayed50); handler.removeCallbacks(delayed150);
        handler.postDelayed(delayed50, 50); handler.postDelayed(delayed150, 150);
        if (!handler.hasCallbacks(poll)) handler.postDelayed(poll, POLL_MS);
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (SystemClock.uptimeMillis() >= watchUntil) { stop("watch_timeout"); return; }
            try { sample("poll"); }
            catch (RuntimeException | org.json.JSONException e) {
                log.record("oem_diagnostic_error", "error", e.getClass().getSimpleName());
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    private final Runnable delayed50 = () -> delayedSample("delayed_50ms");
    private final Runnable delayed150 = () -> delayedSample("delayed_150ms");
    private void delayedSample(String trigger) {
        try { if (watchUntil > 0) sample(trigger); }
        catch (RuntimeException | org.json.JSONException e) { log.record("oem_diagnostic_error", "error", e.getClass().getSimpleName()); }
    }

    /** Window inventory; raw titles/trees are inspected only for the identified OEM source. */
    JSONArray windows() {
        JSONArray result = new JSONArray();
        List<AccessibilityWindowInfo> windows = null;
        try {
            windows = service.getWindows();
            for (AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo node = null;
                JSONObject item = new JSONObject();
                try {
                    item.put("windowId", window.getId()); item.put("type", window.getType()); item.put("layer", window.getLayer());
                    Rect bounds = new Rect(); window.getBoundsInScreen(bounds); item.put("bounds", bounds.flattenToString());
                    item.put("focused", window.isFocused()); item.put("active", window.isActive());
                    node = window.getRoot();
                    String pkg = node == null ? null : String.valueOf(node.getPackageName());
                    boolean target = TARGET.equals(pkg) || (node == null && window.getId() == windowId);
                    item.put("package", pkg == null ? target ? TARGET : JSONObject.NULL : pkg);
                    item.put("package_evidence", node != null ? "root" : target ? "event_window_id" : "unknown");
                    item.put("root_available", node != null);
                    if (target) {
                        CharSequence title = window.getTitle();
                        item.put("title", title == null ? JSONObject.NULL : title.subSequence(0, Math.min(title.length(), 256)).toString());
                        item.put("title_truncated", title != null && title.length() > 256);
                        Map<String, String> nodes = new LinkedHashMap<>(); boolean[] truncated = {false};
                        if (node != null) collect(node, "0", 0, SystemClock.uptimeMillis() + 80, nodes, truncated);
                        item.put("accessible_element_count", nodes.size()); item.put("count_truncated", truncated[0]);
                        item.put("count_scope", "accessible_noneditable_tree_max48_depth8_80ms");
                    }
                } catch (RuntimeException e) {
                    item.put("error", e.getClass().getSimpleName());
                    log.record("window_query_error", "window_id", window.getId(), "error", e.getClass().getSimpleName());
                } finally { if (node != null) node.recycle(); }
                result.put(item);
            }
        } catch (RuntimeException | org.json.JSONException e) {
            log.record("windows_query_error", "error", e.getClass().getSimpleName());
        } finally { if (windows != null) for (AccessibilityWindowInfo window : windows) window.recycle(); }
        return result;
    }

    private void sample(String trigger) throws org.json.JSONException {
        JSONArray inventory = windows();
        String serialized = inventory.toString();
        long now = SystemClock.uptimeMillis();
        if (!"poll".equals(trigger) || !serialized.equals(lastWindowSnapshot) || now - lastWindowSnapshotAt >= 3000) {
            log.record("oem_windows", "trigger", trigger, "event_window_id", windowId, "windows", inventory);
            lastWindowSnapshot = serialized; lastWindowSnapshotAt = now;
        }
        // Query only the previously identified window, never traverse another application's tree.
        List<AccessibilityWindowInfo> windows = service.getWindows();
        try {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getId() != windowId) continue;
                AccessibilityNodeInfo current = window.getRoot();
                if (current == null) continue;
                if (TARGET.contentEquals(String.valueOf(current.getPackageName()))) { discardRoot(); root = current; }
                else current.recycle();
                break;
            }
        } finally { for (AccessibilityWindowInfo window : windows) window.recycle(); }
        if (root == null || !root.refresh() || !TARGET.contentEquals(String.valueOf(root.getPackageName()))) {
            discardRoot(); delta.clear();
            if (!unavailableReported) {
                log.record("oem_tree_unavailable", "window_id", windowId,
                        "reason", "no_accessible_root_or_window_closed");
                unavailableReported = true;
            }
            return;
        }
        unavailableReported = false;
        Map<String, String> nodes = new LinkedHashMap<>();
        boolean[] truncated = {false};
        collect(root, "0", 0, SystemClock.uptimeMillis() + 80, nodes, truncated);
        // Partial trees are independent samples, not reliable deltas against a complete tree.
        if (truncated[0]) delta.clear();
        SnapshotDelta.Change change = delta.update(Integer.toString(windowId), nodes);
        if (!change.isEmpty()) {
            JSONObject changed = new JSONObject();
            for (Map.Entry<String, String> node : change.nodes.entrySet()) changed.put(node.getKey(), new JSONObject(node.getValue()));
            log.record("oem_snapshot", "package", TARGET, "window_id", windowId, "reset", change.reset,
                    "nodes", changed, "removed", new JSONArray(change.removed), "node_count", nodes.size(),
                    "truncated", truncated[0], "trigger", trigger);
            lastHeartbeat = SystemClock.uptimeMillis();
        } else if (SystemClock.uptimeMillis() - lastHeartbeat >= 3000) {
            log.record("oem_snapshot_unchanged", "window_id", windowId, "node_count", nodes.size());
            lastHeartbeat = SystemClock.uptimeMillis();
        }
        if (truncated[0]) delta.clear();
    }

    private void collect(AccessibilityNodeInfo node, String path, int depth, long deadline,
                         Map<String, String> nodes, boolean[] truncated) throws org.json.JSONException {
        if (nodes.size() >= MAX_NODES || depth > MAX_DEPTH || SystemClock.uptimeMillis() > deadline) {
            truncated[0] = true; return;
        }
        if (!TARGET.contentEquals(String.valueOf(node.getPackageName()))) return;
        nodes.put(path, describe(node).toString());
        // Editable/password subtrees are intentionally excluded, including labels.
        if (node.isEditable() || node.isPassword()) return;
        for (int i = 0; i < node.getChildCount(); i++) {
            if (nodes.size() >= MAX_NODES || SystemClock.uptimeMillis() > deadline) { truncated[0] = true; break; }
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (child.refresh()) collect(child, path + "/" + i, depth + 1, deadline, nodes, truncated);
            } finally { child.recycle(); }
        }
    }

    private JSONObject describe(AccessibilityNodeInfo node) throws org.json.JSONException {
        if (!TARGET.contentEquals(String.valueOf(node.getPackageName()))) return new JSONObject();
        JSONObject info = new JSONObject();
        info.put("view_id", node.getViewIdResourceName()); info.put("class", String.valueOf(node.getClassName()));
        info.put("selected", node.isSelected()); info.put("focused", node.isFocused());
        info.put("accessibility_focused", node.isAccessibilityFocused()); info.put("checked", node.isChecked());
        info.put("visible", node.isVisibleToUser()); info.put("enabled", node.isEnabled());
        info.put("clickable", node.isClickable()); info.put("child_count", node.getChildCount());
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds); info.put("bounds", bounds.flattenToString());
        if (!node.isEditable() && !node.isPassword()) {
            String label = knownLabel(node.getText());
            if (label == null) label = knownLabel(node.getContentDescription());
            if (label != null) info.put("menu_label", label);
        }
        return info;
    }

    private String knownLabel(CharSequence value) {
        if (value == null || value.length() > 32) return null;
        switch (value.toString().trim().toLowerCase(Locale.ROOT)) {
            case "radio": return "RADIO";
            case "bluetooth": case "blue tooth": return "BLUETOOTH";
            case "a2dp": return "A2DP";
            case "music": return "MUSIC";
            case "video": return "VIDEO";
            default: return null; // No arbitrary UI text goes into the export.
        }
    }

    void stop(String reason) {
        handler.removeCallbacks(poll);
        handler.removeCallbacks(delayed50); handler.removeCallbacks(delayed150);
        if (watchUntil != 0) log.record("oem_watch_stopped", "window_id", windowId, "reason", reason);
        discardRoot(); delta.clear(); windowId = -1; watchUntil = 0;
        lastHeartbeat = 0; unavailableReported = false;
        lastWindowSnapshot = ""; lastWindowSnapshotAt = 0;
    }
    private void discardRoot() { if (root != null) { root.recycle(); root = null; } }
}
