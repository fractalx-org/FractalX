package org.fractalx.core.auth;

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
        String  authPackage
) {
    public static AuthPattern none() {
        return new AuthPattern(false, null, 86_400_000L, null, null);
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
