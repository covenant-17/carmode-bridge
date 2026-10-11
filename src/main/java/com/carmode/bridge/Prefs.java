package com.carmode.bridge;

import android.content.Context;
import android.content.SharedPreferences;
import com.carmode.bridge.core.ActionDispatcher.Action;

public final class Prefs {
    final SharedPreferences data;
    Prefs(Context context) {
        data = BootStorage.device(context).getSharedPreferences("settings", Context.MODE_PRIVATE);
        // Retire automation settings, preserving app assignments and permissions.
        SharedPreferences.Editor migration = data.edit().remove("target_macrodroid");
        for (Action action : Action.values()) migration.remove("mode_" + action.name());
        migration.apply();
    }
    public String language() { return "ru".equals(data.getString("language", "en")) ? "ru" : "en"; }
    public void language(String value) { data.edit().putString("language", "ru".equals(value) ? "ru" : "en").apply(); }
    public boolean enabled() { return data.getBoolean("enabled", true); }
    public boolean autoStart() { return data.getBoolean("auto_start", true); }
    public boolean diagnostics() { return data.getBoolean("diagnostics", false); }
    public boolean hcnMode() { return data.getBoolean("hcn_mode", false); }
    public boolean musicHttpEnabled() { return data.getBoolean("music_http_enabled", false); }
    public String musicHttpUrl() { return data.getString("music_http_url", com.carmode.bridge.core.MusicHttpRequest.DEFAULT_URL); }
    public void musicHttpUrl(String value) { data.edit().putString("music_http_url", value.trim()).apply(); }
    public int keyCode() { return data.getInt("mode_key", -1); }
    public void setKeyCode(int code) { data.edit().putInt("mode_key", code).apply(); }
    public void set(String key, boolean value) { data.edit().putBoolean(key, value).apply(); }
    public String component(Action action) { return data.getString("component_" + action.name(), ""); }
    public void component(Action action, String value) { data.edit().putString("component_" + action.name(), value).apply(); }

}
