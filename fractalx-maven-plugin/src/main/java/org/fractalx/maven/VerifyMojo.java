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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Verifies the output of {@code fractalx:decompose} at multiple levels.
 *
 * <pre>
 *   mvn fractalx:verify                                       # structural + NetScope (fast)
 *   mvn fractalx:verify -Dfractalx.verify.compile=true        # + compilation
 *   mvn fractalx:verify -Dfractalx.verify.failBuild=true      # fail on any issue
 * </pre>
 */
@Mojo(name = "verify")
public class VerifyMojo extends FractalxBaseMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "fractalx.sourceDirectory",
            defaultValue = "${project.build.sourceDirectory}")
    private File sourceDirectory;

    @Parameter(property = "fractalx.outputDirectory",
            defaultValue = "${project.basedir}/fractalx-output")
    private File outputDirectory;

    @Parameter(property = "fractalx.verify.compile",    defaultValue = "false")
    private boolean compile;

    @Parameter(property = "fractalx.verify.smokeTests", defaultValue = "false")
    private boolean smokeTests;

    @Parameter(property = "fractalx.verify.failBuild",  defaultValue = "false")
    private boolean failBuild;

    @Parameter(property = "fractalx.verify.skip",       defaultValue = "false")
    private boolean skip;

    // =========================================================================
    @Override
    public void execute() throws MojoExecutionException {
        initCli();
        long t0 = System.currentTimeMillis();

        if (skip) { out.println(a(DIM) + "  Skipped." + a(RST)); return; }

        printHeader("Verification Engine");

        if (!outputDirectory.exists()) {
            warn("Output directory not found: " + outputDirectory.getAbsolutePath());
            warn("Run 'mvn fractalx:decompose' first.");
            return;
        }

        Path outputPath = outputDirectory.toPath();

        List<FractalModule> modules;
        try {
            modules = new ModuleAnalyzer().analyzeProject(sourceDirectory.toPath());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to analyse source modules", e);
        }
        if (modules.isEmpty()) {
            warn("No @DecomposableModule classes found — nothing to verify.");
            return;
        }

        // ── Print mode line ───────────────────────────────────────────────────
        String mode = "structural + NetScope + static + advanced"
                + (compile    ? " + compilation"      : "")
                + (smokeTests ? " + smoke-tests"      : "");
        out.println("  " + a(DIM) + "Mode   " + a(RST) + mode);
        out.println("  " + a(DIM) + "Output " + a(RST) + outputDirectory.getAbsolutePath());
        out.println();

        int totalFail = 0;

        totalFail += runLevel(1, "Structural",         () -> runStructuralChecks(outputPath, modules));
        totalFail += runLevel(2, "NetScope compat",    () -> runNetScopeChecks(outputPath, modules));
        totalFail += runLevel(3, "Static analysis",    () -> runStrictStaticChecks(outputPath, modules));
        totalFail += runLevel(4, "Advanced analysis",  () -> runAdvancedAnalysis(outputPath, modules));
        if (compile)    totalFail += runLevel(5, "Compilation",    () -> runCompilationChecks(outputPath, modules));
        if (smokeTests) runLevel(6, "Smoke tests",     () -> { runSmokeTestGeneration(outputPath, modules); return 0; });

        // ── Summary ───────────────────────────────────────────────────────────
        out.println();
        if (totalFail == 0) {
            out.println("  " + a(GRN) + "\u2713" + a(RST) + "  " + a(BLD) + "All checks passed" + a(RST)
                    + "  " + a(DIM) + "[" + fmt(System.currentTimeMillis() - t0) + "]" + a(RST));
        } else {
            out.println("  " + a(RED) + "\u2718" + a(RST) + "  " + a(BLD) + totalFail + " check(s) failed" + a(RST)
                    + "  " + a(DIM) + "[" + fmt(System.currentTimeMillis() - t0) + "]" + a(RST));
        }
        out.println();

        if (totalFail > 0 && failBuild) {
            throw new MojoExecutionException(
                    totalFail + " verification check(s) failed. Re-run 'mvn fractalx:decompose'.");
        } else if (totalFail > 0) {
            out.println("  " + a(DIM) + "Add -Dfractalx.verify.failBuild=true to fail the build on issues." + a(RST));
            out.println();
        }
    }

    // ── Level runner ──────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface CheckBlock { int run() throws Exception; }

    private int runLevel(int n, String name, CheckBlock block) {
        out.println("  " + a(BLD) + a(DIM) + "Level " + n + a(RST)
                + "  " + a(BLD) + name + a(RST));
        out.println();
        try {
            int fails = block.run();
            out.println();
            return fails;
        } catch (Exception e) {
            out.println("  " + a(RED) + "\u2718" + a(RST) + "  " + e.getMessage());
            out.println();
            return 1;
        }
    }

    // ── Level 1: structural ───────────────────────────────────────────────────

    private int runStructuralChecks(Path outputPath, List<FractalModule> modules) {
        DecompositionVerifier.VerificationReport report =
                new DecompositionVerifier().verify(outputPath, modules);

        String currentCat = null;
        for (DecompositionVerifier.CheckResult r : report.results()) {
            if (!r.category().equals(currentCat)) {
                currentCat = r.category();
                out.println("    " + a(DIM) + r.category() + a(RST));
            }
            printCheck(r.status().name(), r.description(), r.detail());
        }
        out.println("    " + a(DIM) + report.pass() + " passed  |  "
                + report.warn() + " warnings  |  " + report.fail() + " failed" + a(RST));
        return report.fail();
    }

    // ── Level 2: NetScope compatibility ───────────────────────────────────────

    private int runNetScopeChecks(Path outputPath, List<FractalModule> modules) {
        boolean hasDeps = modules.stream().anyMatch(m -> !m.getDependencies().isEmpty());
        if (!hasDeps) {
            out.println("  " + a(DIM) + "\u2013  No cross-service dependencies \u2014 skipped." + a(RST));
            return 0;
        }

        List<NetScopeCompatibilityChecker.CompatibilityIssue> issues =
                new NetScopeCompatibilityChecker().check(outputPath, modules);

        if (issues.isEmpty()) {
            out.println("  " + a(GRN) + "\u25AA" + a(RST)
                    + "  All @NetScopeClient interfaces match @NetworkPublic server methods.");
            return 0;
        }

        for (NetScopeCompatibilityChecker.CompatibilityIssue issue : issues) {
            boolean fail = issue.kind() == NetScopeCompatibilityChecker.IssueKind.MISSING_CLIENT_METHOD
                        || issue.kind() == NetScopeCompatibilityChecker.IssueKind.NO_CLIENT_FILE;
            String icon  = fail ? a(RED) + "\u2718" + a(RST) : a(YLW) + "\u26A0" + a(RST);
            out.println("  " + icon + "  [" + issue.callerService() + " \u2192 "
                    + issue.targetService() + "]  " + issue.detail());
        }

        return (int) issues.stream().filter(i ->
                i.kind() == NetScopeCompatibilityChecker.IssueKind.MISSING_CLIENT_METHOD
                || i.kind() == NetScopeCompatibilityChecker.IssueKind.NO_CLIENT_FILE).count();
    }

    // ── Level 3: strict static analysis ──────────────────────────────────────

    private int runStrictStaticChecks(Path outputPath, List<FractalModule> modules) {
        int fails = 0;

        List<PortConflictChecker.Conflict> portConflicts = new PortConflictChecker().check(outputPath, modules);
        if (portConflicts.isEmpty()) {
            pass("No HTTP or gRPC port conflicts detected.");
        } else {
            for (PortConflictChecker.Conflict c : portConflicts) { fail(c.toString()); fails++; }
        }

        List<CrossBoundaryImportChecker.Violation> importViolations =
                new CrossBoundaryImportChecker().check(outputPath, modules);
        if (importViolations.isEmpty()) {
            pass("No cross-boundary package imports detected.");
        } else {
            for (CrossBoundaryImportChecker.Violation v : importViolations) { fail(v.toString()); fails++; }
        }

        List<ApiConventionChecker.ApiViolation> apiViolations =
                new ApiConventionChecker().check(outputPath, modules);
        if (apiViolations.isEmpty()) {
            pass("All REST controllers follow HTTP method conventions.");
        } else {
            for (ApiConventionChecker.ApiViolation v : apiViolations) {
                if (v.isCritical()) { fail(v.toString()); fails++; } else { warn(v.toString()); }
            }
        }

        return fails;
    }

    // ── Level 4: advanced analysis ────────────────────────────────────────────

    private int runAdvancedAnalysis(Path outputPath, List<FractalModule> modules) {
        int fails = 0;

        ServiceGraphAnalyzer.GraphReport graph = new ServiceGraphAnalyzer().analyse(modules);
        if (graph.findings().isEmpty()) {
            pass("Service dependency graph is clean (no cycles, no outliers).");
        } else {
            for (ServiceGraphAnalyzer.Finding f : graph.findings()) {
                if (f.isCritical()) { fail(f.kind() + " \u2014 " + f.service() + ": " + f.detail()); fails++; }
                else { warn(f.kind() + " \u2014 " + f.service() + ": " + f.detail()); }
            }
        }
        out.println("    " + a(DIM) + "fan-out: " + graph.fanOut()
                + "  |  fan-in: " + graph.fanIn() + a(RST));

        List<SqlSchemaValidator.SqlFinding> sqlFindings = new SqlSchemaValidator().validate(outputPath, modules);
        if (sqlFindings.isEmpty()) { pass("All Flyway migration scripts are valid."); }
        else sqlFindings.forEach(f -> { if (f.isCritical()) { fail(f.toString()); } else warn(f.toString()); });

        List<TransactionBoundaryAnalyzer.TransactionViolation> txViolations =
                new TransactionBoundaryAnalyzer().analyse(outputPath, modules);
        if (txViolations.isEmpty()) { pass("No @Transactional + cross-service call combinations."); }
        else txViolations.forEach(v -> warn(v.toString()));

        List<SecretLeakScanner.SecretLeak> leaks = new SecretLeakScanner().scan(outputPath, modules);
        if (leaks.isEmpty()) { pass("No hardcoded secrets detected in generated configs."); }
        else {
            leaks.forEach(l -> warn(l.toString()));
            warn("Rotate these before deploying to any shared/production environment.");
        }

        List<DockerfileValidator.DockerFinding> dockerFindings =
                new DockerfileValidator().validate(outputPath, modules);
        if (dockerFindings.isEmpty()) { pass("All Dockerfiles meet production-readiness standards."); }
        else { for (DockerfileValidator.DockerFinding f : dockerFindings) { if (f.isCritical()) { fail(f.toString()); fails++; } else warn(f.toString()); } }

        List<ConfigPropertyChecker.CfgFinding> cfgFindings =
                new ConfigPropertyChecker().check(outputPath, modules);
        if (cfgFindings.isEmpty()) { pass("All @Value references are covered by application.yml."); }
        else { for (ConfigPropertyChecker.CfgFinding f : cfgFindings) { if (f.isCritical()) { fail(f.toString()); fails++; } else warn(f.toString()); } }

        return fails;
    }

    // ── Level 5: compilation ──────────────────────────────────────────────────

    private int runCompilationChecks(Path outputPath, List<FractalModule> modules) {
        out.println("  " + a(DIM) + "This may take a few minutes..." + a(RST));
        out.println();

        List<CompilationVerifier.CompilationResult> results =
                new CompilationVerifier().compileAll(outputPath, modules);

        int fails = 0;
        for (CompilationVerifier.CompilationResult r : results) {
            if (r.success()) {
                pass(r.serviceName() + " compiled successfully (" + r.durationMs() + "ms)");
            } else {
                fail(r.serviceName() + " \u2014 compilation errors:");
                for (String err : r.errors()) out.println("         " + a(DIM) + err + a(RST));
                fails++;
            }
        }
        out.println("    " + a(DIM) + (results.size() - fails) + " compiled  |  " + fails + " failed" + a(RST));
        return fails;
    }

    // ── Level 6: smoke test generation ───────────────────────────────────────

    private void runSmokeTestGeneration(Path outputPath, List<FractalModule> modules) {
        List<SmokeTestGenerator.GenerationResult> results =
                new SmokeTestGenerator().generate(outputPath, modules);
        for (SmokeTestGenerator.GenerationResult r : results) {
            if (r.success()) pass(r.serviceName() + " \u2014 FractalXSmokeTest.java written");
            else             warn(r.serviceName() + " \u2014 " + r.detail());
        }
        out.println();
        out.println("  " + a(DIM) + "Run with: cd <service-dir> && mvn test" + a(RST));
    }

    // ── Row helpers ───────────────────────────────────────────────────────────

    private void printCheck(String status, String description, String detail) {
        String line = "    " + description + (detail != null && !detail.isBlank() ? "  (" + detail + ")" : "");
        switch (status) {
            case "PASS" -> pass(description + (detail != null && !detail.isBlank() ? "  (" + detail + ")" : ""));
            case "WARN" -> warn(description + (detail != null && !detail.isBlank() ? "  (" + detail + ")" : ""));
            default     -> fail(description + (detail != null && !detail.isBlank() ? "  (" + detail + ")" : ""));
        }
    }

    private void pass(String msg) {
        out.println("    " + a(GRN) + "\u25AA" + a(RST) + "  " + msg);
    }

    private void fail(String msg) {
        out.println("    " + a(RED) + "\u2718" + a(RST) + "  " + a(RED) + msg + a(RST));
    }
}
