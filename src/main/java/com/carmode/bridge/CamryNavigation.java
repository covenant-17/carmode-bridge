package com.carmode.bridge;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.carmode.bridge.core.ZlinkRouting;

/** Place Camry Home underneath a requested external task without intercepting external Back. */
final class CamryNavigation {
    private CamryNavigation() {}
    static boolean appWindow(Context context, Intent target) {
        Prefs prefs = new Prefs(context);
        return target.getComponent() != null && com.carmode.bridge.core.AppWindowRouting.allowed(
                prefs.camryHome(), prefs.zlinkWindow(), prefs.allAppsWindow(), CarModeApp.from(context).recovering(),
                target.getComponent().getPackageName(), context.getPackageName());
    }
    static boolean isCameraTarget(Context context, Intent target) {
        return target.getComponent() != null && com.carmode.bridge.core.CameraRouting.isTarget(
                target.getComponent().getPackageName(), new Prefs(context).component(com.carmode.bridge.core.ActionDispatcher.Action.CAMERAS));
    }
    static boolean cameraWindow(Context context, Intent target) {
        Prefs prefs = new Prefs(context);
        return target.getComponent() != null && com.carmode.bridge.core.CameraRouting.window(
                prefs.camryHome(), prefs.zlinkWindow(), CarModeApp.from(context).recovering(),
                target.getComponent().getPackageName(), prefs.component(com.carmode.bridge.core.ActionDispatcher.Action.CAMERAS));
    }
    static boolean isZlinkTarget(Context context, Intent target) {
        return target.getComponent() != null && ZlinkRouting.isTarget(target.getComponent().getPackageName(), new Prefs(context).zlinkApp());
    }
    static boolean homeOnly(Context context, Intent target) {
        Prefs prefs = new Prefs(context);
        return target.getComponent() != null && ZlinkRouting.homeOnly(prefs.camryHome(), prefs.zlinkWindow(),
                prefs.zlinkHomeOnly(), CarModeApp.from(context).recovering(), target.getComponent().getPackageName(), prefs.zlinkApp());
    }
    static void launch(Context context, Intent target, Bundle options) {
        CarModeApp app = CarModeApp.from(context);
        if (options == null && (homeOnly(context, target) || cameraWindow(context, target) || appWindow(context, target))) {
            CamryHome.cancelStartup(); DockService.hide(context);
            context.startActivity(new Intent(context, HomeProxyActivity.class).setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .putExtra("camry_window_target", target.getComponent().flattenToString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            return;
        }
        if (!app.recovering() && app.prefs().camryHome()) {
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            if (options != null && (context instanceof HomeProxyActivity || context instanceof CamryPreviewActivity)) {
                // Home already exists: rebuilding it here invalidates the measured window geometry.
                context.startActivity(target, options); return;
            }
            Intent home = new Intent(context, HomeProxyActivity.class).setAction(Intent.ACTION_MAIN)
                    .putExtra("camry_external_zlink", true)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivities(new Intent[]{home, target}, options);
        } else context.startActivity(target, options);
    }
}
