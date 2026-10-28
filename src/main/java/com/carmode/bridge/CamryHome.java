package com.carmode.bridge;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;
import com.carmode.bridge.core.ZlinkStartupPolicy;
import com.carmode.bridge.core.ZlinkReturnPolicy;
import com.carmode.bridge.core.HomeWindowEntry;
import java.util.*;
import java.util.concurrent.*;

/** View/controller hosted by the existing HOME component; no competing HOME registration. */
final class CamryHome {
    private static final Handler STARTUP = new Handler(Looper.getMainLooper());
    private static EventLog pendingLog;
    private static CamryHome pendingOwner;
    static void cancelStartup() {
        STARTUP.removeCallbacksAndMessages(null);
        if (pendingLog != null) pendingLog.record("camry_zlink_startup_skipped", "reason", "navigation_or_settings_changed");
        pendingLog = null;
        pendingOwner = null;
    }
    private final Activity activity;
    private final Prefs prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView windowArea;
    private boolean renderedWindowMode;
    private boolean apps, closed;
    private boolean externalZlink;
    private boolean zlinkLostFocus;
    private String requestedWindowTarget;
    private String requestedTargetSelection;
    private boolean cameraWindow;
    private boolean stopped;
    private boolean reopenOnResume;
    private boolean appsInFront;
    private final HomeWindowEntry windowEntry = new HomeWindowEntry();
    private final ZlinkReturnPolicy windowReturn = new ZlinkReturnPolicy();
    private String renderedLanguage;
    private int renderId;
    private CamryPinnedApps pinned;
    CamryHome(Activity activity) { this.activity = activity; prefs = CarModeApp.from(activity).prefs(); }
    void show(boolean showApps) {
        if (pinned != null) { pinned.close(); pinned = null; }
        renderedLanguage = prefs.language();
        renderedWindowMode = prefs.zlinkWindow(); windowArea = null;
        apps = showApps; final int generation = ++renderId;
        externalZlink = !apps && activity.getIntent().getBooleanExtra("camry_external_zlink", false);
        activity.getIntent().removeExtra("camry_external_zlink");
        String requested = activity.getIntent().getStringExtra("camry_window_target");
        activity.getIntent().removeExtra("camry_window_target");
        cameraWindow = false;
        requestedWindowTarget = null;
        requestedTargetSelection = null;
        if (requested != null && com.carmode.bridge.core.ZlinkRouting.isTarget(
                requested.contains("/") ? requested.substring(0, requested.indexOf('/')) : requested, prefs.zlinkApp())) {
            requestedWindowTarget = requested; requestedTargetSelection = prefs.zlinkApp();
        } else if (requested != null && com.carmode.bridge.core.CameraRouting.isTarget(
                requested.contains("/") ? requested.substring(0, requested.indexOf('/')) : requested,
                prefs.component(com.carmode.bridge.core.ActionDispatcher.Action.CAMERAS))) {
            cameraWindow = true;
            requestedWindowTarget = requested;
            requestedTargetSelection = prefs.component(com.carmode.bridge.core.ActionDispatcher.Action.CAMERAS);
        }
        zlinkLostFocus = false;
        reopenOnResume = false;
        appsInFront = false;
        windowEntry.shown(!apps && prefs.camryHome() && renderedWindowMode, externalZlink);
        windowReturn.reset();
        if (externalZlink) windowReturn.launched();
        LinearLayout page = CamryUi.page(activity);
        if (apps || renderedWindowMode) page.setPadding(0, 0, 0, 0);
        if (apps || renderedWindowMode) activity.setContentView(page);
        else {
            ScrollView scroll = new ScrollView(activity); scroll.setFillViewport(true); scroll.addView(page);
            activity.setContentView(scroll);
        }
        if (apps) {
            LinearLayout body = new LinearLayout(activity);
            page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
            LinearLayout appContent = new LinearLayout(activity); appContent.setOrientation(LinearLayout.VERTICAL);
            int padding = CamryUi.dp(activity, 16); appContent.setPadding(padding, padding, padding, padding);
            body.addView(appContent, new LinearLayout.LayoutParams(0, -1, 1));
            addSidebar(body);
            if (activity instanceof CamryAppsActivity && ((CamryAppsActivity) activity).picking())
                CamryUi.text(appContent, I18n.text(activity, R.string.camry_pin_choose), 22);
            CamryUi.button(appContent, R.string.camry_home, () -> {
                if (activity instanceof CamryAppsActivity) ((CamryAppsActivity) activity).goHome();
                else show(false);
            });
            TextView loading = CamryUi.text(appContent, I18n.text(activity, R.string.camry_loading), 18);
            CamryAppGrid grid = new CamryAppGrid(activity);
            appContent.addView(grid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
            CamryUi.button(appContent, R.string.camry_restore, () -> CamryControl.restore(activity));
            worker.execute(() -> {
                try {
                    List<AppResolver.Entry> entries = AppResolver.list(activity, false);
                    List<android.graphics.drawable.Drawable> icons = new ArrayList<>();
                    for (AppResolver.Entry entry : entries) {
                        try { icons.add(activity.getPackageManager().getActivityIcon(entry.component)); }
                        catch (Exception unavailable) { icons.add(activity.getPackageManager().getDefaultActivityIcon()); }
                    }
                    activity.runOnUiThread(() -> {
                        if (closed || activity.isFinishing() || generation != renderId) return;
                        appContent.removeView(loading);
                        if (entries.isEmpty()) CamryUi.text(appContent, I18n.text(activity, R.string.camry_no_apps), 18);
                        grid.apps(entries, icons, entry -> {
                            if (activity instanceof CamryAppsActivity && ((CamryAppsActivity) activity).picking()) {
                                ((CamryAppsActivity) activity).selected(entry); return;
                            }
                            reopenOnResume = true;
                            DockService.hide(activity);
                            try {
                                Intent target = AppResolver.resolve(activity, entry.component.flattenToString());
                                boolean camera = CamryNavigation.cameraWindow(activity, target);
                                if (CamryNavigation.isZlinkTarget(activity, target) || camera)
                                    CamryNavigation.launch(activity, target, null);
                                else activity.startActivity(target);
                                if ("ivicar.cn.ivicaravm".equals(entry.component.getPackageName()) && !camera) DockService.transition(activity);
                            }
                            catch (Exception error) { CamryControl.error(activity, R.string.camry_launch_failed); }
                        });
                    });
                } catch (RuntimeException error) {
                    activity.runOnUiThread(() -> { if (!closed && generation == renderId) loading.setText(R.string.camry_launch_failed); });
                }
            });
        } else if (renderedWindowMode) {
            showWindow(page);
        } else {
            LinearLayout primary = new LinearLayout(activity); primary.setOrientation(LinearLayout.HORIZONTAL); page.addView(primary);
            Button androidAuto = CamryUi.button(primary, R.string.camry_android_auto, () -> launchZlink(activity));
            androidAuto.setTextSize(23);
            android.graphics.drawable.GradientDrawable hero = CamryUi.panel(activity); hero.setColor(android.graphics.Color.rgb(26, 64, 73));
            androidAuto.setBackgroundTintList(null); androidAuto.setBackground(hero);
            int heroPadding = CamryUi.dp(activity, 16);
            androidAuto.setPadding(heroPadding, heroPadding, heroPadding, heroPadding);
            androidAuto.setLayoutParams(new LinearLayout.LayoutParams(0, CamryUi.dp(activity, 112), 2));
            Button camera = CamryUi.button(primary, R.string.camera, this::openCamera);
            LinearLayout.LayoutParams cameraSize = new LinearLayout.LayoutParams(0, CamryUi.dp(activity, 112), 1);
            cameraSize.leftMargin = CamryUi.dp(activity, 12); camera.setLayoutParams(cameraSize);
            LinearLayout secondary = new LinearLayout(activity); secondary.setOrientation(LinearLayout.HORIZONTAL); page.addView(secondary);
            Button applications = CamryUi.button(secondary, R.string.camry_apps, this::openApps);
            Button settings = CamryUi.button(secondary, R.string.camry_settings, () -> openSettings(new Intent(activity, MainActivity.class)));
            applications.setLayoutParams(new LinearLayout.LayoutParams(0, CamryUi.dp(activity, 64), 1));
            settings.setLayoutParams(new LinearLayout.LayoutParams(0, CamryUi.dp(activity, 64), 1));
            CamryUi.button(page, R.string.camry_show_dock, () -> DockService.request(activity));
        }
        if (!apps && !renderedWindowMode) CamryUi.button(page, R.string.camry_restore, () -> CamryControl.restore(activity));
        if (windowArea != null) windowArea.post(this::focused);
    }
    private void showWindow(LinearLayout page) {
        LinearLayout body = new LinearLayout(activity);
        page.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        body.addView(content, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        windowArea = new TextView(activity);
        windowArea.setText(I18n.text(activity, cameraWindow ? R.string.camry_camera_window_area : R.string.camry_window_area));
        windowArea.setTextSize(21); windowArea.setTextColor(CamryUi.ACCENT);
        windowArea.setGravity(Gravity.CENTER);
        windowArea.setFocusable(true); windowArea.setOnClickListener(v -> launchWindow());
        content.addView(windowArea, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        addSidebar(body);
    }
    private void addSidebar(LinearLayout body) {
        LinearLayout side = new LinearLayout(activity); side.setOrientation(LinearLayout.VERTICAL);
        int inset = CamryUi.dp(activity, 6); side.setPadding(inset, inset, inset, inset);
        body.addView(side, new LinearLayout.LayoutParams(CamryUi.dp(activity, 64), LinearLayout.LayoutParams.MATCH_PARENT));
        if (cameraWindow) CamryUi.iconButton(side, R.string.camry_music, () -> launchZlink(activity));
        else CamryUi.iconButton(side, R.string.camera, this::openCamera);
        CamryUi.iconButton(side, R.string.camry_apps, this::openApps);
        CamryUi.iconButton(side, R.string.camry_settings, () -> openSettings(new Intent(activity, MainActivity.class)));
        CamryUi.iconButton(side, R.string.camry_system_settings, () -> openSettings(new Intent(Settings.ACTION_SETTINGS)));
        pinned = new CamryPinnedApps(activity, side, slot -> {
            windowEntry.cancel(); windowReturn.reset();
            appsInFront = true; reopenOnResume = true;
            CamryAppsActivity.pick(activity, slot);
        });
    }
    void openApps() {
        windowEntry.cancel(); windowReturn.reset();
        appsInFront = true; reopenOnResume = true;
        CamryAppsActivity.open(activity);
    }
    private void openCamera() {
        windowEntry.cancel(); windowReturn.reset(); externalZlink = false; reopenOnResume = true;
        new Actions(activity).select(0, "CAMRY_HOME");
    }
    private void openSettings(Intent intent) {
        externalZlink = false; windowReturn.reset(); windowEntry.cancel(); reopenOnResume = true;
        try { activity.startActivity(intent); }
        catch (RuntimeException error) { CamryControl.error(activity, R.string.camry_launch_failed); }
    }
    void back() { externalZlink = false; zlinkLostFocus = false; windowEntry.cancel(); windowReturn.reset(); if (apps) show(false); /* root stays for the five-Back sequence */ }
    void resume() {
        if (pinned != null) pinned.refresh();
        stopped = false;
        appsInFront = false;
        DockService.sidebarShown(activity);
        if (!prefs.language().equals(renderedLanguage) || renderedWindowMode != prefs.zlinkWindow()) show(apps);
        if (reopenOnResume) {
            reopenOnResume = false;
            if (externalZlink) windowReturn.launched();
            windowEntry.shown(!apps && prefs.camryHome() && prefs.zlinkWindow(), false);
        }
        focused();
    }
    void focused() {
        if (closed || stopped || !activity.hasWindowFocus()) return;
        boolean eligible = !apps && prefs.camryHome() && prefs.zlinkWindow() && !CarModeApp.from(activity).recovering();
        if (windowReturn.take(eligible)) {
            windowEntry.cancel();
            CarModeApp.from(activity).log().record(cameraWindow ? "camry_camera_return_window" : "camry_zlink_return_window", "reason", "fullscreen_return", "foreground_confirmed", false);
            launchWindow(true);
        } else if (windowEntry.take(eligible)) {
            CarModeApp.from(activity).log().record(cameraWindow ? "camry_camera_home_window" : "camry_zlink_home_window", "reason", "home_opened", "foreground_confirmed", false);
            launchWindow();
        }
    }
    void focusChanged(boolean focused) {
        if (!focused) { if (externalZlink && !appsInFront) zlinkLostFocus = true; return; }
        if (zlinkLostFocus && externalZlink && !appsInFront) {
            zlinkLostFocus = false; windowReturn.returned();
        }
        focused();
    }
    void pause() { if (pinned != null) pinned.close(); if (pendingOwner == this) cancelStartup(); }
    private void releaseStartup() { if (pendingOwner == this) pendingOwner = null; }
    void stopped() {
        stopped = true;
        DockService.sidebarHidden(activity);
        if (externalZlink && !appsInFront) windowReturn.covered();
    }
    void close() { closed = true; pause(); DockService.sidebarHidden(activity); windowEntry.cancel(); windowReturn.reset(); worker.shutdownNow(); }
    private void launchWindow() { launchWindow(false); }
    private void launchWindow(boolean restoring) {
        cancelStartup();
        pendingOwner = this;
        // Wait for two matching screen rectangles; do not launch into a transient boot/inset layout.
        final android.graphics.Rect[] previous = {null};
        STARTUP.post(new Runnable() {
            int attempts;
            public void run() {
                if (closed || apps || !prefs.zlinkWindow()) { releaseStartup(); return; }
                android.graphics.Rect current = activity.hasWindowFocus() ? CamryAppWindow.bounds(activity, windowArea) : null;
                if (current != null && current.equals(previous[0])) {
                    releaseStartup();
                    externalZlink = true;
                    if (!restoring) windowReturn.launched();
                    String selection = cameraWindow ? prefs.component(com.carmode.bridge.core.ActionDispatcher.Action.CAMERAS) : prefs.zlinkApp();
                    String destination = selection.equals(requestedTargetSelection) ? requestedWindowTarget : null;
                    if (cameraWindow && destination == null)
                        destination = com.carmode.bridge.core.ActionDispatcher.target(com.carmode.bridge.core.ActionDispatcher.Action.CAMERAS, selection);
                    if (CamryAppWindow.launch(activity, windowArea, destination)) {
                        return;
                    }
                    if (restoring || prefs.zlinkHomeOnly() || cameraWindow) { windowReturn.reset(); CamryControl.error(activity, R.string.camry_launch_failed); return; }
                    CamryControl.error(activity, R.string.camry_window_fallback); launchZlink(activity); return;
                }
                previous[0] = current;
                if (++attempts < 8) STARTUP.postDelayed(this, 250);
                else { releaseStartup(); CamryControl.error(activity, R.string.camry_window_not_ready); }
            }
        });
    }
    static boolean launchZlink(Activity activity) {
        try {
            Prefs prefs = new Prefs(activity);
            CamryNavigation.launch(activity, AppResolver.resolve(activity, prefs.zlinkApp()), null);
            CarModeApp app = CarModeApp.from(activity);
            if (!app.recovering()) app.log().record("camry_zlink_launch_requested", "foreground_confirmed", false);
            return true;
        } catch (Exception error) { CamryControl.error(activity, R.string.camry_zlink_missing); return false; }
    }
    boolean startup(boolean requireCamry) {
        // Window mode fills Home on entry; the separate once-per-boot fullscreen launcher must not compete.
        if (requireCamry && prefs.camryHome() && prefs.zlinkWindow()) return false;
        CarModeApp app = CarModeApp.from(activity);
        int boot = -1;
        try { boot = Settings.Global.getInt(activity.getContentResolver(), Settings.Global.BOOT_COUNT, -1); }
        catch (RuntimeException ignored) {}
        String epoch = ZlinkStartupPolicy.epoch(boot);
        String previous = prefs.data.getString("camry_startup_epoch", "");
        boolean claimed = ZlinkStartupPolicy.claim(prefs.zlinkAuto(), app.recovering(), !apps && (!requireCamry || prefs.camryHome()), epoch,
                new ZlinkStartupPolicy.Store() {
                    public String last() { return prefs.data.getString("camry_startup_epoch", ""); }
                    public boolean save(String value) { return prefs.data.edit().putString("camry_startup_epoch", value).commit(); }
                });
        app.log().record("camry_zlink_startup", "claimed", claimed, "epoch", epoch,
                "enabled", prefs.zlinkAuto(), "apps_page", apps, "reason", claimed ? "scheduled"
                        : !prefs.zlinkAuto() ? "disabled" : epoch.equals(previous) ? "already_attempted" : "ineligible_or_storage_error");
        if (!claimed) return false;
        pendingLog = app.log();
        pendingOwner = this;
        STARTUP.postDelayed(() -> {
            pendingLog = null;
            releaseStartup();
            if (closed || !activity.hasWindowFocus() || activity.isFinishing() || app.recovering()
                    || (requireCamry && !prefs.camryHome()) || !prefs.zlinkAuto()) {
                app.log().record("camry_zlink_startup_skipped", "reason", "foreground_or_settings_changed"); return;
            }
            if (requireCamry && prefs.zlinkWindow()) launchWindow();
            else launchZlink(activity);
        }, 900);
        return true;
    }
}
