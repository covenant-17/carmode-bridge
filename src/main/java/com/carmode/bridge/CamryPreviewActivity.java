package com.carmode.bridge;

import android.content.*;
import android.os.Bundle;

/** Explicit internal entry for Dock/manual preview; never claims the Android HOME role. */
public final class CamryPreviewActivity extends CamryActivity {
    private CamryHome home;
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(CarModeApp.from(base).recovering() ? base : I18n.wrap(base));
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        open();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); clearCamry(); open();
    }
    private void open() {
        if (getIntent().getBooleanExtra("restore", false)) { CamryControl.restore(this); return; }
        if (CarModeApp.from(this).recovering()) { CamryControl.recovery(this); return; }
        if (getIntent().getBooleanExtra("apps", false)) { CamryAppsActivity.open(this); finish(); return; }
        try {
            home = new CamryHome(this); home.show(getIntent().getBooleanExtra("apps", false));
        } catch (RuntimeException failure) { if (home != null) home.close(); home = null; CamryControl.recovery(this); }
    }
    @Override protected void normalBack() { if (home != null) home.back(); else super.normalBack(); }
    @Override protected void onPause() { if (home != null) home.pause(); super.onPause(); }
    @Override protected void onStop() { super.onStop(); if (home != null) home.stopped(); }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused); if (home != null) home.focusChanged(focused);
    }
    @Override protected void onResume() { super.onResume(); if (home != null) home.resume(); }
    @Override protected void onDestroy() { if (home != null) home.close(); super.onDestroy(); }
    void clearCamry() { if (home != null) { home.close(); home = null; } }
}
