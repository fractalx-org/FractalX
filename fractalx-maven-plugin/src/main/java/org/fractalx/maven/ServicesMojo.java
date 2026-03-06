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
 * Lists all generated services in the FractalX output directory.
 *
 * <pre>
 *   mvn fractalx:services
 * </pre>
 */
@Mojo(name = "services")
public class ServicesMojo extends FractalxBaseMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "fractalx.outputDirectory",
               defaultValue = "${project.basedir}/fractalx-output")
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        initCli();

        printHeader("Services");

        if (!outputDirectory.exists()) {
            warn("Output directory not found: " + outputDirectory.getAbsolutePath());
            warn("Run 'mvn fractalx:decompose' first.");
            return;
        }

        List<ServiceEntry> services = discoverServices(outputDirectory.toPath());

        if (services.isEmpty()) {
            warn("No generated services found in " + outputDirectory.getAbsolutePath());
            return;
        }

        int pw = services.stream().mapToInt(s -> s.name.length()).max().orElse(10) + 2;

        section("Generated Services");
        for (ServiceEntry s : services) {
            String portStr = s.port > 0 ? a(DIM) + ":" + s.port + a(RST) : a(DIM) + "port unknown" + a(RST);
            out.println("  " + a(GRN) + "\u25AA" + a(RST)
                    + "  " + a(BLD) + pad(s.name, pw) + a(RST)
                    + "  " + portStr
                    + (s.hasDocker ? "  " + a(DIM) + "[docker]" + a(RST) : ""));
        }
        out.println();

        section("Output");
        out.println("  " + a(DIM) + outputDirectory.getAbsolutePath() + a(RST));
        out.println();

        section("Get started");
        cmd("mvn fractalx:start");
        cmd("mvn fractalx:ps");
        cmd("docker-compose -f " + outputDirectory.getAbsolutePath() + "/docker-compose.yml up -d");
        out.println();
    }

    private List<ServiceEntry> discoverServices(Path root) throws MojoExecutionException {
        List<ServiceEntry> result = new ArrayList<>();
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                  .sorted()
                  .forEach(dir -> {
                      Path pom = dir.resolve("pom.xml");
                      if (!Files.exists(pom)) return;
                      String name    = dir.getFileName().toString();
                      int    port    = readPortFromYaml(dir);
                      boolean docker = Files.exists(dir.resolve("Dockerfile"));
                      result.add(new ServiceEntry(name, port, docker));
                  });
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to list services", e);
        }
        return result;
    }

    private int readPortFromYaml(Path serviceDir) {
        Path yml = serviceDir.resolve("src/main/resources/application.yml");
        if (!Files.exists(yml)) return -1;
        try {
            for (String line : Files.readAllLines(yml)) {
                line = line.trim();
                if (line.startsWith("port:")) {
                    String val = line.substring("port:".length()).trim();
                    return Integer.parseInt(val);
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private record ServiceEntry(String name, int port, boolean hasDocker) {}
}
