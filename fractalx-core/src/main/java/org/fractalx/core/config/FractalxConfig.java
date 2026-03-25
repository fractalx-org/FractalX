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
 *   base-package: com.acme.generated       # defaults to monolith groupId + ".generated"
 *   spring-boot-version: 3.3.2             # defaults to version read from source pom.xml
 *   spring-cloud-version: 2023.0.1
 *   registry:
 *     url: http://registry-host:8761
 *     port: 8761
 *   logger:
 *     url: http://logger-host:9099
 *     port: 9099
 *   saga:
 *     port: 8099
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
 *   resilience:
 *     failure-rate-threshold: 50
 *     wait-duration-in-open-state: 30s
 *     permitted-calls-in-half-open-state: 5
 *     sliding-window-size: 10
 *     retry-max-attempts: 3
 *     retry-wait-duration: 100ms
 *     timeout-duration: 2s
 *   docker:
 *     maven-build-image: maven:3.9-eclipse-temurin-17
 *     jre-runtime-image: eclipse-temurin:17-jre-jammy
 *     jaeger-image: jaegertracing/all-in-one:1.53
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
        Map<String, ServiceOverride> serviceOverrides,

        // ── Generalisation fields ─────────────────────────────────────────────
        String basePackage,          // null → derived from source pom groupId at read time
        String springBootVersion,
        String springCloudVersion,
        int    registryPort,
        int    loggerPort,
        int    sagaPort,
        ResilienceDefaults resilience,
        DockerImages dockerImages
) {

    // ── Nested records ────────────────────────────────────────────────────────

    /** Per-service overrides read from fractalx-config.yml. */
    public record ServiceOverride(int port, boolean tracingEnabled,
                                  String datasourceUrl, String datasourceUsername,
                                  String datasourcePassword, String datasourceDriver) {
        public boolean hasPort() { return port > 0; }
        public boolean hasDatasource() { return datasourceUrl != null && !datasourceUrl.isBlank(); }
        public boolean isH2() { return datasourceUrl != null && datasourceUrl.startsWith("jdbc:h2"); }
    }

    /** Resilience4j circuit-breaker / retry / timeout defaults for generated services. */
    public record ResilienceDefaults(
            int    failureRateThreshold,
            String waitDurationInOpenState,
            int    permittedCallsInHalfOpenState,
            int    slidingWindowSize,
            int    retryMaxAttempts,
            String retryWaitDuration,
            String timeoutDuration
    ) {
        public static ResilienceDefaults defaults() {
            return new ResilienceDefaults(50, "30s", 5, 10, 3, "100ms", "2s");
        }
    }

    /** Docker image tags used when generating Dockerfiles and docker-compose.yml. */
    public record DockerImages(
            String mavenBuildImage,
            String jreRuntimeImage,
            String jaegerImage
    ) {
        public static DockerImages defaults() {
            return new DockerImages(
                    "maven:3.9-eclipse-temurin-17",
                    "eclipse-temurin:17-jre-jammy",
                    "jaegertracing/all-in-one:1.53"
            );
        }
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
                Map.of(),
                null,
                "3.2.0",
                "2023.0.0",
                8761,
                9099,
                8099,
                ResilienceDefaults.defaults(),
                DockerImages.defaults()
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the base Java package for generated infrastructure classes.
     * Falls back to {@code "generated"} when no explicit value is configured
     * and the source pom.xml could not be read.
     */
    public String effectiveBasePackage() {
        return (basePackage != null && !basePackage.isBlank()) ? basePackage : "generated";
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
