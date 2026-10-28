package com.carmode.bridge;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.widget.*;
import com.carmode.bridge.core.CrashReportStore;

/** Minimal recovery screen: no EventLog, permission polling, OEM code or generated string IDs. */
public final class CrashActivity extends Activity {
    private boolean ru;
    private CrashReportStore reports;
    private String report;
    private LinearLayout content;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        reports = CrashReporter.store(this);
        try { ru = "ru".equals(BootStorage.device(this).getSharedPreferences("settings", MODE_PRIVATE).getString("language", "en")); }
        catch (RuntimeException ignored) {}
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding); scroll.addView(content); setContentView(scroll);
        text(label("CarMode: saved crash report", "CarMode: сохранён отчёт о падении"), 24);
        text(label("The previous crash report is saved locally. Share it before retrying. Nothing is sent automatically. "
                + "Report may contain exception messages and file paths. After a crash, MODE handling stays paused until recovery.",
                "Отчёт о падении сохранён на устройстве. Отправьте его перед повторным запуском. Ничего не отправляется автоматически. "
                + "Отчёт может содержать сообщения ошибки и пути файлов. После падения обработка MODE приостановлена до восстановления."), 17);
        try { report = reports.read(); }
        catch (Exception e) { report = "Could not read crash report: " + e; }
        if (report.isEmpty()) report = label("No saved Java crash report. Native crashes, forced stops and power loss may leave no report.",
                "Сохранённого отчёта Java нет. Нативное падение, принудительная остановка или отключение питания могут не оставить отчёт.");
        button(label("Share report", "Поделиться отчётом"), () -> {
            try {
                Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_STREAM, LogProvider.CRASH_URI).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri("CarMode crash report", LogProvider.CRASH_URI));
                if (!reports.reportFile().isFile()) { intent.removeExtra(Intent.EXTRA_STREAM); intent.putExtra(Intent.EXTRA_TEXT, report); }
                startActivity(Intent.createChooser(intent, label("Share crash report", "Отправить отчёт о падении")));
            } catch (RuntimeException e) { toast(label("Cannot share. Use Copy report.", "Не удалось отправить. Используйте «Копировать отчёт».")); }
        });
        button(label("Copy report", "Копировать отчёт"), () -> {
            try {
                getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("CarMode crash", report));
                toast(label("Report copied", "Отчёт скопирован"));
            } catch (RuntimeException e) { toast(label("Cannot copy report", "Не удалось скопировать отчёт")); }
        });
        button(label("Open settings with MODE handling disabled", "Открыть настройки без обработки MODE"), () -> {
            try {
                // Only this explicit recovery action changes settings. Keep application assignments and logs.
                if (!BootStorage.device(this).getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("enabled", false)
                        .putBoolean("hcn_mode", false).putBoolean("diagnostics", false).commit()) {
                    toast(label("Could not save recovery settings", "Не удалось сохранить настройки восстановления")); return;
                }
                reports.acknowledge();
            } catch (Exception e) { toast(label("Could not reopen: ", "Не удалось открыть: ") + e); return; }
            // Initialization failures must reach the fatal handler and replace the crash evidence.
            CarModeApp app = CarModeApp.from(this); app.resumeAfterCrash();
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish();
        });
        TextView trace = text(report, 13); trace.setTextIsSelectable(true);
        button(label("Restore previous CarMode behavior", "Вернуть прежний режим CarMode"), () -> CamryControl.restore(this));
        button(label("Choose stock Android launcher", "Выбрать штатный лаунчер Android"), () -> CamryControl.chooseStock(this));
    }
    @Override protected void onResume() { super.onResume(); CamryUi.navigation(this); CamryControl.checkStock(this); }
    private String label(String en, String russian) { return ru ? russian : en; }
    private TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); content.addView(view); return view;
    }
    private void button(String title, Runnable action) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false);
        button.setOnClickListener(view -> action.run()); content.addView(button);
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
