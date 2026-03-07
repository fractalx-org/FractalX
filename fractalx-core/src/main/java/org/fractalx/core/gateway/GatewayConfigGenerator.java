package org.fractalx.core.gateway;

import org.fractalx.core.config.FractalxConfig;
import org.fractalx.core.model.FractalModule;
import org.fractalx.core.gateway.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
        generateConfig(srcMainResources, modules, routes, FractalxConfig.defaults());
    }

    public void generateConfig(Path srcMainResources,
                               List<FractalModule> modules,
                               List<RouteDefinition> routes,
                               FractalxConfig cfg) throws IOException {
        log.debug("Generating gateway configuration");
        Files.writeString(srcMainResources.resolve("application.yml"),
                generateApplicationYml(modules, cfg));
        log.info("✓ Generated gateway configuration");
    }

    private String generateApplicationYml(List<FractalModule> modules, FractalxConfig cfg) {
        if (modules == null || modules.isEmpty()) {
            log.warn("No modules provided for gateway configuration");
            modules = new ArrayList<>(); // Use empty list to avoid NPE
        }

        StringBuilder routesConfig = new StringBuilder();

        for (FractalModule module : modules) {
            routesConfig.append(generateServiceRoute(module));
        }

        // Build the YAML step by step for better control
        StringBuilder ymlBuilder = new StringBuilder();

        ymlBuilder.append("server:\n");
        ymlBuilder.append("  port: ").append(cfg.gatewayPort()).append("\n\n");

        ymlBuilder.append("spring:\n");
        ymlBuilder.append("  application:\n");
        ymlBuilder.append("    name: fractalx-gateway\n");
        ymlBuilder.append("  main:\n");
        ymlBuilder.append("    web-application-type: reactive\n\n");
        ymlBuilder.append("  cloud:\n");
        ymlBuilder.append("    gateway:\n");
        ymlBuilder.append("      default-filters:\n");
        ymlBuilder.append("        - name: Retry\n");
        ymlBuilder.append("          args:\n");
        ymlBuilder.append("            retries: 2\n");
        ymlBuilder.append("            statuses: BAD_GATEWAY,SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT\n");
        ymlBuilder.append("      routes:\n");
        ymlBuilder.append(routesConfig.toString());
        ymlBuilder.append("\n");

        ymlBuilder.append("fractalx:\n");
        ymlBuilder.append("  registry:\n");
        ymlBuilder.append("    url: ${FRACTALX_REGISTRY_URL:").append(cfg.registryUrl()).append("}\n");
        ymlBuilder.append("  observability:\n");
        ymlBuilder.append("    otel:\n");
        ymlBuilder.append("      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}\n");
        ymlBuilder.append("  gateway:\n");
        ymlBuilder.append("    security:\n");
        ymlBuilder.append("      # Set enabled: true and configure one or more auth mechanisms below\n");
        ymlBuilder.append("      enabled: ${GATEWAY_SECURITY_ENABLED:false}\n");
        ymlBuilder.append("      public-paths: /api/*/public/**, /api/*/auth/**\n");
        ymlBuilder.append("      bearer:\n");
        ymlBuilder.append("        enabled: ${GATEWAY_BEARER_ENABLED:false}\n");
        ymlBuilder.append("        jwt-secret: ${JWT_SECRET:fractalx-default-secret-change-in-prod-min-32chars!!}\n");
        ymlBuilder.append("      oauth2:\n");
        ymlBuilder.append("        enabled: ${GATEWAY_OAUTH2_ENABLED:false}\n");
        ymlBuilder.append("        jwk-set-uri: ${OAUTH2_JWK_URI:").append(cfg.oauth2JwksUri()).append("}\n");
        ymlBuilder.append("      basic:\n");
        ymlBuilder.append("        enabled: ${GATEWAY_BASIC_ENABLED:false}\n");
        ymlBuilder.append("        username: ${GATEWAY_BASIC_USER:fractalx}\n");
        ymlBuilder.append("        password: ${GATEWAY_BASIC_PASS:changeme}\n");
        ymlBuilder.append("      api-key:\n");
        ymlBuilder.append("        enabled: ${GATEWAY_APIKEY_ENABLED:false}\n");
        ymlBuilder.append("        valid-keys:\n");
        ymlBuilder.append("          - ${GATEWAY_API_KEY_1:dev-key-replace-me}\n");
        ymlBuilder.append("    cors:\n");
        ymlBuilder.append("      allowed-origins: ${CORS_ORIGINS:").append(cfg.corsAllowedOrigins()).append("}\n");
        ymlBuilder.append("      allowed-methods: GET,POST,PUT,DELETE,PATCH,OPTIONS\n");
        ymlBuilder.append("      allow-credentials: true\n");
        ymlBuilder.append("    rate-limit:\n");
        ymlBuilder.append("      default-rps: ${GATEWAY_DEFAULT_RPS:100}\n");
        ymlBuilder.append("\n");

        ymlBuilder.append("management:\n");
        ymlBuilder.append("  endpoints:\n");
        ymlBuilder.append("    web:\n");
        ymlBuilder.append("      exposure:\n");
        ymlBuilder.append("        include: health,info,gateway,routes,metrics\n");
        ymlBuilder.append("  endpoint:\n");
        ymlBuilder.append("    gateway:\n");
        ymlBuilder.append("      enabled: true\n");
        ymlBuilder.append("    health:\n");
        ymlBuilder.append("      show-details: always\n");
        ymlBuilder.append("  tracing:\n");
        ymlBuilder.append("    sampling:\n");
        ymlBuilder.append("      probability: 1.0\n");
        ymlBuilder.append("\n");

        ymlBuilder.append("logging:\n");
        ymlBuilder.append("  level:\n");
        ymlBuilder.append("    org.springframework.cloud.gateway: INFO\n");
        ymlBuilder.append("    org.fractalx.gateway: INFO\n");
        ymlBuilder.append("    reactor.netty: DEBUG\n");
        ymlBuilder.append("    com.netflix.eureka: OFF\n");
        ymlBuilder.append("    com.netflix.discovery: OFF\n");

        return ymlBuilder.toString();
    }

    private String generateServiceRoute(FractalModule module) {
        // Handle port conflicts
        int servicePort = resolvePortConflict(module.getPort(), module.getServiceName());

        // Extract base path (e.g. inventory-service -> inventory)
        String serviceName = module.getServiceName();
        String baseName = serviceName.replace("-service", "");

        // Support both singular and simple plural (e.g. /api/inventory and /api/inventorys).
        // While 'inventorys' isn't grammatically correct, it's what the simple pluralizer produced.
        // Matching both ensures backward compatibility and fixes 404s for singular calls.
        String pluralName = baseName.endsWith("s") ? baseName : baseName + "s";
        String pathPattern = baseName.equals(pluralName)
                ? "/api/" + baseName + "/**"
                : "/api/" + baseName + "/**,/api/" + pluralName + "/**";

        StringBuilder route = new StringBuilder();
        route.append("        # ").append(module.getServiceName()).append(" Service\n");
        route.append("        - id: ").append(module.getServiceName()).append("-service\n");
        route.append("          uri: http://localhost:").append(servicePort).append("\n");
        route.append("          predicates:\n");
        route.append("            - Path=").append(pathPattern).append("\n");
        route.append("          filters:\n");
        route.append("            - StripPrefix=0\n");
        route.append("            - name: CircuitBreaker\n");
        route.append("              args:\n");
        route.append("                name: ").append(module.getServiceName()).append("\n");
        route.append("                fallbackUri: forward:/fallback/").append(module.getServiceName()).append("\n");
        // Rate limiting is handled globally by RateLimitFilter (GlobalFilter bean) —
        // do NOT add RequestRateLimiter here; that factory requires Redis and is not registered.

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