package org.fractalx.core.generator.service;

import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.fractalx.core.model.CrossModuleCall;
import org.fractalx.core.model.FractalModule;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates a {@code @NetScopeClient} interface for each cross-module dependency
 * declared on the current module.
 *
 * <p>For each dependency type (e.g., {@code PaymentService}), this step:
 * <ol>
 *   <li>Locates the target bean's source file in the monolith source root.</li>
 *   <li>Extracts all public, non-static method signatures via AST parsing.</li>
 *   <li>Generates an annotated interface so the client service can invoke the
 *       remote bean as a plain Java method call.</li>
 * </ol>
 */
public class NetScopeClientGenerator implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(NetScopeClientGenerator.class);

    @Override
    public void generate(GenerationContext context) throws IOException {
        FractalModule module = context.getModule();
        List<String> deps = module.getDependencies();

        if (deps.isEmpty()) {
            log.debug("No dependencies for {} – skipping NetScope client generation", module.getServiceName());
            return;
        }

        Path packagePath = toPackagePath(context.getSrcMainJava(), module.getPackageName());
        Files.createDirectories(packagePath);

        for (String beanType : deps) {
            generateClientInterface(beanType, packagePath, context);
        }
    }

    private void generateClientInterface(String beanType, Path packagePath, GenerationContext context) throws IOException {
        String targetServiceName = beanTypeToServiceName(beanType);
        FractalModule targetModule = findModule(targetServiceName, context.getAllModules());

        if (targetModule == null) {
            log.warn("Target module '{}' not found for dependency type '{}' – skipping", targetServiceName, beanType);
            return;
        }

        int grpcPort = targetModule.grpcPort();
        Set<String> requiredImports = new LinkedHashSet<>();
        List<CrossModuleCall> methods = extractPublicMethods(beanType, context.getSourceRoot(), targetServiceName, requiredImports);

        copyRequiredModelClasses(requiredImports, context.getSourceRoot(), context.getSrcMainJava());

        String interfaceName = beanType + "Client";
        Files.writeString(
                packagePath.resolve(interfaceName + ".java"),
                buildClientInterface(context.getModule().getPackageName(), interfaceName,
                        beanType, targetServiceName, grpcPort, methods, requiredImports)
        );

        log.info("Generated NetScope client interface: {} → {} (gRPC :{})",
                interfaceName, targetServiceName, grpcPort);
    }

    // -------------------------------------------------------------------------
    // Source analysis
    // -------------------------------------------------------------------------

    /**
     * Walks the monolith source root to find {@code <beanType>.java}, parses it
     * with JavaParser, and returns a {@link CrossModuleCall} for every public
     * non-static method.
     */
    private List<CrossModuleCall> extractPublicMethods(String beanType, Path sourceRoot,
                                                       String targetServiceName,
                                                       Set<String> requiredImports) {
        List<CrossModuleCall> calls = new ArrayList<>();

        try {
            Optional<Path> sourceFile = findSourceFile(sourceRoot, beanType, targetServiceName);

            if (sourceFile.isEmpty()) {
                log.warn("Source file not found for bean type '{}' – client interface will be empty", beanType);
                return calls;
            }

            CompilationUnit cu = new JavaParser().parse(sourceFile.get()).getResult().orElse(null);
            if (cu == null) return calls;

            // Build simple-name → FQN map from the source file's own imports
            Map<String, String> importMap = new HashMap<>();
            cu.getImports().forEach(imp -> {
                if (!imp.isAsterisk() && !imp.isStatic()) {
                    String fqn = imp.getNameAsString();
                    String simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);
                    importMap.put(simpleName, fqn);
                }
            });

            // Same-package types are never imported in their own source file.
            // Resolve them by scanning the source file's directory.
            Path sourceFileDir = sourceFile.get().getParent();
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(c -> c.getNameAsString().equals(beanType))
                    .findFirst()
                    .ifPresent(classDecl ->
                            classDecl.getMethods().forEach(method -> {
                                if (method.isPublic() && !method.isStatic()) {
                                    List<String> params = method.getParameters().stream()
                                            .map(p -> p.getType().asString() + " " + p.getNameAsString())
                                            .collect(Collectors.toList());

                                    collectTypeImports(method.getType().asString(), importMap,
                                            requiredImports, sourceFileDir, packageName);
                                    method.getParameters().forEach(p ->
                                            collectTypeImports(p.getType().asString(), importMap,
                                                    requiredImports, sourceFileDir, packageName));

                                    calls.add(new CrossModuleCall(
                                            beanType,
                                            targetServiceName,
                                            method.getNameAsString(),
                                            method.getType().asString(),
                                            params
                                    ));
                                }
                            })
                    );

        } catch (IOException e) {
            log.error("Failed to extract public methods from bean type '{}'", beanType, e);
        }

        return calls;
    }

    /**
     * Resolves type tokens (including generics like {@code List<Product>}) to their
     * FQNs and adds them to {@code requiredImports}.
     *
     * <p>First checks {@code importMap} (explicit imports in the source file).
     * Falls back to a same-directory lookup for types in the same package as the
     * source bean — those are never imported in their own file.
     */
    private void collectTypeImports(String typeStr, Map<String, String> importMap,
                                    Set<String> requiredImports,
                                    Path sourceFileDir, String packageName) {
        for (String token : typeStr.replaceAll("[<>,?\\[\\]]", " ").split("\\s+")) {
            token = token.trim();
            if (token.isEmpty()) continue;
            String fqn = importMap.get(token);
            if (fqn != null) {
                requiredImports.add(fqn);
            } else if (sourceFileDir != null && !packageName.isEmpty()) {
                // Same-package class — not imported, but lives next to the source file
                Path candidate = sourceFileDir.resolve(token + ".java");
                if (Files.exists(candidate)) {
                    requiredImports.add(packageName + "." + token);
                }
            }
        }
    }

    /**
     * Copies model class files (non-JDK, non-Spring, non-FractalX) referenced in
     * {@code requiredImports} from the monolith source root into the consuming
     * service's {@code src/main/java}, preserving the original package structure.
     */
    private void copyRequiredModelClasses(Set<String> requiredImports, Path sourceRoot,
                                          Path destSrcMain) {
        for (String fqn : requiredImports) {
            // Skip JDK, Jakarta EE, Spring, and FractalX framework packages.
            // The Files.exists() check below is the real gate — framework classes
            // won't be present in the monolith's src/main/java and are silently skipped.
            if (fqn.startsWith("java.")
                    || fqn.startsWith("javax.")
                    || fqn.startsWith("jakarta.")
                    || fqn.startsWith("org.springframework.")
                    || fqn.startsWith("org.fractalx.")) {
                continue;
            }
            // Derive relative path, e.g. com.fractalx.testapp.inventory.Product → .../Product.java
            String relativePath = fqn.replace('.', '/') + ".java";
            Path src = sourceRoot.resolve(relativePath);
            if (!Files.exists(src)) continue;

            Path dest = destSrcMain.resolve(relativePath);
            try {
                Files.createDirectories(dest.getParent());
                if (!Files.exists(dest)) {
                    Files.copy(src, dest);
                    log.info("Copied model class {} → {}", fqn, dest);
                }
            } catch (IOException e) {
                log.warn("Could not copy model class {}: {}", fqn, e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Source generation
    // -------------------------------------------------------------------------

    private String buildClientInterface(String packageName,
                                        String interfaceName,
                                        String beanType,
                                        String targetServiceName,
                                        int grpcPort,
                                        List<CrossModuleCall> methods,
                                        Set<String> requiredImports) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import org.fractalx.netscope.client.annotation.NetScopeClient;\n");
        for (String imp : new java.util.TreeSet<>(requiredImports)) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("\n");
        sb.append("/**\n");
        sb.append(" * NetScope client interface for ").append(beanType).append(".\n");
        sb.append(" * Connects to ").append(targetServiceName).append(" on gRPC port ").append(grpcPort).append(".\n");
        sb.append(" * Generated by FractalX Framework.\n");
        sb.append(" */\n");
        sb.append("@NetScopeClient(server = \"").append(targetServiceName)
                .append("\", beanName = \"").append(beanType).append("\")\n");
        sb.append("public interface ").append(interfaceName).append(" {\n\n");

        for (CrossModuleCall method : methods) {
            String params = String.join(", ", method.getParameters());
            sb.append("    ").append(method.getReturnType())
                    .append(" ").append(method.getMethodName())
                    .append("(").append(params).append(");\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /**
     * Derives the target service name from a bean type name using convention.
     * <p>Examples: {@code PaymentService → payment-service},
     * {@code OrderClient → order-service}.
     */
    public static String beanTypeToServiceName(String beanType) {
        String stripped = beanType.replaceAll("(Service|Client)$", "");
        return stripped.replaceAll("([A-Z])", "-$1").toLowerCase().replaceFirst("^-", "") + "-service";
    }

    /**
     * Finds the source file for {@code beanType} under {@code sourceRoot}.
     *
     * <p>When multiple files share the same simple name (different packages), the one
     * whose package declaration starts with {@code expectedServiceName} converted to a
     * package prefix is preferred. Falls back to the first match with a warning.
     *
     * @param sourceRoot          monolith {@code src/main/java}
     * @param beanType            simple class name, e.g., {@code PaymentService}
     * @param expectedServiceName logical service name, e.g., {@code payment-service}
     */
    private Optional<Path> findSourceFile(Path sourceRoot, String beanType, String expectedServiceName)
            throws IOException {
        List<Path> candidates;
        try (java.util.stream.Stream<Path> stream = Files.walk(sourceRoot)) {
            candidates = stream
                    .filter(p -> p.getFileName().toString().equals(beanType + ".java"))
                    .collect(java.util.stream.Collectors.toList());
        }

        if (candidates.isEmpty()) return Optional.empty();
        if (candidates.size() == 1) return Optional.of(candidates.get(0));

        // Multiple candidates — prefer the one whose package matches the target service
        JavaParser parser = new JavaParser();
        String pkgHint = expectedServiceName.replace("-", ".");
        for (Path candidate : candidates) {
            Optional<CompilationUnit> cu = parser.parse(candidate).getResult();
            if (cu.isPresent()) {
                String pkg = cu.get().getPackageDeclaration()
                        .map(pd -> pd.getNameAsString()).orElse("");
                if (pkg.contains(pkgHint)) return Optional.of(candidate);
            }
        }

        log.warn("Multiple files named {}.java found under {}; using first match. " +
                 "Declare explicit @DecomposableModule(dependencies={{...}}) to resolve ambiguity.",
                 beanType, sourceRoot);
        return Optional.of(candidates.get(0));
    }

    private FractalModule findModule(String serviceName, List<FractalModule> modules) {
        return modules.stream()
                .filter(m -> serviceName.equals(m.getServiceName()))
                .findFirst()
                .orElse(null);
    }

    private Path toPackagePath(Path root, String packageName) {
        Path path = root;
        for (String part : packageName.split("\\.")) {
            path = path.resolve(part);
        }
        return path;
    }
}
