package com.carmode.bridge.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class StorageMigration {
    private StorageMigration() {}
    public static void move(File source, File target) throws IOException {
        if (!source.exists() || target.exists()) return;
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create migration directory");
        Files.move(source.toPath(), target.toPath());
    }
}
