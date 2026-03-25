package org.fractalx.core.generator.transformation;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Deletes monolith-specific implementation files that must not appear in a generated service.
 *
 * <p>When constructed with no arguments (default), this step uses AST analysis to detect
 * cross-module stub implementations: any copied {@code .java} file whose class {@code implements}
 * or {@code extends} a type listed in the module's dependencies is considered a stub that should
 * be removed (it is replaced by a NetScope-generated client interface).
 *
 * <p>An explicit {@code filesToDelete} list can still be supplied for cases where the naming
 * convention departs from what AST detection can find.
 *
 * <p>This step separates the concern of file deletion from annotation removal, which
 * was previously mixed into {@link AnnotationRemover}.
 */
public class FileCleanupStep implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupStep.class);

    /**
     * Simple file names (not paths) to remove. {@code null} means derive from context via AST.
     */
    private final List<String> filesToDelete;

    /** AST-based mode: detect cross-module stubs at generation time. */
    public FileCleanupStep() {
        this.filesToDelete = null;
    }

    /** Explicit-list mode: delete exactly these file names (original behaviour). */
    public FileCleanupStep(List<String> filesToDelete) {
        this.filesToDelete = List.copyOf(filesToDelete);
    }

    @Override
    public void generate(GenerationContext context) throws IOException {
        List<String> targets = (filesToDelete != null) ? filesToDelete : detectStubs(context);
        if (targets.isEmpty()) {
            return;
        }
        try (Stream<Path> paths = Files.walk(context.getServiceRoot())) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> targets.contains(p.getFileName().toString()))
                    .forEach(this::deleteQuietly);
        }
    }

    /**
     * Walks the already-copied service source tree and returns the names of {@code .java} files
     * whose top-level class directly implements or extends any of the module's cross-module
     * dependency types. These are local stub/adapter classes that are superseded by the
     * NetScope-generated client interfaces.
     */
    private List<String> detectStubs(GenerationContext context) throws IOException {
        Set<String> depTypes = new HashSet<>(context.getModule().getDependencies());
        if (depTypes.isEmpty()) {
            return List.of();
        }

        JavaParser parser = new JavaParser();
        List<String> stubs = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(context.getServiceRoot())) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFile -> {
                     try {
                         CompilationUnit cu = parser.parse(javaFile)
                                 .getResult().orElse(null);
                         if (cu == null) return;

                         boolean isStub = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                                 .anyMatch(c ->
                                     c.getImplementedTypes().stream()
                                      .anyMatch(t -> depTypes.contains(t.getNameAsString()))
                                     || c.getExtendedTypes().stream()
                                        .anyMatch(t -> depTypes.contains(t.getNameAsString()))
                                 );
                         if (isStub) {
                             stubs.add(javaFile.getFileName().toString());
                             log.debug("Detected cross-module stub for cleanup: {}", javaFile.getFileName());
                         }
                     } catch (Exception e) {
                         log.debug("Skipping unparseable file during stub detection: {}", javaFile);
                     }
                 });
        }
        return stubs;
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
