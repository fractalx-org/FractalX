package org.fractalx.core.generator.registry;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Generates application.yml for the fractalx-registry service. */
class RegistryConfigGenerator {

    private static final Logger log = LoggerFactory.getLogger(RegistryConfigGenerator.class);
    static final int REGISTRY_PORT = 8761;

    void generate(Path srcMainResources, List<FractalModule> modules) throws IOException {
        StringBuilder yml = new StringBuilder();
        yml.append("server:\n");
        yml.append("  port: ").append(REGISTRY_PORT).append("\n\n");
        yml.append("spring:\n");
        yml.append("  application:\n");
        yml.append("    name: fractalx-registry\n\n");
        yml.append("fractalx:\n");
        yml.append("  registry:\n");
        yml.append("    evict-after-ms: 90000\n");
        yml.append("    poll-interval-ms: 15000\n");
        yml.append("    # Pre-seeded known services (services also self-register on startup)\n");
        yml.append("    known-services:\n");
        for (FractalModule m : modules) {
            yml.append("      - name: ").append(m.getServiceName()).append("\n");
            yml.append("        host: ${").append(m.getServiceName().toUpperCase().replace("-", "_"))
               .append("_HOST:localhost}\n");
            yml.append("        port: ").append(m.getPort()).append("\n");
            yml.append("        grpcPort: ").append(m.getPort() + 10000).append("\n");
            yml.append("        healthUrl: http://localhost:").append(m.getPort()).append("/actuator/health\n");
        }
        yml.append("\nmanagement:\n");
        yml.append("  endpoints:\n");
        yml.append("    web:\n");
        yml.append("      exposure:\n");
        yml.append("        include: health,info\n");
        yml.append("\nlogging:\n");
        yml.append("  level:\n");
        yml.append("    org.fractalx.registry: INFO\n");

        Files.writeString(srcMainResources.resolve("application.yml"), yml.toString());
        log.debug("Generated registry application.yml");
    }
}
