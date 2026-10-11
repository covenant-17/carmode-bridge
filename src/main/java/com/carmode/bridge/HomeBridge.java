package com.carmode.bridge;

import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.*;
import org.json.JSONObject;
import org.json.JSONArray;
import com.carmode.bridge.core.HomeNavigation;

/** Explicit forwarding to the launcher identified in the supplied APK, never implicit HOME. */
final class HomeBridge {
    static final String OEM_PACKAGE = "com.android.launcher3";
    static final String OEM_ACTIVITY = "com.android.launcher3.Launcher";
    static ComponentName target() { return new ComponentName(OEM_PACKAGE, OEM_ACTIVITY); }
    static boolean available(Context context) {
        try {
            ActivityInfo info = context.getPackageManager().getActivityInfo(target(), 0);
            return info.enabled && info.applicationInfo.enabled && info.exported
                    && (info.permission == null || context.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED);
        } catch (Exception e) { return false; }
    }
    static String current(Context context) {
        try {
            ResolveInfo resolved = context.getPackageManager().resolveActivity(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved == null || resolved.activityInfo == null) return "unknown";
            return new ComponentName(resolved.activityInfo.packageName, resolved.activityInfo.name).flattenToString();
        } catch (Exception e) { return "unknown"; }
    }
    static boolean ownDefault(Context context) {
        return new ComponentName(context, HomeProxyActivity.class).flattenToString().equals(current(context));
    }
    static String page(Intent intent) {
        try { return intent == null ? null : intent.getStringExtra(HomeNavigation.SHOW_PAGE); }
        catch (RuntimeException error) { return null; }
    }
    static boolean navigation(Intent intent) {
        return intent != null && HomeNavigation.navigation(intent.getAction(), intent.hasCategory(Intent.CATEGORY_HOME), page(intent));
    }
    static Intent forward(Intent incoming) {
        String action = HomeNavigation.forwardAction(incoming == null ? null : incoming.getAction(), page(incoming));
        Intent result = new Intent(action).setComponent(target())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        if (HomeNavigation.ALL_APPS.equals(action)) result.putExtra(HomeNavigation.SHOW_PAGE, "allapps");
        else result.addCategory(Intent.CATEGORY_HOME);
        return result;
    }
    static Intent proxy(Context context, Intent incoming) {
        Intent result = forward(incoming).setClass(context, HomeProxyActivity.class);
        return result;
    }
    static JSONObject intentState(Intent intent) {
        JSONObject result = new JSONObject();
        try {
            if (intent != null) result.put("action", intent.getAction()).put("flags", intent.getFlags())
                    .put("categories", intent.getCategories() == null ? new JSONArray() : new JSONArray(intent.getCategories()))
                    .put("show_page", page(intent)).put("component", intent.getComponent() == null ? JSONObject.NULL : intent.getComponent().flattenToString())
                    .put("navigation", navigation(intent));
        } catch (Exception ignored) {}
        return result;
    }
    static void logEntry(android.app.Activity activity, String stage) {
        CarModeApp app = CarModeApp.from(activity);
        if (!app.recovering()) app.log().record("navigation_entry", "entry", activity.getClass().getSimpleName(),
                "stage", stage, "task_id", activity.getTaskId(), "task_root", activity.isTaskRoot(),
                "intent", intentState(activity.getIntent()));
    }
    static JSONObject snapshot(Context context) {
        JSONObject state = new JSONObject();
        try {
            state.put("default_home", current(context)).put("proxy_selected", ownDefault(context))
                    .put("oem_component", target().flattenToString()).put("oem_available", available(context));
            RoleManager roles = context.getSystemService(RoleManager.class);
            state.put("role_available", roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME));
            state.put("role_held", roles != null && roles.isRoleHeld(RoleManager.ROLE_HOME));
        } catch (Exception e) {
            try { state.put("query_error", e.toString()); } catch (Exception ignored) {}
        }
        return state;
    }
}
