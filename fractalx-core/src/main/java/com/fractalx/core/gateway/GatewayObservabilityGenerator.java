package com.fractalx.core.gateway;

import com.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates three gateway observability filters:
 * <ol>
 *   <li>{@code RequestLoggingFilter} (order -100) — structured ingress/egress logging</li>
 *   <li>{@code TracingFilter}        (order -99)  — propagates X-Correlation-Id + W3C traceparent</li>
 *   <li>{@code GatewayMetricsFilter} (order -98)  — Micrometer counters + timers per service</li>
 * </ol>
 */
public class GatewayObservabilityGenerator {

    private static final Logger log = LoggerFactory.getLogger(GatewayObservabilityGenerator.class);

    public void generate(Path srcMainJava, List<FractalModule> modules) throws IOException {
        Path pkg = createPkg(srcMainJava, "com/fractalx/gateway/observability");

        generateLoggingFilter(pkg);
        generateTracingFilter(pkg);
        generateMetricsFilter(pkg, modules);

        log.info("Generated gateway observability filters");
    }

    /** Overload for callers that don't pass modules (backward compat). */
    public void generate(Path srcMainJava) throws IOException {
        generate(srcMainJava, List.of());
    }

    // -------------------------------------------------------------------------

    private void generateTracingFilter(Path pkg) throws IOException {
        String content = """
                package com.fractalx.gateway.observability;

                import org.springframework.cloud.gateway.filter.GatewayFilterChain;
                import org.springframework.cloud.gateway.filter.GlobalFilter;
                import org.springframework.core.Ordered;
                import org.springframework.stereotype.Component;
                import org.springframework.web.server.ServerWebExchange;
                import reactor.core.publisher.Mono;

                import java.util.UUID;

                /**
                 * Propagates correlation and W3C trace-context headers through the gateway.
                 * <ul>
                 *   <li>X-Request-Id     — fresh UUID per request</li>
                 *   <li>X-Correlation-Id — propagated from upstream if present, else equals X-Request-Id</li>
                 *   <li>traceparent      — W3C Trace-Context header forwarded downstream for OTEL</li>
                 * </ul>
                 */
                @Component
                public class TracingFilter implements GlobalFilter, Ordered {

                    @Override
                    public int getOrder() { return -99; }

                    @Override
                    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                        String requestId     = UUID.randomUUID().toString();
                        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
                        if (correlationId == null || correlationId.isBlank()) {
                            correlationId = requestId;
                        }
                        final String finalCorrelationId = correlationId;

                        // Forward W3C traceparent if already set by an upstream OTEL agent;
                        // otherwise leave it unset so Micrometer/OTEL generates a root span.
                        String traceparent = exchange.getRequest().getHeaders().getFirst("traceparent");

                        ServerWebExchange mutated = exchange.mutate()
                                .request(r -> r.headers(h -> {
                                    h.set("X-Request-Id",     requestId);
                                    h.set("X-Correlation-Id", finalCorrelationId);
                                    if (traceparent != null) h.set("traceparent", traceparent);
                                }))
                                .build();

                        return chain.filter(mutated).doFinally(signal -> {
                            mutated.getResponse().getHeaders().set("X-Request-Id",     requestId);
                            mutated.getResponse().getHeaders().set("X-Correlation-Id", finalCorrelationId);
                        });
                    }
                }
                """;
        Files.writeString(pkg.resolve("TracingFilter.java"), content);
    }

    private void generateLoggingFilter(Path pkg) throws IOException {
        String content = """
                package com.fractalx.gateway.observability;

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
                    public int getOrder() { return -100; }

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
                package com.fractalx.gateway.observability;

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

    private Path createPkg(Path base, String pkg) throws IOException {
        Path p = base;
        for (String part : pkg.split("/")) p = p.resolve(part);
        Files.createDirectories(p);
        return p;
    }
}
