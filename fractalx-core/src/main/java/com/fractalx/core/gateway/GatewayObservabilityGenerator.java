package com.fractalx.core.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates request tracing and structured logging filters for the gateway. */
public class GatewayObservabilityGenerator {

    private static final Logger log = LoggerFactory.getLogger(GatewayObservabilityGenerator.class);

    public void generate(Path srcMainJava) throws IOException {
        Path pkg = createPkg(srcMainJava, "com/fractalx/gateway/observability");

        generateTracingFilter(pkg);
        generateLoggingFilter(pkg);

        log.info("Generated gateway observability filters");
    }

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
                 * Propagates distributed tracing headers through the gateway.
                 * <ul>
                 *   <li>X-Request-Id — always a fresh UUID per request</li>
                 *   <li>X-Trace-Id  — propagated from upstream if present, else equals X-Request-Id</li>
                 * </ul>
                 * Both headers are forwarded to downstream services and echoed in the response.
                 */
                @Component
                public class TracingFilter implements GlobalFilter, Ordered {

                    @Override
                    public int getOrder() { return -99; }

                    @Override
                    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                        String requestId = UUID.randomUUID().toString();
                        String traceId  = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
                        if (traceId == null || traceId.isBlank()) {
                            traceId = requestId;
                        }
                        final String finalTraceId = traceId;

                        ServerWebExchange mutated = exchange.mutate()
                                .request(r -> r.headers(h -> {
                                    h.set("X-Request-Id", requestId);
                                    h.set("X-Trace-Id",   finalTraceId);
                                }))
                                .build();

                        return chain.filter(mutated).doFinally(signal ->
                                mutated.getResponse().getHeaders().set("X-Request-Id", requestId));
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
                 * Logs: method, path, traceId, response status, and duration in ms.
                 */
                @Component
                public class RequestLoggingFilter implements GlobalFilter, Ordered {

                    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

                    @Override
                    public int getOrder() { return -100; }

                    @Override
                    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                        long   startTime = System.currentTimeMillis();
                        String method    = exchange.getRequest().getMethod().name();
                        String path      = exchange.getRequest().getURI().getPath();
                        String traceId   = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");

                        log.info("[GATEWAY] --> {} {} traceId={}", method, path,
                                traceId != null ? traceId : "-");

                        return chain.filter(exchange).doFinally(signal -> {
                            long duration = System.currentTimeMillis() - startTime;
                            int  status   = exchange.getResponse().getStatusCode() != null
                                    ? exchange.getResponse().getStatusCode().value() : 0;
                            log.info("[GATEWAY] <-- {} {} status={} duration={}ms traceId={}",
                                    method, path, status, duration,
                                    traceId != null ? traceId : "-");
                        });
                    }
                }
                """;
        Files.writeString(pkg.resolve("RequestLoggingFilter.java"), content);
    }

    private Path createPkg(Path base, String pkg) throws IOException {
        Path p = base;
        for (String part : pkg.split("/")) p = p.resolve(part);
        Files.createDirectories(p);
        return p;
    }
}
