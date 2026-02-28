package org.fractalx.core.gateway;

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

    public GatewayGenerator(Path sourceRoot, Path outputRoot) {
        this.sourceRoot = sourceRoot;
        this.outputRoot = outputRoot;
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

        // Step 4: Generate configuration
        GatewayConfigGenerator configGen = new GatewayConfigGenerator();
        configGen.generateConfig(srcMainResources, modules, allRoutes);

        // Step 5: Generate documentation
        ApiDocumentationGenerator docGen = new ApiDocumentationGenerator();
        docGen.generateDocumentation(gatewayRoot, modules, allRoutes);

        // Step 6: Dynamic route locator (registry-backed with static fallback)
        new GatewayRouteLocatorGenerator().generate(srcMainJava, modules);

        // Step 7: Multi-mechanism security (OAuth2 / Bearer / Basic / API-Key)
        new GatewaySecurityGenerator().generate(srcMainJava, modules);

        // Step 8: Gateway-level circuit breakers + fallback controller
        new GatewayCircuitBreakerGenerator().generate(srcMainJava, modules);

        // Step 9: In-memory rate limiter
        new GatewayRateLimiterGenerator().generate(srcMainJava, modules);

        // Step 10: Global CORS filter
        new GatewayCorsGenerator().generate(srcMainJava);

        // Step 11: Request tracing + structured logging + metrics filter
        new GatewayObservabilityGenerator().generate(srcMainJava, modules);

        log.info("✓ API Gateway generated at: {}", gatewayRoot.toAbsolutePath());
    }
}