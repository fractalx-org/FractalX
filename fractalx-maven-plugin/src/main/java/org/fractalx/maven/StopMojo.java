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
 * Stops running generated services.
 *
 * <pre>
 *   mvn fractalx:stop                                    # stop all services
 *   mvn fractalx:stop -Dfractalx.service=order-service   # stop one service
 * </pre>
 */
@Mojo(name = "stop")
public class StopMojo extends FractalxBaseMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "fractalx.outputDirectory",
               defaultValue = "${project.basedir}/fractalx-output")
    private File outputDirectory;

    /** Optional: name of a single service to stop. If blank, stops all. */
    @Parameter(property = "fractalx.service", defaultValue = "")
    private String service = "";

    @Override
    public void execute() throws MojoExecutionException {
        initCli();
        long t0 = System.currentTimeMillis();

        printHeader("Stop");

        if (!outputDirectory.exists()) {
            warn("Output directory not found: " + outputDirectory.getAbsolutePath());
            return;
        }

        Path root = outputDirectory.toPath();

        if (!service.isBlank()) {
            stopSingle(root, service.trim(), t0);
        } else {
            stopAll(root, t0);
        }
    }

    // ── Stop all ──────────────────────────────────────────────────────────────

    private void stopAll(Path root, long t0) throws MojoExecutionException {
        Path script = root.resolve("stop-all.sh");
        if (Files.exists(script)) {
            List<String> labels = List.of("stopping all services");
            runWithDashboard(labels, "Stop", t0, () -> exec(script, root));
        } else {
            List<Path> services = discoverServiceDirs(root);
            if (services.isEmpty()) { warn("No services found."); return; }

            List<String> labels = new ArrayList<>();
            services.forEach(d -> labels.add(d.getFileName().toString()));

            runWithDashboardPer(labels, "Stop", t0, (dash, lbls) -> {
                for (int i = 0; i < services.size(); i++) {
                    dash.onStart(lbls.get(i));
                    killByPort(readPort(services.get(i)));
                    dash.onDone(lbls.get(i));
                }
            });
        }
    }

    // ── Stop single ───────────────────────────────────────────────────────────

    private void stopSingle(Path root, String name, long t0) throws MojoExecutionException {
        Path svcDir = root.resolve(name);
        if (!Files.isDirectory(svcDir)) {
            throw new MojoExecutionException("Service not found: " + svcDir.toAbsolutePath());
        }
        runWithDashboard(List.of(name), "Stop", t0, () -> killByPort(readPort(svcDir)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    private void killByPort(int port) throws IOException, InterruptedException {
        if (port < 0) return;
        ProcessBuilder pb;
        if (WINDOWS) {
            // netstat -ano | findstr :<PORT>  → extract PID → taskkill /F /PID <pid>
            pb = new ProcessBuilder("cmd.exe", "/c",
                    "for /f \"tokens=5\" %a in ('netstat -ano ^| findstr /R \":" + port + "\\>\"') do taskkill /F /PID %a 2>nul");
        } else {
            pb = new ProcessBuilder("/bin/sh", "-c",
                    "lsof -ti :" + port + " | xargs kill -9 2>/dev/null || true");
        }
        pb.start().waitFor();
    }

    private int readPort(Path serviceDir) {
        Path yml = serviceDir.resolve("src/main/resources/application.yml");
        if (!Files.exists(yml)) return -1;
        try {
            for (String line : Files.readAllLines(yml)) {
                line = line.trim();
                if (line.startsWith("port:")) return Integer.parseInt(line.substring("port:".length()).trim());
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private void exec(Path script, Path workDir) throws IOException, InterruptedException {
        List<String> cmd = WINDOWS
                ? List.of("cmd.exe", "/c", script.toAbsolutePath().toString())
                : List.of("/bin/sh", script.toAbsolutePath().toString());
        new ProcessBuilder(cmd)
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

    @FunctionalInterface
    private interface DashAction { void run(Dashboard dash, List<String> labels) throws Exception; }

    private void runWithDashboardPer(List<String> labels, String subtitle, long t0, DashAction action)
            throws MojoExecutionException {
        if (ansi) { out.print(ALT_ON); out.flush(); }
        Dashboard dash = new Dashboard(labels, out, ansi, subtitle);
        dash.render();
        try {
            action.run(dash, labels);
        } catch (Exception e) {
            dash.onFail(labels.get(0), e.getMessage());
            if (ansi) {
                try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
                out.print(ALT_OFF); out.flush();
            }
            throw new MojoExecutionException("Stop failed", e);
        }
        dash.finish();
        if (ansi) {
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            out.print(ALT_OFF); out.flush();
        }
        out.println();
        done(System.currentTimeMillis() - t0);
    }

    private void runWithDashboard(List<String> labels, String subtitle, long t0, Action action)
            throws MojoExecutionException {
        if (ansi) { out.print(ALT_ON); out.flush(); }
        Dashboard dash = new Dashboard(labels, out, ansi, subtitle);
        dash.render();
        try {
            labels.forEach(dash::onStart);
            action.run();
            labels.forEach(dash::onDone);
        } catch (Exception e) {
            dash.onFail(labels.get(0), e.getMessage());
            if (ansi) {
                try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
                out.print(ALT_OFF); out.flush();
            }
            throw new MojoExecutionException("Stop failed", e);
        }
        dash.finish();
        if (ansi) {
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            out.print(ALT_OFF); out.flush();
        }
        out.println();
        done(System.currentTimeMillis() - t0);
    }
}
