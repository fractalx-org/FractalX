package org.fractalx.core.verifier;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Validates generated Flyway SQL migration scripts.
 *
 * <p>Checks:
 * <ol>
 *   <li>Naming convention — files must match {@code V<n>__<description>.sql}</li>
 *   <li>Non-empty — migration file must not be blank</li>
 *   <li>Cross-service table references — a service must not reference a table that
 *       is owned (i.e. created via CREATE TABLE) by another service. This would
 *       couple the databases at the schema level and defeat DB-per-service isolation.</li>
 *   <li>Outbox table — if the module has outbox support, verify the
 *       {@code outbox_events} table is defined</li>
 *   <li>Dangerous statements — DROP TABLE / TRUNCATE TABLE in a V* migration
 *       (should be in an undo script instead)</li>
 * </ol>
 */
public class SqlSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(SqlSchemaValidator.class);

    private static final Pattern FLYWAY_NAME = Pattern.compile("V\\d+__.*\\.sql");
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`\"']?(\\w+)[`\"']?");
    private static final Pattern REFERENCE_TABLE = Pattern.compile(
            "(?i)REFERENCES\\s+[`\"']?(\\w+)[`\"']?");
    private static final Pattern DANGEROUS = Pattern.compile(
            "(?i)^\\s*(DROP\\s+TABLE|TRUNCATE\\s+TABLE)");

    // ── Result model ──────────────────────────────────────────────────────────

    public enum SqlFindingKind {
        BAD_NAMING, EMPTY_MIGRATION, CROSS_SERVICE_REFERENCE, MISSING_OUTBOX_TABLE,
        DANGEROUS_STATEMENT
    }

    public record SqlFinding(SqlFindingKind kind, String service, Path file, String detail) {
        public boolean isCritical() {
            return kind == SqlFindingKind.CROSS_SERVICE_REFERENCE
                    || kind == SqlFindingKind.BAD_NAMING;
        }
        @Override
        public String toString() {
            String level = isCritical() ? "[FAIL]" : "[WARN]";
            return level + " SQL [" + service + "] " + file.getFileName() + " — " + detail;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public List<SqlFinding> validate(Path outputDir, List<FractalModule> modules) {
        List<SqlFinding> findings = new ArrayList<>();

        // First pass: collect all tables owned by each service
        List<ServiceTables> ownership = collectTableOwnership(outputDir, modules);

        // Second pass: validate each service's migrations
        for (FractalModule module : modules) {
            Path migrationsDir = outputDir.resolve(module.getServiceName())
                    .resolve("src/main/resources/db/migration");
            if (!Files.isDirectory(migrationsDir)) continue;

            validateService(migrationsDir, module, ownership, findings);
        }

        return findings;
    }

    // ── Table ownership collection ────────────────────────────────────────────

    private record ServiceTables(String serviceName, List<String> tables) {}

    private List<ServiceTables> collectTableOwnership(Path outputDir,
                                                       List<FractalModule> modules) {
        List<ServiceTables> result = new ArrayList<>();
        for (FractalModule module : modules) {
            Path dir = outputDir.resolve(module.getServiceName())
                    .resolve("src/main/resources/db/migration");
            if (!Files.isDirectory(dir)) continue;
            List<String> tables = new ArrayList<>();
            collectSqlFiles(dir).forEach(sql -> {
                try {
                    Matcher m = CREATE_TABLE.matcher(Files.readString(sql));
                    while (m.find()) tables.add(m.group(1).toLowerCase());
                } catch (IOException e) { /* skip */ }
            });
            result.add(new ServiceTables(module.getServiceName(), tables));
        }
        return result;
    }

    // ── Per-service validation ────────────────────────────────────────────────

    private void validateService(Path migrationsDir, FractalModule module,
                                  List<ServiceTables> ownership, List<SqlFinding> findings) {
        List<Path> sqlFiles = collectSqlFiles(migrationsDir);
        boolean hasOutboxTable = false;
        boolean expectsOutbox  = !module.getOwnedSchemas().isEmpty(); // proxy for outbox detection

        for (Path sql : sqlFiles) {
            String fileName = sql.getFileName().toString();

            // Naming convention
            if (!FLYWAY_NAME.matcher(fileName).matches()) {
                findings.add(new SqlFinding(SqlFindingKind.BAD_NAMING,
                        module.getServiceName(), sql,
                        "File does not match Flyway naming convention V<n>__<description>.sql"));
            }

            String content;
            try { content = Files.readString(sql); }
            catch (IOException e) { continue; }

            // Non-empty
            if (content.isBlank()) {
                findings.add(new SqlFinding(SqlFindingKind.EMPTY_MIGRATION,
                        module.getServiceName(), sql, "Migration file is empty"));
                continue;
            }

            // Outbox table detection
            if (content.toLowerCase().contains("outbox_event")) hasOutboxTable = true;

            // Dangerous statements
            for (String line : content.lines().toList()) {
                if (DANGEROUS.matcher(line).find()) {
                    findings.add(new SqlFinding(SqlFindingKind.DANGEROUS_STATEMENT,
                            module.getServiceName(), sql,
                            "Found '" + line.trim() + "' in a versioned migration — "
                                    + "use an undo migration instead"));
                }
            }

            // Cross-service table references
            Matcher ref = REFERENCE_TABLE.matcher(content);
            while (ref.find()) {
                String referencedTable = ref.group(1).toLowerCase();
                for (ServiceTables st : ownership) {
                    if (st.serviceName().equals(module.getServiceName())) continue;
                    if (st.tables().contains(referencedTable)) {
                        findings.add(new SqlFinding(SqlFindingKind.CROSS_SERVICE_REFERENCE,
                                module.getServiceName(), sql,
                                "REFERENCES table '" + referencedTable
                                        + "' owned by " + st.serviceName()
                                        + " — cross-service FK violates DB isolation"));
                    }
                }
            }
        }

        // Outbox table presence
        if (expectsOutbox && !hasOutboxTable) {
            findings.add(new SqlFinding(SqlFindingKind.MISSING_OUTBOX_TABLE,
                    module.getServiceName(), migrationsDir,
                    "Module owns schemas but no outbox_event table found in migrations"));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Path> collectSqlFiles(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.debug("Could not walk {}: {}", dir, e.getMessage());
            return List.of();
        }
    }
}
