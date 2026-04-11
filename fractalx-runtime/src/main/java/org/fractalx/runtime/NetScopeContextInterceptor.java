package org.fractalx.runtime;

import io.grpc.*;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Propagates the FractalX correlation ID across gRPC/NetScope calls.
 *
 * Client side: reads correlationId from MDC and injects it into outgoing gRPC metadata.
 * Server side: extracts correlationId from incoming gRPC metadata and populates MDC
 *              via ForwardingServerCallListener so it is available in the gRPC thread
 *              that actually invokes the service method (onHalfClose / onMessage).
 */
@Component
@ConditionalOnClass(name = "io.grpc.BindableService")
public class NetScopeContextInterceptor implements ClientInterceptor, ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(NetScopeContextInterceptor.class);
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final Metadata.Key<String> CORRELATION_METADATA_KEY =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    // Internal Call Token — signed JWT minted by the gateway; carries user identity securely
    private static final Metadata.Key<String> INTERNAL_TOKEN_KEY =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @Autowired(required = false)
    private Tracer tracer;

    // ---- Client side: inject correlationId into outgoing gRPC metadata ----

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // Propagate correlation ID
                String correlationId = MDC.get(CORRELATION_ID_KEY);
                if (correlationId != null) {
                    headers.put(CORRELATION_METADATA_KEY, correlationId);
                    log.debug("NetScope: injected correlationId={} into outgoing gRPC metadata", correlationId);
                }
                // Propagate Internal Call Token (signed JWT) for secured inter-service identity
                try {
                    org.springframework.security.core.Authentication auth =
                            org.springframework.security.core.context.SecurityContextHolder
                                    .getContext().getAuthentication();
                    if (auth != null && auth.getCredentials() instanceof String token
                            && !((String) auth.getCredentials()).isBlank()) {
                        headers.put(INTERNAL_TOKEN_KEY, (String) auth.getCredentials());
                        log.debug("NetScope: injected x-internal-token into outgoing gRPC metadata");
                    }
                } catch (NoClassDefFoundError ignored) {
                    // Spring Security not on classpath — skip identity propagation
                }
                super.start(responseListener, headers);
            }
        };
    }

    // ---- Server side: extract correlationId from metadata, propagate via listener ----

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String correlationId = headers.get(CORRELATION_METADATA_KEY);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            log.debug("NetScope: no correlationId in gRPC metadata, generated {}", correlationId);
        } else {
            log.debug("NetScope: extracted correlationId={} from gRPC metadata", correlationId);
        }

        final String cid = correlationId;
        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);

        // Wrap the listener so MDC is set in each callback where the service method may run.
        // For unary calls, invokeMethod() is triggered inside onHalfClose().
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {

            private void tagCurrentSpan(String cid) {
                if (tracer != null) {
                    io.micrometer.tracing.Span span = tracer.currentSpan();
                    if (span != null) span.tag(CORRELATION_ID_KEY, cid);
                }
            }

            @Override
            public void onMessage(ReqT message) {
                MDC.put(CORRELATION_ID_KEY, cid);
                try {
                    super.onMessage(message);
                } finally {
                    MDC.remove(CORRELATION_ID_KEY);
                }
            }

            @Override
            public void onHalfClose() {
                MDC.put(CORRELATION_ID_KEY, cid);
                tagCurrentSpan(cid);
                // Reconstruct Spring Authentication from x-internal-token gRPC metadata (if present)
                String internalToken = headers.get(INTERNAL_TOKEN_KEY);
                if (internalToken != null && !internalToken.isBlank()) {
                    try {
                        String secret = resolveInternalJwtSecret();
                        javax.crypto.SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        // JJWT 0.12.x API: parser() / verifyWith() / parseSignedClaims() / getPayload()
                        io.jsonwebtoken.Claims claims = io.jsonwebtoken.Jwts.parser()
                                .verifyWith(key)
                                .requireIssuer("fractalx-gateway")
                                .build()
                                .parseSignedClaims(internalToken)
                                .getPayload();
                        // Validate audience — JJWT 0.12.x returns Set<String>
                        java.util.Set<String> audSet = claims.getAudience();
                        if (audSet == null || !audSet.contains("fractalx-internal")) {
                            log.warn("NetScope: x-internal-token has unexpected audience '{}' — rejecting", audSet);
                            super.onHalfClose();
                            return;
                        }
                        String userId   = claims.getSubject();
                        String rolesStr = claims.get("roles", String.class);
                        java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority> auths =
                                (rolesStr == null || rolesStr.isBlank())
                                        ? java.util.List.of()
                                        : java.util.Arrays.stream(rolesStr.split(","))
                                                .map(String::trim)
                                                .filter(r -> !r.isBlank())
                                                .map(r -> new org.springframework.security.core.authority
                                                        .SimpleGrantedAuthority("ROLE_" + r))
                                                .collect(java.util.stream.Collectors.toList());

                        // Extract extended claims forwarded by the gateway
                        String username = claims.get("username", String.class);
                        String email    = claims.get("email", String.class);
                        java.util.Map<String, Object> attributes = new java.util.HashMap<>();
                        java.util.Set<String> reserved = java.util.Set.of(
                                "sub", "roles", "iss", "aud", "iat", "exp", "username", "email");
                        claims.forEach((k, v) -> {
                            if (!reserved.contains(k)) attributes.put(k, v);
                        });

                        GatewayPrincipal principal = new GatewayPrincipal(
                                userId, username, email, auths,
                                java.util.Collections.unmodifiableMap(attributes));

                        org.springframework.security.core.context.SecurityContextHolder
                                .getContext()
                                .setAuthentication(
                                        new org.springframework.security.authentication
                                                .UsernamePasswordAuthenticationToken(principal, internalToken, auths));
                        log.debug("NetScope: established Authentication for user={} from x-internal-token", userId);
                    } catch (io.jsonwebtoken.ExpiredJwtException e) {
                        log.warn("NetScope: x-internal-token expired for subject={} — request proceeds without auth. " +
                                 "Check gateway clock sync or increase token TTL.", e.getClaims().getSubject());
                    } catch (Exception e) {
                        log.debug("NetScope: x-internal-token validation failed — {}", e.getMessage());
                    }
                }
                try {
                    super.onHalfClose();
                } finally {
                    // Clear security context after gRPC call to prevent thread-local leakage
                    try {
                        org.springframework.security.core.context.SecurityContextHolder.clearContext();
                    } catch (NoClassDefFoundError ignored) { }
                    MDC.remove(CORRELATION_ID_KEY);
                }
            }

            @Override
            public void onComplete() {
                MDC.remove(CORRELATION_ID_KEY);
                super.onComplete();
            }

            @Override
            public void onCancel() {
                MDC.remove(CORRELATION_ID_KEY);
                super.onCancel();
            }
        };
    }

    /**
     * Resolves the internal JWT secret used to validate cross-service call tokens.
     * Checks (in order): system property, env var, then a safe fallback default.
     * In production set {@code FRACTALX_INTERNAL_JWT_SECRET} as an env var —
     * must be identical across the gateway and all generated services.
     */
    private static String resolveInternalJwtSecret() {
        String sysProp = System.getProperty("fractalx.security.internal-jwt-secret");
        if (sysProp != null && !sysProp.isBlank()) return sysProp;
        String envVar = System.getenv("FRACTALX_INTERNAL_JWT_SECRET");
        if (envVar != null && !envVar.isBlank()) return envVar;
        return "fractalx-internal-secret-change-in-prod-!!";
    }
}
