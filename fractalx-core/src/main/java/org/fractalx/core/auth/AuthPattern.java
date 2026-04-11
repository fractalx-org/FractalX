package org.fractalx.core.auth;

import java.util.Map;

/**
 * Holds the result of {@link AuthPatternDetector} scanning the monolith source tree.
 *
 * <p>When {@code detected} is {@code true}, {@link AuthServiceGenerator} should be
 * invoked to produce a standalone {@code auth-service} that issues JWTs matching
 * the gateway's {@code fractalx.gateway.security.bearer.jwt-secret}.
 */
public record AuthPattern(
        boolean detected,
        /** The raw HMAC-SHA256 key read from the monolith's jwt.secret (or jwt-secret) property. */
        String  jwtSecret,
        /** Token TTL in milliseconds (jwt.expiration-ms). Defaults to 86400000 (24 h) if unset. */
        long    expirationMs,
        /** Package of the JPA User entity that implements UserDetails. */
        String  userPackage,
        /** Package of the @RestController serving /api/auth endpoints. */
        String  authPackage,
        /**
         * Non-standard fields from the monolith's User entity (field name → Java type).
         * These are added to the generated auth-service User entity and included as
         * String claims in the issued JWT so downstream services can reconstruct them
         * via {@code GatewayPrincipal.getAttribute(name)}.
         * Example: {@code {"customerId": "Long", "email": "String"}}.
         */
        Map<String, String> domainFields,
        /**
         * The generated service name of a cross-module service whose "create" method is called
         * in the monolith's {@code AuthController.register()} to create a linked domain entity
         * (e.g. {@code "customer-service"}). The generated auth-service will POST to this service
         * during registration and link the returned entity ID as the user's domain ID field.
         * {@code null} if no such cross-module call was detected.
         */
        String registerLinkedService,
        /**
         * The REST API path on {@link #registerLinkedService} to POST to when creating the
         * linked entity during registration (e.g. {@code "/api/customers"}).
         * {@code null} when {@link #registerLinkedService} is {@code null}.
         */
        String registerLinkedPath,
        /**
         * The domain field name on {@code User} that should receive the returned entity's
         * {@code id} (e.g. {@code "customerId"}).
         * {@code null} when {@link #registerLinkedService} is {@code null}.
         */
        String registerLinkedIdField
) {
    public static AuthPattern none() {
        return new AuthPattern(false, null, 86_400_000L, null, null, Map.of(), null, null, null);
    }

    /** Effective secret — falls back to a safe default so downstream code never receives null. */
    public String effectiveSecret() {
        return jwtSecret != null && !jwtSecret.isBlank()
                ? jwtSecret
                : "fractalx-default-secret-change-in-prod-min-32chars!!";
    }

    public long effectiveExpirationMs() {
        return expirationMs > 0 ? expirationMs : 86_400_000L;
    }
}
