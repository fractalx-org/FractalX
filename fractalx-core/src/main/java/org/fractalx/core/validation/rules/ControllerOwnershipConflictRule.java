package org.fractalx.core.validation.rules;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import org.fractalx.core.model.FractalModule;
import org.fractalx.core.validation.ValidationContext;
import org.fractalx.core.validation.ValidationIssue;
import org.fractalx.core.validation.ValidationRule;
import org.fractalx.core.validation.ValidationSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Detects when two modules expose controllers with identical HTTP method + path combinations.
 *
 * <p>After decomposition, each endpoint must be owned by exactly one service. If two modules
 * both declare a handler for {@code GET /api/orders/{id}}, the gateway cannot route the request
 * deterministically and one service will never receive it.
 *
 * <p>Fix: consolidate the duplicate handler into the single module that logically owns it, or
 * refactor the path to make the two endpoints distinct.
 */
public class ControllerOwnershipConflictRule implements ValidationRule {

    private static final Logger log = LoggerFactory.getLogger(ControllerOwnershipConflictRule.class);

    /** Maps HTTP verb → Spring annotation simple name. */
    private static final Map<String, String> VERB_ANNOTATIONS = Map.of(
            "GET",    "GetMapping",
            "POST",   "PostMapping",
            "PUT",    "PutMapping",
            "DELETE", "DeleteMapping",
            "PATCH",  "PatchMapping"
    );

    @Override
    public String ruleId() { return "CTRL_CONFLICT"; }

    @Override
    public List<ValidationIssue> validate(ValidationContext ctx) throws IOException {
        List<ValidationIssue> issues = new ArrayList<>();

        // endpointKey ("GET /api/orders/{id}") → first module that owns it
        Map<String, String> endpointOwner = new LinkedHashMap<>();

        for (FractalModule module : ctx.modules()) {
            String packageName = module.getPackageName();
            if (packageName == null || packageName.isBlank()) continue;

            // Convert package name to relative directory path: com.example.order → com/example/order
            String packagePath = packageName.replace('.', '/');
            Path moduleDir = ctx.sourceRoot().resolve(packagePath);

            if (!Files.isDirectory(moduleDir)) continue;

            List<String> endpoints = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(moduleDir)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .forEach(file -> collectEndpoints(file, endpoints));
            } catch (IOException e) {
                log.debug("Could not walk module package dir {}: {}", moduleDir, e.getMessage());
                continue;
            }

            for (String endpointKey : endpoints) {
                String firstOwner = endpointOwner.get(endpointKey);
                if (firstOwner == null) {
                    endpointOwner.put(endpointKey, module.getServiceName());
                } else if (!firstOwner.equals(module.getServiceName())) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR,
                            ruleId(),
                            module.getServiceName(),
                            "[" + module.getServiceName() + "] exposes [" + endpointKey
                                    + "] which is already owned by [" + firstOwner
                                    + "]. Two services cannot handle the same endpoint.",
                            "Move the handler to a single module package, or refactor to"
                                    + " eliminate the duplicate endpoint."));
                }
            }
        }

        return issues;
    }

    // ── Per-file scanning ─────────────────────────────────────────────────────

    private void collectEndpoints(Path file, List<String> out) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(c -> hasAnnotation(c.getAnnotations(), "RestController"))
                    .forEach(cls -> scanController(cls, out));
        } catch (Exception e) {
            log.debug("Could not parse {}: {}", file, e.getMessage());
        }
    }

    private void scanController(ClassOrInterfaceDeclaration cls, List<String> out) {
        String basePath = extractPath(cls.getAnnotations(), "RequestMapping");

        for (MethodDeclaration method : cls.getMethods()) {
            List<AnnotationExpr> annots = method.getAnnotations();

            for (Map.Entry<String, String> entry : VERB_ANNOTATIONS.entrySet()) {
                String httpMethod = entry.getKey();
                String annotName  = entry.getValue();
                if (!hasAnnotation(annots, annotName)) continue;

                String methodPath = extractPath(annots, annotName);
                String fullPath   = joinPaths(basePath, methodPath);
                out.add(httpMethod + " " + fullPath);
                break; // one verb annotation per method
            }
        }
    }

    // ── Annotation value extraction ───────────────────────────────────────────

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
