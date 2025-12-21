package com.fractalx.core.gateway;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Gateway generator with discovery awareness
 */
public class DiscoveryAwareGatewayGenerator extends GatewayConfigGenerator {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryAwareGatewayGenerator.class);

    @Override
    public void generateConfig(Path srcMainResources,
                               List<FractalModule> modules,
                               List<RouteDefinition> routes) throws IOException {
        log.debug("Generating discovery-aware gateway configuration");

        // Generate standard configuration
        super.generateConfig(srcMainResources, modules, routes);

        // Generate discovery-specific configuration
        generateDiscoveryGatewayConfig(srcMainResources, modules);
    }

    private void generateDiscoveryGatewayConfig(Path srcMainResources, List<FractalModule> modules) throws IOException {
        String gatewayDiscoveryYml = generateGatewayDiscoveryYml(modules);
        Path discoveryPath = srcMainResources.resolve("application-discovery.yml");
        Files.writeString(discoveryPath, gatewayDiscoveryYml);

        log.info("✓ Generated discovery-aware gateway configuration");
    }

    private String generateGatewayDiscoveryYml(List<FractalModule> modules) {
        StringBuilder yml = new StringBuilder();

        yml.append("# FractalX Gateway Discovery Configuration\n\n");

        yml.append("fractalx:\n");
        yml.append("  discovery:\n");
        yml.append("    enabled: true\n");
        yml.append("    mode: HYBRID\n");
        yml.append("    # Gateway acts as discovery client\n");
        yml.append("    client-only: true\n\n");

        yml.append("spring:\n");
        yml.append("  cloud:\n");
        yml.append("    gateway:\n");
        yml.append("      discovery:\n");
        yml.append("        locator:\n");
        yml.append("          enabled: true\n");
        yml.append("          lower-case-service-id: true\n\n");

        yml.append("# Static service routes for discovery\n");
        yml.append("discovery:\n");
        yml.append("  static-routes:\n");

        for (FractalModule module : modules) {
            String servicePath = module.getServiceName().replace("-service", "s");
            yml.append("    - service: ").append(module.getServiceName()).append("\n");
            yml.append("      path: /api/").append(servicePath).append("/**\n");
            yml.append("      predicates:\n");
            yml.append("        - Path=/api/").append(servicePath).append("/**\n");
            yml.append("      filters:\n");
            yml.append("        - StripPrefix=1\n");
            yml.append("      uri: lb://").append(module.getServiceName()).append("\n\n");
        }

        return yml.toString();
    }
}