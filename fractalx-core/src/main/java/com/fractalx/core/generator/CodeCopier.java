package com.fractalx.core.generator;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Copies module code from monolith to microservice project
 */
public class CodeCopier {

    private static final Logger log = LoggerFactory.getLogger(CodeCopier.class);
    private final Path sourceRoot;

    public CodeCopier(Path sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    public void copyModuleCode(FractalModule module, Path targetSrcMainJava) throws IOException {
        log.debug("Copying code for module: {}", module.getServiceName());

        if (module.getPackageName() == null || module.getPackageName().isEmpty()) {
            log.warn("No package name found for module: {}", module.getServiceName());
            return;
        }

        // Determine source package directory
        Path sourcePackageDir = sourceRoot.resolve(module.getPackageName().replace('.', '/'));

        if (!Files.exists(sourcePackageDir)) {
            log.warn("Source package directory does not exist: {}", sourcePackageDir);
            return;
        }

        // Copy entire package
        copyDirectory(sourcePackageDir, targetSrcMainJava.resolve(module.getPackageName().replace('.', '/')));

        log.info("Copied {} to generated service", module.getPackageName());
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    log.error("Failed to copy: " + sourcePath, e);
                }
            });
        }
    }
}