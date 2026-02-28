package org.fractalx.core.generator.admin;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates the Data Consistency section for the admin service:
 * <ul>
 *   <li>{@code SagaMetaRegistry} — baked-in saga definitions</li>
 *   <li>{@code DataConsistencyController} — REST API for sagas, databases, outbox</li>
 * </ul>
 */
class AdminDataConsistencyGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminDataConsistencyGenerator.class);

    void generate(Path srcMainJava, String basePackage, List<FractalModule> modules) throws IOException {
        Path pkg = AdminPackageUtil.createPackagePath(srcMainJava, basePackage + ".data");

        generateSagaMetaRegistry(pkg, modules);
        generateDataConsistencyController(pkg, modules);

        log.debug("Generated admin data consistency components");
    }

    // -------------------------------------------------------------------------

    private void generateSagaMetaRegistry(Path pkg, List<FractalModule> modules) throws IOException {
        // Derive saga entries from modules that have cross-service dependencies
        StringBuilder sagaEntries = new StringBuilder();
        for (FractalModule m : modules) {
            if (m.getDependencies() != null && !m.getDependencies().isEmpty()) {
                // Generate a representative saga definition for each service with dependencies
                String sagaId = toCamelCase(m.getServiceName()) + "Saga";
                StringBuilder steps = new StringBuilder("List.of(");
                for (int i = 0; i < m.getDependencies().size(); i++) {
                    steps.append("\"Call ").append(m.getDependencies().get(i)).append("\"");
                    if (i < m.getDependencies().size() - 1) steps.append(", ");
                }
                steps.append(")");
                sagaEntries.append(String.format(
                        "        new SagaInfo(\"%s\", \"%s\", %s, List.of(\"Compensate\"), true),\n",
                        sagaId, m.getServiceName(), steps.toString()));
            }
        }

        String content = """
                package org.fractalx.admin.data;

                import org.springframework.stereotype.Component;

                import java.util.List;
                import java.util.Optional;

                /**
                 * Baked-in registry of saga orchestration definitions derived at generation time.
                 * Reflects modules that have cross-service dependencies and may participate in sagas.
                 */
                @Component
                public class SagaMetaRegistry {

                    public record SagaInfo(
                            String sagaId, String orchestratedBy,
                            List<String> steps, List<String> compensationSteps, boolean enabled) {}

                    private static final List<SagaInfo> SAGAS = List.of(
                %s    );

                    public List<SagaInfo> getAll()                     { return SAGAS; }
                    public int            count()                      { return SAGAS.size(); }

                    public Optional<SagaInfo> findById(String sagaId) {
                        return SAGAS.stream().filter(s -> s.sagaId().equals(sagaId)).findFirst();
                    }

                    public boolean hasSagas() { return !SAGAS.isEmpty(); }
                }
                """.formatted(stripTrailingComma(sagaEntries.toString()));

        Files.writeString(pkg.resolve("SagaMetaRegistry.java"), content);
    }

    private void generateDataConsistencyController(Path pkg, List<FractalModule> modules)
            throws IOException {

        // Generate health check calls per service for DB endpoint
        StringBuilder dbChecks = new StringBuilder();
        for (FractalModule m : modules) {
            if (m.getOwnedSchemas() != null && !m.getOwnedSchemas().isEmpty()) {
                String schemas = String.join(", ", m.getOwnedSchemas());
                dbChecks.append(String.format(
                        "        {Map<String,Object> db = new LinkedHashMap<>(); db.put(\"service\",\"%s\"); db.put(\"schemas\",\"%s\"); db.put(\"health\", fetchDbHealth(%d)); dbList.add(db);}\n",
                        m.getServiceName(), schemas, m.getPort()));
            } else {
                dbChecks.append(String.format(
                        "        {Map<String,Object> db = new LinkedHashMap<>(); db.put(\"service\",\"%s\"); db.put(\"schemas\",\"default\"); db.put(\"health\", fetchDbHealth(%d)); dbList.add(db);}\n",
                        m.getServiceName(), m.getPort()));
            }
        }

        // Outbox check for services with dependencies (they generated outbox support)
        StringBuilder outboxChecks = new StringBuilder();
        for (FractalModule m : modules) {
            outboxChecks.append(String.format(
                    "        {Map<String,Object> ob = new LinkedHashMap<>(); ob.put(\"service\",\"%s\"); ob.put(\"metrics\", fetchOutboxMetrics(%d)); outboxList.add(ob);}\n",
                    m.getServiceName(), m.getPort()));
        }

        int serviceCount = modules.size();

        String content = """
                package org.fractalx.admin.data;

                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                import org.springframework.web.client.RestTemplate;

                import java.util.*;

                /**
                 * REST API for the Data Consistency section of the admin dashboard.
                 *
                 * <pre>
                 * GET /api/data/overview              — summary of all data consistency features
                 * GET /api/data/sagas                 — all baked saga definitions
                 * GET /api/data/sagas/instances       — proxy to saga-orchestrator GET /saga
                 * GET /api/data/sagas/{sagaId}/instances — instances filtered by sagaId
                 * GET /api/data/databases             — per-service DB health (actuator/health/db)
                 * GET /api/data/schemas               — per-service owned schemas
                 * GET /api/data/outbox                — per-service outbox metrics
                 * </pre>
                 */
                @RestController
                @RequestMapping("/api/data")
                @CrossOrigin(origins = "*")
                public class DataConsistencyController {

                    private static final int SAGA_ORCHESTRATOR_PORT = 8099;

                    private final SagaMetaRegistry registry;
                    private final RestTemplate     restTemplate = new RestTemplate();

                    public DataConsistencyController(SagaMetaRegistry registry) {
                        this.registry = registry;
                    }

                    /** Overview: service count, saga count, schema summary. */
                    @GetMapping("/overview")
                    public ResponseEntity<Map<String, Object>> getOverview() {
                        Map<String, Object> overview = new LinkedHashMap<>();
                        overview.put("totalServices",  %d);
                        overview.put("totalSagas",     registry.count());
                        overview.put("hasSagas",       registry.hasSagas());
                        overview.put("sagaOrchestrator",
                            Map.of("port", SAGA_ORCHESTRATOR_PORT,
                                   "health", fetchSagaOrchestratorHealth()));
                        return ResponseEntity.ok(overview);
                    }

                    /** Returns all baked saga definitions. */
                    @GetMapping("/sagas")
                    public ResponseEntity<List<SagaMetaRegistry.SagaInfo>> getSagas() {
                        return ResponseEntity.ok(registry.getAll());
                    }

                    /** Proxies to saga-orchestrator GET /saga for all saga instances. */
                    @GetMapping("/sagas/instances")
                    public ResponseEntity<Object> getAllSagaInstances() {
                        try {
                            Object resp = restTemplate.getForObject(
                                    "http://localhost:" + SAGA_ORCHESTRATOR_PORT + "/saga", Object.class);
                            return ResponseEntity.ok(resp);
                        } catch (Exception e) {
                            return ResponseEntity.ok(Map.of(
                                "error", "Saga orchestrator unavailable: " + e.getMessage()));
                        }
                    }

                    /** Proxies to saga-orchestrator filtered by sagaId. */
                    @GetMapping("/sagas/{sagaId}/instances")
                    public ResponseEntity<Object> getSagaInstances(@PathVariable("sagaId") String sagaId) {
                        try {
                            Object resp = restTemplate.getForObject(
                                    "http://localhost:" + SAGA_ORCHESTRATOR_PORT + "/saga?sagaId=" + sagaId,
                                    Object.class);
                            return ResponseEntity.ok(resp);
                        } catch (Exception e) {
                            return ResponseEntity.ok(Map.of(
                                "error", "Saga orchestrator unavailable: " + e.getMessage()));
                        }
                    }

                    /** Per-service database health from /actuator/health/db. */
                    @GetMapping("/databases")
                    public ResponseEntity<List<Map<String, Object>>> getDatabases() {
                        List<Map<String, Object>> dbList = new ArrayList<>();
                %s                return ResponseEntity.ok(dbList);
                    }

                    /** Per-service owned schema info (baked from generation metadata). */
                    @GetMapping("/schemas")
                    public ResponseEntity<List<Map<String, Object>>> getSchemas() {
                        List<Map<String, Object>> result = new ArrayList<>();
                        for (SagaMetaRegistry.SagaInfo saga : registry.getAll()) {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("service", saga.orchestratedBy());
                            result.add(entry);
                        }
                        return ResponseEntity.ok(result);
                    }

                    /** Per-service outbox metrics from /actuator/metrics. */
                    @GetMapping("/outbox")
                    public ResponseEntity<List<Map<String, Object>>> getOutbox() {
                        List<Map<String, Object>> outboxList = new ArrayList<>();
                %s                return ResponseEntity.ok(outboxList);
                    }

                    // -------------------------------------------------------------------------

                    private String fetchDbHealth(int port) {
                        try {
                            Object resp = restTemplate.getForObject(
                                    "http://localhost:" + port + "/actuator/health/db", Object.class);
                            return resp != null && resp.toString().contains("UP") ? "UP" : "DOWN";
                        } catch (Exception e) {
                            return "UNKNOWN";
                        }
                    }

                    private Object fetchOutboxMetrics(int port) {
                        try {
                            return restTemplate.getForObject(
                                    "http://localhost:" + port + "/actuator/metrics", Object.class);
                        } catch (Exception e) {
                            return Map.of("error", e.getMessage());
                        }
                    }

                    private String fetchSagaOrchestratorHealth() {
                        try {
                            String resp = restTemplate.getForObject(
                                    "http://localhost:" + SAGA_ORCHESTRATOR_PORT + "/actuator/health",
                                    String.class);
                            return (resp != null && resp.contains("UP")) ? "UP" : "DOWN";
                        } catch (Exception e) {
                            return "DOWN";
                        }
                    }
                }
                """.formatted(serviceCount, dbChecks.toString(), outboxChecks.toString());

        Files.writeString(pkg.resolve("DataConsistencyController.java"), content);
    }

    // -------------------------------------------------------------------------

    /** Removes a trailing {@code ,\n} so List.of() method calls don't end with a stray comma. */
    private String stripTrailingComma(String s) {
        return s.endsWith(",\n") ? s.substring(0, s.length() - 2) + "\n" : s;
    }

    private String toCamelCase(String kebabCase) {
        String[] parts = kebabCase.split("-");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)))
                  .append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }
}
