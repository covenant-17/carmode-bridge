package com.carmode.bridge;

import android.app.ActivityOptions;
import android.content.*;
import android.os.Bundle;

/** Normal opaque application task for Apps, separate from the HOME task underneath OEM freeform windows. */
public final class CamryAppsActivity extends CamryActivity {
    private CamryHome home;
    static void open(Context context) {
        open(context, -1);
    }
    static void pick(Context context, int slot) { open(context, slot); }
    private static void open(Context context, int slot) {
        CamryHome.cancelStartup(); DockService.hide(context);
        Intent intent = new Intent(context, CamryAppsActivity.class)
                .putExtra("camry_pin_slot", slot)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Bundle fullscreen = ActivityOptions.makeBasic().toBundle();
        // Android 10 ActivityOptions fullscreen mode; avoid inheriting a freeform caller's window mode.
        fullscreen.putInt("android.activity.windowingMode", 1);
        try { context.startActivity(intent, fullscreen); }
        catch (RuntimeException rejected) {
            try { context.startActivity(intent); }
            catch (RuntimeException failure) { CamryControl.error(context, R.string.camry_launch_failed); }
        }
    }
    boolean picking() { int slot = getIntent().getIntExtra("camry_pin_slot", -1); return slot >= 0 && slot < 5; }
    void selected(AppResolver.Entry entry) {
        if (!picking()) return;
        CarModeApp.from(this).prefs().pinApp(getIntent().getIntExtra("camry_pin_slot", -1), entry.component.flattenToString());
        goHome();
    }
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(CarModeApp.from(base).recovering() ? base : I18n.wrap(base));
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); showApps();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); clearCamry(); showApps();
    }
    private void showApps() {
        if (CarModeApp.from(this).recovering()) { CamryControl.recovery(this); return; }
        home = new CamryHome(this); home.show(true);
    }
    void goHome() {
        CamryHome.cancelStartup();
        try {
            startActivity(new Intent(this, HomeProxyActivity.class).setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        } catch (RuntimeException failure) { CamryControl.error(this, R.string.camry_launch_failed); }
    }
    @Override protected void normalBack() { DockService.transition(this); finish(); }
    @Override protected void onResume() { super.onResume(); if (home != null) home.resume(); }
    @Override protected void onPause() { if (home != null) home.pause(); super.onPause(); }
    @Override protected void onDestroy() { clearCamry(); super.onDestroy(); }
    void clearCamry() { if (home != null) { home.close(); home = null; } }
}
