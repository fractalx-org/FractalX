package org.fractalx.core.gateway;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates three gateway observability filters:
 * <ol>
 *   <li>{@code TracingFilter}        (order -100) — sets X-Correlation-Id first so downstream filters can log it</li>
 *   <li>{@code RequestLoggingFilter} (order -99)  — structured ingress/egress logging (reads correlation ID set above)</li>
 *   <li>{@code GatewayMetricsFilter} (order -98)  — Micrometer counters + timers per service</li>
 * </ol>
 */
public class GatewayObservabilityGenerator {

    private static final Logger log = LoggerFactory.getLogger(GatewayObservabilityGenerator.class);

    public void generate(Path srcMainJava, List<FractalModule> modules) throws IOException {
        generate(srcMainJava, modules, null);
    }

    public void generate(Path srcMainJava, List<FractalModule> modules, String springBootVersion) throws IOException {
        Path pkg = createPkg(srcMainJava, "org/fractalx/gateway/observability");

        generateLoggingFilter(pkg);
        generateTracingFilter(pkg);
        generateMetricsFilter(pkg, modules);

        boolean isBoot4Plus = isBoot4Plus(springBootVersion);
        if (!isBoot4Plus) {
            generateOtelConfig(pkg);
        } else {
            // OtelConfig uses OTel SDK directly which conflicts with Boot 4.x managed versions.
            // Boot 4.x configures OTLP tracing via management.otlp.tracing.endpoint instead.
            java.nio.file.Path stale = pkg.resolve("GatewayOtelConfig.java");
            if (Files.exists(stale)) Files.delete(stale);
        }
        generateTracingExclusion(pkg);

        log.info("Generated gateway observability filters{}", isBoot4Plus ? "" : " + OtelConfig");
    }

    /** Overload for callers that don't pass modules (backward compat). */
    public void generate(Path srcMainJava) throws IOException {
        generate(srcMainJava, List.of(), null);
    }

    // -------------------------------------------------------------------------

    private void generateTracingFilter(Path pkg) throws IOException {
        String content = """
                package org.fractalx.gateway.observability;

                import io.opentelemetry.api.common.AttributeKey;
                import io.opentelemetry.api.trace.Span;
                import org.springframework.cloud.gateway.filter.GatewayFilterChain;
                import org.springframework.cloud.gateway.filter.GlobalFilter;
                import org.springframework.core.Ordered;
                import org.springframework.stereotype.Component;
                import org.springframework.web.server.ServerWebExchange;
                import reactor.core.publisher.Mono;

                import java.util.UUID;

                /**
                 * Propagates correlation and W3C trace-context headers through the gateway,
                 * and tags the active OTel span with the correlation ID for Jaeger search.
                 * <ul>
                 *   <li>X-Request-Id     — fresh UUID per request</li>
                 *   <li>X-Correlation-Id — propagated from upstream if present, else equals X-Request-Id</li>
                 *   <li>correlation.id   — added as OTel span attribute for Jaeger tag search</li>
                 * </ul>
                 * With micrometer-tracing-bridge-otel on the classpath, Spring Boot auto-configures
                 * traceparent propagation to downstream services via Reactor Netty HTTP client.
                 */
                @Component
                public class TracingFilter implements GlobalFilter, Ordered {

                    @Override
                    public int getOrder() { return -100; }

                    @Override
                    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                        String requestId     = UUID.randomUUID().toString();
                        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
                        if (correlationId == null || correlationId.isBlank()) {
                            correlationId = requestId;
                        }
                        final String finalCorrelationId = correlationId;

                        ServerWebExchange mutated = exchange.mutate()
                                .request(r -> r.headers(h -> {
                                    h.set("X-Request-Id",     requestId);
                                    h.set("X-Correlation-Id", finalCorrelationId);
                                }))
                                .build();

                        // Register response headers to be set before the response is committed
                        mutated.getResponse().beforeCommit(() -> {
                            mutated.getResponse().getHeaders().set("X-Request-Id",     requestId);
                            mutated.getResponse().getHeaders().set("X-Correlation-Id", finalCorrelationId);
                            return Mono.empty();
                        });

                        // Tag the active OTel span with the correlation ID synchronously.
                        // ServerHttpObservationFilter (WebFilter, ordered at MIN_VALUE+1) runs before
                        // all GlobalFilters, so the span is already started when we reach this point.
                        // Span.current() is reliable here on the calling thread.
                        Span span = Span.current();
                        if (span.getSpanContext().isValid()) {
                            span.setAttribute(AttributeKey.stringKey("correlation.id"), finalCorrelationId);
                        }

                        return chain.filter(mutated);
                    }
                }
                """;
        Files.writeString(pkg.resolve("TracingFilter.java"), content);
    }

