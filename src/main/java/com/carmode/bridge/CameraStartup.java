package com.carmode.bridge;

import android.content.*;
import android.os.*;
import android.app.KeyguardManager;
import com.carmode.bridge.core.CameraStartupSequence;

/** Survives the expected camera Activity pause; explicit navigation/settings cancel the handoff. */
final class CameraStartup {
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static CameraStartupSequence sequence;
    private static CarModeApp owner;
    private CameraStartup() {}
    static void cancel(String reason) {
        handler.removeCallbacksAndMessages(null);
        if (sequence != null) {
            sequence.cancel();
            if (owner != null && !owner.recovering()) owner.log().record("camry_camera_startup_cancelled", "reason", reason);
        }
        sequence = null; owner = null;
    }
    static void afterCamera(Context context, boolean launchAccepted) {
        cancel("new_sequence");
        CarModeApp app = CarModeApp.from(context);
        app.log().record("camry_camera_startup_camera_requested", "launch_accepted", launchAccepted, "foreground_confirmed", false);
        if (!app.prefs().zlinkAuto()) return;
        owner = app; sequence = new CameraStartupSequence(SystemClock.elapsedRealtime(), true);
        handler.postDelayed(() -> {
            CameraStartupSequence current = sequence;
            sequence = null; owner = null;
            if (current == null || !current.take(SystemClock.elapsedRealtime())) return;
            Prefs prefs = app.prefs();
            if (app.recovering() || !prefs.camryHome() || !prefs.zlinkWindow() || !prefs.cameraAuto() || !prefs.zlinkAuto()
                    || !app.getSystemService(PowerManager.class).isInteractive()
                    || app.getSystemService(KeyguardManager.class).isKeyguardLocked()) {
                app.log().record("camry_camera_startup_cancelled", "reason", "settings_or_display_changed"); return;
            }
            try {
                Intent zlink = AppResolver.resolve(app, prefs.zlinkApp());
                app.startActivity(new Intent(app, HomeProxyActivity.class).setAction(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .putExtra("camry_window_target", zlink.getComponent().flattenToString())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                app.log().record("camry_camera_startup_zlink_requested", "foreground_confirmed", false);
            } catch (Exception error) { app.log().record("camry_camera_startup_error", "error", error.getClass().getSimpleName()); }
        }, CameraStartupSequence.DELAY_MS);
    }
}
