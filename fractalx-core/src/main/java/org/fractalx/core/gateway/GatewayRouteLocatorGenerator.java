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
        StringBuilder staticFallbacks = new StringBuilder();
        for (FractalModule m : modules) {
            String path = m.getServiceName().replace("-service", "");
            if (!path.endsWith("s")) path += "s";
            staticFallbacks.append("""
                            builder.routes()
                                .route("%s-static", r -> r.path("/api/%s/**")
                                    .uri("http://localhost:%d"))
                                .build().getRoutes().toIterable().forEach(routes::add);
                    """.formatted(m.getServiceName(), path, m.getPort()));
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
                            List<Route> routes = new ArrayList<>();
                            List<Route> liveRoutes = registryFetcher.fetchRoutes(builder);
                            if (!liveRoutes.isEmpty()) {
                                log.debug("Using {} live routes from fractalx-registry", liveRoutes.size());
                                routes.addAll(liveRoutes);
                            } else {
                                log.warn("fractalx-registry unavailable — falling back to static routes");
                                %s
                            }
                            return Flux.fromIterable(routes);
                        };
                    }
                }
                """.formatted(staticFallbacks.toString());

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
                            List<Map<String, Object>> services =
                                    restTemplate.getForObject(registryUrl + "/services", List.class);
                            if (services == null) return routes;
                            for (Map<String, Object> svc : services) {
                                if (!"UP".equals(svc.get("status"))) continue;
                                String name   = (String) svc.get("name");
                                String host   = (String) svc.get("host");
                                int    port   = ((Number) svc.get("port")).intValue();
                                String prefix = toPathPrefix(name);
                                String uri    = "http://" + host + ":" + port;
                                builder.routes()
                                        .route(name, r -> r.path("/api/" + prefix + "/**").uri(uri))
                                        .build()
                                        .getRoutes()
                                        .toIterable()
                                        .forEach(routes::add);
                                log.debug("Live route: /api/{}/** -> {}", prefix, uri);
                            }
                        } catch (Exception e) {
                            log.warn("Could not fetch routes from registry: {}", e.getMessage());
                        }
                        return routes;
                    }

                    private String toPathPrefix(String serviceName) {
                        String path = serviceName.replace("-service", "");
                        return path.endsWith("s") ? path : path + "s";
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
