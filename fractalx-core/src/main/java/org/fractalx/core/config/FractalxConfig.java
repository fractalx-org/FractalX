package org.fractalx.core.config;

import java.util.Map;

/**
 * Immutable snapshot of all FractalX platform-level configuration values read
 * from the pre-decomposed application's {@code fractalx-config.yml} (and,
 * optionally, its {@code application.yml}).
 *
 * <p>Every field carries a sensible default so generators remain functional
 * even when no {@code fractalx-config.yml} is present. When a value is
 * explicitly set in the config file it overrides the default.
 *
 * <p>Expected {@code fractalx-config.yml} structure:
 * <pre>
 * fractalx:
 *   registry:
 *     url: http://registry-host:8761
 *   logger:
 *     url: http://logger-host:9099
 *   otel:
 *     endpoint: http://jaeger-host:4317
 *   gateway:
 *     port: 9999
 *     cors:
 *       allowed-origins: "http://myapp.com,http://localhost:3000"
 *     security:
 *       oauth2:
 *         jwk-set-uri: http://auth-host:8080/realms/fractalx/protocol/openid-connect/certs
 *   admin:
 *     port: 9090
 *   services:
 *     order-service:
 *       port: 8081
 *       datasource:
 *         url: jdbc:mysql://db-host:3306/order_db
 *         username: root
 *         password: secret
 *     payment-service:
 *       tracing:
 *         enabled: false   # disables OTel + Jaeger for this service only
 * </pre>
 */
public record FractalxConfig(
        String registryUrl,
        String loggerUrl,
        String otelEndpoint,
        int    gatewayPort,
        String corsAllowedOrigins,
        String oauth2JwksUri,
        int    adminPort,
        Map<String, ServiceOverride> serviceOverrides
) {

    /** Per-service overrides read from fractalx-config.yml. */
    public record ServiceOverride(int port, boolean tracingEnabled) {
        public boolean hasPort() { return port > 0; }
    }

    // ── Defaults ─────────────────────────────────────────────────────────────

    public static FractalxConfig defaults() {
        return new FractalxConfig(
                "http://localhost:8761",
                "http://localhost:9099",
                "http://localhost:4317",
                9999,
                "http://localhost:3000,http://localhost:4200",
                "http://localhost:8080/realms/fractalx/protocol/openid-connect/certs",
                9090,
                Map.of()
        );
    }

    /** Returns the configured port for a service, or {@code defaultPort} if not set. */
    public int portFor(String serviceName, int defaultPort) {
        ServiceOverride ov = serviceOverrides.get(serviceName);
        return (ov != null && ov.hasPort()) ? ov.port() : defaultPort;
    }

    /**
     * Returns {@code false} only when the service explicitly sets
     * {@code tracing.enabled: false} in its fractalx-config.yml services block.
     * Defaults to {@code true} for all services.
     */
    public boolean isTracingEnabled(String serviceName) {
        ServiceOverride ov = serviceOverrides.get(serviceName);
        return ov == null || ov.tracingEnabled();
    }
}
