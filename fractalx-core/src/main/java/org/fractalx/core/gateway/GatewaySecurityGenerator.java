package org.fractalx.core.gateway;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates the gateway security layer with four pluggable authentication mechanisms:
 * <ul>
 *   <li><b>OAuth2</b> — JWT from external IdP (Keycloak, Auth0) via JWK Set URI</li>
 *   <li><b>Bearer JWT</b> — symmetric HMAC-SHA256 signed tokens</li>
 *   <li><b>Basic / Simple Auth</b> — username + password in Authorization header</li>
 *   <li><b>API Key</b> — X-Api-Key header or api_key query param</li>
 * </ul>
 * All mechanisms are <b>disabled by default</b> (enabled: false).
 * Activate selectively via {@code fractalx.gateway.security.*} properties.
 */
public class GatewaySecurityGenerator {

    private static final Logger log = LoggerFactory.getLogger(GatewaySecurityGenerator.class);

    public void generate(Path srcMainJava, List<FractalModule> modules) throws IOException {
        Path pkg = createPkg(srcMainJava, "org/fractalx/gateway/security");

        generateAuthProperties(pkg);
        generateSecurityConfig(pkg);
        generateJwtBearerFilter(pkg);
        generateApiKeyFilter(pkg);
        generateBasicAuthFilter(pkg);

        log.info("Generated gateway security (OAuth2 / Bearer / Basic / API-Key)");
    }

    private void generateAuthProperties(Path pkg) throws IOException {
        // Note: "&#42;" is the HTML entity for "*" — prevents "&#42;/" from being
        // misread as the Javadoc block-comment terminator "*/" inside the <pre> block.
        String content = """
                package org.fractalx.gateway.security;

                import org.springframework.boot.context.properties.ConfigurationProperties;

                import java.util.ArrayList;
                import java.util.List;

                /**
                 * Unified security properties for the generated API gateway.
                 *
                 * <pre>
                 * fractalx:
                 *   gateway:
                 *     security:
                 *       enabled: true
                 *       public-paths: /api/&#42;/public/&#42;&#42;, /api/&#42;/auth/&#42;&#42;
                 *       bearer:
                 *         enabled: true
                 *         jwt-secret: my-secret-key-min-32-chars-long!!
                 *       oauth2:
                 *         enabled: false
                 *         jwk-set-uri: http://keycloak:8080/realms/myrealm/protocol/openid-connect/certs
                 *       basic:
                 *         enabled: false
                 *         username: admin
                 *         password: changeme
                 *       api-key:
                 *         enabled: false
                 *         valid-keys:
                 *           - my-api-key-1
                 *           - my-api-key-2
                 * </pre>
                 */
                @ConfigurationProperties(prefix = "fractalx.gateway.security")
                public class GatewayAuthProperties {

                    private boolean enabled = false;
                    private String[] publicPaths = {"/api/*/public/**", "/api/*/auth/**"};

                    private Bearer bearer = new Bearer();
                    private OAuth2 oauth2 = new OAuth2();
                    private Basic  basic  = new Basic();
                    private ApiKey apiKey = new ApiKey();

                    public boolean isEnabled() { return enabled; }
                    public void setEnabled(boolean enabled) { this.enabled = enabled; }
                    public String[] getPublicPaths() { return publicPaths; }
                    public void setPublicPaths(String[] publicPaths) { this.publicPaths = publicPaths; }
                    public Bearer getBearer() { return bearer; }
                    public void setBearer(Bearer bearer) { this.bearer = bearer; }
                    public OAuth2 getOauth2() { return oauth2; }
                    public void setOauth2(OAuth2 oauth2) { this.oauth2 = oauth2; }
                    public Basic getBasic() { return basic; }
                    public void setBasic(Basic basic) { this.basic = basic; }
                    public ApiKey getApiKey() { return apiKey; }
                    public void setApiKey(ApiKey apiKey) { this.apiKey = apiKey; }

                    public static class Bearer {
                        private boolean enabled = false;
                        private String jwtSecret = "fractalx-default-secret-change-in-prod-min-32chars!!";
                        public boolean isEnabled() { return enabled; }
                        public void setEnabled(boolean enabled) { this.enabled = enabled; }
                        public String getJwtSecret() { return jwtSecret; }
                        public void setJwtSecret(String s) { this.jwtSecret = s; }
                    }

                    public static class OAuth2 {
                        private boolean enabled = false;
                        private String jwkSetUri = "http://localhost:8080/realms/fractalx/protocol/openid-connect/certs";
                        public boolean isEnabled() { return enabled; }
                        public void setEnabled(boolean enabled) { this.enabled = enabled; }
                        public String getJwkSetUri() { return jwkSetUri; }
                        public void setJwkSetUri(String uri) { this.jwkSetUri = uri; }
                    }

                    public static class Basic {
                        private boolean enabled = false;
                        private String username = "fractalx";
                        private String password = "changeme";
                        public boolean isEnabled() { return enabled; }
                        public void setEnabled(boolean enabled) { this.enabled = enabled; }
                        public String getUsername() { return username; }
                        public void setUsername(String u) { this.username = u; }
                        public String getPassword() { return password; }
                        public void setPassword(String p) { this.password = p; }
                    }

                    public static class ApiKey {
                        private boolean enabled = false;
                        private List<String> validKeys = new ArrayList<>(List.of("dev-key-replace-me"));
                        public boolean isEnabled() { return enabled; }
                        public void setEnabled(boolean enabled) { this.enabled = enabled; }
                        public List<String> getValidKeys() { return validKeys; }
                        public void setValidKeys(List<String> keys) { this.validKeys = keys; }
                    }
                }
                """;
        Files.writeString(pkg.resolve("GatewayAuthProperties.java"), content);
    }

