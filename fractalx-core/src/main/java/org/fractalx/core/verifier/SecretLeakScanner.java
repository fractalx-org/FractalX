package org.fractalx.core.verifier;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans generated YAML, properties, and Java files for hardcoded secrets.
 *
 * <p>A secret is considered hardcoded when a sensitive key (password, token,
 * secret, api-key, private-key, …) has a plain-text value that is not:
 * <ul>
 *   <li>A Spring EL expression: {@code ${VAR:default}}</li>
 *   <li>Empty / null / placeholder: {@code ""}, {@code changeme}, {@code replace-me}</li>
 *   <li>A boolean / numeric literal</li>
 * </ul>
 *
 * <p>Findings are reported as WARNINGs (not failures) because the generator
 * may intentionally embed dev-only defaults. Operators should rotate these
 * before deploying to production.
 */
public class SecretLeakScanner {

    private static final Logger log = LoggerFactory.getLogger(SecretLeakScanner.class);

    // ── Sensitive key patterns ────────────────────────────────────────────────

    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i)(password|passwd|secret|token|api[._-]?key|private[._-]?key"
            + "|client[._-]?secret|jwt[._-]?secret|auth[._-]?key|credentials)"
    );

    /**
     * A value is suspicious if it is non-empty, not a Spring EL placeholder,
     * not a known safe placeholder, and longer than 3 characters.
     */
    private static final Pattern SAFE_VALUE = Pattern.compile(
            "^\\$\\{.*}$"                   // ${VAR} or ${VAR:default}
            + "|^(''|\"\"|null|~|true|false|0)$"   // YAML nulls/booleans
            + "|^[0-9]+$"                   // numeric
            + "|(?i)^(changeme|replace[_-]?me|your[_-]?.+|todo|fixme|example|test|demo|dummy|secret)$"
    );

    // ── Result model ──────────────────────────────────────────────────────────

    public record SecretLeak(
            String service,
            Path   file,
            int    line,
            String key,
            String maskedValue
    ) {
        @Override
        public String toString() {
            return "[WARN] Possible hardcoded secret in " + service
                    + " — key='" + key + "' value='" + maskedValue + "'"
                    + " at " + file.getFileName() + ":" + line;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public List<SecretLeak> scan(Path outputDir, List<FractalModule> modules) {
        List<SecretLeak> leaks = new ArrayList<>();

        for (FractalModule module : modules) {
            Path svcDir = outputDir.resolve(module.getServiceName());
            if (!Files.isDirectory(svcDir)) continue;
            scanDirectory(svcDir, module.getServiceName(), leaks);
        }

        // Also scan infrastructure services
        for (String infra : List.of("fractalx-gateway", "admin-service",
                "fractalx-registry", "logger-service")) {
            Path svcDir = outputDir.resolve(infra);
            if (Files.isDirectory(svcDir))
                scanDirectory(svcDir, infra, leaks);
        }

        return leaks;
    }

    // ── Directory scan ────────────────────────────────────────────────────────

    private void scanDirectory(Path svcDir, String serviceName, List<SecretLeak> leaks) {
        try (Stream<Path> walk = Files.walk(svcDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(this::isScannable)
                    .forEach(file -> scanFile(file, serviceName, leaks));
        } catch (IOException e) {
            log.debug("Could not walk {}: {}", svcDir, e.getMessage());
        }
    }

    private boolean isScannable(Path p) {
        String name = p.toString();
        return name.endsWith(".yml") || name.endsWith(".yaml")
                || name.endsWith(".properties");
    }

    private void scanFile(Path file, String serviceName, List<SecretLeak> leaks) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith("#")) continue;

                // Split on first colon (YAML key: value) or equals (properties key=value)
                int sep = firstSeparator(line);
                if (sep < 0) continue;

                String key   = line.substring(0, sep).trim();
                // If the key contains '${', the separator landed inside a Spring EL
                // expression like "- ${GATEWAY_API_KEY_1:default}" — skip the line.
                if (key.contains("${")) continue;
                String value = line.substring(sep + 1).trim()
                        .replaceAll("^['\"]|['\"]$", ""); // strip surrounding quotes

                if (SENSITIVE_KEY.matcher(key).find()
                        && !value.isBlank()
                        && !SAFE_VALUE.matcher(value).matches()
                        && value.length() > 3) {
                    leaks.add(new SecretLeak(serviceName, file, i + 1, key, mask(value)));
                }
            }
        } catch (IOException e) {
            log.debug("Could not read {}: {}", file, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int firstSeparator(String line) {
        int colon = line.indexOf(':');
        int eq    = line.indexOf('=');
        if (colon < 0 && eq < 0) return -1;
        if (colon < 0) return eq;
        if (eq < 0)    return colon;
        return Math.min(colon, eq);
    }

    private String mask(String value) {
        if (value.length() <= 4) return "****";
        return value.substring(0, 2) + "*".repeat(value.length() - 4)
                + value.substring(value.length() - 2);
    }
}
