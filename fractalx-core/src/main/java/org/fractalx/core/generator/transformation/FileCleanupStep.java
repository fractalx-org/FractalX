package org.fractalx.core.generator.transformation;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.fractalx.core.model.FractalModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
        List<String> targets;
        if (filesToDelete != null) {
            targets = filesToDelete;
        } else {
            targets = new ArrayList<>(detectStubs(context));
            targets.addAll(detectMisplacedComponents(context));
            targets.addAll(detectCrossModuleEventListeners(context));
        }
        if (targets.isEmpty()) {
            return;
        }
        Set<String> targetSet = new HashSet<>(targets);
        try (Stream<Path> paths = Files.walk(context.getServiceRoot())) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> targetSet.contains(p.getFileName().toString()))
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

    private static final Set<String> COMPONENT_ANNOTATIONS = Set.of(
            "Component", "Service", "Repository", "Controller", "RestController",
            "Configuration", "EventListener", "TransactionalEventListener"
    );

    /**
     * Detects Spring component files that were copied into this service but whose package
     * belongs to a different module's namespace. These are misplaced files — for example,
     * an {@code @EventListener} class in {@code com.example.demo.order} that ended up in
     * customer-service because it transitively imported a customer-module type.
     *
     * <p>Only component-annotated classes are removed this way; plain DTOs and enums that
     * live in another module's package are kept because they may be legitimately shared
     * (e.g., a request/response DTO used by a generated NetScope client).
     */
    private List<String> detectMisplacedComponents(GenerationContext context) throws IOException {
        Set<String> otherModulePkgs = context.getAllModules().stream()
                .filter(m -> !m.equals(context.getModule()))
                .map(FractalModule::getPackageName)
                .filter(Objects::nonNull)
                .filter(p -> !p.isBlank())
                .collect(Collectors.toSet());

        if (otherModulePkgs.isEmpty()) return List.of();

        JavaParser parser = new JavaParser();
        List<String> misplaced = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(context.getSrcMainJava())) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFile -> {
                     try {
                         CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                         if (cu == null) return;

                         String filePkg = cu.getPackageDeclaration()
                                 .map(pd -> pd.getNameAsString())
                                 .orElse("");

                         boolean inOtherModulePkg = otherModulePkgs.stream()
                                 .anyMatch(pkg -> filePkg.equals(pkg) || filePkg.startsWith(pkg + "."));
                         if (!inOtherModulePkg) return;

                         boolean isComponent = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                                 .anyMatch(c -> c.getAnnotations().stream()
                                         .anyMatch(a -> COMPONENT_ANNOTATIONS.contains(a.getNameAsString())));
                         if (isComponent) {
                             misplaced.add(javaFile.getFileName().toString());
                             log.debug("Detected misplaced component in foreign module package: {}", javaFile.getFileName());
                         }
                     } catch (Exception e) {
                         log.debug("Skipping {} during misplaced-component detection", javaFile);
                     }
                 });
        }
        return misplaced;
    }

    /**
     * Detects Spring component files that contain {@code @EventListener} or
     * {@code @TransactionalEventListener} methods whose event-parameter type is imported
     * from another module's package. Such listeners relied on in-process Spring events
     * that no longer work across JVM boundaries after decomposition; they are dead code
     * in the generated service.
     */
    private List<String> detectCrossModuleEventListeners(GenerationContext context) throws IOException {
        Set<String> otherModulePkgs = context.getAllModules().stream()
                .filter(m -> !m.equals(context.getModule()))
                .map(FractalModule::getPackageName)
                .filter(Objects::nonNull)
                .filter(p -> !p.isBlank())
                .collect(Collectors.toSet());

        if (otherModulePkgs.isEmpty()) return List.of();

        JavaParser parser = new JavaParser();
        List<String> listeners = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(context.getSrcMainJava())) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFile -> {
                     try {
                         CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                         if (cu == null) return;

                         // Build a map: simple class name → FQN for all non-static imports
                         Map<String, String> importMap = new java.util.HashMap<>();
                         cu.getImports().stream()
                           .filter(i -> !i.isStatic() && !i.isAsterisk())
                           .forEach(i -> {
                               String fqn = i.getNameAsString();
                               importMap.put(fqn.substring(fqn.lastIndexOf('.') + 1), fqn);
                           });

                         boolean hasCrossModuleListener = cu.findAll(
                                 com.github.javaparser.ast.body.MethodDeclaration.class).stream()
                             .filter(m -> m.getAnnotationByName("EventListener").isPresent()
                                       || m.getAnnotationByName("TransactionalEventListener").isPresent())
                             .anyMatch(m -> m.getParameters().stream()
                                 .anyMatch(param -> {
                                     String fqn = importMap.get(param.getTypeAsString());
                                     if (fqn == null) return false;
                                     return otherModulePkgs.stream()
                                             .anyMatch(pkg -> fqn.startsWith(pkg + "."));
                                 }));

                         if (hasCrossModuleListener) {
                             listeners.add(javaFile.getFileName().toString());
                             log.debug("Detected cross-module event listener for cleanup: {}", javaFile.getFileName());
                         }
                     } catch (Exception e) {
                         log.debug("Skipping {} during cross-module listener detection", javaFile);
                     }
                 });
        }
        return listeners;
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
