package org.fractalx.core.gateway;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import org.fractalx.core.auth.AuthPattern;
import org.fractalx.core.config.FractalxConfig;
import org.fractalx.core.model.FractalModule;
import org.fractalx.core.gateway.RouteDefinition;
import org.fractalx.core.util.SpringBootVersionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Generates Spring Cloud Gateway configuration
 */
public class GatewayConfigGenerator {
    private static final Logger log = LoggerFactory.getLogger(GatewayConfigGenerator.class);
    private static final int GATEWAY_PORT = 9999;

    // Track used ports to avoid conflicts
    private final Map<Integer, Boolean> usedPorts = new HashMap<>();

    /** When non-null, used to scan controllers for smart route generation. */
    private Path monolithSrc;

    /** HTTP verb → Spring annotation simple name, used for controller scanning. */
    private static final Map<String, String> VERB_ANNOTATIONS = Map.of(
            "GET",    "GetMapping",
            "POST",   "PostMapping",
            "PUT",    "PutMapping",
            "DELETE", "DeleteMapping",
            "PATCH",  "PatchMapping"
    );

    public GatewayConfigGenerator() {
        // Reserve gateway port
        usedPorts.put(GATEWAY_PORT, true);
    }

    /**
     * Constructs a generator that uses controller-scanning for smart route generation.
     *
     * @param monolithSrc path to the monolith's {@code src/main/java} directory
     */
    public GatewayConfigGenerator(Path monolithSrc) {
        usedPorts.put(GATEWAY_PORT, true);
        this.monolithSrc = monolithSrc;
    }

    public void generateConfig(Path srcMainResources,
                               List<FractalModule> modules,
                               List<RouteDefinition> routes) throws IOException {
        generateConfig(srcMainResources, modules, routes, FractalxConfig.defaults(), SecurityProfile.none());
    }

    public void generateConfig(Path srcMainResources,
                               List<FractalModule> modules,
                               List<RouteDefinition> routes,
                               FractalxConfig cfg) throws IOException {
        generateConfig(srcMainResources, modules, routes, cfg, SecurityProfile.none());
    }

    public void generateConfig(Path srcMainResources,
                               List<FractalModule> modules,
                               List<RouteDefinition> routes,
                               FractalxConfig cfg,
                               SecurityProfile securityProfile) throws IOException {
        generateConfig(srcMainResources, modules, routes, cfg, securityProfile, null);
    }

    public void generateConfig(Path srcMainResources,
                               List<FractalModule> modules,
                               List<RouteDefinition> routes,
                               FractalxConfig cfg,
                               SecurityProfile securityProfile,
                               AuthPattern authPattern) throws IOException {
        log.debug("Generating gateway configuration");
        Files.writeString(srcMainResources.resolve("application.yml"),
                generateApplicationYml(modules, cfg, securityProfile, authPattern));
        log.info("✓ Generated gateway configuration");
    }

    /**
     * Overload that also accepts a monolith source root for controller-scanning based
     * route generation. Sets {@link #monolithSrc} then delegates to the 6-param overload.
     */
    public void generateConfig(Path srcMainResources,
                               List<FractalModule> modules,
                               List<RouteDefinition> routes,
                               FractalxConfig cfg,
                               SecurityProfile securityProfile,
                               AuthPattern authPattern,
                               Path monolithSrc) throws IOException {
        this.monolithSrc = monolithSrc;
        generateConfig(srcMainResources, modules, routes, cfg, securityProfile, authPattern);
    }

    private String generateApplicationYml(List<FractalModule> modules, FractalxConfig cfg,
                                           SecurityProfile securityProfile) {
        return generateApplicationYml(modules, cfg, securityProfile, null);
    }

