package org.fractalx.core.generator.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates the Incident Management subsystem for the admin service.
 *
 * <p>Produces 3 classes in {@code org.fractalx.admin.incidents}:
 * <ol>
 *   <li>{@code Incident}          — immutable record with severity, status, timeline</li>
 *   <li>{@code IncidentStore}     — thread-safe in-memory store</li>
 *   <li>{@code IncidentController} — REST API: /api/incidents/**</li>
 * </ol>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET    /api/incidents          — all incidents sorted by createdAt desc</li>
 *   <li>GET    /api/incidents/open     — OPEN + INVESTIGATING only</li>
 *   <li>GET    /api/incidents/stats    — counts per status + severity</li>
 *   <li>POST   /api/incidents          — create</li>
 *   <li>PUT    /api/incidents/{id}/status — update status</li>
 *   <li>PUT    /api/incidents/{id}     — full update</li>
 *   <li>DELETE /api/incidents/{id}     — delete</li>
 * </ul>
 */
class AdminIncidentGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminIncidentGenerator.class);

    void generate(Path srcMainJava, String basePackage) throws IOException {
        Path pkg = AdminPackageUtil.createPackagePath(srcMainJava, basePackage + ".incidents");
        generateIncident(pkg);
        generateIncidentStore(pkg);
        generateIncidentController(pkg);
        log.debug("Generated admin incident management subsystem");
    }

    // -------------------------------------------------------------------------

    private void generateIncident(Path pkg) throws IOException {
        Files.writeString(pkg.resolve("Incident.java"), """
                package org.fractalx.admin.incidents;

                import java.time.Instant;

                /**
                 * Represents a production incident.
                 * Immutable — use {@link Incident#withStatus} to transition.
                 */
                public record Incident(
                        String  id,
                        String  title,
                        String  description,
                        String  severity,         // P1 | P2 | P3 | P4
                        String  status,           // OPEN | INVESTIGATING | RESOLVED
                        String  affectedService,
                        String  notes,
                        String  assignee,
                        Instant createdAt,
                        Instant updatedAt,
                        Instant resolvedAt
                ) {
                    public Incident withStatus(String newStatus, String newNotes) {
                        return new Incident(id, title, description, severity, newStatus,
                                affectedService, newNotes != null ? newNotes : notes,
                                assignee, createdAt, Instant.now(),
                                "RESOLVED".equals(newStatus) ? Instant.now() : resolvedAt);
                    }
                    public Incident withUpdate(String newTitle, String newDesc,
                            String newSeverity, String newService, String newAssignee) {
                        return new Incident(id,
                                newTitle != null ? newTitle : title,
                                newDesc  != null ? newDesc  : description,
                                newSeverity != null ? newSeverity : severity,
                                status,
                                newService  != null ? newService  : affectedService,
                                notes,
                                newAssignee != null ? newAssignee : assignee,
                                createdAt, Instant.now(), resolvedAt);
                    }
                }
                """);
    }

    private void generateIncidentStore(Path pkg) throws IOException {
        Files.writeString(pkg.resolve("IncidentStore.java"), """
                package org.fractalx.admin.incidents;

                import org.springframework.stereotype.Component;
                import java.util.*;
                import java.util.concurrent.ConcurrentHashMap;
                import java.util.stream.Collectors;

                /** Thread-safe in-memory incident store. */
                @Component
                public class IncidentStore {

                    private final ConcurrentHashMap<String, Incident> incidents = new ConcurrentHashMap<>();

                    public IncidentStore() {}

                    public List<Incident> getAll() {
                        return incidents.values().stream()
                                .sorted(Comparator.comparing(Incident::createdAt).reversed())
                                .collect(Collectors.toList());
                    }

                    public List<Incident> getOpen() {
                        return incidents.values().stream()
                                .filter(i -> "OPEN".equals(i.status()) || "INVESTIGATING".equals(i.status()))
                                .sorted(Comparator.comparing(Incident::createdAt).reversed())
                                .collect(Collectors.toList());
                    }

                    public Optional<Incident> findById(String id) {
                        return Optional.ofNullable(incidents.get(id));
                    }

                    public Incident save(Incident incident) {
                        incidents.put(incident.id(), incident);
                        return incident;
                    }

                    public boolean delete(String id) {
                        return incidents.remove(id) != null;
                    }

                    public Map<String, Object> stats() {
                        Collection<Incident> all = incidents.values();
                        Map<String, Long> byStatus = all.stream()
                                .collect(Collectors.groupingBy(Incident::status, Collectors.counting()));
                        Map<String, Long> bySev = all.stream()
                                .collect(Collectors.groupingBy(Incident::severity, Collectors.counting()));
                        Map<String, Object> out = new LinkedHashMap<>();
                        out.put("total",         all.size());
                        out.put("open",          byStatus.getOrDefault("OPEN", 0L));
                        out.put("investigating", byStatus.getOrDefault("INVESTIGATING", 0L));
                        out.put("resolved",      byStatus.getOrDefault("RESOLVED", 0L));
                        out.put("bySeverity",    bySev);
                        return out;
                    }
                }
                """);
    }

    private void generateIncidentController(Path pkg) throws IOException {
        Files.writeString(pkg.resolve("IncidentController.java"), """
                package org.fractalx.admin.incidents;

                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                import java.time.Instant;
                import java.util.*;

                /**
                 * Incident management REST API.
                 * All state is in-memory; restarts clear incidents except the seeded example.
                 */
                @RestController
                @RequestMapping("/api/incidents")
                public class IncidentController {

                    private final IncidentStore store;

                    public IncidentController(IncidentStore store) { this.store = store; }

                    @GetMapping
                    public List<Incident> getAll() { return store.getAll(); }

                    @GetMapping("/open")
                    public List<Incident> getOpen() { return store.getOpen(); }

                    @GetMapping("/stats")
                    public Map<String, Object> stats() { return store.stats(); }

                    @GetMapping("/{id}")
                    public ResponseEntity<Incident> getOne(@PathVariable String id) {
                        return store.findById(id)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
                    }

                    @PostMapping
                    public Incident create(@RequestBody Map<String, Object> body) {
                        String id  = UUID.randomUUID().toString();
                        String sev = str(body, "severity", "P3");
                        Incident inc = new Incident(
                                id,
                                str(body, "title", "Untitled Incident"),
                                str(body, "description", ""),
                                sev, "OPEN",
                                str(body, "affectedService", ""),
                                str(body, "notes", ""),
                                str(body, "assignee", ""),
                                Instant.now(), Instant.now(), null);
                        return store.save(inc);
                    }

                    @PutMapping("/{id}/status")
                    public ResponseEntity<Incident> updateStatus(
                            @PathVariable String id,
                            @RequestBody Map<String, Object> body) {
                        return store.findById(id)
                                .map(inc -> {
                                    Incident updated = inc.withStatus(
                                            str(body, "status", inc.status()),
                                            str(body, "notes", null));
                                    return ResponseEntity.ok(store.save(updated));
                                })
                                .orElse(ResponseEntity.notFound().build());
                    }

                    @PutMapping("/{id}")
                    public ResponseEntity<Incident> update(
                            @PathVariable String id,
                            @RequestBody Map<String, Object> body) {
                        return store.findById(id)
                                .map(inc -> {
                                    Incident updated = inc.withUpdate(
                                            str(body, "title", null),
                                            str(body, "description", null),
                                            str(body, "severity", null),
                                            str(body, "affectedService", null),
                                            str(body, "assignee", null));
                                    return ResponseEntity.ok(store.save(updated));
                                })
                                .orElse(ResponseEntity.notFound().build());
                    }

                    @DeleteMapping("/{id}")
                    public ResponseEntity<Void> delete(@PathVariable String id) {
                        return store.delete(id)
                                ? ResponseEntity.noContent().build()
                                : ResponseEntity.notFound().build();
                    }

                    private static String str(Map<String, Object> m, String key, String def) {
                        Object v = m.get(key);
                        return (v != null && !String.valueOf(v).isBlank()) ? String.valueOf(v) : def;
                    }
                }
                """);
    }
}
