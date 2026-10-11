package com.carmode.bridge;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.PowerManager;
import android.provider.Settings;
import com.carmode.bridge.core.AccessibilitySetting;
import org.json.JSONObject;

/** Read-only evidence. Never writes secure settings or grants accessibility. */
final class ServiceEvidence {
    static JSONObject capture(Context context) {
        JSONObject state = new JSONObject();
        try {
            String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            boolean listed = AccessibilitySetting.contains(enabled, context.getPackageName(), ModeService.class.getName());
            boolean connected = ModeService.active() != null;
            state.put("secure_service_enabled", listed);
            state.put("accessibility_master_enabled", Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1);
            state.put("connected", connected); state.put("comparison", AccessibilitySetting.status(listed, connected));
            state.put("component_enabled_setting", context.getPackageManager().getComponentEnabledSetting(new ComponentName(context, ModeService.class)));
            state.put("runtime_running", ModeRuntimeService.active() != null);
            PowerManager power = context.getSystemService(PowerManager.class);
            state.put("battery_optimization_exempt", power != null && power.isIgnoringBatteryOptimizations(context.getPackageName()));
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            state.put("background_restricted", manager != null && manager.isBackgroundRestricted());
            state.put("pid", android.os.Process.myPid());
        } catch (Exception e) {
            try { state.put("query_error", e.getClass().getSimpleName()); } catch (org.json.JSONException ignored) {}
        }
        return state;
    }
    static void record(Context context, String event) {
        CarModeApp app = CarModeApp.from(context);
        if (app.recovering()) return;
        JSONObject state = capture(context);
        app.log().record(event, "service_state", state);
        if (event.startsWith("accessibility_")) BootStorage.device(context).getSharedPreferences("service_lifecycle", Context.MODE_PRIVATE).edit()
                .putString("last_callback", event).putLong("last_timestamp", System.currentTimeMillis())
                .putInt("last_pid", android.os.Process.myPid()).putString("last_state", state.toString()).commit();
    }
    static JSONObject previous(Context context) {
        return new JSONObject(BootStorage.device(context).getSharedPreferences("service_lifecycle", Context.MODE_PRIVATE).getAll());
    }
    static String status(Context context) {
        JSONObject state = capture(context);
        if (state.has("query_error")) return context.getString(R.string.accessibility_state_unknown);
        String comparison = state.optString("comparison");
        return context.getString("enabled_connected".equals(comparison) ? R.string.accessibility_state_connected
                : "enabled_disconnected".equals(comparison) ? R.string.accessibility_state_unbound
                : R.string.accessibility_state_disabled);
    }
}
