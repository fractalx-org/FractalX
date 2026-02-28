package org.fractalx.core.generator.admin;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates the Config Editor subsystem for the admin service.
 *
 * <p>Produces 1 class in {@code org.fractalx.admin.svcconfig}:
 * <ul>
 *   <li>{@code ConfigEditorController} — REST API: /api/config/editor/**</li>
 * </ul>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET    /api/config/editor/all              — all service configs</li>
 *   <li>GET    /api/config/editor/{name}           — one service config</li>
 *   <li>GET    /api/config/editor/overrides        — all in-memory overrides</li>
 *   <li>POST   /api/config/editor/override         — set override {service, key, value}</li>
 *   <li>DELETE /api/config/editor/override/{svc}/{key} — clear override</li>
 *   <li>POST   /api/config/editor/reload/{service} — try /actuator/refresh on the service</li>
 *   <li>GET    /api/config/editor/diff             — compare overrides vs base config</li>
 * </ul>
 */
class AdminConfigEditorGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminConfigEditorGenerator.class);

    void generate(Path srcMainJava, String basePackage, List<FractalModule> modules) throws IOException {
        Path pkg = AdminPackageUtil.createPackagePath(srcMainJava, basePackage + ".svcconfig");
        generateConfigEditorController(pkg);
        log.debug("Generated admin config editor subsystem ({} modules)", modules.size());
    }

    // -------------------------------------------------------------------------

    private void generateConfigEditorController(Path pkg) throws IOException {
        Files.writeString(pkg.resolve("ConfigEditorController.java"), """
                package org.fractalx.admin.svcconfig;

                import org.springframework.http.ResponseEntity;
                import org.springframework.http.client.SimpleClientHttpRequestFactory;
                import org.springframework.web.bind.annotation.*;
                import org.springframework.web.client.RestTemplate;

                import java.util.*;
                import java.util.concurrent.ConcurrentHashMap;

                /**
                 * Config Editor REST API.
                 *
                 * <p>Reads the baked-in service configuration from {@link ServiceConfigStore} and
                 * allows operators to layer environment-variable overrides on top. Overrides are
                 * kept in-memory only — a service restart is required to apply them unless the
                 * target service supports Spring Cloud {@code /actuator/refresh}.
                 */
                @RestController
                @RequestMapping("/api/config/editor")
                public class ConfigEditorController {

                    private final ServiceConfigStore configStore;
                    private final RestTemplate rest;

                    /** service → (key → value) */
                    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> overrides =
                            new ConcurrentHashMap<>();

                    public ConfigEditorController(ServiceConfigStore configStore) {
                        this.configStore = configStore;
                        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
                        f.setConnectTimeout(2_000);
                        f.setReadTimeout(3_000);
                        this.rest = new RestTemplate(f);
                    }

                    @GetMapping("/all")
                    public List<Map<String, Object>> all() {
                        List<Map<String, Object>> out = new ArrayList<>();
                        for (ServiceConfigStore.ServiceConfig cfg : configStore.getAll()) {
                            out.add(toMap(cfg));
                        }
                        return out;
                    }

                    @GetMapping("/{name}")
                    public ResponseEntity<Map<String, Object>> one(@PathVariable String name) {
                        return configStore.findByName(name)
                                .map(cfg -> ResponseEntity.ok(toMap(cfg)))
                                .orElse(ResponseEntity.notFound().build());
                    }

                    @GetMapping("/overrides")
                    public Map<String, Object> overrides() {
                        Map<String, Object> out = new LinkedHashMap<>();
                        overrides.forEach((svc, kvs) -> out.put(svc, new LinkedHashMap<>(kvs)));
                        return out;
                    }

                    @PostMapping("/override")
                    public Map<String, Object> setOverride(@RequestBody Map<String, String> body) {
                        String service = body.get("service");
                        String key     = body.get("key");
                        String value   = body.get("value");
                        if (service == null || key == null || value == null)
                            return Map.of("error", "service, key, value are required");
                        overrides.computeIfAbsent(service, k -> new ConcurrentHashMap<>()).put(key, value);
                        return Map.of("service", service, "key", key, "value", value, "status", "saved");
                    }

                    @DeleteMapping("/override/{service}/{key}")
                    public ResponseEntity<Map<String, Object>> removeOverride(
                            @PathVariable String service, @PathVariable String key) {
                        ConcurrentHashMap<String, String> svcOverrides = overrides.get(service);
                        if (svcOverrides == null || svcOverrides.remove(key) == null)
                            return ResponseEntity.notFound().build();
                        return ResponseEntity.ok(Map.of("removed", key, "service", service));
                    }

                    @GetMapping("/diff")
                    public Map<String, Object> diff() {
                        Map<String, Object> out = new LinkedHashMap<>();
                        overrides.forEach((svc, kvs) -> {
                            Optional<ServiceConfigStore.ServiceConfig> cfgOpt = configStore.findByName(svc);
                            List<Map<String, Object>> changes = new ArrayList<>();
                            kvs.forEach((k, newVal) -> {
                                String baseVal = cfgOpt.map(c -> c.envVars().get(k)).orElse(null);
                                Map<String, Object> change = new LinkedHashMap<>();
                                change.put("key",      k);
                                change.put("baseValue", baseVal != null ? baseVal : "(not set)");
                                change.put("newValue",  newVal);
                                change.put("isNew",     baseVal == null);
                                changes.add(change);
                            });
                            if (!changes.isEmpty()) out.put(svc, changes);
                        });
                        return out;
                    }

                    @PostMapping("/reload/{service}")
                    public Map<String, Object> reload(@PathVariable String service) {
                        return configStore.findByName(service)
                                .map(cfg -> {
                                    String url = "http://localhost:" + cfg.httpPort() + "/actuator/refresh";
                                    Map<String, Object> result = new LinkedHashMap<>();
                                    try {
                                        rest.postForObject(url, null, Object.class);
                                        result.put("success", true);
                                        result.put("message", "Refresh triggered on " + service);
                                        result.put("url", url);
                                    } catch (Exception e) {
                                        result.put("success", false);
                                        result.put("message", "Could not reach " + url + ": " + e.getMessage());
                                        result.put("hint", "Service may need a restart to apply changes");
                                    }
                                    return result;
                                })
                                .orElseGet(() -> {
                                    Map<String, Object> err = new LinkedHashMap<>();
                                    err.put("error", "Unknown service: " + service);
                                    return err;
                                });
                    }

                    private Map<String, Object> toMap(ServiceConfigStore.ServiceConfig cfg) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name",         cfg.name());
                        m.put("httpPort",     cfg.httpPort());
                        m.put("grpcPort",     cfg.grpcPort());
                        m.put("packageName",  cfg.packageName());
                        m.put("hasOutbox",    cfg.hasOutbox());
                        m.put("hasSaga",      cfg.hasSaga());
                        m.put("ownedSchemas", cfg.ownedSchemas());
                        m.put("envVars",      cfg.envVars());
                        // merge in-memory overrides
                        Map<String, String> ov = overrides.getOrDefault(cfg.name(), new ConcurrentHashMap<>());
                        m.put("overrides", ov);
                        Map<String, String> effective = new LinkedHashMap<>(cfg.envVars());
                        effective.putAll(ov);
                        m.put("effective", effective);
                        return m;
                    }
                }
                """);
    }
}
