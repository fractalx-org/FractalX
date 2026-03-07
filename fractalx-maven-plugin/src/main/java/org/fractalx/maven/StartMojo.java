package org.fractalx.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
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
    private String service = "";

    /** Seconds to wait for a service port to open before marking it as timed out. */
    @Parameter(property = "fractalx.start.timeout", defaultValue = "120")
    private int startupTimeout = 120;

    @Override
    public void execute() throws MojoExecutionException {
        initCli();
        long t0 = System.currentTimeMillis();

        printHeader("Start");

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
            runWithDashboard(List.of("starting all services via start-all.sh"), "Start", t0,
                    (dash, labels) -> {
                        dash.onStart(labels.get(0));
                        exec(script, root);
                        dash.onDone(labels.get(0));
                    });
            return;
        }

        List<Path> services = discoverServiceDirs(root);
        if (services.isEmpty()) { warn("No services found in " + root); return; }

        List<String> labels = new ArrayList<>();
        services.forEach(d -> labels.add(d.getFileName().toString()));

        List<SpawnedService> spawned = new ArrayList<>();
        List<StartResult>    results = new ArrayList<>();

        runWithDashboard(labels, "Start", t0, (dash, lbls) -> {
            for (int i = 0; i < services.size(); i++) {
                dash.onStart(lbls.get(i));
                Process p    = spawnService(services.get(i));
                int     port = readPort(services.get(i));
                spawned.add(new SpawnedService(lbls.get(i), services.get(i), p, port));
            }
            for (SpawnedService s : spawned) {
                StartResult r = awaitStartup(s);
                results.add(r);
                if (r.ok()) dash.onDone(s.label);
                else        dash.onWarn(s.label, r.reason());
            }
        });

        // Print failures on main screen after dashboard closes (results already computed)
        for (int i = 0; i < results.size(); i++) {
            if (!results.get(i).ok()) printLogTail(spawned.get(i).svcDir, spawned.get(i).label);
        }
    }

    // ── Start single ──────────────────────────────────────────────────────────

    private void startSingle(Path root, String name, long t0) throws MojoExecutionException {
        Path svcDir = root.resolve(name);
        if (!Files.isDirectory(svcDir)) {
            throw new MojoExecutionException("Service not found: " + svcDir.toAbsolutePath());
        }

        int port = readPort(svcDir);
        final StartResult[] result = { null };

        runWithDashboard(List.of(name), "Start", t0, (dash, labels) -> {
            dash.onStart(name);
            Process p = spawnService(svcDir);
            StartResult r = awaitStartup(new SpawnedService(name, svcDir, p, port));
            result[0] = r;
            if (r.ok()) dash.onDone(name);
            else        dash.onWarn(name, r.reason());
        });

        // Print error log on main screen after dashboard closes (result already computed)
        if (result[0] != null && !result[0].ok()) {
            printLogTail(svcDir, name);
        }
    }

    // ── Await startup (checks process death + port) ───────────────────────────

    private record StartResult(boolean ok, String reason) {}

    /**
     * Waits up to {@code startupTimeout} seconds for the service port to open.
     * Fails immediately if the spawned process exits (crashed).
     */
    private StartResult awaitStartup(SpawnedService s) throws InterruptedException {
        if (s.port <= 0) {
            // No port info: just wait a moment and check if process is still alive
            Thread.sleep(3_000);
            if (!s.process.isAlive()) {
                int code = s.process.exitValue();
                return new StartResult(false, "process exited with code " + code + " — see logs");
            }
            return new StartResult(false, "port unknown — see logs");
        }

        long deadline = System.currentTimeMillis() + startupTimeout * 1000L;
        while (System.currentTimeMillis() < deadline) {
            // Fail fast if process died
            if (!s.process.isAlive()) {
                int code = s.process.exitValue();
                return new StartResult(false, "process exited (code " + code + ") — see logs");
            }
            try (Socket sock = new Socket("localhost", s.port)) {
                return new StartResult(true, null);
            } catch (IOException ignored) {}
            Thread.sleep(500);
        }
        return new StartResult(false, "timed out waiting for :" + s.port);
    }

    // ── Log tail (shown on main screen after dashboard) ───────────────────────

    private void printLogTail(Path svcDir, String name) {
        Path log = logFile(svcDir);
        if (!Files.exists(log)) {
            out.println("  " + a(YLW) + "⚠" + a(RST) + "  " + a(DIM) + name
                    + ": no log file found at " + log + a(RST));
            return;
        }
        try {
            List<String> lines = Files.readAllLines(log);
            int from = Math.max(0, lines.size() - 30);
            out.println();
            out.println("  " + a(YLW) + "⚠" + a(RST) + "  " + a(BLD) + name + " failed to start" + a(RST)
                    + "  " + a(DIM) + log + a(RST));
            out.println();
            for (int i = from; i < lines.size(); i++) {
                out.println("  " + a(DIM) + lines.get(i) + a(RST));
            }
            out.println();
        } catch (IOException e) {
            out.println("  " + a(DIM) + "Could not read " + log + a(RST));
        }
    }

    // ── Core: spawn a service process ─────────────────────────────────────────

    private Process spawnService(Path svcDir) throws IOException {
        Path log = logFile(svcDir);
        Files.createDirectories(log.getParent());

        Path startScript = svcDir.resolve("start.sh");
        if (Files.exists(startScript)) {
            startScript.toFile().setExecutable(true);
            return new ProcessBuilder("/bin/sh", startScript.toAbsolutePath().toString())
                    .directory(svcDir.toFile())
                    .redirectOutput(log.toFile())
                    .redirectError(ProcessBuilder.Redirect.appendTo(log.toFile()))
                    .start();
        }

        return new ProcessBuilder(findMaven(svcDir), "spring-boot:run")
                .directory(svcDir.toFile())
                .redirectOutput(log.toFile())
                .redirectError(ProcessBuilder.Redirect.appendTo(log.toFile()))
                .start();
    }

    // ── Maven path resolution ─────────────────────────────────────────────────

    private static String findMaven(Path svcDir) {
        String home = System.getProperty("maven.home");
        if (home != null) {
            File mvn = new File(home, "bin/mvn");
            if (mvn.canExecute()) return mvn.getAbsolutePath();
        }
        File mvnw = svcDir.resolve("mvnw").toFile();
        if (mvnw.canExecute()) return mvnw.getAbsolutePath();
        File mvnwParent = svcDir.getParent().resolve("mvnw").toFile();
        if (mvnwParent.canExecute()) return mvnwParent.getAbsolutePath();
        return "mvn";
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private static Path logFile(Path svcDir) {
        return svcDir.resolve("logs/startup.log");
    }

    private int readPort(Path serviceDir) {
        Path yml = serviceDir.resolve("src/main/resources/application.yml");
        if (!Files.exists(yml)) return -1;
        try {
            for (String line : Files.readAllLines(yml)) {
                line = line.trim();
                if (line.startsWith("port:"))
                    return Integer.parseInt(line.substring("port:".length()).trim());
            }
        } catch (Exception ignored) {}
        return -1;
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

    private record SpawnedService(String label, Path svcDir, Process process, int port) {}

    // ── Dashboard wrapper ─────────────────────────────────────────────────────

    @FunctionalInterface
    private interface DashAction { void run(Dashboard dash, List<String> labels) throws Exception; }

    private void runWithDashboard(List<String> labels, String subtitle, long t0, DashAction action)
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
            throw new MojoExecutionException("Start failed", e);
        }
        dash.finish();
        if (ansi) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            out.print(ALT_OFF); out.flush();
        }
        out.println();
        section("Start");
        out.println("  " + a(DIM) + "Logs  <service>/logs/startup.log" + a(RST));
        out.println();
        cmd("mvn fractalx:ps                 # check all service status");
        out.println();
        done(System.currentTimeMillis() - t0);
    }
}
