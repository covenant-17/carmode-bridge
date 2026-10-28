package com.carmode.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Parcelable;
import android.provider.Settings;
import android.view.KeyEvent;
import com.carmode.bridge.core.HcnModeRouter;
import org.json.JSONException;
import org.json.JSONObject;

/** Context-registered in the foreground runtime; independent of Accessibility. */
final class HcnModeReceiver extends BroadcastReceiver {
    private final Context service;
    private final CarModeApp app;
    private final MenuController menu;
    private final HcnModeRouter router = new HcnModeRouter();
    private volatile boolean registered;
    private volatile Observation last;
    private static final class Observation {
        final long time = System.currentTimeMillis();
        final String action;
        final boolean ordered;
        final HcnModeRouter.Result result;
        Observation(String action, boolean ordered, HcnModeRouter.Result result) {
            this.action = action; this.ordered = ordered; this.result = result;
        }
    }
    HcnModeReceiver(Context service, MenuController menu) {
        this.service = service; this.menu = menu; app = CarModeApp.from(service);
    }
    void register() {
        if (registered) return;
        IntentFilter filter = new IntentFilter(HcnModeRouter.MODE);
        filter.addAction(HcnModeRouter.AUXILIARY); filter.setPriority(999);
        try {
            if (Build.VERSION.SDK_INT >= 33) service.registerReceiver(this, filter, Context.RECEIVER_EXPORTED);
            else service.registerReceiver(this, filter);
            registered = true;
            app.log().record("hcn_receiver_registered", "priority", 999);
            BootEvidence.record(service, "hcn_receiver_registered", "priority_999");
        } catch (RuntimeException e) {
            BootEvidence.record(service, "hcn_receiver_error", e.toString());
            app.log().record("hcn_receiver_error", "operation", "register", "error", e.getClass().getSimpleName());
        }
    }
    void close() {
        reset();
        if (!registered) return;
        try { service.unregisterReceiver(this); }
        catch (RuntimeException e) { app.log().record("hcn_receiver_error", "operation", "unregister", "error", e.getClass().getSimpleName()); }
        finally { registered = false; }
    }
    void reset() { router.reset(); }
    interface AbortOperation { boolean abort(); }
    @Override public void onReceive(Context context, Intent intent) {
        handle(intent, isOrderedBroadcast(), () -> { abortBroadcast(); return getAbortBroadcast(); }, isInitialStickyBroadcast());
    }
    void handle(Intent intent, boolean ordered, AbortOperation abortOperation) {
        handle(intent, ordered, abortOperation, false);
    }
    private void handle(Intent intent, boolean ordered, AbortOperation abortOperation, boolean initialSticky) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!HcnModeRouter.MODE.equals(action) && !HcnModeRouter.AUXILIARY.equals(action)) return;
        HcnModeRouter.Input input = new HcnModeRouter.Input();
        input.action = action; input.ordered = ordered;
        input.initialSticky = initialSticky;
        input.noAbort = (intent.getFlags() & Intent.FLAG_RECEIVER_NO_ABORT) != 0;
        KeyEvent key = null;
        Integer auxiliaryCode = null;
        boolean payloadPresent = false;
        String payloadError = null;
        try {
            if (HcnModeRouter.MODE.equals(action) && intent.hasExtra(Intent.EXTRA_KEY_EVENT)) {
                payloadPresent = true;
                Parcelable payload = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (!(payload instanceof KeyEvent)) input.malformed = true;
                else {
                    key = (KeyEvent) payload; input.hasKey = true;
                    input.keyAction = key.getAction(); input.repeatCount = key.getRepeatCount();
                    input.keyCode = key.getKeyCode(); input.deviceId = key.getDeviceId(); input.downTime = key.getDownTime();
                    input.malformed = input.repeatCount < 0;
                }
            }
            if (HcnModeRouter.AUXILIARY.equals(action) && intent.hasExtra("KEYCODE")) auxiliaryCode = intent.getIntExtra("KEYCODE", -1);
        } catch (RuntimeException e) { input.malformed = true; payloadError = e.getClass().getSimpleName(); }
        HcnModeRouter.State state = new HcnModeRouter.State();
        state.enabled = app.prefs().hcnMode(); state.ready = app.ready();
        state.scenario = app.diagnostics().active();
        state.learning = ModeService.active() != null && ModeService.active().learning();
        try { state.overlay = Settings.canDrawOverlays(service); }
        catch (RuntimeException e) { app.log().record("hcn_permission_error", "error", e.getClass().getSimpleName()); }
        HcnModeRouter.Result result = router.route(input, state, new HcnModeRouter.Port() {
            // Physical MODE uses the same source label on both native input paths.
            @Override public boolean press() { return menu.press("STEERING_WHEEL"); }
            @Override public boolean abort() { return abortOperation.abort(); }
            @Override public void cancel() { menu.cancel("hcn_fail_open"); }
        });
        last = new Observation(action, input.ordered, result);
        // Whitelisted scalar fields only. Public broadcasts do not authenticate a physical press.
        app.log().record("hcn_broadcast", "intent_action", action, "flags", intent.getFlags(),
                "ordered", input.ordered, "initial_sticky", input.initialSticky, "no_abort", input.noAbort, "payload_present", payloadPresent,
                "payload_error", payloadError,
                "malformed", input.malformed, "auxiliary_keycode", auxiliaryCode,
                "keyCode", key == null ? null : key.getKeyCode(), "scanCode", key == null ? null : key.getScanCode(),
                "deviceId", key == null ? null : key.getDeviceId(), "source", key == null ? null : key.getSource(),
                "action", key == null ? null : key.getAction(), "repeatCount", key == null ? null : key.getRepeatCount(),
                "eventTime", key == null ? null : key.getEventTime(), "downTime", key == null ? null : key.getDownTime(),
                "hcn_enabled", state.enabled, "ready", state.ready, "overlay_allowed", state.overlay,
                "menu_advanced", result.advanced, "aborted", result.aborted, "reason", result.reason,
                "error", result.error, "stock_suppression_confirmed", false);
    }
    JSONObject snapshot() throws JSONException {
        JSONObject data = new JSONObject(); data.put("receiver_registered", registered);
        data.put("enabled", app.prefs().hcnMode());
        Observation observed = last;
        if (observed != null) {
            data.put("last_received_at", observed.time); data.put("last_action", observed.action);
            data.put("last_ordered", observed.ordered); data.put("last_aborted", observed.result.aborted);
            data.put("last_reason", observed.result.reason);
        }
        data.put("stock_suppression_confirmed", false);
        return data;
    }
    String status(Context context) {
        Observation observed = last;
        return context.getString(R.string.hcn_status, context.getString(registered ? R.string.connected : R.string.disconnected),
                observed == null ? context.getString(R.string.hcn_not_received) :
                context.getString(R.string.hcn_last, observed.action,
                        context.getString(observed.ordered ? R.string.yes : R.string.no),
                        context.getString(observed.result.aborted ? R.string.yes : R.string.no),
                        context.getString(reasonLabel(observed.result.reason))));
    }
    private static int reasonLabel(String reason) {
        switch (reason) {
            case "handled": return R.string.hcn_reason_handled;
            case "held_event_suppressed": return R.string.hcn_reason_held;
            case "auxiliary_only": return R.string.hcn_reason_auxiliary;
            case "disabled": return R.string.hcn_reason_disabled;
            case "initial_sticky_replay": return R.string.hcn_reason_sticky;
            case "not_ready": return R.string.hcn_reason_not_ready;
            case "diagnostic_scenario": case "learning": return R.string.hcn_reason_passive;
            case "malformed_payload": return R.string.hcn_reason_malformed;
            case "not_ordered": return R.string.hcn_reason_not_ordered;
            case "no_abort_flag": return R.string.hcn_reason_no_abort;
            case "overlay_missing": return R.string.hcn_reason_overlay;
            case "key_up": case "key_repeat": case "key_action": return R.string.hcn_reason_key;
            default: return R.string.hcn_reason_error;
        }
    }
}
