package org.fractalx.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts generated services using the generated start scripts.
 *
 * <pre>
 *   mvn fractalx:start                                    # start all services
 *   mvn fractalx:start -Dfractalx.service=order-service   # start one service
 * </pre>
 */
@Mojo(name = "start")
public class StartMojo extends FractalxBaseMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "fractalx.outputDirectory",
               defaultValue = "${project.basedir}/fractalx-output")
    private File outputDirectory;

    /** Optional: name of a single service to start. If blank, starts all. */
    @Parameter(property = "fractalx.service", defaultValue = "")
    private String service;

    @Override
    public void execute() throws MojoExecutionException {
        initCli();
        long t0 = System.currentTimeMillis();

        printHeader("Run");

        if (!outputDirectory.exists()) {
            warn("Output directory not found: " + outputDirectory.getAbsolutePath());
            warn("Run 'mvn fractalx:decompose' first.");
            return;
        }

        Path root = outputDirectory.toPath();

        if (!service.isBlank()) {
            startSingle(root, service.trim(), t0);
        } else {
            startAll(root, t0);
        }
    }

    // ── Start all ─────────────────────────────────────────────────────────────

    private void startAll(Path root, long t0) throws MojoExecutionException {
        Path script = root.resolve("start-all.sh");
        if (Files.exists(script)) {
            List<String> labels = List.of("starting all services via start-all.sh");
            runWithDashboard(labels, "Run", t0, () -> exec(script, root));
        } else {
            // Fall back: discover services and start each one
            List<Path> services = discoverServiceDirs(root);
            if (services.isEmpty()) { warn("No services found in " + root); return; }

            List<String> labels = new ArrayList<>();
            services.forEach(d -> labels.add(d.getFileName().toString()));

            runWithDashboard(labels, "Run", t0, () -> {
                for (Path svc : services) startService(svc);
            });
        }
    }

    // ── Start single ──────────────────────────────────────────────────────────

    private void startSingle(Path root, String name, long t0) throws MojoExecutionException {
        Path svcDir = root.resolve(name);
        if (!Files.isDirectory(svcDir)) {
            throw new MojoExecutionException("Service not found: " + svcDir.toAbsolutePath());
        }
        runWithDashboard(List.of(name), "Run", t0, () -> startService(svcDir));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void startService(Path svcDir) throws IOException, InterruptedException {
        Path script = svcDir.resolve("start.sh");
        if (Files.exists(script)) {
            exec(script, svcDir);
        } else {
            // Run in background via mvn spring-boot:run
            new ProcessBuilder("mvn", "spring-boot:run")
                    .directory(svcDir.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            // Brief wait to let the process spawn
            Thread.sleep(500);
        }
    }

    private void exec(Path script, Path workDir) throws IOException, InterruptedException {
        new ProcessBuilder("/bin/sh", script.toAbsolutePath().toString())
                .directory(workDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor();
    }

    private List<Path> discoverServiceDirs(Path root) throws MojoExecutionException {
        List<Path> result = new ArrayList<>();
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                  .filter(d -> Files.exists(d.resolve("pom.xml")))
                  .sorted()
                  .forEach(result::add);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to list services", e);
        }
        return result;
    }

    // ── Dashboard wrapper ─────────────────────────────────────────────────────

    @FunctionalInterface
    private interface Action { void run() throws Exception; }

    private void runWithDashboard(List<String> labels, String subtitle, long t0, Action action)
            throws MojoExecutionException {
        if (ansi) { out.print(ALT_ON); out.flush(); }
        Dashboard dash = new Dashboard(labels, out, ansi, subtitle);
        dash.render();
        try {
            String first = labels.get(0);
            dash.onStart(first);
            action.run();
            labels.forEach(dash::onDone);
        } catch (Exception e) {
            dash.onFail(labels.get(0), e.getMessage());
            if (ansi) {
                try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
                out.print(ALT_OFF); out.flush();
            }
            throw new MojoExecutionException("Run failed", e);
        }
        dash.finish();
        if (ansi) {
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            out.print(ALT_OFF); out.flush();
        }
        out.println();
        section("Running");
        out.println("  " + a(DIM) + "Use 'mvn fractalx:ps' to check service status." + a(RST));
        out.println();
        done(System.currentTimeMillis() - t0);
    }
}