    private String generateApplicationYml(List<FractalModule> modules, FractalxConfig cfg,
                                           SecurityProfile securityProfile, AuthPattern authPattern) {
        if (modules == null || modules.isEmpty()) {
            log.warn("No modules provided for gateway configuration");
            modules = new ArrayList<>(); // Use empty list to avoid NPE
        }

        StringBuilder routesConfig = new StringBuilder();
        routesConfig.append(generateRoutesSection(modules, authPattern));

        // Build the YAML step by step for better control
        StringBuilder ymlBuilder = new StringBuilder();

        ymlBuilder.append("server:\n");
        ymlBuilder.append("  port: ").append(cfg.gatewayPort()).append("\n\n");

        ymlBuilder.append("spring:\n");
        ymlBuilder.append("  application:\n");
        ymlBuilder.append("    name: fractalx-gateway\n");
        ymlBuilder.append("  profiles:\n");
        ymlBuilder.append("    active: ${SPRING_PROFILES_ACTIVE:dev}\n");
        // NOTE: The gateway is pinned to Spring Boot 3.x (Spring Cloud 2024.x / Gateway 4.2.x).
        // Spring Cloud Gateway 4.3.x has a binary incompatibility with Spring Framework 7.0.3+
        // (HttpHeaders.containsKey(Object) removed). Boot 3.x uses Spring Framework 6.x which
        // is unaffected. The gateway is a pure HTTP proxy so the version mismatch is safe.
        ymlBuilder.append("  main:\n");
        ymlBuilder.append("    web-application-type: reactive\n\n");
        ymlBuilder.append("  cloud:\n");
        ymlBuilder.append("    gateway:\n");
        ymlBuilder.append("      default-filters:\n");
        ymlBuilder.append("        - name: Retry\n");
        ymlBuilder.append("          args:\n");
        ymlBuilder.append("            retries: 2\n");
        ymlBuilder.append("            statuses: BAD_GATEWAY,SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT\n");
        ymlBuilder.append("      routes:\n");
        ymlBuilder.append(routesConfig.toString());
        ymlBuilder.append("\n");

        // Security defaults driven by detected monolith security profile
        boolean secEnabled  = securityProfile.securityEnabled();
        boolean bearerOn    = securityProfile.authType() == SecurityProfile.AuthType.BEARER_JWT;
        boolean oauth2On    = securityProfile.authType() == SecurityProfile.AuthType.OAUTH2;
        boolean basicOn     = securityProfile.authType() == SecurityProfile.AuthType.BASIC;
        // Prefer: (1) security profile secret, (2) auth-pattern detected secret, (3) default
        String  jwtSecret   = securityProfile.jwtSecret() != null ? securityProfile.jwtSecret()
                              : (authPattern != null && authPattern.detected())
                                    ? authPattern.effectiveSecret()
                                    : "fractalx-default-secret-change-in-prod-min-32chars!!";
        String  jwkUri      = securityProfile.jwkSetUri()   != null ? securityProfile.jwkSetUri()
                              : securityProfile.issuerUri() != null ? securityProfile.issuerUri()
                              : cfg.oauth2JwksUri();
        String  basicUser   = securityProfile.basicUsername() != null ? securityProfile.basicUsername() : "fractalx";
        String  basicPass   = securityProfile.basicPassword() != null ? securityProfile.basicPassword() : "changeme";

        ymlBuilder.append("fractalx:\n");
        ymlBuilder.append("  registry:\n");
        ymlBuilder.append("    url: ${FRACTALX_REGISTRY_URL:").append(cfg.registryUrl()).append("}\n");
        ymlBuilder.append("  observability:\n");
        ymlBuilder.append("    otel:\n");
        ymlBuilder.append("      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}\n");
        ymlBuilder.append("  gateway:\n");
        ymlBuilder.append("    security:\n");
        ymlBuilder.append("      # Auth type detected from monolith: ").append(securityProfile.authType()).append("\n");
        ymlBuilder.append("      enabled: ${GATEWAY_SECURITY_ENABLED:").append(secEnabled).append("}\n");
        ymlBuilder.append("      public-paths: /api/*/public/**, /api/*/auth/**\n");
        ymlBuilder.append("      bearer:\n");
        ymlBuilder.append("        enabled: ${GATEWAY_BEARER_ENABLED:").append(bearerOn).append("}\n");
        ymlBuilder.append("        jwt-secret: ${JWT_SECRET:").append(jwtSecret).append("}\n");
        ymlBuilder.append("      oauth2:\n");
        ymlBuilder.append("        enabled: ${GATEWAY_OAUTH2_ENABLED:").append(oauth2On).append("}\n");
        ymlBuilder.append("        jwk-set-uri: ${OAUTH2_JWK_URI:").append(jwkUri).append("}\n");
        ymlBuilder.append("      basic:\n");
        ymlBuilder.append("        enabled: ${GATEWAY_BASIC_ENABLED:").append(basicOn).append("}\n");
        ymlBuilder.append("        username: ${GATEWAY_BASIC_USER:").append(basicUser).append("}\n");
        ymlBuilder.append("        password: ${GATEWAY_BASIC_PASS:").append(basicPass).append("}\n");
        ymlBuilder.append("      api-key:\n");
        ymlBuilder.append("        enabled: ${GATEWAY_APIKEY_ENABLED:false}\n");
        ymlBuilder.append("        valid-keys:\n");
        ymlBuilder.append("          - ${GATEWAY_API_KEY_1:dev-key-replace-me}\n");
        // Internal Call Token secret — must match fractalx.security.internal-jwt-secret in all services
        ymlBuilder.append("      # Internal Call Token: short-lived signed JWT forwarded to downstream services.\n");
        ymlBuilder.append("      # Set FRACTALX_INTERNAL_JWT_SECRET to the same value on all services + gateway.\n");
        ymlBuilder.append("      internal-jwt-secret: ${FRACTALX_INTERNAL_JWT_SECRET:fractalx-internal-secret-change-in-prod-!!}\n");
        ymlBuilder.append("    cors:\n");
        ymlBuilder.append("      allowed-origins: ${CORS_ORIGINS:").append(cfg.corsAllowedOrigins()).append("}\n");
        ymlBuilder.append("      allowed-methods: GET,POST,PUT,DELETE,PATCH,OPTIONS\n");
        ymlBuilder.append("      allow-credentials: true\n");
        ymlBuilder.append("    rate-limit:\n");
        ymlBuilder.append("      default-rps: ${GATEWAY_DEFAULT_RPS:100}\n");
        ymlBuilder.append("\n");

        // Resilience4j circuit breaker config per service (fixes missing YAML bug)
        if (modules != null && !modules.isEmpty()) {
            FractalxConfig.ResilienceDefaults r = cfg.resilience();
            ymlBuilder.append("resilience4j:\n");
            ymlBuilder.append("  circuitbreaker:\n    instances:\n");
            for (FractalModule m : modules) {
                ymlBuilder.append("      ").append(m.getServiceName()).append(":\n");
                ymlBuilder.append("        failure-rate-threshold: ").append(r.failureRateThreshold()).append("\n");
                ymlBuilder.append("        wait-duration-in-open-state: ").append(r.waitDurationInOpenState()).append("\n");
                ymlBuilder.append("        permitted-number-of-calls-in-half-open-state: ").append(r.permittedCallsInHalfOpenState()).append("\n");
                ymlBuilder.append("        sliding-window-size: ").append(r.slidingWindowSize()).append("\n");
            }
            ymlBuilder.append("  timelimiter:\n    instances:\n");
            for (FractalModule m : modules) {
                ymlBuilder.append("      ").append(m.getServiceName()).append(":\n");
                ymlBuilder.append("        timeout-duration: ").append(r.timeoutDuration()).append("\n");
            }
            ymlBuilder.append("\n");
        }

        ymlBuilder.append("management:\n");
        ymlBuilder.append("  endpoints:\n");
        ymlBuilder.append("    web:\n");
        ymlBuilder.append("      exposure:\n");
        ymlBuilder.append("        include: health,info,gateway,routes,metrics\n");
        ymlBuilder.append("  endpoint:\n");
        if (!SpringBootVersionUtil.isBoot4Plus(cfg.springBootVersion())) {
            // management.endpoint.gateway.enabled was removed in Spring Boot 4.x
            ymlBuilder.append("    gateway:\n");
            ymlBuilder.append("      enabled: true\n");
        }
        ymlBuilder.append("    health:\n");
        ymlBuilder.append("      show-details: always\n");
        ymlBuilder.append("  tracing:\n");
        ymlBuilder.append("    sampling:\n");
        ymlBuilder.append("      probability: 1.0\n");
        if (SpringBootVersionUtil.isBoot4Plus(cfg.springBootVersion())) {
            // Spring Boot 4.x: configure OTel exporter via management.otlp.tracing.endpoint
            // (custom OtelConfig bean conflicts with Boot 4.x managed OTel dependency versions)
            ymlBuilder.append("  otlp:\n");
            ymlBuilder.append("    tracing:\n");
            ymlBuilder.append("      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}/v1/traces\n");
        }
        ymlBuilder.append("\n");

        ymlBuilder.append("logging:\n");
        ymlBuilder.append("  level:\n");
        ymlBuilder.append("    org.springframework.cloud.gateway: INFO\n");
        ymlBuilder.append("    org.fractalx.gateway: INFO\n");
        ymlBuilder.append("    reactor.netty: DEBUG\n");
        ymlBuilder.append("    com.netflix.eureka: OFF\n");
        ymlBuilder.append("    com.netflix.discovery: OFF\n");

        return ymlBuilder.toString();
    }

