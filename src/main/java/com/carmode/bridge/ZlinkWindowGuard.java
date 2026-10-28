package com.carmode.bridge;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.graphics.Rect;
import android.os.*;
import android.view.accessibility.*;
import com.carmode.bridge.core.ZlinkRouting;
import com.carmode.bridge.core.ZlinkWindowGuardPolicy;

/** Observes application package/bounds only; never reads text or captures navigation/gestures. */
final class ZlinkWindowGuard {
    private final AccessibilityService service;
    private final CarModeApp app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ZlinkWindowGuardPolicy policy = new ZlinkWindowGuardPolicy();
    private boolean limitReported;
    private long cameraObserved = -1;
    ZlinkWindowGuard(AccessibilityService service) { this.service = service; app = CarModeApp.from(service); }
    private boolean enabled() {
        return !app.recovering() && app.prefs() != null && app.prefs().camryHome()
                && app.prefs().zlinkWindow() && app.prefs().zlinkHomeOnly();
    }
    void event(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) inspect();
    }
    void inspect() {
        handler.removeCallbacks(check);
        if (enabled()) handler.postDelayed(check, 250);
    }
    private final Runnable check = this::checkWindow;
    private void checkWindow() {
        if (!enabled() || !service.getSystemService(PowerManager.class).isInteractive()
                || service.getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
        try {
            for (AccessibilityWindowInfo window : service.getWindows()) {
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION || !window.isFocused()) continue;
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) return;
                String pkg;
                try { pkg = root.getPackageName() == null ? "" : root.getPackageName().toString(); }
                finally { root.recycle(); }
                long now = SystemClock.elapsedRealtime();
                if ("ivicar.cn.ivicaravm".equals(pkg) && (cameraObserved < 0 || now - cameraObserved > 5000)) {
                    cameraObserved = now;
                    app.log().record("camry_camera_foreground_observed", "package", pkg,
                            "since_android_boot_ms", now, "launch_source", "unknown_from_window_event");
                }
                if (!ZlinkRouting.isTarget(pkg, app.prefs().zlinkApp())) return;
                Rect bounds = new Rect(); window.getBoundsInScreen(bounds);
                android.util.DisplayMetrics screen = service.getResources().getDisplayMetrics();
                boolean fullscreen = bounds.width() >= screen.widthPixels - CamryUi.dp(service, 32)
                        && bounds.height() >= screen.heightPixels / 2;
                boolean take = policy.take(true, true, bounds.width(), bounds.height(), screen.widthPixels,
                        screen.heightPixels, CamryUi.dp(service, 32), now);
                if (take) {
                    app.log().record("camry_zlink_external_fullscreen", "package", pkg, "bounds", bounds.toShortString(),
                            "correction_requested", true, "bounds_confirmed", false);
                    CamryNavigation.launch(service, AppResolver.resolve(service, app.prefs().zlinkApp()), null);
                } else if (fullscreen && policy.exhausted() && !limitReported) {
                    limitReported = true;
                    app.log().record("camry_zlink_guard_paused", "reason", "attempt_limit");
                    CamryControl.error(service, R.string.camry_zlink_guard_limit);
                }
                return;
            }
        } catch (Exception error) { app.log().record("camry_zlink_guard_error", "error", error.getClass().getSimpleName()); }
    }
    void reset() { close(); policy.reset(); limitReported = false; inspect(); }
    void close() { handler.removeCallbacksAndMessages(null); }
}
