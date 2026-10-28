package com.carmode.bridge;

import android.app.Activity;
import android.content.*;
import android.provider.Settings;
import android.widget.Toast;

/** Recovery must also work before normal application/logger initialization. */
final class CamryControl {
    private CamryControl() {}
    static boolean available(Context context) {
        return HomeBridge.available(context) && new Intent(Settings.ACTION_HOME_SETTINGS)
                .resolveActivity(context.getPackageManager()) != null;
    }
    static void error(Context context, int resource) {
        String message;
        try { message = I18n.text(context, resource); }
        catch (RuntimeException failure) { message = context.getString(resource); }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
    static boolean reset(Context context) {
        CameraStartup.cancel("rollback");
        // Stop pending foreground work even if storage subsequently fails.
        CamryHome.cancelStartup();
        DockService.hide(context);
        CarModeApp app = CarModeApp.from(context);
        app.backSequence.reset();
        try {
            if (!new Prefs(context).restoreCamry()) { error(context, R.string.camry_save_failed); return false; }
            if (!app.recovering()) app.log().record("camry_restored", "mode_settings_preserved", true);
            return true;
        } catch (RuntimeException failure) { error(context, R.string.camry_save_failed); return false; }
    }
    static void restore(Activity activity) {
        if (!reset(activity)) { recovery(activity); return; }
        try {
            if (!HomeBridge.available(activity)) throw new IllegalStateException("OEM unavailable");
            activity.startActivity(HomeBridge.forward(null).addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME));
            // Keep a recovery path if firmware accepts the request without displaying OEM Home.
            recovery(activity);
        } catch (RuntimeException failure) { recovery(activity); error(activity, R.string.home_forward_failed); }
    }
    static void chooseStock(Activity activity) {
        reset(activity); // A disk failure must never prevent access to the system escape route.
        try { new Prefs(activity).set("camry_check_home", true); }
        catch (RuntimeException ignored) {}
        try { activity.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
        catch (RuntimeException failure) { recovery(activity); error(activity, R.string.home_settings_failed); }
    }
    static void checkStock(Activity activity) {
        try {
            Prefs prefs = new Prefs(activity);
            if (!prefs.data.getBoolean("camry_check_home", false)) return;
            prefs.set("camry_check_home", false);
            boolean stock = HomeBridge.target().flattenToString().equals(HomeBridge.current(activity));
            error(activity, stock ? R.string.camry_stock_selected : R.string.camry_stock_not_selected);
        } catch (RuntimeException ignored) { /* System settings remain available if storage is broken. */ }
    }
    static void recovery(Activity activity) {
        if (activity instanceof HomeProxyActivity) ((HomeProxyActivity) activity).clearCamry();
        if (activity instanceof CamryPreviewActivity) ((CamryPreviewActivity) activity).clearCamry();
        if (activity instanceof CamryAppsActivity) ((CamryAppsActivity) activity).clearCamry();
        android.widget.LinearLayout page = CamryUi.page(activity);
        CamryUi.text(page, I18n.text(activity, R.string.camry_recovery), 24);
        CamryUi.button(page, R.string.camry_restore, () -> restore(activity));
        CamryUi.button(page, R.string.camry_choose_stock, () -> chooseStock(activity));
        CamryUi.button(page, R.string.camry_settings, () -> activity.startActivity(new Intent(activity, LauncherActivity.class)));
        activity.setContentView(page);
    }
}
