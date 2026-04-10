package org.fractalx.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.fractalx.core.verifier.EndpointScanner;
import org.fractalx.core.verifier.EndpointScanner.EndpointSpec;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Starts each generated service, discovers all REST endpoints via static analysis of
 * {@code *Controller.java} files, probes each endpoint with an appropriate HTTP request,
 * and reports whether every route responds without a 5xx server error.
 *
 * <p>Endpoint probing strategy:
 * <ul>
 *   <li><b>No path variables</b> — sends the request as-is; e.g. {@code GET /users}</li>
 *   <li><b>Path variables</b> — substitutes each {@code {var}} with {@value #PROBE_VALUE};
 *       e.g. {@code GET /users/__probe__}</li>
 *   <li><b>POST / PUT / PATCH with {@code @RequestBody}</b> — sends an empty JSON object
 *       {@code {}} as the request body</li>
 * </ul>
 *
 * <p>A probe <em>passes</em> when the HTTP status is in the range 1xx–4xx.
 * A 4xx response (404 Not Found, 400 Bad Request, 401 Unauthorized, 422 Unprocessable)
 * is considered a pass because the route resolved correctly and the application logic
 * rejected the probe input — not a server-side crash.
 * A 5xx response or connection failure after startup is a failure.
 *
 * <pre>
 *   mvn fractalx:endpoint-verify                                               # all services
 *   mvn fractalx:endpoint-verify -Dfractalx.endpoints.build=false             # skip build phase
 *   mvn fractalx:endpoint-verify -Dfractalx.endpoints.service=order-service   # single service
 *   mvn fractalx:endpoint-verify -Dfractalx.endpoints.failBuild=true          # fail on 5xx
 * </pre>
 */
@Mojo(name = "endpoint-verify", requiresProject = true, threadSafe = false)
public class EndpointVerifyMojo extends FractalxBaseMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Directory that contains the generated microservices. */
    @Parameter(property = "fractalx.outputDirectory",
               defaultValue = "${project.basedir}/microservices")
    private File outputDirectory;

    /**
     * When {@code true} (default), runs {@code mvn package -DskipTests} before starting
     * each service. Set to {@code false} to reuse previously built artifacts.
     */
    @Parameter(property = "fractalx.endpoints.build", defaultValue = "true")
    private boolean build = true;

    /** Seconds to wait for the HTTP port to open before declaring a startup failure. */
    @Parameter(property = "fractalx.endpoints.timeout", defaultValue = "120")
    private int startupTimeout = 120;

    /** Actuator health endpoint verified before endpoint probing begins. */
    @Parameter(property = "fractalx.endpoints.health", defaultValue = "/actuator/health")
    private String healthPath = "/actuator/health";

    /** Name of a single service to verify. When blank (default) all services are tested. */
    @Parameter(property = "fractalx.endpoints.service", defaultValue = "")
    private String service = "";

    /**
     * When {@code true}, throws {@link MojoFailureException} if any endpoint returns 5xx
     * or is unreachable after the service started.
     */
    @Parameter(property = "fractalx.endpoints.failBuild", defaultValue = "false")
    private boolean failBuild = false;

    /** Skip this mojo entirely. */
    @Parameter(property = "fractalx.endpoints.skip", defaultValue = "false")
    private boolean skip = false;

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    /** Value substituted for every {@code {variable}} segment during probing. */
    private static final String PROBE_VALUE = "__probe__";

    private static final Pattern PATH_VAR = Pattern.compile("\\{[^}]+}");

    // ── Execute ───────────────────────────────────────────────────────────────

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        initCli();
        if (skip) { info("endpoint-verify skipped (fractalx.endpoints.skip=true)"); return; }

        printHeader("Endpoint Verify");

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

        final List<Path>          dirs    = List.copyOf(discovered);
        final List<ServiceResult> results = new ArrayList<>();
        long t0 = System.currentTimeMillis();

        // Dashboard: build rows first, then one endpoint-probe row per service
        List<String> labels = new ArrayList<>();
        if (build) for (Path d : dirs) labels.add(d.getFileName() + " · build");
        for (Path d : dirs) labels.add(d.getFileName() + " · endpoints");

        runWithDashboard(labels, "Endpoint Verify", t0, dash -> {
            EndpointScanner scanner = new EndpointScanner();

            for (Path svcDir : dirs) {
                String        name = svcDir.getFileName().toString();
                ServiceResult r    = new ServiceResult(name);

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
                        dash.onWarn(name + " · endpoints", "skipped (build failed)");
                        results.add(r);
                        continue;
                    }
                } else {
                    r.buildPassed = true;
                }

                // ── Static endpoint discovery ──────────────────────────────
                r.endpoints = scanner.scan(svcDir);

                // ── Start + probe ──────────────────────────────────────────
                String probeLabel = name + " · endpoints";
                dash.onStart(probeLabel);
                int     port = readPort(svcDir);
                Process proc = null;
                try {
                    proc = spawnService(svcDir);

                    boolean portOpen = awaitPort(port, startupTimeout, proc);
                    if (!portOpen) {
                        r.startDetail = proc.isAlive()
                                ? "timeout waiting for :" + port
                                : "process exited (code " + proc.exitValue() + ")";
                        dash.onWarn(probeLabel, r.startDetail);
                        results.add(r);
                        continue;
                    }
                    r.startPassed = true;

                    // confirm health before probing
                    if (httpGet(port, healthPath) <= 0) {
                        r.startDetail = "actuator unreachable after port opened";
                        dash.onWarn(probeLabel, r.startDetail);
                        results.add(r);
                        continue;
                    }

                    // probe endpoints
                    for (EndpointSpec spec : r.endpoints) {
                        String probedPath = PATH_VAR.matcher(spec.path()).replaceAll(PROBE_VALUE);
                        int    status     = probeEndpoint(port, spec.httpMethod(), probedPath,
                                                          spec.hasRequestBody());
                        r.probeResults.add(new ProbeResult(spec, probedPath, status));
                    }

                    long failed = r.probeResults.stream().filter(p -> !p.passed()).count();
                    if (r.endpoints.isEmpty() || failed == 0) {
                        dash.onDone(probeLabel);
                    } else {
                        dash.onWarn(probeLabel,
                                failed + "/" + r.probeResults.size() + " endpoint(s) returned 5xx");
                    }

                } catch (IOException e) {
                    r.startDetail = e.getMessage();
                    dash.onWarn(probeLabel, r.startDetail);
                } finally {
                    killProcess(proc);
                }
                results.add(r);
            }
        });

        writeConsolidatedLog(root, results, t0);
        printSummary(results, root, t0);

        if (failBuild) {
            long failures = results.stream()
                    .flatMap(r -> r.probeResults.stream())
                    .filter(p -> !p.passed())
                    .count();
            if (failures > 0)
                throw new MojoFailureException(failures + " endpoint(s) failed (5xx or unreachable)");
        }
    }

    // ── Endpoint probing ──────────────────────────────────────────────────────

    /**
     * Shared HTTP client for endpoint probing.  Uses {@link HttpClient} instead of
     * {@link HttpURLConnection} because the latter rejects PATCH as an HTTP method.
     */
    private final HttpClient probeClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * Sends a single HTTP probe to {@code localhost:<port><path>}.
     *
     * <ul>
     *   <li>POST / PUT / PATCH with {@code hasBody=true} — sends {@code {}} as JSON body</li>
     *   <li>All other methods — no request body</li>
     * </ul>
     *
     * @return the HTTP response code, or {@code -1} if the connection failed
     */
    private int probeEndpoint(int port, String method, String path, boolean hasBody) {
        try {
            URI uri = new URI("http", null, "localhost", port, path, null, null);
            HttpRequest.Builder rb = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5));

            boolean bodyMethod = method.equals("POST") || method.equals("PUT") || method.equals("PATCH");
            if (hasBody && bodyMethod) {
                rb.method(method, HttpRequest.BodyPublishers.ofString("{}"))
                  .header("Content-Type", "application/json");
            } else {
                rb.method(method, HttpRequest.BodyPublishers.noBody());
            }

            return probeClient.send(rb.build(), HttpResponse.BodyHandlers.discarding())
                              .statusCode();
        } catch (Exception e) {
            return -1;
        }
    }

    private int httpGet(int port, String path) {
        try {
            URI               uri  = new URI("http", null, "localhost", port, path, null, null);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(3_000);
            conn.setReadTimeout(5_000);
            conn.setRequestMethod("GET");
            return conn.getResponseCode();
        } catch (Exception e) {
            return -1;
        }
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private int runBuild(Path svcDir) throws IOException, InterruptedException {
        Path log = svcDir.resolve("logs/endpoint-verify-build.log");
        Files.createDirectories(log.getParent());
        return new ProcessBuilder(buildMvnCommand(svcDir, "package", "-DskipTests", "-q"))
                .directory(svcDir.toFile())
                .redirectOutput(log.toFile())
                .redirectError(ProcessBuilder.Redirect.appendTo(log.toFile()))
                .start()
                .waitFor();
    }

    // ── Spawn / await / kill ──────────────────────────────────────────────────

    private Process spawnService(Path svcDir) throws IOException {
        Path log = svcDir.resolve("logs/endpoint-verify-run.log");
        Files.createDirectories(log.getParent());

        if (WINDOWS) {
            Path bat = svcDir.resolve("start.bat");
            if (Files.exists(bat))
                return new ProcessBuilder("cmd.exe", "/c", bat.toAbsolutePath().toString())
                        .directory(svcDir.toFile())
                        .redirectOutput(log.toFile())
                        .redirectError(ProcessBuilder.Redirect.appendTo(log.toFile()))
                        .start();
        } else {
            Path sh = svcDir.resolve("start.sh");
            if (Files.exists(sh)) {
                sh.toFile().setExecutable(true);
                return new ProcessBuilder("/bin/sh", sh.toAbsolutePath().toString())
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

    private boolean awaitPort(int port, int timeoutSec, Process proc) throws InterruptedException {
        if (port <= 0) { Thread.sleep(3_000); return proc.isAlive(); }
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

    private void killProcess(Process proc) {
        if (proc == null || !proc.isAlive()) return;
        proc.destroy();
        try { proc.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        if (proc.isAlive()) proc.destroyForcibly();
    }

    // ── Consolidated log ──────────────────────────────────────────────────────

    private void writeConsolidatedLog(Path root, List<ServiceResult> results, long t0) {
        Path        log  = root.resolve("endpoint-verify.log");
        String      ts   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String      div  = "═".repeat(60);
        String      sub  = "─".repeat(60);
        List<String> lines = new ArrayList<>();

        lines.add(div);
        lines.add("  FractalX Endpoint Verify  —  " + ts);
        lines.add(div);
        lines.add("");

        for (ServiceResult r : results) {
            lines.add(div);
            lines.add("  " + r.name + "  [" + r.overallStatus() + "]");
            lines.add(div);

            if (!r.buildPassed) {
                lines.add("  BUILD FAILED: " + r.buildDetail);
                lines.add("");
                continue;
            }
            if (!r.startPassed) {
                lines.add("  START FAILED: " + (r.startDetail != null ? r.startDetail : "unknown"));
                lines.add("");
                continue;
            }
            if (r.endpoints.isEmpty()) {
                lines.add("  No REST controllers found — nothing to probe.");
                lines.add("");
                continue;
            }

            lines.add("  " + r.endpoints.size() + " endpoint(s) discovered, "
                    + r.probeResults.size() + " probed");
            lines.add("");
            lines.add(sub);

            int padW = r.probeResults.stream()
                    .mapToInt(p -> p.spec().httpMethod().length() + 1 + p.probedPath().length())
                    .max().orElse(20);

            for (ProbeResult p : r.probeResults) {
                String label  = p.spec().httpMethod() + " " + p.probedPath();
                String status = p.httpStatus() < 0 ? "UNREACHABLE" : "HTTP " + p.httpStatus();
                String result = p.passed() ? "PASS" : "FAIL";
                lines.add("  [" + result + "]  " + pad(label, padW) + "  " + status);
            }
            lines.add(sub);
            lines.add("");
        }

        long totalProbed = results.stream().mapToLong(r -> r.probeResults.size()).sum();
        long totalPassed = results.stream()
                .flatMap(r -> r.probeResults.stream())
                .filter(ProbeResult::passed).count();
        lines.add(div);
        lines.add("  SUMMARY  —  " + totalPassed + " / " + totalProbed
                + " endpoints passed  [" + fmt(System.currentTimeMillis() - t0) + "]");
        lines.add(div);

        try {
            Files.write(log, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warn("Could not write log: " + e.getMessage());
        }
    }

    // ── Summary (console) ─────────────────────────────────────────────────────

    private void printSummary(List<ServiceResult> results, Path root, long t0) {
        section("Endpoint Verify");

        int labelW = results.stream().mapToInt(r -> r.name.length()).max().orElse(12);

        for (ServiceResult r : results) {
            if (!r.buildPassed) {
                out.println("  " + a(YLW) + "\u26A0" + a(RST) + "  "
                        + pad(r.name, labelW) + "  " + a(DIM) + "build failed" + a(RST));
                continue;
            }
            if (!r.startPassed) {
                out.println("  " + a(YLW) + "\u26A0" + a(RST) + "  "
                        + pad(r.name, labelW) + "  " + a(DIM) + "start failed"
                        + (r.startDetail != null ? ": " + r.startDetail : "") + a(RST));
                continue;
            }
            if (r.endpoints.isEmpty()) {
                out.println("  " + a(DIM) + "\u2013" + a(RST) + "  "
                        + pad(r.name, labelW) + "  " + a(DIM) + "no endpoints" + a(RST));
                continue;
            }

            long passed = r.probeResults.stream().filter(ProbeResult::passed).count();
            long total  = r.probeResults.size();
            boolean allGood = passed == total;
            String icon   = allGood ? a(GRN) + "\u2713" + a(RST) : a(YLW) + "\u26A0" + a(RST);
            String detail = a(DIM) + passed + "/" + total + " endpoints passed" + a(RST);

            out.println("  " + icon + "  " + pad(r.name, labelW) + "  " + detail);

            if (!allGood) {
                for (ProbeResult p : r.probeResults) {
                    if (p.passed()) continue;
                    String status = p.httpStatus() < 0 ? "unreachable" : "HTTP " + p.httpStatus();
                    out.println("  " + a(RED) + "    \u2022 " + a(RST)
                            + a(DIM) + p.spec().httpMethod() + " " + p.spec().path()
                            + "  (" + status + ")" + a(RST));
                }
            }
        }

        out.println();
        long totalProbed = results.stream().mapToLong(r -> r.probeResults.size()).sum();
        long totalPassed = results.stream()
                .flatMap(r -> r.probeResults.stream())
                .filter(ProbeResult::passed).count();
        String summary = (totalPassed == totalProbed && totalProbed > 0)
                ? a(GRN) + totalPassed + " / " + totalProbed + " endpoints passed" + a(RST)
                : a(YLW) + totalPassed + " / " + totalProbed + " endpoints passed" + a(RST);
        out.println("  " + summary + "  " + a(DIM) + "[" + fmt(System.currentTimeMillis() - t0) + "]" + a(RST));
        out.println();

        out.println("  " + a(DIM) + "Full report   " + root.resolve("endpoint-verify.log") + a(RST));
        out.println("  " + a(DIM) + "Run logs      <service>/logs/endpoint-verify-run.log" + a(RST));
        out.println();
        cmd("mvn fractalx:endpoint-verify -Dfractalx.endpoints.build=false  # skip build");
        cmd("mvn fractalx:smoke-test                                          # health-only check");
        out.println();
        done(System.currentTimeMillis() - t0);
    }

    // ── Maven helpers (mirrors SmokeTestMojo) ─────────────────────────────────

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

    // ── Port reading (mirrors SmokeTestMojo) ──────────────────────────────────

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

    // ── Service discovery (mirrors SmokeTestMojo) ────────────────────────────

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

    // ── Dashboard wrapper (mirrors SmokeTestMojo) ─────────────────────────────

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
            throw new MojoExecutionException("Endpoint verify failed", e);
        }
        dash.finish();
        if (ansi) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            out.print(ALT_OFF); out.flush();
        }
        out.println();
    }

    // ── Result model ──────────────────────────────────────────────────────────

    private static final class ServiceResult {
        final String            name;
        boolean                 buildPassed  = false;
        String                  buildDetail  = null;
        boolean                 startPassed  = false;
        String                  startDetail  = null;
        List<EndpointSpec>      endpoints    = List.of();
        final List<ProbeResult> probeResults = new ArrayList<>();

        ServiceResult(String name) { this.name = name; }

        String overallStatus() {
            if (!buildPassed) return "BUILD FAILED";
            if (!startPassed) return "START FAILED";
            long failed = probeResults.stream().filter(p -> !p.passed()).count();
            if (failed > 0) return failed + " ENDPOINT(S) FAILED";
            return endpoints.isEmpty() ? "NO ENDPOINTS" : "PASSED";
        }
    }

    private record ProbeResult(EndpointSpec spec, String probedPath, int httpStatus) {
        /** Passes when the response is any non-5xx status (1xx–4xx). */
        boolean passed() { return httpStatus > 0 && httpStatus < 500; }
    }
}
