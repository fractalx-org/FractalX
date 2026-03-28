package org.fractalx.core;

import org.fractalx.core.model.FractalModule;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

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
                    FractalModule module = extractModuleDescriptor(classDecl, annotation, cu, imports, sourceRoot);
                    modules.add(module);
                    log.info("Found decomposable module: {}  ({} imports detected)",
                            module.getServiceName(), module.getDetectedImports().size());
                });
            });
        }

        // Validate: no two modules may share the same package — CodeCopier would copy the
        // same directory twice, producing duplicate/conflicting generated services.
        modules.stream()
                .collect(Collectors.groupingBy(FractalModule::getPackageName, Collectors.counting()))
                .forEach((pkg, count) -> {
                    if (count > 1) {
                        throw new IllegalStateException(
                            count + " @DecomposableModule classes share package '" + pkg + "'. " +
                            "Each module must have its own distinct package subtree. " +
                            "Move them into separate sub-packages (e.g., com.example.order, com.example.payment).");
                    }
                });

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
                                                   Set<String> detectedImports,
                                                   Path sourceRoot) {
        FractalModule.Builder builder = FractalModule.builder();
        builder.className(classDecl.getFullyQualifiedName().orElse(classDecl.getNameAsString()));

        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString()).orElse("");
        if (!packageName.isEmpty()) builder.packageName(packageName);

        List<String> explicitDeps = new ArrayList<>();

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
                    case "dependencies" -> explicitDeps.addAll(extractClassArrayValues(pair.getValue()));
                }
            }
        }

        List<String> dependencies;
        if (!explicitDeps.isEmpty()) {
            dependencies = explicitDeps;
            log.info("Explicit dependencies declared for {}: {}", builder.build().getServiceName(), dependencies);
        } else {
            dependencies = findDependencies(classDecl, packageName, sourceRoot);
            if (!dependencies.isEmpty()) {
                log.warn("Module '{}': dependencies inferred by type-suffix heuristic ({}). " +
                         "Declare them explicitly with @DecomposableModule(dependencies={{...}}) " +
                         "for reliability with any naming convention.",
                         // serviceName not yet set on builder if we call build() — get from annotation
                         annotation.isNormalAnnotationExpr()
                             ? annotation.asNormalAnnotationExpr().getPairs().stream()
                               .filter(p -> "serviceName".equals(p.getNameAsString()))
                               .map(p -> p.getValue().toString().replace("\"", ""))
                               .findFirst().orElse("?")
                             : "?",
                         dependencies);
            }
        }

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
     * Finds cross-module dependencies by scanning <em>all</em> {@code .java} files in the
     * module's package directory tree (not just the {@code @DecomposableModule} class).
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Walk every {@code .java} file under {@code sourceRoot/<packagePath>}.</li>
     *   <li>Collect the simple names of every class/interface declared in those files
     *       (these are "local" types that live inside this module).</li>
     *   <li>Collect every field type and constructor-parameter type that ends with
     *       {@code "Service"} or {@code "Client"}.</li>
     *   <li>Subtract local types — what remains are genuine cross-module references.</li>
     * </ol>
     *
     * <p>Falls back to scanning just {@code classDecl} when the package directory cannot be
     * resolved (e.g. running from a single-file test fixture).
     */
    private List<String> findDependencies(ClassOrInterfaceDeclaration classDecl,
                                           String packageName, Path sourceRoot) {
        Set<String> dependencies = new HashSet<>();
        Set<String> localTypes   = new HashSet<>();

        Path packageDir = sourceRoot.resolve(packageName.replace('.', '/'));
        if (Files.isDirectory(packageDir)) {
            try (var stream = Files.walk(packageDir)) {
                stream.filter(p -> p.toString().endsWith(".java"))
                      .forEach(javaFile -> {
                          try {
                              CompilationUnit cu = new JavaParser().parse(javaFile)
                                      .getResult().orElse(null);
                              if (cu == null) return;

                              // Track all locally-defined type names (classes + interfaces)
                              cu.findAll(ClassOrInterfaceDeclaration.class)
                                .forEach(c -> localTypes.add(c.getNameAsString()));

                              // Collect *Service / *Client field types
                              cu.findAll(FieldDeclaration.class).forEach(field -> {
                                  String t = field.getCommonType().asString();
                                  if (t.endsWith("Service") || t.endsWith("Client")) {
                                      dependencies.add(t);
                                      log.debug("Found candidate dependency: {} in {}", t, javaFile.getFileName());
                                  }
                              });

                              // Collect *Service / *Client constructor-parameter types
                              cu.findAll(ConstructorDeclaration.class).forEach(ctor ->
                                  ctor.getParameters().forEach(param -> {
                                      String t = param.getTypeAsString();
                                      if (t.endsWith("Service") || t.endsWith("Client")) {
                                          dependencies.add(t);
                                          log.debug("Found candidate dependency: {} in {} constructor", t, javaFile.getFileName());
                                      }
                                  }));
                          } catch (IOException e) {
                              log.warn("Could not parse {} during dependency scan", javaFile, e);
                          }
                      });
            } catch (IOException e) {
                log.warn("Could not walk package directory {} — falling back to single-class scan", packageDir, e);
                fallbackScan(classDecl, dependencies);
            }
        } else {
            // Package directory not found on disk (test fixture / non-standard layout) — fall back
            log.debug("Package directory not found: {} — using single-class fallback scan", packageDir);
            fallbackScan(classDecl, dependencies);
        }

        // Remove types that are defined within this module — they are not cross-module
        dependencies.removeAll(localTypes);
        log.debug("Local types in package '{}': {}", packageName, localTypes);
        return new ArrayList<>(dependencies);
    }

    /** Original single-class scan used as a fallback when the package directory is unavailable. */
    private void fallbackScan(ClassOrInterfaceDeclaration classDecl, Set<String> dependencies) {
        classDecl.findAll(FieldDeclaration.class).forEach(field -> {
            String t = field.getCommonType().asString();
            if (t.endsWith("Service") || t.endsWith("Client")) {
                dependencies.add(t);
                log.debug("Fallback: found dependency {} in field declaration", t);
            }
        });
        classDecl.getConstructors().forEach(ctor ->
            ctor.getParameters().forEach(param -> {
                String t = param.getType().asString();
                if (t.endsWith("Service") || t.endsWith("Client")) {
                    dependencies.add(t);
                    log.debug("Fallback: found dependency {} in constructor parameter", t);
                }
            })
        );
    }

    /**
     * Extracts simple class names from a {@code dependencies={Foo.class, Bar.class}} annotation value.
     * Handles both a single {@code ClassExpr} and an {@code ArrayInitializerExpr} of {@code ClassExpr}.
     */
    private List<String> extractClassArrayValues(Expression expr) {
        List<String> result = new ArrayList<>();
        if (expr instanceof ClassExpr ce) {
            result.add(ce.getTypeAsString());
        } else if (expr instanceof ArrayInitializerExpr aie) {
            for (Expression element : aie.getValues()) {
                if (element instanceof ClassExpr ce) {
                    result.add(ce.getTypeAsString());
                }
            }
        }
        return result;
    }
}
