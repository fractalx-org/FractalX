package org.fractalx.core.verifier;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Verifies that every {@code @Value("${key}")} reference in generated Java source has a
 * corresponding entry in the service's {@code application.yml} (or {@code application.properties}).
 *
 * <p>This catches the common class of bugs where a generator emits a {@code @Value} placeholder
 * but forgets to add the matching key to the YAML config, causing a
 * {@code BeanCreationException: Could not resolve placeholder 'key'} at startup.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Walk all Java files — find {@code @Value("${...}")} string literals via JavaParser.</li>
 *   <li>Extract the key (strip {@code ${} and optional {@code :default} suffix).</li>
 *   <li>Flatten all YAML keys in {@code application.yml} using a simple dot-path walker.</li>
 *   <li>Report any {@code @Value} key not covered by the flattened YAML set.</li>
 * </ol>
 *
 * <p>Known limitations: does not resolve multi-document YAML ({@code ---}) or Spring profile
 * activation; keys present only in {@code application-dev.yml} are accepted as long as
 * they appear in any {@code application*.yml} file under {@code src/main/resources}.
 */
public class ConfigPropertyChecker {

    private static final Logger log = LoggerFactory.getLogger(ConfigPropertyChecker.class);

    /** Matches ${key}, ${key:default}, ${key:${nested}} — captures the key part only. */
    private static final Pattern VALUE_PLACEHOLDER =
            Pattern.compile("\\$\\{([^}:]+)(?::[^}]*)?}");

    /** Matches YAML scalar lines like `  someKey: someValue` or `someKey:`. */
    private static final Pattern YAML_KEY_LINE =
            Pattern.compile("^([ \\t]*)([\\w.\\-]+)\\s*:(\\s.*|$)");

    // ── Result model ──────────────────────────────────────────────────────────

    public enum CfgFindingKind {
        MISSING_PROPERTY,   // @Value key not found in any application*.yml
        EMPTY_VALUE_REF     // @Value("") — likely a generated placeholder error
    }

    public record CfgFinding(
            CfgFindingKind kind,
            String         serviceName,
            Path           javaFile,
            String         key,
            String         detail
    ) {
        public boolean isCritical() { return kind == CfgFindingKind.MISSING_PROPERTY; }

        @Override
        public String toString() {
            String level = isCritical() ? "[FAIL]" : "[WARN]";
            return level + " Config [" + serviceName + "] @Value(\"${" + key + "}\")"
                    + " — " + detail + " [" + javaFile.getFileName() + "]";
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public List<CfgFinding> check(Path outputDir, List<FractalModule> modules) {
        List<CfgFinding> findings = new ArrayList<>();

        for (FractalModule module : modules) {
            Path svcDir = outputDir.resolve(module.getServiceName());
            if (!Files.isDirectory(svcDir)) continue;
            checkService(svcDir, module.getServiceName(), findings);
        }

        for (String infra : List.of("admin-service", "fractalx-gateway",
                "fractalx-registry", "logger-service")) {
            Path svcDir = outputDir.resolve(infra);
            if (Files.isDirectory(svcDir))
                checkService(svcDir, infra, findings);
        }

        return findings;
    }

    // ── Per-service check ─────────────────────────────────────────────────────

    private void checkService(Path svcDir, String serviceName,
                               List<CfgFinding> findings) {
        Path srcJava      = svcDir.resolve("src/main/java");
        Path srcResources = svcDir.resolve("src/main/resources");

        if (!Files.isDirectory(srcJava)) return;

        // Collect all property keys from every application*.yml / *.properties
        Set<String> definedKeys = collectDefinedKeys(srcResources);

        // Collect all @Value references from Java source
        try (Stream<Path> walk = Files.walk(srcJava)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> checkJavaFile(file, serviceName, definedKeys, findings));
        } catch (IOException e) {
            log.debug("Could not walk {}: {}", srcJava, e.getMessage());
        }
    }

    // ── YAML key collection ───────────────────────────────────────────────────

    private Set<String> collectDefinedKeys(Path resourcesDir) {
        Set<String> keys = new HashSet<>();
        if (!Files.isDirectory(resourcesDir)) return keys;

        try (Stream<Path> walk = Files.walk(resourcesDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return (name.startsWith("application") && name.endsWith(".yml"))
                                || (name.startsWith("application") && name.endsWith(".yaml"))
                                || (name.startsWith("application") && name.endsWith(".properties"));
                    })
                    .forEach(f -> {
                        if (f.toString().endsWith(".properties"))
                            collectPropertiesKeys(f, keys);
                        else
                            collectYamlKeys(f, keys);
                    });
        } catch (IOException e) {
            log.debug("Could not walk {}: {}", resourcesDir, e.getMessage());
        }
        return keys;
    }

    private void collectYamlKeys(Path yamlFile, Set<String> keys) {
        try {
            List<String> lines = Files.readAllLines(yamlFile);
            // Stack tracks current key path per indent level (2-space YAML assumed)
            List<String> pathStack = new ArrayList<>();
            int prevIndent = 0;

            for (String line : lines) {
                if (line.trim().startsWith("#") || line.trim().isBlank()
                        || line.trim().equals("---")) continue;

                Matcher m = YAML_KEY_LINE.matcher(line);
                if (!m.matches()) continue;

                String indent  = m.group(1);
                String keyPart = m.group(2);
                int depth      = indent.length() / 2; // assume 2-space indent

                // Trim stack to current depth
                while (pathStack.size() > depth) pathStack.remove(pathStack.size() - 1);
                pathStack.add(keyPart);

                String fullKey = String.join(".", pathStack);
                keys.add(fullKey);
                // Also add with hyphen-to-camel variants Spring resolves
                keys.add(fullKey.replace("-", ""));

            }
        } catch (IOException e) {
            log.debug("Could not read YAML {}: {}", yamlFile, e.getMessage());
        }
    }

    private void collectPropertiesKeys(Path propsFile, Set<String> keys) {
        try {
            Files.readAllLines(propsFile).stream()
                    .filter(l -> !l.trim().startsWith("#") && l.contains("="))
                    .map(l -> l.substring(0, l.indexOf('=')).trim())
                    .forEach(keys::add);
        } catch (IOException e) {
            log.debug("Could not read properties {}: {}", propsFile, e.getMessage());
        }
    }

    // ── Java @Value analysis ──────────────────────────────────────────────────

    private void checkJavaFile(Path file, String serviceName,
                                Set<String> definedKeys,
                                List<CfgFinding> findings) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);

            cu.findAll(AnnotationExpr.class).stream()
                    .filter(a -> a.getNameAsString().equals("Value"))
                    .forEach(ann -> {
                        String raw = extractValueString(ann);
                        if (raw == null) return;

                        if (raw.isBlank()) {
                            findings.add(new CfgFinding(CfgFindingKind.EMPTY_VALUE_REF,
                                    serviceName, file, "(empty)", "@Value(\"\") is a generated stub"));
                            return;
                        }

                        Matcher m = VALUE_PLACEHOLDER.matcher(raw);
                        while (m.find()) {
                            String key = m.group(1).trim();
                            if (!isKnownFrameworkKey(key) && !definedKeys.contains(key)
                                    && !isCoveredByPrefix(key, definedKeys)) {
                                findings.add(new CfgFinding(CfgFindingKind.MISSING_PROPERTY,
                                        serviceName, file, key,
                                        "No matching key in application*.yml — service will fail to start"));
                            }
                        }
                    });

        } catch (Exception e) {
            log.debug("Could not parse {}: {}", file, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractValueString(AnnotationExpr ann) {
        // @Value("${key}") — single-value annotation
        if (ann.isSingleMemberAnnotationExpr()) {
            var expr = ann.asSingleMemberAnnotationExpr().getMemberValue();
            if (expr instanceof StringLiteralExpr sle) return sle.asString();
        }
        // @Value(value = "${key}")
        if (ann.isNormalAnnotationExpr()) {
            return ann.asNormalAnnotationExpr().getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("value"))
                    .map(MemberValuePair::getValue)
                    .filter(e -> e instanceof StringLiteralExpr)
                    .map(e -> ((StringLiteralExpr) e).asString())
                    .findFirst().orElse(null);
        }
        return null;
    }

    /** Well-known Spring / actuator / server keys that are always resolved by the framework. */
    private boolean isKnownFrameworkKey(String key) {
        return key.startsWith("spring.")
                || key.startsWith("server.")
                || key.startsWith("management.")
                || key.startsWith("logging.")
                || key.startsWith("info.")
                || key.startsWith("fractalx.");  // FractalX platform keys are injected at runtime
    }

    /**
     * Returns true if any defined key is a prefix of the requested key
     * (accounts for YAML maps where a parent key covers nested keys).
     */
    private boolean isCoveredByPrefix(String key, Set<String> definedKeys) {
        for (String defined : definedKeys) {
            if (key.startsWith(defined + ".") || key.startsWith(defined + "-")) return true;
        }
        return false;
    }
}
