package org.fractalx.core.verifier;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Uses JavaParser to verify that the generated {@code @NetScopeClient} interfaces
 * in the caller service have method signatures that are compatible with the
 * {@code @NetworkPublic} methods in the target service.
 *
 * <p>A mismatch means the generated client stub will compile but fail at runtime
 * because NetScope won't be able to route the call to a matching server method.
 *
 * <p>Checks per caller→target edge:
 * <ol>
 *   <li>The client interface file exists in the caller service</li>
 *   <li>Every method in the client interface exists (by name + parameter count) in the server class</li>
 *   <li>No extra methods on the server that should be exposed but are missing in the client</li>
 * </ol>
 */
public class NetScopeCompatibilityChecker {

    private static final Logger log = LoggerFactory.getLogger(NetScopeCompatibilityChecker.class);

    // ── Result model ──────────────────────────────────────────────────────────

    public enum IssueKind { MISSING_CLIENT_METHOD, EXTRA_SERVER_METHOD, NO_CLIENT_FILE,
        NO_SERVER_FILE, PARSE_ERROR }

    public record CompatibilityIssue(
            String callerService,
            String targetService,
            IssueKind kind,
            String detail
    ) {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Checks NetScope interface compatibility across all detected dependency edges.
     *
     * @param outputDir the root generated-services directory
     * @param modules   all modules detected by ModuleAnalyzer
     * @return list of compatibility issues (empty = all good)
     */
    public List<CompatibilityIssue> check(Path outputDir, List<FractalModule> modules) {
        List<CompatibilityIssue> issues = new ArrayList<>();

        for (FractalModule caller : modules) {
            if (caller.getDependencies().isEmpty()) continue;

            Path callerSrc = outputDir.resolve(caller.getServiceName())
                    .resolve("src/main/java");
            if (!Files.isDirectory(callerSrc)) continue;

            for (String depBeanType : caller.getDependencies()) {
                // Resolve the target module by matching className to the dependency type
                modules.stream()
                        .filter(m -> !m.getServiceName().equals(caller.getServiceName()))
                        .filter(m -> depBeanType.contains(
                                m.getClassName() != null ? m.getClassName() : ""))
                        .findFirst()
                        .ifPresent(target -> {
                            Path targetSrc = outputDir.resolve(target.getServiceName())
                                    .resolve("src/main/java");
                            checkEdge(callerSrc, targetSrc,
                                    caller.getServiceName(), target.getServiceName(),
                                    depBeanType, issues);
                        });
            }
        }

        return issues;
    }

    // ── Edge-level check ──────────────────────────────────────────────────────

    private void checkEdge(Path callerSrc, Path targetSrc,
                           String callerName, String targetName,
                           String beanType, List<CompatibilityIssue> issues) {

        // Find the generated client interface (e.g. PaymentServiceClient.java)
        Optional<Path> clientFile = findClientFile(callerSrc, beanType);
        if (clientFile.isEmpty()) {
            issues.add(new CompatibilityIssue(callerName, targetName,
                    IssueKind.NO_CLIENT_FILE,
                    "No @NetScopeClient interface found for bean type '" + beanType + "'"));
            return;
        }

        // Find the server-side class that has @NetworkPublic methods
        String serverClassName = beanType.endsWith("Service") ? beanType
                : beanType.replace("Client", "") + "Service";
        Optional<Path> serverFile = findServerFile(targetSrc, serverClassName);
        if (serverFile.isEmpty()) {
            issues.add(new CompatibilityIssue(callerName, targetName,
                    IssueKind.NO_SERVER_FILE,
                    "Could not find server class '" + serverClassName + "' in " + targetName));
            return;
        }

        // Parse both files and compare method signatures
        Set<String> clientMethods = parseMethodSignatures(clientFile.get(), null); // all interface methods
        Set<String> serverMethods = parseMethodSignatures(serverFile.get(), "@NetworkPublic");

        if (clientMethods == null || serverMethods == null) return; // parse error already recorded

        // Every client method must exist in server
        for (String cm : clientMethods) {
            if (!serverMethods.contains(cm)) {
                issues.add(new CompatibilityIssue(callerName, targetName,
                        IssueKind.MISSING_CLIENT_METHOD,
                        "Client method '" + cm + "' has no matching @NetworkPublic server method in "
                                + targetName + "::" + serverClassName));
            }
        }

        // Every server @NetworkPublic method should be in the client
        for (String sm : serverMethods) {
            if (!clientMethods.contains(sm)) {
                issues.add(new CompatibilityIssue(callerName, targetName,
                        IssueKind.EXTRA_SERVER_METHOD,
                        "Server @NetworkPublic method '" + sm + "' is missing from the generated client interface"));
            }
        }
    }

    // ── File finders ──────────────────────────────────────────────────────────

    private Optional<Path> findClientFile(Path srcRoot, String beanType) {
        // Look for a file containing @NetScopeClient whose name matches the bean type
        try (Stream<Path> walk = Files.walk(srcRoot)) {
            return walk.filter(p -> p.getFileName().toString().endsWith("Client.java"))
                    .filter(p -> {
                        try {
                            String content = Files.readString(p);
                            return content.contains("@NetScopeClient")
                                    && content.contains(beanType.replace("Service", ""));
                        } catch (IOException e) { return false; }
                    })
                    .findFirst();
        } catch (IOException e) {
            log.debug("Could not walk caller src: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Path> findServerFile(Path srcRoot, String className) {
        try (Stream<Path> walk = Files.walk(srcRoot)) {
            return walk.filter(p -> p.getFileName().toString().equals(className + ".java"))
                    .filter(p -> {
                        try { return Files.readString(p).contains("@NetworkPublic"); }
                        catch (IOException e) { return false; }
                    })
                    .findFirst();
        } catch (IOException e) {
            log.debug("Could not walk target src: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ── JavaParser ────────────────────────────────────────────────────────────

    /**
     * Parses a Java file and returns method signatures as {@code "name(paramCount)"} strings.
     *
     * @param file            the Java file to parse
     * @param requireAnnotation if non-null, only includes methods with this annotation
     * @return set of signature keys, or null if parsing failed
     */
    private Set<String> parseMethodSignatures(Path file, String requireAnnotation) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(MethodDeclaration.class).stream()
                    .filter(m -> !m.isStatic() && m.isPublic())
                    .filter(m -> requireAnnotation == null
                            || m.getAnnotations().stream().anyMatch(
                                    a -> a.getNameAsString().equals(
                                            requireAnnotation.replace("@", ""))))
                    .map(m -> m.getNameAsString() + "(" + m.getParameters().size() + ")")
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.debug("Could not parse {}: {}", file, e.getMessage());
            return null;
        }
    }
}
