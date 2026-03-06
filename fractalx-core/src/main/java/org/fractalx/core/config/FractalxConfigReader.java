package org.fractalx.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Reads platform-level FractalX configuration from the pre-decomposed
 * application's resource directory and produces a {@link FractalxConfig}.
 *
 * <p>Two sources are consulted in priority order:
 * <ol>
 *   <li>{@code fractalx-config.yml} — FractalX-specific config file (preferred)</li>
 *   <li>{@code application.yml} / {@code application.properties} — the monolith's
 *       own Spring config (fallback for values not in fractalx-config.yml)</li>
 * </ol>
 *
 * <p>Any value not found in either source falls back to {@link FractalxConfig#defaults()}.
 */
public class FractalxConfigReader {

    private static final Logger log = LoggerFactory.getLogger(FractalxConfigReader.class);

    /**
     * Reads configuration from {@code <sourceResourcesDir>/fractalx-config.yml}
     * and {@code <sourceResourcesDir>/application.yml}.
     *
     * @param sourceResourcesDir  {@code src/main/resources} of the monolith project
     * @return populated {@link FractalxConfig}, never null
     */
    public FractalxConfig read(Path sourceResourcesDir) {
        FractalxConfig defaults = FractalxConfig.defaults();
        if (!Files.isDirectory(sourceResourcesDir)) return defaults;

        // Primary: fractalx-config.yml
        Map<String, Object> fractalxMap = loadYaml(sourceResourcesDir.resolve("fractalx-config.yml"));
        // Fallback: application.yml for spring-standard keys
        Map<String, Object> appMap      = loadYaml(sourceResourcesDir.resolve("application.yml"));
        // Also check application.properties
        Properties appProps = loadProperties(sourceResourcesDir.resolve("application.properties"));

        // Navigate into the 'fractalx' sub-map from fractalx-config.yml
        Map<String, Object> fx = nestedMap(fractalxMap, "fractalx");

        String registryUrl  = first(
                leaf(fx, "registry", "url"),
                leaf(appMap, "fractalx", "registry", "url"),
                appProps.getProperty("fractalx.registry.url"),
                defaults.registryUrl());

        String loggerUrl    = first(
                leaf(fx, "logger", "url"),
                leaf(appMap, "fractalx", "logger", "url"),
                appProps.getProperty("fractalx.logger.url"),
                defaults.loggerUrl());

        String otelEndpoint = first(
                leaf(fx, "otel", "endpoint"),
                leaf(appMap, "fractalx", "otel", "endpoint"),
                appProps.getProperty("fractalx.otel.endpoint"),
                defaults.otelEndpoint());

        int gatewayPort     = firstInt(defaults.gatewayPort(),
                leaf(fx, "gateway", "port"),
                leaf(appMap, "fractalx", "gateway", "port"),
                appProps.getProperty("fractalx.gateway.port"));

        String corsOrigins  = first(
                leaf(fx, "gateway", "cors", "allowed-origins"),
                leaf(appMap, "fractalx", "gateway", "cors", "allowed-origins"),
                appProps.getProperty("fractalx.gateway.cors.allowed-origins"),
                defaults.corsAllowedOrigins());

        String jwksUri      = first(
                leaf(fx, "gateway", "security", "oauth2", "jwk-set-uri"),
                leaf(appMap, "fractalx", "gateway", "security", "oauth2", "jwk-set-uri"),
                appProps.getProperty("fractalx.gateway.security.oauth2.jwk-set-uri"),
                defaults.oauth2JwksUri());

        int adminPort       = firstInt(defaults.adminPort(),
                leaf(fx, "admin", "port"),
                leaf(appMap, "fractalx", "admin", "port"),
                appProps.getProperty("fractalx.admin.port"));

        // Per-service overrides
        Map<String, FractalxConfig.ServiceOverride> overrides = readServiceOverrides(fx);

        FractalxConfig cfg = new FractalxConfig(
                registryUrl, loggerUrl, otelEndpoint,
                gatewayPort, corsOrigins, jwksUri,
                adminPort, overrides);

        log.info("[FractalxConfig] registry={} logger={} otel={} gateway-port={} admin-port={}",
                registryUrl, loggerUrl, otelEndpoint, gatewayPort, adminPort);
        return cfg;
    }

    // ── Per-service port overrides ────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, FractalxConfig.ServiceOverride> readServiceOverrides(Map<String, Object> fx) {
        Map<String, FractalxConfig.ServiceOverride> result = new HashMap<>();
        Object services = fx.get("services");
        if (!(services instanceof Map)) return result;
        Map<String, Object> svcMap = (Map<String, Object>) services;
        svcMap.forEach((name, val) -> {
            if (!(val instanceof Map)) return;
            Map<?, ?> svc = (Map<?, ?>) val;
            int port = 0;
            Object p = svc.get("port");
            if (p instanceof Number n) port = n.intValue();
            boolean tracingEnabled = true;
            Object tracingObj = svc.get("tracing");
            if (tracingObj instanceof Map<?, ?> tracingMap) {
                Object enabledVal = tracingMap.get("enabled");
                if (enabledVal instanceof Boolean b) tracingEnabled = b;
                else if ("false".equalsIgnoreCase(String.valueOf(enabledVal))) tracingEnabled = false;
            }
            result.put(name, new FractalxConfig.ServiceOverride(port, tracingEnabled));
        });
        return result;
    }

    // ── YAML / Properties loading ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(Path path) {
        if (!Files.exists(path)) return Map.of();
        try (InputStream is = Files.newInputStream(path)) {
            Object loaded = new Yaml().load(is);
            if (loaded instanceof Map<?, ?> m) return (Map<String, Object>) m;
        } catch (Exception e) {
            log.debug("Could not parse {}: {}", path.getFileName(), e.getMessage());
        }
        return Map.of();
    }

    private Properties loadProperties(Path path) {
        Properties p = new Properties();
        if (!Files.exists(path)) return p;
        try (InputStream is = Files.newInputStream(path)) {
            p.load(is);
        } catch (IOException e) {
            log.debug("Could not read {}: {}", path.getFileName(), e.getMessage());
        }
        return p;
    }

    // ── Navigation helpers ────────────────────────────────────────────────────

    /**
     * Navigates into a nested map and returns the sub-map at the given key.
     * Returns an empty map if the key is absent or the value is not a map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> map, String key) {
        if (map == null || map.isEmpty()) return Map.of();
        Object val = map.get(key);
        if (val instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }

    /**
     * Navigates nested map keys and returns the leaf string value, or {@code null}.
     * Intermediate keys navigate into sub-maps; the last key fetches the leaf value.
     */
    @SuppressWarnings("unchecked")
    private String leaf(Map<String, Object> map, String... keys) {
        if (map == null || map.isEmpty()) return null;
        Map<String, Object> cur = map;
        for (int i = 0; i < keys.length - 1; i++) {
            Object next = cur.get(keys[i]);
            if (!(next instanceof Map)) return null;
            cur = (Map<String, Object>) next;
        }
        Object val = cur.get(keys[keys.length - 1]);
        return val != null ? val.toString() : null;
    }

    /** Returns the first non-null, non-blank string from the candidates. */
    private String first(String... candidates) {
        for (String c : candidates) if (c != null && !c.isBlank()) return c;
        return null; // should not happen — last candidate is always a default
    }

    /** Returns the first parseable integer from the candidates, or {@code defaultVal}. */
    private int firstInt(int defaultVal, String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                try { return Integer.parseInt(c.trim()); }
                catch (NumberFormatException ignored) {}
            }
        }
        return defaultVal;
    }
}
