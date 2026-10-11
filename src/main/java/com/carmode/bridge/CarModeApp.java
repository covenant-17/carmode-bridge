package com.carmode.bridge;

import android.app.Application;

public final class CarModeApp extends Application {
    private boolean recovering;
    private Prefs prefs;
    private EventLog log;
    private DiagnosticScenarios diagnostics;
    private MusicHttpAction musicHttp;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener runtimeSettings = (data, key) -> {
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
