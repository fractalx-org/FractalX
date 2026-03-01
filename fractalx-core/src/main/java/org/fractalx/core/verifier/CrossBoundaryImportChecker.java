package org.fractalx.core.verifier;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Detects architectural violations where a generated service directly imports
 * classes from another generated service's package.
 *
 * <p>After decomposition each service must be fully isolated — it may only
 * communicate with peers through the generated NetScope client interfaces.
 * A cross-boundary import means generated code accidentally copied a direct
 * reference that should have been replaced by a NetScope call.
 *
 * <p>Example violation:
 * <pre>
 *   // Inside order-service (WRONG — direct cross-service import)
 *   import com.example.payment.PaymentService;
 * </pre>
 *
 * <p>The allowed exception is imports from {@code org.fractalx.*} (framework
 * runtime) and {@code java.*} / {@code org.springframework.*}.
 */
public class CrossBoundaryImportChecker {

    private static final Logger log = LoggerFactory.getLogger(CrossBoundaryImportChecker.class);

    public record Violation(
            String violatingService,
            String importedPackage,
            String targetService,
            Path file,
            int line
    ) {
        @Override
        public String toString() {
            return "[FAIL] " + violatingService + " → imports '" + importedPackage
                    + "' (belongs to " + targetService + ") in " + file.getFileName() + ":" + line;
        }
    }

    /**
     * Checks all generated services for cross-boundary imports.
     *
     * @param outputDir root generated-services directory
     * @param modules   all decomposed modules (used to identify each service's package)
     * @return list of violations (empty = architecturally clean)
     */
    public List<Violation> check(Path outputDir, List<FractalModule> modules) {
        List<Violation> violations = new ArrayList<>();

        for (FractalModule service : modules) {
            Path srcJava = outputDir.resolve(service.getServiceName()).resolve("src/main/java");
            if (!Files.isDirectory(srcJava)) continue;

            // Build a list of all OTHER services' packages to watch for
            List<FractalModule> peers = modules.stream()
                    .filter(m -> !m.getServiceName().equals(service.getServiceName()))
                    .toList();

            checkService(srcJava, service, peers, violations);
        }

        return violations;
    }

    // ── Per-service scan ──────────────────────────────────────────────────────

    private void checkService(Path srcJava, FractalModule service,
                              List<FractalModule> peers, List<Violation> violations) {
        try (Stream<Path> walk = Files.walk(srcJava)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(javaFile -> checkFile(javaFile, service, peers, violations));
        } catch (IOException e) {
            log.debug("Could not walk {}: {}", srcJava, e.getMessage());
        }
    }

    private void checkFile(Path javaFile, FractalModule service,
                           List<FractalModule> peers, List<Violation> violations) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);
            for (ImportDeclaration imp : cu.getImports()) {
                String importName = imp.getNameAsString();
                if (isAllowed(importName)) continue;

                for (FractalModule peer : peers) {
                    if (peer.getPackageName() != null
                            && importName.startsWith(peer.getPackageName())) {
                        violations.add(new Violation(
                                service.getServiceName(),
                                importName,
                                peer.getServiceName(),
                                javaFile,
                                imp.getBegin().map(p -> p.line).orElse(-1)
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse {}: {}", javaFile, e.getMessage());
        }
    }

    // ── Import allow-list ─────────────────────────────────────────────────────

    private boolean isAllowed(String importName) {
        return importName.startsWith("java.")
                || importName.startsWith("javax.")
                || importName.startsWith("jakarta.")
                || importName.startsWith("org.springframework.")
                || importName.startsWith("org.fractalx.")
                || importName.startsWith("com.github.javaparser.")
                || importName.startsWith("io.micrometer.")
                || importName.startsWith("org.slf4j.")
                || importName.startsWith("com.fasterxml.");
    }
}
