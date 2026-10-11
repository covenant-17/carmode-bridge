package com.carmode.bridge.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class StorageMigrationTest {
    @TempDir Path root;
    @Test void preservesBytesAndCreatesParent() throws Exception {
        Path old = root.resolve("old.jsonl"), target = root.resolve("device/events.jsonl");
        byte[] bytes = new byte[]{0, 1, 2, -1}; Files.write(old, bytes);
        StorageMigration.move(old.toFile(), target.toFile());
        assertArrayEquals(bytes, Files.readAllBytes(target)); assertFalse(Files.exists(old));
    }
    @Test void missingSourceIsSafe() throws Exception {
        StorageMigration.move(root.resolve("missing").toFile(), root.resolve("new").toFile());
        assertFalse(Files.exists(root.resolve("new")));
    }
    @Test void neverOverwritesDeviceData() throws Exception {
        Path old = root.resolve("old"), target = root.resolve("new");
        Files.write(old, new byte[]{1}); Files.write(target, new byte[]{2});
        StorageMigration.move(old.toFile(), target.toFile());
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(old));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(target));
    }
    @Test void movesCrashDirectoryWithPendingMarker() throws Exception {
        Path old = Files.createDirectory(root.resolve("crashes")), target = root.resolve("device/crashes");
        Files.write(old.resolve("crash-report.txt"), new byte[]{3});
        Files.write(old.resolve("crash-report.pending"), new byte[]{1});
        StorageMigration.move(old.toFile(), target.toFile());
        assertTrue(Files.exists(target.resolve("crash-report.pending")));
        assertArrayEquals(new byte[]{3}, Files.readAllBytes(target.resolve("crash-report.txt")));
        StorageMigration.move(old.toFile(), target.toFile());
    }
    @Test void reportsFailureWithoutDeletingSource() throws Exception {
        Path old = root.resolve("old"), parent = root.resolve("file-not-directory");
        Files.write(old, new byte[]{1}); Files.write(parent, new byte[]{2});
        assertThrows(java.io.IOException.class, () -> StorageMigration.move(old.toFile(), parent.resolve("new").toFile()));
        assertTrue(Files.exists(old));
    }
}
