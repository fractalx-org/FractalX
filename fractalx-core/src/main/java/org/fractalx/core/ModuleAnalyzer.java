package org.fractalx.core;

import org.fractalx.core.model.FractalModule;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Analyzes source code to identify FractalX decomposable modules.
 *
 * <p>Uses a two-phase approach:
 * <ol>
 *   <li>Parse every {@code .java} file in the source tree once.</li>
 *   <li>For each {@code @DecomposableModule} class found, collect all import
 *       statements from every file whose package matches that module's package
 *       prefix. These are stored in {@link FractalModule#getDetectedImports()}
 *       so downstream generators (e.g. {@code PomGenerator}) can emit only the
 *       Maven dependencies the module actually needs.</li>
 * </ol>
 */
public class ModuleAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ModuleAnalyzer.class);
    private final JavaParser javaParser;

    public ModuleAnalyzer() {
        this.javaParser = new JavaParser();
    }

    /**
     * Analyzes a source directory and returns all decomposable modules found.
     */
    public List<FractalModule> analyzeProject(Path sourceRoot) throws IOException {

        // ── Phase 1: parse every .java file once ─────────────────────────────
        Map<Path, CompilationUnit> parsedFiles = new LinkedHashMap<>();
        Files.walk(sourceRoot)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> {
                    try {
                        javaParser.parse(p).getResult()
                                .ifPresent(cu -> parsedFiles.put(p, cu));
                    } catch (IOException e) {
                        log.error("Failed to parse file: {}", p, e);
                    }
                });

        // ── Phase 2: find @DecomposableModule classes ─────────────────────────
        List<FractalModule> modules = new ArrayList<>();
        for (CompilationUnit cu : parsedFiles.values()) {
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                classDecl.getAnnotationByName("DecomposableModule").ifPresent(annotation -> {
                    String pkgName = cu.getPackageDeclaration()
                            .map(pd -> pd.getNameAsString()).orElse("");
                    Set<String> imports = collectImportsForPackage(pkgName, parsedFiles);
                    FractalModule module = extractModuleDescriptor(classDecl, annotation, cu, imports);
                    modules.add(module);
                    log.info("Found decomposable module: {}  ({} imports detected)",
                            module.getServiceName(), module.getDetectedImports().size());
                });
            });
        }

        return modules;
    }

    // ── Import collection ────────────────────────────────────────────────────

    /**
     * Collects all import strings from every parsed file whose package equals
     * {@code basePackage} or starts with {@code basePackage + "."}.
     */
    private Set<String> collectImportsForPackage(String basePackage,
                                                  Map<Path, CompilationUnit> parsedFiles) {
        Set<String> imports = new LinkedHashSet<>();
        for (CompilationUnit cu : parsedFiles.values()) {
            String pkg = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString()).orElse("");
            if (pkg.equals(basePackage) || pkg.startsWith(basePackage + ".")) {
                cu.getImports().forEach(imp -> imports.add(imp.getNameAsString()));
            }
        }
        return imports;
    }

    // ── Module extraction ────────────────────────────────────────────────────

    private FractalModule extractModuleDescriptor(ClassOrInterfaceDeclaration classDecl,
                                                   AnnotationExpr annotation,
                                                   CompilationUnit cu,
                                                   Set<String> detectedImports) {
        FractalModule.Builder builder = FractalModule.builder();
        builder.className(classDecl.getFullyQualifiedName().orElse(classDecl.getNameAsString()));

        cu.getPackageDeclaration().ifPresent(pd -> builder.packageName(pd.getNameAsString()));

        if (annotation.isNormalAnnotationExpr()) {
            NormalAnnotationExpr normalAnnotation = annotation.asNormalAnnotationExpr();
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                String name  = pair.getNameAsString();
                String value = pair.getValue().toString().replace("\"", "");

                switch (name) {
                    case "serviceName" -> builder.serviceName(value);
                    case "port" -> {
                        try {
                            builder.port(Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            log.warn("Invalid port number: {}", value);
                        }
                    }
                    case "independentDeployment" -> builder.independentDeployment(Boolean.parseBoolean(value));
                }
            }
        }

        List<String> dependencies = findDependencies(classDecl);
        builder.dependencies(dependencies);
        builder.detectedImports(detectedImports);

        FractalModule module = builder.build();

        if (!dependencies.isEmpty()) {
            log.info("Cross-module dependencies for {}: {}", module.getServiceName(), dependencies);
        }
        if (!detectedImports.isEmpty()) {
            log.debug("Detected imports for {}: {}", module.getServiceName(), detectedImports);
        }

        return module;
    }

    /**
     * Finds cross-module dependencies by inspecting injected fields and constructor parameters.
     * Types ending with "Client" or "Service" are treated as potential cross-module calls.
     */
    private List<String> findDependencies(ClassOrInterfaceDeclaration classDecl) {
        Set<String> dependencies = new HashSet<>();

        classDecl.findAll(FieldDeclaration.class).forEach(field -> {
            String fieldType = field.getCommonType().asString();
            if (fieldType.endsWith("Client") || fieldType.endsWith("Service")) {
                dependencies.add(fieldType);
                log.debug("Found dependency: {} in field declaration", fieldType);
            }
        });

        classDecl.getConstructors().forEach(constructor ->
                constructor.getParameters().forEach(param -> {
                    String paramType = param.getType().asString();
                    if (paramType.endsWith("Client") || paramType.endsWith("Service")) {
                        dependencies.add(paramType);
                        log.debug("Found dependency: {} in constructor parameter", paramType);
                    }
                })
        );

        return new ArrayList<>(dependencies);
    }
}
