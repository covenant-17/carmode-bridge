package com.carmode.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import com.carmode.bridge.core.HcnModeRouter;
import com.carmode.bridge.core.ModeWakePolicy;

/**
 * Cold-start fallback. Dynamic receiver priority 999 wins when the runtime exists.
 * Target 29 implicit-broadcast delivery remains firmware-dependent, not bypassed here.
 */
public final class ModeWakeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !HcnModeRouter.MODE.equals(intent.getAction())) return;
        CarModeApp app = CarModeApp.from(context);
        if (app.recovering()) return;
        boolean running = ModeRuntimeService.active() != null;
        boolean malformed = false, hasKey = false;
        int action = 0, repeats = 0;
        try {
            if (intent.hasExtra(Intent.EXTRA_KEY_EVENT)) {
                Parcelable payload = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (!(payload instanceof KeyEvent)) malformed = true;
                else {
                    KeyEvent key = (KeyEvent) payload;
                    hasKey = true; action = key.getAction(); repeats = key.getRepeatCount();
                    malformed = repeats < 0;
                }
            }
        } catch (RuntimeException e) { malformed = true; }
        boolean overlay = false;
        try { overlay = Settings.canDrawOverlays(context); }
        catch (RuntimeException e) { app.log().record("mode_wake_permission_error", "error", e.getClass().getSimpleName()); }
        boolean allowed = ModeWakePolicy.allowed(running, app.ready(), app.prefs().hcnMode(),
                isOrderedBroadcast(), (intent.getFlags() & Intent.FLAG_RECEIVER_NO_ABORT) != 0, overlay,
                app.diagnostics().active() || (ModeService.active() != null && ModeService.active().learning()),
                malformed, hasKey, action, repeats);
        app.log().record("mode_wake_received", "runtime_running", running, "allowed", allowed,
                "ordered", isOrderedBroadcast(), "flags", intent.getFlags(), "malformed", malformed,
                "key_action", action, "repeat_count", repeats);
        if (!allowed) return; // Do not abort native delivery or duplicate an existing runtime.
        PendingResult pending = goAsync();
        if (pending == null) return;
        Handler handler = new Handler(Looper.getMainLooper());
        long deadline = SystemClock.elapsedRealtime() + 3000;
        ModeRuntimeService.sync(context.getApplicationContext(), "mode_wake");
        handler.post(new Runnable() {
            private boolean finished;
            @Override public void run() {
                if (finished) return;
                ModeRuntimeService runtime = ModeRuntimeService.active();
                if (runtime == null && SystemClock.elapsedRealtime() < deadline) {
                    handler.postDelayed(this, 25); return;
                }
                finished = true;
                try {
                    if (runtime == null) {
                        app.log().record("mode_wake_timeout", "native_delivery_preserved", true);
                    } else {
                        // Same validation, menu and captured key-pair state as ordinary HCN events.
                        // Abort the held ORIGINAL ordered broadcast only after successful menu display.
                        runtime.wakeMode(intent, () -> {
                            pending.abortBroadcast(); return pending.getAbortBroadcast();
                        });
                        app.log().record("mode_wake_completed", "aborted", pending.getAbortBroadcast());
                    }
                } catch (RuntimeException e) {
                    if (runtime != null) runtime.cancel("mode_wake_error");
                    pending.clearAbortBroadcast();
                    app.log().record("mode_wake_error", "error", e.getClass().getSimpleName());
                } finally { pending.finish(); }
            }
        });
    }
}
