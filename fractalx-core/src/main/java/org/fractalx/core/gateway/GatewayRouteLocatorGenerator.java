package org.fractalx.core.gateway;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates a dynamic RouteLocator that pulls live service locations from
 * fractalx-registry and falls back to static YAML routes if the registry
 * is unavailable.
 */
public class GatewayRouteLocatorGenerator {

    private static final Logger log = LoggerFactory.getLogger(GatewayRouteLocatorGenerator.class);

    public void generate(Path srcMainJava, List<FractalModule> modules) throws IOException {
        Path pkg = createPkg(srcMainJava, "org/fractalx/gateway/routing");

        generateDynamicRouteLocator(pkg, modules);
        generateRegistryRouteFetcher(pkg);

        log.info("Generated dynamic route locator");
    }

    private void generateDynamicRouteLocator(Path pkg, List<FractalModule> modules) throws IOException {
        StringBuilder staticRoutesBuilder = new StringBuilder();
        for (FractalModule m : modules) {
            String base = m.getServiceName().replace("-service", "");
            String plural;
            if (base.endsWith("y")) {
                plural = base.substring(0, base.length() - 1) + "ies";
            } else if (base.endsWith("s") || base.endsWith("sh") || base.endsWith("ch")) {
                plural = base + "es";
            } else {
                plural = base + "s";
            }

            staticRoutesBuilder.append("        // ").append(m.getServiceName()).append(" static route\n");
            staticRoutesBuilder.append("        routeBuilder.route(\"").append(m.getServiceName()).append("-static\",\n");
            staticRoutesBuilder.append("                r -> r.path(\"/api/").append(base).append("/**\", \"/api/").append(plural).append("/**\")\n");
            staticRoutesBuilder.append("                        .filters(f -> f.circuitBreaker(c -> c.setName(\"").append(m.getServiceName()).append("\")\n");
            staticRoutesBuilder.append("                                .setFallbackUri(\"forward:/fallback/").append(m.getServiceName()).append("\")))\n");
            staticRoutesBuilder.append("                        .uri(\"http://localhost:").append(m.getPort()).append("\"));\n\n");
        }

        String content = """
                package org.fractalx.gateway.routing;

                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.cloud.gateway.route.Route;
                import org.springframework.cloud.gateway.route.RouteLocator;
                import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import reactor.core.publisher.Flux;

                import java.util.ArrayList;
                import java.util.List;

                @Configuration
                public class DynamicRouteLocatorConfig {

                    private static final Logger log = LoggerFactory.getLogger(DynamicRouteLocatorConfig.class);

                    private final RegistryRouteFetcher registryFetcher;
                    private final RouteLocatorBuilder builder;

                    public DynamicRouteLocatorConfig(RegistryRouteFetcher registryFetcher,
                                                     RouteLocatorBuilder builder) {
                        this.registryFetcher = registryFetcher;
                        this.builder = builder;
                    }

                    @Bean
                    public RouteLocator dynamicRouteLocator() {
                        return () -> {
                            // Try to fetch live routes from registry first
                            try {
                                List<Route> liveRoutes = registryFetcher.fetchRoutes(builder);
                                if (!liveRoutes.isEmpty()) {
                                    log.info("Using {} live routes from fractalx-registry", liveRoutes.size());
                                    return Flux.fromIterable(liveRoutes);
                                }
                                log.warn("No live routes available from registry");
                            } catch (Exception e) {
                                log.warn("Could not fetch routes from registry: {}", e.getMessage());
                            }

                            // Fallback to static routes
                            log.info("Using static fallback routes");
                            return Flux.fromIterable(buildStaticRoutes());
                        };
                    }

                    private List<Route> buildStaticRoutes() {
                        List<Route> routes = new ArrayList<>();
                        RouteLocatorBuilder.Builder routeBuilder = builder.routes();
                %s
                        routeBuilder.build().getRoutes().subscribe(routes::add);
                        log.debug("Built {} static fallback routes", routes.size());
                        return routes;
                    }
                }
                """.formatted(staticRoutesBuilder.toString());

        Files.writeString(pkg.resolve("DynamicRouteLocatorConfig.java"), content);
    }

    private void generateRegistryRouteFetcher(Path pkg) throws IOException {
        String content = """
                package org.fractalx.gateway.routing;

                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.cloud.gateway.route.Route;
                import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
                import org.springframework.stereotype.Component;
                import org.springframework.web.client.ResourceAccessException;
                import org.springframework.web.client.HttpClientErrorException;
                import org.springframework.web.client.RestTemplate;

                import java.util.ArrayList;
                import java.util.List;
                import java.util.Map;

                @Component
                public class RegistryRouteFetcher {

                    private static final Logger log = LoggerFactory.getLogger(RegistryRouteFetcher.class);

                    @Value("${fractalx.registry.url:http://localhost:8761}")
                    private String registryUrl;

                    private final RestTemplate restTemplate = new RestTemplate();

                    @SuppressWarnings("unchecked")
                    public List<Route> fetchRoutes(RouteLocatorBuilder builder) {
                        List<Route> routes = new ArrayList<>();
                        try {
                            log.debug("Fetching routes from registry at {}", registryUrl);
                            
                            List<Map<String, Object>> services =
                                    restTemplate.getForObject(registryUrl + "/services", List.class);
                                    
                            if (services == null || services.isEmpty()) {
                                log.debug("No services found in registry");
                                return routes;
                            }
                            
                            RouteLocatorBuilder.Builder routeBuilder = builder.routes();
                            
                            for (Map<String, Object> svc : services) {

                                if (!"UP".equals(svc.get("status"))) {
                                    log.debug("Skipping service {} - status: {}",
                                        svc.get("name"), svc.get("status"));
                                    continue;
                                }
                                
                                String name = (String) svc.get("name");
                                String host = (String) svc.get("host");
                                int port = ((Number) svc.get("port")).intValue();
                                String base = toPathBase(name);
                                String plural = toPathPlural(name);
                                String uri = "http://" + host + ":" + port;

                                routeBuilder.route(name + "-live",
                                    r -> r.path("/api/" + base + "/**", "/api/" + plural + "/**")
                                        .filters(f -> f.circuitBreaker(c -> c.setName(name)
                                            .setFallbackUri("forward:/fallback/" + name)))
                                        .uri(uri));

                                log.info("Live routes added: /api/{}/** and /api/{}/** -> {}", base, plural, uri);
                            }
                            
                            routeBuilder.build().getRoutes().subscribe(routes::add);
                            
                        } catch (ResourceAccessException e) {
                            log.warn("Cannot connect to registry at {}: {}", registryUrl, e.getMessage());
                        } catch (HttpClientErrorException e) {
                            log.warn("Registry returned error: {}", e.getMessage());
                        } catch (Exception e) {
                            log.warn("Could not fetch routes from registry: {}", e.getMessage());
                        }
                        return routes;
                    }

                    private String toPathBase(String serviceName) {
                        return serviceName.replace("-service", "");
                    }

                    private String toPathPlural(String serviceName) {
                        String path = serviceName.replace("-service", "");
                        if (path.endsWith("y")) {
                            return path.substring(0, path.length() - 1) + "ies";
                        } else if (path.endsWith("s") || path.endsWith("sh") || path.endsWith("ch")) {
                            return path + "es";
                        } else {
                            return path + "s";
                        }
                    }
                }
                """;

        Files.writeString(pkg.resolve("RegistryRouteFetcher.java"), content);
    }

    private Path createPkg(Path base, String pkg) throws IOException {
        Path p = base;
        for (String part : pkg.split("/")) p = p.resolve(part);
        Files.createDirectories(p);
        return p;
    }
}
