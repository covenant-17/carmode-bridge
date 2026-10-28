package com.carmode.bridge;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import com.carmode.bridge.core.DockPolicy;
import com.carmode.bridge.core.ForegroundStartQueue;

/** Manual or Camry-transition overlay session, independent of MODE. Non-sticky, with no boot receiver. */
public final class DockService extends Service {
    private static DockService instance;
    private static final ForegroundStartQueue STARTS = new ForegroundStartQueue();
    private static final com.carmode.bridge.core.SidebarVisibility SIDEBARS = new com.carmode.bridge.core.SidebarVisibility();
    private static final Handler VISIBILITY = new Handler(Looper.getMainLooper());
    private boolean automatic;
    private DockController dock;
    private boolean registered;
    private Notification notification;
    private int lastStartId;
    private long activeRequest;
    private final Handler handler = new Handler(Looper.getMainLooper());
    static DockService active() { return instance; }
    static void hide(Context context) {
        STARTS.cancel();
        if (instance != null) {
            if (instance.dock != null) instance.dock.close();
            instance.stopWhenReady();
        }
    }
    static void sidebarShown(Activity host) {
        SIDEBARS.shown(host); VISIBILITY.removeCallbacksAndMessages(null);
        hide(host);
    }
    static void sidebarHidden(Activity host) {
        if (SIDEBARS.hidden(host)) sidebarAbsent(host);
    }
    static void sidebarAbsent(Context context) {
        Context appContext = context.getApplicationContext();
        VISIBILITY.removeCallbacksAndMessages(null);
        VISIBILITY.postDelayed(() -> { if (!SIDEBARS.visible()) transition(appContext); }, 250);
    }
    static void transition(Context context) {
        CarModeApp app = CarModeApp.from(context);
        if (app.recovering() || app.prefs() == null || !DockPolicy.allowed(true, app.prefs().camryHome(), app.prefs().dockEnabled(),
                false, Settings.canDrawOverlays(context), SIDEBARS.visible())) return;
        if (!context.getSystemService(PowerManager.class).isInteractive()
                || context.getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
        try { start(context, true); }
        catch (RuntimeException error) { app.log().record("camry_dock_transition_failed", "error", error.toString()); }
    }
    private static void start(Context context, boolean automatic) {
        long token = STARTS.request();
        try {
            context.startForegroundService(new Intent(context, DockService.class)
                    .setAction(automatic ? "transition" : "manual").putExtra("request_token", token));
        } catch (RuntimeException error) {
            STARTS.failed(token);
            if (instance != null) instance.stopWhenReady();
            throw error;
        }
    }
    private boolean allowed() {
        CarModeApp app = CarModeApp.from(this);
        return app.prefs() != null && DockPolicy.allowed(automatic, app.prefs().camryHome(), app.prefs().dockEnabled(),
                app.recovering(), Settings.canDrawOverlays(this), SIDEBARS.visible());
    }
    static void request(Activity activity) {
        if (!new Prefs(activity).dockEnabled()) { CamryControl.error(activity, R.string.camry_dock_disabled); return; }
        if (!Settings.canDrawOverlays(activity)) { CamryControl.error(activity, R.string.need_overlay); return; }
        new AlertDialog.Builder(activity).setTitle(R.string.camry_show_dock).setMessage(R.string.camry_dock_warning)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try { start(activity, false); }
                    catch (RuntimeException error) { CamryControl.error(activity, R.string.menu_failed); }
                }).show();
    }
    @Override public void onCreate() {
        super.onCreate();
        promote();
        instance = this;
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, filter);
        registered = true;
    }
    private void promote() {
        if (notification == null) notification = buildNotification();
        startForeground(101, notification);
    }
    private Notification buildNotification() {
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("camry_dock",
                I18n.text(this, R.string.camry_dock), NotificationManager.IMPORTANCE_LOW));
        PendingIntent settings = PendingIntent.getActivity(this, 2, new Intent(this, LauncherActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 3, new Intent(this, DockService.class).setAction("hide"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "camry_dock").setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(I18n.text(this, R.string.camry_dock)).setContentText(I18n.text(this, R.string.camry_dock_session))
                .setContentIntent(settings).addAction(new Notification.Action.Builder(null, I18n.text(this, R.string.camry_hide_dock), stop).build())
                .setOngoing(true).build();
    }
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { hide(DockService.this); }
    };
    private final Runnable permissionCheck = new Runnable() {
        @Override public void run() {
            if (!allowed()) { hide(DockService.this); return; }
            handler.postDelayed(this, 1000);
        }
    };
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        // Every startForegroundService request needs acknowledgement, even if cancelled or stale.
        promote();
        lastStartId = id;
        long token = intent == null ? 0 : intent.getLongExtra("request_token", 0);
        boolean current = STARTS.delivered(token);
        if (intent != null && "hide".equals(intent.getAction())) { hide(this); return START_NOT_STICKY; }
        if (!current) { stopWhenReady(); return START_NOT_STICKY; }
        automatic = intent != null && "transition".equals(intent.getAction());
        if (!allowed()) {
            hide(this); return START_NOT_STICKY;
        }
        activeRequest = token;
        if (dock == null) dock = new DockController(this);
        dock.show(); handler.removeCallbacks(permissionCheck); handler.post(permissionCheck);
        return START_NOT_STICKY;
    }
    private void stopWhenReady() {
        if (STARTS.pending() || STARTS.current(activeRequest)) return;
        handler.removeCallbacks(permissionCheck);
        if (dock != null) dock.close();
        // A newer system startId must not be stopped by an older command finishing.
        if (lastStartId != 0) stopSelfResult(lastStartId);
    }
    void settingsChanged() {
        if (!allowed()) hide(this);
        else if (dock != null) dock.refresh();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (dock != null) dock.close();
        if (registered) unregisterReceiver(screen);
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
