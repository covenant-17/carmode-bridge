package com.carmode.bridge;

import android.content.Context;
import android.content.SharedPreferences;
import com.carmode.bridge.core.ActionDispatcher.Action;

public final class Prefs {
    final SharedPreferences data;
    Prefs(Context context) {
        this(BootStorage.device(context).getSharedPreferences("settings", Context.MODE_PRIVATE));
    }
    Prefs(SharedPreferences storage) {
        data = storage;
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

    public boolean camryHome() { return data.getBoolean("camry_home", false); }
    public boolean zlinkAuto() { return data.getBoolean("camry_zlink_auto", false); }
    public boolean zlinkWindow() { return data.getBoolean("camry_zlink_window", false); }
    public boolean zlinkHomeOnly() { return data.getBoolean("camry_zlink_home_only", false); }
    public boolean dockEnabled() { return data.getBoolean("camry_dock", false); }
    public String zlinkApp() { return data.getString("camry_zlink_app", "com.zjinnova.zlink"); }
    public String dockPosition() {
        String value = data.getString("camry_dock_position", "bottom_right");
        return "top_left".equals(value) || "top_right".equals(value) || "bottom_left".equals(value)
                || "bottom_right".equals(value) ? value : "bottom_right";
    }
    public int dockOpacity() { return Math.max(40, Math.min(100, data.getInt("camry_dock_opacity", 95))); }
    public void camryString(String key, String value) { data.edit().putString(key, value).apply(); }
    public void dockOpacity(int value) { data.edit().putInt("camry_dock_opacity", value).apply(); }
    public boolean restoreCamry() {
        // One durable edit. Never touch MODE/runtime settings or app assignments.
        return data.edit().putBoolean("camry_home", false).putBoolean("camry_zlink_auto", false)
                .putBoolean("camry_dock", false).putBoolean("camry_zlink_window", false)
                .putBoolean("camry_zlink_home_only", false).commit();
    }

    String[] pinnedApps() {
        String[] slots = new String[Math.max(5, data.getInt("camry_pin_count", 5))];
        boolean full = true;
        for (int i = 0; i < slots.length; i++) {
            slots[i] = data.getString("camry_pin_" + i, "");
            if (slots[i].isEmpty()) full = false;
        }
        if (!full) return slots;
        String[] expanded = java.util.Arrays.copyOf(slots, slots.length + 1);
        expanded[slots.length] = ""; return expanded;
    }
    void pinApp(int slot, String component) {
        String[] slots = pinnedApps();
        if (slot < 0 || slot >= slots.length) return;
        for (int i = 0; i < slots.length; i++) if (component.equals(slots[i])) slots[i] = "";
        slots[slot] = component; savePins(slots);
    }
    void movePin(int from, int to) {
        String[] slots = pinnedApps();
        if (from < 0 || from >= slots.length || to < 0 || to >= slots.length) return;
        String previous = slots[to]; slots[to] = slots[from]; slots[from] = previous; savePins(slots);
    }
    private void savePins(String[] slots) {
        SharedPreferences.Editor edit = data.edit().putInt("camry_pin_count", slots.length);
        for (int i = 0; i < slots.length; i++) edit.putString("camry_pin_" + i, slots[i]);
        edit.apply();
    }

}