    private void generateLoggingFilter(Path pkg) throws IOException {
        String content = """
                package org.fractalx.gateway.observability;

                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.cloud.gateway.filter.GatewayFilterChain;
                import org.springframework.cloud.gateway.filter.GlobalFilter;
                import org.springframework.core.Ordered;
                import org.springframework.stereotype.Component;
                import org.springframework.web.server.ServerWebExchange;
                import reactor.core.publisher.Mono;

                /**
                 * Structured request/response logging at the gateway edge.
                 * Logs: method, path, correlationId, response status, and duration in ms.
                 */
                @Component
                public class RequestLoggingFilter implements GlobalFilter, Ordered {

                    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

                    @Override
                    public int getOrder() { return -99; }

                    @Override
                    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                        long   startTime     = System.currentTimeMillis();
                        String method        = exchange.getRequest().getMethod().name();
                        String path          = exchange.getRequest().getURI().getPath();
                        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");

                        log.info("[GATEWAY] --> {} {} correlationId={}", method, path,
                                correlationId != null ? correlationId : "-");

                        return chain.filter(exchange).doFinally(signal -> {
                            long duration = System.currentTimeMillis() - startTime;
                            int  status   = exchange.getResponse().getStatusCode() != null
                                    ? exchange.getResponse().getStatusCode().value() : 0;
                            log.info("[GATEWAY] <-- {} {} status={} duration={}ms correlationId={}",
                                    method, path, status, duration,
                                    correlationId != null ? correlationId : "-");
                        });
                    }
                }
                """;
        Files.writeString(pkg.resolve("RequestLoggingFilter.java"), content);
    }

    private void generateMetricsFilter(Path pkg, List<FractalModule> modules) throws IOException {
        String content = """
                package org.fractalx.gateway.observability;

                import io.micrometer.core.instrument.Counter;
                import io.micrometer.core.instrument.MeterRegistry;
                import io.micrometer.core.instrument.Timer;
                import org.springframework.cloud.gateway.filter.GatewayFilterChain;
                import org.springframework.cloud.gateway.filter.GlobalFilter;
                import org.springframework.core.Ordered;
                import org.springframework.stereotype.Component;
                import org.springframework.web.server.ServerWebExchange;
                import reactor.core.publisher.Mono;

                import java.util.concurrent.TimeUnit;

                /**
                 * Records per-service request counts and latency histograms via Micrometer.
                 * Metrics:
                 * <ul>
                 *   <li>{@code gateway.requests.total{service, method, status}} — counter</li>
                 *   <li>{@code gateway.requests.duration{service}}               — timer (ms)</li>
                 * </ul>
                 * These metrics are scraped from {@code /actuator/metrics} and exposed via
                 * {@code GET /api/observability/metrics} in the admin service.
                 */
                @Component
                public class GatewayMetricsFilter implements GlobalFilter, Ordered {

                    private final MeterRegistry meterRegistry;

                    public GatewayMetricsFilter(MeterRegistry meterRegistry) {
                        this.meterRegistry = meterRegistry;
                    }

                    @Override
                    public int getOrder() { return -98; }

                    @Override
                    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                        long   start   = System.currentTimeMillis();
                        String path    = exchange.getRequest().getPath().value();
                        String service = extractServiceName(path);
                        String method  = exchange.getRequest().getMethod().name();

                        return chain.filter(exchange).doFinally(signal -> {
                            long   duration = System.currentTimeMillis() - start;
                            String status   = exchange.getResponse().getStatusCode() != null
                                    ? String.valueOf(exchange.getResponse().getStatusCode().value()) : "0";

                            Counter.builder("gateway.requests.total")
                                    .tag("service", service)
                                    .tag("method",  method)
                                    .tag("status",  status)
                                    .register(meterRegistry)
                                    .increment();

                            Timer.builder("gateway.requests.duration")
                                    .tag("service", service)
                                    .register(meterRegistry)
                                    .record(duration, TimeUnit.MILLISECONDS);
                        });
                    }

                    private String extractServiceName(String path) {
                        // e.g. /api/orders/123 -> "orders"
                        String[] parts = path.split("/");
                        return parts.length > 2 ? parts[2] : "unknown";
                    }
                }
                """;
        Files.writeString(pkg.resolve("GatewayMetricsFilter.java"), content);
    }