    // ── Route section generation ──────────────────────────────────────────────

    /**
     * Builds the full YAML block for all gateway routes.
     *
     * <p>When {@link #monolithSrc} is set, uses controller-scanning to determine
     * which paths each module owns (including cross-resource endpoints). When
     * {@code monolithSrc} is {@code null}, falls back to the name-based heuristic
     * (backward-compatible).
     */
    private String generateRoutesSection(List<FractalModule> modules, AuthPattern authPattern) {
        StringBuilder sb = new StringBuilder();

        // Auth-service route always comes first when auth was detected
        if (authPattern != null && authPattern.detected()) {
            sb.append(generateAuthServiceRoute());
            log.info("Prepending auth-service route (/api/auth/**) to gateway configuration");
        }

        if (monolithSrc != null && !modules.isEmpty()) {
            // ── Controller-scanning mode ──────────────────────────────────────
            // Step 1: scan each module's package tree and collect normalised paths
            Map<FractalModule, Set<String>> modulePaths = new LinkedHashMap<>();
            for (FractalModule module : modules) {
                Set<String> paths = scanModulePaths(module);
                modulePaths.put(module, paths);
            }

            // Step 2: build primary prefix map — last segment of packageName → module
            // e.g. com.example.order → /api/order
            Map<FractalModule, String> primaryPrefix = new LinkedHashMap<>();
            for (FractalModule module : modules) {
                String pkg = module.getPackageName();
                String lastSegment = pkg != null && pkg.contains(".")
                        ? pkg.substring(pkg.lastIndexOf('.') + 1)
                        : (pkg != null ? pkg : module.getServiceName().replace("-service", ""));
                primaryPrefix.put(module, "/api/" + lastSegment);
            }

            // Step 3: partition each module's paths into own vs. foreign
            Map<FractalModule, Set<String>> ownPaths     = new LinkedHashMap<>();
            Map<FractalModule, Set<String>> foreignPaths = new LinkedHashMap<>();
            for (FractalModule module : modules) {
                String myPrefix     = primaryPrefix.get(module);
                String myPluralPfx  = myPrefix.endsWith("s") ? myPrefix : myPrefix + "s";
                Set<String> own     = new LinkedHashSet<>();
                Set<String> foreign = new LinkedHashSet<>();
                for (String path : modulePaths.get(module)) {
                    if (path.startsWith(myPrefix) || path.startsWith(myPluralPfx)) {
                        own.add(path);
                    } else {
                        // Check whether this path starts with any other module's primary prefix
                        boolean isForeign = modules.stream()
                                .filter(m -> m != module)
                                .anyMatch(m -> {
                                    String op = primaryPrefix.get(m);
                                    String opp = op.endsWith("s") ? op : op + "s";
                                    return path.startsWith(op) || path.startsWith(opp);
                                });
                        if (isForeign) {
                            foreign.add(path);
                        } else {
                            own.add(path); // unclassified → treat as own
                        }
                    }
                }
                ownPaths.put(module, own);
                foreignPaths.put(module, foreign);
            }

            // Step 4a: emit cross-resource routes (before general routes)
            for (FractalModule module : modules) {
                Set<String> foreign = foreignPaths.get(module);
                if (foreign.isEmpty()) continue;

                // Build a de-duplicated list of path predicates: for each foreign path,
                // add it plus a /** sub-resource variant
                Set<String> predicates = new LinkedHashSet<>();
                for (String path : foreign) {
                    // Normalize: replace {variable} segments with *
                    String normalized = normalizePathVars(path);
                    predicates.add(normalized);
                    // Also cover sub-resources (e.g. /api/customers/*/orders/**)
                    if (!normalized.endsWith("/**")) {
                        predicates.add(normalized + "/**");
                    }
                }

                // The cross-resource route targets the same service port — do not consume a new port.
                sb.append("        # ").append(module.getServiceName()).append(" cross-resource routes\n");
                sb.append("        - id: ").append(module.getServiceName()).append("-cross\n");
                sb.append("          uri: http://localhost:").append(module.getPort()).append("\n");
                sb.append("          predicates:\n");
                sb.append("            - Path=").append(String.join(",", predicates)).append("\n");
                sb.append("          filters:\n");
                sb.append("            - StripPrefix=0\n");
                sb.append("            - name: CircuitBreaker\n");
                sb.append("              args:\n");
                sb.append("                name: ").append(module.getServiceName()).append("\n");
                sb.append("                fallbackUri: forward:/fallback/").append(module.getServiceName()).append("\n");
            }

            // Step 4b: emit general (own-path wildcard) routes
            for (FractalModule module : modules) {
                Set<String> own   = ownPaths.get(module);
                String myPrefix   = primaryPrefix.get(module);
                String myPluralPfx = myPrefix.endsWith("s") ? myPrefix : myPrefix + "s";

                String pathPattern;
                if (!own.isEmpty()) {
                    // Use the detected primary prefix wildcards (covers all own paths)
                    pathPattern = myPrefix.equals(myPluralPfx)
                            ? myPrefix + "/**"
                            : myPrefix + "/**," + myPluralPfx + "/**";
                } else {
                    // No own paths found; fall back to name-heuristic (same as non-scanning mode)
                    String baseName   = module.getServiceName().replace("-service", "");
                    String pluralName = baseName.endsWith("s") ? baseName : baseName + "s";
                    pathPattern = baseName.equals(pluralName)
                            ? "/api/" + baseName + "/**"
                            : "/api/" + baseName + "/**,/api/" + pluralName + "/**";
                }

                int servicePort = resolvePortConflict(module.getPort(), module.getServiceName());
                sb.append("        # ").append(module.getServiceName()).append(" Service\n");
                sb.append("        - id: ").append(module.getServiceName()).append("-service\n");
                sb.append("          uri: http://localhost:").append(servicePort).append("\n");
                sb.append("          predicates:\n");
                sb.append("            - Path=").append(pathPattern).append("\n");
                sb.append("          filters:\n");
                sb.append("            - StripPrefix=0\n");
                sb.append("            - name: CircuitBreaker\n");
                sb.append("              args:\n");
                sb.append("                name: ").append(module.getServiceName()).append("\n");
                sb.append("                fallbackUri: forward:/fallback/").append(module.getServiceName()).append("\n");
            }
        } else {
            // ── Backward-compatible heuristic mode ───────────────────────────
            for (FractalModule module : modules) {
                sb.append(generateServiceRoute(module));
            }
        }

        return sb.toString();
    }

