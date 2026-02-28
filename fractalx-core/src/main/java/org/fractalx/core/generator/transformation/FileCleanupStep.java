package org.fractalx.core.generator.transformation;

import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Deletes monolith-specific implementation files that must not appear in a generated service.
 *
 * <p>This step separates the concern of file deletion from annotation removal, which
 * was previously mixed into {@link AnnotationRemover}.
 */
public class FileCleanupStep implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupStep.class);

    /** Simple file names (not paths) that should be removed from the generated service. */
    private final List<String> filesToDelete;

    public FileCleanupStep(List<String> filesToDelete) {
        this.filesToDelete = List.copyOf(filesToDelete);
    }

    @Override
    public void generate(GenerationContext context) throws IOException {
        if (filesToDelete.isEmpty()) {
            return;
        }
        try (Stream<Path> paths = Files.walk(context.getServiceRoot())) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> filesToDelete.contains(p.getFileName().toString()))
                    .forEach(this::deleteQuietly);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.delete(path);
            log.info("Removed monolith-only file from generated service: {}", path.getFileName());
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", path, e.getMessage());
        }
    }
}
