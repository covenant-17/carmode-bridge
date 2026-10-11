package com.carmode.bridge.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Independent of Android, preferences and the normal event log (which may be the crash cause). */
public final class CrashReportStore {
    public static final int MAX_CHARS = 65536;
    private final File directory;
    public CrashReportStore(File directory) { this.directory = directory; }
    public File reportFile() { return new File(directory, "crash-report.txt"); }
    private File pendingFile() { return new File(directory, "crash-report.pending"); }
    public boolean pending() { return pendingFile().isFile(); }
    public synchronized void save(String metadata, Throwable error) throws IOException {
        // A recovery-screen failure must not overwrite the original, still-unacknowledged cause.
        if (pending() && reportFile().isFile()) return;
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Crash directory unavailable");
        BoundedWriter buffer = new BoundedWriter();
        PrintWriter writer = new PrintWriter(buffer);
        writer.println("CarMode Bridge crash report");
        writer.println("timestamp_ms=" + System.currentTimeMillis());
        writer.println(metadata);
        error.printStackTrace(writer); writer.flush();
        File temporary = new File(directory, "crash-report.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(buffer.toString().getBytes(StandardCharsets.UTF_8)); output.getFD().sync();
        }
        try { Files.move(temporary.toPath(), reportFile().toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary.toPath(), reportFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        try (FileOutputStream pending = new FileOutputStream(pendingFile())) {
            pending.write(1); pending.getFD().sync();
        }
    }
    public synchronized String read() throws IOException {
        if (!reportFile().isFile()) return "";
        StringBuilder text = new StringBuilder();
        try (Reader reader = new InputStreamReader(new FileInputStream(reportFile()), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096]; int count;
            while (text.length() < MAX_CHARS + 64 && (count = reader.read(buffer, 0,
                    Math.min(buffer.length, MAX_CHARS + 64 - text.length()))) != -1) text.append(buffer, 0, count);
        }
        return text.toString();
    }
    /** Dismiss the startup gate but retain the report for later sharing. */
    public synchronized void acknowledge() throws IOException {
        if (pendingFile().exists() && !pendingFile().delete()) throw new IOException("Cannot acknowledge crash report");
    }
    private static final class BoundedWriter extends Writer {
        private final StringBuilder text = new StringBuilder();
        private boolean truncated;
        @Override public void write(char[] chars, int offset, int length) {
            int accepted = Math.min(length, MAX_CHARS - text.length());
            if (accepted > 0) text.append(chars, offset, accepted);
            truncated |= accepted < length;
        }
        @Override public void flush() {}
        @Override public void close() {}
        @Override public String toString() { return text.toString() + (truncated ? "\n[report truncated]\n" : ""); }
    }
}
