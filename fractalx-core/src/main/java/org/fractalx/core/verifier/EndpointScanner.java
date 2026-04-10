package org.fractalx.core.verifier;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Scans generated Spring MVC controllers to produce a list of {@link EndpointSpec}
 * objects describing every REST endpoint exposed by a service.
 *
 * <p>Walks all {@code *Controller.java} files under {@code src/main/java},
 * reads the class-level {@code @RequestMapping} base path, and combines it with
 * per-method {@code @GetMapping}, {@code @PostMapping}, etc. annotations to yield
 * fully-qualified paths such as {@code GET /users/{id}}.
 */
public class EndpointScanner {

    private static final Logger log = LoggerFactory.getLogger(EndpointScanner.class);

    /** Maps HTTP verb → Spring annotation simple name. */
    private static final Map<String, String> VERB_ANNOTATIONS = Map.of(
            "GET",    "GetMapping",
            "POST",   "PostMapping",
            "PUT",    "PutMapping",
            "DELETE", "DeleteMapping",
            "PATCH",  "PatchMapping"
    );

    // ── Result model ──────────────────────────────────────────────────────────

    /**
     * Describes a single REST endpoint discovered via static analysis.
     *
     * @param httpMethod      HTTP verb (GET, POST, PUT, DELETE, PATCH)
     * @param path            Full path including path variables, e.g. {@code /users/{id}}
     * @param hasPathVars     {@code true} if the path contains at least one {@code {variable}}
     * @param hasRequestBody  {@code true} if the handler method declares a {@code @RequestBody} parameter
     * @param controllerClass Simple name of the declaring controller class
     * @param methodName      Java method name of the handler
     */
    public record EndpointSpec(
            String  httpMethod,
            String  path,
            boolean hasPathVars,
            boolean hasRequestBody,
            String  controllerClass,
            String  methodName
    ) {
        @Override
        public String toString() {
            return httpMethod + " " + path
                    + (hasPathVars    ? " [path-vars]" : "")
                    + (hasRequestBody ? " [body]"      : "");
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Scans all {@code *Controller.java} files under
     * {@code <serviceDir>/src/main/java} and returns the discovered endpoints.
     *
     * @param serviceDir root directory of a generated service
     * @return immutable list of endpoint specs; empty if no controllers found or on parse errors
     */
    public List<EndpointSpec> scan(Path serviceDir) {
        Path srcJava = serviceDir.resolve("src/main/java");
        if (!Files.isDirectory(srcJava)) return List.of();

        List<EndpointSpec> specs = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(srcJava)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("Controller.java"))
                    .forEach(file -> scanFile(file, specs));
        } catch (IOException e) {
            log.debug("Could not walk {}: {}", srcJava, e.getMessage());
        }
        return List.copyOf(specs);
    }

    // ── Per-file scanning ─────────────────────────────────────────────────────

    private void scanFile(Path file, List<EndpointSpec> out) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(c -> hasAnnotation(c.getAnnotations(), "RestController"))
                    .forEach(cls -> scanController(cls, out));
        } catch (Exception e) {
            log.debug("Could not parse {}: {}", file, e.getMessage());
        }
    }

    private void scanController(ClassOrInterfaceDeclaration cls, List<EndpointSpec> out) {
        String className = cls.getNameAsString();
        String basePath  = extractPath(cls.getAnnotations(), "RequestMapping");

        for (MethodDeclaration method : cls.getMethods()) {
            List<AnnotationExpr> annots = method.getAnnotations();

            for (Map.Entry<String, String> entry : VERB_ANNOTATIONS.entrySet()) {
                String httpMethod = entry.getKey();
                String annotName  = entry.getValue();
                if (!hasAnnotation(annots, annotName)) continue;

                String methodPath = extractPath(annots, annotName);
                String fullPath   = joinPaths(basePath, methodPath);
                boolean hasVars   = fullPath.contains("{");
                boolean hasBody   = method.getParameters().stream()
                        .anyMatch(p -> hasAnnotation(p.getAnnotations(), "RequestBody"));

                out.add(new EndpointSpec(
                        httpMethod, fullPath, hasVars, hasBody, className, method.getNameAsString()));
                break; // one verb annotation per method
            }
        }
    }

    // ── Annotation value extraction ───────────────────────────────────────────

    /**
     * Extracts the path string from annotations in these forms:
     * <ul>
     *   <li>{@code @GetMapping("/path")}            — single-member</li>
     *   <li>{@code @GetMapping(value = "/path")}    — named {@code value}</li>
     *   <li>{@code @GetMapping(path  = "/path")}    — named {@code path}</li>
     *   <li>{@code @GetMapping}                     — no value → returns {@code ""}</li>
     * </ul>
     */
    private String extractPath(List<AnnotationExpr> annotations, String annotName) {
        for (AnnotationExpr a : annotations) {
            if (!a.getNameAsString().equals(annotName)) continue;

            if (a instanceof SingleMemberAnnotationExpr sma) {
                return stripQuotes(sma.getMemberValue().toString());
            }
            if (a instanceof NormalAnnotationExpr nma) {
                for (var pair : nma.getPairs()) {
                    String key = pair.getNameAsString();
                    if (key.equals("value") || key.equals("path"))
                        return stripQuotes(pair.getValue().toString());
                }
            }
            // MarkerAnnotationExpr → @GetMapping with no arguments
            return "";
        }
        return "";
    }

    private static String stripQuotes(String raw) {
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() > 1)
            return raw.substring(1, raw.length() - 1);
        return raw;
    }

    private static String joinPaths(String base, String sub) {
        if (base.isBlank() && sub.isBlank()) return "/";
        if (base.isBlank()) return sub.startsWith("/") ? sub : "/" + sub;
        if (sub.isBlank())  return base;
        boolean baseSlash = base.endsWith("/");
        boolean subSlash  = sub.startsWith("/");
        if (baseSlash && subSlash)   return base + sub.substring(1);
        if (!baseSlash && !subSlash) return base + "/" + sub;
        return base + sub;
    }

    private static boolean hasAnnotation(List<AnnotationExpr> annotations, String name) {
        return annotations.stream().anyMatch(a -> a.getNameAsString().equals(name));
    }
}
