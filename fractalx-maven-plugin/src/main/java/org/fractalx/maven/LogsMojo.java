package org.fractalx.maven;

import org.fractalx.core.FractalxVersion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactively selects a generated service and streams its log file.
 *
 * <pre>
 *   mvn fractalx:logs                                    # pick service interactively
 *   mvn fractalx:logs -Dfractalx.service=order-service   # stream directly
 * </pre>
 *
 * The command tails the last 100 lines then follows. Press Ctrl-C to stop.
 * Services must write logs to a file — add {@code logging.file.name=logs/app.log}
 * to the service's {@code application.yml} if no log file is found.
 */
@Mojo(name = "logs")
public class LogsMojo extends FractalxBaseMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "fractalx.outputDirectory",
               defaultValue = "${project.basedir}/fractalx-output")
    private File outputDirectory;

    /** Optional: skip the interactive picker and stream this service directly. */
    @Parameter(property = "fractalx.service", defaultValue = "")
    private String service = "";

    /** Number of historical lines to show before following. */
    @Parameter(property = "fractalx.logs.lines", defaultValue = "100")
    private int lines = 100;

    // ── Execute ───────────────────────────────────────────────────────────────

    @Override
    public void execute() throws MojoExecutionException {
        initCli();

        if (!outputDirectory.exists()) {
            printHeader("Logs");
            warn("Output directory not found: " + outputDirectory.getAbsolutePath());
            warn("Run 'mvn fractalx:decompose' first.");
            return;
        }

        List<Path> serviceDirs = discoverServiceDirs(outputDirectory.toPath());
        if (serviceDirs.isEmpty()) {
            printHeader("Logs");
            warn("No generated services found in " + outputDirectory.getAbsolutePath());
            return;
        }

        // Resolve which service to stream
        Path chosen;
        if (!service.isBlank()) {
            chosen = outputDirectory.toPath().resolve(service.trim());
            if (!Files.isDirectory(chosen)) {
                printHeader("Logs");
                warn("Service not found: " + chosen.toAbsolutePath());
                return;
            }
        } else if (serviceDirs.size() == 1) {
            chosen = serviceDirs.get(0);
        } else {
            chosen = pickService(serviceDirs);
            if (chosen == null) return; // user quit
        }

        // Find log file
        Path logFile = findLogFile(chosen);

        printHeader("Logs");
        out.println("  " + a(BLD) + chosen.getFileName() + a(RST));
        out.println();

        if (logFile == null) {
            warn("No log file found for " + chosen.getFileName());
            out.println();
            out.println("  " + a(DIM) + "Add the following to "
                    + chosen.getFileName() + "/src/main/resources/application.yml:" + a(RST));
            out.println();
            out.println("  " + a(CYN) + "  logging.file.name: logs/app.log" + a(RST));
            out.println();
            return;
        }

        out.println("  " + a(DIM) + logFile.toAbsolutePath() + a(RST));
        out.println("  " + a(DIM) + "Showing last " + lines + " lines — Ctrl-C to stop" + a(RST));
        out.println();
        section("Output");

        tailLog(logFile);
    }

    // ── Interactive service picker ────────────────────────────────────────────

    private Path pickService(List<Path> dirs) throws MojoExecutionException {
        if (!ansi) return pickNumbered(dirs);

        int nameW = dirs.stream()
                .mapToInt(d -> d.getFileName().toString().length())
                .max().orElse(8) + 3;

        out.print(ALT_ON);
        out.flush();

        boolean rawEnabled = enableRawMode();
        if (rawEnabled) {
            Runtime.getRuntime().addShutdownHook(new Thread(LogsMojo::restoreTerminal));
        }

        int selected = 0;
        try (FileInputStream tty = new FileInputStream("/dev/tty")) {
            drawPicker(dirs, selected, nameW);

            while (true) {
                int b = tty.read();

                if (b == 27) {
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                    if (tty.available() > 0) {
                        int b2 = tty.read();
                        if (b2 == '[' && tty.available() > 0) {
                            int b3 = tty.read();
                            if      (b3 == 'A') selected = (selected - 1 + dirs.size()) % dirs.size();
                            else if (b3 == 'B') selected = (selected + 1) % dirs.size();
                        }
                    } else {
                        selected = -1; break;           // bare ESC → quit
                    }
                } else if (b == '\r' || b == '\n') {
                    break;
                } else if (b == 'q' || b == 3 || b == 4) {
                    selected = -1; break;
                } else if (b == 'k') {
                    selected = (selected - 1 + dirs.size()) % dirs.size();
                } else if (b == 'j') {
                    selected = (selected + 1) % dirs.size();
                }

                drawPicker(dirs, selected, nameW);
            }

        } catch (IOException e) {
            selected = -1;
        } finally {
            restoreTerminal();
            out.print(ALT_OFF);
            out.flush();
        }

        return selected >= 0 ? dirs.get(selected) : null;
    }

    private void drawPicker(List<Path> dirs, int selected, int nameW) {
        StringBuilder sb = new StringBuilder();
        sb.append(HOME);

        sb.append("\r\033[2K\r\n");

        // Banner
        for (int i = 0; i < BANNER.length; i++) {
            String color = i < BANNER_COLORS.length ? BANNER_COLORS[i] : "";
            sb.append("\r\033[2K").append(color).append(BANNER[i]).append(RST).append("\r\n");
        }

        sb.append("\r\033[2K\r\n");
        sb.append("\r\033[2K  ").append(DIM)
          .append("Logs  ").append(FractalxVersion.get()).append(RST).append("\r\n");
        sb.append("\r\033[2K\r\n");

        sb.append("\r\033[2K  ").append(DIM)
          .append("\u2191\u2193 navigate   Enter select   q quit").append(RST).append("\r\n");
        sb.append("\r\033[2K\r\n");

        for (int i = 0; i < dirs.size(); i++) {
            boolean sel   = (i == selected);
            String  name  = dirs.get(i).getFileName().toString();
            Path    lf    = findLogFile(dirs.get(i));
            String  hint  = lf != null
                    ? a(DIM) + "  " + lf.getParent().getFileName() + "/" + lf.getFileName() + RST
                    : a(DIM) + "  no log file" + RST;

            String cursor  = sel ? GRN + "\u25B6" + RST : " ";
            String nameStr = sel ? BLD + pad(name, nameW) + RST : DIM + pad(name, nameW) + RST;

            sb.append("\r\033[2K  ").append(cursor).append("  ")
              .append(nameStr).append(sel ? hint : "").append("\r\n");
        }

        sb.append(CLR_END);
        out.print(sb);
        out.flush();
    }

    /** Fallback picker for dumb / no-ANSI terminals. */
    private Path pickNumbered(List<Path> dirs) {
        printHeader("Logs");
        out.println("  Select a service:");
        out.println();
        for (int i = 0; i < dirs.size(); i++) {
            out.println("  [" + (i + 1) + "] " + dirs.get(i).getFileName());
        }
        out.println();
        out.print("  Enter number: ");
        out.flush();
        try (java.util.Scanner sc = new java.util.Scanner(System.in)) {
            if (sc.hasNextInt()) {
                int choice = sc.nextInt();
                if (choice >= 1 && choice <= dirs.size()) return dirs.get(choice - 1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ── Log file discovery ────────────────────────────────────────────────────

    private Path findLogFile(Path serviceDir) {
        // Try common Spring Boot log locations in priority order
        for (String candidate : new String[]{
                "logs/app.log", "logs/spring.log", "logs/application.log",
                "app.log", "spring.log"}) {
            Path p = serviceDir.resolve(candidate);
            if (Files.exists(p)) return p;
        }
        // Search for any .log file inside a logs/ subdirectory
        Path logsDir = serviceDir.resolve("logs");
        if (Files.isDirectory(logsDir)) {
            try (var stream = Files.list(logsDir)) {
                return stream.filter(f -> f.toString().endsWith(".log"))
                             .sorted()
                             .findFirst()
                             .orElse(null);
            } catch (IOException ignored) {}
        }
        return null;
    }

    // ── Log streaming ─────────────────────────────────────────────────────────

    private void tailLog(Path logFile) throws MojoExecutionException {
        try {
            Process p = new ProcessBuilder(
                    "tail", "-n", String.valueOf(lines), "-f",
                    logFile.toAbsolutePath().toString())
                    .inheritIO()
                    .start();
            Runtime.getRuntime().addShutdownHook(new Thread(p::destroy));
            p.waitFor();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to stream log: " + logFile, e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private static boolean enableRawMode() {
        try {
            new ProcessBuilder("sh", "-c", "stty -echo -icanon min 1 < /dev/tty")
                    .redirectErrorStream(true).start().waitFor();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void restoreTerminal() {
        try {
            new ProcessBuilder("sh", "-c", "stty echo icanon < /dev/tty")
                    .redirectErrorStream(true).start().waitFor();
        } catch (Exception ignored) {}
    }
}
