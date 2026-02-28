package org.fractalx.maven;

import org.fractalx.core.ModuleAnalyzer;
import org.fractalx.core.model.FractalModule;
import org.fractalx.core.verifier.DecompositionVerifier;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Maven goal that statically verifies the output of {@code fractalx:decompose}.
 *
 * <p>Runs after decomposition (no services need to start) and checks:
 * <ul>
 *   <li>All expected service directories, pom.xml, Dockerfiles, and application.yml exist</li>
 *   <li>Each application.yml has the correct port</li>
 *   <li>NetScope wiring is present (@NetScopeClient / @NetworkPublic)</li>
 *   <li>docker-compose.yml references every service, Jaeger, and logger-service</li>
 *   <li>Infrastructure services (registry, gateway, admin, logger) were generated</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   mvn fractalx:verify
 *   mvn fractalx:verify -Dfractalx.verify.failBuild=true
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

    /** Fail the build when any verification check fails. Default: false (warnings only). */
    @Parameter(property = "fractalx.verify.failBuild", defaultValue = "false")
    private boolean failBuild;

    /** Skip verification entirely. */
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

        getLog().info(line('='));
        getLog().info("FractalX Decomposition Verifier");
        getLog().info(line('='));

        // Re-analyze to know which modules were expected
        List<FractalModule> modules;
        try {
            ModuleAnalyzer analyzer = new ModuleAnalyzer();
            modules = analyzer.analyzeProject(sourceDirectory.toPath());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to analyze source modules", e);
        }

        if (modules.isEmpty()) {
            getLog().warn("No @DecomposableModule classes found in source — nothing to verify.");
            return;
        }

        getLog().info("Verifying output for " + modules.size() + " module(s) in:");
        getLog().info("  " + outputDirectory.getAbsolutePath());
        getLog().info("");

        // Run verification
        DecompositionVerifier verifier = new DecompositionVerifier();
        DecompositionVerifier.VerificationReport report =
                verifier.verify(outputPath, modules);

        // Print results grouped by category
        printReport(report);

        // Summary line
        getLog().info(line('='));
        String summary = String.format("Result: %d passed, %d warnings, %d failed",
                report.pass(), report.warn(), report.fail());
        getLog().info(summary);
        getLog().info(line('='));

        if (report.hasFailures() && failBuild) {
            throw new MojoExecutionException(
                    report.fail() + " verification check(s) failed. "
                    + "Fix the issues above or re-run 'mvn fractalx:decompose'.");
        } else if (report.hasFailures()) {
            getLog().warn("Verification found " + report.fail()
                    + " failure(s). Add -Dfractalx.verify.failBuild=true to fail the build.");
        }
    }

    // ── Formatting ─────────────────────────────────────────────────────────────

    private void printReport(DecompositionVerifier.VerificationReport report) {
        String currentCat = null;
        for (DecompositionVerifier.CheckResult r : report.results()) {
            if (!r.category().equals(currentCat)) {
                currentCat = r.category();
                getLog().info("");
                getLog().info("  " + currentCat);
            }
            String icon;
            switch (r.status()) {
                case PASS -> icon = "    [PASS] ";
                case WARN -> icon = "    [WARN] ";
                default   -> icon = "    [FAIL] ";
            }
            String line = icon + r.description();
            if (!r.detail().isBlank()) line += "  (" + r.detail() + ")";

            if (r.status() == DecompositionVerifier.Status.FAIL) {
                getLog().error(line);
            } else if (r.status() == DecompositionVerifier.Status.WARN) {
                getLog().warn(line);
            } else {
                getLog().info(line);
            }
        }
        getLog().info("");
    }

    private static String line(char ch) {
        return String.valueOf(ch).repeat(60);
    }
}