    // ── Controller scanning helpers (used by generateRoutesSection) ───────────

    /**
     * Scans the module's package subtree in {@link #monolithSrc} and returns
     * the set of normalised paths (path-variable segments replaced with {@code *})
     * exposed by its {@code @RestController} classes.
     */
    private Set<String> scanModulePaths(FractalModule module) {
        Set<String> paths = new LinkedHashSet<>();
        if (monolithSrc == null) return paths;

        String packageName = module.getPackageName();
        if (packageName == null || packageName.isBlank()) return paths;

        Path moduleDir = monolithSrc.resolve(packageName.replace('.', '/'));
        if (!Files.isDirectory(moduleDir)) return paths;

        try (Stream<Path> walk = Files.walk(moduleDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> collectPathsFromFile(file, paths));
        } catch (IOException e) {
            log.debug("Could not walk module package dir {}: {}", moduleDir, e.getMessage());
        }
        return paths;
    }

    private void collectPathsFromFile(Path file, Set<String> out) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(c -> ctrlHasAnnotation(c.getAnnotations(), "RestController"))
                    .forEach(cls -> {
                        String basePath = ctrlExtractPath(cls.getAnnotations(), "RequestMapping");
                        for (MethodDeclaration method : cls.getMethods()) {
                            List<AnnotationExpr> annots = method.getAnnotations();
                            for (Map.Entry<String, String> entry : VERB_ANNOTATIONS.entrySet()) {
                                String annotName = entry.getValue();
                                if (!ctrlHasAnnotation(annots, annotName)) continue;
                                String methodPath = ctrlExtractPath(annots, annotName);
                                String fullPath   = ctrlJoinPaths(basePath, methodPath);
                                out.add(normalizePathVars(fullPath));
                                break;
                            }
                        }
                    });
        } catch (Exception e) {
            log.debug("Could not parse {}: {}", file, e.getMessage());
        }
    }

    /** Replaces {@code {variable}} path segments with {@code *}. */
    private static String normalizePathVars(String path) {
        return path.replaceAll("\\{[^/]+\\}", "*");
    }

    private String ctrlExtractPath(List<AnnotationExpr> annotations, String annotName) {
        for (AnnotationExpr a : annotations) {
            if (!a.getNameAsString().equals(annotName)) continue;
            if (a instanceof SingleMemberAnnotationExpr sma) {
                return ctrlStripQuotes(sma.getMemberValue().toString());
            }
            if (a instanceof NormalAnnotationExpr nma) {
                for (var pair : nma.getPairs()) {
                    String key = pair.getNameAsString();
                    if (key.equals("value") || key.equals("path"))
                        return ctrlStripQuotes(pair.getValue().toString());
                }
            }
            return "";
        }
        return "";
    }

    private static String ctrlStripQuotes(String raw) {
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() > 1)
            return raw.substring(1, raw.length() - 1);
        return raw;
    }

    private static String ctrlJoinPaths(String base, String sub) {
        if (base.isBlank() && sub.isBlank()) return "/";
        if (base.isBlank()) return sub.startsWith("/") ? sub : "/" + sub;
        if (sub.isBlank())  return base;
        boolean baseSlash = base.endsWith("/");
        boolean subSlash  = sub.startsWith("/");
        if (baseSlash && subSlash)   return base + sub.substring(1);
        if (!baseSlash && !subSlash) return base + "/" + sub;
        return base + sub;
    }

    private static boolean ctrlHasAnnotation(List<AnnotationExpr> annotations, String name) {
        return annotations.stream().anyMatch(a -> a.getNameAsString().equals(name));
    }

    private String generateAuthServiceRoute() {
        // auth-service runs on port 8090 and handles /api/auth/** — no circuit breaker needed
        // because auth failures are intentional (wrong credentials) and should reach the client.
        return "        # auth-service (generated by FractalX — handles login + registration)\n"
             + "        - id: auth-service\n"
             + "          uri: http://localhost:8090\n"
             + "          predicates:\n"
             + "            - Path=/api/auth/**\n"
             + "          filters:\n"
             + "            - StripPrefix=0\n";
    }

    private String generateServiceRoute(FractalModule module) {
        // Handle port conflicts
        int servicePort = resolvePortConflict(module.getPort(), module.getServiceName());

        // Extract base path (e.g. inventory-service -> inventory)
        String serviceName = module.getServiceName();
        String baseName = serviceName.replace("-service", "");

        // Support both singular and simple plural (e.g. /api/inventory and /api/inventorys).
        // While 'inventorys' isn't grammatically correct, it's what the simple pluralizer produced.
        // Matching both ensures backward compatibility and fixes 404s for singular calls.
        String pluralName = baseName.endsWith("s") ? baseName : baseName + "s";
        String pathPattern = baseName.equals(pluralName)
                ? "/api/" + baseName + "/**"
                : "/api/" + baseName + "/**,/api/" + pluralName + "/**";

        StringBuilder route = new StringBuilder();
        route.append("        # ").append(module.getServiceName()).append(" Service\n");
        route.append("        - id: ").append(module.getServiceName()).append("-service\n");
        route.append("          uri: http://localhost:").append(servicePort).append("\n");
        route.append("          predicates:\n");
        route.append("            - Path=").append(pathPattern).append("\n");
        route.append("          filters:\n");
        route.append("            - StripPrefix=0\n");
        route.append("            - name: CircuitBreaker\n");
        route.append("              args:\n");
        route.append("                name: ").append(module.getServiceName()).append("\n");
        route.append("                fallbackUri: forward:/fallback/").append(module.getServiceName()).append("\n");
        // Rate limiting is handled globally by RateLimitFilter (GlobalFilter bean) —
        // do NOT add RequestRateLimiter here; that factory requires Redis and is not registered.

        return route.toString();
    }

    private int resolvePortConflict(int requestedPort, String serviceName) {
        int finalPort = requestedPort;

        // Check if port is already used (skip if it's the service's own port)
        while (usedPorts.containsKey(finalPort) && finalPort != GATEWAY_PORT) {
            log.warn("Port {} is already in use for service {}. Trying next port.",
                    finalPort, serviceName);
            finalPort++;
        }

        // Reserve this port (if it's not the gateway port)
        if (finalPort != GATEWAY_PORT) {
            usedPorts.put(finalPort, true);
        }

        if (finalPort != requestedPort) {
            log.info("Service {} will use port {} instead of {}",
                    serviceName, finalPort, requestedPort);
        }

        return finalPort;
    }
}