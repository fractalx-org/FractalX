package org.fractalx.core.generator.observability;

import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.fractalx.core.generator.service.NetScopeClientGenerator;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Pipeline step that generates a {@code ServiceHealthConfig} class for services
 * that have cross-module NetScope dependencies.
 *
 * <p>The generated class registers one Spring Boot {@link HealthIndicator} per dependency.
 * Each indicator attempts a TCP connection to the peer's gRPC port within a 2-second
 * timeout; it reports {@code UP} on success or {@code DOWN} with the exception message
 * on failure.
 *
 * <p>Additionally, a {@link MeterBinder} registers a Micrometer gauge
 * {@code fractalx.service.dependency.up{service=<name>}} (1 = UP, 0 = DOWN).
 *
 * <p>The step is skipped for services with no dependencies.
 */
public class HealthMetricsStep implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(HealthMetricsStep.class);

    @Override
    public void generate(GenerationContext context) throws IOException {
        FractalModule module = context.getModule();
        List<String>  deps   = module.getDependencies();

        if (deps.isEmpty()) {
            log.debug("Skipping HealthMetricsStep for {} — no dependencies", module.getServiceName());
            return;
        }
        log.debug("Generating ServiceHealthConfig for {}", module.getServiceName());

        String pkg     = "org.fractalx.generated." + toJavaId(module.getServiceName()).toLowerCase();
        Path   pkgPath = resolvePackage(context.getSrcMainJava(), pkg);

        Files.writeString(pkgPath.resolve("ServiceHealthConfig.java"),
                buildContent(pkg, deps, context.getAllModules()));
    }

    private String buildContent(String pkg, List<String> deps, List<FractalModule> allModules) {
        StringBuilder indicators = new StringBuilder();
        StringBuilder gauges     = new StringBuilder();

        for (String beanType : deps) {
            String svcName  = NetScopeClientGenerator.beanTypeToServiceName(beanType);
            String beanId   = toCamelCase(svcName);
            int    grpcPort = allModules.stream()
                    .filter(m -> svcName.equals(m.getServiceName()))
                    .findFirst().map(m -> m.getPort() + 10_000).orElse(18_080);
            String propPfx  = "netscope.client.servers." + svcName;

            indicators.append("""

                        /** Health indicator for the "%s" NetScope peer (gRPC on port %d). */
                        @Bean
                        public HealthIndicator %sHealthIndicator(
                                @Value("${%s.host:localhost}") String host,
                                @Value("${%s.port:%d}")        int    port) {
                            return () -> {
                                try (java.net.Socket s = new java.net.Socket()) {
                                    s.connect(new java.net.InetSocketAddress(host, port), 2_000);
                                    return Health.up()
                                            .withDetail("service",  "%s")
                                            .withDetail("grpcPort", port)
                                            .build();
                                } catch (Exception e) {
                                    return Health.down()
                                            .withDetail("service", "%s")
                                            .withDetail("error",   e.getMessage())
                                            .build();
                                }
                            };
                        }
                    """.formatted(svcName, grpcPort, beanId, propPfx, propPfx, grpcPort, svcName, svcName));

            gauges.append("""

                        /** Micrometer gauge: fractalx.service.dependency.up{service="%s"} */
                        @Bean
                        public MeterBinder %sDependencyGauge(
                                HealthIndicator %sHealthIndicator) {
                            return registry -> Gauge.builder(
                                    "fractalx.service.dependency.up",
                                    %sHealthIndicator,
                                    hi -> "UP".equals(hi.health().getStatus().getCode()) ? 1.0 : 0.0)
                                    .tag("service", "%s")
                                    .description("1 if the %s dependency is reachable, 0 otherwise")
                                    .register(registry);
                        }
                    """.formatted(svcName, beanId, beanId, beanId, svcName, svcName));
        }

        return """
                package %s;

                import io.micrometer.core.instrument.Gauge;
                import io.micrometer.core.instrument.MeterRegistry;
                import io.micrometer.core.instrument.binder.MeterBinder;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.boot.actuate.health.Health;
                import org.springframework.boot.actuate.health.HealthIndicator;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                /**
                 * Registers custom health indicators and Micrometer gauges for each
                 * NetScope peer dependency of this service. Exposed via
                 * {@code GET /actuator/health} (show-details: always).
                 */
                @Configuration
                public class ServiceHealthConfig {
                    %s
                    %s
                }
                """.formatted(pkg, indicators.toString(), gauges.toString());
    }

    private Path resolvePackage(Path base, String pkg) throws IOException {
        Path p = base;
        for (String part : pkg.split("\\.")) p = p.resolve(part);
        Files.createDirectories(p);
        return p;
    }

    private String toJavaId(String serviceName) {
        StringBuilder sb = new StringBuilder();
        for (String part : serviceName.split("-")) {
            if (!part.isEmpty())
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private String toCamelCase(String name) {
        String[] parts = name.split("-");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty())
                sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }
}
