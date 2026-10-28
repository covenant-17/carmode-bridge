package com.carmode.bridge;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;

/** OEM-style bounded launch request. Firmware decides whether an external task uses these bounds. */
final class CamryAppWindow {
    // AOSP Android 10 ActivityOptions.KEY_LAUNCH_WINDOWING_MODE / WINDOWING_MODE_FREEFORM.
    // Internal bundle protocol, deliberately limited to the opt-in experiment; no reflection/grants.
    private static final String WINDOWING_MODE = "android.activity.windowingMode";
    private static final int FREEFORM = 5;
    private CamryAppWindow() {}

    static Rect bounds(Activity activity, View area) {
        Rect bounds = new Rect(), usable = new Rect();
        if (area == null || !area.isAttachedToWindow() || !area.getLocalVisibleRect(bounds)
                || !activity.hasWindowFocus() || activity.isFinishing()) return null;
        int[] origin = new int[2]; area.getLocationOnScreen(origin);
        bounds.offset(origin[0], origin[1]);
        area.getWindowVisibleDisplayFrame(usable);
        if (!bounds.intersect(usable) || bounds.width() < CamryUi.dp(activity, 160)
                || bounds.height() < CamryUi.dp(activity, 96)) return null;
        return bounds;
    }
    static boolean launch(Activity activity, View area, String selectedTarget) {
        CarModeApp app = CarModeApp.from(activity);
        Rect bounds = bounds(activity, area);
        if (bounds == null) return false;
        try {
            Intent intent = AppResolver.resolve(activity, selectedTarget == null ? app.prefs().zlinkApp() : selectedTarget);
            // Request a bounded task, without LAUNCH_ADJACENT forcing adjacent/split placement.
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ActivityOptions options = ActivityOptions.makeBasic().setLaunchBounds(bounds);
            Bundle request = options.toBundle();
            request.putInt(WINDOWING_MODE, FREEFORM);
            DockService.hide(activity);
            boolean modeRequested = true;
            try { CamryNavigation.launch(activity, intent, request); }
            catch (RuntimeException rejected) {
                // Some OEM builds reject the internal mode hint but accept their bounds-only path.
                if (!app.recovering()) app.log().record("camry_app_window_mode_rejected", "error", rejected.toString());
                modeRequested = false;
                CamryNavigation.launch(activity, intent, options.toBundle());
            }
            if (!app.recovering()) app.log().record("camry_app_window_requested",
                    "component", intent.getComponent().flattenToShortString(), "bounds", bounds.toShortString(),
                    "freeform_feature", activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT),
                    "freeform_mode_requested", modeRequested,
                    "foreground_confirmed", false, "bounds_confirmed", false);
            return true;
        } catch (Exception error) {
            if (!app.recovering()) app.log().record("camry_app_window_failed", "error", error.toString());
            return false;
        }
    }
}
