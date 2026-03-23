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
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the output of {@code fractalx:decompose} at multiple levels.
 *
 * <p>Progress is shown on an alternate-screen Dashboard (identical to
 * {@code fractalx:decompose}). Each verification level is a step; failures
 * show a yellow ⚠ (level had issues but continued) or red ✗ (fatal crash).
 * On completion the main screen shows a Vercel-style findings summary.
 *
 * <pre>
 *   mvn fractalx:verify
 *   mvn fractalx:verify -Dfractalx.verify.compile=true -Dfractalx.verify.failBuild=true
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
            defaultValue = "${project.basedir}/microservices")
    private File outputDirectory;

    @Parameter(property = "fractalx.verify.compile",    defaultValue = "false")
    private boolean compile;

    @Parameter(property = "fractalx.verify.smokeTests", defaultValue = "false")
    private boolean smokeTests;

    @Parameter(property = "fractalx.verify.failBuild",  defaultValue = "false")
    private boolean failBuild;

    @Parameter(property = "fractalx.verify.skip",       defaultValue = "false")
    private boolean skip;

    // ── Collected results (populated during verification, printed after) ──────
    private final List<LevelResult> results = new ArrayList<>();

    private record LevelResult(String name, int pass, int fail,
                                List<String> failures, List<String> warnings) {}

    // =========================================================================
    @Override
    public void execute() throws MojoExecutionException {
        initCli();
        long t0 = System.currentTimeMillis();

        if (skip) { out.println(a(DIM) + "  Skipped." + a(RST)); return; }

        // ── Normal screen: banner + subtitle (same as decompose) ──────────────
        printHeader("Verification Engine");

        if (!outputDirectory.exists()) {
            warn("Output directory not found: " + outputDirectory.getAbsolutePath());
            warn("Run 'mvn fractalx:decompose' first.");
            return;
        }

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

        out.println("  " + a(DIM) + "Output " + a(RST) + outputDirectory.getAbsolutePath());
        out.println();

        // ── Build step labels ─────────────────────────────────────────────────
        List<String> labels = new ArrayList<>();
        labels.add("Structural");
        labels.add("NetScope compatibility");
        labels.add("Static analysis");
        labels.add("Advanced analysis");
        if (compile)    labels.add("Compilation");
        if (smokeTests) labels.add("Smoke tests");

        // ── Enter alternate screen ────────────────────────────────────────────
        if (ansi) { out.print(ALT_ON); out.flush(); }

        Dashboard dash   = new Dashboard(labels, out, ansi, "Verification Engine");
        Path      outPath = outputDirectory.toPath();
        String[]  active = { null };

        dash.render();

        int totalFail = 0;
        try {
            totalFail += runLevel(dash, "Structural",
                    () -> runStructuralChecks(outPath, modules));

            totalFail += runLevel(dash, "NetScope compatibility",
                    () -> runNetScopeChecks(outPath, modules));

            totalFail += runLevel(dash, "Static analysis",
                    () -> runStrictStaticChecks(outPath, modules));

            totalFail += runLevel(dash, "Advanced analysis",
                    () -> runAdvancedAnalysis(outPath, modules));

            if (compile)
                totalFail += runLevel(dash, "Compilation",
                        () -> runCompilationChecks(outPath, modules));

            if (smokeTests)
                runLevel(dash, "Smoke tests",
                        () -> { runSmokeTestGeneration(outPath, modules); return new LevelResult("Smoke tests", 0, 0, List.of(), List.of()); });

        } catch (Exception e) {
            String step = active[0] != null ? active[0] : "verification";
            dash.onFail(step, e.getMessage());
            if (ansi) {
                try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
                out.print(ALT_OFF); out.flush();
            }
            throw new MojoExecutionException("Verification failed at: " + step, e);
        }

        dash.finish();

        if (ansi) {
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            out.print(ALT_OFF); out.flush();
        }

        // ── Summary on main screen ────────────────────────────────────────────
        out.println();
        printSummary(totalFail, System.currentTimeMillis() - t0);

        if (totalFail > 0 && failBuild) {
            throw new MojoExecutionException(
                    totalFail + " verification check(s) failed. Re-run 'mvn fractalx:decompose'.");
        }
    }

    // ── Level runner ──────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface LevelBlock { LevelResult run() throws Exception; }

    private int runLevel(Dashboard dash, String label, LevelBlock block) throws Exception {
        dash.onStart(label);
        LevelResult result = block.run();
        results.add(result);
        if (result.fail() > 0) {
            dash.onWarn(label, result.fail() + " failed");
        } else {
            dash.onDone(label);
        }
        return result.fail();
    }

    // ── Level 1: structural ───────────────────────────────────────────────────

    private LevelResult runStructuralChecks(Path outputPath, List<FractalModule> modules) {
        DecompositionVerifier.VerificationReport report =
                new DecompositionVerifier().verify(outputPath, modules);

        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (DecompositionVerifier.CheckResult r : report.results()) {
            if ("FAIL".equals(r.status().name())) failures.add(r.description()
                    + (r.detail() != null && !r.detail().isBlank() ? "  (" + r.detail() + ")" : ""));
            else if ("WARN".equals(r.status().name())) warnings.add(r.description());
        }
        return new LevelResult("Structural", report.pass(), report.fail(), failures, warnings);
    }

    // ── Level 2: NetScope compatibility ───────────────────────────────────────

    private LevelResult runNetScopeChecks(Path outputPath, List<FractalModule> modules) {
        boolean hasDeps = modules.stream().anyMatch(m -> !m.getDependencies().isEmpty());
        if (!hasDeps) return new LevelResult("NetScope compatibility", 1, 0, List.of(), List.of());

        List<NetScopeCompatibilityChecker.CompatibilityIssue> issues =
                new NetScopeCompatibilityChecker().check(outputPath, modules);

        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (NetScopeCompatibilityChecker.CompatibilityIssue issue : issues) {
            boolean isFail = issue.kind() == NetScopeCompatibilityChecker.IssueKind.MISSING_CLIENT_METHOD
                          || issue.kind() == NetScopeCompatibilityChecker.IssueKind.NO_CLIENT_FILE;
            String msg = "[" + issue.callerService() + " \u2192 " + issue.targetService() + "]  " + issue.detail();
            if (isFail) failures.add(msg); else warnings.add(msg);
        }
        return new LevelResult("NetScope compatibility",
                issues.size() - failures.size(), failures.size(), failures, warnings);
    }

    // ── Level 3: strict static analysis ──────────────────────────────────────

    private LevelResult runStrictStaticChecks(Path outputPath, List<FractalModule> modules) {
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        new PortConflictChecker().check(outputPath, modules)
                .forEach(c -> failures.add("Port conflict: " + c));

        new CrossBoundaryImportChecker().check(outputPath, modules)
                .forEach(v -> failures.add("Cross-boundary import: " + v));

        for (ApiConventionChecker.ApiViolation v : new ApiConventionChecker().check(outputPath, modules)) {
            if (v.isCritical()) failures.add("API: " + v); else warnings.add("API: " + v);
        }
        return new LevelResult("Static analysis", 0, failures.size(), failures, warnings);
    }

    // ── Level 4: advanced analysis ────────────────────────────────────────────

    private LevelResult runAdvancedAnalysis(Path outputPath, List<FractalModule> modules) {
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        ServiceGraphAnalyzer.GraphReport graph = new ServiceGraphAnalyzer().analyse(modules);
        for (ServiceGraphAnalyzer.Finding f : graph.findings()) {
            String msg = f.kind() + " \u2014 " + f.service() + ": " + f.detail();
            if (f.isCritical()) failures.add(msg); else warnings.add(msg);
        }

        for (SqlSchemaValidator.SqlFinding f : new SqlSchemaValidator().validate(outputPath, modules)) {
            if (f.isCritical()) failures.add("SQL: " + f); else warnings.add("SQL: " + f);
        }

        new TransactionBoundaryAnalyzer().analyse(outputPath, modules)
                .forEach(v -> warnings.add("Tx: " + v));

        new SecretLeakScanner().scan(outputPath, modules)
                .forEach(l -> warnings.add("Secret: " + l));

        for (DockerfileValidator.DockerFinding f : new DockerfileValidator().validate(outputPath, modules)) {
            if (f.isCritical()) failures.add("Docker: " + f); else warnings.add("Docker: " + f);
        }

        for (ConfigPropertyChecker.CfgFinding f : new ConfigPropertyChecker().check(outputPath, modules)) {
            if (f.isCritical()) failures.add("Config: " + f); else warnings.add("Config: " + f);
        }

        return new LevelResult("Advanced analysis", 0, failures.size(), failures, warnings);
    }

    // ── Level 5: compilation ──────────────────────────────────────────────────

    private LevelResult runCompilationChecks(Path outputPath, List<FractalModule> modules) {
        List<CompilationVerifier.CompilationResult> compiled =
                new CompilationVerifier().compileAll(outputPath, modules);

        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (CompilationVerifier.CompilationResult r : compiled) {
            if (!r.success()) {
                String detail = r.serviceName() + " \u2014 " + String.join(", ", r.errors());
                failures.add(detail);
            }
        }
        return new LevelResult("Compilation",
                (int) compiled.stream().filter(CompilationVerifier.CompilationResult::success).count(),
                failures.size(), failures, warnings);
    }

    // ── Level 6: smoke test generation ───────────────────────────────────────

    private void runSmokeTestGeneration(Path outputPath, List<FractalModule> modules) {
        new SmokeTestGenerator().generate(outputPath, modules);
    }

    // ── Summary on main screen ────────────────────────────────────────────────

    private void printSummary(int totalFail, long totalMs) {
        boolean anyFailures = results.stream().anyMatch(r -> !r.failures().isEmpty());
        boolean anyWarnings = results.stream().anyMatch(r -> !r.warnings().isEmpty());

        // Failures section
        if (anyFailures) {
            section("Failures");
            for (LevelResult r : results) {
                if (r.failures().isEmpty()) continue;
                out.println("  " + a(BLD) + r.name() + a(RST));
                for (String f : r.failures())
                    out.println("    " + a(RED) + "\u2718" + a(RST) + "  " + f);
                out.println();
            }
        }

        // Warnings section
        if (anyWarnings) {
            section("Warnings");
            for (LevelResult r : results) {
                if (r.warnings().isEmpty()) continue;
                out.println("  " + a(BLD) + r.name() + a(RST));
                for (String w : r.warnings())
                    out.println("    " + a(YLW) + "\u26A0" + a(RST) + "  " + a(DIM) + w + a(RST));
                out.println();
            }
        }

        // Level summary table
        section("Results");
        int pw = results.stream().mapToInt(r -> r.name().length()).max().orElse(10) + 2;
        for (LevelResult r : results) {
            String icon = r.fail() > 0 ? a(YLW) + "\u26A0" + a(RST)
                        : a(GRN) + "\u25AA" + a(RST);
            String counts = a(DIM) + r.pass() + " passed"
                    + (r.fail() > 0 ? "  " + r.fail() + " failed" : "")
                    + (r.warnings().size() > 0 ? "  " + r.warnings().size() + " warnings" : "")
                    + a(RST);
            out.println("  " + icon + "  " + a(BLD) + pad(r.name(), pw) + a(RST) + "  " + counts);
        }
        out.println();

        // Final line
        String t = fmt(totalMs);
        if (totalFail == 0) {
            out.println("  " + a(GRN) + "\u2713" + a(RST) + "  " + a(BLD) + "All checks passed" + a(RST)
                    + "  " + a(DIM) + "[" + t + "]" + a(RST));
        } else {
            out.println("  " + a(YLW) + "\u26A0" + a(RST) + "  " + a(BLD) + totalFail + " check(s) failed" + a(RST)
                    + "  " + a(DIM) + "[" + t + "]" + a(RST));
            out.println();
            out.println("  " + a(DIM) + "Add -Dfractalx.verify.failBuild=true to fail the build on issues." + a(RST));
        }
        out.println();
    }
}
