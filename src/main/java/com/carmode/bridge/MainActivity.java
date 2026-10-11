package com.carmode.bridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.*;
import com.carmode.bridge.core.ActionDispatcher;
import com.carmode.bridge.core.ActionDispatcher.Action;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private CarModeApp app;
    private Prefs prefs;
    private LinearLayout content;
    private StableScrollView scroll;
    private TextView status, logs, learning, diagnosticStatus, hcnStatus, serviceStatus;
    private Button accessibilityButton, overlayButton, exportOemButton, homeButton;
    private TextView homeStatus;
    private boolean exportingOem;
    private boolean archived;
    private boolean exportingZlink;
    private Button copyZlinkButton, shareZlinkButton;
    private boolean exportingFactory;
    private Button copyFactoryButton, shareFactoryButton;
    private boolean touchingLogs, selectingLogs;
    private Toast hintToast;
    private MenuController preview;
    private final Handler handler = new Handler();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!scroll.isScrolling()) scroll.updateInPlace(MainActivity.this::refreshText);
            handler.postDelayed(this, 750);
        }
    };
    private void refreshText() {
        ModeService service = ModeService.active();
        refreshPermissionButtons();
        if (homeStatus != null) {
            setTextIfChanged(homeStatus, getString(R.string.home_status, HomeBridge.current(this)));
            permissionLabel(homeButton, getString(R.string.home_choose), HomeBridge.ownDefault(this));
        }
        setTextIfChanged(status, getString(R.string.status_summary,
                getString(service != null ? R.string.connected : R.string.disconnected),
                getString(Settings.canDrawOverlays(this) ? R.string.allowed : R.string.not_allowed),
                prefs.hcnMode() ? "HCN MODE" : prefs.keyCode() < 0 ? getString(R.string.key_not_learned) : keyName(prefs.keyCode()),
                getString(app.ready() ? (prefs.hcnMode() ? R.string.hcn_interception_on : R.string.interception_on) : R.string.off),
                getString(prefs.diagnostics() ? R.string.on : R.string.off)));
        setTextIfChanged(hcnStatus, ModeRuntimeService.active() == null ? getString(R.string.hcn_unavailable) : ModeRuntimeService.active().hcnStatus(this));
        setTextIfChanged(serviceStatus, ServiceEvidence.status(this) + "\n" + getString(ModeRuntimeService.active() == null
                ? R.string.runtime_stopped : R.string.runtime_running));
        setTextIfChanged(learning, service != null && service.learning()
                ? getString(R.string.learning_status, keyName(service.candidate())) : getString(R.string.learning_off));
        String filters = app.diagnostics().otherFilters();
        setTextIfChanged(diagnosticStatus, getString(R.string.key_callback_status, app.log().keyCallbacks())
                + "\n" + app.diagnostics().status()
                + (filters.isEmpty() ? "" : "\n" + getString(R.string.key_filter_warning, filters)));
        // Replacing the text destroys Android's selection and copy toolbar.
        // Freeze before the long-press timeout as well as during selection.
        if (logs != null && !touchingLogs && !selectingLogs && !logs.hasSelection()) {
            setTextIfChanged(logs, app.log().recent());
        }
    }
    private void setTextIfChanged(TextView view, String text) {
        if (view != null && !text.contentEquals(view.getText())) view.setText(text);
    }
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(CarModeApp.from(base).recovering() ? base : I18n.wrap(base));
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        HomeBridge.logEntry(this, "create");
        if (redirectNavigation(getIntent())) return;
        if (CarModeApp.from(this).recovering()) {
            startActivity(new Intent(this, CrashActivity.class)); finish(); return;
        }
        app = CarModeApp.from(this); prefs = app.prefs(); app.manuallyActivated = true;
        archived = state != null && state.getBoolean("archive_tab", false);
        preview = new MenuController(this);
        app.log().record("app_opened", "intent_action", getIntent().getAction());
        ServiceEvidence.record(this, "activity_created");
        ModeRuntimeService.sync(this, "activity_opened");
        render();
    }
    private void render() {
        if (hintToast != null) { hintToast.cancel(); hintToast = null; }
        touchingLogs = false; selectingLogs = false;
        accessibilityButton = null; overlayButton = null; exportOemButton = null;
        homeButton = null; homeStatus = null;
        copyZlinkButton = null; shareZlinkButton = null;
        copyFactoryButton = null; shareFactoryButton = null;
        status = null; serviceStatus = null; hcnStatus = null; learning = null; diagnosticStatus = null; logs = null;
        scroll = new StableScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(20), dp(24), dp(24)); content.setBackgroundColor(Color.rgb(15, 23, 35));
        scroll.addView(content);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(15, 23, 35));
        RadioGroup tabs = new RadioGroup(this); tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(16), dp(8), dp(16), 0);
        RadioButton current = new RadioButton(this); current.setId(View.generateViewId());
        current.setText(R.string.current_tab); current.setTextColor(Color.WHITE); current.setMinHeight(dp(48));
        RadioButton archive = new RadioButton(this); archive.setId(View.generateViewId());
        archive.setText("Archive"); archive.setTextColor(Color.WHITE); archive.setMinHeight(dp(48));
        tabs.addView(current, new RadioGroup.LayoutParams(0, dp(52), 1));
        tabs.addView(archive, new RadioGroup.LayoutParams(0, dp(52), 1));
        tabs.check(archived ? archive.getId() : current.getId());
        tabs.setOnCheckedChangeListener((group, id) -> {
            boolean next = id == archive.getId();
            if (next == archived) return;
            archived = next;
            if (preview != null) preview.cancel("tab_changed");
            render(); refreshText();
        });
        page.addView(tabs);
        page.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);
        languagePicker();
        String version;
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException e) { version = "?"; }
        heading("CarMode Bridge · " + version);
        hint(R.string.intro);
        status = label("");
        serviceStatus = label("");
        if (!archived) {
        overlayButton = button(getString(R.string.grant_overlay), () -> openSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        refreshPermissionButtons();
        toggle(getString(R.string.handle_mode), prefs.enabled(), value -> {
            prefs.set("enabled", value); cancelMenus("disabled");
            app.log().record("settings_changed", "enabled", value);
        });
        toggle(getString(R.string.auto_start), prefs.autoStart(), value -> {
            prefs.set("auto_start", value);
            app.log().record("settings_changed", "auto_start", value);
        });
        hint(R.string.auto_start_hint);
        hint(R.string.background_hint);
        button(getString(R.string.android_app_settings), () -> openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        button(getString(R.string.battery_settings), () -> openSettings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        heading(getString(R.string.home_heading));
        hint(R.string.home_hint);
        homeStatus = label(getString(R.string.home_status, HomeBridge.current(this)));
        homeButton = button(getString(R.string.home_choose), this::requestHomeProxy);
        button(getString(R.string.home_restore), () -> {
            app.log().record("home_restore_settings_opened", "home_state", HomeBridge.snapshot(this));
            openSettings(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        heading(getString(R.string.mode_button));
        toggle(getString(R.string.hcn_mode), prefs.hcnMode(), value -> {
            cancelMenus("hcn_mode_changed"); prefs.set("hcn_mode", value);
            app.log().record("settings_changed", "hcn_mode", value);
        });
        hint(R.string.hcn_hint);
        hcnStatus = label("");
        } else {
        hint(R.string.archive_hint);
        accessibilityButton = button(getString(R.string.grant_accessibility), () -> openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        refreshPermissionButtons();
        learning = label("");
        button(getString(R.string.learn_start), () -> {
            if (ModeService.active() == null) { toast(getString(R.string.enable_accessibility_first)); return; }
            ModeService.active().learn();
        });
        button(getString(R.string.confirm_detected_key), () -> {
            ModeService service = ModeService.active();
            if (service == null || !service.learning() || service.candidate() < 0) { toast(getString(R.string.start_learning_first)); return; }
            int code = service.candidate();
            new AlertDialog.Builder(this).setTitle(getString(R.string.assign_key_title, keyName(code)))
                    .setMessage(getString(R.string.confirm_key_message))
                    .setPositiveButton(getString(R.string.assign), (dialog, which) -> {
                        if (ModeService.active() != null) ModeService.active().confirmKey(code);
                    }).setNegativeButton(getString(R.string.cancel), null).show();
        });
        button(getString(R.string.cancel_learning), () -> { if (ModeService.active() != null) ModeService.active().stopLearning(); });
        button(getString(R.string.forget_key), () -> {
            cancelMenus("key_forgotten"); prefs.setKeyCode(-1);
            if (ModeService.active() != null) ModeService.active().stopLearning();
        });
        }
        if (!archived) {
        button(getString(R.string.simulate_mode), () -> {
            if (ModeRuntimeService.active() != null && prefs.hcnMode()) ModeRuntimeService.active().simulate();
            else if (ModeService.active() != null) ModeService.active().simulate(); else preview.press("TOUCH");
        });
        hint(R.string.simulate_hint);
        heading(getString(R.string.apps_actions));
        for (Action action : Action.values()) actionSettings(action);
        }
        heading(getString(R.string.diagnostics_title));
        if (CrashReporter.store(this).reportFile().isFile()) {
            button(getString(R.string.previous_crash), () -> startActivity(new Intent(this, CrashActivity.class)));
        }
        if (archived) {
        hint(R.string.diagnostics_hint);
        hint(R.string.oem_diagnostics_hint);
        hint(R.string.test_hint);
        hint(R.string.hcn_test_hint);
        diagnosticStatus = label("");
        button(getString(R.string.test_mode), () -> startTest(DiagnosticScenarios.Kind.MODE));
        button(getString(R.string.test_no_press), () -> startTest(DiagnosticScenarios.Kind.NO_PRESS));
        button(getString(R.string.test_other), () -> startTest(DiagnosticScenarios.Kind.OTHER));
        button(getString(R.string.test_keyboard), () -> startTest(DiagnosticScenarios.Kind.KEYBOARD));
        hint(R.string.test_keyboard_hint);
        button(getString(R.string.test_cancel), () -> app.diagnostics().cancel("user_cancelled"));
        }
        button(getString(R.string.start_diagnostics), () -> {
            app.diagnostics().startRecording("user");
            if (!archived) return; // Current tab needs lifecycle logging, not the old app-discovery scan.
            if (ModeService.active() == null && ModeRuntimeService.active() == null) toast(getString(R.string.diagnostics_needs_accessibility));
            worker.execute(() -> {
                try {
                    List<AppResolver.Entry> entries = AppResolver.list(this, true);
                    int candidates = 0;
                    for (AppResolver.Entry entry : entries) {
                        if (!AppResolver.candidate(entry)) continue;
                        app.log().record("app_candidate", "label", entry.label,
                                "component", entry.component.flattenToString(), "confirmed", false);
                        candidates++;
                    }
                    app.log().record("app_scan_complete", "exported_activities", entries.size(), "candidates", candidates);
                } catch (RuntimeException e) { app.log().record("app_scan_error", "error", e.getClass().getSimpleName()); }
            });
        });
        button(getString(R.string.stop_diagnostics), () -> app.diagnostics().stopRecording("user"));
        button(getString(R.string.clear_logs), this::confirmClearLogs);
        button(getString(R.string.export_log), this::export);
        if (archived) {
        exportOemButton = button(getString(exportingOem ? R.string.export_oem_busy : R.string.export_oem_apk), this::exportOemApk);
        exportOemButton.setEnabled(!exportingOem);
        hint(R.string.export_oem_hint);
        copyZlinkButton = button(getString(R.string.copy_zlink_apk), () -> exportZlinkApk(false));
        shareZlinkButton = button(getString(R.string.share_zlink_apk), () -> exportZlinkApk(true));
        copyZlinkButton.setEnabled(!exportingZlink);
        shareZlinkButton.setEnabled(!exportingZlink);
        hint(R.string.export_zlink_hint);
        copyFactoryButton = button(getString(R.string.copy_factory_apk), () -> chooseFactoryApk(false));
        shareFactoryButton = button(getString(R.string.share_factory_apk), () -> chooseFactoryApk(true));
        factoryExportButtons();
        hint(R.string.export_factory_hint);
        }
        button(getString(R.string.copy_clipboard), () -> {
            try {
                getSystemService(ClipboardManager.class).setPrimaryClip(
                        ClipData.newPlainText(getString(R.string.log_label), logs == null ? app.log().recent() : logs.getText().toString()));
                toast(getString(R.string.log_copied));
            } catch (RuntimeException e) { toast(getString(R.string.copy_failed)); }
        });
        if (archived) {
        hint(R.string.selection_hint);
        logs = label(app.log().recent()); logs.setTextSize(12); logs.setTextIsSelectable(true);
        logs.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) touchingLogs = true;
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) touchingLogs = false;
            return false; // Preserve native selection, long press and scroll handling.
        });
        logs.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) { selectingLogs = true; return true; }
            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }
            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) { return false; }
            @Override public void onDestroyActionMode(ActionMode mode) { selectingLogs = false; }
        });
    }
    }
    private void confirmClearLogs() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.clear_logs))
                .setMessage(getString(R.string.clear_logs_message))
                .setPositiveButton(getString(R.string.clear_logs), (dialog, which) -> {
                    app.diagnostics().cancel("logs_cleared");
                    worker.execute(() -> {
                        try {
                            app.log().clear();
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                touchingLogs = false; selectingLogs = false;
                                if (logs != null) {
                                    logs.clearFocus();
                                    scroll.updateInPlace(() -> logs.setText(app.log().recent()));
                                }
                                toast(getString(R.string.logs_cleared));
                            });
                        } catch (java.io.IOException e) {
                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed()) toast(getString(R.string.clear_logs_error, e.getMessage()));
                            });
                        }
                    });
                })
                .setNegativeButton(getString(R.string.cancel), null).show();
    }
    private void startTest(DiagnosticScenarios.Kind kind) {
        cancelMenus("diagnostic_scenario");
        app.diagnostics().start(kind);
    }
    private void actionSettings(Action action) {
        int[] names = {R.string.choose_camera_app, R.string.choose_music_app, R.string.choose_maps_app};
        String target = ActionDispatcher.target(action, prefs.component(action));
        String display = target;
        try {
            android.content.Intent resolved = AppResolver.resolve(this, target);
            android.content.pm.ApplicationInfo info = getPackageManager().getApplicationInfo(resolved.getComponent().getPackageName(), 0);
            display = getPackageManager().getApplicationLabel(info).toString();
        } catch (Exception ignored) { /* Keep a missing/explicit target visible rather than resetting it. */ }
        button(getString(names[action.ordinal()]) + "\n" + display, () -> chooseApp(action));
        if (action == Action.MUSIC) {
            toggle(getString(R.string.music_http_enabled), prefs.musicHttpEnabled(), value -> {
                prefs.set("music_http_enabled", value);
                app.log().record("settings_changed", "music_http_enabled", value);
            });
            hint(R.string.music_http_hint);
            button(getString(R.string.music_http_address) + "\n" + prefs.musicHttpUrl(), this::editMusicHttpUrl);
        }
    }
    private void editMusicHttpUrl() {
        EditText input = new EditText(this); input.setSingleLine(true); input.setText(prefs.musicHttpUrl());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.music_http_address).setView(input)
                .setPositiveButton(R.string.validate_save, null).setNegativeButton(R.string.cancel, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                com.carmode.bridge.core.MusicHttpRequest.validate(input.getText().toString());
                prefs.musicHttpUrl(input.getText().toString()); dialog.dismiss(); render(); refreshText();
            } catch (IllegalArgumentException e) { input.setError(getString(R.string.music_http_invalid_url)); }
        }));
        dialog.show();
    }
    private void chooseApp(Action action) {
        toast(getString(R.string.searching_apps));
        worker.execute(() -> {
            try {
                List<AppResolver.Entry> entries = AppResolver.list(this, false);
                app.log().record("apps_discovered", "count", entries.size(), "all_activities", false);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    String[] names = new String[entries.size()];
                    for (int i = 0; i < names.length; i++) names[i] = entries.get(i).toString();
                    new AlertDialog.Builder(this).setTitle(getString(R.string.installed_apps))
                            .setItems(names, (dialog, which) -> assign(action, entries.get(which).component.flattenToString()))
                            .setNeutralButton(getString(R.string.clear_assignment), (dialog, which) -> { prefs.component(action, ""); render(); })
                            .setNegativeButton(getString(R.string.cancel), null).show();
                });
            } catch (RuntimeException e) { runOnUiThread(() -> toast(getString(R.string.search_error, e.getClass().getSimpleName()))); }
        });
    }
    private void assign(Action action, String value) {
        try {
            AppResolver.resolve(this, value); prefs.component(action, value);
            app.log().record("app_assigned", "action", action.name(), "component", value); render();
        } catch (Exception e) { toast(getString(R.string.assignment_error, e.getMessage())); }
    }
    private void export() {
        worker.execute(() -> {
            try {
                app.log().export(prefs);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Intent intent = new Intent(Intent.ACTION_SEND).setType("application/json")
                            .putExtra(Intent.EXTRA_STREAM, LogProvider.URI)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.setClipData(ClipData.newRawUri("CarMode diagnostics", LogProvider.URI));
                    try { startActivity(Intent.createChooser(intent, getString(R.string.share_log))); }
                    catch (RuntimeException e) { toast(getString(R.string.no_share_app)); }
                });
            } catch (Exception e) { runOnUiThread(() -> toast(getString(R.string.export_error, e.getMessage()))); }
        });
    }
    private void exportOemApk() {
        if (exportingOem) return;
        exportingOem = true; exportOemButton.setEnabled(false); exportOemButton.setText(R.string.export_oem_busy);
        worker.execute(() -> {
            OemApkExporter.Export result = null;
            String failure = null;
            try { result = OemApkExporter.create(getApplicationContext()); }
            catch (android.content.pm.PackageManager.NameNotFoundException e) {
                failure = getString(R.string.export_oem_missing);
                app.log().record("oem_apk_export_failed", "package", OemApkExporter.PACKAGE, "error", "NameNotFoundException");
            } catch (Exception e) {
                failure = getString(R.string.export_oem_failed, e.getClass().getSimpleName() + ": " + e.getMessage());
                app.log().record("oem_apk_export_failed", "package", OemApkExporter.PACKAGE, "error", e.getClass().getSimpleName());
            }
            final OemApkExporter.Export ready = result;
            final String message = failure;
            runOnUiThread(() -> {
                exportingOem = false;
                if (isFinishing() || isDestroyed()) return;
                if (exportOemButton != null) {
                    exportOemButton.setEnabled(true); exportOemButton.setText(R.string.export_oem_apk);
                }
                if (ready == null) { toast(message); return; }
                Intent intent = new Intent(Intent.ACTION_SEND).setType(ready.mime)
                        .putExtra(Intent.EXTRA_STREAM, ready.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri("OEM APK", ready.uri));
                try { startActivity(Intent.createChooser(intent, getString(R.string.share_oem_apk))); }
                catch (RuntimeException e) {
                    app.log().record("oem_apk_share_failed", "error", e.getClass().getSimpleName());
                    toast(getString(R.string.no_oem_share_app));
                }
            });
        });
    }
    private void exportZlinkApk(boolean share) {
        if (exportingZlink) return;
        exportingZlink = true;
        copyZlinkButton.setEnabled(false); shareZlinkButton.setEnabled(false);
        toast(getString(R.string.export_oem_busy));
        worker.execute(() -> {
            OemApkExporter.Export result = null;
            String failure = null;
            try {
                result = OemApkExporter.create(getApplicationContext(), OemApkExporter.ZLINK_PACKAGE);
                if (!share) {
                    android.net.Uri saved = ApkDownload.save(getApplicationContext(), result);
                    app.log().record("zlink_apk_saved", "uri", saved.toString(), "name", result.displayName);
                }
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                failure = getString(R.string.export_zlink_missing);
                app.log().record("zlink_apk_export_failed", "error", "NameNotFoundException");
                result = null;
            } catch (Exception e) {
                failure = getString(R.string.export_oem_failed, e.getClass().getSimpleName() + ": " + e.getMessage());
                app.log().record("zlink_apk_export_failed", "operation", share ? "share" : "downloads",
                        "error", e.getClass().getSimpleName(), "message", e.getMessage());
                result = null;
            }
            final OemApkExporter.Export ready = result;
            final String message = failure;
            runOnUiThread(() -> {
                exportingZlink = false;
                if (isFinishing() || isDestroyed()) return;
                if (copyZlinkButton != null) copyZlinkButton.setEnabled(true);
                if (shareZlinkButton != null) shareZlinkButton.setEnabled(true);
                if (ready == null) { toast(message); return; }
                if (!share) { toast(getString(R.string.zlink_apk_saved, ready.displayName)); return; }
                Intent intent = new Intent(Intent.ACTION_SEND).setType(ready.mime)
                        .putExtra(Intent.EXTRA_STREAM, ready.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri("ZLink APK", ready.uri));
                try { startActivity(Intent.createChooser(intent, getString(R.string.share_zlink_apk))); }
                catch (RuntimeException e) {
                    app.log().record("zlink_apk_share_failed", "error", e.getClass().getSimpleName());
                    toast(getString(R.string.no_oem_share_app));
                }
            });
        });
    }
    private void factoryExportButtons() {
        if (copyFactoryButton != null) copyFactoryButton.setEnabled(!exportingFactory);
        if (shareFactoryButton != null) shareFactoryButton.setEnabled(!exportingFactory);
    }
    private void chooseFactoryApk(boolean share) {
        if (exportingFactory) return;
        exportingFactory = true; factoryExportButtons();
        toast(getString(R.string.searching_apps));
        worker.execute(() -> {
            try {
                List<InstalledApkApps.Entry> entries = InstalledApkApps.list(getApplicationContext());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) { exportingFactory = false; return; }
                    if (entries.isEmpty()) {
                        exportingFactory = false; factoryExportButtons(); toast(getString(R.string.factory_apps_empty)); return;
                    }
                    String[] labels = new String[entries.size()];
                    for (int i = 0; i < labels.length; i++) labels[i] = entries.get(i).toString();
                    boolean[] selected = {false};
                    AlertDialog picker = new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.choose_factory_app))
                            .setItems(labels, (dialog, which) -> {
                                selected[0] = true;
                                exportFactoryApk(entries.get(which).packageName, share);
                            })
                            .setNegativeButton(getString(R.string.cancel), null).create();
                    picker.setOnDismissListener(dialog -> {
                        if (!selected[0]) { exportingFactory = false; factoryExportButtons(); }
                    });
                    picker.show();
                });
            } catch (RuntimeException e) {
                app.log().record("factory_app_scan_failed", "error", e.getClass().getSimpleName());
                runOnUiThread(() -> {
                    exportingFactory = false;
                    if (isFinishing() || isDestroyed()) return;
                    factoryExportButtons(); toast(getString(R.string.export_oem_failed, e.getMessage()));
                });
            }
        });
    }
    private void exportFactoryApk(String packageName, boolean share) {
        toast(getString(R.string.export_oem_busy));
        worker.execute(() -> {
            OemApkExporter.Export result = null;
            String failure = null;
            try {
                result = OemApkExporter.createFactory(getApplicationContext(), packageName);
                if (!share) {
                    android.net.Uri saved = ApkDownload.save(getApplicationContext(), result);
                    app.log().record("factory_apk_saved", "package", packageName, "uri", saved.toString());
                }
            } catch (Exception e) {
                failure = getString(R.string.export_oem_failed, e.getClass().getSimpleName() + ": " + e.getMessage());
                app.log().record("factory_apk_export_failed", "package", packageName,
                        "error", e.getClass().getSimpleName(), "message", e.getMessage());
                result = null;
            }
            final OemApkExporter.Export ready = result;
            final String message = failure;
            runOnUiThread(() -> {
                exportingFactory = false;
                if (isFinishing() || isDestroyed()) return;
                factoryExportButtons();
                if (ready == null) { toast(message); return; }
                if (!share) { toast(getString(R.string.factory_apk_saved, ready.displayName)); return; }
                Intent intent = new Intent(Intent.ACTION_SEND).setType(ready.mime)
                        .putExtra(Intent.EXTRA_STREAM, ready.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri("Factory settings APK", ready.uri));
                try { startActivity(Intent.createChooser(intent, getString(R.string.share_factory_apk))); }
                catch (RuntimeException e) {
                    app.log().record("factory_apk_share_failed", "package", packageName, "error", e.getClass().getSimpleName());
                    toast(getString(R.string.no_oem_share_app));
                }
            });
        });
    }
    private void openSettings(Intent intent) {
        try { startActivity(intent); } catch (RuntimeException e) { toast(getString(R.string.settings_unavailable)); }
    }
    private void languagePicker() {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setContentDescription(getString(R.string.language));
        RadioButton english = new RadioButton(this); english.setId(View.generateViewId());
        english.setText("EN"); english.setAllCaps(false); english.setMinHeight(dp(48));
        RadioButton russian = new RadioButton(this); russian.setId(View.generateViewId());
        russian.setText("ru"); russian.setAllCaps(false); russian.setMinHeight(dp(48));
        group.addView(english, new RadioGroup.LayoutParams(dp(100), dp(48)));
        group.addView(russian, new RadioGroup.LayoutParams(dp(100), dp(48)));
        group.check("ru".equals(prefs.language()) ? russian.getId() : english.getId());
        group.setOnCheckedChangeListener((buttons, checked) -> {
            String language = checked == russian.getId() ? "ru" : "en";
            if (!language.equals(prefs.language())) {
                prefs.language(language);
                cancelMenus("language_changed");
                recreate();
            }
        });
        content.addView(group);
    }
    private void refreshPermissionButtons() {
        boolean accessibilityEnabled = false;
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        if (manager != null) {
            for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                android.content.pm.ServiceInfo service = info.getResolveInfo().serviceInfo;
                if (getPackageName().equals(service.packageName) && ModeService.class.getName().equals(service.name)) {
                    accessibilityEnabled = true;
                    break;
                }
            }
        }
        permissionLabel(accessibilityButton, getString(R.string.grant_accessibility), accessibilityEnabled);
        permissionLabel(overlayButton, getString(R.string.grant_overlay), Settings.canDrawOverlays(this));
    }
    private void permissionLabel(Button button, String label, boolean granted) {
        if (button == null) return;
        String text = label + (granted ? " ✓" : "");
        if (text.contentEquals(button.getText())) return;
        SpannableString styled = new SpannableString(text);
        if (granted) styled.setSpan(new ForegroundColorSpan(Color.rgb(76, 217, 100)),
                text.length() - 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setText(styled);
    }
    private void cancelMenus(String reason) {
        if (preview != null) preview.cancel(reason);
        if (ModeService.active() != null) ModeService.active().cancel(reason);
        if (ModeRuntimeService.active() != null) ModeRuntimeService.active().cancel(reason);
    }
    private String keyName(int key) { return key < 0 ? getString(R.string.no_key_yet) : android.view.KeyEvent.keyCodeToString(key) + " (" + key + ")"; }
    private int dp(int value) { return (int)(getResources().getDisplayMetrics().density * value); }
    private TextView label(String text) {
        TextView view = new TextView(this); view.setText(text); view.setTextColor(Color.rgb(207, 220, 235));
        view.setTextSize(17); view.setPadding(0, dp(8), 0, dp(8)); content.addView(view); return view;
    }
    /** Inline help beside its control; Toast and native long-press tooltip never take focus. */
    private void hint(int resource) {
        String text = getString(resource);
        View anchor = content.getChildAt(content.getChildCount() - 1);
        LinearLayout row;
        Button info;
        if (anchor instanceof LinearLayout && "hint_row".equals(anchor.getTag())) {
            row = (LinearLayout) anchor;
            info = (Button) row.getChildAt(row.getChildCount() - 1);
            text = info.getTag().toString() + "\n\n" + text;
        } else {
            row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL); row.setTag("hint_row");
            if (anchor != null) {
                content.removeView(anchor);
                row.addView(anchor, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            }
            content.addView(row);
            info = new Button(this); info.setText("ⓘ"); info.setAllCaps(false);
            info.setTextSize(22); info.setMinWidth(0); info.setMinimumWidth(0);
            info.setMinHeight(0); info.setMinimumHeight(0); info.setPadding(0, 0, 0, 0);
            info.setOnClickListener(view -> {
                if (hintToast != null) hintToast.cancel();
                hintToast = Toast.makeText(this, view.getTag().toString(), Toast.LENGTH_LONG); hintToast.show();
            });
            row.addView(info, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        info.setTag(text);
        info.setContentDescription(getString(R.string.help_description, text));
        info.setTooltipText(text);
    }
    private void heading(String text) { TextView view = label(text); view.setTextSize(25); view.setTextColor(Color.WHITE); }
    private Button button(String text, Runnable action) {
        Button button = new Button(this); button.setText(text); button.setAllCaps(false); button.setMinHeight(dp(52));
        button.setOnClickListener(view -> action.run()); content.addView(button);
        return button;
    }
    private interface Toggle { void changed(boolean value); }
    private void toggle(String text, boolean checked, Toggle callback) {
        Switch view = new Switch(this); view.setText(text); view.setTextSize(19); view.setMinHeight(dp(56)); view.setChecked(checked);
        view.setOnCheckedChangeListener((button, value) -> callback.changed(value)); content.addView(view);
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private static final int HOME_REQUEST = 901;
    private void requestHomeProxy() {
        if (!HomeBridge.available(this)) { toast(getString(R.string.home_oem_missing)); return; }
        new AlertDialog.Builder(this).setTitle(R.string.home_heading).setMessage(R.string.home_confirm)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.home_choose, (dialog, which) -> {
                    app.log().record("home_role_requested", "home_state", HomeBridge.snapshot(this));
                    try {
                        android.app.role.RoleManager roles = getSystemService(android.app.role.RoleManager.class);
                        if (roles != null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                            if (roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                                openSettings(new Intent(Settings.ACTION_HOME_SETTINGS));
                            } else startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), HOME_REQUEST);
                        } else openSettings(new Intent(Settings.ACTION_HOME_SETTINGS));
                    } catch (RuntimeException e) {
                        app.log().record("home_role_request_error", "error", e.toString());
                        openSettings(new Intent(Settings.ACTION_HOME_SETTINGS));
                    }
                }).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == HOME_REQUEST) {
            app.log().record("home_role_result", "result_code", result, "home_state", HomeBridge.snapshot(this));
            refreshText();
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("archive_tab", archived);
        super.onSaveInstanceState(state);
    }
    @Override protected void onResume() {
        super.onResume(); handler.removeCallbacks(refresh);
        HomeBridge.logEntry(this, "resume");
        if (preview != null) {
            ServiceEvidence.record(this, "activity_resumed");
            ModeRuntimeService.sync(this, "activity_resumed"); refresh.run();
        }
    }
    @Override protected void onPause() {
        if (hintToast != null) { hintToast.cancel(); hintToast = null; }
        touchingLogs = false;
        handler.removeCallbacks(refresh); if (preview != null) preview.cancel("activity_paused"); super.onPause();
        if (preview != null) ServiceEvidence.record(this, "activity_paused");
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        HomeBridge.logEntry(this, "new_intent");
        redirectNavigation(intent);
    }
    private boolean redirectNavigation(Intent intent) {
        if (!HomeBridge.navigation(intent)) return false;
        startActivity(HomeBridge.proxy(this, intent));
        finish(); return true;
    }
    @Override public void onBackPressed() { cancelMenus("system_back"); super.onBackPressed(); }
    @Override protected void onDestroy() {
        if (app != null && !app.recovering()) app.log().record("activity_destroyed", "finishing", isFinishing(),
                "changing_configuration", isChangingConfigurations(), "task_id", getTaskId(), "service_state", ServiceEvidence.capture(this));
        handler.removeCallbacks(refresh); if (preview != null) preview.cancel("activity_destroyed"); worker.shutdown(); super.onDestroy();
    }
}
