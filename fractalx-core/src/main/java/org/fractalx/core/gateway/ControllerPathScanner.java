package org.fractalx.core.gateway;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Scans monolith source files to discover HTTP endpoints exposed by each module's controllers.
 *
 * <p>Module boundary = Java package: all {@code @RestController} classes under the module's
 * {@link FractalModule#getPackageName()} subtree are considered owned by that module,
 * regardless of URL prefix. Cross-resource endpoints (e.g. {@code GET /api/customers/{id}/orders}
 * declared in an OrderController inside {@code com.example.order}) are correctly attributed
 * to the order-service.
 *
 * <p>Used by {@link GatewayOpenApiGenerator} to produce Postman collections and OpenAPI specs
 * that reflect actual controller paths rather than name-based URL heuristics.
 */
public class ControllerPathScanner {

    private static final Logger log = LoggerFactory.getLogger(ControllerPathScanner.class);

    /** Spring mapping annotation name → HTTP method string. */
    private static final Map<String, String> VERB_ANNOTATIONS = Map.of(
            "GetMapping",    "GET",
            "PostMapping",   "POST",
            "PutMapping",    "PUT",
            "DeleteMapping", "DELETE",
            "PatchMapping",  "PATCH"
    );

    /**
     * A discovered HTTP endpoint: HTTP method + OpenAPI-style path
     * (path variable segments rendered as {@code {id}}).
     */
    public record EndpointInfo(String method, String path) {
        /** Returns the path with {@code {id}} segments replaced by {@code *} for gateway predicates. */
        public String gatewayPath() {
            return path.replaceAll("\\{[^/]+\\}", "*");
        }

        /** Whether this path contains at least one path-variable segment. */
        public boolean hasPathVar() {
            return path.contains("{");
        }
    }

    /**
     * Scans all {@code @RestController} classes under the module's package subtree and returns
     * the set of endpoints they expose.
     *
     * @param monolithSrc path to the monolith's {@code src/main/java} directory
     * @param module      the module whose package subtree to scan
     * @return set of discovered endpoints; empty if source is unavailable or package not found
     */
    public Set<EndpointInfo> scan(Path monolithSrc, FractalModule module) {
        Set<EndpointInfo> endpoints = new LinkedHashSet<>();
        if (monolithSrc == null) return endpoints;

        String packageName = module.getPackageName();
        if (packageName == null || packageName.isBlank()) return endpoints;

        Path moduleDir = monolithSrc.resolve(packageName.replace('.', '/'));
        if (!Files.isDirectory(moduleDir)) return endpoints;

        try (Stream<Path> walk = Files.walk(moduleDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> collectEndpoints(file, endpoints));
        } catch (IOException e) {
            log.debug("Could not walk module package dir {}: {}", moduleDir, e.getMessage());
        }
        return endpoints;
    }

    private void collectEndpoints(Path file, Set<EndpointInfo> out) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(c -> hasAnnotation(c.getAnnotations(), "RestController"))
                    .forEach(cls -> {
                        String basePath = extractPath(cls.getAnnotations(), "RequestMapping");
                        for (MethodDeclaration method : cls.getMethods()) {
                            List<AnnotationExpr> annots = method.getAnnotations();
                            for (Map.Entry<String, String> entry : VERB_ANNOTATIONS.entrySet()) {
                                if (!hasAnnotation(annots, entry.getKey())) continue;
                                String methodPath = extractPath(annots, entry.getKey());
                                String fullPath   = joinPaths(basePath, methodPath);
                                out.add(new EndpointInfo(entry.getValue(), normalizePathVars(fullPath)));
                                break; // each method has at most one verb annotation
                            }
                        }
                    });
        } catch (Exception e) {
            log.debug("Could not parse {}: {}", file, e.getMessage());
        }
    }

    // ── Path utilities ────────────────────────────────────────────────────────

    /** Replaces {@code {variable}} segments with {@code {id}} for OpenAPI display. */
    public static String normalizePathVars(String path) {
        return path.replaceAll("\\{[^/]+\\}", "{id}");
    }

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
