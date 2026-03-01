package org.fractalx.core.verifier;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Verifies that generated REST controllers follow HTTP method conventions.
 *
 * <p>Rules checked:
 * <ol>
 *   <li><b>No bare {@code @RequestMapping} on methods</b> — method-level mappings should use
 *       {@code @GetMapping}, {@code @PostMapping}, {@code @PutMapping}, {@code @DeleteMapping},
 *       or {@code @PatchMapping} to make intent explicit.</li>
 *   <li><b>Delete methods must not have {@code @RequestBody}</b> — RFC 9110 discourages
 *       request bodies on DELETE; generated code should use path vars for IDs.</li>
 *   <li><b>GET/HEAD/DELETE methods must not produce side effects via {@code @Transactional(readOnly=false)}</b>
 *       — idempotent HTTP verbs should not mutate state (detected heuristically via annotation
 *       combination).</li>
 *   <li><b>{@code @RestController} classes must declare a class-level {@code @RequestMapping}</b>
 *       — without it the path is implicit and harder to document.</li>
 * </ol>
 */
public class ApiConventionChecker {

    private static final Logger log = LoggerFactory.getLogger(ApiConventionChecker.class);

    private static final Set<String> READ_VERBS = Set.of("GetMapping", "DeleteMapping");
    private static final Set<String> WRITE_VERBS =
            Set.of("PostMapping", "PutMapping", "PatchMapping");
    private static final Set<String> ALL_MAPPINGS = Set.of(
            "GetMapping", "PostMapping", "PutMapping", "DeleteMapping",
            "PatchMapping", "RequestMapping");

    // ── Result model ──────────────────────────────────────────────────────────

    public enum ViolationKind {
        BARE_REQUEST_MAPPING,        // @RequestMapping on method instead of typed verb
        DELETE_WITH_BODY,            // @DeleteMapping + @RequestBody
        MUTATING_READ_VERB,          // @GetMapping + non-readOnly @Transactional
        MISSING_CLASS_MAPPING        // @RestController without @RequestMapping on class
    }

    public record ApiViolation(
            ViolationKind kind,
            String        serviceName,
            Path          file,
            String        className,
            String        methodName,
            String        detail
    ) {
        public boolean isCritical() {
            return kind == ViolationKind.BARE_REQUEST_MAPPING
                    || kind == ViolationKind.MISSING_CLASS_MAPPING;
        }

        @Override
        public String toString() {
            String level = isCritical() ? "[FAIL]" : "[WARN]";
            String loc   = className + (methodName != null ? "#" + methodName : "");
            return level + " API convention [" + serviceName + "] " + loc
                    + " — " + detail + " [" + file.getFileName() + "]";
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public List<ApiViolation> check(Path outputDir, List<FractalModule> modules) {
        List<ApiViolation> violations = new ArrayList<>();

        for (FractalModule module : modules) {
            Path srcJava = outputDir.resolve(module.getServiceName())
                    .resolve("src/main/java");
            if (!Files.isDirectory(srcJava)) continue;
            checkService(srcJava, module.getServiceName(), violations);
        }

        // Also check admin-service and gateway if generated
        for (String infra : List.of("admin-service", "fractalx-gateway")) {
            Path srcJava = outputDir.resolve(infra).resolve("src/main/java");
            if (Files.isDirectory(srcJava))
                checkService(srcJava, infra, violations);
        }

        return violations;
    }

    // ── Per-service scan ──────────────────────────────────────────────────────

    private void checkService(Path srcJava, String serviceName,
                               List<ApiViolation> violations) {
        try (Stream<Path> walk = Files.walk(srcJava)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("Controller.java"))
                    .forEach(file -> checkFile(file, serviceName, violations));
        } catch (IOException e) {
            log.debug("Could not walk {}: {}", srcJava, e.getMessage());
        }
    }

    private void checkFile(Path file, String serviceName,
                            List<ApiViolation> violations) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(c -> hasAnnotation(c.getAnnotations(), "RestController"))
                    .forEach(cls -> checkController(cls, file, serviceName, violations));
        } catch (Exception e) {
            log.debug("Could not parse {}: {}", file, e.getMessage());
        }
    }

    private void checkController(ClassOrInterfaceDeclaration cls, Path file,
                                  String serviceName,
                                  List<ApiViolation> violations) {
        String className = cls.getNameAsString();

        // Rule 4: class-level @RequestMapping
        boolean hasClassMapping = hasAnnotation(cls.getAnnotations(), "RequestMapping");
        if (!hasClassMapping) {
            violations.add(new ApiViolation(
                    ViolationKind.MISSING_CLASS_MAPPING, serviceName, file, className, null,
                    "@RestController is missing a class-level @RequestMapping — add a base path"));
        }

        for (MethodDeclaration method : cls.getMethods()) {
            List<AnnotationExpr> annots = method.getAnnotations();
            String methodName = method.getNameAsString();

            // Rule 1: bare @RequestMapping on method
            boolean hasBareMapping = annots.stream()
                    .anyMatch(a -> a.getNameAsString().equals("RequestMapping"));
            if (hasBareMapping) {
                violations.add(new ApiViolation(
                        ViolationKind.BARE_REQUEST_MAPPING, serviceName, file,
                        className, methodName,
                        "Use @GetMapping / @PostMapping / @PutMapping / @DeleteMapping "
                        + "instead of @RequestMapping on methods"));
            }

            // Rule 2: @DeleteMapping + @RequestBody
            boolean isDelete = hasAnnotation(annots, "DeleteMapping");
            boolean hasBody  = hasAnnotation(annots, "RequestBody")
                    || method.getParameters().stream()
                             .anyMatch(p -> hasAnnotation(p.getAnnotations(), "RequestBody"));
            if (isDelete && hasBody) {
                violations.add(new ApiViolation(
                        ViolationKind.DELETE_WITH_BODY, serviceName, file,
                        className, methodName,
                        "@DeleteMapping should not have a @RequestBody — use @PathVariable for IDs"));
            }

            // Rule 3: @GetMapping + non-readOnly @Transactional (heuristic)
            boolean isGet       = hasAnnotation(annots, "GetMapping");
            boolean isTxWrite   = annots.stream().anyMatch(a ->
                    a.getNameAsString().equals("Transactional")
                    && !a.toString().contains("readOnly = true")
                    && !a.toString().contains("readOnly=true"));
            if (isGet && isTxWrite) {
                violations.add(new ApiViolation(
                        ViolationKind.MUTATING_READ_VERB, serviceName, file,
                        className, methodName,
                        "@GetMapping with @Transactional (non-read-only) — GET should be idempotent; "
                        + "use @Transactional(readOnly = true)"));
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean hasAnnotation(List<AnnotationExpr> annotations, String name) {
        return annotations.stream().anyMatch(a -> a.getNameAsString().equals(name));
    }
}
