package com.fractalx.core.gateway;

import com.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes controllers to extract API endpoints for gateway routing
 */
public class GatewayAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(GatewayAnalyzer.class);

    /**
     * Analyze a service directory to extract all API routes
     */
    public List<RouteDefinition> analyzeServiceRoutes(Path serviceSrcDir, FractalModule module) throws IOException {
        List<RouteDefinition> routes = new ArrayList<>();

        // Simplified version for now - just create basic routes
        routes.add(new RouteDefinition(module.getServiceName(), "/api/**", "GET", module.getPort()));
        routes.add(new RouteDefinition(module.getServiceName(), "/api/**", "POST", module.getPort()));
        routes.add(new RouteDefinition(module.getServiceName(), "/api/**", "PUT", module.getPort()));
        routes.add(new RouteDefinition(module.getServiceName(), "/api/**", "DELETE", module.getPort()));
        routes.add(new RouteDefinition(module.getServiceName(), "/actuator/health", "GET", module.getPort()));

        log.info("Created {} basic routes for {}", routes.size(), module.getServiceName());
        return routes;
    }
}