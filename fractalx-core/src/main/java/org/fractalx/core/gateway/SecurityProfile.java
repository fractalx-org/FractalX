package org.fractalx.core.gateway;

import java.util.List;

/**
 * Describes the security configuration detected from the monolith source.
 * Passed to gateway generators so the produced gateway mirrors the monolith's
 * auth mechanism and route-level authorization rules without manual configuration.
 */
public record SecurityProfile(
        AuthType authType,
        boolean securityEnabled,
        String jwkSetUri,
        String issuerUri,
        String jwtSecret,
        String basicUsername,
        String basicPassword,
        List<RouteSecurityRule> routeRules,
        List<String> publicPaths
) {

    public enum AuthType { OAUTH2, BEARER_JWT, BASIC, NONE }

    /**
     * A single path-to-role authorization rule extracted from the monolith.
     * {@code permitAll=true} means the path is public — roles are ignored.
     */
    public record RouteSecurityRule(String pathPattern, List<String> roles, boolean permitAll) {}

    /** Returns a profile representing a monolith with no detected security. */
    public static SecurityProfile none() {
        return new SecurityProfile(
                AuthType.NONE, false, null, null, null, null, null, List.of(), List.of());
    }

    /** Whether any route rules were detected. */
    public boolean hasRouteRules() {
        return routeRules != null && !routeRules.isEmpty();
    }

    /** Whether any recognized auth mechanism was found. */
    public boolean isAnyAuthDetected() {
        return authType != AuthType.NONE;
    }
}
