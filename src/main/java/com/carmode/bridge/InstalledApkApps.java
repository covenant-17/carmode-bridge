package com.carmode.bridge;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import com.carmode.bridge.core.FactoryAppRank;
import java.util.*;

/** Includes system apps and apps without launcher Activities, needed for OEM settings. */
final class InstalledApkApps {
    static final class Entry {
        final String label, packageName;
        Entry(String label, String packageName) { this.label = label; this.packageName = packageName; }
        @Override public String toString() { return label + "\n" + packageName; }
    }
    static List<Entry> list(Context context) {
        PackageManager manager = context.getPackageManager();
        List<Entry> entries = new ArrayList<>();
        for (ApplicationInfo info : manager.getInstalledApplications(0)) {
            if (context.getPackageName().equals(info.packageName) || info.sourceDir == null) continue;
            entries.add(new Entry(info.loadLabel(manager).toString(), info.packageName));
        }
        entries.sort(Comparator.comparingInt((Entry e) -> FactoryAppRank.rank(e.label, e.packageName))
                .thenComparing(e -> e.label.toLowerCase(Locale.ROOT)).thenComparing(e -> e.packageName));
        return entries;
    }
}
