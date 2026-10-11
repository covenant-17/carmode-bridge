package com.carmode.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_USER_UNLOCKED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        BootEvidence.record(context, "broadcast_received", action);
        CarModeApp app = CarModeApp.from(context);
        if (BootStorage.unlocked(context)) app.afterUnlock();
        if (app.recovering()) {
            BootEvidence.record(context, "startup_skipped", BootStorage.ready(context) ? "pending_crash" : "awaiting_storage_migration");
            return;
        }
        app.log().record("boot_signal",
                "intent_action", intent.getAction(), "auto_start", app.prefs().autoStart(), "handling_enabled", app.prefs().enabled());
        if (app.prefs().autoStart()) ModeRuntimeService.sync(context, action);
        else BootEvidence.record(context, "startup_skipped", "auto_start_disabled");
        // Android binds a user-enabled AccessibilityService itself; never start it as a normal service.
        ModeService service = ModeService.active();
        if (service != null) service.cancel("boot");
    }
}
