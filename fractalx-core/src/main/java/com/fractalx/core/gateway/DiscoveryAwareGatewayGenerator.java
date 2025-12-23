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

        // Generate discovery service configuration
        generateDiscoveryServiceConfig(srcMainResources);
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
        yml.append("    client-only: true\n");
        yml.append("    registry-url: http://localhost:8761\n\n");

        yml.append("spring:\n");
        yml.append("  application:\n");
        yml.append("    name: fractalx-gateway\n");
        yml.append("  cloud:\n");
        yml.append("    gateway:\n");
        yml.append("      discovery:\n");
        yml.append("        locator:\n");
        yml.append("          enabled: true\n");
        yml.append("          lower-case-service-id: true\n");
        yml.append("      routes:\n");

        // Generate dynamic routes based on discovery
        for (FractalModule module : modules) {
            String servicePath = module.getServiceName().replace("-service", "");
            yml.append("        # Dynamic route for ").append(module.getServiceName()).append("\n");
            yml.append("        - id: ").append(module.getServiceName()).append("\n");
            yml.append("          uri: lb://").append(module.getServiceName()).append("\n");
            yml.append("          predicates:\n");
            yml.append("            - Path=/api/").append(servicePath).append("/**\n");
            yml.append("          filters:\n");
            yml.append("            - StripPrefix=1\n");
            yml.append("            - name: Retry\n");
            yml.append("              args:\n");
            yml.append("                retries: 3\n");
            yml.append("                statuses: BAD_GATEWAY,SERVICE_UNAVAILABLE\n\n");
        }

        // Static fallback routes
        yml.append("        # Static fallback routes\n");
        for (FractalModule module : modules) {
            String servicePath = module.getServiceName().replace("-service", "");
            yml.append("        - id: ").append(module.getServiceName()).append("-static\n");
            yml.append("          uri: http://localhost:").append(module.getPort()).append("\n");
            yml.append("          predicates:\n");
            yml.append("            - Path=/api/").append(servicePath).append("/**\n");
            yml.append("          filters:\n");
            yml.append("            - StripPrefix=1\n\n");
        }

        yml.append("# Discovery Service Health Check\n");
        yml.append("management:\n");
        yml.append("  endpoints:\n");
        yml.append("    web:\n");
        yml.append("      exposure:\n");
        yml.append("        include: health,gateway,routes\n");
        yml.append("  health:\n");
        yml.append("    discovery:\n");
        yml.append("      enabled: true\n");

        return yml.toString();
    }

    private void generateDiscoveryServiceConfig(Path srcMainResources) throws IOException {
        String discoveryServiceYml = """
            # Discovery Service Configuration
            spring:
              application:
                name: fractalx-discovery-service
            
            server:
              port: 8761
            
            fractalx:
              discovery:
                enabled: true
                mode: DYNAMIC
                host: localhost
                port: 8761
                heartbeat-interval: 30000
                instance-ttl: 90000
                auto-cleanup: true
            
            # Static service configuration (for initial bootstrap)
            discovery:
              static-config:
                enabled: true
                file: classpath:static-services.yml
            
            management:
              endpoints:
                web:
                  exposure:
                    include: health,info,metrics,discovery
              endpoint:
                health:
                  show-details: always
            
            logging:
              level:
                com.fractalx.core.discovery: DEBUG
                org.springframework.cloud.gateway: INFO
            """;

        Path discoveryServicePath = srcMainResources.resolve("discovery-service.yml");
        Files.writeString(discoveryServicePath, discoveryServiceYml);

        // Generate static services configuration
        generateStaticServicesConfig(srcMainResources);
    }

    private void generateStaticServicesConfig(Path srcMainResources) throws IOException {
        String staticServicesYml = """
            # Static Services Configuration
            # This file is used for initial service discovery bootstrap
            
            instances:
              # Discovery service itself
              discovery-service:
                - host: localhost
                  port: 8761
            
              # API Gateway
              fractalx-gateway:
                - host: localhost
                  port: 9999
            
            # Service metadata
            metadata:
              environment: generated
              framework: fractalx
              version: 1.0.0
            """;

        Path staticServicesPath = srcMainResources.resolve("static-services.yml");
        Files.writeString(staticServicesPath, staticServicesYml);

        log.info("✓ Generated static services configuration");
    }
}