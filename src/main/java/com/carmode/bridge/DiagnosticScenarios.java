package com.carmode.bridge;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import com.carmode.bridge.core.DiagnosticTimeline;
import org.json.JSONObject;
import java.util.UUID;

/** Main-thread owner; survives Activity recreation and OEM foreground switches. */
final class DiagnosticScenarios {
    enum Kind {
        MODE(R.string.test_mode), NO_PRESS(R.string.test_no_press), OTHER(R.string.test_other),
        KEYBOARD(R.string.test_keyboard);
        final int title;
        Kind(int title) { this.title = title; }
    }
    private final CarModeApp app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String id, status = "", otherFilters = "";
    private Kind kind;
    private boolean requiresAccessibility;
    private int stage = -1;
    private long started, baselineKeys, previousTick;
    private TextView banner;
    DiagnosticScenarios(CarModeApp app) { this.app = app; }
    boolean active() { return id != null; }
    String status() { return status; }
    String otherFilters() { return otherFilters; }
    void startRecording(String reason) {
        app.log().startSession(reason, DiagnosticState.capture(app));
        app.prefs().set("diagnostics", true);
        refreshState();
    }
    void refreshState() {
        JSONObject state = DiagnosticState.capture(app);
        org.json.JSONArray filters = state.optJSONArray("other_key_filters");
        otherFilters = filters == null || filters.length() == 0 ? "" : filters.toString();
        app.log().record("diagnostic_state", "state", state);
        if (!otherFilters.isEmpty()) app.log().record("other_key_filter_warning", "services", filters, "conflict_proven", false);
    }
    void stopRecording(String reason) {
        cancel(reason);
        app.prefs().set("diagnostics", false);
        app.log().stopSession(reason, DiagnosticState.capture(app));
    }
    void start(Kind test) {
        if (active()) return;
        requiresAccessibility = test == Kind.KEYBOARD || !app.prefs().hcnMode();
        if ((requiresAccessibility ? ModeService.active() == null : ModeRuntimeService.active() == null) || !Settings.canDrawOverlays(app)) {
            Toast.makeText(app, I18n.text(app, R.string.test_permissions), Toast.LENGTH_LONG).show(); return;
        }
        startRecording("scenario");
        if (ModeService.active() != null) {
            ModeService.active().cancel("diagnostic_scenario"); ModeService.active().stopLearning();
        }
        if (ModeRuntimeService.active() != null) ModeRuntimeService.active().cancel("diagnostic_scenario");
        id = UUID.randomUUID().toString(); kind = test; stage = -1;
        started = previousTick = SystemClock.elapsedRealtime(); baselineKeys = app.log().keyCallbacks();
        JSONObject summary = new JSONObject();
        try {
            summary.put("scenario_id", id); summary.put("kind", kind.name()); summary.put("status", "running");
            summary.put("start_timestamp", System.currentTimeMillis()); summary.put("start_elapsed_ms", started);
            summary.put("state_at_start", DiagnosticState.capture(app)); summary.put("expected_presses", kind == Kind.NO_PRESS ? 0 : 3);
            if (kind == Kind.KEYBOARD) summary.put("expected_key_code", android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
            summary.put("end_timestamp", JSONObject.NULL);
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
        app.log().scenario(id, "countdown"); app.log().scenarioSummary(summary);
        app.log().record("scenario_started", "kind", kind.name(), "expected_presses", kind == Kind.NO_PRESS ? 0 : 3,
                "duration_ms", DiagnosticTimeline.DURATION_MS, "state", DiagnosticState.capture(app), "key_handling", "pass_through",
                "hcn_handling", "pass_through",
                "requires_accessibility", requiresAccessibility,
                "expected_key_code", kind == Kind.KEYBOARD ? android.view.KeyEvent.KEYCODE_DPAD_RIGHT : null);
        try {
            banner = new TextView(I18n.wrap(app)); banner.setTextColor(Color.WHITE); banner.setTextSize(20);
            banner.setBackgroundColor(0xee14283a); banner.setPadding(20, 10, 20, 10); banner.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; params.y = 12;
            app.getSystemService(WindowManager.class).addView(banner, params);
        } catch (RuntimeException e) {
            app.log().record("scenario_overlay_error", "error", e.getClass().getSimpleName());
            finish("overlay_failed"); return;
        }
        tick.run();
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active()) return;
            long now = SystemClock.elapsedRealtime(), elapsed = now - started;
            // Do not silently skip cues when the main thread or device was suspended.
            if (now - previousTick > 1500) { finish("timing_interrupted"); return; }
            previousTick = now;
            if (requiresAccessibility ? ModeService.active() == null : ModeRuntimeService.active() == null) {
                finish(requiresAccessibility ? "accessibility_disconnected" : "runtime_disconnected"); return;
            }
            if (!Settings.canDrawOverlays(app)) { finish("overlay_revoked"); return; }
            int next = DiagnosticTimeline.stage(elapsed);
            if (next != stage) {
                if (stage >= 0) app.log().record("scenario_phase_end", "phase", DiagnosticTimeline.phase(stage), "actual_elapsed_ms", elapsed);
                stage = next; app.log().scenario(id, DiagnosticTimeline.phase(stage));
                app.log().record("scenario_phase_start", "phase", DiagnosticTimeline.phase(stage), "actual_elapsed_ms", elapsed,
                        "requested_press", kind != Kind.NO_PRESS && stage >= 1 && stage <= 3);
            }
            if (stage == 5) { finish("completed"); return; }
            int seconds = DiagnosticTimeline.secondsRemaining(elapsed, stage);
            String title = I18n.text(app, kind.title);
            String cue = stage == 0 ? I18n.text(app, R.string.test_countdown, seconds)
                    : stage == 4 ? I18n.text(app, R.string.test_observe, seconds)
                    : kind == Kind.NO_PRESS ? I18n.text(app, R.string.test_keep_still, seconds)
                    : I18n.text(app, R.string.test_press, kind == Kind.MODE ? "MODE" : I18n.text(app,
                            kind == Kind.KEYBOARD ? R.string.test_keyboard_key : R.string.test_other_button), stage);
            status = title + "\n" + cue;
            if (!status.contentEquals(banner.getText())) banner.setText(status);
            handler.postDelayed(this, 100);
        }
    };
    void cancel(String reason) { if (active()) finish(reason); }
    void accessibilityUnavailable(String reason) { if (requiresAccessibility) cancel(reason); }
    private void finish(String reason) {
        handler.removeCallbacks(tick);
        long keys = app.log().keyCallbacks() - baselineKeys;
        if (stage >= 0 && stage < 5) app.log().record("scenario_phase_end", "phase", DiagnosticTimeline.phase(stage));
        app.log().endScenario(reason, keys);
        app.log().scenario(null, null); id = null;
        if (banner != null) {
            try { app.getSystemService(WindowManager.class).removeView(banner); }
            catch (RuntimeException e) { app.log().record("scenario_overlay_remove_error", "error", e.getClass().getSimpleName()); }
            banner = null;
        }
        status = I18n.text(app, "completed".equals(reason) ? R.string.test_completed : R.string.test_cancelled, keys);
        Toast.makeText(app, status, Toast.LENGTH_LONG).show();
    }
}
