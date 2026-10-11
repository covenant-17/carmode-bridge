package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class CrashReportStoreTest {
    @TempDir Path temp;
    private CrashReportStore store() { return new CrashReportStore(temp.resolve("crashes").toFile()); }
    @Test void freshInstallHasNoPendingReport() throws Exception {
        assertFalse(store().pending()); assertEquals("", store().read());
    }
    @Test void capturesStackCauseMetadataAndSurvivesNewInstance() throws Exception {
        store().save("app_version=0.1.6\nthread=main", new IllegalStateException("Ошибка", new NullPointerException("cause")));
        CrashReportStore restarted = store();
        assertTrue(restarted.pending());
        String report = restarted.read();
        assertTrue(report.contains("app_version=0.1.6")); assertTrue(report.contains("thread=main"));
        assertTrue(report.contains("IllegalStateException: Ошибка"));
        assertTrue(report.contains("Caused by: java.lang.NullPointerException: cause"));
        assertTrue(report.contains("CrashReportStoreTest.capturesStackCauseMetadataAndSurvivesNewInstance"));
    }
    @Test void acknowledgmentKeepsReportAvailableForSharing() throws Exception {
        store().save("v1", new RuntimeException("failure")); String original = store().read();
        store().acknowledge(); assertFalse(store().pending()); assertEquals(original, store().read());
        store().acknowledge(); // Idempotent.
    }
    @Test void nextCrashReplacesReportAndReopensRecoveryGate() throws Exception {
        store().save("v1", new RuntimeException("first")); store().acknowledge();
        store().save("v2", new LinkageError("second"));
        assertTrue(store().pending()); assertTrue(store().read().contains("LinkageError: second"));
        assertFalse(store().read().contains("RuntimeException: first"));
    }
    @Test void recoveryFailureDoesNotOverwriteUnacknowledgedCause() throws Exception {
        store().save("v1", new RuntimeException("original")); String original = store().read();
        store().save("v1", new RuntimeException("recovery failure"));
        assertTrue(store().pending()); assertEquals(original, store().read());
    }
    @Test void giantExceptionIsBounded() throws Exception {
        char[] chars = new char[200000]; java.util.Arrays.fill(chars, 'x');
        store().save("v1", new RuntimeException(new String(chars)));
        String report = store().read();
        assertTrue(report.length() <= CrashReportStore.MAX_CHARS + 64);
        assertTrue(report.endsWith("[report truncated]\n"));
    }
    @Test void storageFailureIsReportedToFatalHandler() throws Exception {
        Files.write(temp.resolve("crashes"), new byte[]{1});
        assertThrows(java.io.IOException.class, () -> store().save("v1", new RuntimeException()));
    }
    @Test void readsAreBoundedEvenForOversizedExistingFiles() throws Exception {
        Files.createDirectories(temp.resolve("crashes"));
        Files.write(store().reportFile().toPath(), new byte[200000]);
        assertTrue(store().read().length() <= CrashReportStore.MAX_CHARS + 64);
    }
}
