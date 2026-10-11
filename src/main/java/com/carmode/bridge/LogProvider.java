package com.carmode.bridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import com.carmode.bridge.core.ApkExportFiles;

/** Shares only generated logs/OEM copies, read-only, with per-file temporary URI grants. */
public final class LogProvider extends ContentProvider {
    public static final Uri URI = Uri.parse("content://com.carmode.bridge.logs/diagnostics.json");
    public static final Uri CRASH_URI = Uri.parse("content://com.carmode.bridge.logs/crash-report.txt");
    @Override public boolean onCreate() { return true; }
    private File file(Uri uri) {
        if (CRASH_URI.equals(uri)) return CrashReporter.store(getContext()).reportFile();
        if (URI.equals(uri)) return new File(BootStorage.device(getContext()).getCacheDir(), "diagnostics.json");
        if (!"content".equals(uri.getScheme()) || !URI.getAuthority().equals(uri.getAuthority())
                || uri.getQuery() != null || uri.getFragment() != null || uri.getPathSegments().size() != 2
                || !"oem".equals(uri.getPathSegments().get(0))) throw new IllegalArgumentException("Unknown export");
        try { return ApkExportFiles.resolve(OemApkExporter.directory(getContext()), uri.getLastPathSegment()); }
        catch (IOException e) { throw new IllegalArgumentException("APK export unavailable", e); }
    }
    @Override public String getType(Uri uri) {
        file(uri);
        return CRASH_URI.equals(uri) ? "text/plain" : URI.equals(uri) ? "application/json" : uri.getLastPathSegment().endsWith(".zip")
                ? "application/zip" : "application/vnd.android.package-archive";
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        try { return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY); }
        catch (IllegalArgumentException e) { throw new FileNotFoundException(e.getMessage()); }
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        File file = file(uri);
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor result = new MatrixCursor(columns);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = CRASH_URI.equals(uri) ? "carmode-crash.txt" : URI.equals(uri) ? "carmode-diagnostics.json"
                    : new OemApkExporter.Export(file).displayName;
            if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
        }
        result.addRow(row); return result;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
}