    private void generateSecurityConfig(Path pkg) throws IOException {
        // The three auth filters (ApiKeyFilter, JwtBearerFilter, BasicAuthGatewayFilter)
        // implement GlobalFilter + Ordered and are @Component beans. Spring Cloud Gateway
        // picks them up automatically — they must NOT be passed to addFilterBefore()
        // because that method expects WebFilter, not GlobalFilter. Authentication rejection
        // (HTTP 401) is handled directly inside each GlobalFilter implementation.
        // OAuth2 resource-server support is the only thing wired through Spring Security here.
        String content = """
                package org.fractalx.gateway.security;

                import org.springframework.boot.context.properties.EnableConfigurationProperties;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
                import org.springframework.security.config.web.server.ServerHttpSecurity;
                import org.springframework.security.web.server.SecurityWebFilterChain;

                @Configuration
                @EnableWebFluxSecurity
                @EnableConfigurationProperties(GatewayAuthProperties.class)
                public class GatewaySecurityConfig {

                    private final GatewayAuthProperties authProps;

                    public GatewaySecurityConfig(GatewayAuthProperties authProps) {
                        this.authProps = authProps;
                    }

                    @Bean
                    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
                        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                            .formLogin(ServerHttpSecurity.FormLoginSpec::disable);

                        // Always public — actuator, discovery, docs, fallback, and configured paths
                        http.authorizeExchange(ex -> ex
                                .pathMatchers("/actuator/health", "/actuator/info",
                                        "/services/**", "/api-docs/**", "/swagger-ui/**",
                                        "/fallback/**").permitAll()
                                .pathMatchers(authProps.getPublicPaths()).permitAll()
                                .anyExchange().permitAll()
                        );

                        // Auth GlobalFilter beans (auto-registered by Spring Cloud Gateway):
                        //   JwtBearerFilter (order -90)  — HMAC-SHA256 Bearer JWT
                        //   ApiKeyFilter (order -95)      — X-Api-Key header / api_key param
                        //   BasicAuthGatewayFilter (order -85) — HTTP Basic credentials
                        // These GlobalFilter beans must NOT be passed to addFilterBefore() because
                        // that method expects WebFilter; Spring Cloud Gateway discovers them automatically.

                        // OAuth2 resource-server (external IdP): only active when explicitly enabled.
                        // Bearer/Basic/ApiKey auth is enforced by the GlobalFilter beans directly.
                        if (authProps.isEnabled() && authProps.getOauth2().isEnabled()) {
                            http.oauth2ResourceServer(oauth2 -> oauth2
                                    .jwt(jwt -> jwt.jwkSetUri(authProps.getOauth2().getJwkSetUri())));
                        }

                        return http.build();
                    }
                }
                """;
        Files.writeString(pkg.resolve("GatewaySecurityConfig.java"), content);
    }

