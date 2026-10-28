package com.carmode.bridge;

import android.app.*;
import android.app.usage.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import com.carmode.bridge.core.CameraBootReturnPolicy;
import com.carmode.bridge.core.CameraRouting;
import com.carmode.bridge.core.ActionDispatcher.Action;

/** Brief boot-only observation; no Accessibility, Back injection, force-stop or repeated Home loop. */
final class CameraBootReturn {
    private static final String CLAIM = "camry_camera_return_boot";
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean permissionReported;
    CameraBootReturn(Context context) { this.context = context; }
    private static int boot(Context context) {
        try { return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1); }
        catch (RuntimeException unavailable) { return -1; }
    }
    static boolean needed(Context context) {
        CarModeApp app = CarModeApp.from(context);
        if (app.recovering()) return false;
        Prefs prefs = app.prefs();
        return CameraBootReturnPolicy.pending(prefs.camryHome() && prefs.cameraBootReturn(), false,
                boot(context), SystemClock.elapsedRealtime(), prefs.data.getInt(CLAIM, -1));
    }
    static boolean usageAllowed(Context context) {
        try { return context.getSystemService(AppOpsManager.class).checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED; }
        catch (RuntimeException unavailable) { return false; }
    }
    private static boolean claim(Context context) {
        Prefs prefs = CarModeApp.from(context).prefs();
        return CameraBootReturnPolicy.claim(prefs.camryHome() && prefs.cameraBootReturn(),
                CarModeApp.from(context).recovering(), boot(context), SystemClock.elapsedRealtime(), new CameraBootReturnPolicy.Store() {
                    public int lastBoot() { return prefs.data.getInt(CLAIM, -1); }
                    public boolean saveBoot(int value) { return prefs.data.edit().putInt(CLAIM, value).commit(); }
                });
    }
    static void manual(Context context, Intent target) {
        CarModeApp app = CarModeApp.from(context);
        if (!needed(context) || target.getComponent() == null || !CameraRouting.isTarget(
                target.getComponent().getPackageName(), app.prefs().component(Action.CAMERAS))) return;
        if (claim(context)) app.log().record("camry_camera_boot_return_skipped", "reason", "manual_camera_launch");
    }
    void refresh() { close(); if (needed(context)) handler.post(check); }
    private final Runnable check = this::inspect;
    private void inspect() {
        if (!needed(context)) { ModeRuntimeService.sync(context, "camera_boot_watch_completed"); return; }
        try {
            if (!usageAllowed(context)) {
                if (!permissionReported) {
                    permissionReported = true;
                    CarModeApp.from(context).log().record("camry_camera_boot_return_unavailable", "reason", "usage_access_missing");
                }
            } else if (context.getSystemService(PowerManager.class).isInteractive()
                    && !context.getSystemService(KeyguardManager.class).isKeyguardLocked()
                    && Settings.canDrawOverlays(context)) {
                long now = System.currentTimeMillis();
                UsageEvents events = context.getSystemService(UsageStatsManager.class).queryEvents(now - CameraBootReturnPolicy.WINDOW_MS, now + 1);
                UsageEvents.Event event = new UsageEvents.Event();
                String foreground = null;
                while (events != null && events.hasNextEvent()) {
                    events.getNextEvent(event);
                    if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) foreground = event.getPackageName();
                    else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED
                            && event.getPackageName().equals(foreground)) foreground = null;
                }
                CarModeApp app = CarModeApp.from(context);
                if (CameraRouting.isTarget(foreground, app.prefs().component(Action.CAMERAS)) && claim(context)) {
                    // Persist before navigation, so recreation or rejected background starts cannot repeat it.
                    DockService.hide(context);
                    context.startActivity(new Intent(context, HomeProxyActivity.class).setAction(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    app.log().record("camry_camera_boot_return_requested", "package", foreground,
                            "since_android_boot_ms", SystemClock.elapsedRealtime(), "foreground_confirmed", false);
                    ModeRuntimeService.sync(context, "camera_boot_return_completed");
                    return;
                }
            }
        } catch (RuntimeException error) {
            CarModeApp.from(context).log().record("camry_camera_boot_return_error", "error", error.getClass().getSimpleName());
            return;
        }
        if (needed(context)) handler.postDelayed(check, 500);
    }
    void close() { handler.removeCallbacksAndMessages(null); }
}
