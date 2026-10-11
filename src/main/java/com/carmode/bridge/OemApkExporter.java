package com.carmode.bridge;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import com.carmode.bridge.core.ApkExportFiles;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** PackageManager identifies the exact installed base APK and every split. No root/ADB. */
final class OemApkExporter {
    static final String PACKAGE = "com.hcn.autosource";
    static final String ZLINK_PACKAGE = "com.zjinnova.zlink";
    static File directory(Context context) { return new File(BootStorage.device(context).getCacheDir(), "oem-apk-exports"); }
    static final class Export {
        final Uri uri;
        final String mime;
        final File file;
        final String displayName;
        Export(File file) {
            this.file = file;
            displayName = file.getName().startsWith(ZLINK_PACKAGE + "-")
                    ? ZLINK_PACKAGE + (file.getName().endsWith(".zip") ? "-apks.zip" : ".apk")
                    : file.getName().startsWith("factory-settings-")
                    ? "factory-settings" + (file.getName().endsWith(".zip") ? "-apks.zip" : ".apk")
                    : PACKAGE + (file.getName().endsWith(".zip") ? "-apks.zip" : ".apk");
            uri = Uri.parse("content://com.carmode.bridge.logs/oem/" + file.getName());
            mime = file.getName().endsWith(".zip") ? "application/zip" : "application/vnd.android.package-archive";
        }
    }
    static Export create(Context context) throws PackageManager.NameNotFoundException, IOException {
        return create(context, PACKAGE);
    }
    static Export create(Context context, String packageName) throws PackageManager.NameNotFoundException, IOException {
        if (!PACKAGE.equals(packageName) && !ZLINK_PACKAGE.equals(packageName))
            throw new IOException("Unsupported APK export package");
        return createInstalled(context, packageName, packageName);
    }
    static Export createFactory(Context context, String selectedPackage) throws PackageManager.NameNotFoundException, IOException {
        if (context.getPackageName().equals(selectedPackage)) throw new IOException("Cannot export CarMode as factory settings");
        return createInstalled(context, selectedPackage, "factory-settings");
    }
    private static Export createInstalled(Context context, String packageName, String filePrefix) throws PackageManager.NameNotFoundException, IOException {
        PackageManager manager = context.getPackageManager();
        PackageInfo installed = manager.getPackageInfo(packageName, 0);
        ApplicationInfo info = installed.applicationInfo;
        if (info == null || info.sourceDir == null) throw new IOException("Installed package has no base APK path");
        List<File> sources = new ArrayList<>();
        sources.add(new File(info.sourceDir));
        if (info.splitSourceDirs != null) for (String split : info.splitSourceDirs) {
            if (split == null) throw new IOException("Installed package has an invalid split path");
            sources.add(new File(split));
        }
        File file = ApkExportFiles.create(sources, directory(context), filePrefix);
        try {
            PackageInfo after = manager.getPackageInfo(packageName, 0);
            if (installed.lastUpdateTime != after.lastUpdateTime || installed.getLongVersionCode() != after.getLongVersionCode()
                    || after.applicationInfo == null || !info.sourceDir.equals(after.applicationInfo.sourceDir)
                    || !Arrays.equals(info.splitSourceDirs, after.applicationInfo.splitSourceDirs))
                throw new IOException("Package updated during export; retry");
            CarModeApp.from(context).log().record("oem_apk_exported", "package", packageName,
                    "version", installed.versionName, "version_code", installed.getLongVersionCode(),
                    "apk_count", sources.size(), "bytes", file.length(), "format", sources.size() > 1 ? "zip" : "apk");
            return new Export(file);
        } catch (PackageManager.NameNotFoundException | IOException | RuntimeException e) {
            if (!file.delete()) CarModeApp.from(context).log().record("oem_apk_cache_cleanup_failed", "file", file.getName());
            throw e;
        }
    }
}