    private void generateJwtBearerFilter(Path pkg) throws IOException {
        String content = """
                package org.fractalx.gateway.security;

                import io.jsonwebtoken.Claims;
                import io.jsonwebtoken.JwtException;
                import io.jsonwebtoken.Jwts;
                import io.jsonwebtoken.security.Keys;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.cloud.gateway.filter.GatewayFilterChain;
                import org.springframework.cloud.gateway.filter.GlobalFilter;
                import org.springframework.core.Ordered;
                import org.springframework.http.HttpHeaders;
                import org.springframework.http.HttpStatus;
                import org.springframework.stereotype.Component;
                import org.springframework.web.server.ServerWebExchange;
                import reactor.core.publisher.Mono;

                import javax.crypto.SecretKey;
                import java.nio.charset.StandardCharsets;

                /**
                 * Validates Bearer JWT tokens signed with HMAC-SHA256.
                 * Active when {@code fractalx.gateway.security.bearer.enabled=true}.
                 * Injects X-User-Id and X-User-Roles headers downstream on success.
                 * Registered automatically by Spring Cloud Gateway as a GlobalFilter bean.
                 */
                @Component
                public class JwtBearerFilter implements GlobalFilter, Ordered {

                    private static final Logger log = LoggerFactory.getLogger(JwtBearerFilter.class);

                    private final GatewayAuthProperties props;

                    public JwtBearerFilter(GatewayAuthProperties props) {
                        this.props = props;
                    }

                    @Override
                    public int getOrder() { return -90; }

                    @Override
                    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                        if (!props.isEnabled() || !props.getBearer().isEnabled()) {
                            return chain.filter(exchange);
                        }
                        String authHeader = exchange.getRequest().getHeaders()
                                .getFirst(HttpHeaders.AUTHORIZATION);
                        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                            return chain.filter(exchange); // let other filters decide
                        }
                        String token = authHeader.substring(7);
                        try {
                            SecretKey key = Keys.hmacShaKeyFor(
                                    props.getBearer().getJwtSecret().getBytes(StandardCharsets.UTF_8));
                            Claims claims = Jwts.parserBuilder()
                                    .setSigningKey(key).build()
                                    .parseClaimsJws(token).getBody();
                            String roles = claims.get("roles", String.class);
                            ServerWebExchange mutated = exchange.mutate()
                                    .request(r -> r.headers(h -> {
                                        h.set("X-User-Id",    claims.getSubject());
                                        h.set("X-User-Roles", roles != null ? roles : "");
                                        h.set("X-Auth-Method", "bearer-jwt");
                                    }))
                                    .build();
                            return chain.filter(mutated);
                        } catch (JwtException e) {
                            log.warn("Invalid Bearer JWT: {}", e.getMessage());
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }
                    }
                }
                """;
        Files.writeString(pkg.resolve("JwtBearerFilter.java"), content);
    }

