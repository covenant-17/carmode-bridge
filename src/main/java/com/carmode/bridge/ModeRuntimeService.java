package com.carmode.bridge;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;

/** Started foreground lifetime; never owned/bound/stopped by an Activity. HCN needs no Accessibility. */
public final class ModeRuntimeService extends Service {
    private static volatile ModeRuntimeService instance;
    private CarModeApp app;
    private MenuController menu;
    private HcnModeReceiver hcn;
    private boolean screenRegistered;
    private CameraBootReturn cameraBootReturn;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String previousComparison = "";
    public static ModeRuntimeService active() { return instance; }
    private static boolean needed(CarModeApp app) {
        return !app.recovering() && (com.carmode.bridge.core.RuntimePolicy.keepService(false, app.ready(), app.prefs().diagnostics())
                || CameraBootReturn.needed(app));
    }
    public static void sync(Context context, String reason) {
        CarModeApp app = CarModeApp.from(context);
        if (app.recovering()) return;
        try {
            if (needed(app)) {
                BootEvidence.record(context, "runtime_start_requested", reason);
                context.startForegroundService(new Intent(context, ModeRuntimeService.class).putExtra("reason", reason));
            } else if (instance != null) {
                app.log().record("runtime_stop_requested", "reason", reason, "ready", false);
                context.stopService(new Intent(context, ModeRuntimeService.class));
            } else BootEvidence.record(context, "startup_skipped", "runtime_not_needed:" + reason);
        } catch (RuntimeException e) {
            BootEvidence.record(context, "runtime_start_error", reason + ":" + e.toString());
            app.log().record("runtime_start_stop_error", "reason", reason, "error", e.getClass().getSimpleName());
        }
    }
    @Override public void onCreate() {
        super.onCreate(); app = CarModeApp.from(this);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("mode_background", I18n.text(this, R.string.runtime_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, LauncherActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        startForeground(100, new Notification.Builder(this, "mode_background").setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("CarMode Bridge").setContentText(I18n.text(this, R.string.runtime_notification))
                .setContentIntent(open).setOngoing(true).build());
        BootEvidence.record(this, "runtime_foreground", "onCreate");
        if (app.recovering()) { stopSelf(); return; }
        instance = this; menu = new MenuController(this); hcn = new HcnModeReceiver(this, menu); hcn.register();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(screen, filter);
        screenRegistered = true;
        cameraBootReturn = new CameraBootReturn(this);
        ServiceEvidence.record(this, "runtime_created"); monitor.run();
    }
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) || "homekey".equals(intent.getStringExtra("reason"))
                    || "recentapps".equals(intent.getStringExtra("reason"))) CameraStartup.cancel("system_navigation_or_screen_off");
            cancel(Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) ? "screen_off" : "system_dialogs_closed");
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) app.diagnostics().cancel("screen_off");
        }
    };
    private final Runnable monitor = new Runnable() {
        @Override public void run() {
            if (app.recovering()) return;
            JSONObject state = ServiceEvidence.capture(ModeRuntimeService.this);
            String comparison = state.optString("comparison", "query_error");
            if (!comparison.equals(previousComparison)) {
                app.log().record("accessibility_state_observed", "previous_comparison", previousComparison, "service_state", state);
                previousComparison = comparison;
            }
            handler.postDelayed(this, 5000);
        }
    };
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!needed(app)) { stopSelf(); return START_NOT_STICKY; }
        if (cameraBootReturn != null) cameraBootReturn.refresh();
        app.log().record("runtime_started", "reason", intent == null ? "system_sticky_restart" : intent.getStringExtra("reason"),
                "flags", flags, "start_id", startId);
        BootEvidence.record(this, "runtime_started", intent == null ? "system_sticky_restart" : intent.getStringExtra("reason"));
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onTaskRemoved(Intent rootIntent) {
        ServiceEvidence.record(this, "runtime_task_removed"); // Deliberately keep receiver and timer alive.
        super.onTaskRemoved(rootIntent);
    }
    public void cancel(String reason) { if (menu != null) menu.cancel(reason); if (hcn != null) hcn.reset(); }
    public void simulate() { if (menu != null) menu.press("TOUCH"); }
    void wakeMode(Intent original, HcnModeReceiver.AbortOperation abort) {
        if (hcn != null) hcn.handle(original, true, abort);
    }
    public String hcnStatus(Context context) { return hcn == null ? context.getString(R.string.hcn_unavailable) : hcn.status(context); }
    public JSONObject hcnSnapshot() throws org.json.JSONException {
        return hcn == null ? new JSONObject().put("receiver_registered", false) : hcn.snapshot();
    }
    @Override public void onDestroy() {
        if (cameraBootReturn != null) cameraBootReturn.close();
        handler.removeCallbacksAndMessages(null); cancel("runtime_destroyed");
        if (hcn != null) hcn.close();
        if (screenRegistered) unregisterReceiver(screen);
        if (instance == this) instance = null;
        ServiceEvidence.record(this, "runtime_destroyed");
        super.onDestroy();
    }
}
