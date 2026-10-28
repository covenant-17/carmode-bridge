package com.carmode.bridge;

import android.app.Application;

public final class CarModeApp extends Application {
    final com.carmode.bridge.core.BackSequence backSequence = new com.carmode.bridge.core.BackSequence();
    private boolean recovering;
    private Prefs prefs;
    private EventLog log;
    private DiagnosticScenarios diagnostics;
    private MusicHttpAction musicHttp;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener runtimeSettings = (data, key) -> {
        if ("camry_home".equals(key) || "camry_zlink_window".equals(key) || "camry_zlink_auto".equals(key)
                || "camry_camera_auto".equals(key) || "camry_zlink_app".equals(key) || "component_CAMERAS".equals(key))
            CameraStartup.cancel("settings_changed");
        if ("camry_home".equals(key) || "camry_zlink_auto".equals(key) || "camry_zlink_app".equals(key)
                || "camry_zlink_window".equals(key) || "camry_zlink_home_only".equals(key)
                || "camry_all_apps_window".equals(key) || "camry_camera_auto".equals(key)) CamryHome.cancelStartup();
        if ("camry_home".equals(key) || "camry_dock".equals(key) || "camry_dock_position".equals(key) || "camry_dock_opacity".equals(key) || "language".equals(key))
            if (DockService.active() != null) DockService.active().settingsChanged();
        if ("camry_home".equals(key)) DockService.sidebarAbsent(this);
        if ("camry_camera_boot_return".equals(key) || "camry_home".equals(key))
            ModeRuntimeService.sync(this, "camera_boot_return_settings");
        if (musicHttp != null && ("enabled".equals(key) || "auto_start".equals(key)
                || "hcn_mode".equals(key) || "music_http_enabled".equals(key) || "music_http_url".equals(key)))
            musicHttp.cancel("settings_changed");
        if ("enabled".equals(key) || "hcn_mode".equals(key) || "auto_start".equals(key) || "diagnostics".equals(key)) {
            if (ModeRuntimeService.active() != null) ModeRuntimeService.active().cancel("settings_changed");
            ModeRuntimeService.sync(this, "settings_changed");
        }
    };
    // Intentionally process-local. Auto-start=false requires an explicit app launch after process death.
    boolean manuallyActivated;
    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(BootStorage.device(base));
        // Before providers, Activity creation and normal logger initialization.
        CrashReporter.install(this);
        BootStorage.prepare(base);
    }
    @Override public void onCreate() {
        super.onCreate();
        BootEvidence.record(this, "application_created", "process_start");
        if (!BootStorage.unlocked(this)) {
            android.content.BroadcastReceiver unlock = new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context context, android.content.Intent intent) {
                    unregisterReceiver(this);
                    new BootReceiver().onReceive(CarModeApp.this, intent);
                }
            };
            android.content.IntentFilter filter = new android.content.IntentFilter(android.content.Intent.ACTION_USER_UNLOCKED);
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(unlock, filter, android.content.Context.RECEIVER_EXPORTED);
            else registerReceiver(unlock, filter);
        }
        recovering = !BootStorage.ready(this) || CrashReporter.store(this).pending();
        if (!recovering) initialize();
    }
    private void initialize() {
        prefs = new Prefs(this);
        log = new EventLog(this);
        diagnostics = new DiagnosticScenarios(this);
        musicHttp = new MusicHttpAction(this);
        prefs.data.registerOnSharedPreferenceChangeListener(runtimeSettings);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            public void onActivityResumed(android.app.Activity activity) {
                if (!(activity instanceof HomeProxyActivity) && !(activity instanceof CamryPreviewActivity)
                        && !(activity instanceof CamryAppsActivity)) DockService.sidebarAbsent(activity);
            }
            public void onActivityCreated(android.app.Activity activity, android.os.Bundle state) {}
            public void onActivityStarted(android.app.Activity activity) {}
            public void onActivityPaused(android.app.Activity activity) {}
            public void onActivityStopped(android.app.Activity activity) {}
            public void onActivitySaveInstanceState(android.app.Activity activity, android.os.Bundle state) {}
            public void onActivityDestroyed(android.app.Activity activity) {}
        });
        android.content.IntentFilter wake = new android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON);
        wake.addAction(android.content.Intent.ACTION_USER_PRESENT);
        android.content.BroadcastReceiver dockWake = new android.content.BroadcastReceiver() {
            public void onReceive(android.content.Context context, android.content.Intent intent) { DockService.sidebarAbsent(context); }
        };
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(dockWake, wake, android.content.Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(dockWake, wake);
        log.record("process_started", "pid", android.os.Process.myPid(), "previous_accessibility_lifecycle", ServiceEvidence.previous(this),
                "service_state", ServiceEvidence.capture(this), "previous_exit_reason", "unknown_unless_crash_report_present",
                "device_protected_storage", isDeviceProtectedStorage(), "user_unlocked", BootStorage.unlocked(this));
        if (prefs.diagnostics()) diagnostics.startRecording("process_restarted");
    }
    public boolean recovering() { return recovering; }
    void afterUnlock() {
        BootStorage.prepare(this);
        recovering = CrashReporter.store(this).pending();
        if (!recovering && prefs == null) initialize();
    }
    public void resumeAfterCrash() {
        if (prefs == null || log == null || diagnostics == null) initialize();
        recovering = false;
        if (ModeService.active() != null) ModeService.active().resumeAfterCrash();
    }
    public static CarModeApp from(android.content.Context context) { return (CarModeApp) context.getApplicationContext(); }
    public Prefs prefs() { return prefs; }
    public EventLog log() { return log; }
    DiagnosticScenarios diagnostics() { return diagnostics; }
    MusicHttpAction musicHttp() { return musicHttp; }
    public boolean ready() { return prefs != null && com.carmode.bridge.core.RuntimePolicy.ready(recovering, prefs.enabled(), prefs.autoStart(), manuallyActivated); }
}
