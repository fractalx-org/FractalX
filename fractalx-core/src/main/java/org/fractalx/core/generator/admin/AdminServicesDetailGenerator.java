package org.fractalx.core.generator.admin;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates the extended services sub-system for the admin service:
 * <ul>
 *   <li>{@code ServiceMetaRegistry} — baked-in metadata for every generated service + infra</li>
 *   <li>{@code DeploymentTracker}   — per-service deployment stage history (pre-seeded)</li>
 *   <li>{@code ServicesController}  — REST API: health, metrics, deployment, lifecycle commands</li>
 * </ul>
 */
class AdminServicesDetailGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminServicesDetailGenerator.class);

    void generate(Path srcMainJava, String basePackage, List<FractalModule> modules) throws IOException {
        Path pkg = AdminPackageUtil.createPackagePath(srcMainJava, basePackage + ".services");

        generateServiceMetaRegistry(pkg, modules);
        generateDeploymentTracker(pkg, modules);
        generateServicesController(pkg);

        log.debug("Generated admin services detail components");
    }

    // -------------------------------------------------------------------------

    private void generateServiceMetaRegistry(Path pkg, List<FractalModule> modules) throws IOException {
        StringBuilder entries = new StringBuilder();

        for (FractalModule m : modules) {
            String deps = buildDepsLiteral(m.getDependencies());
            entries.append(String.format(
                    "        new ServiceMeta(\"%s\", %d, %d, \"microservice\", %s, \"%s\", \"%s\"),\n",
                    m.getServiceName(), m.getPort(), m.getPort() + 10000,
                    deps, m.getPackageName(), m.getClassName()));
        }
        // Always-present infra services
        entries.append("        new ServiceMeta(\"fractalx-registry\", 8761, 0, \"infrastructure\", List.of(), \"\", \"\"),\n");
        entries.append("        new ServiceMeta(\"api-gateway\",       8080, 0, \"infrastructure\", List.of(), \"\", \"\"),\n");
        entries.append("        new ServiceMeta(\"admin-service\",     9090, 0, \"infrastructure\", List.of(), \"\", \"\"),\n");
        entries.append("        new ServiceMeta(\"logger-service\",    9099, 0, \"infrastructure\", List.of(), \"\", \"\")\n");

        String content = """
                package org.fractalx.admin.services;

                import org.springframework.stereotype.Component;

                import java.util.List;
                import java.util.Optional;

                /**
                 * Baked-in registry of all generated services and infrastructure services.
                 * Populated at generation time by FractalX — no runtime discovery needed.
                 */
                @Component
                public class ServiceMetaRegistry {

                    public record ServiceMeta(
                            String name, int port, int grpcPort, String type,
                            List<String> dependencies, String packageName, String className) {}

                    private static final List<ServiceMeta> SERVICES = List.of(
                %s    );

                    public List<ServiceMeta> getAll()                    { return SERVICES; }
                    public int               size()                      { return SERVICES.size(); }

                    public Optional<ServiceMeta> findByName(String name) {
                        return SERVICES.stream().filter(s -> s.name().equals(name)).findFirst();
                    }

                    public List<ServiceMeta> getByType(String type) {
                        return SERVICES.stream().filter(s -> s.type().equals(type)).toList();
                    }

                    public long countMicroservices() {
                        return SERVICES.stream().filter(s -> "microservice".equals(s.type())).count();
                    }
                }
                """.formatted(entries.toString());

        Files.writeString(pkg.resolve("ServiceMetaRegistry.java"), content);
    }

    private void generateDeploymentTracker(Path pkg, List<FractalModule> modules) throws IOException {
        StringBuilder initCalls = new StringBuilder();
        for (FractalModule m : modules) {
            initCalls.append(String.format("        addInitialRecord(\"%s\");\n", m.getServiceName()));
        }
        initCalls.append("        addInitialRecord(\"fractalx-registry\");\n");
        initCalls.append("        addInitialRecord(\"api-gateway\");\n");
        initCalls.append("        addInitialRecord(\"admin-service\");\n");
        initCalls.append("        addInitialRecord(\"logger-service\");\n");

        String content = """
                package org.fractalx.admin.services;

                import jakarta.annotation.PostConstruct;
                import org.springframework.stereotype.Component;

                import java.util.ArrayList;
                import java.util.List;
                import java.util.Map;
                import java.util.concurrent.ConcurrentHashMap;
                import java.util.concurrent.CopyOnWriteArrayList;

                /**
                 * Tracks per-service deployment stage history.
                 * Pre-seeded at startup with the initial generation record.
                 * New deployment records can be appended at runtime via {@link #addRecord}.
                 */
                @Component
                public class DeploymentTracker {

                    public enum StageStatus { PENDING, IN_PROGRESS, DONE, FAILED }

                    public record DeploymentStage(
                            String name, StageStatus status, String timestamp, long durationMs) {}

                    public record DeploymentRecord(
                            String service, String version, String deployedAt,
                            List<DeploymentStage> stages, String triggeredBy) {}

                    private final Map<String, List<DeploymentRecord>> history = new ConcurrentHashMap<>();

                    @PostConstruct
                    public void init() {
                %s    }

                    public void addRecord(DeploymentRecord record) {
                        history.computeIfAbsent(record.service(), k -> new CopyOnWriteArrayList<>())
                               .add(record);
                    }

                    public DeploymentRecord getLatest(String service) {
                        List<DeploymentRecord> records = history.getOrDefault(service, List.of());
                        return records.isEmpty() ? null : records.get(records.size() - 1);
                    }

                    public List<DeploymentRecord> getHistory(String service) {
                        return new ArrayList<>(history.getOrDefault(service, List.of()));
                    }

                    public Map<String, List<DeploymentRecord>> getAllHistory() {
                        return Map.copyOf(history);
                    }

                    private void addInitialRecord(String service) {
                        List<DeploymentStage> stages = List.of(
                            new DeploymentStage("BUILD",   StageStatus.DONE, "Initial generation", 0L),
                            new DeploymentStage("PACKAGE", StageStatus.DONE, "Initial generation", 0L),
                            new DeploymentStage("DEPLOY",  StageStatus.DONE, "Initial generation", 0L)
                        );
                        addRecord(new DeploymentRecord(
                            service, "1.0.0", "Generated by FractalX v0.3.2",
                            stages, "FractalX Generator"));
                    }
                }
                """.formatted(initCalls.toString());

        Files.writeString(pkg.resolve("DeploymentTracker.java"), content);
    }

    private void generateServicesController(Path pkg) throws IOException {
        String content = """
                package org.fractalx.admin.services;

                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                import org.springframework.web.client.RestTemplate;

                import java.util.*;

                /**
                 * REST API for the Services section of the admin dashboard.
                 *
                 * <pre>
                 * GET /api/services/all              — all services with live health + commands
                 * GET /api/services/{name}/detail    — full detail (health, metrics, commands)
                 * GET /api/services/{name}/health    — proxy to actuator health
                 * GET /api/services/{name}/metrics   — proxy to actuator metrics
                 * GET /api/services/{name}/deployment — deployment stages
                 * GET /api/services/{name}/history   — full deployment history
                 * GET /api/services/{name}/commands  — docker-compose lifecycle commands
                 * </pre>
                 */
                @RestController
                @RequestMapping("/api/services")
                @CrossOrigin(origins = "*")
                public class ServicesController {

                    private final ServiceMetaRegistry    registry;
                    private final DeploymentTracker      deploymentTracker;
                    private final RestTemplate           restTemplate = new RestTemplate();

                    @Value("${fractalx.registry.url:http://localhost:8761}")
                    private String registryUrl;

                    public ServicesController(ServiceMetaRegistry registry, DeploymentTracker deploymentTracker) {
                        this.registry          = registry;
                        this.deploymentTracker = deploymentTracker;
                    }

                    /** Returns all services with live health status, metadata, and lifecycle commands. */
                    @GetMapping("/all")
                    public ResponseEntity<List<Map<String, Object>>> getAllServices() {
                        List<Map<String, Object>> result = new ArrayList<>();
                        for (ServiceMetaRegistry.ServiceMeta meta : registry.getAll()) {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("meta",       meta);
                            entry.put("health",     fetchHealthStatus(meta));
                            entry.put("commands",   buildCommands(meta.name()));
                            entry.put("deployment", deploymentTracker.getLatest(meta.name()));
                            result.add(entry);
                        }
                        return ResponseEntity.ok(result);
                    }

                    /** Full detail for one service: health, metrics snapshot, dependencies, commands. */
                    @GetMapping("/{name}/detail")
                    public ResponseEntity<Map<String, Object>> getServiceDetail(@PathVariable("name") String name) {
                        return registry.findByName(name).map(meta -> {
                            Map<String, Object> detail = new LinkedHashMap<>();
                            detail.put("meta",       meta);
                            detail.put("health",     fetchHealthFull(meta));
                            detail.put("commands",   buildCommands(name));
                            detail.put("deployment", deploymentTracker.getLatest(name));
                            return ResponseEntity.ok(detail);
                        }).orElse(ResponseEntity.notFound().build());
                    }

                    /** Proxies to the service's /actuator/health endpoint. */
                    @GetMapping("/{name}/health")
                    public ResponseEntity<Object> getServiceHealth(@PathVariable("name") String name) {
                        return registry.findByName(name).map(meta -> {
                            if (meta.port() == 0) return ResponseEntity.ok((Object) Map.of("status", "UNKNOWN"));
                            try {
                                Object resp = restTemplate.getForObject(
                                        "http://localhost:" + meta.port() + "/actuator/health", Object.class);
                                return ResponseEntity.ok(resp);
                            } catch (Exception e) {
                                return ResponseEntity.ok((Object) Map.of("status", "DOWN", "error", e.getMessage()));
                            }
                        }).orElse(ResponseEntity.notFound().build());
                    }

                    /** Proxies to the service's /actuator/metrics endpoint. */
                    @GetMapping("/{name}/metrics")
                    public ResponseEntity<Object> getServiceMetrics(@PathVariable("name") String name) {
                        return registry.findByName(name).map(meta -> {
                            if (meta.port() == 0)
                                return ResponseEntity.ok((Object) Map.of("error", "No metrics port configured"));
                            try {
                                Object resp = restTemplate.getForObject(
                                        "http://localhost:" + meta.port() + "/actuator/metrics", Object.class);
                                return ResponseEntity.ok(resp);
                            } catch (Exception e) {
                                return ResponseEntity.ok((Object) Map.of("error", e.getMessage()));
                            }
                        }).orElse(ResponseEntity.notFound().build());
                    }

                    /** Returns the latest deployment record + stages for a service. */
                    @GetMapping("/{name}/deployment")
                    public ResponseEntity<Object> getDeployment(@PathVariable("name") String name) {
                        DeploymentTracker.DeploymentRecord record = deploymentTracker.getLatest(name);
                        if (record == null) return ResponseEntity.notFound().build();
                        return ResponseEntity.ok(record);
                    }

                    /** Returns the full deployment history for a service. */
                    @GetMapping("/{name}/history")
                    public ResponseEntity<List<DeploymentTracker.DeploymentRecord>> getHistory(
                            @PathVariable("name") String name) {
                        return ResponseEntity.ok(deploymentTracker.getHistory(name));
                    }

                    /** Returns docker-compose lifecycle commands (start/stop/restart/logs). */
                    @GetMapping("/{name}/commands")
                    public ResponseEntity<Map<String, String>> getLifecycleCommands(@PathVariable("name") String name) {
                        return ResponseEntity.ok(buildCommands(name));
                    }

                    // -------------------------------------------------------------------------

                    private String fetchHealthStatus(ServiceMetaRegistry.ServiceMeta meta) {
                        if (meta.port() == 0) return "UNKNOWN";
                        try {
                            String resp = restTemplate.getForObject(
                                    "http://localhost:" + meta.port() + "/actuator/health", String.class);
                            return (resp != null && resp.contains("UP")) ? "UP" : "DOWN";
                        } catch (Exception e) {
                            return "DOWN";
                        }
                    }

                    private Object fetchHealthFull(ServiceMetaRegistry.ServiceMeta meta) {
                        if (meta.port() == 0) return Map.of("status", "UNKNOWN");
                        try {
                            return restTemplate.getForObject(
                                    "http://localhost:" + meta.port() + "/actuator/health", Object.class);
                        } catch (Exception e) {
                            return Map.of("status", "DOWN", "error", e.getMessage());
                        }
                    }

                    private Map<String, String> buildCommands(String service) {
                        Map<String, String> cmds = new LinkedHashMap<>();
                        cmds.put("start",   "docker compose up -d " + service);
                        cmds.put("stop",    "docker compose stop " + service);
                        cmds.put("restart", "docker compose restart " + service);
                        cmds.put("logs",    "docker compose logs -f " + service);
                        return cmds;
                    }
                }
                """;

        Files.writeString(pkg.resolve("ServicesController.java"), content);
    }

    // -------------------------------------------------------------------------

    /** Converts a list of Spring bean type names (e.g., "PaymentService") to service names. */
    private String buildDepsLiteral(List<String> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) return "List.of()";
        StringBuilder sb = new StringBuilder("List.of(");
        for (int i = 0; i < dependencies.size(); i++) {
            String dep = dependencies.get(i);
            String serviceName = dep.replaceAll("(?i)(Service|Client)$", "");
            serviceName = serviceName.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase() + "-service";
            sb.append("\"").append(serviceName).append("\"");
            if (i < dependencies.size() - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }
}
