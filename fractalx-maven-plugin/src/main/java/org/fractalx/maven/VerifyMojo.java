package org.fractalx.maven;

import org.fractalx.core.ModuleAnalyzer;
import org.fractalx.core.model.FractalModule;
import org.fractalx.core.verifier.ApiConventionChecker;
import org.fractalx.core.verifier.CompilationVerifier;
import org.fractalx.core.verifier.ConfigPropertyChecker;
import org.fractalx.core.verifier.CrossBoundaryImportChecker;
import org.fractalx.core.verifier.DecompositionVerifier;
import org.fractalx.core.verifier.DockerfileValidator;
import org.fractalx.core.verifier.NetScopeCompatibilityChecker;
import org.fractalx.core.verifier.PortConflictChecker;
import org.fractalx.core.verifier.SecretLeakScanner;
import org.fractalx.core.verifier.ServiceGraphAnalyzer;
import org.fractalx.core.verifier.SmokeTestGenerator;
import org.fractalx.core.verifier.SqlSchemaValidator;
import org.fractalx.core.verifier.TransactionBoundaryAnalyzer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Maven goal that verifies the output of {@code fractalx:decompose} at three levels:
 *
 * <ol>
 *   <li><b>Structural</b> (always) — all expected directories, pom.xml, Dockerfiles,
 *       application.yml, NetScope annotations, docker-compose.yml</li>
 *   <li><b>NetScope compatibility</b> (always) — JavaParser checks that every
 *       {@code @NetScopeClient} interface method matches a {@code @NetworkPublic}
 *       server method in the target service (same name + parameter count)</li>
 *   <li><b>Compilation</b> (opt-in) — runs {@code mvn compile} on each generated
 *       service to catch missing imports, type errors, and classpath issues</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 *   # Structural + NetScope checks only (fast, ~1s)
 *   mvn fractalx:verify
 *
 *   # Full verification including compilation (slow, compiles every service)
 *   mvn fractalx:verify -Dfractalx.verify.compile=true
 *
 *   # Fail the build on any issue
 *   mvn fractalx:verify -Dfractalx.verify.compile=true -Dfractalx.verify.failBuild=true
 *
 *   # One-shot: decompose then full verify
 *   mvn fractalx:decompose fractalx:verify -Dfractalx.verify.compile=true -Dfractalx.verify.failBuild=true
 * </pre>
 */
