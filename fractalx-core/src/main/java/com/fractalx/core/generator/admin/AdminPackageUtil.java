package com.fractalx.core.generator.admin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared utility for resolving and creating package directories in the admin service. */
final class AdminPackageUtil {

    private AdminPackageUtil() {}

    static Path createPackagePath(Path srcMainJava, String packageName) throws IOException {
        Path path = srcMainJava;
        for (String part : packageName.split("\\.")) {
            path = path.resolve(part);
        }
        Files.createDirectories(path);
        return path;
    }
}
