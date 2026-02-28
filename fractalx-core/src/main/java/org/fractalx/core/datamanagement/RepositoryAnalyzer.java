package org.fractalx.core.datamanagement;

import org.fractalx.core.model.FractalModule;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Analyzes JPA repository usage across module boundaries.
 *
 * <p>After decomposition each service must own its own repositories — no service
 * should directly depend on a repository that lives in another service's package.
 * This analyzer detects such violations and reports them so developers can:
 * <ul>
 *   <li>Replace direct repo calls with NetScope client calls to the owning service.</li>
 *   <li>Introduce a read-model / snapshot for read-only cross-service data needs.</li>
 * </ul>
 *
 * <p>Results are logged as structured warnings and returned as a
 * {@link RepositoryReport} so the call site can decide how to surface them.
 */
public class RepositoryAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(RepositoryAnalyzer.class);

    private static final Set<String> SPRING_REPO_SUPERINTERFACES = Set.of(
            "JpaRepository", "CrudRepository", "PagingAndSortingRepository",
            "ListCrudRepository", "ReactiveCrudRepository", "MongoRepository"
    );

    private final JavaParser javaParser = new JavaParser();

    /**
     * Analyses all modules and returns a consolidated report of cross-boundary
     * repository usages.
     *
     * @param sourceRoot monolith source root
     * @param modules    all discovered decomposable modules
     */
    public RepositoryReport analyze(Path sourceRoot, List<FractalModule> modules) {
        // Phase 1: discover which repos belong to which module
        Map<String, String> repoOwnership = discoverRepositoryOwnership(sourceRoot, modules);

        if (repoOwnership.isEmpty()) {
            log.debug("No Spring Data repositories found in source root");
            return RepositoryReport.empty();
        }

        log.info("Discovered {} Spring Data repositories", repoOwnership.size());

        // Phase 2: detect cross-boundary usages
        List<Violation> violations = detectCrossBoundaryUsage(sourceRoot, modules, repoOwnership);

        if (violations.isEmpty()) {
            log.info("✅ [Repository] No cross-boundary repository violations detected");
        } else {
            log.warn("⚠️ [Repository] {} cross-boundary repository violation(s) detected:", violations.size());
            for (Violation v : violations) {
                log.warn("  • {} (owned by {}) is injected in {} (belongs to {})",
                        v.repositoryName, v.ownerModule, v.usedInClass, v.usedInModule);
                log.warn("    → Replace with a NetScope client call to '{}' or introduce a read-model.",
                        v.ownerModule);
            }
        }

        return new RepositoryReport(repoOwnership, violations);
    }

    // -------------------------------------------------------------------------
    // Phase 1: ownership discovery
    // -------------------------------------------------------------------------

    /**
     * Walks the source and maps each repository interface name to the module whose
     * package contains it.
     */
    private Map<String, String> discoverRepositoryOwnership(Path sourceRoot, List<FractalModule> modules) {
        Map<String, String> ownership = new LinkedHashMap<>();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    CompilationUnit cu = javaParser.parse(path).getResult().orElse(null);
                    if (cu == null) return;

                    String filePackage = cu.getPackageDeclaration()
                            .map(pd -> pd.getNameAsString())
                            .orElse("");

                    for (ClassOrInterfaceDeclaration iface : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                        if (!iface.isInterface()) continue;
                        if (!extendsSpringRepository(iface)) continue;

                        String repoName = iface.getNameAsString();
                        String ownerModule = findOwningModule(filePackage, modules);
                        ownership.put(repoName, ownerModule);
                        log.debug("Repository '{}' owned by '{}'", repoName, ownerModule);
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (IOException e) {
            log.warn("Could not walk source root for repository discovery: {}", e.getMessage());
        }
        return ownership;
    }

    // -------------------------------------------------------------------------
    // Phase 2: cross-boundary usage detection
    // -------------------------------------------------------------------------

    private List<Violation> detectCrossBoundaryUsage(Path sourceRoot,
                                                      List<FractalModule> modules,
                                                      Map<String, String> repoOwnership) {
        List<Violation> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    CompilationUnit cu = javaParser.parse(path).getResult().orElse(null);
                    if (cu == null) return;

                    String filePackage = cu.getPackageDeclaration()
                            .map(pd -> pd.getNameAsString()).orElse("");
                    String usedInModule = findOwningModule(filePackage, modules);

                    for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                        if (cls.isInterface()) continue;
                        checkFieldInjections(cls, usedInModule, repoOwnership, violations);
                        checkConstructorParams(cls, usedInModule, repoOwnership, violations);
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (IOException e) {
            log.warn("Could not walk source for cross-boundary analysis: {}", e.getMessage());
        }
        return violations;
    }

    private void checkFieldInjections(ClassOrInterfaceDeclaration cls,
                                      String usedInModule,
                                      Map<String, String> repoOwnership,
                                      List<Violation> violations) {
        for (FieldDeclaration field : cls.getFields()) {
            String typeName = field.getElementType().asString();
            if (repoOwnership.containsKey(typeName)) {
                String ownerModule = repoOwnership.get(typeName);
                if (!ownerModule.equals(usedInModule) && !"unknown".equals(ownerModule)) {
                    violations.add(new Violation(typeName, ownerModule, cls.getNameAsString(), usedInModule));
                }
            }
        }
    }

    private void checkConstructorParams(ClassOrInterfaceDeclaration cls,
                                        String usedInModule,
                                        Map<String, String> repoOwnership,
                                        List<Violation> violations) {
        cls.getConstructors().forEach(ctor ->
                ctor.getParameters().forEach(param -> {
                    String typeName = param.getType().asString();
                    if (repoOwnership.containsKey(typeName)) {
                        String ownerModule = repoOwnership.get(typeName);
                        if (!ownerModule.equals(usedInModule) && !"unknown".equals(ownerModule)) {
                            violations.add(new Violation(typeName, ownerModule,
                                    cls.getNameAsString(), usedInModule));
                        }
                    }
                })
        );
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private boolean extendsSpringRepository(ClassOrInterfaceDeclaration iface) {
        return iface.getExtendedTypes().stream()
                .map(ClassOrInterfaceType::getNameAsString)
                .anyMatch(SPRING_REPO_SUPERINTERFACES::contains);
    }

    private String findOwningModule(String packageName, List<FractalModule> modules) {
        return modules.stream()
                .filter(m -> packageName.startsWith(m.getPackageName())
                          || m.getPackageName().startsWith(packageName))
                .map(FractalModule::getServiceName)
                .findFirst()
                .orElse("unknown");
    }

    // -------------------------------------------------------------------------
    // Result types
    // -------------------------------------------------------------------------

    public static final class Violation {
        public final String repositoryName;
        public final String ownerModule;
        public final String usedInClass;
        public final String usedInModule;

        Violation(String repositoryName, String ownerModule, String usedInClass, String usedInModule) {
            this.repositoryName = repositoryName;
            this.ownerModule    = ownerModule;
            this.usedInClass    = usedInClass;
            this.usedInModule   = usedInModule;
        }
    }

    public static final class RepositoryReport {
        /** Maps repository simple name → owning module service name. */
        public final Map<String, String> ownership;
        public final List<Violation> violations;

        RepositoryReport(Map<String, String> ownership, List<Violation> violations) {
            this.ownership  = Collections.unmodifiableMap(ownership);
            this.violations = Collections.unmodifiableList(violations);
        }

        static RepositoryReport empty() {
            return new RepositoryReport(Map.of(), List.of());
        }

        public boolean hasViolations() { return !violations.isEmpty(); }
    }
}
