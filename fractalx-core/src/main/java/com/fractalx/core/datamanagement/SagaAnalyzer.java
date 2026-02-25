package com.fractalx.core.datamanagement;

import com.fractalx.core.generator.service.NetScopeClientGenerator;
import com.fractalx.core.model.FractalModule;
import com.fractalx.core.model.SagaDefinition;
import com.fractalx.core.model.SagaStep;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Scans source files for {@code @DistributedSaga}-annotated methods and builds
 * {@link SagaDefinition} objects for the saga orchestrator generator.
 *
 * <p>For each saga method, this analyzer:
 * <ol>
 *   <li>Reads the {@code sagaId}, {@code compensationMethod}, {@code timeout}, and
 *       {@code description} from the annotation.</li>
 *   <li>Identifies the injected fields whose types are cross-module dependencies
 *       (via {@link FractalModule#getDependencies()}).</li>
 *   <li>Scans the method body for calls on those fields in source order — each
 *       becomes a {@link SagaStep}.</li>
 *   <li>Attempts to find a matching compensation method on the same dependency bean.</li>
 * </ol>
 */
public class SagaAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(SagaAnalyzer.class);

    private final JavaParser javaParser = new JavaParser();

    /**
     * Analyzes all modules' source files and returns every detected saga definition.
     *
     * @param sourceRoot monolith source root
     * @param modules    all decomposable modules (used to resolve dependency → service mappings)
     * @return unmodifiable list of detected sagas, possibly empty
     */
    public List<SagaDefinition> analyzeSagas(Path sourceRoot, List<FractalModule> modules) {
        List<SagaDefinition> sagas = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    sagas.addAll(analyzeFile(path, modules));
                } catch (IOException e) {
                    log.error("Failed to analyze saga in: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.error("Failed to walk source root for saga analysis", e);
        }

        if (!sagas.isEmpty()) {
            log.info("Detected {} saga(s): {}", sagas.size(),
                    sagas.stream().map(SagaDefinition::getSagaId).toList());
        }
        return Collections.unmodifiableList(sagas);
    }

    // -------------------------------------------------------------------------
    // Per-file analysis
    // -------------------------------------------------------------------------

    private List<SagaDefinition> analyzeFile(Path javaFile, List<FractalModule> modules) throws IOException {
        CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
        if (cu == null) return List.of();

        List<SagaDefinition> result = new ArrayList<>();

        for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            // Build a map: field variable name → bean type (for cross-module deps only)
            Map<String, String> crossModuleFields = findCrossModuleFields(cls, modules);

            for (MethodDeclaration method : cls.getMethods()) {
                method.getAnnotationByName("DistributedSaga").ifPresent(annotation -> {
                    SagaDefinition saga = buildSagaDefinition(annotation, method, cls,
                            crossModuleFields, modules, cu);
                    if (saga != null) {
                        result.add(saga);
                        log.info("Found @DistributedSaga '{}' in {}.{}",
                                saga.getSagaId(), cls.getNameAsString(), method.getNameAsString());
                    }
                });
            }
        }
        return result;
    }

    private SagaDefinition buildSagaDefinition(AnnotationExpr annotation,
                                               MethodDeclaration method,
                                               ClassOrInterfaceDeclaration cls,
                                               Map<String, String> crossModuleFields,
                                               List<FractalModule> modules,
                                               CompilationUnit cu) {
        // Extract annotation attributes
        String sagaId            = extractAttr(annotation, "sagaId", "");
        String compensationMethod= extractAttr(annotation, "compensationMethod", "");
        String description       = extractAttr(annotation, "description", "");
        long   timeout           = extractLongAttr(annotation, "timeout", 30000L);

        if (sagaId.isBlank()) {
            log.warn("@DistributedSaga on {}.{} has no sagaId — skipping",
                    cls.getNameAsString(), method.getNameAsString());
            return null;
        }

        // Detect the owning module
        String packageName  = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        String ownerService = findOwnerService(packageName, modules);

        // Detect ordered cross-module calls within this method body
        List<SagaStep> steps = detectSteps(method, crossModuleFields, modules);

        return new SagaDefinition(
                sagaId,
                ownerService,
                cls.getNameAsString(),
                method.getNameAsString(),
                steps,
                compensationMethod,
                timeout,
                description
        );
    }

    // -------------------------------------------------------------------------
    // Field and step detection
    // -------------------------------------------------------------------------

    /**
     * Returns a map of {@code fieldName → beanType} for fields whose type is in
     * the cross-module dependency set of any module.
     */
    private Map<String, String> findCrossModuleFields(ClassOrInterfaceDeclaration cls,
                                                      List<FractalModule> modules) {
        Set<String> allDepTypes = new HashSet<>();
        for (FractalModule m : modules) {
            allDepTypes.addAll(m.getDependencies());
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (FieldDeclaration field : cls.getFields()) {
            String typeName = field.getElementType().asString();
            if (allDepTypes.contains(typeName)) {
                field.getVariables().forEach(v -> result.put(v.getNameAsString(), typeName));
            }
        }
        return result;
    }

    /**
     * Walks the method body in source order and records calls whose receiver is a
     * cross-module dependency field.
     */
    private List<SagaStep> detectSteps(MethodDeclaration method,
                                       Map<String, String> crossModuleFields,
                                       List<FractalModule> modules) {
        List<SagaStep> steps = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>(); // dedup while preserving order

        method.findAll(MethodCallExpr.class).forEach(call -> {
            if (call.getScope().isEmpty()) return;
            String scope = call.getScope().get().toString();

            if (!crossModuleFields.containsKey(scope)) return;

            String beanType    = crossModuleFields.get(scope);
            String calledMethod = call.getNameAsString();
            String key         = beanType + "#" + calledMethod;

            if (seen.contains(key)) return;
            seen.add(key);

            String targetService    = NetScopeClientGenerator.beanTypeToServiceName(beanType);
            String compensationName = deriveCompensationName(calledMethod, beanType, method, modules);

            steps.add(new SagaStep(beanType, targetService, calledMethod, compensationName));
        });

        return steps;
    }

    /**
     * Heuristically derives a compensation method name for a given forward method.
     * Checks common naming conventions: {@code cancel*}, {@code rollback*}, {@code undo*}.
     */
    private String deriveCompensationName(String forwardMethod,
                                          String beanType,
                                          MethodDeclaration parentMethod,
                                          List<FractalModule> modules) {
        // If the parent class compensation method is declared, use it
        // (set at saga level, not step level — step-level TBD)
        String base = forwardMethod;
        String capitalized = Character.toUpperCase(base.charAt(0)) + base.substring(1);

        List<String> candidates = List.of(
                "cancel"   + capitalized,
                "rollback" + capitalized,
                "undo"     + capitalized,
                "revert"   + capitalized
        );

        // For now return the first candidate — actual availability checked at generation time
        return candidates.get(0);
    }

    // -------------------------------------------------------------------------
    // Helper utilities
    // -------------------------------------------------------------------------

    private String findOwnerService(String packageName, List<FractalModule> modules) {
        return modules.stream()
                .filter(m -> packageName.startsWith(m.getPackageName())
                          || m.getPackageName().startsWith(packageName))
                .map(FractalModule::getServiceName)
                .findFirst()
                .orElse("unknown-service");
    }

    private String extractAttr(AnnotationExpr annotation, String attrName, String defaultValue) {
        if (annotation.isNormalAnnotationExpr()) {
            for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
                if (pair.getNameAsString().equals(attrName)) {
                    return pair.getValue().toString().replace("\"", "");
                }
            }
        } else if (annotation.isSingleMemberAnnotationExpr() && "sagaId".equals(attrName)) {
            return annotation.asSingleMemberAnnotationExpr().getMemberValue()
                    .toString().replace("\"", "");
        }
        return defaultValue;
    }

    private long extractLongAttr(AnnotationExpr annotation, String attrName, long defaultValue) {
        if (!annotation.isNormalAnnotationExpr()) return defaultValue;
        for (MemberValuePair pair : annotation.asNormalAnnotationExpr().getPairs()) {
            if (pair.getNameAsString().equals(attrName)) {
                try {
                    return Long.parseLong(pair.getValue().toString().replace("L", ""));
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }
}
