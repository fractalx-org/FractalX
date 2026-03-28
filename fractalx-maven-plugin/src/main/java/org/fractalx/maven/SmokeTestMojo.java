package org.fractalx.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Builds each generated service with {@code mvn package -DskipTests}, starts it, confirms
 * that Spring Boot's HTTP port opened and that the Actuator health endpoint responded, then
 * shuts it down. Services are tested one at a time so port conflicts are impossible.
 *
 * <p>A single Dashboard TUI screen shows interleaved {@code · build} and
 * {@code · start + health} rows, one pair per service, processed sequentially.
 *
 * <p>A consolidated log is written to {@code <outputDirectory>/smoketest.log}
 * aggregating the build and start output of every service in one file.
 *
 * <pre>
 *   mvn fractalx:smoke-test                                             # build + start + health all
 *   mvn fractalx:smoke-test -Dfractalx.smoketest.build=false            # skip build, start-only
 *   mvn fractalx:smoke-test -Dfractalx.smoketest.service=order-service  # single service
 *   mvn fractalx:smoke-test -Dfractalx.smoketest.failBuild=true         # fail Maven if any service fails
 * </pre>
 */
@Mojo(name = "smoke-test", requiresProject = true, threadSafe = false)
public class SmokeTestMojo extends FractalxBaseMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Directory that contains the generated microservices. */
    @Parameter(property = "fractalx.outputDirectory",
               defaultValue = "${project.basedir}/microservices")
    private File outputDirectory;

    /**
     * When {@code true} (default), runs {@code mvn package -DskipTests} before starting each service.
     * <p>Java initialisers match Maven defaults so values are correct when this Mojo is invoked
     * programmatically (e.g. from {@code MenuMojo}) without Maven parameter injection.
     */
    @Parameter(property = "fractalx.smoketest.build", defaultValue = "true")
    private boolean build = true;

    /** Seconds to wait for the HTTP port to open before declaring a startup failure. */
    @Parameter(property = "fractalx.smoketest.timeout", defaultValue = "120")
    private int startupTimeout = 120;

    /** Actuator endpoint polled after the port opens. Any HTTP response = Spring context loaded. */
    @Parameter(property = "fractalx.smoketest.health", defaultValue = "/actuator/health")
    private String healthPath = "/actuator/health";

    /** Name of a single service to test. When blank (default) all services are tested. */
    @Parameter(property = "fractalx.smoketest.service", defaultValue = "")
    private String service = "";

    /** When {@code true}, throws {@link MojoFailureException} if any service fails. */
    @Parameter(property = "fractalx.smoketest.failBuild", defaultValue = "false")
    private boolean failBuild = false;

    /** Skip this mojo entirely. */
    @Parameter(property = "fractalx.smoketest.skip", defaultValue = "false")
    private boolean skip = false;

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    // ── Execute ───────────────────────────────────────────────────────────────

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        initCli();
        if (skip) { info("smoke-test skipped (fractalx.smoketest.skip=true)"); return; }

        printHeader("Smoke Test");

        Path root = outputDirectory.toPath();
        if (!Files.isDirectory(root)) {
            warn("Output directory not found: " + root);
            warn("Run 'mvn fractalx:decompose' first.");
            return;
        }

        List<Path> discovered = discoverServiceDirs(root);
        if (!service.isBlank()) {
            discovered = discovered.stream()
                       .filter(d -> d.getFileName().toString().equals(service.trim()))
                       .toList();
            if (discovered.isEmpty())
                throw new MojoExecutionException("Service not found: " + service.trim());
        }
        if (discovered.isEmpty()) { warn("No services found in " + root); return; }

        final List<Path> dirs = List.copyOf(discovered);
        final List<SmokeResult> results = new ArrayList<>();
        long t0 = System.currentTimeMillis();

        // ── Labels: all build rows first, then all start+health rows ─────────
        // Visual order in the Dashboard mirrors the user-facing layout:
        //   service1 · build          [done]
        //   service2 · build          [running]
        //   service1 · start + health [running]  ← fires as soon as svc1 build done
        //   service2 · start + health [pending]
        // Execution is still per-service (build₁→start₁→build₂→start₂…);
        // Dashboard.onDone/onWarn look up rows by label name, so visual position
        // and execution order are independent.
        List<String> labels = new ArrayList<>();
        if (build) for (Path svcDir : dirs) labels.add(svcDir.getFileName() + " · build");
        for (Path svcDir : dirs) labels.add(svcDir.getFileName() + " · start + health");

        runWithDashboard(labels, "Smoke Test", t0, dash -> {
            for (Path svcDir : dirs) {
                String name = svcDir.getFileName().toString();
                SmokeResult r = new SmokeResult(name);

                // ── Build ──────────────────────────────────────────────────
                if (build) {
                    String buildLabel = name + " · build";
                    dash.onStart(buildLabel);
                    int exitCode;
                    try {
                        exitCode = runBuild(svcDir);
                    } catch (IOException | InterruptedException e) {
                        exitCode = -1;
                        r.buildDetail = e.getMessage();
                    }
                    r.buildPassed = (exitCode == 0);
                    if (r.buildPassed) {
                        dash.onDone(buildLabel);
                    } else {
                        if (r.buildDetail == null) r.buildDetail = "exit " + exitCode;
                        dash.onWarn(buildLabel, r.buildDetail);
                        printLogTail(svcDir, "smoketest-build.log", 20);
                        dash.onWarn(name + " · start + health", "skipped (build failed)");
                        results.add(r);
                        continue;
                    }
                } else {
                    r.buildPassed = true; // build skipped — treat as passed
                }

                // ── Start + Health ─────────────────────────────────────────
                String runLabel = name + " · start + health";
                dash.onStart(runLabel);
                int port = readPort(svcDir);
                Process proc = null;
                try {
                    proc = spawnService(svcDir);
                    boolean portOpen = awaitPort(port, startupTimeout, proc);
                    if (!portOpen) {
                        r.startDetail = proc.isAlive()
                                ? "timeout waiting for :" + port
                                : "process exited (code " + proc.exitValue() + ") — see logs";
                        dash.onWarn(runLabel, r.startDetail);
                        printLogTail(svcDir, "smoketest-run.log", 20);
                    } else {
                        r.startPassed = true;
                        int httpStatus = checkHealth(port, healthPath);
                        r.httpStatus = httpStatus;
                        if (httpStatus > 0) {
                            r.healthPassed = true;
                            dash.onDone(runLabel);
                        } else {
                            r.startDetail = "actuator unreachable after port opened";
                            dash.onWarn(runLabel, r.startDetail);
                        }
                    }
                } catch (IOException e) {
                    r.startDetail = e.getMessage();
                    dash.onWarn(runLabel, r.startDetail);
                } finally {
                    killProcess(proc);
                }
                results.add(r);
            }
        });

        // ── Consolidated log ──────────────────────────────────────────────────
        writeConsolidatedLog(root, dirs, results, t0);

        printSummary(results, root, t0);

        if (failBuild) {
            long failures = results.stream().filter(r -> !r.passed()).count();
            if (failures > 0)
                throw new MojoFailureException(failures + " service(s) failed smoke-test");
        }
    }

    // ── Build (blocking) ──────────────────────────────────────────────────────

    private int runBuild(Path svcDir) throws IOException, InterruptedException {
        Path log = svcDir.resolve("logs/smoketest-build.log");
        Files.createDirectories(log.getParent());
        return new ProcessBuilder(buildMvnCommand(svcDir, "package", "-DskipTests", "-q"))
                .directory(svcDir.toFile())
                .redirectOutput(log.toFile())
                .redirectError(ProcessBuilder.Redirect.appendTo(log.toFile()))
                .start()
                .waitFor();
    }

    // ── Spawn service (non-blocking) ──────────────────────────────────────────

    private Process spawnService(Path svcDir) throws IOException {
        Path log = svcDir.resolve("logs/smoketest-run.log");
        Files.createDirectories(log.getParent());

        if (WINDOWS) {
            Path startBat = svcDir.resolve("start.bat");
            if (Files.exists(startBat)) {
                return new ProcessBuilder("cmd.exe", "/c", startBat.toAbsolutePath().toString())
                        .directory(svcDir.toFile())
                        .redirectOutput(log.toFile())
                        .redirectError(ProcessBuilder.Redirect.appendTo(log.toFile()))
                        .start();
            }
        } else {
            Path startSh = svcDir.resolve("start.sh");
            if (Files.exists(startSh)) {
                startSh.toFile().setExecutable(true);
                return new ProcessBuilder("/bin/sh", startSh.toAbsolutePath().toString())
                        .directory(svcDir.toFile())
                        .redirectOutput(log.toFile())
                        .redirectError(ProcessBuilder.Redirect.appendTo(log.toFile()))
                        .start();
            }
        }

        return new ProcessBuilder(buildMvnCommand(svcDir, "spring-boot:run"))
                .directory(svcDir.toFile())
                .redirectOutput(log.toFile())
                .redirectError(ProcessBuilder.Redirect.appendTo(log.toFile()))
                .start();
    }

    // ── Await port ────────────────────────────────────────────────────────────

    private boolean awaitPort(int port, int timeoutSec, Process proc) throws InterruptedException {
        if (port <= 0) {
            Thread.sleep(3_000);
            return proc.isAlive();
        }
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive()) return false;
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("localhost", port), 300);
                return true;
            } catch (IOException ignored) {}
            Thread.sleep(500);
        }
        return false;
    }

    // ── Health check ──────────────────────────────────────────────────────────

    /** Returns the HTTP status code, or {@code -1} if the connection could not be made. */
    private int checkHealth(int port, String path) {
        try {
            URI uri = new URI("http", null, "localhost", port, path, null, null);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(3_000);
            conn.setReadTimeout(5_000);
            conn.setRequestMethod("GET");
            return conn.getResponseCode();
        } catch (Exception e) {
            return -1;
        }
    }

    // ── Kill process ──────────────────────────────────────────────────────────

    private void killProcess(Process proc) {
        if (proc == null || !proc.isAlive()) return;
        proc.destroy();
        try { proc.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        if (proc.isAlive()) proc.destroyForcibly();
    }

    // ── Consolidated log ──────────────────────────────────────────────────────

    /**
     * Writes {@code <outputRoot>/smoketest.log} aggregating the build and start output
     * of every service in one file, with a section header per service and a summary footer.
     */
    private void writeConsolidatedLog(Path root, List<Path> dirs,
                                      List<SmokeResult> results, long t0) {
        Path log = root.resolve("smoketest.log");
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String divider  = "═".repeat(60);
        String subdiver = "─".repeat(60);
        List<String> lines = new ArrayList<>();

        lines.add(divider);
        lines.add("  FractalX Smoke Test  —  " + ts);
        lines.add(divider);
        lines.add("");

        for (int i = 0; i < dirs.size(); i++) {
            Path       svcDir = dirs.get(i);
            SmokeResult r     = results.get(i);
            String status = r.passed() ? "PASSED" : (r.buildPassed ? "START FAILED" : "BUILD FAILED");

            lines.add(divider);
            lines.add("  " + r.name + "  [" + status + "]");
            lines.add(divider);
            lines.add("");

            // ── Build section ──────────────────────────────────────────────
            if (build) {
                lines.add(subdiver);
                lines.add("  BUILD  —  " + (r.buildPassed ? "PASSED" : "FAILED: " + r.buildDetail));
                lines.add(subdiver);
                appendFileLines(lines, svcDir.resolve("logs/smoketest-build.log"));
                lines.add("");
            }

            // ── Start + health section ─────────────────────────────────────
            lines.add(subdiver);
            String startStatus = !r.buildPassed ? "SKIPPED (build failed)"
                    : r.healthPassed ? "PASSED  (HTTP " + r.httpStatus + ")"
                    : r.startPassed  ? "FAILED — " + r.startDetail
                    :                  "FAILED — " + (r.startDetail != null ? r.startDetail : "did not start");
            lines.add("  START + HEALTH  —  " + startStatus);
            lines.add(subdiver);
            if (r.buildPassed) appendFileLines(lines, svcDir.resolve("logs/smoketest-run.log"));
            lines.add("");
        }

        // ── Summary footer ─────────────────────────────────────────────────
        long passed = results.stream().filter(SmokeResult::passed).count();
        lines.add(divider);
        lines.add("  SUMMARY  —  " + passed + " / " + results.size()
                + " services passed  [" + fmt(System.currentTimeMillis() - t0) + "]");
        lines.add(divider);

        try {
            Files.write(log, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warn("Could not write consolidated log: " + e.getMessage());
        }
    }

    /** Appends all lines from {@code path} into {@code dest}; silently skips if missing. */
    private static void appendFileLines(List<String> dest, Path path) {
        if (!Files.exists(path)) return;
        try {
            dest.addAll(Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (IOException ignored) {}
    }

    // ── Maven helpers (mirrors StartMojo pattern) ─────────────────────────────

    private static List<String> buildMvnCommand(Path svcDir, String... args) {
        String mvn = findMaven(svcDir);
        List<String> cmd = new ArrayList<>();
        if (WINDOWS) { cmd.add("cmd.exe"); cmd.add("/c"); }
        cmd.add(mvn);
        cmd.addAll(List.of(args));
        return cmd;
    }

    private static String findMaven(Path svcDir) {
        String home = System.getProperty("maven.home");
        if (home != null) {
            String exe = WINDOWS ? "bin/mvn.cmd" : "bin/mvn";
            File mvn = new File(home, exe);
            if (mvn.exists()) return mvn.getAbsolutePath();
        }
        String wrapper = WINDOWS ? "mvnw.cmd" : "mvnw";
        File mvnw = svcDir.resolve(wrapper).toFile();
        if (mvnw.exists()) return mvnw.getAbsolutePath();
        File mvnwParent = svcDir.getParent().resolve(wrapper).toFile();
        if (mvnwParent.exists()) return mvnwParent.getAbsolutePath();
        return WINDOWS ? "mvn.cmd" : "mvn";
    }

    // ── Port reading (mirrors StartMojo pattern) ──────────────────────────────

    private int readPort(Path svcDir) {
        Path yml = svcDir.resolve("src/main/resources/application.yml");
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

    // ── Service discovery (mirrors StartMojo pattern) ─────────────────────────

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

    // ── Log tail (printed to console on failure) ──────────────────────────────

    private void printLogTail(Path svcDir, String logFileName, int maxLines) {
        Path log = svcDir.resolve("logs/" + logFileName);
        if (!Files.exists(log)) return;
        try {
            List<String> lines = Files.readAllLines(log);
            int from = Math.max(0, lines.size() - maxLines);
            out.println();
            out.println("  " + a(YLW) + "\u26A0" + a(RST)
                    + "  " + a(BLD) + svcDir.getFileName() + a(RST)
                    + "  " + a(DIM) + log + a(RST));
            out.println();
            for (int i = from; i < lines.size(); i++) {
                out.println("  " + a(DIM) + lines.get(i) + a(RST));
            }
            out.println();
        } catch (IOException ignored) {}
    }

    // ── Dashboard wrapper ─────────────────────────────────────────────────────

    @FunctionalInterface
    private interface DashAction { void run(Dashboard dash) throws Exception; }

    private void runWithDashboard(List<String> labels, String subtitle, long t0, DashAction action)
            throws MojoExecutionException {
        if (ansi) { out.print(ALT_ON); out.flush(); }
        Dashboard dash = new Dashboard(labels, out, ansi, subtitle);
        dash.render();
        try {
            action.run(dash);
        } catch (Exception e) {
            if (!labels.isEmpty()) dash.onFail(labels.get(0), e.getMessage());
            if (ansi) {
                try { Thread.sleep(2_500); } catch (InterruptedException ignored) {}
                out.print(ALT_OFF); out.flush();
            }
            throw new MojoExecutionException("Smoke test failed", e);
        }
        dash.finish();
        if (ansi) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            out.print(ALT_OFF); out.flush();
        }
        out.println();
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    private void printSummary(List<SmokeResult> results, Path root, long t0) {
        int passed = (int) results.stream().filter(SmokeResult::passed).count();
        int total  = results.size();
        int labelW = results.stream().mapToInt(r -> r.name.length()).max().orElse(12);

        section("Smoke Test");

        for (SmokeResult r : results) {
            String icon   = r.passed() ? a(GRN) + "\u2713" + a(RST) : a(YLW) + "\u26A0" + a(RST);
            String detail = r.passed()
                    ? a(DIM) + "build + start + health" + (r.httpStatus > 0 ? " (HTTP " + r.httpStatus + ")" : "") + a(RST)
                    : a(DIM) + buildFailDetail(r) + a(RST);
            out.println("  " + icon + "  " + pad(r.name, labelW) + "  " + detail);
        }

        out.println();
        String passFail = passed == total
                ? a(GRN) + passed + " / " + total + " services passed" + a(RST)
                : a(YLW) + passed + " / " + total + " services passed" + a(RST);
        out.println("  " + passFail + "  " + a(DIM) + "[" + fmt(System.currentTimeMillis() - t0) + "]" + a(RST));
        out.println();

        out.println("  " + a(DIM) + "Consolidated log  " + root.resolve("smoketest.log") + a(RST));
        out.println("  " + a(DIM) + "Per-service logs  <service>/logs/smoketest-{build,run}.log" + a(RST));
        out.println();
        cmd("mvn fractalx:smoke-test -Dfractalx.smoketest.build=false  # start-only re-run");
        cmd("mvn fractalx:verify                                        # static verification");
        out.println();
        done(System.currentTimeMillis() - t0);
    }

    private static String buildFailDetail(SmokeResult r) {
        if (!r.buildPassed && r.buildDetail != null) return "build failed: " + r.buildDetail;
        if (!r.buildPassed)                          return "build failed";
        if (!r.startPassed && r.startDetail != null) return "start failed: " + r.startDetail;
        if (!r.healthPassed)                         return "build + start passed · actuator unreachable";
        return "unknown failure";
    }

    // ── Result record ─────────────────────────────────────────────────────────

    private static final class SmokeResult {
        final String name;
        boolean buildPassed  = false;
        String  buildDetail  = null;
        boolean startPassed  = false;
        boolean healthPassed = false;
        int     httpStatus   = -1;
        String  startDetail  = null;

        SmokeResult(String name) { this.name = name; }

        boolean passed() { return buildPassed && startPassed && healthPassed; }
    }

    // ── Windows helper ────────────────────────────────────────────────────────

    private static boolean isWindows() { return WINDOWS; }
}
