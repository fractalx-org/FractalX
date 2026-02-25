package com.fractalx.core.generator.transformation;

import com.fractalx.core.generator.GenerationContext;
import com.fractalx.core.generator.ServiceFileGenerator;
import com.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Copies the module's source package from the monolith into the generated service.
 */
public class CodeCopier implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(CodeCopier.class);

    @Override
    public void generate(GenerationContext context) throws IOException {
        FractalModule module = context.getModule();
        log.debug("Copying code for module: {}", module.getServiceName());

        if (module.getPackageName() == null || module.getPackageName().isBlank()) {
            log.warn("No package name found for module: {}", module.getServiceName());
            return;
        }

        Path sourcePackageDir = context.getSourceRoot()
                .resolve(module.getPackageName().replace('.', '/'));

        if (!Files.exists(sourcePackageDir)) {
            log.warn("Source package directory does not exist: {}", sourcePackageDir);
            return;
        }

        Path targetPackageDir = context.getSrcMainJava()
                .resolve(module.getPackageName().replace('.', '/'));

        copyDirectory(sourcePackageDir, targetPackageDir);
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
                    log.error("Failed to copy: {}", sourcePath, e);
                }
            });
        }
    }
}
