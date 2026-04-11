package org.fractalx.core.auth;

import org.fractalx.core.FractalxVersion;
import org.fractalx.core.config.FractalxConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates a standalone {@code auth-service} Spring Boot project when the
 * monolith's auth pattern is detected by {@link AuthPatternDetector}.
 *
 * <p>The generated service:
 * <ul>
 *   <li>Serves {@code POST /api/auth/login} and {@code POST /api/auth/register}</li>
 *   <li>Issues HMAC-SHA256 JWTs signed with the same secret the gateway validates</li>
 *   <li>Stores users in an H2 (dev) or PostgreSQL (docker) database via JPA</li>
 *   <li>Self-registers with {@code fractalx-registry} at startup</li>
 * </ul>
 *
 * <p>Port: 8090  ·  gRPC: 18090
 */
public class AuthServiceGenerator {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceGenerator.class);

    static final int  AUTH_PORT      = 8090;
    static final int  AUTH_GRPC_PORT = 18090;
    static final String SERVICE_NAME = "auth-service";
    static final String BASE_PKG     = "org.fractalx.auth";
    private static final String JJWT_VERSION = "0.12.6";

    public void generate(Path outputRoot, AuthPattern pattern, FractalxConfig cfg) throws IOException {
        log.info("Generating auth-service (port={})", AUTH_PORT);

        Path root             = outputRoot.resolve(SERVICE_NAME);
        Path srcMainJava      = root.resolve("src/main/java");
        Path srcMainResources = root.resolve("src/main/resources");
        Path srcTestJava      = root.resolve("src/test/java");

        Files.createDirectories(srcMainJava);
        Files.createDirectories(srcMainResources.resolve("db/migration"));
        Files.createDirectories(srcTestJava);

        Path modelPkg      = mkpkg(srcMainJava, "model");
        Path repoPkg       = mkpkg(srcMainJava, "repository");
        Path securityPkg   = mkpkg(srcMainJava, "security");
        Path controllerPkg = mkpkg(srcMainJava, "controller");
        Path regPkg        = mkpkg(srcMainJava, "registration");
        Path rootPkg       = mkpkg(srcMainJava, "");

        generatePom(root, cfg, pattern);
        generateApplicationClass(rootPkg);
        generateApplicationYml(srcMainResources, pattern, cfg);
        generateApplicationDevYml(srcMainResources);
        generateFlywayMigration(srcMainResources, pattern.domainFields());

        generateRoleEnum(modelPkg);
        generateUserEntity(modelPkg, pattern.domainFields());
        generateUserRepository(repoPkg);
        generateJwtUtil(securityPkg, pattern);
        generateUserDetailsServiceImpl(securityPkg);
        generateGatewayAuthHeaderFilter(securityPkg);
        generateSecurityConfig(securityPkg);

        generateDtos(controllerPkg, pattern.domainFields(), pattern.registerLinkedIdField());
        generateAuthController(controllerPkg, pattern.domainFields(), pattern);

        if (pattern.registerLinkedService() != null) {
            injectLinkedServiceUrl(srcMainResources, pattern);
        }

        generateRegistryClient(regPkg, cfg);
        generateServiceRegistrationAutoConfig(regPkg);

        log.info("auth-service generated at {}", root);
    }

    // =========================================================================
    // POM
    // =========================================================================

    private void generatePom(Path root, FractalxConfig cfg, AuthPattern pattern) throws IOException {
        String bootVersion = cfg.springBootVersion() != null ? cfg.springBootVersion() : "4.0.5";

        String runtimeDep = "<version>" + FractalxVersion.get() + "</version>";

        String content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>%s</version>
                        <relativePath/>
                    </parent>

                    <groupId>org.fractalx</groupId>
                    <artifactId>auth-service</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>auth-service</name>
                    <description>FractalX Auth Service — issues JWT tokens for gateway-validated access</description>

                    <properties>
                        <java.version>21</java.version>
                    </properties>

                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-dependencies</artifactId>
                                <version>%s</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-security</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-jpa</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-actuator</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-validation</artifactId>
                        </dependency>

                        <!-- Database -->
                        <dependency>
                            <groupId>com.h2database</groupId>
                            <artifactId>h2</artifactId>
                            <scope>runtime</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.postgresql</groupId>
                            <artifactId>postgresql</artifactId>
                            <scope>runtime</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.flywaydb</groupId>
                            <artifactId>flyway-core</artifactId>
                        </dependency>

                        <!-- JWT — user token issuance (same version as services) -->
                        <dependency>
                            <groupId>io.jsonwebtoken</groupId>
                            <artifactId>jjwt-api</artifactId>
                            <version>%s</version>
                        </dependency>
                        <dependency>
                            <groupId>io.jsonwebtoken</groupId>
                            <artifactId>jjwt-impl</artifactId>
                            <version>%s</version>
                            <scope>runtime</scope>
                        </dependency>
                        <dependency>
                            <groupId>io.jsonwebtoken</groupId>
                            <artifactId>jjwt-jackson</artifactId>
                            <version>%s</version>
                            <scope>runtime</scope>
                        </dependency>

                        <!-- FractalX Runtime -->
                        <dependency>
                            <groupId>org.fractalx</groupId>
                            <artifactId>fractalx-runtime</artifactId>
                            %s
                        </dependency>

                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(bootVersion, bootVersion,
                JJWT_VERSION, JJWT_VERSION, JJWT_VERSION, runtimeDep);

        Files.writeString(root.resolve("pom.xml"), content);
    }

    // =========================================================================
    // Application class
    // =========================================================================

    private void generateApplicationClass(Path rootPkg) throws IOException {
        String content = """
                package %s;

                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                import org.springframework.boot.context.event.ApplicationReadyEvent;
                import org.springframework.context.event.EventListener;
                import org.springframework.scheduling.annotation.EnableScheduling;
                import org.springframework.stereotype.Component;

                import java.io.IOException;
                import java.net.ServerSocket;

                /**
                 * FractalX Auth Service — auto-generated.
                 *
                 * <p>Issues HMAC-SHA256 JWTs for {@code POST /api/auth/login} and
                 * {@code POST /api/auth/register}. The same {@code JWT_SECRET} must be
                 * configured in the API Gateway's {@code fractalx.gateway.security.bearer.jwt-secret}.
                 */
                @SpringBootApplication
                @EnableScheduling
                public class AuthServiceApplication {
                    public static void main(String[] args) {
                        waitForPort(%d, 20);
                        SpringApplication.run(AuthServiceApplication.class, args);
                    }

                    private static void waitForPort(int port, int timeoutSeconds) {
                        long deadline = System.currentTimeMillis() + timeoutSeconds * 1_000L;
                        while (System.currentTimeMillis() < deadline) {
                            try (ServerSocket ss = new ServerSocket(port)) {
                                return;
                            } catch (IOException e) {
                                try { Thread.sleep(500); } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                            }
                        }
                    }

                    /** Warns loudly at startup if the default JWT secret is in use. */
                    @Component
                    static class JwtSecretChecker {
                        private static final Logger log = LoggerFactory.getLogger(JwtSecretChecker.class);
                        private static final String DEFAULT_SECRET =
                                "fractalx-default-secret-change-in-prod-min-32chars!!";

                        @Value("${jwt.secret:}")
                        private String jwtSecret;

                        @EventListener(ApplicationReadyEvent.class)
                        public void checkSecret() {
                            if (DEFAULT_SECRET.equals(jwtSecret) || jwtSecret.isBlank()) {
                                log.warn("***************************************************************");
                                log.warn("* WARNING: auth-service is using the default JWT secret.     *");
                                log.warn("* Set JWT_SECRET env var before deploying to production!     *");
                                log.warn("***************************************************************");
                            }
                        }
                    }
                }
                """.formatted(BASE_PKG, AUTH_PORT);
        Files.writeString(rootPkg.resolve("AuthServiceApplication.java"), content);
    }

    // =========================================================================
    // YAML configuration
    // =========================================================================

    private void generateApplicationYml(Path res, AuthPattern pattern, FractalxConfig cfg) throws IOException {
        String registryUrl = cfg.registryUrl() != null ? cfg.registryUrl() : "http://localhost:8761";
        String content = """
                spring:
                  application:
                    name: auth-service
                  profiles:
                    active: ${SPRING_PROFILES_ACTIVE:dev}
                  cloud:
                    compatibility-verifier:
                      enabled: false
                  autoconfigure:
                    exclude:
                      - org.springframework.cloud.autoconfigure.LifecycleMvcEndpointAutoConfiguration
                      - org.springframework.cloud.autoconfigure.RefreshAutoConfiguration
                      - org.springframework.cloud.openfeign.FeignAutoConfiguration
                      - org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration
                      - org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClientAutoConfiguration
                      - org.springframework.cloud.client.loadbalancer.LoadBalancerAutoConfiguration

                server:
                  port: %d

                # JWT — MUST match fractalx.gateway.security.bearer.jwt-secret in the API Gateway
                jwt:
                  secret: ${JWT_SECRET:%s}
                  expiration-ms: ${JWT_EXPIRATION_MS:%d}

                fractalx:
                  enabled: true
                  registry:
                    url: ${FRACTALX_REGISTRY_URL:%s}
                    enabled: true
                    host: ${FRACTALX_REGISTRY_HOST:localhost}
                  security:
                    internal-jwt-secret: ${FRACTALX_INTERNAL_JWT_SECRET:fractalx-internal-secret-change-in-prod-!!}

                management:
                  endpoints:
                    web:
                      exposure:
                        include: health,info,metrics,prometheus
                  endpoint:
                    health:
                      show-details: always

                logging:
                  level:
                    org.fractalx: DEBUG
                    org.springframework.security: INFO
                """.formatted(AUTH_PORT, pattern.effectiveSecret(),
                pattern.effectiveExpirationMs(), registryUrl);
        Files.writeString(res.resolve("application.yml"), content);
    }

    private void generateApplicationDevYml(Path res) throws IOException {
        String content = """
                # Dev profile — H2 in-memory, localhost defaults
                spring:
                  datasource:
                    url: jdbc:h2:mem:auth_service
                    driver-class-name: org.h2.Driver
                    username: sa
                    password:
                  jpa:
                    hibernate:
                      ddl-auto: create-drop
                    show-sql: false
                  h2:
                    console:
                      enabled: true
                  flyway:
                    enabled: false
                """;
        Files.writeString(res.resolve("application-dev.yml"), content);
    }

    // =========================================================================
    // Flyway migration
    // =========================================================================

    private void generateFlywayMigration(Path res, Map<String, String> domainFields) throws IOException {
        StringBuilder sb = new StringBuilder("""
                -- FractalX auth-service: user store
                CREATE TABLE IF NOT EXISTS users (
                    id       BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    username VARCHAR(255) NOT NULL UNIQUE,
                    password VARCHAR(255) NOT NULL,
                    role     VARCHAR(50)  NOT NULL DEFAULT 'USER',
                    enabled  BOOLEAN      NOT NULL DEFAULT TRUE""");
        domainFields.forEach((name, type) ->
                sb.append(",\n    ").append(toSnakeCase(name))
                  .append(" ").append(toSqlType(type)).append(" NULL"));
        sb.append("\n);\n");
        Files.writeString(res.resolve("db/migration/V1__init_users.sql"), sb.toString());
    }

    /**
     * Builds the domain-claims block inserted into {@code JwtUtil.generateToken()}.
     * Each claim is stored as a String so it survives the gateway's
     * {@code JwtBearerFilter} forwarding (which converts all non-standard claims to String).
     */
    private static String buildDomainClaimsSnippet(Map<String, String> domainFields) {
        if (domainFields.isEmpty()) return "";
        String innerLines = domainFields.entrySet().stream()
                .map(e -> {
                    String cap = Character.toUpperCase(e.getKey().charAt(0)) + e.getKey().substring(1);
                    // Null-safe: skip null values rather than storing the string "null"
                    return "if (u.get" + cap + "() != null) "
                         + "builder.claim(\"" + e.getKey() + "\", String.valueOf(u.get" + cap + "()));";
                })
                .collect(Collectors.joining("\n                            "));
        return "// Include domain-specific claims so downstream services can\n"
             + "                        // reconstruct them via GatewayPrincipal.getAttribute(name).\n"
             + "                        if (userDetails instanceof " + BASE_PKG + ".model.User u) {\n"
             + "                            " + innerLines + "\n"
             + "                        }";
    }

    /**
     * Builds the setter lines inserted into AuthController.register() for domain fields.
     * The {@code linkedIdField} (e.g. "customerId") is excluded because it is populated
     * by the linked-service HTTP call, not taken from the RegisterRequest.
     */
    private static String buildDomainSettersSnippet(Map<String, String> domainFields,
                                                     String linkedIdField) {
        if (domainFields.isEmpty()) return "";
        return domainFields.keySet().stream()
                .filter(name -> !name.equals(linkedIdField))
                .map(name -> {
                    String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                    return "user.set" + cap + "(request." + name + "());";
                })
                .collect(Collectors.joining("\n                        "));
    }

    private static String toSnakeCase(String camel) {
        return camel.replaceAll("([A-Z])", "_$1").toLowerCase();
    }

    private static String toSqlType(String javaType) {
        return switch (javaType) {
            case "Long", "long"         -> "BIGINT";
            case "Integer", "int"       -> "INTEGER";
            case "Short", "short"       -> "SMALLINT";
            case "Double", "double",
                 "Float", "float"       -> "DECIMAL(19,4)";
            case "Boolean", "boolean"   -> "BOOLEAN";
            case "LocalDate"            -> "DATE";
            case "LocalDateTime",
                 "Instant"             -> "TIMESTAMP";
            default                    -> "VARCHAR(255)";
        };
    }

    // =========================================================================
    // Domain model
    // =========================================================================

    private void generateRoleEnum(Path pkg) throws IOException {
        String content = """
                package %s.model;

                public enum Role {
                    USER, ADMIN
                }
                """.formatted(BASE_PKG);
        Files.writeString(pkg.resolve("Role.java"), content);
    }

    private void generateUserEntity(Path pkg, Map<String, String> domainFields) throws IOException {
        // Build extra field declarations, getters and setters for domain-specific fields
        String extraFields = domainFields.entrySet().stream()
                .map(e -> "    @Column\n    private " + e.getValue() + " " + e.getKey() + ";")
                .collect(Collectors.joining("\n\n"));
        String extraAccessors = domainFields.entrySet().stream()
                .map(e -> {
                    String cap = Character.toUpperCase(e.getKey().charAt(0)) + e.getKey().substring(1);
                    return "    public " + e.getValue() + " get" + cap + "() { return " + e.getKey() + "; }\n"
                         + "    public void set" + cap + "(" + e.getValue() + " v) { this." + e.getKey() + " = v; }";
                })
                .collect(Collectors.joining("\n\n"));

        String content = """
                package %s.model;

                import jakarta.persistence.*;
                import org.springframework.security.core.GrantedAuthority;
                import org.springframework.security.core.authority.SimpleGrantedAuthority;
                import org.springframework.security.core.userdetails.UserDetails;

                import java.util.Collection;
                import java.util.List;

                @Entity
                @Table(name = "users")
                public class User implements UserDetails {

                    @Id
                    @GeneratedValue(strategy = GenerationType.IDENTITY)
                    private Long id;

                    @Column(unique = true, nullable = false)
                    private String username;

                    @Column(nullable = false)
                    private String password;

                    @Enumerated(EnumType.STRING)
                    @Column(nullable = false)
                    private Role role = Role.USER;

                    @Column(nullable = false)
                    private boolean enabled = true;
                %s
                    public User() {}

                    public Long getId()                { return id; }
                    public void setId(Long id)         { this.id = id; }
                    public void setUsername(String u)  { this.username = u; }
                    public void setPassword(String p)  { this.password = p; }
                    public Role getRole()              { return role; }
                    public void setRole(Role r)        { this.role = r; }
                    public void setEnabled(boolean e)  { this.enabled = e; }
                %s
                    @Override public String getUsername() { return username; }
                    @Override public String getPassword() { return password; }
                    @Override public boolean isEnabled()  { return enabled; }
                    @Override public boolean isAccountNonExpired()     { return true; }
                    @Override public boolean isAccountNonLocked()      { return true; }
                    @Override public boolean isCredentialsNonExpired() { return true; }

                    @Override
                    public Collection<? extends GrantedAuthority> getAuthorities() {
                        return List.of(new SimpleGrantedAuthority(role.name()));
                    }
                }
                """.formatted(BASE_PKG,
                        extraFields.isEmpty() ? "" : "\n" + extraFields + "\n",
                        extraAccessors.isEmpty() ? "" : "\n" + extraAccessors + "\n");
        Files.writeString(pkg.resolve("User.java"), content);
    }

    // =========================================================================
    // Repository
    // =========================================================================

    private void generateUserRepository(Path pkg) throws IOException {
        String content = """
                package %s.repository;

                import %s.model.User;
                import org.springframework.data.jpa.repository.JpaRepository;

                import java.util.Optional;

                public interface UserRepository extends JpaRepository<User, Long> {
                    Optional<User> findByUsername(String username);
                }
                """.formatted(BASE_PKG, BASE_PKG);
        Files.writeString(pkg.resolve("UserRepository.java"), content);
    }

    // =========================================================================
    // Security
    // =========================================================================

    private void generateJwtUtil(Path pkg, AuthPattern pattern) throws IOException {
        String content = """
                package %s.security;

                import io.jsonwebtoken.Claims;
                import io.jsonwebtoken.Jwts;
                import io.jsonwebtoken.security.Keys;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.security.core.userdetails.UserDetails;
                import org.springframework.stereotype.Component;

                import javax.crypto.SecretKey;
                import java.nio.charset.StandardCharsets;
                import java.util.Date;
                import java.util.stream.Collectors;

                /**
                 * Issues and validates user JWTs.
                 *
                 * <p>The {@code jwt.secret} property MUST match
                 * {@code fractalx.gateway.security.bearer.jwt-secret} in the API Gateway
                 * so that tokens issued here are accepted by the gateway's JwtBearerFilter.
                 */
                @Component
                public class JwtUtil {

                    private final SecretKey secretKey;
                    private final long      expirationMs;

                    public JwtUtil(
                            @Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration-ms:%d}") long expirationMs) {
                        this.secretKey    = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
                        this.expirationMs = expirationMs;
                    }

                    public String generateToken(UserDetails userDetails) {
                        String roles = userDetails.getAuthorities().stream()
                                .map(a -> a.getAuthority())
                                .collect(Collectors.joining(","));
                        var builder = Jwts.builder()
                                .subject(userDetails.getUsername())
                                .claim("roles", roles)
                                .issuedAt(new Date())
                                .expiration(new Date(System.currentTimeMillis() + expirationMs));
                %s
                        return builder.signWith(secretKey).compact();
                    }

                    public String extractUsername(String token) {
                        return parseClaims(token).getSubject();
                    }

                    public boolean isTokenValid(String token, UserDetails user) {
                        try {
                            String username = extractUsername(token);
                            return username.equals(user.getUsername()) && !isExpired(token);
                        } catch (Exception e) {
                            return false;
                        }
                    }

                    private boolean isExpired(String token) {
                        return parseClaims(token).getExpiration().before(new Date());
                    }

                    private Claims parseClaims(String token) {
                        return Jwts.parser()
                                .verifyWith(secretKey).build()
                                .parseSignedClaims(token).getPayload();
                    }
                }
                """.formatted(BASE_PKG, pattern.effectiveExpirationMs(), buildDomainClaimsSnippet(pattern.domainFields()));
        Files.writeString(pkg.resolve("JwtUtil.java"), content);
    }

    private void generateUserDetailsServiceImpl(Path pkg) throws IOException {
        String content = """
                package %s.security;

                import %s.repository.UserRepository;
                import org.springframework.security.core.userdetails.UserDetails;
                import org.springframework.security.core.userdetails.UserDetailsService;
                import org.springframework.security.core.userdetails.UsernameNotFoundException;
                import org.springframework.stereotype.Service;

                @Service
                public class UserDetailsServiceImpl implements UserDetailsService {

                    private final UserRepository userRepository;

                    public UserDetailsServiceImpl(UserRepository userRepository) {
                        this.userRepository = userRepository;
                    }

                    @Override
                    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
                        return userRepository.findByUsername(username)
                                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
                    }
                }
                """.formatted(BASE_PKG, BASE_PKG);
        Files.writeString(pkg.resolve("UserDetailsServiceImpl.java"), content);
    }

    private void generateGatewayAuthHeaderFilter(Path pkg) throws IOException {
        // Same logic as ServiceSecurityStep — validates X-Internal-Token from gateway
        String content = """
                package %s.security;

                import io.jsonwebtoken.Claims;
                import io.jsonwebtoken.Jwts;
                import io.jsonwebtoken.security.Keys;
                import jakarta.servlet.FilterChain;
                import jakarta.servlet.ServletException;
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.servlet.http.HttpServletResponse;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
                import org.springframework.security.core.authority.SimpleGrantedAuthority;
                import org.springframework.security.core.context.SecurityContextHolder;
                import org.springframework.web.filter.OncePerRequestFilter;

                import javax.crypto.SecretKey;
                import java.io.IOException;
                import java.nio.charset.StandardCharsets;
                import java.util.Arrays;
                import java.util.List;
                import java.util.stream.Collectors;

                /**
                 * Validates the {@code X-Internal-Token} forwarded by the API Gateway for
                 * protected endpoints (e.g. a future {@code /api/auth/me}).
                 * Public endpoints ({@code /api/auth/login}, {@code /api/auth/register}) are
                 * excluded by {@link SecurityConfig}.
                 */
                public class GatewayAuthHeaderFilter extends OncePerRequestFilter {

                    @Value("${fractalx.security.internal-jwt-secret:fractalx-internal-secret-change-in-prod-!!}")
                    private String internalJwtSecret;

                    @Override
                    protected void doFilterInternal(HttpServletRequest request,
                                                     HttpServletResponse response,
                                                     FilterChain filterChain)
                            throws ServletException, IOException {

                        String internalToken = request.getHeader("X-Internal-Token");
                        if (internalToken != null && !internalToken.isBlank()) {
                            try {
                                SecretKey key = Keys.hmacShaKeyFor(
                                        internalJwtSecret.getBytes(StandardCharsets.UTF_8));
                                Claims claims = Jwts.parser()
                                        .verifyWith(key).build()
                                        .parseSignedClaims(internalToken).getPayload();

                                String userId   = claims.getSubject();
                                String rolesStr = claims.get("roles", String.class);
                                List<SimpleGrantedAuthority> authorities =
                                        (rolesStr == null || rolesStr.isBlank())
                                                ? List.of()
                                                : Arrays.stream(rolesStr.split(","))
                                                         .map(String::trim)
                                                         .filter(r -> !r.isBlank())
                                                         .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                                                         .collect(Collectors.toList());

                                var auth = new UsernamePasswordAuthenticationToken(
                                        userId, internalToken, authorities);
                                SecurityContextHolder.getContext().setAuthentication(auth);
                            } catch (Exception e) {
                                logger.warn("GatewayAuthHeaderFilter: invalid X-Internal-Token — " + e.getMessage());
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json");
                                response.getWriter().write("{\\"error\\":\\"invalid-internal-token\\"}");
                                return;
                            }
                        }
                        filterChain.doFilter(request, response);
                    }
                }
                """.formatted(BASE_PKG);
        Files.writeString(pkg.resolve("GatewayAuthHeaderFilter.java"), content);
    }

    private void generateSecurityConfig(Path pkg) throws IOException {
        // Auth-service security: permits /api/auth/** publicly, protects everything else.
        // Also provides DaoAuthenticationProvider so AuthController can call authenticationManager.
        String content = """
                package %s.security;

                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.security.authentication.AuthenticationManager;
                import org.springframework.security.authentication.AuthenticationProvider;
                import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
                import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
                import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
                import org.springframework.security.config.http.SessionCreationPolicy;
                import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
                import org.springframework.security.crypto.password.PasswordEncoder;
                import org.springframework.security.web.SecurityFilterChain;
                import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

                /**
                 * Security configuration for the auth-service.
                 *
                 * <ul>
                 *   <li>{@code /api/auth/**} — permitted without authentication (login + register)</li>
                 *   <li>{@code /actuator/**} — permitted for health checks</li>
                 *   <li>Everything else — requires a valid {@code X-Internal-Token} from the gateway</li>
                 * </ul>
                 */
                @Configuration
                @EnableWebSecurity
                @EnableMethodSecurity
                public class SecurityConfig {

                    private final UserDetailsServiceImpl userDetailsService;

                    public SecurityConfig(UserDetailsServiceImpl userDetailsService) {
                        this.userDetailsService = userDetailsService;
                    }

                    @Bean
                    public SecurityFilterChain securityFilterChain(
                            HttpSecurity http,
                            GatewayAuthHeaderFilter authFilter) throws Exception {

                        http.csrf(csrf -> csrf.disable())
                            .sessionManagement(sm -> sm
                                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                            .authenticationProvider(authenticationProvider())
                            .authorizeHttpRequests(auth -> auth
                                    .requestMatchers("/api/auth/**").permitAll()
                                    .requestMatchers("/actuator/**").permitAll()
                                    .anyRequest().authenticated()
                            )
                            .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class);

                        return http.build();
                    }

                    @Bean
                    public GatewayAuthHeaderFilter gatewayAuthHeaderFilter() {
                        return new GatewayAuthHeaderFilter();
                    }

                    @Bean
                    public AuthenticationProvider authenticationProvider() {
                        // Spring Security 6+: constructor requires UserDetailsService
                        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
                        provider.setPasswordEncoder(passwordEncoder());
                        return provider;
                    }

                    @Bean
                    public AuthenticationManager authenticationManager(
                            AuthenticationConfiguration config) throws Exception {
                        return config.getAuthenticationManager();
                    }

                    @Bean
                    public PasswordEncoder passwordEncoder() {
                        return new BCryptPasswordEncoder();
                    }
                }
                """.formatted(BASE_PKG);
        Files.writeString(pkg.resolve("SecurityConfig.java"), content);
    }

    // =========================================================================
    // Controller + DTOs
    // =========================================================================

    private void generateDtos(Path pkg, Map<String, String> domainFields,
                              String linkedIdField) throws IOException {
        Files.writeString(pkg.resolve("AuthRequest.java"), """
                package %s.controller;

                public record AuthRequest(String username, String password) {}
                """.formatted(BASE_PKG));

        // Include domain fields in RegisterRequest so callers can supply them on sign-up.
        // Exclude the linked-service id field (e.g. customerId) — it is set by the linked
        // service during registration, not supplied by the caller.
        String extraParams = domainFields.entrySet().stream()
                .filter(e -> !e.getKey().equals(linkedIdField))
                .map(e -> e.getValue() + " " + e.getKey())
                .collect(Collectors.joining(", "));
        String registerParams = "String username, String password"
                + (extraParams.isEmpty() ? "" : ", " + extraParams);
        Files.writeString(pkg.resolve("RegisterRequest.java"),
                "package " + BASE_PKG + ".controller;\n\n"
                + "public record RegisterRequest(" + registerParams + ") {}\n");

        Files.writeString(pkg.resolve("AuthResponse.java"), """
                package %s.controller;

                public record AuthResponse(String token, String username, String role) {}
                """.formatted(BASE_PKG));
    }

    private void generateAuthController(Path pkg, Map<String, String> domainFields,
                                         AuthPattern pattern) throws IOException {
        boolean hasLinked = pattern.registerLinkedService() != null;

        // Extra imports and fields only needed when a cross-module service call is generated
        String extraImports = hasLinked ? """
                import io.jsonwebtoken.Jwts;
                import io.jsonwebtoken.security.Keys;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.http.HttpEntity;
                import org.springframework.http.HttpHeaders;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.client.RestTemplate;
                import javax.crypto.SecretKey;
                import java.nio.charset.StandardCharsets;
                import java.util.Date;
                import java.util.Map;
                """ : "";

        String extraFields = hasLinked ? """

                    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

                    @Value("${fractalx.services.%s:http://localhost:8081}")
                    private String %sUrl;

                    @Value("${fractalx.security.internal-jwt-secret:fractalx-internal-secret-change-in-prod-!!}")
                    private String internalJwtSecret;

                    private final RestTemplate restTemplate = new RestTemplate();
                """.formatted(pattern.registerLinkedService(),
                              servicePropKey(pattern.registerLinkedService())) : "";

        // The linked-service call inserted before userRepository.save(user)
        String linkedServiceCall = hasLinked ? buildLinkedServiceCallSnippet(pattern) : "";

        String content = """
                package %s.controller;

                import %s.model.Role;
                import %s.model.User;
                import %s.repository.UserRepository;
                import %s.security.JwtUtil;
                import org.springframework.http.HttpStatus;
                import org.springframework.security.authentication.AuthenticationManager;
                import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
                import org.springframework.security.core.Authentication;
                import org.springframework.security.core.userdetails.UserDetails;
                import org.springframework.security.crypto.password.PasswordEncoder;
                import org.springframework.web.bind.annotation.*;
                import org.springframework.web.server.ResponseStatusException;
                %s
                /**
                 * Auto-generated by FractalX.
                 *
                 * <p>Provides JWT-based authentication for the decomposed microservices system.
                 * The issued tokens are HMAC-SHA256 signed with {@code jwt.secret} — the same
                 * secret configured in the API Gateway's {@code fractalx.gateway.security.bearer.jwt-secret}.
                 *
                 * <p>Endpoints:
                 * <ul>
                 *   <li>{@code POST /api/auth/login}    — authenticate and receive a JWT</li>
                 *   <li>{@code POST /api/auth/register} — create an account and receive a JWT</li>
                 * </ul>
                 */
                @RestController
                @RequestMapping("/api/auth")
                public class AuthController {

                    private final AuthenticationManager authenticationManager;
                    private final UserRepository        userRepository;
                    private final PasswordEncoder       passwordEncoder;
                    private final JwtUtil               jwtUtil;
                %s
                    public AuthController(AuthenticationManager authenticationManager,
                                          UserRepository userRepository,
                                          PasswordEncoder passwordEncoder,
                                          JwtUtil jwtUtil) {
                        this.authenticationManager = authenticationManager;
                        this.userRepository        = userRepository;
                        this.passwordEncoder       = passwordEncoder;
                        this.jwtUtil               = jwtUtil;
                    }

                    @PostMapping("/login")
                    public AuthResponse login(@RequestBody AuthRequest request) {
                        Authentication auth = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                        request.username(), request.password()));
                        UserDetails user = (UserDetails) auth.getPrincipal();
                        String token = jwtUtil.generateToken(user);
                        String role  = user.getAuthorities().stream()
                                .findFirst().map(a -> a.getAuthority()).orElse("USER");
                        return new AuthResponse(token, user.getUsername(), role);
                    }

                    @PostMapping("/register")
                    @ResponseStatus(HttpStatus.CREATED)
                    public AuthResponse register(@RequestBody RegisterRequest request) {
                        if (userRepository.findByUsername(request.username()).isPresent()) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Username already exists: " + request.username());
                        }
                        User user = new User();
                        user.setUsername(request.username());
                        user.setPassword(passwordEncoder.encode(request.password()));
                        user.setRole(Role.USER);
                %s
                %s
                        userRepository.save(user);
                        String token = jwtUtil.generateToken(user);
                        return new AuthResponse(token, user.getUsername(), user.getRole().name());
                    }
                }
                """.formatted(BASE_PKG, BASE_PKG, BASE_PKG, BASE_PKG, BASE_PKG,
                        extraImports, extraFields,
                        buildDomainSettersSnippet(domainFields, pattern.registerLinkedIdField()),
                        linkedServiceCall);
        Files.writeString(pkg.resolve("AuthController.java"), content);
    }

    /**
     * Builds the snippet that calls the linked cross-module service to create the associated
     * entity and sets its ID on the user (e.g. calls customer-service and sets customerId).
     * The call includes a freshly-minted {@code X-Internal-Token} so the downstream service's
     * {@code GatewayAuthHeaderFilter} accepts it.
     */
    private static String buildLinkedServiceCallSnippet(AuthPattern pattern) {
        String propKey = servicePropKey(pattern.registerLinkedService());
        String idField = pattern.registerLinkedIdField();                     // e.g. "customerId"
        String cap     = Character.toUpperCase(idField.charAt(0)) + idField.substring(1);
        String path    = pattern.registerLinkedPath();                        // e.g. "/api/customers"

        return """
                        // Call %s to create the linked entity and obtain its ID.
                        // URL configured via fractalx.services.%s in application.yml / application-dev.yml.
                        // Includes X-Internal-Token so the downstream GatewayAuthHeaderFilter accepts the request.
                        try {
                            SecretKey _key = Keys.hmacShaKeyFor(
                                    internalJwtSecret.getBytes(StandardCharsets.UTF_8));
                            String _internalToken = Jwts.builder()
                                    .subject("auth-service")
                                    .claim("roles", "")
                                    .issuer("fractalx-gateway")
                                    .issuedAt(new Date())
                                    .expiration(new Date(System.currentTimeMillis() + 30_000L))
                                    .signWith(_key)
                                    .compact();
                            HttpHeaders _headers = new HttpHeaders();
                            _headers.set("X-Internal-Token", _internalToken);
                            _headers.set("Content-Type", "application/json");
                            @SuppressWarnings("unchecked")
                            HttpEntity<Map<String, Object>> _req = new HttpEntity<>(
                                    Map.of("name", request.username(),
                                           "email", request.username() + "@example.com"),
                                    _headers);
                            ResponseEntity<Map> resp = restTemplate.postForEntity(
                                    %sUrl + "%s", _req, Map.class);
                            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                                Object id = resp.getBody().get("id");
                                if (id instanceof Number n) user.set%s(n.longValue());
                            }
                        } catch (Exception e) {
                            log.warn("Could not create linked entity in %s for user {}: {}",
                                     request.username(), e.getMessage());
                            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                    "Registration incomplete: could not reach %s. Please try again.");
                        }
                """.formatted(pattern.registerLinkedService(), propKey, propKey, path, cap,
                              pattern.registerLinkedService(), pattern.registerLinkedService());
    }

    /** Converts a service name like {@code customer-service} to a Java identifier like {@code customerService}. */
    private static String servicePropKey(String serviceName) {
        // "customer-service" → "customerService"
        String[] parts = serviceName.split("-");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    /**
     * Injects the linked service URL property into {@code application-dev.yml} so the
     * generated auth-service knows where to reach the other service.
     */
    private void injectLinkedServiceUrl(Path srcMainResources, AuthPattern pattern) throws IOException {
        Path devYml = srcMainResources.resolve("application-dev.yml");
        if (!Files.exists(devYml)) return;
        String existing = Files.readString(devYml);
        String propKey  = servicePropKey(pattern.registerLinkedService());
        if (existing.contains("fractalx.services." + propKey)
                || existing.contains(propKey + ":")) return;
        String block = """

                # URL of %s — used by AuthController.register() to create the linked entity.
                # Override with env var if the service runs on a different host/port.
                fractalx:
                  services:
                    %s: ${%s_URL:http://localhost:%s}
                """.formatted(pattern.registerLinkedService(), propKey,
                              pattern.registerLinkedService().toUpperCase().replace("-", "_"),
                              guessServicePort(pattern.registerLinkedService()));
        Files.writeString(devYml, existing + block);
    }

    /** Best-effort port guess from the service name (falls back to 8080). */
    private static int guessServicePort(String serviceName) {
        // Common convention used in the demo; a real impl would query the registry manifest
        return switch (serviceName) {
            case "customer-service" -> 8081;
            case "order-service"    -> 8082;
            default                 -> 8080;
        };
    }

    // =========================================================================
    // Self-registration (mirrors ServiceRegistrationStep)
    // =========================================================================

    private void generateRegistryClient(Path pkg, FractalxConfig cfg) throws IOException {
        String content = """
                package %s.registration;

                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.stereotype.Component;
                import org.springframework.web.client.RestTemplate;

                import java.util.Map;

                @Component
                public class FractalRegistryClient {

                    private static final Logger log = LoggerFactory.getLogger(FractalRegistryClient.class);

                    @Value("${fractalx.registry.url:http://localhost:8761}")
                    private String registryUrl;

                    private final RestTemplate restTemplate = new RestTemplate();

                    public void register(String name, String host, int port, int grpcPort, String healthUrl) {
                        try {
                            Map<String, Object> payload = Map.of(
                                    "name", name, "host", host,
                                    "port", port, "grpcPort", grpcPort, "healthUrl", healthUrl);
                            restTemplate.postForObject(registryUrl + "/services", payload, Object.class);
                            log.info("Registered with fractalx-registry: {} at {}:{}", name, host, port);
                        } catch (Exception e) {
                            log.warn("Could not register with fractalx-registry at '{}': {}", registryUrl, e.getMessage());
                        }
                    }

                    public void deregister(String name) {
                        try {
                            restTemplate.delete(registryUrl + "/services/" + name + "/deregister");
                            log.info("Deregistered from fractalx-registry: {}", name);
                        } catch (Exception e) {
                            log.warn("Could not deregister: {}", e.getMessage());
                        }
                    }

                    public boolean heartbeat(String name) {
                        try {
                            restTemplate.postForObject(registryUrl + "/services/" + name + "/heartbeat",
                                    null, Void.class);
                            return true;
                        } catch (Exception e) {
                            log.trace("Heartbeat failed for {}: {}", name, e.getMessage());
                            return false;
                        }
                    }
                }
                """.formatted(BASE_PKG);
        Files.writeString(pkg.resolve("FractalRegistryClient.java"), content);
    }

    private void generateServiceRegistrationAutoConfig(Path pkg) throws IOException {
        String content = """
                package %s.registration;

                import jakarta.annotation.PostConstruct;
                import jakarta.annotation.PreDestroy;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
                import org.springframework.scheduling.annotation.Scheduled;
                import org.springframework.stereotype.Component;

                @Component
                @ConditionalOnProperty(name = "fractalx.registry.enabled", havingValue = "true", matchIfMissing = true)
                public class ServiceRegistrationAutoConfig {

                    private final FractalRegistryClient registryClient;

                    @Value("${spring.application.name:auth-service}")
                    private String serviceName;

                    @Value("${fractalx.registry.host:localhost}")
                    private String serviceHost;

                    @Value("${server.port:%d}")
                    private int httpPort;

                    public ServiceRegistrationAutoConfig(FractalRegistryClient registryClient) {
                        this.registryClient = registryClient;
                    }

                    @PostConstruct
                    public void onStartup() {
                        String healthUrl = "http://" + serviceHost + ":" + httpPort + "/actuator/health";
                        registryClient.register(serviceName, serviceHost, httpPort, %d, healthUrl);
                    }

                    @Scheduled(fixedDelay = 5_000)
                    public void sendHeartbeat() {
                        boolean ok = registryClient.heartbeat(serviceName);
                        if (!ok) {
                            onStartup();
                        }
                    }

                    @PreDestroy
                    public void onShutdown() {
                        registryClient.deregister(serviceName);
                    }
                }
                """.formatted(BASE_PKG, AUTH_PORT, AUTH_GRPC_PORT);
        Files.writeString(pkg.resolve("ServiceRegistrationAutoConfig.java"), content);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Path mkpkg(Path srcMainJava, String subPkg) throws IOException {
        Path p = subPkg.isEmpty()
                ? srcMainJava.resolve(BASE_PKG.replace('.', '/'))
                : srcMainJava.resolve((BASE_PKG + "." + subPkg).replace('.', '/'));
        Files.createDirectories(p);
        return p;
    }
}
