package com.carmode.bridge;

import android.app.Activity;
import android.os.*;
import android.view.*;

/** Local Back escape only. Never intercepts Home/Recents or input in another app. */
public abstract class CamryActivity extends Activity {
    private static CamryActivity foreground;
    private static final Handler BACK_HANDLER = new Handler(Looper.getMainLooper());
    private boolean backDown;
    private final Runnable external = () -> {
        if (foreground == null) CarModeApp.from(this).backSequence.reset();
    };
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) interacted();
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) backDown = true;
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            boolean pressed = backDown && !event.isCanceled(); backDown = false;
            if (pressed) onBackPressed();
        }
        return true;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) interacted();
        return super.dispatchTouchEvent(event);
    }
    private void interacted() {
        CameraStartup.cancel("user_input");
        CarModeApp.from(this).backSequence.reset(); CamryHome.cancelStartup();
    }
    @Override public void onBackPressed() {
        CameraStartup.cancel("back");
        CamryHome.cancelStartup();
        CarModeApp app = CarModeApp.from(this);
        if (!app.recovering() && (app.prefs().camryHome() || this instanceof CamryPreviewActivity)
                && app.backSequence.press(SystemClock.elapsedRealtime())) {
            CamryControl.restore(this); return;
        }
        normalBack();
    }
    protected void normalBack() { super.onBackPressed(); }
    @Override protected void onResume() {
        super.onResume(); foreground = this; CamryUi.navigation(this); CamryControl.checkStock(this);
    }
    @Override protected void onPause() {
        if (foreground == this) foreground = null;
        backDown = false; // Home controllers cancel only their own delayed launches in pause().
        BACK_HANDLER.postDelayed(external, 150); super.onPause();
    }
    @Override protected void onDestroy() {
        super.onDestroy(); // The pending external-app reset must survive this Activity finishing.
    }
}