    private void generateOtelConfig(Path pkg) throws IOException {
        String content = """
                package org.fractalx.gateway.observability;

                import io.opentelemetry.api.OpenTelemetry;
                import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
                import io.opentelemetry.api.common.Attributes;
                import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
                import io.opentelemetry.context.propagation.ContextPropagators;
                import io.opentelemetry.context.propagation.TextMapPropagator;
                import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
                import io.opentelemetry.sdk.OpenTelemetrySdk;
                import io.opentelemetry.sdk.resources.Resource;
                import io.opentelemetry.sdk.trace.SdkTracerProvider;
                import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
                import io.opentelemetry.semconv.ResourceAttributes;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                /**
                 * OpenTelemetry SDK configuration for the API Gateway.
                 *
                 * <p>Uses OTLP/gRPC exporter on port 4317 — consistent with all generated microservices.
                 * This means the same Jaeger Docker command works for both gateway and services:
                 * {@code docker run -d --name jaeger -p 16686:16686 -p 4317:4317 jaegertracing/all-in-one:latest}
                 *
                 * <p>The {@code @ConditionalOnMissingBean} guard means Spring Boot's own OTLP
                 * auto-configuration (if present) takes precedence.
                 */
                @Configuration
                public class GatewayOtelConfig {

                    @Bean
                    @ConditionalOnMissingBean(OpenTelemetry.class)
                    public OpenTelemetry openTelemetry(
                            @Value("${fractalx.observability.otel.endpoint:http://localhost:4317}") String endpoint) {

                        Resource resource = Resource.getDefault()
                                .merge(Resource.create(Attributes.of(
                                        ResourceAttributes.SERVICE_NAME, "fractalx-gateway")));

                        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                                .setEndpoint(endpoint)
                                .build();

                        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                                .setResource(resource)
                                .build();

                        return OpenTelemetrySdk.builder()
                                .setTracerProvider(tracerProvider)
                                .setPropagators(ContextPropagators.create(
                                        TextMapPropagator.composite(
                                                W3CTraceContextPropagator.getInstance(),
                                                W3CBaggagePropagator.getInstance())))
                                .buildAndRegisterGlobal();
                    }
                }
                """;
        Files.writeString(pkg.resolve("GatewayOtelConfig.java"), content);
    }

    private void generateTracingExclusion(Path pkg) throws IOException {
        String content = """
                package org.fractalx.gateway.observability;

                import io.micrometer.observation.ObservationPredicate;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                /**
                 * Excludes noisy observations from gateway tracing so Jaeger stays clean:
                 * <ul>
                 *   <li>Actuator health/metrics endpoints</li>
                 *   <li>Scheduled tasks (defensive — gateway has none, but guard anyway)</li>
                 * </ul>
                 * Uses reflection to read the request path from the WebFlux
                 * {@code ServerWebExchange} carrier, avoiding a direct import of
                 * {@code ServerRequestObservationContext} whose package differs across
                 * Spring Framework versions.
                 */
                @Configuration
                public class GatewayTracingExclusionConfig {

                    @Bean
                    public ObservationPredicate noActuatorTracing() {
                        return (name, context) -> {
                            try {
                                // carrier = ServerWebExchange
                                Object exchange = context.getClass().getMethod("getCarrier").invoke(context);
                                // request = ServerHttpRequest
                                Object request  = exchange.getClass().getMethod("getRequest").invoke(exchange);
                                // path   = RequestPath → toString() = "/actuator/health"
                                String  path    = request.getClass().getMethod("getPath").invoke(request).toString();
                                return !path.startsWith("/actuator");
                            } catch (Exception ignored) {
                                return true;
                            }
                        };
                    }

                    @Bean
                    public ObservationPredicate noScheduledTaskTracing() {
                        return (name, context) -> !name.startsWith("tasks.scheduled");
                    }
                }
                """;
        Files.writeString(pkg.resolve("GatewayTracingExclusionConfig.java"), content);
    }

    private static boolean isBoot4Plus(String version) {
        return version != null && !version.isBlank()
                && Character.getNumericValue(version.charAt(0)) >= 4;
    }

    private Path createPkg(Path base, String pkg) throws IOException {
        Path p = base;
        for (String part : pkg.split("/")) p = p.resolve(part);
        Files.createDirectories(p);
        return p;
    }
}
