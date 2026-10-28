package com.carmode.bridge;

import android.content.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import com.carmode.bridge.core.HomeProxyFlow;
import com.carmode.bridge.core.HomeReturnPolicy;
import com.carmode.bridge.core.HomeNavigation;

/** Existing HOME entry with OEM forwarding or opt-in Camry UI; never implicit HOME recursion. */
public final class HomeProxyActivity extends CamryActivity {
    private CamryHome camry;
    private boolean startupOnly, startupWasPaused;
    private final Handler handler = new Handler();
    private final HomeReturnPolicy returns = new HomeReturnPolicy();
    private boolean handedOff, needsReturn, resumed, receiverRegistered;
    private final Runnable startupFallback = () -> {
        if (resumed && startupOnly && hasWindowFocus()) { clearCamry(); route(); }
    };
    private final Runnable handoffTimeout = () -> {
        if (resumed && handedOff && !isFinishing()) {
            HomeBridge.logEntry(this, "handoff_not_paused"); showRecovery();
        }
    };
    private final BroadcastReceiver freeform = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            returns.freeformMove(SystemClock.elapsedRealtime());
            // Accelerate an already pending foreground return, never a background launch.
            if (resumed && needsReturn && !isFinishing()) {
                handler.removeCallbacks(returnToOem); handler.post(returnToOem);
            }
            HomeBridge.logEntry(HomeProxyActivity.this, "oem_freeform_move");
            // Never launch over music/camera/another app from a broadcast.
        }
    };
    private final Runnable returnToOem = () -> {
        if (!resumed || !needsReturn || isFinishing()) return;
        needsReturn = false;
        if (!HomeBridge.ownDefault(this)) { finish(); return; }
        HomeReturnPolicy.Target target = returns.returned(SystemClock.elapsedRealtime());
        HomeBridge.logEntry(this, "return_" + target.name());
        if (target == HomeReturnPolicy.Target.RECOVERY) { showRecovery(); return; }
        setIntent(new Intent(target == HomeReturnPolicy.Target.ALL_APPS ? HomeNavigation.ALL_APPS : Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME));
        route();
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); overridePendingTransition(0, 0);
        try {
            registerReceiver(freeform, new IntentFilter("action.hcn.launcher.freeform.move")); receiverRegistered = true;
        } catch (RuntimeException error) {
            CarModeApp app = CarModeApp.from(this);
            if (!app.recovering()) app.log().record("home_freeform_receiver_error", "error", error.toString());
        }
        returns.explicitNavigation(); route();
    }
    @Override protected void onNewIntent(Intent intent) {
        CameraStartup.cancel("home_navigation");
        CamryHome.cancelStartup();
        CarModeApp.from(this).backSequence.reset();
        super.onNewIntent(intent); setIntent(intent);
        handler.removeCallbacks(returnToOem); needsReturn = false;
        returns.explicitNavigation(); route();
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        CarModeApp app = CarModeApp.from(this);
        if (camry != null && (app.recovering() || (startupOnly ? startupWasPaused : !app.prefs().camryHome()))) {
            clearCamry(); route();
        } else if (camry != null) camry.resume();
        // Keep the old fallback only when the OEM signal has not arrived yet.
        if (needsReturn) handler.postDelayed(returnToOem, returns.returnDelayMs(SystemClock.elapsedRealtime()));
        HomeBridge.logEntry(this, "resume");
    }
    @Override protected void onPause() {
        if (startupOnly) startupWasPaused = true;
        handler.removeCallbacks(startupFallback);
        if (camry != null) camry.pause();
        resumed = false; handler.removeCallbacks(returnToOem); handler.removeCallbacks(handoffTimeout);
        if (handedOff) needsReturn = true;
        HomeBridge.logEntry(this, "pause"); super.onPause();
    }
    @Override protected void onStop() {
        super.onStop();
        if (camry != null && !startupOnly) camry.stopped();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (camry != null && !startupOnly) camry.focusChanged(focused);
    }
    @Override protected void normalBack() {
        if (camry != null && !startupOnly) { camry.back(); return; }
        if (startupOnly) clearCamry();
        handler.removeCallbacks(returnToOem); needsReturn = false; returns.explicitNavigation();
        setIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)); route();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(startupFallback);
        if (camry != null) camry.close();
        handler.removeCallbacks(returnToOem); handler.removeCallbacks(handoffTimeout);
        if (receiverRegistered) unregisterReceiver(freeform);
        HomeBridge.logEntry(this, "destroy"); super.onDestroy();
    }
    private void route() {
        HomeBridge.logEntry(this, "route");
        CarModeApp app = CarModeApp.from(this);
        final boolean bootTrace = !handedOff;
        // Boot history uses synchronous commits. Keep it for initial startup, not every swipe.
        if (bootTrace) BootEvidence.record(this, "home_proxy_entered", HomeBridge.current(this));
        if (!app.recovering()) {
            if (bootTrace) app.log().record("home_proxy_entered", "home_state", HomeBridge.snapshot(this));
            else app.log().record("home_proxy_entered", "retained_home", true);
        }
        boolean autoStart = !app.recovering() && app.prefs().autoStart();
        if (bootTrace && !autoStart) BootEvidence.record(this, "home_proxy_runtime_skipped", app.recovering() ? "crash_or_storage_gate" : "auto_start_disabled");
        if (bootTrace && autoStart && ModeRuntimeService.active() != null)
            BootEvidence.record(this, "home_proxy_runtime_reused", "already_running");
        HomeProxyFlow.run(autoStart, app.recovering(), ModeRuntimeService.active() != null, new HomeProxyFlow.Port() {
            public void startRuntime() { ModeRuntimeService.sync(HomeProxyActivity.this, "home_proxy"); }
            public void forwardOem() {
                if (!app.recovering() && app.prefs().camryHome()) {
                    startupOnly = false; startupWasPaused = false; handler.removeCallbacks(startupFallback);
                    handedOff = false; needsReturn = false;
                    handler.removeCallbacks(returnToOem); handler.removeCallbacks(handoffTimeout);
                    try {
                        if (camry == null) camry = new CamryHome(HomeProxyActivity.this);
                        boolean apps = HomeNavigation.ALL_APPS.equals(HomeBridge.forward(getIntent()).getAction());
                        camry.show(false);
                        if (apps) camry.openApps();
                        else camry.startup(true);
                        app.log().record("camry_home_shown", "apps", apps);
                    } catch (RuntimeException failure) {
                        clearCamry(); throw failure;
                    }
                    return;
                }
                clearCamry();
                if (!app.recovering() && app.prefs().zlinkAuto()
                        && !HomeNavigation.ALL_APPS.equals(HomeBridge.forward(getIntent()).getAction())) {
                    camry = new CamryHome(HomeProxyActivity.this);
                    if (camry.startup(false)) {
                        startupOnly = true;
                        camry.show(false);
                        handler.postDelayed(startupFallback, 1100);
                        return;
                    }
                    clearCamry();
                }
                if (!HomeBridge.available(HomeProxyActivity.this)) throw new IllegalStateException("OEM launcher unavailable");
                Intent forwarded = HomeBridge.forward(getIntent());
                forwarded.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);
                startActivity(forwarded, android.app.ActivityOptions.makeCustomAnimation(HomeProxyActivity.this, 0, 0).toBundle());
                overridePendingTransition(0, 0);
                if (bootTrace) BootEvidence.record(HomeProxyActivity.this, "home_proxy_forward_requested", HomeBridge.target().flattenToString());
                if (!app.recovering()) app.log().record("home_proxy_forward_requested", "component", HomeBridge.target().flattenToString(),
                        "incoming", HomeBridge.intentState(getIntent()), "outgoing", HomeBridge.intentState(forwarded), "foreground_confirmed", false);
            }
            public void error(String stage, RuntimeException error) {
                BootEvidence.record(HomeProxyActivity.this, "home_proxy_error", stage + ":" + error.toString());
                if (!app.recovering()) app.log().record("home_proxy_error", "stage", stage, "error", error.toString());
            }
            public void recovery() { showRecovery(); }
            public void retainHome() {
                if (camry != null) return;
                handedOff = true; HomeBridge.logEntry(HomeProxyActivity.this, "home_retained");
                handler.removeCallbacks(handoffTimeout); handler.postDelayed(handoffTimeout, 1500);
            }
        });
    }
    private void showRecovery() {
        clearCamry();
        CamryControl.recovery(this);
    }
    void clearCamry() {
        handler.removeCallbacks(startupFallback); startupOnly = false; startupWasPaused = false;
        if (camry != null) { camry.close(); camry = null; }
        handedOff = false; needsReturn = false; handler.removeCallbacks(returnToOem); handler.removeCallbacks(handoffTimeout);
    }
}
