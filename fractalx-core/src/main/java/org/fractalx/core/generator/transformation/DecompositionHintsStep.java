package org.fractalx.core.generator.transformation;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Detects cross-cutting patterns that silently break after decomposition and writes
 * a {@code DECOMPOSITION_HINTS.md} into the service root with actionable warnings.
 *
 * <p>Covers five categories:
 * <ul>
 *   <li><b>Gap 3</b> — {@code @Transactional} methods that call {@code *Client} (cross-service)
 *       methods: the monolith's single DB transaction no longer spans the remote call.</li>
 *   <li><b>Gap 4</b> — {@code @Cacheable} / {@code @CacheEvict} / {@code @CachePut}: the
 *       in-memory Caffeine / EhCache cache from the monolith is now per-JVM; invalidations
 *       from one service are invisible to another.</li>
 *   <li><b>Gap 5</b> — {@code ApplicationEventPublisher.publishEvent()} calls and
 *       {@code @EventListener} / {@code @TransactionalEventListener} methods: Spring
 *       events are in-JVM only; cross-service listeners never fire.</li>
 *   <li><b>Gap 6</b> — {@code @Aspect} classes whose {@code @Pointcut} patterns reference
 *       packages belonging to other modules: after decomposition the aspect only fires
 *       inside the service it was copied into.</li>
 *   <li><b>Gap 7</b> — {@code @Scheduled} methods that invoke {@code *Client} (cross-service)
 *       methods: the scheduled invocation still runs locally, but the data it needs may
 *       have moved to another service's database.</li>
 * </ul>
 *
 * <p>This step runs after {@link NetScopeClientWiringStep} so {@code *Client} references
 * are already in place and can be detected as cross-service calls.
 */
