package com.carmode.bridge;

import android.app.*;
import android.content.Intent;
import android.widget.*;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.*;

/** Current-tab controls; no changes to MODE or the Archive layout. */
final class CamrySettings {
    private static final String[] CORNERS = {"top_left", "top_right", "bottom_left", "bottom_right"};
    private final Activity activity;
    private final Prefs prefs;
    private Switch home, auto, dock, window, homeOnly, allApps;
    private TextView windowHint;
    private Button target;
    private boolean refreshing;
    private CamrySettings(Activity activity) { this.activity = activity; prefs = new Prefs(activity); }
    static CamrySettings render(Activity activity, LinearLayout parent) {
        CamrySettings settings = new CamrySettings(activity); settings.build(parent); return settings;
    }
    private void build(LinearLayout page) {
        CamryUi.text(page, "Camry OS", 25);
        home = toggle(page, R.string.camry_home_mode, prefs.camryHome(), value -> {
            if (value && !CamryControl.available(activity)) {
                CamryControl.error(activity, R.string.camry_no_exit); refresh(); return;
            }
            prefs.set("camry_home", value);
            if (!value) { prefs.set("camry_zlink_auto", false); prefs.set("camry_zlink_home_only", false); prefs.set("camry_all_apps_window", false); }
            refresh();
        });
        auto = toggle(page, R.string.camry_auto_zlink, prefs.zlinkAuto(), value -> {
            prefs.set("camry_zlink_auto", value);
        });
        window = toggle(page, R.string.camry_zlink_window, prefs.zlinkWindow(), value -> {
            prefs.set("camry_zlink_window", value);
            if (!value) { prefs.set("camry_zlink_home_only", false); prefs.set("camry_all_apps_window", false); }
            refresh();
        });
        homeOnly = toggle(page, R.string.camry_zlink_home_only, prefs.zlinkHomeOnly(), value -> {
            if (value && !CamryControl.available(activity)) {
                CamryControl.error(activity, R.string.camry_no_exit); refresh(); return;
            }
            android.content.SharedPreferences.Editor edit = prefs.data.edit().putBoolean("camry_zlink_home_only", value);
            if (value) edit.putBoolean("camry_home", true).putBoolean("camry_zlink_window", true);
            edit.apply(); refresh();
        });
        homeOnly.setTooltipText(I18n.text(activity, R.string.camry_zlink_home_only_hint));
        allApps = toggle(page, R.string.camry_all_apps_window, prefs.allAppsWindow(), value -> {
            if (value && !CamryControl.available(activity)) {
                CamryControl.error(activity, R.string.camry_no_exit); refresh(); return;
            }
            android.content.SharedPreferences.Editor edit = prefs.data.edit().putBoolean("camry_all_apps_window", value);
            if (value) edit.putBoolean("camry_home", true).putBoolean("camry_zlink_window", true);
            edit.apply(); refresh();
        });
        allApps.setTooltipText(I18n.text(activity, R.string.camry_all_apps_window_hint));
        windowHint = CamryUi.text(page, I18n.text(activity, R.string.camry_window_hint), 16);
        dock = toggle(page, R.string.camry_dock, prefs.dockEnabled(), value -> prefs.set("camry_dock", value));
        CamryUi.button(page, R.string.camry_help, () -> Toast.makeText(activity,
                I18n.text(activity, R.string.camry_settings_hint), Toast.LENGTH_LONG).show());
        CamryUi.button(page, R.string.camry_show_dock, () -> DockService.request(activity));
        CamryUi.button(page, R.string.camry_hide_dock, () -> DockService.hide(activity));
        CamryUi.button(page, R.string.camry_position, () -> new AlertDialog.Builder(activity).setTitle(R.string.camry_corner_position)
                .setSingleChoiceItems(new String[]{I18n.text(activity, R.string.camry_top_left), I18n.text(activity, R.string.camry_top_right),
                        I18n.text(activity, R.string.camry_bottom_left), I18n.text(activity, R.string.camry_bottom_right)}, positionIndex(), (dialog, which) -> {
                    prefs.camryString("camry_dock_position", CORNERS[which]); dialog.dismiss();
                }).setNegativeButton(R.string.cancel, null).show());
        CamryUi.button(page, R.string.camry_opacity, () -> {
            LinearLayout box = CamryUi.page(activity);
            TextView label = CamryUi.text(box, prefs.dockOpacity() + "%", 18);
            SeekBar seek = new SeekBar(activity); seek.setMax(60); seek.setProgress(prefs.dockOpacity() - 40); box.addView(seek);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar v, int p, boolean user) { label.setText((p + 40) + "%"); }
                public void onStartTrackingTouch(SeekBar v) {}
                public void onStopTrackingTouch(SeekBar v) {}
            });
            new AlertDialog.Builder(activity).setTitle(R.string.camry_opacity).setView(box)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> prefs.dockOpacity(seek.getProgress() + 40))
                    .setNegativeButton(R.string.cancel, null).show();
        });
        target = CamryUi.button(page, "", this::pickZlink);
        CamryUi.button(page, R.string.camry_open_home, () -> activity.startActivity(new Intent(activity, CamryPreviewActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)));
        CamryUi.button(page, R.string.camry_restore, () -> CamryControl.restore(activity));
        CamryUi.button(page, R.string.camry_choose_stock, () -> CamryControl.chooseStock(activity));
        refresh();
    }
    void refresh() {
        refreshing = true;
        home.setChecked(prefs.camryHome()); auto.setChecked(prefs.zlinkAuto()); dock.setChecked(prefs.dockEnabled());
        window.setChecked(prefs.zlinkWindow());
        homeOnly.setChecked(prefs.zlinkHomeOnly());
        allApps.setChecked(prefs.allAppsWindow());
        windowHint.setVisibility(prefs.zlinkWindow() ? android.view.View.VISIBLE : android.view.View.GONE);
        target.setText(I18n.text(activity, R.string.camry_select_zlink) + "\n" + prefs.zlinkApp());
        refreshing = false;
    }
    private int positionIndex() { return Arrays.asList(CORNERS).indexOf(prefs.dockPosition()); }
    private interface Change { void changed(boolean value); }
    private Switch toggle(LinearLayout page, int title, boolean checked, Change change) {
        Switch control = new Switch(activity); control.setText(title); control.setTextSize(19);
        control.setMinHeight(CamryUi.dp(activity, 56)); control.setChecked(checked);
        control.setOnCheckedChangeListener((button, value) -> { if (!refreshing) change.changed(value); });
        page.addView(control); return control;
    }
    private void pickZlink() {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            try {
                List<AppResolver.Entry> entries = AppResolver.list(activity, false);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    String[] labels = new String[entries.size() + 1]; labels[0] = "ZLink5 (com.zjinnova.zlink)";
                    for (int i = 0; i < entries.size(); i++) labels[i + 1] = entries.get(i).toString();
                    new AlertDialog.Builder(activity).setTitle(R.string.camry_select_zlink).setItems(labels, (dialog, which) -> {
                        prefs.camryString("camry_zlink_app", which == 0 ? "com.zjinnova.zlink" : entries.get(which - 1).component.flattenToString());
                        refresh();
                    }).setNegativeButton(R.string.cancel, null).show();
                });
            } catch (RuntimeException failure) {
                activity.runOnUiThread(() -> { if (!activity.isDestroyed()) CamryControl.error(activity, R.string.camry_launch_failed); });
            } finally { worker.shutdown(); }
        });
    }
}