    private void generateApiKeyFilter(Path pkg) throws IOException {
        String content = """
                package org.fractalx.gateway.security;

                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.cloud.gateway.filter.GatewayFilterChain;
                import org.springframework.cloud.gateway.filter.GlobalFilter;
                import org.springframework.core.Ordered;
                import org.springframework.http.HttpStatus;
                import org.springframework.stereotype.Component;
                import org.springframework.web.server.ServerWebExchange;
                import reactor.core.publisher.Mono;

                import java.util.List;

                /**
                 * Validates API Key authentication.
                 * Accepts the key via {@code X-Api-Key} header or {@code api_key} query param.
                 * Active when {@code fractalx.gateway.security.api-key.enabled=true}.
                 * Registered automatically by Spring Cloud Gateway as a GlobalFilter bean.
                 */
                @Component
                public class ApiKeyFilter implements GlobalFilter, Ordered {

                    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

                    private final GatewayAuthProperties props;

                    public ApiKeyFilter(GatewayAuthProperties props) {
                        this.props = props;
                    }

                    @Override
                    public int getOrder() { return -95; }

                    @Override
                    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                        if (!props.isEnabled() || !props.getApiKey().isEnabled()) {
                            return chain.filter(exchange);
                        }
                        String key = exchange.getRequest().getHeaders().getFirst("X-Api-Key");
                        if (key == null) {
                            key = exchange.getRequest().getQueryParams().getFirst("api_key");
                        }
                        List<String> validKeys = props.getApiKey().getValidKeys();
                        if (key != null && validKeys.contains(key)) {
                            final String finalKey = key;
                            ServerWebExchange mutated = exchange.mutate()
                                    .request(r -> r.headers(h -> {
                                        h.set("X-Auth-Method", "api-key");
                                        h.set("X-Api-Client", finalKey.substring(0,
                                                Math.min(6, finalKey.length())) + "***");
                                    }))
                                    .build();
                            return chain.filter(mutated);
                        }
                        if (key != null) {
                            log.warn("Invalid API key from {}", exchange.getRequest().getRemoteAddress());
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }
                        return chain.filter(exchange); // no key present — let other filters handle
                    }
                }
                """;
        Files.writeString(pkg.resolve("ApiKeyFilter.java"), content);
    }

    private void generateBasicAuthFilter(Path pkg) throws IOException {
        String content = """
                package org.fractalx.gateway.security;

                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.cloud.gateway.filter.GatewayFilterChain;
                import org.springframework.cloud.gateway.filter.GlobalFilter;
                import org.springframework.core.Ordered;
                import org.springframework.http.HttpHeaders;
                import org.springframework.http.HttpStatus;
                import org.springframework.stereotype.Component;
                import org.springframework.web.server.ServerWebExchange;
                import reactor.core.publisher.Mono;

                import java.nio.charset.StandardCharsets;
                import java.util.Base64;

                /**
                 * Validates HTTP Basic / Simple Auth credentials.
                 * Active when {@code fractalx.gateway.security.basic.enabled=true}.
                 * Registered automatically by Spring Cloud Gateway as a GlobalFilter bean.
                 */
                @Component
                public class BasicAuthGatewayFilter implements GlobalFilter, Ordered {

                    private static final Logger log = LoggerFactory.getLogger(BasicAuthGatewayFilter.class);

                    private final GatewayAuthProperties props;

                    public BasicAuthGatewayFilter(GatewayAuthProperties props) {
                        this.props = props;
                    }

                    @Override
                    public int getOrder() { return -85; }

                    @Override
                    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                        if (!props.isEnabled() || !props.getBasic().isEnabled()) {
                            return chain.filter(exchange);
                        }
                        String authHeader = exchange.getRequest().getHeaders()
                                .getFirst(HttpHeaders.AUTHORIZATION);
                        if (authHeader == null || !authHeader.startsWith("Basic ")) {
                            return chain.filter(exchange);
                        }
                        try {
                            String decoded = new String(
                                    Base64.getDecoder().decode(authHeader.substring(6)),
                                    StandardCharsets.UTF_8);
                            String[] parts = decoded.split(":", 2);
                            if (parts.length == 2
                                    && props.getBasic().getUsername().equals(parts[0])
                                    && props.getBasic().getPassword().equals(parts[1])) {
                                ServerWebExchange mutated = exchange.mutate()
                                        .request(r -> r.headers(h -> {
                                            h.set("X-User-Id",    parts[0]);
                                            h.set("X-Auth-Method", "basic");
                                        }))
                                        .build();
                                return chain.filter(mutated);
                            }
                        } catch (Exception e) {
                            log.warn("Malformed Basic Auth header: {}", e.getMessage());
                        }
                        log.warn("Invalid Basic Auth credentials from {}", exchange.getRequest().getRemoteAddress());
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }
                }
                """;
        Files.writeString(pkg.resolve("BasicAuthGatewayFilter.java"), content);
    }

    private Path createPkg(Path base, String pkg) throws IOException {
        Path p = base;
        for (String part : pkg.split("/")) p = p.resolve(part);
        Files.createDirectories(p);
        return p;
    }
}
