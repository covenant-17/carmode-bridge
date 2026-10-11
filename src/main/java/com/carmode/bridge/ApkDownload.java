package com.carmode.bridge;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;

/** Android 10+ Downloads export; no broad storage permission or direct shared-path writes. */
final class ApkDownload {
    static Uri save(Context context, OemApkExporter.Export export) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, export.displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, export.mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Cannot create Downloads entry");
        try {
            long copied = 0;
            try (InputStream input = new BufferedInputStream(new FileInputStream(export.file));
                 OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null) throw new IOException("Cannot open Downloads entry");
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) { output.write(buffer, 0, count); copied += count; }
            }
            if (copied != export.file.length()) throw new IOException("Incomplete Downloads copy");
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(uri, values, null, null) != 1) throw new IOException("Cannot publish Downloads copy");
            return uri;
        } catch (IOException | RuntimeException e) {
            // Only the entry created above, never an existing user file.
            try { resolver.delete(uri, null, null); }
            catch (RuntimeException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }
}
