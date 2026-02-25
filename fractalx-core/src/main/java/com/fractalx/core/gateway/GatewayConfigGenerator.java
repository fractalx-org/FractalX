package com.fractalx.core.gateway;

import com.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates Spring Cloud Gateway configuration
 */
public class GatewayConfigGenerator {
    private static final Logger log = LoggerFactory.getLogger(GatewayConfigGenerator.class);
    private static final int GATEWAY_PORT = 9999;

    // Track used ports to avoid conflicts
    private final Map<Integer, Boolean> usedPorts = new HashMap<>();

    public GatewayConfigGenerator() {
        // Reserve gateway port
        usedPorts.put(GATEWAY_PORT, true);
    }

    public void generateConfig(Path srcMainResources,
                               List<FractalModule> modules,
                               List<RouteDefinition> routes) throws IOException {
        log.debug("Generating gateway configuration");

        // Generate application.yml
        String ymlContent = generateApplicationYml(modules);
        Path ymlPath = srcMainResources.resolve("application.yml");
        Files.writeString(ymlPath, ymlContent);

        log.info("✓ Generated gateway configuration");
    }

    private String generateApplicationYml(List<FractalModule> modules) {
        StringBuilder routesConfig = new StringBuilder();

        for (FractalModule module : modules) {
            routesConfig.append(generateServiceRoute(module));
        }

        // Build the YAML step by step for better control
        StringBuilder ymlBuilder = new StringBuilder();

        ymlBuilder.append("server:\n");
        ymlBuilder.append("  port: ").append(GATEWAY_PORT).append("\n\n");

        ymlBuilder.append("spring:\n");
        ymlBuilder.append("  application:\n");
        ymlBuilder.append("    name: fractalx-gateway\n");
        ymlBuilder.append("  main:\n");
        ymlBuilder.append("    web-application-type: reactive\n\n");
        ymlBuilder.append("  cloud:\n");
        ymlBuilder.append("    gateway:\n");
        ymlBuilder.append("      routes:\n");
        ymlBuilder.append(routesConfig.toString());
        ymlBuilder.append("\n");

        // REMOVED ALL EUREKA CONFIGURATION
        // No Eureka client configuration at all

        ymlBuilder.append("# Simple logging\n");
        ymlBuilder.append("logging:\n");
        ymlBuilder.append("  level:\n");
        ymlBuilder.append("    org.springframework.cloud.gateway: INFO\n");
        ymlBuilder.append("    com.fractalx.gateway: INFO\n");
        ymlBuilder.append("    com.netflix.eureka: OFF\n");  // Turn off Eureka logs
        ymlBuilder.append("    com.netflix.discovery: OFF\n");

        return ymlBuilder.toString();
    }

    private String generateServiceRoute(FractalModule module) {
        // Handle port conflicts
        int servicePort = resolvePortConflict(module.getPort(), module.getServiceName());

        // Only remove "-service" suffix and handle pluralization
        String serviceName = module.getServiceName();
        String servicePath = serviceName.replace("-service", "");

        // Convert to plural if it's not already plural
        // For common cases like order->orders, payment->payments
        if (!servicePath.endsWith("s")) {
            servicePath = servicePath + "s"; // Simple pluralization
        }

        StringBuilder route = new StringBuilder();
        route.append("        # ").append(module.getServiceName()).append(" Service\n");
        route.append("        - id: ").append(module.getServiceName()).append("-service\n");
        route.append("          uri: http://localhost:").append(servicePort).append("\n");
        route.append("          predicates:\n");
        route.append("            - Path=/api/").append(servicePath).append("/**\n");
        route.append("          filters:\n");
        route.append("            - StripPrefix=0\n");

        return route.toString();
    }

    private int resolvePortConflict(int requestedPort, String serviceName) {
        int finalPort = requestedPort;

        // Check if port is already used (skip if it's the service's own port)
        while (usedPorts.containsKey(finalPort) && finalPort != GATEWAY_PORT) {
            log.warn("Port {} is already in use for service {}. Trying next port.",
                    finalPort, serviceName);
            finalPort++;
        }

        // Reserve this port (if it's not the gateway port)
        if (finalPort != GATEWAY_PORT) {
            usedPorts.put(finalPort, true);
        }

        if (finalPort != requestedPort) {
            log.info("Service {} will use port {} instead of {}",
                    serviceName, finalPort, requestedPort);
        }

        return finalPort;
    }
}