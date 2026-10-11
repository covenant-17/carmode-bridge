package com.carmode.bridge;

import android.content.Context;
import android.os.Build;
import com.carmode.bridge.core.CrashReportStore;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/** Saves best-effort evidence, then delegates fatal termination to Android. Never resumes a crashed thread. */
final class CrashReporter {
    static CrashReportStore store(Context context) {
        return new CrashReportStore(new File(BootStorage.device(context).getFilesDir(), "crashes"));
    }
    static void install(Context context) {
        CrashReportStore store = store(context);
        String version = "unknown";
        try { version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName; }
        catch (Exception ignored) {}
        final String metadata = "app_version=" + version + "\nsdk=" + Build.VERSION.SDK_INT
                + "\nmanufacturer=" + Build.MANUFACTURER + "\nmodel=" + Build.MODEL + "\nbuild=" + Build.DISPLAY;
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        AtomicBoolean saving = new AtomicBoolean();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                if (saving.compareAndSet(false, true)) {
                    try { store.save(metadata + "\nthread=" + thread.getName(), error); }
                    catch (Throwable failure) { android.util.Log.e("CarModeCrash", "Could not persist crash report", failure); }
                }
            } finally {
                try { if (previous != null) previous.uncaughtException(thread, error); }
                finally {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
            }
        });
    }
}
