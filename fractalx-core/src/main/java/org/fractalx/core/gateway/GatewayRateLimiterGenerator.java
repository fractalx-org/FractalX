package org.fractalx.core.gateway;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Generates token-bucket in-memory rate limiting for the API gateway. */
public class GatewayRateLimiterGenerator {

    private static final Logger log = LoggerFactory.getLogger(GatewayRateLimiterGenerator.class);

    public void generate(Path srcMainJava, List<FractalModule> modules) throws IOException {
        Path pkg = createPkg(srcMainJava, "org/fractalx/gateway/ratelimit");

        generateRateLimitConfig(pkg);
        generateRateLimitFilter(pkg);

        log.info("Generated gateway rate limiter");
    }

    private void generateRateLimitConfig(Path pkg) throws IOException {
        String content = """
                package org.fractalx.gateway.ratelimit;

                import org.springframework.boot.context.properties.ConfigurationProperties;
                import org.springframework.context.annotation.Configuration;

                import java.util.HashMap;
                import java.util.Map;

                /**
                 * Rate limit config. Override per-service via:
                 * <pre>
                 * fractalx:
                 *   gateway:
                 *     rate-limit:
                 *       default-rps: 100
                 *       per-service:
                 *         order-service: 200
                 *         payment-service: 50
                 * </pre>
                 */
                @Configuration
                @ConfigurationProperties(prefix = "fractalx.gateway.rate-limit")
                public class RateLimitConfig {

                    private int defaultRps = 100;
                    private Map<String, Integer> perService = new HashMap<>();

                    public int getDefaultRps() { return defaultRps; }
                    public void setDefaultRps(int rps) { this.defaultRps = rps; }
                    public Map<String, Integer> getPerService() { return perService; }
                    public void setPerService(Map<String, Integer> m) { this.perService = m; }

                    public int getRpsForService(String serviceName) {
                        return perService.getOrDefault(serviceName, defaultRps);
                    }
                }
                """;
        Files.writeString(pkg.resolve("RateLimitConfig.java"), content);
    }

    private void generateRateLimitFilter(Path pkg) throws IOException {
        String content = """
                package org.fractalx.gateway.ratelimit;

                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.cloud.gateway.filter.GatewayFilterChain;
                import org.springframework.cloud.gateway.filter.GlobalFilter;
                import org.springframework.core.Ordered;
                import org.springframework.http.HttpStatus;
                import org.springframework.stereotype.Component;
                import org.springframework.web.server.ServerWebExchange;
                import reactor.core.publisher.Mono;

                import java.util.concurrent.ConcurrentHashMap;

                /**
                 * Sliding-window in-memory rate limiter per remote IP + service path.
                 * No Redis required — suitable for single-instance gateway.
                 */
                @Component
                public class RateLimitFilter implements GlobalFilter, Ordered {

                    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

                    private final RateLimitConfig config;
                    // key: "ip:service", value: [windowStartMs, count]
                    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();

                    public RateLimitFilter(RateLimitConfig config) {
                        this.config = config;
                    }

                    @Override
                    public int getOrder() { return -80; }

                    @Override
                    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                        String path    = exchange.getRequest().getPath().value();
                        String svcName = extractServiceName(path);
                        String ip      = extractIp(exchange);
                        String key     = ip + ":" + svcName;
                        int    limit   = config.getRpsForService(svcName);

                        long now    = System.currentTimeMillis();
                        long[] slot = windows.computeIfAbsent(key, k -> new long[]{now, 0});

                        synchronized (slot) {
                            if (now - slot[0] > 1000L) {
                                slot[0] = now;
                                slot[1] = 0;
                            }
                            slot[1]++;
                            if (slot[1] > limit) {
                                log.warn("Rate limit exceeded: ip={} service={} count={}", ip, svcName, slot[1]);
                                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                                exchange.getResponse().getHeaders().set("Retry-After", "1");
                                exchange.getResponse().getHeaders().set("X-RateLimit-Limit",     String.valueOf(limit));
                                exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", "0");
                                return exchange.getResponse().setComplete();
                            }
                            exchange.getResponse().getHeaders()
                                    .set("X-RateLimit-Remaining", String.valueOf(limit - slot[1]));
                        }
                        return chain.filter(exchange);
                    }

                    private String extractServiceName(String path) {
                        String[] parts = path.split("/");
                        return parts.length > 2 ? parts[2] : "default";
                    }

                    private String extractIp(ServerWebExchange exchange) {
                        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
                        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
                        var addr = exchange.getRequest().getRemoteAddress();
                        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
                    }
                }
                """;
        Files.writeString(pkg.resolve("RateLimitFilter.java"), content);
    }

    private Path createPkg(Path base, String pkg) throws IOException {
        Path p = base;
        for (String part : pkg.split("/")) p = p.resolve(part);
        Files.createDirectories(p);
        return p;
    }
}
