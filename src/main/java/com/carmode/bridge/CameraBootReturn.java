package com.carmode.bridge;

import android.app.*;
import android.app.usage.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import com.carmode.bridge.core.CameraBootReturnPolicy;
import com.carmode.bridge.core.CameraRouting;
import com.carmode.bridge.core.ActionDispatcher.Action;
import com.carmode.bridge.core.BootCameraForeground;
import org.json.JSONObject;

/** Brief boot-only observation; no Accessibility, Back injection, force-stop or repeated Home loop. */
final class CameraBootReturn {
    private static final String CLAIM = "camry_camera_return_boot";
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static JSONObject lastObservation = new JSONObject();
    private long lastReport = -1;
    private String lastReason = "";
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
    static JSONObject snapshot(Context context) {
        JSONObject result = new JSONObject();
        try {
            Prefs prefs = CarModeApp.from(context).prefs();
            result.put("enabled", prefs.cameraBootReturn()).put("home_enabled", prefs.camryHome())
                    .put("boot_count", boot(context)).put("claimed_boot", prefs.data.getInt(CLAIM, -1))
                    .put("elapsed_ms", SystemClock.elapsedRealtime()).put("usage_allowed", usageAllowed(context))
                    .put("overlay_allowed", Settings.canDrawOverlays(context))
                    .put("interactive", context.getSystemService(PowerManager.class).isInteractive())
                    .put("keyguard_locked", context.getSystemService(KeyguardManager.class).isKeyguardLocked())
                    .put("last_observation", new JSONObject(lastObservation.toString()));
        } catch (Exception unavailable) { /* Preserve the successfully captured fields. */ }
        return result;
    }
    private void report(String reason, JSONObject details) {
        long elapsed = SystemClock.elapsedRealtime();
        try { details.put("reason", reason).put("observed_elapsed_ms", elapsed); }
        catch (Exception ignored) {}
        lastObservation = details;
        if (!reason.equals(lastReason) || lastReport < 0 || elapsed - lastReport >= 10000) {
            lastReason = reason; lastReport = elapsed;
            CarModeApp.from(context).log().record("camry_camera_boot_observation", "observation", details);
        }
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
    void refresh() {
        close();
        if (needed(context)) handler.post(check);
        else report("not_pending", new JSONObject());
    }
    private final Runnable check = this::inspect;
    private void inspect() {
        if (!needed(context)) {
            report("completed_or_expired", new JSONObject());
            ModeRuntimeService.sync(context, "camera_boot_watch_completed"); return;
        }
        try {
            if (!usageAllowed(context)) {
                report("usage_access_missing", new JSONObject());
            } else if (!context.getSystemService(PowerManager.class).isInteractive()) {
                report("screen_not_interactive", new JSONObject());
            } else if (context.getSystemService(KeyguardManager.class).isKeyguardLocked()) {
                report("keyguard_locked", new JSONObject());
            } else if (!Settings.canDrawOverlays(context)) {
                report("overlay_missing", new JSONObject());
            } else {
                long now = System.currentTimeMillis();
                UsageStatsManager usage = context.getSystemService(UsageStatsManager.class);
                UsageEvents events = usage.queryEvents(now - CameraBootReturnPolicy.WINDOW_MS, now + 1);
                UsageEvents.Event event = new UsageEvents.Event();
                BootCameraForeground evidence = new BootCameraForeground();
                int count = 0;
                while (events != null && events.hasNextEvent()) {
                    events.getNextEvent(event); count++;
                    if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED)
                        evidence.resumed(event.getPackageName(), event.getClassName(), event.getTimeStamp());
                    else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED)
                        evidence.paused(event.getPackageName(), event.getClassName(), event.getTimeStamp());
                }
                CarModeApp app = CarModeApp.from(context);
                String selected = app.prefs().component(Action.CAMERAS);
                String foreground = evidence.pkg, source = "activity_events";
                long cameraUse = 0, otherUse = 0;
                if (!CameraRouting.isTarget(foreground, selected)) {
                    java.util.List<UsageStats> stats = usage.queryUsageStats(UsageStatsManager.INTERVAL_BEST,
                            now - CameraBootReturnPolicy.WINDOW_MS, now + 1);
                    if (stats != null) for (UsageStats item : stats) {
                        if (CameraRouting.isTarget(item.getPackageName(), selected)) cameraUse = Math.max(cameraUse, item.getLastTimeUsed());
                        else otherUse = Math.max(otherUse, item.getLastTimeUsed());
                    }
                    if (BootCameraForeground.recentCameraUse(cameraUse, otherUse, evidence.latest, now, SystemClock.elapsedRealtime())) {
                        String camera = com.carmode.bridge.core.ActionDispatcher.target(Action.CAMERAS, selected);
                        foreground = camera.contains("/") ? camera.substring(0, camera.indexOf('/')) : camera;
                        source = "recent_usage";
                    }
                }
                JSONObject observation = new JSONObject();
                observation.put("event_count", count).put("resumes", evidence.resumes).put("pauses", evidence.pauses)
                        .put("foreground", foreground == null ? JSONObject.NULL : foreground)
                        .put("latest_lifecycle_ms", evidence.latest).put("camera_last_used_ms", cameraUse)
                        .put("other_last_used_ms", otherUse).put("query_end_ms", now).put("source", source);
                boolean camera = CameraRouting.isTarget(foreground, selected);
                report(camera ? "camera_detected" : count == 0 ? "events_empty" : "camera_not_foreground", observation);
                if (camera && claim(context)) {
                    // Persist before navigation, so recreation or rejected background starts cannot repeat it.
                    DockService.hide(context);
                    context.startActivity(new Intent(context, HomeProxyActivity.class).setAction(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    app.log().record("camry_camera_boot_return_requested", "package", foreground,
                            "source", source, "since_android_boot_ms", SystemClock.elapsedRealtime(), "foreground_confirmed", false);
                    ModeRuntimeService.sync(context, "camera_boot_return_completed");
                    return;
                }
            }
        } catch (Exception error) {
            CarModeApp.from(context).log().record("camry_camera_boot_return_error", "error", error.getClass().getSimpleName());
            return;
        }
        if (needed(context)) handler.postDelayed(check, 500);
    }
    void close() { handler.removeCallbacksAndMessages(null); }
}
