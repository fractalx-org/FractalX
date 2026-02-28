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
import java.util.List;
import java.util.Optional;
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

        int grpcPort = targetModule.getPort() + 10000;
        List<CrossModuleCall> methods = extractPublicMethods(beanType, context.getSourceRoot(), targetServiceName);

        String interfaceName = beanType + "Client";
        Files.writeString(
                packagePath.resolve(interfaceName + ".java"),
                buildClientInterface(context.getModule().getPackageName(), interfaceName,
                        beanType, targetServiceName, grpcPort, methods)
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
    private List<CrossModuleCall> extractPublicMethods(String beanType, Path sourceRoot, String targetServiceName) {
        List<CrossModuleCall> calls = new ArrayList<>();

        try {
            Optional<Path> sourceFile = Files.walk(sourceRoot)
                    .filter(p -> p.getFileName().toString().equals(beanType + ".java"))
                    .findFirst();

            if (sourceFile.isEmpty()) {
                log.warn("Source file not found for bean type '{}' – client interface will be empty", beanType);
                return calls;
            }

            CompilationUnit cu = new JavaParser().parse(sourceFile.get()).getResult().orElse(null);
            if (cu == null) return calls;

            cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(c -> c.getNameAsString().equals(beanType))
                    .findFirst()
                    .ifPresent(classDecl ->
                            classDecl.getMethods().forEach(method -> {
                                if (method.isPublic() && !method.isStatic()) {
                                    List<String> params = method.getParameters().stream()
                                            .map(p -> p.getType().asString() + " " + p.getNameAsString())
                                            .collect(Collectors.toList());
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

    // -------------------------------------------------------------------------
    // Source generation
    // -------------------------------------------------------------------------

    private String buildClientInterface(String packageName,
                                        String interfaceName,
                                        String beanType,
                                        String targetServiceName,
                                        int grpcPort,
                                        List<CrossModuleCall> methods) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import org.fractalx.netscope.client.annotation.NetScopeClient;\n\n");
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
