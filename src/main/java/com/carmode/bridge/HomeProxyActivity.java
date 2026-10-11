package com.carmode.bridge;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.*;
import com.carmode.bridge.core.HomeProxyFlow;
import com.carmode.bridge.core.HomeReturnPolicy;
import com.carmode.bridge.core.HomeNavigation;

/** Optional HOME entry. No menu selection, settings mutation or implicit HOME recursion. */
public final class HomeProxyActivity extends Activity {
    private final Handler handler = new Handler();
    private final HomeReturnPolicy returns = new HomeReturnPolicy();
    private boolean handedOff, needsReturn, resumed, receiverRegistered;
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
        super.onNewIntent(intent); setIntent(intent);
        handler.removeCallbacks(returnToOem); needsReturn = false;
        returns.explicitNavigation(); route();
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        // Keep the old fallback only when the OEM signal has not arrived yet.
        if (needsReturn) handler.postDelayed(returnToOem, returns.returnDelayMs(SystemClock.elapsedRealtime()));
        HomeBridge.logEntry(this, "resume");
    }
    @Override protected void onPause() {
        resumed = false; handler.removeCallbacks(returnToOem); handler.removeCallbacks(handoffTimeout);
        if (handedOff) needsReturn = true;
        HomeBridge.logEntry(this, "pause"); super.onPause();
    }
    @Override public void onBackPressed() {
        handler.removeCallbacks(returnToOem); needsReturn = false; returns.explicitNavigation();
        setIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)); route();
    }
    @Override protected void onDestroy() {
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
                handedOff = true; HomeBridge.logEntry(HomeProxyActivity.this, "home_retained");
                handler.removeCallbacks(handoffTimeout); handler.postDelayed(handoffTimeout, 1500);
            }
        });
    }
    private void showRecovery() {
        handedOff = false; needsReturn = false; handler.removeCallbacks(returnToOem); handler.removeCallbacks(handoffTimeout);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(24, 24, 24, 24);
        page.setBackgroundColor(android.graphics.Color.rgb(15, 23, 35));
        TextView message = new TextView(this); message.setText(I18n.text(this, R.string.home_forward_failed)); page.addView(message);
        Button settings = new Button(this); settings.setText(I18n.text(this, R.string.home_restore));
        settings.setOnClickListener(view -> {
            try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
            catch (RuntimeException e) { Toast.makeText(this, I18n.text(this, R.string.home_settings_failed), Toast.LENGTH_LONG).show(); }
        }); page.addView(settings);
        Button app = new Button(this); app.setText("CarMode Bridge");
        app.setOnClickListener(view -> startActivity(new Intent(this, LauncherActivity.class))); page.addView(app);
        Button retry = new Button(this); retry.setText("Launcher");
        retry.setOnClickListener(view -> {
            returns.explicitNavigation(); setIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)); route();
        }); page.addView(retry);
        setContentView(page);
    }
}