public class DecompositionHintsStep implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(DecompositionHintsStep.class);

    @Override
    public void generate(GenerationContext context) throws IOException {
        List<String> deps = context.getModule().getDependencies();

        // Build a set of client type names produced by NetScopeClientGenerator
        Set<String> clientTypes = deps.stream()
                .map(d -> d + "Client")
                .collect(Collectors.toSet());

        // Collect all other module packages to detect cross-module @Pointcut patterns
        Set<String> otherModulePackages = context.getAllModules().stream()
                .filter(m -> !m.equals(context.getModule()))
                .map(FractalModule::getPackageName)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.toSet());

        // Category → list of (file:line description) findings
        Map<String, List<String>> findings = new LinkedHashMap<>();
        findings.put("transactional_cross_service", new ArrayList<>());
        findings.put("shared_cache",                new ArrayList<>());
        findings.put("spring_events",               new ArrayList<>());
        findings.put("aspect_scope",                new ArrayList<>());
        findings.put("scheduled_cross_service",     new ArrayList<>());
        findings.put("security_patterns",           new ArrayList<>());

        try (Stream<Path> paths = Files.walk(context.getSrcMainJava())) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(javaFile -> {
                try {
                    analyseFile(javaFile, clientTypes, otherModulePackages, findings);
                } catch (Exception e) {
                    log.debug("DecompositionHintsStep: could not analyse {}", javaFile);
                }
            });
        }

        boolean hasFindings = findings.values().stream().anyMatch(l -> !l.isEmpty());
        if (!hasFindings) {
            log.debug("No decomposition warnings for {}", context.getModule().getServiceName());
            return;
        }

        Path hintsFile = context.getServiceRoot().resolve("DECOMPOSITION_HINTS.md");
        Files.writeString(hintsFile, buildMarkdown(context.getModule().getServiceName(), findings),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        int total = findings.values().stream().mapToInt(List::size).sum();
        log.warn("⚠  {} decomposition hint(s) written to DECOMPOSITION_HINTS.md for {}",
                total, context.getModule().getServiceName());
    }

    // -------------------------------------------------------------------------
    // Per-file analysis
    // -------------------------------------------------------------------------

    private void analyseFile(Path javaFile, Set<String> clientTypes,
                              Set<String> otherModulePkgs,
                              Map<String, List<String>> findings) throws Exception {
        CompilationUnit cu = new JavaParser().parse(javaFile).getResult().orElse(null);
        if (cu == null) return;

        String shortName = javaFile.getFileName().toString();

        // ── Gap 3: @Transactional methods calling a *Client ───────────────────
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            boolean isTransactional = method.getAnnotationByName("Transactional").isPresent();
            if (!isTransactional) continue;
            List<MethodCallExpr> crossServiceCalls = method.findAll(MethodCallExpr.class).stream()
                    .filter(call -> call.getScope().isPresent())
                    .filter(call -> {
                        String receiver = call.getScope().get().toString();
                        return clientTypes.stream()
                                .anyMatch(ct -> receiver.endsWith(decapitalize(ct))
                                        || receiver.endsWith(ct));
                    })
                    .collect(Collectors.toList());
            if (!crossServiceCalls.isEmpty()) {
                String calls = crossServiceCalls.stream()
                        .map(c -> c.getScope().get() + "." + c.getNameAsString() + "()")
                        .distinct().collect(Collectors.joining(", "));
                findings.get("transactional_cross_service").add(
                        "`" + shortName + "` → `" + method.getNameAsString() + "()`: calls " + calls);
            }
        }

        // ── Gap 4: @Cacheable / @CacheEvict / @CachePut ───────────────────────
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            boolean hasCache = method.getAnnotationByName("Cacheable").isPresent()
                    || method.getAnnotationByName("CacheEvict").isPresent()
                    || method.getAnnotationByName("CachePut").isPresent();
            if (hasCache) {
                String annNames = method.getAnnotations().stream()
                        .filter(a -> Set.of("Cacheable", "CacheEvict", "CachePut")
                                .contains(a.getNameAsString()))
                        .map(a -> "@" + a.getNameAsString())
                        .collect(Collectors.joining(", "));
                findings.get("shared_cache").add(
                        "`" + shortName + "` → `" + method.getNameAsString() + "()` (" + annNames + ")");
            }
        }

        // ── Gap 5a: publishEvent() calls ──────────────────────────────────────
        cu.findAll(MethodCallExpr.class).stream()
                .filter(call -> "publishEvent".equals(call.getNameAsString()))
                .forEach(call -> findings.get("spring_events").add(
                        "`" + shortName + "` calls `publishEvent()` — listeners in other " +
                        "services will NOT receive this event (Spring events are in-JVM only)"));

        // ── Gap 5b: @EventListener / @TransactionalEventListener ─────────────
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            boolean isListener = method.getAnnotationByName("EventListener").isPresent()
                    || method.getAnnotationByName("TransactionalEventListener").isPresent();
            if (isListener) {
                findings.get("spring_events").add(
                        "`" + shortName + "` → `" + method.getNameAsString() + "()` annotated " +
                        "@EventListener / @TransactionalEventListener — will only fire for events " +
                        "published within this service; cross-service events require an outbox/queue");
            }
        }

        // ── Gap 6: @Aspect with @Pointcut targeting other module packages ─────
        boolean isAspect = cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                .stream().anyMatch(c -> c.getAnnotationByName("Aspect").isPresent());
        if (isAspect) {
            cu.findAll(MethodDeclaration.class).stream()
                    .filter(m -> m.getAnnotationByName("Pointcut").isPresent()
                            || m.getAnnotationByName("Before").isPresent()
                            || m.getAnnotationByName("After").isPresent()
                            || m.getAnnotationByName("Around").isPresent()
                            || m.getAnnotationByName("AfterThrowing").isPresent())
                    .forEach(m -> {
                        // Collect pointcut expression strings from all AOP annotations
                        m.getAnnotations().forEach(ann -> {
                            String expr = ann.toString();
                            boolean crossModule = otherModulePkgs.stream()
                                    .anyMatch(pkg -> expr.contains(pkg));
                            if (crossModule || expr.contains("service.*.*(")) {
                                findings.get("aspect_scope").add(
                                        "`" + shortName + "` → `" + m.getNameAsString() + "()`: " +
                                        "pointcut may target classes in other services; after " +
                                        "decomposition this aspect only fires within this service");
                            }
                        });
                    });

            // Also warn even if no specific package match — aspect scope is always restricted post-decomp
            if (findings.get("aspect_scope").isEmpty()) {
                findings.get("aspect_scope").add(
                        "`" + shortName + "` is an @Aspect — its pointcuts only fire within " +
                        "this service after decomposition. If cross-service advice is needed, " +
                        "copy the aspect to the other generated services or use an interceptor.");
            }
        }

        // ── Gap 7: @Scheduled methods calling a *Client ───────────────────────
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            boolean isScheduled = method.getAnnotationByName("Scheduled").isPresent();
            if (!isScheduled) continue;
            List<MethodCallExpr> crossServiceCalls = method.findAll(MethodCallExpr.class).stream()
                    .filter(call -> call.getScope().isPresent())
                    .filter(call -> {
                        String receiver = call.getScope().get().toString();
                        return clientTypes.stream()
                                .anyMatch(ct -> receiver.endsWith(decapitalize(ct))
                                        || receiver.endsWith(ct));
                    })
                    .collect(Collectors.toList());
            if (!crossServiceCalls.isEmpty()) {
                findings.get("scheduled_cross_service").add(
                        "`" + shortName + "` → `" + method.getNameAsString() + "()`: calls " +
                        crossServiceCalls.stream()
                                .map(c -> c.getScope().get() + "." + c.getNameAsString() + "()")
                                .distinct().collect(Collectors.joining(", ")) +
                        " — data may have moved to another service's database");
            }
        }

        // ── Gap 8a: @EnableWebSecurity — original SecurityConfig superseded ────
        boolean hasWebSecurity = cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                .stream().anyMatch(c -> c.getAnnotationByName("EnableWebSecurity").isPresent());
        if (hasWebSecurity) {
            findings.get("security_patterns").add(
                    "`" + shortName + "` is annotated `@EnableWebSecurity` — the monolith " +
                    "SecurityConfig has been superseded by the generated `ServiceSecurityConfig` " +
                    "(gateway-trust model). Review the original access rules and re-express any " +
                    "per-route authorization as `@PreAuthorize` annotations on service methods, " +
                    "or configure them in the API Gateway.");
        }

        // ── Gap 8b: method-level security annotations ──────────────────────────
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            boolean hasMethodSecurity = method.getAnnotationByName("PreAuthorize").isPresent()
                    || method.getAnnotationByName("Secured").isPresent()
                    || method.getAnnotationByName("RolesAllowed").isPresent();
            if (hasMethodSecurity) {
                String annList = method.getAnnotations().stream()
                        .filter(a -> Set.of("PreAuthorize", "Secured", "RolesAllowed")
                                .contains(a.getNameAsString()))
                        .map(a -> "@" + a.getNameAsString())
                        .collect(Collectors.joining(", "));
                findings.get("security_patterns").add(
                        "`" + shortName + "` → `" + method.getNameAsString() + "()` (" + annList +
                        "): method-level security is preserved. It works on HTTP calls via " +
                        "`GatewayAuthHeaderFilter` (which validates `X-Internal-Token`). " +
                        "For cross-service gRPC calls, the Internal Call Token is forwarded " +
                        "automatically by `fractalx-runtime` ≥0.3.3.");
            }
        }

        // ── Gap 8c: custom JWT filter (OncePerRequestFilter subclass) ──────────
        cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).forEach(cls -> {
            boolean extendsOnce = cls.getExtendedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals("OncePerRequestFilter"));
            if (extendsOnce) {
                findings.get("security_patterns").add(
                        "`" + shortName + "` extends `OncePerRequestFilter` — this custom JWT/auth " +
                        "filter attempts to validate `Authorization: Bearer` tokens that the API " +
                        "Gateway already consumed and converted to an `X-Internal-Token`. " +
                        "**Remove this filter from this service** and rely on the generated " +
                        "`GatewayAuthHeaderFilter` instead.");
            }
        });

        // ── Gap 8d: UserDetailsService / AuthenticationProvider beans ──────────
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (!method.getAnnotationByName("Bean").isPresent()) continue;
            String returnType = method.getTypeAsString();
            if (returnType.contains("UserDetailsService")
                    || returnType.contains("AuthenticationProvider")
                    || returnType.contains("AuthenticationManager")) {
                findings.get("security_patterns").add(
                        "`" + shortName + "` → `" + method.getNameAsString() + "()` produces `" +
                        returnType + "` — user credentials / identity store belongs exclusively " +
                        "to the service that owns the users table (typically an auth or user " +
                        "management service). **Other services must not load user credentials**; " +
                        "they trust the `X-Internal-Token` minted by the gateway instead.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Markdown report builder
    // -------------------------------------------------------------------------

    private String buildMarkdown(String serviceName, Map<String, List<String>> findings) {
        StringBuilder md = new StringBuilder();
        md.append("# Decomposition Hints — ").append(serviceName).append("\n\n");
        md.append("> Generated by FractalX. Review and resolve before going to production.\n\n");

        appendSection(md, findings.get("transactional_cross_service"),
                "## Gap 3 — @Transactional methods that call cross-service clients",
                """
                In the monolith these methods ran inside a single database transaction.
                After decomposition the `*Client` calls are remote gRPC calls and are
                **outside** any transaction boundary. If the remote call succeeds but a
                subsequent local DB write fails, the remote state is not rolled back.

                **Fix options:**
                - Convert the method to a `@DistributedSaga` and let FractalX generate
                  an outbox-backed saga with compensating actions.
                - Use the outbox pattern manually: write a domain event to the outbox
                  table inside the local transaction; a poller forwards it asynchronously.
                """);

        appendSection(md, findings.get("shared_cache"),
                "## Gap 4 — Shared in-memory caches (@Cacheable / @CacheEvict / @CachePut)",
                """
                The monolith used a single in-memory `CacheManager` (Caffeine/EhCache).
                After decomposition each service instance has its **own** cache. An
                `@CacheEvict` in Service A does **not** invalidate Service B's copy.

                **Fix options:**
                - Replace `CacheManager` with a Redis-backed implementation and point
                  all generated services at the same Redis instance.
                - If cache entries are service-local (no cross-service invalidation
                  needed), this warning can be ignored.
                """);

        appendSection(md, findings.get("spring_events"),
                "## Gap 5 — Spring ApplicationEvents across service boundaries",
                """
                `ApplicationEventPublisher.publishEvent()` and `@EventListener` /
                `@TransactionalEventListener` are **in-JVM only**. After decomposition,
                a publisher in Service A cannot fire a listener in Service B.

                **Fix options:**
                - Use the transactional **outbox pattern**: persist the event in the
                  local DB inside the originating transaction; a poller reads and
                  publishes it to a message broker (Kafka / RabbitMQ / SQS).
                - FractalX already generates `OutboxPublisher` / `OutboxPoller` for
                  services with `@DistributedSaga` — wire your events through them.
                """);

        appendSection(md, findings.get("aspect_scope"),
                "## Gap 6 — AOP @Aspect scope restricted to one service",
                """
                AOP pointcuts only intercept method calls within the same JVM.
                After decomposition this aspect fires **only inside this service**.

                **Fix options:**
                - If the aspect provides cross-cutting behaviour needed in other
                  services (e.g. audit logging, exception compensation), copy the
                  aspect into those services as well, or refactor the behaviour into
                  a shared library that all services depend on.
                - If the aspect is only needed here, no action required.
                """);

        appendSection(md, findings.get("scheduled_cross_service"),
                "## Gap 7 — @Scheduled jobs calling cross-service clients",
                """
                These scheduled jobs call methods on `*Client` interfaces — i.e., they
                make remote gRPC calls on a schedule. While this works technically, the
                data the job relies on may now live in another service's database.

                **Fix options:**
                - Ensure the scheduled job only reads/writes data owned by this service.
                  Data from other services should be fetched via the `*Client` at
                  runtime, not cached locally.
                - If the job orchestrates multiple services, consider moving it to the
                  saga-orchestrator service or using a dedicated scheduler service.
                """);

        appendSection(md, findings.get("security_patterns"),
                "## Gap 8 — Spring Security patterns that require attention after decomposition",
                """
                Spring Security configuration designed for a single JVM does not map cleanly
                onto a distributed microservice system.

                **`@EnableWebSecurity` (Gap 8a):**
                The monolith's `SecurityConfig` has been superseded by the generated
                `ServiceSecurityConfig` (gateway-trust model). FractalX generates a
                `GatewayAuthHeaderFilter` that validates the `X-Internal-Token` signed JWT
                injected by the API Gateway. Per-route rules from the monolith's
                `SecurityFilterChain` should be re-expressed as `@PreAuthorize` on service
                methods or configured in the Gateway's `fractalx.gateway.security` properties.

                **`@PreAuthorize` / `@Secured` / `@RolesAllowed` (Gap 8b):**
                Method-level security annotations are preserved and **work correctly** via the
                generated `GatewayAuthHeaderFilter`. The filter validates `X-Internal-Token`
                and populates `SecurityContextHolder` before method invocation. For cross-service
                gRPC (NetScope) calls the token is automatically forwarded via `x-internal-token`
                metadata by `fractalx-runtime` ≥0.3.3 — `@PreAuthorize` also works in services
                called via NetScope.

                **Custom JWT `OncePerRequestFilter` (Gap 8c):**
                Custom Bearer token filters should be **removed** from decomposed services.
                The API Gateway validates the user's JWT once and converts it to an
                `X-Internal-Token`; downstream services no longer receive raw Bearer tokens.
                The generated `GatewayAuthHeaderFilter` handles downstream authentication.

                **`UserDetailsService` / `AuthenticationProvider` (Gap 8d):**
                These beans load user credentials from a database. After decomposition, only the
                service that owns the users table should retain them. All other services receive
                verified user identity via `X-Internal-Token` and must **not** attempt to load
                user credentials directly.

                **Fix options summary:**
                - Remove `@EnableWebSecurity` classes from non-auth services (they are replaced).
                - Keep `@PreAuthorize` / `@Secured` — they work via the generated security config.
                - Remove custom `OncePerRequestFilter` JWT validators from decomposed services.
                - Move `UserDetailsService` / `AuthenticationProvider` to the auth/user service only.
                - Set `FRACTALX_INTERNAL_JWT_SECRET` to the same value for the gateway and all
                  services (default is insecure — **must** be changed in production).
                """);

        return md.toString();
    }

    private void appendSection(StringBuilder md, List<String> items,
                                String heading, String explanation) {
        if (items.isEmpty()) return;
        md.append(heading).append("\n\n");
        md.append(explanation.strip()).append("\n\n");
        md.append("**Detected occurrences:**\n\n");
        for (String item : items) {
            md.append("- ").append(item).append("\n");
        }
        md.append("\n---\n\n");
    }

    private static String decapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
