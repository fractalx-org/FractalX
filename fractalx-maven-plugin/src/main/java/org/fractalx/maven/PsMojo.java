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
 * Shows which generated services are currently running (port-based check).
 *
 * <pre>
 *   mvn fractalx:ps
 * </pre>
 */
@Mojo(name = "ps")
public class PsMojo extends FractalxBaseMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "fractalx.outputDirectory",
               defaultValue = "${project.basedir}/fractalx-output")
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        initCli();

        printHeader("Process Status");

        if (!outputDirectory.exists()) {
            warn("Output directory not found: " + outputDirectory.getAbsolutePath());
            warn("Run 'mvn fractalx:decompose' first.");
            return;
        }

        List<ProcessEntry> entries = discoverAndProbe(outputDirectory.toPath());

        if (entries.isEmpty()) {
            warn("No generated services found.");
            return;
        }

        int running = (int) entries.stream().filter(e -> e.up).count();
        int pw = entries.stream().mapToInt(e -> e.name.length()).max().orElse(10) + 4;

        section("Service Status");
        for (ProcessEntry e : entries) {
            String type = serviceType(e.name);
            String tag  = a(DIM) + "[" + type + "]" + a(RST);
            if (e.port < 0) {
                out.println("  " + a(DIM) + "\u2013  " + pad(e.name, pw) + "  port unknown" + a(RST)
                        + "  " + tag);
            } else if (e.up) {
                String grpc = "service".equals(type)
                        ? "  " + a(DIM) + "grpc:" + (e.port + 10000) + a(RST) : "";
                String logIndicator = Files.exists(e.svcDir.resolve("logs/startup.log"))
                        ? "" : "  " + a(DIM) + "[no log]" + a(RST);
                out.println("  " + a(GRN) + "\u25AA" + a(RST)
                        + "  " + a(BLD) + pad(e.name, pw) + a(RST)
                        + "  " + a(GRN) + "running" + a(RST)
                        + "  " + a(DIM) + "http://localhost:" + e.port + a(RST)
                        + grpc + "  " + tag + logIndicator);
            } else {
                boolean hasLog = Files.exists(e.svcDir.resolve("logs/startup.log"));
                String logHint = hasLog
                        ? "  " + a(DIM) + "logs/" + a(RST) : "  " + a(DIM) + "[never started]" + a(RST);
                out.println("  " + a(DIM) + "\u25AB  " + pad(e.name, pw) + a(RST)
                        + "  " + a(DIM) + "stopped  :" + e.port + a(RST)
                        + "  " + tag + logHint);
            }
        }
        out.println();

        String status = running + "/" + entries.size() + " running";
        if (running == entries.size()) {
            out.println("  " + a(GRN) + "\u2713" + a(RST) + "  " + a(BLD) + status + a(RST));
        } else if (running == 0) {
            out.println("  " + a(DIM) + "\u25AB  " + status + a(RST));
            out.println();
            cmd("mvn fractalx:start");
        } else {
            out.println("  " + a(YLW) + "\u26A0" + a(RST) + "  " + status);
        }

        // Show admin dashboard link if admin-service is running
        entries.stream()
               .filter(e -> "admin".equals(serviceType(e.name)) && e.up)
               .findFirst()
               .ifPresent(e -> {
                   out.println();
                   out.println("  " + a(DIM) + "Admin   \u2192  " + a(RST) + "http://localhost:" + e.port);
               });
        out.println();
    }

    private List<ProcessEntry> discoverAndProbe(Path root) throws MojoExecutionException {
        List<ProcessEntry> result = new ArrayList<>();
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                  .sorted()
                  .forEach(dir -> {
                      if (!Files.exists(dir.resolve("pom.xml"))) return;
                      String name = dir.getFileName().toString();
                      int    port = readPort(dir);
                      boolean up  = port > 0 && isPortOpen(port);
                      result.add(new ProcessEntry(name, port, up, dir));
                  });
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to list services", e);
        }
        return result;
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

    private boolean isPortOpen(int port) {
        try (Socket s = new Socket("localhost", port)) { return true; }
        catch (IOException e) { return false; }
    }

    private record ProcessEntry(String name, int port, boolean up, Path svcDir) {}

    private static String serviceType(String name) {
        return switch (name) {
            case "fractalx-gateway"  -> "gateway";
            case "fractalx-registry" -> "registry";
            case "admin-service"     -> "admin";
            default                  -> "service";
        };
    }
}
