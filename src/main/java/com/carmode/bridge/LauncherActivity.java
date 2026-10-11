package com.carmode.bridge;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Route recovery before loading the regular settings Activity or its view initialization. */
public final class LauncherActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        HomeBridge.logEntry(this, "create");
        if (HomeBridge.navigation(getIntent())) {
            startActivity(HomeBridge.proxy(this, getIntent()));
            finish(); return;
        }
        String target = CarModeApp.from(this).recovering()
                ? "com.carmode.bridge.CrashActivity" : "com.carmode.bridge.MainActivity";
        startActivity(new Intent().setClassName(this, target));
        finish();
    }
}
