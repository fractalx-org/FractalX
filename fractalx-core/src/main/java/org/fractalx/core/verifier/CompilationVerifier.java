package org.fractalx.core.verifier;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Compiles each generated service directory using {@code mvn compile} to verify that
 * all generated classes, imports, and cross-service references resolve correctly.
 *
 * <p>This catches problems that static file-existence checks cannot:
 * <ul>
 *   <li>Missing imports or wrong package declarations in generated code</li>
 *   <li>Generated classes that reference types not on the classpath</li>
 *   <li>Method signature mismatches in generated wrappers</li>
 *   <li>Annotation processor errors</li>
 * </ul>
 */
public class CompilationVerifier {

    private static final Logger log = LoggerFactory.getLogger(CompilationVerifier.class);
    private static final int TIMEOUT_MINUTES = 5;

    private static final List<String> INFRASTRUCTURE_SERVICES = List.of(
            "fractalx-registry", "fractalx-gateway", "admin-service",
            "logger-service", "fractalx-saga-orchestrator"
    );

    // ── Result model ──────────────────────────────────────────────────────────

    public record CompilationResult(
            String serviceName,
            boolean success,
            List<String> errors,
            long durationMs
    ) {
        public String summary() {
            return success
                    ? "[PASS] " + serviceName + " compiled successfully (" + durationMs + "ms)"
                    : "[FAIL] " + serviceName + " failed to compile";
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Compiles all generated services (infrastructure + user modules).
     *
     * @param outputDir the root generated-services directory
     * @param modules   modules detected by ModuleAnalyzer (determines user services)
     * @return one CompilationResult per service directory that was found
     */
    public List<CompilationResult> compileAll(Path outputDir, List<FractalModule> modules) {
        List<CompilationResult> results = new ArrayList<>();

        // Infrastructure services first (registry must be compiled before others may depend on it)
        for (String svc : INFRASTRUCTURE_SERVICES) {
            Path svcDir = outputDir.resolve(svc);
            if (Files.isDirectory(svcDir) && Files.exists(svcDir.resolve("pom.xml"))) {
                results.add(compile(svcDir, svc));
            }
        }

        // User-decomposed services
        for (FractalModule module : modules) {
            Path svcDir = outputDir.resolve(module.getServiceName());
            if (Files.isDirectory(svcDir) && Files.exists(svcDir.resolve("pom.xml"))) {
                results.add(compile(svcDir, module.getServiceName()));
            }
        }

        return results;
    }

    // ── Single-service compilation ─────────────────────────────────────────────

    private CompilationResult compile(Path svcDir, String name) {
        long start = System.currentTimeMillis();
        log.debug("Compiling {} ...", name);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    mvnExecutable(), "compile",
                    "--no-transfer-progress",
                    "-q"
            );
            pb.directory(svcDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String rawOutput = new String(process.getInputStream().readAllBytes());

            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                return new CompilationResult(name, false,
                        List.of("Compilation timed out after " + TIMEOUT_MINUTES + " minutes"),
                        System.currentTimeMillis() - start);
            }

            int exitCode = process.exitValue();
            long duration = System.currentTimeMillis() - start;

            if (exitCode == 0) {
                return new CompilationResult(name, true, List.of(), duration);
            }

            List<String> errors = extractErrors(rawOutput);
            return new CompilationResult(name, false, errors, duration);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CompilationResult(name, false,
                    List.of("Compilation interrupted"), System.currentTimeMillis() - start);
        } catch (IOException e) {
            return new CompilationResult(name, false,
                    List.of("Could not start Maven: " + e.getMessage()),
                    System.currentTimeMillis() - start);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Extracts the most useful error lines from Maven output (max 15). */
    private List<String> extractErrors(String output) {
        List<String> errors = new ArrayList<>();
        for (String line : output.lines().toList()) {
            String t = line.trim();
            if (t.startsWith("[ERROR]") || t.contains("error:") || t.contains("cannot find symbol")
                    || t.contains("package ") && t.contains("does not exist")
                    || t.contains("cannot access")) {
                errors.add(t);
                if (errors.size() >= 15) {
                    errors.add("... (truncated, check full Maven output)");
                    break;
                }
            }
        }
        if (errors.isEmpty() && !output.isBlank()) {
            // Fallback: last 10 lines of output
            List<String> lines = output.lines().toList();
            int from = Math.max(0, lines.size() - 10);
            errors.addAll(lines.subList(from, lines.size()));
        }
        return errors;
    }

    /** Returns "mvn" or "mvn.cmd" depending on OS. */
    private String mvnExecutable() {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "mvn.cmd" : "mvn";
    }
}
