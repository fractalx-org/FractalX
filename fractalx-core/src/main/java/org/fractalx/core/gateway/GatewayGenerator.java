package org.fractalx.core.gateway;

import org.fractalx.core.config.FractalxConfig;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// New generators wired in below

/**
 * Main generator for API Gateway project
 */
public class GatewayGenerator {
    private static final Logger log = LoggerFactory.getLogger(GatewayGenerator.class);

    private final Path sourceRoot;
    private final Path outputRoot;
    private final FractalxConfig fractalxConfig;

    public GatewayGenerator(Path sourceRoot, Path outputRoot) {
        this(sourceRoot, outputRoot, FractalxConfig.defaults());
    }

    public GatewayGenerator(Path sourceRoot, Path outputRoot, FractalxConfig fractalxConfig) {
        this.sourceRoot = sourceRoot;
        this.outputRoot = outputRoot;
        this.fractalxConfig = fractalxConfig;
    }

    /**
     * Generate API Gateway for all services
     */
    public void generateGateway(List<FractalModule> modules) throws IOException {
        log.info("Generating API Gateway for {} services", modules.size());

        // Create gateway directory
        Path gatewayRoot = outputRoot.resolve("fractalx-gateway");
        Path srcMainJava = gatewayRoot.resolve("src/main/java");
        Path srcMainResources = gatewayRoot.resolve("src/main/resources");

        Files.createDirectories(srcMainJava);
        Files.createDirectories(srcMainResources);

        // Step 1: Collect basic routes from all services
        List<RouteDefinition> allRoutes = new ArrayList<>();
        GatewayAnalyzer analyzer = new GatewayAnalyzer();

        for (FractalModule module : modules) {
            Path serviceSrcDir = sourceRoot.resolve(module.getPackageName().replace('.', '/'));
            if (Files.exists(serviceSrcDir)) {
                List<RouteDefinition> routes = analyzer.analyzeServiceRoutes(serviceSrcDir, module);
                allRoutes.addAll(routes);
                log.info("Found {} routes in {}", routes.size(), module.getServiceName());
            }
        }

        log.info("Total routes discovered: {}", allRoutes.size());

        // Step 2: Generate POM
        GatewayPomGenerator pomGen = new GatewayPomGenerator();
        pomGen.generatePom(gatewayRoot, modules);

        // Step 3: Generate Application class
        GatewayApplicationGenerator appGen = new GatewayApplicationGenerator();
        appGen.generateApplicationClass(srcMainJava);

        // Step 4: Detect monolith security configuration
        SecurityProfile securityProfile = new SecurityAnalyzer().analyze(
                sourceRoot, sourceRoot.resolve("src/main/resources"));
        log.info("Monolith security profile — authType={} rules={}",
                securityProfile.authType(), securityProfile.routeRules().size());

        // Step 5: Generate configuration (security profile drives YAML defaults + resilience4j)
        GatewayConfigGenerator configGen = new GatewayConfigGenerator();
        configGen.generateConfig(srcMainResources, modules, allRoutes, fractalxConfig, securityProfile);

        // Step 6: Generate documentation
        ApiDocumentationGenerator docGen = new ApiDocumentationGenerator();
        docGen.generateDocumentation(gatewayRoot, modules, allRoutes);

        // Step 7: Dynamic route locator (registry-backed with static fallback)
        new GatewayRouteLocatorGenerator().generate(srcMainJava, modules);

        // Step 8: Multi-mechanism security — mirrors monolith auth type and route rules
        new GatewaySecurityGenerator().generate(srcMainJava, modules, securityProfile);

        // Step 9: Gateway-level circuit breakers + fallback controller
        new GatewayCircuitBreakerGenerator().generate(srcMainJava, modules);

        // Step 10: In-memory rate limiter
        new GatewayRateLimiterGenerator().generate(srcMainJava, modules);

        // Step 11: Global CORS filter
        new GatewayCorsGenerator().generate(srcMainJava);

        // Step 12: Request tracing + structured logging + metrics filter
        new GatewayObservabilityGenerator().generate(srcMainJava, modules);

        // Step 13: OpenAPI 3.0.3 spec + Postman Collection v2.1 (with inline tests)
        new GatewayOpenApiGenerator().generate(gatewayRoot, modules);

        log.info("✓ API Gateway generated at: {}", gatewayRoot.toAbsolutePath());
    }
}