@Mojo(name = "verify")
public class VerifyMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "fractalx.sourceDirectory",
            defaultValue = "${project.build.sourceDirectory}")
    private File sourceDirectory;

    @Parameter(property = "fractalx.outputDirectory",
            defaultValue = "${project.build.directory}/generated-services")
    private File outputDirectory;

    /**
     * When true, compiles every generated service with {@code mvn compile}
     * to catch type errors, missing imports, and classpath issues.
     * Slower — adds ~30-120s depending on how many services were generated.
     */
    @Parameter(property = "fractalx.verify.compile", defaultValue = "false")
    private boolean compile;

    /**
     * When true, generates a {@code FractalXSmokeTest.java} in every service
     * that loads the Spring application context with {@code webEnvironment=NONE}.
     * Run {@code mvn test} in each service to execute them.
     */
    @Parameter(property = "fractalx.verify.smokeTests", defaultValue = "false")
    private boolean smokeTests;

    /** Fail the Maven build when any check fails. Default: false (print failures, continue). */
    @Parameter(property = "fractalx.verify.failBuild", defaultValue = "false")
    private boolean failBuild;

    @Parameter(property = "fractalx.verify.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("FractalX verification skipped");
            return;
        }

        Path outputPath = outputDirectory.toPath();
        if (!outputDirectory.exists()) {
            getLog().warn("Output directory does not exist: " + outputDirectory.getAbsolutePath());
            getLog().warn("Run 'mvn fractalx:decompose' first.");
            return;
        }

        getLog().info(banner('='));
        getLog().info("FractalX Decomposition Verifier");
        getLog().info(banner('='));
        getLog().info("Output: " + outputDirectory.getAbsolutePath());
        getLog().info("Mode:   structural + NetScope + port/boundary/API/Docker/cfg + advanced"
                + (compile ? " + compilation" : "")
                + (smokeTests ? " + smoke-test generation" : "")
                + (!compile ? "  (add -Dfractalx.verify.compile=true for full compilation)" : ""));
        getLog().info("");

        // Re-analyse source to know which modules were expected
        List<FractalModule> modules;
        try {
            modules = new ModuleAnalyzer().analyzeProject(sourceDirectory.toPath());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to analyse source modules", e);
        }
        if (modules.isEmpty()) {
            getLog().warn("No @DecomposableModule classes found — nothing to verify.");
            return;
        }

        int totalFail = 0;

        // ── Level 1: Structural ───────────────────────────────────────────────
        totalFail += runStructuralChecks(outputPath, modules);

        // ── Level 2: NetScope interface compatibility ─────────────────────────
        totalFail += runNetScopeChecks(outputPath, modules);

        // ── Level 3: Strict static analysis ──────────────────────────────────
        totalFail += runStrictStaticChecks(outputPath, modules);

        // ── Level 4: Advanced analysis ────────────────────────────────────────
        totalFail += runAdvancedAnalysis(outputPath, modules);

        // ── Level 5: Compilation (opt-in) ─────────────────────────────────────
        if (compile) {
            totalFail += runCompilationChecks(outputPath, modules);
        }

        // ── Level 6: Smoke test generation (opt-in) ───────────────────────────
        if (smokeTests) {
            runSmokeTestGeneration(outputPath, modules);
        }

        // ── Summary ───────────────────────────────────────────────────────────
        getLog().info("");
        getLog().info(banner('='));
        if (totalFail == 0) {
            getLog().info("All checks passed.");
        } else {
            getLog().info(totalFail + " check(s) failed.");
        }
        getLog().info(banner('='));

        if (totalFail > 0 && failBuild) {
            throw new MojoExecutionException(
                    totalFail + " verification check(s) failed. "
                    + "Re-run 'mvn fractalx:decompose' and check the issues above.");
        } else if (totalFail > 0) {
            getLog().warn("Add -Dfractalx.verify.failBuild=true to fail the build on issues.");
        }
    }

    // ── Level 1: structural ───────────────────────────────────────────────────

    private int runStructuralChecks(Path outputPath, List<FractalModule> modules) {
        getLog().info("[ Level 1 ] Structural checks");
        getLog().info(banner('-'));

        DecompositionVerifier.VerificationReport report =
                new DecompositionVerifier().verify(outputPath, modules);

        String currentCat = null;
        for (DecompositionVerifier.CheckResult r : report.results()) {
            if (!r.category().equals(currentCat)) {
                currentCat = r.category();
                getLog().info("");
                getLog().info("  " + currentCat);
            }
            printCheck(r.status().name(), r.description(), r.detail());
        }

        getLog().info("");
        getLog().info("  " + report.pass() + " passed  |  "
                + report.warn() + " warnings  |  " + report.fail() + " failed");
        return report.fail();
    }

    // ── Level 2: NetScope compatibility ───────────────────────────────────────

    private int runNetScopeChecks(Path outputPath, List<FractalModule> modules) {
        getLog().info("");
        getLog().info("[ Level 2 ] NetScope interface compatibility");
        getLog().info(banner('-'));

        boolean hasDeps = modules.stream().anyMatch(m -> !m.getDependencies().isEmpty());
        if (!hasDeps) {
            getLog().info("  No cross-service dependencies — skipping.");
            return 0;
        }

        List<NetScopeCompatibilityChecker.CompatibilityIssue> issues =
                new NetScopeCompatibilityChecker().check(outputPath, modules);

        if (issues.isEmpty()) {
            getLog().info("  [PASS] All @NetScopeClient interfaces match @NetworkPublic server methods.");
            return 0;
        }

        for (NetScopeCompatibilityChecker.CompatibilityIssue issue : issues) {
            String label = switch (issue.kind()) {
                case MISSING_CLIENT_METHOD -> "[FAIL] Client stub missing server method";
                case EXTRA_SERVER_METHOD   -> "[WARN] Server exposes method not in client";
                case NO_CLIENT_FILE        -> "[FAIL] Client interface not generated";
                case NO_SERVER_FILE        -> "[WARN] Server class not found";
                case PARSE_ERROR           -> "[WARN] Could not parse file";
            };
            boolean isFail = issue.kind() == NetScopeCompatibilityChecker.IssueKind.MISSING_CLIENT_METHOD
                    || issue.kind() == NetScopeCompatibilityChecker.IssueKind.NO_CLIENT_FILE;
            String line = "  " + label + " [" + issue.callerService() + " → "
                    + issue.targetService() + "]  " + issue.detail();
            if (isFail) getLog().error(line); else getLog().warn(line);
        }

        long fails = issues.stream().filter(i ->
                i.kind() == NetScopeCompatibilityChecker.IssueKind.MISSING_CLIENT_METHOD
                || i.kind() == NetScopeCompatibilityChecker.IssueKind.NO_CLIENT_FILE).count();
        return (int) fails;
    }

    // ── Level 3: strict static analysis ──────────────────────────────────────

    private int runStrictStaticChecks(Path outputPath, List<FractalModule> modules) {
        getLog().info("");
        getLog().info("[ Level 3 ] Strict static analysis");
        getLog().info(banner('-'));

        int fails = 0;

        // Port conflicts
        List<PortConflictChecker.Conflict> portConflicts =
                new PortConflictChecker().check(outputPath, modules);
        if (portConflicts.isEmpty()) {
            getLog().info("  [PASS] No HTTP or gRPC port conflicts detected.");
        } else {
            for (PortConflictChecker.Conflict c : portConflicts) {
                getLog().error("  " + c);
                fails++;
            }
        }

        // Cross-boundary imports
        List<CrossBoundaryImportChecker.Violation> importViolations =
                new CrossBoundaryImportChecker().check(outputPath, modules);
        if (importViolations.isEmpty()) {
            getLog().info("  [PASS] No cross-boundary package imports detected.");
        } else {
            for (CrossBoundaryImportChecker.Violation v : importViolations) {
                getLog().error("  " + v);
                fails++;
            }
        }

        // REST API convention checks
        List<ApiConventionChecker.ApiViolation> apiViolations =
                new ApiConventionChecker().check(outputPath, modules);
        if (apiViolations.isEmpty()) {
            getLog().info("  [PASS] All REST controllers follow HTTP method conventions.");
        } else {
            for (ApiConventionChecker.ApiViolation v : apiViolations) {
                if (v.isCritical()) { getLog().error("  " + v); fails++; }
                else                  getLog().warn("  " + v);
            }
        }

        return fails;
    }

    // ── Level 4: advanced analysis ────────────────────────────────────────────

    private int runAdvancedAnalysis(Path outputPath, List<FractalModule> modules) {
        getLog().info("");
        getLog().info("[ Level 4 ] Advanced analysis");
        getLog().info(banner('-'));
        int fails = 0;

        // Service dependency graph (cycles, fan-in/out, orphans)
        ServiceGraphAnalyzer.GraphReport graph =
                new ServiceGraphAnalyzer().analyse(modules);
        if (graph.findings().isEmpty()) {
            getLog().info("  [PASS] Service dependency graph is clean (no cycles, no outliers).");
        } else {
            for (ServiceGraphAnalyzer.Finding f : graph.findings()) {
                String line = "  [" + (f.isCritical() ? "FAIL" : "WARN") + "] "
                        + f.kind() + " — " + f.service() + ": " + f.detail();
                if (f.isCritical()) { getLog().error(line); fails++; }
                else                  getLog().warn(line);
            }
        }
        // Print coupling metrics
        getLog().info("  Coupling metrics:  fan-out (calls peers): "
                + graph.fanOut() + "  |  fan-in (called by peers): " + graph.fanIn());

        // SQL schema validation
        List<SqlSchemaValidator.SqlFinding> sqlFindings =
                new SqlSchemaValidator().validate(outputPath, modules);
        if (sqlFindings.isEmpty()) {
            getLog().info("  [PASS] All Flyway migration scripts are valid.");
        } else {
            for (SqlSchemaValidator.SqlFinding f : sqlFindings) {
                if (f.isCritical()) { getLog().error("  " + f); fails++; }
                else                  getLog().warn("  " + f);
            }
        }

        // Transaction boundary analysis
        List<TransactionBoundaryAnalyzer.TransactionViolation> txViolations =
                new TransactionBoundaryAnalyzer().analyse(outputPath, modules);
        if (txViolations.isEmpty()) {
            getLog().info("  [PASS] No @Transactional + cross-service call combinations detected.");
        } else {
            txViolations.forEach(v -> getLog().warn("  " + v));
        }

        // Secret leak scan
        List<SecretLeakScanner.SecretLeak> leaks =
                new SecretLeakScanner().scan(outputPath, modules);
        if (leaks.isEmpty()) {
            getLog().info("  [PASS] No hardcoded secrets detected in generated configs.");
        } else {
            leaks.forEach(l -> getLog().warn("  " + l));
            getLog().warn("  Rotate these before deploying to any shared/production environment.");
        }

        // Dockerfile quality checks
        List<DockerfileValidator.DockerFinding> dockerFindings =
                new DockerfileValidator().validate(outputPath, modules);
        if (dockerFindings.isEmpty()) {
            getLog().info("  [PASS] All Dockerfiles meet production-readiness standards.");
        } else {
            for (DockerfileValidator.DockerFinding f : dockerFindings) {
                if (f.isCritical()) { getLog().error("  " + f); fails++; }
                else                  getLog().warn("  " + f);
            }
        }

        // @Value → application.yml property coverage
        List<ConfigPropertyChecker.CfgFinding> cfgFindings =
                new ConfigPropertyChecker().check(outputPath, modules);
        if (cfgFindings.isEmpty()) {
            getLog().info("  [PASS] All @Value references are covered by application.yml.");
        } else {
            for (ConfigPropertyChecker.CfgFinding f : cfgFindings) {
                if (f.isCritical()) { getLog().error("  " + f); fails++; }
                else                  getLog().warn("  " + f);
            }
        }

        return fails;
    }

    // ── Level 5: compilation ──────────────────────────────────────────────────

    private int runCompilationChecks(Path outputPath, List<FractalModule> modules) {
        getLog().info("");
        getLog().info("[ Level 3 ] Compilation (mvn compile per service)");
        getLog().info(banner('-'));
        getLog().info("  This may take a few minutes...");
        getLog().info("");

        List<CompilationVerifier.CompilationResult> results =
                new CompilationVerifier().compileAll(outputPath, modules);

        int fails = 0;
        for (CompilationVerifier.CompilationResult r : results) {
            if (r.success()) {
                getLog().info("  [PASS] " + r.serviceName()
                        + " compiled successfully (" + r.durationMs() + "ms)");
            } else {
                getLog().error("  [FAIL] " + r.serviceName() + " — compilation errors:");
                for (String err : r.errors()) {
                    getLog().error("         " + err);
                }
                fails++;
            }
        }

        getLog().info("");
        getLog().info("  " + (results.size() - fails) + " compiled  |  " + fails + " failed");
        return fails;
    }

    // ── Level 5: smoke test generation ───────────────────────────────────────

    private void runSmokeTestGeneration(Path outputPath, List<FractalModule> modules) {
        getLog().info("");
        getLog().info("[ Level 5 ] Smoke test generation");
        getLog().info(banner('-'));

        List<SmokeTestGenerator.GenerationResult> results =
                new SmokeTestGenerator().generate(outputPath, modules);

        for (SmokeTestGenerator.GenerationResult r : results) {
            if (r.success()) {
                getLog().info("  [PASS] " + r.serviceName()
                        + " — FractalXSmokeTest.java written");
            } else {
                getLog().warn("  [WARN] " + r.serviceName() + " — " + r.detail());
            }
        }
        getLog().info("");
        getLog().info("  Run smoke tests with: cd <service-dir> && mvn test");
        getLog().info("  Spring context must load cleanly for each test to pass.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void printCheck(String status, String description, String detail) {
        String icon = switch (status) {
            case "PASS" -> "    [PASS] ";
            case "WARN" -> "    [WARN] ";
            default     -> "    [FAIL] ";
        };
        String line = icon + description;
        if (detail != null && !detail.isBlank()) line += "  (" + detail + ")";
        if ("FAIL".equals(status)) getLog().error(line);
        else if ("WARN".equals(status)) getLog().warn(line);
        else getLog().info(line);
    }

    private static String banner(char ch) {
        return String.valueOf(ch).repeat(60);
    }
}
