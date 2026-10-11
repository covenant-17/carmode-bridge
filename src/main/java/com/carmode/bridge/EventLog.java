package com.carmode.bridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.UUID;

/** Bounded JSONL plus independently persisted summaries that survive log rotation. */
public final class EventLog {
    private final Context context;
    private final File current, previous;
    private final SharedPreferences metadata;
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    private JSONArray sessions;
    private JSONObject session;
    private String scenarioId, phase, error = "";
    private long keyCallbacks, evicted, writeFailures, permissionErrors, trimmedSessions, lastCheckpoint;
    EventLog(Context context) {
        this.context = context;
        current = new File(context.getFilesDir(), "events.jsonl");
        previous = new File(context.getFilesDir(), "events.previous.jsonl");
        metadata = context.getSharedPreferences("diagnostic_metadata", Context.MODE_PRIVATE);
        evicted = metadata.getLong("evicted", 0); writeFailures = metadata.getLong("write_failures", 0);
        permissionErrors = metadata.getLong("permission_errors", 0); trimmedSessions = metadata.getLong("trimmed_sessions", 0);
        try {
            sessions = new JSONArray(metadata.getString("sessions", "[]"));
            for (int i = 0; i < sessions.length(); i++) {
                JSONObject old = sessions.getJSONObject(i);
                if ("recording".equals(old.optString("status"))) {
                    old.put("status", "interrupted"); old.put("end_timestamp", JSONObject.NULL);
                    old.put("interruption_detected_at", System.currentTimeMillis());
                    JSONArray tests = old.optJSONArray("scenarios");
                    if (tests != null) for (int j = 0; j < tests.length(); j++) {
                        JSONObject test = tests.getJSONObject(j);
                        if ("running".equals(test.optString("status"))) test.put("status", "interrupted");
                    }
                }
            }
        } catch (Exception e) { sessions = new JSONArray(); error = "SessionMetadataUnreadable"; }
        persist();
    }
    private void persist() {
        lastCheckpoint = SystemClock.elapsedRealtime();
        metadata.edit().putString("sessions", sessions.toString()).putLong("evicted", evicted)
                .putLong("write_failures", writeFailures).putLong("permission_errors", permissionErrors)
                .putLong("trimmed_sessions", trimmedSessions).apply();
    }
    public synchronized void startSession(String reason, JSONObject state) {
        if (session != null) return;
        try {
            if (sessions.length() >= 32) { sessions.remove(0); trimmedSessions++; }
            session = new JSONObject(); session.put("session_id", UUID.randomUUID().toString());
            session.put("start_timestamp", System.currentTimeMillis()); session.put("start_elapsed_ms", SystemClock.elapsedRealtime());
            session.put("end_timestamp", JSONObject.NULL); session.put("status", "recording");
            session.put("start_state", state); session.put("key_callback_count", 0); session.put("scenarios", new JSONArray());
            session.put("on_key_event_called", false);
            sessions.put(session);
            record("diagnostics_started", "reason", reason, "state", state);
            persist();
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
    }
    public synchronized void stopSession(String reason, JSONObject state) {
        if (session == null) return;
        record("diagnostics_stopped", "reason", reason, "state", state);
        try {
            session.put("end_timestamp", System.currentTimeMillis()); session.put("end_elapsed_ms", SystemClock.elapsedRealtime());
            session.put("status", "stopped"); session.put("end_state", state);
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
        session = null; scenarioId = null; phase = null; persist();
    }
    synchronized void scenario(String id, String stage) { scenarioId = id; phase = stage; }
    synchronized void scenarioSummary(JSONObject summary) {
        if (session == null) return;
        JSONArray tests = session.optJSONArray("scenarios");
        if (tests.length() >= 64) {
            tests.remove(0);
            try { session.put("trimmed_scenario_summaries", session.optInt("trimmed_scenario_summaries") + 1); }
            catch (org.json.JSONException e) { throw new IllegalStateException(e); }
        }
        tests.put(summary); persist();
    }
    synchronized void endScenario(String reason, long keys) {
        if (session != null) try {
            JSONArray tests = session.getJSONArray("scenarios");
            for (int i = 0; i < tests.length(); i++) {
                JSONObject test = tests.getJSONObject(i);
                if (!test.optString("scenario_id").equals(scenarioId)) continue;
                test.put("status", reason); test.put("end_timestamp", System.currentTimeMillis());
                test.put("end_elapsed_ms", SystemClock.elapsedRealtime()); test.put("key_callback_count", keys);
                test.put("on_key_event_called", keys > 0); test.put("physical_press_count", JSONObject.NULL);
            }
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
        record("scenario_ended", "reason", reason, "key_callback_count", keys, "on_key_event_called", keys > 0);
        persist();
    }
    public synchronized long keyCallbacks() { return keyCallbacks; }
    public synchronized void record(String type, Object... fields) {
        try {
            JSONObject event = new JSONObject();
            event.put("timestamp", System.currentTimeMillis()); event.put("elapsed_ms", SystemClock.elapsedRealtime());
            event.put("type", type); event.put("session_id", session == null ? JSONObject.NULL : session.getString("session_id"));
            event.put("recording_active", session != null); event.put("scenario_id", scenarioId == null ? JSONObject.NULL : scenarioId);
            event.put("scenario_phase", phase == null ? JSONObject.NULL : phase);
            for (int i = 0; i + 1 < fields.length; i += 2) event.put(String.valueOf(fields[i]), fields[i + 1] == null ? JSONObject.NULL : fields[i + 1]);
            if ("key".equals(type)) {
                keyCallbacks++; event.put("callback_number_process", keyCallbacks);
                if (session != null) {
                    session.put("key_callback_count", session.optLong("key_callback_count") + 1);
                    session.put("on_key_event_called", true);
                }
            }
            if ("SecurityException".equals(event.optString("error"))) permissionErrors++;
            if (session != null) session.put("last_observed_timestamp", System.currentTimeMillis());
            String line = event.toString();
            recent.addLast(line); while (recent.size() > 30) recent.removeFirst();
            if (current.length() >= 256 * 1024) {
                long removed = countLines(previous);
                if (previous.exists() && !previous.delete()) throw new IOException("Cannot rotate old log");
                evicted += removed;
                if (!current.renameTo(previous)) throw new IOException("Cannot rotate current log");
            }
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(current, true), StandardCharsets.UTF_8)) {
                writer.write(line); writer.write('\n');
            }
            error = "";
        } catch (Exception e) { error = e.getClass().getSimpleName(); writeFailures++; }
        // Bound checkpoint work on the Accessibility thread; explicit boundaries flush separately.
        if (SystemClock.elapsedRealtime() - lastCheckpoint >= 1000) persist();
    }
    private long countLines(File file) throws IOException {
        if (!file.exists()) return 0;
        long count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) count++;
        }
        return count;
    }
    public synchronized String recent() {
        StringBuilder out = new StringBuilder(error.isEmpty() ? "" : I18n.text(context, R.string.log_write_error, error));
        for (String line : recent) out.append('\n').append(line);
        return out.length() == 0 ? I18n.text(context, R.string.no_events) : out.toString();
    }
    /** Main thread cancels any scenario first; an active recording continues in a new session. */
    public synchronized void clear() throws IOException {
        if (scenarioId != null) throw new IOException("A new test started; cancel it before clearing");
        boolean recording = session != null;
        for (File file : new File[]{previous, current, new File(context.getCacheDir(), "diagnostics.json")}) {
            if (file.exists() && !file.delete()) throw new IOException("Cannot clear " + file.getName());
        }
        recent.clear(); error = ""; sessions = new JSONArray(); session = null; scenarioId = null; phase = null;
        evicted = writeFailures = permissionErrors = trimmedSessions = 0; persist();
        if (recording) startSession("logs_cleared", DiagnosticState.capture(context));
    }
    public synchronized File export(Prefs prefs) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema_version", 2); root.put("exported_at", System.currentTimeMillis());
        root.put("boot_history", BootEvidence.history(context));
        root.put("manufacturer", Build.MANUFACTURER); root.put("model", Build.MODEL);
        root.put("sdk", Build.VERSION.SDK_INT); root.put("build", Build.DISPLAY);
        root.put("app_version", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
        root.put("app_version_code", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode());
        root.put("diagnostic_profile", "oem_source_v2");
        try {
            String crash = CrashReporter.store(context).read();
            if (!crash.isEmpty()) root.put("last_crash_report", crash);
        } catch (IOException e) { root.put("crash_report_read_error", e.getClass().getSimpleName()); }
        root.put("settings", new JSONObject(prefs.data.getAll())); root.put("settings_captured_at", "export");
        root.put("state_at_export", DiagnosticState.capture(context)); root.put("sessions", sessions);
        root.put("recording_active", session != null); root.put("log_error", error);
        root.put("key_callback_count_process", keyCallbacks);
        root.put("on_key_event_called_process", keyCallbacks > 0);
        JSONArray events = new JSONArray();
        long malformed = read(previous, events) + read(current, events);
        JSONObject completeness = new JSONObject();
        completeness.put("counter_scope", "since_last_clear_or_v2_upgrade");
        completeness.put("evicted_events", evicted); completeness.put("write_failures", writeFailures);
        completeness.put("malformed_retained_lines", malformed); completeness.put("lost_events", evicted + writeFailures + malformed);
        completeness.put("lost_events_semantics", "detected_loss_estimate; partial_write_can_overlap_malformed_line");
        completeness.put("permission_errors", permissionErrors); completeness.put("trimmed_session_summaries", trimmedSessions);
        completeness.put("undelivered_android_events", "unknown");
        completeness.put("persistence", "async_checkpoint; abrupt_process_death_may_lose_last_writes");
        root.put("completeness", completeness); root.put("events", events);
        persist();
        File file = new File(context.getCacheDir(), "diagnostics.json");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) { writer.write(root.toString(2)); }
        return file;
    }
    private long read(File file, JSONArray events) throws IOException {
        if (!file.exists()) return 0;
        long malformed = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try { events.put(new JSONObject(line)); } catch (org.json.JSONException ignored) { malformed++; }
            }
        }
        return malformed;
    }
}
