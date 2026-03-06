package org.fractalx.maven;

import org.fractalx.core.FractalxVersion;
import org.fractalx.core.ModuleAnalyzer;
import org.fractalx.core.model.FractalModule;
import org.fractalx.core.generator.ServiceGenerator;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Decomposes a modular monolith into microservices.
 *
 * <p>Progress is shown on an <em>alternate screen buffer</em> so the dashboard
 * is fully in-place — nothing scrolls during generation.  When generation
 * completes (or fails) the plugin returns to the normal screen and prints a
 * concise Vercel-style summary.
 */
@Mojo(name = "decompose", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class DecomposeMojo extends FractalxBaseMojo {

    private static final int GATEWAY_PORT  = 9999;
    private static final int ADMIN_PORT    = 8080;
    private static final int REGISTRY_PORT = 8761;

    // ── Parameters ───────────────────────────────────────────────────────────
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "fractalx.sourceDirectory",
               defaultValue = "${project.build.sourceDirectory}")
    private File sourceDirectory;

    @Parameter(property = "fractalx.outputDirectory",
               defaultValue = "${project.basedir}/fractalx-output")
    private File outputDirectory;

    @Parameter(property = "fractalx.skip",    defaultValue = "false")
    private boolean skip;

    @Parameter(property = "fractalx.generate", defaultValue = "true")
    private boolean generate;

    // =========================================================================
    @Override
    public void execute() throws MojoExecutionException {
        initCli();
        long t0 = System.currentTimeMillis();

        if (skip) { out.println(a(DIM) + "  Skipped." + a(RST)); return; }

        printHeader("Decomposition Engine");

        try {
            Path sourcePath = sourceDirectory.toPath();

            out.println(a(DIM) + "  Inspecting " + a(RST) + sourceDirectory.getAbsolutePath());
            out.println();

            List<FractalModule> modules = new ModuleAnalyzer().analyzeProject(sourcePath);

            if (modules.isEmpty()) {
                warn("No @DecomposableModule classes found.");
                return;
            }

            for (FractalModule m : modules) {
                String deps = m.getDependencies().isEmpty() ? ""
                        : a(DIM) + "  \u2192  " + a(RST) + String.join(", ", m.getDependencies());
                out.println("  " + a(GRN) + "\u25AA" + a(RST)
                        + "  " + a(BLD) + m.getServiceName() + a(RST)
                        + a(DIM) + "  :" + m.getPort() + a(RST) + deps);
            }
            out.println();

            if (generate) {
                runGeneration(sourcePath, modules, t0);
            } else {
                out.println(a(DIM) + "  Generation skipped (fractalx.generate=false)." + a(RST));
            }

        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Decomposition failed", e);
        }
    }

    // =========================================================================
    // Generation — dashboard lives on the alternate screen
    // =========================================================================

    private void runGeneration(Path srcPath, List<FractalModule> modules, long t0) throws Exception {

        List<String> labels = new ArrayList<>();
        labels.add("fractalx-registry");
        modules.forEach(m -> labels.add(m.getServiceName()));
        if (modules.size() > 1) labels.add("fractalx-gateway");
        labels.add("fractalx-admin");
        labels.add("fractalx-saga-orchestrator");
        labels.add("docker-compose + scripts");

        if (ansi) { out.print(ALT_ON); out.flush(); }

        Dashboard        dash   = new Dashboard(labels, out, ansi, "Decomposition Engine");
        ServiceGenerator gen    = new ServiceGenerator(srcPath, outputDirectory.toPath());
        String[]         active = { null };

        gen.setProgressCallbacks(
            label -> { active[0] = label; dash.onStart(label); },
            label -> { dash.onDone(label); active[0] = null;   }
        );

        dash.render();

        try {
            gen.generateServices(modules);
        } catch (Exception e) {
            String step = active[0] != null ? active[0] : "generation";
            dash.onFail(step, e.getMessage());
            if (ansi) {
                try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
                out.print(ALT_OFF); out.flush();
            }
            throw new MojoExecutionException("Failed at: " + step, e);
        }

        dash.finish();

        if (ansi) {
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            out.print(ALT_OFF); out.flush();
        }

        out.println();
        printSummary(modules, outputDirectory.toPath(), System.currentTimeMillis() - t0);
    }

    // =========================================================================
    // Summary on the normal screen
    // =========================================================================

    private void printSummary(List<FractalModule> modules, Path outDir, long totalMs) {
        int pw = Math.max(22, modules.stream()
                .mapToInt(m -> m.getServiceName().length()).max().orElse(0) + 2);

        section("Microservices");
        for (FractalModule m : modules)
            link(pw, m.getServiceName(), "http://localhost:" + m.getPort());
        out.println();

        section("Infrastructure");
        if (modules.size() > 1) link(pw, "fractalx-gateway",  "http://localhost:" + GATEWAY_PORT);
        link(pw, "fractalx-admin",    "http://localhost:" + ADMIN_PORT);
        link(pw, "fractalx-registry", "http://localhost:" + REGISTRY_PORT);
        out.println();

        section("Get started");
        cmd("cd " + outDir.toAbsolutePath());
        cmd("./start-all.sh");
        cmd("docker-compose up -d");
        out.println();
        out.println("  " + a(DIM) + "Docs  \u2192  " + a(RST)
                + outDir.toAbsolutePath() + "/README.md");
        out.println();

        done(totalMs);
    }
}
