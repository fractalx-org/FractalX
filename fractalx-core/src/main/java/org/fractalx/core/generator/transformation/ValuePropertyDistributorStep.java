package org.fractalx.core.generator.transformation;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Distributes {@code @Value("${...}")} and {@code @ConfigurationProperties(prefix="...")}
 * property values from the monolith's {@code application.yml} / {@code application.properties}
 * into the generated service's {@code application-dev.yml}.
 *
 * <p>Runs after {@link SharedCodeCopier} so all module source files are in place.
 * Only appends properties that actually exist in the monolith's config — missing keys
 * are listed in a warning comment so the developer knows what still needs to be set.
 */
public class ValuePropertyDistributorStep implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(ValuePropertyDistributorStep.class);

    /**
     * Keys (and key prefixes) that FractalX already generates correctly for each service.
     * Copying these from the monolith would overwrite the per-service values with the
     * monolith's global values (e.g. spring.application.name=demo overrides the service name).
     */
    private static final Set<String> BLOCKED_KEY_PREFIXES = Set.of(
            "spring.application.name",  // each service has its own name
            "spring.datasource",        // dev profile uses H2; monolith uses PostgreSQL
            "spring.jpa",               // generated in application-dev.yml already
            "spring.h2",                // generated in application-dev.yml already
            "spring.flyway",            // generated in application-dev.yml already
            "spring.profiles",          // managed by base application.yml
            "server.port",              // each service has its own port
            "management.",              // managed by base application.yml
            "logging."                  // managed by base application.yml
    );

    @Override
    public void generate(GenerationContext context) throws IOException {
        Path monolithResources = context.getSourceRoot().getParent().resolve("resources");
        Map<String, String> monolithProps = loadMonolithProperties(monolithResources);

        // Scan all copied Java files for @Value("${...}") and @ConfigurationProperties
        Set<String> requiredKeys   = new LinkedHashSet<>();
        Set<String> cfgPropPrefixes = new LinkedHashSet<>();

        try (Stream<Path> paths = Files.walk(context.getSrcMainJava())) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> extractPropertyReferences(p, requiredKeys, cfgPropPrefixes));
        }

        if (requiredKeys.isEmpty() && cfgPropPrefixes.isEmpty()) return;

        // Match against monolith props
        Map<String, String> resolved  = new LinkedHashMap<>();
        Set<String>          missing   = new LinkedHashSet<>();

        for (String key : requiredKeys) {
            if (isBlocked(key)) continue;
            if (monolithProps.containsKey(key)) {
                resolved.put(key, monolithProps.get(key));
            } else {
                missing.add(key);
            }
        }
        for (String prefix : cfgPropPrefixes) {
            if (isBlocked(prefix)) continue;
            boolean anyFound = false;
            for (Map.Entry<String, String> e : monolithProps.entrySet()) {
                if (isBlocked(e.getKey())) continue;
                if (e.getKey().startsWith(prefix + ".") || e.getKey().equals(prefix)) {
                    resolved.put(e.getKey(), e.getValue());
                    anyFound = true;
                }
            }
            if (!anyFound) missing.add(prefix + ".*");
        }

        if (resolved.isEmpty() && missing.isEmpty()) return;

        // Append to application-dev.yml
        Path devYml = context.getSrcMainResources().resolve("application-dev.yml");
        StringBuilder sb = new StringBuilder();

        if (!resolved.isEmpty()) {
            sb.append("\n# ---------------------------------------------------------------------------\n");
            sb.append("# Properties migrated from monolith application.yml by FractalX\n");
            sb.append("# ---------------------------------------------------------------------------\n");
            for (Map.Entry<String, String> e : resolved.entrySet()) {
                sb.append(e.getKey()).append(": ").append(quoteIfNeeded(e.getValue())).append("\n");
            }
        }

        if (!missing.isEmpty()) {
            sb.append("\n# ---------------------------------------------------------------------------\n");
            sb.append("# FRACTALX-WARNING: the following @Value / @ConfigurationProperties keys\n");
            sb.append("# were referenced in this service but not found in the monolith's\n");
            sb.append("# application.yml. Add their values manually before running:\n");
            for (String key : missing) {
                sb.append("# ").append(key).append(": <REQUIRED>\n");
            }
            sb.append("# ---------------------------------------------------------------------------\n");
        }

        Files.writeString(devYml, sb.toString(), StandardOpenOption.APPEND);
        log.info("Distributed {}/{} properties to {}",
                resolved.size(), requiredKeys.size() + cfgPropPrefixes.size(),
                context.getModule().getServiceName());
    }

    // -------------------------------------------------------------------------
    // Property key extraction from Java source files
    // -------------------------------------------------------------------------

    private void extractPropertyReferences(Path javaFile, Set<String> keys,
                                            Set<String> configPropPrefixes) {
        try {
            CompilationUnit cu = new JavaParser().parse(javaFile).getResult().orElse(null);
            if (cu == null) return;

            cu.findAll(AnnotationExpr.class).forEach(ann -> {
                String name = ann.getNameAsString();

                if ("Value".equals(name)) {
                    // @Value("${key}") or @Value("${key:default}")
                    String raw = extractSingleStringValue(ann);
                    if (raw != null && raw.startsWith("${") && raw.endsWith("}")) {
                        String inner = raw.substring(2, raw.length() - 1);
                        // Strip default value after ':'
                        String key = inner.contains(":") ? inner.substring(0, inner.indexOf(':')) : inner;
                        if (!key.isBlank()) keys.add(key.strip());
                    }
                } else if ("ConfigurationProperties".equals(name)) {
                    // @ConfigurationProperties(prefix = "some.prefix") or @ConfigurationProperties("prefix")
                    String prefix = extractConfigPropPrefix(ann);
                    if (prefix != null && !prefix.isBlank()) configPropPrefixes.add(prefix.strip());
                }
            });
        } catch (Exception e) {
            log.debug("Could not parse {} for @Value extraction", javaFile);
        }
    }

    private String extractSingleStringValue(AnnotationExpr ann) {
        if (ann instanceof SingleMemberAnnotationExpr sma) {
            return stripQuotes(sma.getMemberValue().toString());
        }
        if (ann instanceof NormalAnnotationExpr nae) {
            return nae.getPairs().stream()
                    .filter(p -> "value".equals(p.getNameAsString()))
                    .map(p -> stripQuotes(p.getValue().toString()))
                    .findFirst().orElse(null);
        }
        return null;
    }

    private String extractConfigPropPrefix(AnnotationExpr ann) {
        if (ann instanceof SingleMemberAnnotationExpr sma) {
            return stripQuotes(sma.getMemberValue().toString());
        }
        if (ann instanceof NormalAnnotationExpr nae) {
            for (MemberValuePair pair : nae.getPairs()) {
                if ("prefix".equals(pair.getNameAsString()) || "value".equals(pair.getNameAsString())) {
                    return stripQuotes(pair.getValue().toString());
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Monolith property loading
    // -------------------------------------------------------------------------

    private Map<String, String> loadMonolithProperties(Path resourcesDir) {
        Map<String, String> props = new LinkedHashMap<>();
        if (!Files.isDirectory(resourcesDir)) return props;

        // application.yml
        for (String name : new String[]{"application.yml", "application.yaml"}) {
            Path yml = resourcesDir.resolve(name);
            if (Files.exists(yml)) {
                try (InputStream is = Files.newInputStream(yml)) {
                    Object loaded = new Yaml().load(is);
                    if (loaded instanceof Map<?, ?> map) {
                        flattenMap(castMap(map), "", props);
                    }
                } catch (Exception e) {
                    log.debug("Could not parse {} for property distribution", yml);
                }
            }
        }

        // application.properties (plain key=value)
        Path propsFile = resourcesDir.resolve("application.properties");
        if (Files.exists(propsFile)) {
            try {
                for (String line : Files.readAllLines(propsFile)) {
                    line = line.strip();
                    if (line.startsWith("#") || !line.contains("=")) continue;
                    int eq = line.indexOf('=');
                    props.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
                }
            } catch (IOException e) {
                log.debug("Could not parse application.properties");
            }
        }

        return props;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    private static void flattenMap(Map<String, Object> map, String prefix,
                                    Map<String, String> out) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (e.getValue() instanceof Map<?, ?> nested) {
                flattenMap(castMap(nested), key, out);
            } else if (e.getValue() != null) {
                out.put(key, e.getValue().toString());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static String stripQuotes(String s) {
        if (s == null) return null;
        s = s.strip();
        if (s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s;
    }

    private static boolean isBlocked(String key) {
        if (key == null) return false;
        for (String prefix : BLOCKED_KEY_PREFIXES) {
            if (key.equals(prefix) || key.startsWith(prefix + ".")) return true;
        }
        return false;
    }

    private static String quoteIfNeeded(String value) {
        if (value == null) return "";
        // Quote if it contains special YAML chars or looks like a boolean/number the dev expects as string
        if (value.contains(":") || value.contains("#") || value.startsWith("*")) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }
}
