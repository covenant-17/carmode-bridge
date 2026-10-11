package com.carmode.bridge.core;

import java.io.*;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Copies installed APK bytes; never edits, signs or installs a package. */
public final class ApkExportFiles {
    private ApkExportFiles() { }
    private static final String NAME_PATTERN = "(com\\.hcn\\.autosource|com\\.zjinnova\\.zlink|factory-settings)-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(apk|zip)";

    public static File create(List<File> sources, File directory) throws IOException {
        return create(sources, directory, "com.hcn.autosource");
    }

    public static File create(List<File> sources, File directory, String packageName) throws IOException {
        if (!"com.hcn.autosource".equals(packageName) && !"com.zjinnova.zlink".equals(packageName)
                && !"factory-settings".equals(packageName))
            throw new IOException("Unsupported APK export package");
        if (sources == null || sources.isEmpty()) throw new IOException("No APK paths supplied");
        for (File source : sources) {
            if (source == null || !source.isFile() || !source.canRead() || source.length() == 0)
                throw new IOException("An installed APK is missing, empty or not readable");
        }
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create export cache");
        boolean split = sources.size() > 1;
        File result = new File(directory, packageName + "-" + UUID.randomUUID() + (split ? ".zip" : ".apk"));
        File pending = File.createTempFile("pending-", ".tmp", directory);
        try {
            if (split) {
                try (ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(pending)))) {
                    for (int i = 0; i < sources.size(); i++) {
                        // Deterministic names; do not expose or trust source path names as ZIP entries.
                        output.putNextEntry(new ZipEntry(i == 0 ? "base.apk" : "split-" + i + ".apk"));
                        copy(sources.get(i), output);
                        output.closeEntry();
                    }
                }
            } else {
                try (OutputStream output = new BufferedOutputStream(new FileOutputStream(pending))) { copy(sources.get(0), output); }
            }
            if (!pending.renameTo(result)) throw new IOException("Cannot finalize APK export");
            return result;
        } finally {
            // Only our newly-created temporary file; existing exports and installed files are untouched.
            if (pending.exists() && !pending.delete()) pending.deleteOnExit();
        }
    }

    private static void copy(File source, OutputStream output) throws IOException {
        long length = source.length(), modified = source.lastModified(), copied = 0;
        try (InputStream input = new BufferedInputStream(new FileInputStream(source))) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) { output.write(buffer, 0, count); copied += count; }
        }
        if (copied != length || source.length() != length || source.lastModified() != modified)
            throw new IOException("Installed APK changed during export; retry");
    }

    /** Strict provider allowlist: no paths, other packages, temporary files or symlink escapes. */
    public static File resolve(File directory, String name) throws IOException {
        if (name == null || !name.matches(NAME_PATTERN)) throw new FileNotFoundException("Unknown APK export");
        File file = new File(directory, name).getCanonicalFile();
        if (!directory.getCanonicalFile().equals(file.getParentFile()) || !file.isFile())
            throw new FileNotFoundException("APK export unavailable");
        return file;
    }
}
