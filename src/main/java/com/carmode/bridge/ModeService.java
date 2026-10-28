package com.carmode.bridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import org.json.JSONArray;
import org.json.JSONObject;
import com.carmode.bridge.core.KeyGate;
import com.carmode.bridge.core.HcnModeRouter;

public final class ModeService extends AccessibilityService {
    private static volatile ModeService instance;
    private CarModeApp app;
    private MenuController menu;
    private OemDiagnostics oem;
    private ZlinkWindowGuard zlinkGuard;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable learningExpired = this::updateDiagnosticState;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsChanged = (prefs, key) -> {
        if ("diagnostics".equals(key)) updateDiagnosticState();
        if ("hcn_mode".equals(key) || "enabled".equals(key)) cancel("input_settings_changed");
        if (zlinkGuard != null && ("camry_home".equals(key) || "camry_zlink_window".equals(key)
                || "camry_zlink_home_only".equals(key) || "camry_zlink_app".equals(key))) zlinkGuard.reset();
    };
    private final KeyGate gate = new KeyGate();
    private long learnUntil;
    private int candidate = -1;
    private boolean capturedPress;
    private int capturedCode = -1;
    private long capturedDownTime;
    private boolean receiverRegistered;
    private String previousPackage, previousClass;
    private long previousTransition;
    private boolean observing;
    public static ModeService active() { return instance; }
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            app.log().record("screen", "intent_action", intent.getAction());
            if (diagnosticActive() && Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) {
                String reason = intent.getStringExtra("reason");
                app.log().record("system_dialogs_closed", "reason", reason == null ? "unknown" : reason.substring(0, Math.min(reason.length(), 64)));
            }
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) cancel("screen_off");
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) app.diagnostics().cancel("screen_off");
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) && oem != null) oem.stop("screen_off");
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) cancel("system_dialogs_closed");
        }
    };
    @Override public void onCreate() {
        super.onCreate(); app = CarModeApp.from(this); ServiceEvidence.record(this, "accessibility_created");
    }
    @Override protected void onServiceConnected() {
        if (app.recovering()) { instance = this; return; }
        if (menu != null) menu.cancel("service_reconnected");
        if (oem != null) oem.stop("service_reconnected");
        if (zlinkGuard != null) zlinkGuard.close();
        instance = this; app = CarModeApp.from(this); menu = new MenuController(this);
        oem = new OemDiagnostics(this, app.log());
        zlinkGuard = new ZlinkWindowGuard(this); zlinkGuard.inspect();
        app.prefs().data.registerOnSharedPreferenceChangeListener(settingsChanged);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        if (!receiverRegistered) { registerReceiver(screen, filter); receiverRegistered = true; }
        app.log().record("service_connected", "ready", app.ready(), "key_code", app.prefs().keyCode());
        ServiceEvidence.record(this, "accessibility_connected");
        ModeRuntimeService.sync(this, "accessibility_connected");
        updateDiagnosticState();
    }
    public void learn() {
        if (ModeRuntimeService.active() != null) ModeRuntimeService.active().cancel("learning");
        cancel("learning"); candidate = -1; learnUntil = SystemClock.elapsedRealtime() + 30000;
        app.log().record("learning_started", "duration_ms", 30000);
        handler.removeCallbacks(learningExpired);
        handler.postDelayed(learningExpired, 30000);
        updateDiagnosticState();
    }
    public boolean learning() { return SystemClock.elapsedRealtime() < learnUntil; }
    public int candidate() { return candidate; }
    public void stopLearning() {
        learnUntil = 0; candidate = -1;
        handler.removeCallbacks(learningExpired);
        updateDiagnosticState();
    }
    private boolean diagnosticActive() { return app != null && !app.recovering() && (app.prefs().diagnostics() || learning()); }
    public void resumeAfterCrash() { if (menu == null) onServiceConnected(); }
    private void updateDiagnosticState() {
        if (app == null || oem == null) return;
        boolean active = diagnosticActive();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
            if (active) info.eventTypes |= AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | AccessibilityEvent.TYPE_VIEW_SELECTED
                    | AccessibilityEvent.TYPE_VIEW_FOCUSED | AccessibilityEvent.TYPE_VIEW_SCROLLED | AccessibilityEvent.TYPE_VIEW_CLICKED;
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            setServiceInfo(info);
        }
        if (active != observing) { previousPackage = null; previousClass = null; previousTransition = 0; observing = active; }
        app.diagnostics().refreshState();
        if (!active) { oem.stop("diagnostics_and_learning_off"); return; }
        JSONArray enabledServices = new JSONArray();
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        try {
            if (manager != null) for (AccessibilityServiceInfo other : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                JSONObject item = new JSONObject(); item.put("id", other.getId()); item.put("flags", other.flags);
                item.put("requests_key_filter", (other.flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0);
                enabledServices.put(item);
            }
        } catch (org.json.JSONException | RuntimeException e) {
            app.log().record("service_info_error", "error", e.getClass().getSimpleName());
        }
        app.log().record("diagnostic_profile", "profile", "oem_source_v2", "target_package", OemDiagnostics.TARGET,
                "poll_interval_ms", 250, "watch_ms", 20000, "event_types", info == null ? 0 : info.eventTypes,
                "service_flags", info == null ? 0 : info.flags, "enabled_services", enabledServices);
    }
    public void confirmKey(int code) {
        cancel("key_changed"); app.prefs().setKeyCode(code); stopLearning();
        app.log().record("mode_key_confirmed", "key_code", code);
    }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (app == null || app.recovering()) return false;
        boolean consumed = false;
        try {
            consumed = handleKey(event);
            return consumed;
        } catch (RuntimeException e) {
            app.log().record("key_handler_error", "error", e.getClass().getSimpleName());
            return false;
        } finally {
            // Log every callback, irrespective of assignment, learning or recording state.
            app.log().record("key", "keyCode", event.getKeyCode(), "scanCode", event.getScanCode(),
                    "deviceId", event.getDeviceId(), "source", event.getSource(), "action", event.getAction(),
                    "repeatCount", event.getRepeatCount(), "eventTime", event.getEventTime(), "consumed", consumed);
        }
    }
    private boolean handleKey(KeyEvent event) {
        boolean learning = learning();
        // Finish a pair already consumed, even if settings changed while the button was held.
        if (capturedPress && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                && event.getDownTime() != capturedDownTime) {
            capturedPress = false; capturedCode = -1; gate.reset();
        }
        if (capturedPress && event.getKeyCode() == capturedCode) {
            if (event.getAction() == KeyEvent.ACTION_UP) { capturedPress = false; capturedCode = -1; gate.up(); }
            return true;
        }
        if (app.diagnostics().active() || menu == null) return false;
        if (learning) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) candidate = event.getKeyCode();
            return false;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN
                && (app.prefs().hcnMode() || !app.ready() || event.getKeyCode() != app.prefs().keyCode())) menu.cancel("system_back");
        if (!HcnModeRouter.useLearnedKey(app.prefs().hcnMode()) || !app.ready() || event.getKeyCode() != app.prefs().keyCode()) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) { gate.up(); return false; }
        if (event.getAction() == KeyEvent.ACTION_DOWN && gate.down(event.getRepeatCount())) {
            if (menu.press("STEERING_WHEEL")) {
                capturedPress = true; capturedCode = event.getKeyCode(); capturedDownTime = event.getDownTime(); return true;
            }
            gate.reset();
        }
        return false;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (zlinkGuard != null) zlinkGuard.event(event);
        if (!diagnosticActive()) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            app.log().record("window", "event_type", event.getEventType(), "window_id", event.getWindowId(),
                    "package", String.valueOf(event.getPackageName()), "class", String.valueOf(event.getClassName()),
                    "window_changes", event.getWindowChanges());
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) transition(event);
        }
        if (oem != null) oem.event(event);
    }
    private void transition(AccessibilityEvent event) {
        String pkg = event.getPackageName() == null ? null : event.getPackageName().toString();
        String cls = event.getClassName() == null ? null : event.getClassName().toString();
        if (pkg == null || (pkg.equals(previousPackage) && java.util.Objects.equals(cls, previousClass))) return;
        String activity = null;
        if (cls != null) try {
            activity = getPackageManager().getActivityInfo(new android.content.ComponentName(pkg, cls), 0).name;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            // RelativeLayout, dialogs and OEM overlays are not Activities.
        } catch (RuntimeException e) { app.log().record("activity_query_error", "error", e.getClass().getSimpleName()); }
        long now = SystemClock.elapsedRealtime();
        app.log().record("window_transition", "previous_package", previousPackage, "package", pkg,
                "previous_class", previousClass, "observed_class", cls, "current_activity", activity,
                "activity_evidence", activity == null ? "unavailable_from_event" : "event_class_resolved_by_package_manager",
                "since_previous_ms", previousTransition == 0 ? null : now - previousTransition,
                "windows", oem == null ? null : oem.windows());
        previousPackage = pkg; previousClass = cls; previousTransition = now;
    }
    public void simulate() { menu.press("TOUCH"); }
    public String hcnStatus(Context context) {
        return ModeRuntimeService.active() == null ? context.getString(R.string.hcn_unavailable) : ModeRuntimeService.active().hcnStatus(context);
    }
    public JSONObject hcnSnapshot() throws org.json.JSONException {
        return ModeRuntimeService.active() == null ? new JSONObject().put("receiver_registered", false) : ModeRuntimeService.active().hcnSnapshot();
    }
    public void cancel(String reason) {
        if (menu != null) menu.cancel(reason);
        gate.reset();
    }
    @Override public void onInterrupt() {
        ServiceEvidence.record(this, "accessibility_interrupted");
        if (app != null && !app.recovering()) app.diagnostics().accessibilityUnavailable("service_interrupted");
        cancel("service_interrupted");
        if (oem != null) oem.stop("service_interrupted");
    }
    @Override public boolean onUnbind(Intent intent) {
        if (zlinkGuard != null) zlinkGuard.close();
        if (instance == this) instance = null;
        ServiceEvidence.record(this, "accessibility_unbound");
        cancel("accessibility_unbound"); handler.removeCallbacks(learningExpired); learnUntil = 0;
        if (oem != null) oem.stop("accessibility_unbound");
        if (!app.recovering()) app.diagnostics().accessibilityUnavailable("accessibility_unbound");
        return super.onUnbind(intent); // System owns rebind; never toggle the user's permission.
    }
    @Override public void onDestroy() {
        if (zlinkGuard != null) zlinkGuard.close();
        if (app != null && !app.recovering()) app.diagnostics().accessibilityUnavailable("service_destroyed");
        cancel("service_destroyed");
        handler.removeCallbacks(learningExpired);
        if (oem != null) oem.stop("service_destroyed");
        if (app != null && !app.recovering()) app.prefs().data.unregisterOnSharedPreferenceChangeListener(settingsChanged);
        if (receiverRegistered) unregisterReceiver(screen);
        if (app != null && !app.recovering()) app.log().record("service_destroyed");
        if (instance == this) instance = null;
        ServiceEvidence.record(this, "accessibility_destroyed");
        super.onDestroy();
    }
}
