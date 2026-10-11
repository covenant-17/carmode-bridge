package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class ApkExportFilesTest {
    @TempDir Path temp;
    private File source(String name, byte[] bytes) throws IOException {
        Path path = temp.resolve(name); Files.createDirectories(path.getParent()); Files.write(path, bytes); return path.toFile();
    }
    private File cache() { return temp.resolve("cache").toFile(); }

    @Test void singleApkIsByteIdenticalAndSourceIsUntouched() throws Exception {
        byte[] bytes = new byte[180000]; new java.util.Random(7).nextBytes(bytes);
        File original = source("installed/base.apk", bytes);
        File result = ApkExportFiles.create(Collections.singletonList(original), cache());
        assertTrue(result.getName().endsWith(".apk"));
        assertArrayEquals(bytes, Files.readAllBytes(result.toPath()));
        assertArrayEquals(bytes, Files.readAllBytes(original.toPath()));
        assertEquals(result.getCanonicalFile(), ApkExportFiles.resolve(cache(), result.getName()));
    }
    @Test void splitExportIncludesAllPartsEvenIfTheirBasenamesMatch() throws Exception {
        File base = source("one/base.apk", new byte[]{1, 2, 3});
        File split = source("two/base.apk", new byte[]{4, 5});
        File result = ApkExportFiles.create(Arrays.asList(base, split), cache());
        assertTrue(result.getName().endsWith(".zip"));
        try (ZipFile zip = new ZipFile(result)) {
            assertEquals(2, zip.size());
            assertArrayEquals(new byte[]{1, 2, 3}, read(zip, "base.apk"));
            assertArrayEquals(new byte[]{4, 5}, read(zip, "split-1.apk"));
        }
    }
    private byte[] read(ZipFile zip, String name) throws IOException {
        try (java.io.InputStream input = zip.getInputStream(zip.getEntry(name));
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[64]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }
    @Test void repeatExportDoesNotOverwriteEarlierSharedFile() throws Exception {
        File original = source("original.apk", new byte[]{1});
        File first = ApkExportFiles.create(Collections.singletonList(original), cache());
        Files.write(original.toPath(), new byte[]{2});
        File second = ApkExportFiles.create(Collections.singletonList(original), cache());
        assertNotEquals(first, second);
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(first.toPath()));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(second.toPath()));
    }
    @Test void missingSplitNeverProducesAnIncompleteExport() throws Exception {
        File original = source("original.apk", new byte[]{1});
        assertThrows(IOException.class, () -> ApkExportFiles.create(Arrays.asList(original, temp.resolve("missing.apk").toFile()), cache()));
        assertFalse(cache().exists());
        assertTrue(original.exists());
    }
    @Test void emptySourcesAndEmptyApkAreRejected() throws Exception {
        assertThrows(IOException.class, () -> ApkExportFiles.create(Collections.emptyList(), cache()));
        File empty = source("empty.apk", new byte[0]);
        assertThrows(IOException.class, () -> ApkExportFiles.create(Collections.singletonList(empty), cache()));
    }
    @Test void changingSourceRemovesPartialCopyButKeepsOriginal() throws Exception {
        File original = source("original.apk", new byte[]{1, 2});
        File changing = new File(original.getPath()) {
            private long reads;
            @Override public long lastModified() { return ++reads; }
        };
        assertThrows(IOException.class, () -> ApkExportFiles.create(Collections.singletonList(changing), cache()));
        assertArrayEquals(new String[0], cache().list());
        assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(original.toPath()));
    }
    @Test void providerRejectsTraversalTemporaryAndUnrelatedFiles() throws Exception {
        String[] names = {"../original.apk", "..\\original.apk", "pending-123.tmp", "diagnostics.json", "other.apk",
                "com.hcn.autosource-00000000-0000-0000-0000-000000000000.apk/../secret", null};
        for (String name : names) assertThrows(IOException.class, () -> ApkExportFiles.resolve(cache(), name));
    }
    @Test void providerRejectsMissingOtherwiseValidExport() {
        assertThrows(IOException.class, () -> ApkExportFiles.resolve(cache(), "com.hcn.autosource-00000000-0000-0000-0000-000000000000.apk"));
    }
    @Test void zlinkCopyIsIdenticalAndProviderAllowsIt() throws Exception {
        File original = source("zlink/base.apk", new byte[]{9, 8, 7});
        File export = ApkExportFiles.create(Collections.singletonList(original), cache(), "com.zjinnova.zlink");
        assertTrue(export.getName().startsWith("com.zjinnova.zlink-"));
        assertArrayEquals(new byte[]{9, 8, 7}, Files.readAllBytes(export.toPath()));
        assertEquals(export.getCanonicalFile(), ApkExportFiles.resolve(cache(), export.getName()));
    }
    @Test void zlinkSplitsAreAllIncludedAndProviderAllowsZip() throws Exception {
        File base = source("zlink/base.apk", new byte[]{1});
        File split = source("zlink/split.apk", new byte[]{2});
        File export = ApkExportFiles.create(Arrays.asList(base, split), cache(), "com.zjinnova.zlink");
        assertEquals(export.getCanonicalFile(), ApkExportFiles.resolve(cache(), export.getName()));
        try (ZipFile zip = new ZipFile(export)) {
            assertEquals(2, zip.size());
            assertArrayEquals(new byte[]{1}, read(zip, "base.apk"));
            assertArrayEquals(new byte[]{2}, read(zip, "split-1.apk"));
        }
    }
    @Test void unsupportedPackageCannotCreateOrExposeAnExport() throws Exception {
        File original = source("other/base.apk", new byte[]{1});
        assertThrows(IOException.class, () -> ApkExportFiles.create(Collections.singletonList(original), cache(), "other.app"));
        assertFalse(cache().exists());
        assertThrows(IOException.class, () -> ApkExportFiles.resolve(cache(), "other.app-00000000-0000-0000-0000-000000000000.apk"));
    }
    @Test void zlinkProviderRejectsTraversalAndUnfinishedFiles() {
        for (String name : new String[]{"../com.zjinnova.zlink.apk", "com.zjinnova.zlink-pending.tmp",
                "com.zjinnova.zlink-00000000-0000-0000-0000-000000000000.apk/../../secret"})
            assertThrows(IOException.class, () -> ApkExportFiles.resolve(cache(), name));
    }
    @Test void selectedFactoryExportUsesSafePrefixAndOriginalBytes() throws Exception {
        File original = source("system/settings.apk", new byte[]{3, 4, 5});
        File export = ApkExportFiles.create(Collections.singletonList(original), cache(), "factory-settings");
        assertTrue(export.getName().startsWith("factory-settings-"));
        assertEquals(export.getCanonicalFile(), ApkExportFiles.resolve(cache(), export.getName()));
        assertArrayEquals(new byte[]{3, 4, 5}, Files.readAllBytes(export.toPath()));
        assertArrayEquals(new byte[]{3, 4, 5}, Files.readAllBytes(original.toPath()));
    }
    @Test void factoryProviderRejectsArbitraryAndPendingNames() {
        for (String name : new String[]{"factory-settings.apk", "factory-settings-pending.tmp",
                "../factory-settings-00000000-0000-0000-0000-000000000000.apk"})
            assertThrows(IOException.class, () -> ApkExportFiles.resolve(cache(), name));
    }
}
