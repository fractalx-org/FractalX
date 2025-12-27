package com.fractalx.core.datamanagement;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Generates the 'schema.sql' script for database initialization.
 * INTELLIGENT: Detects the database dialect (MySQL, H2, Postgres) from the driver class
 * and adapts the SQL syntax accordingly.
 */
public class SqlScriptGenerator {

    private static final Logger log = LoggerFactory.getLogger(SqlScriptGenerator.class);

    // UPDATED: Accepts 'driverClassName' to determine the correct SQL syntax
    public void generateSchemaScript(FractalModule module, Path srcMainJava, Path srcMainResources, String driverClassName) {
        log.info("🗄️ [Data] Generating Schema Script for '{}'...", module.getServiceName());

        Map<String, String> localEntityMap = new HashMap<>();

        // 1. Scan for Local Entities FIRST
        try (Stream<Path> paths = Files.walk(srcMainJava)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                String content = readFile(path);
                if (content.contains("@Entity")) {
                    String className = extractClassName(content);
                    String tableName = extractTableName(content);
                    if (className != null && tableName != null) {
                        localEntityMap.put(className, tableName);
                    }
                }
            });
        } catch (IOException e) {
            log.error("Failed to scan for local entities", e);
        }

        // --- SAFETY CHECK: Skip if no entities found ---
        // This prevents "H2 Syntax Error" or "MySQL Error" when running empty scripts.
        // This fixes the error in your Leader's POC.
        if (localEntityMap.isEmpty()) {
            log.warn("   ⚠️ No @Entity classes found in {}. Skipping schema.sql generation.", module.getServiceName());
            writeSafeEmptyScript(srcMainResources);
            return;
        }

        StringBuilder tableSql = new StringBuilder();
        StringBuilder constraintSql = new StringBuilder();

        // --- INTELLIGENT DIALECT DETECTION ---
        DbDialect dialect = detectDialect(driverClassName);
        log.info("   🔍 Detected Database Dialect: {}", dialect.name());

        // Disable Foreign Key Checks using the correct syntax for the DB
        tableSql.append(dialect.disableFkChecks()).append("\n\n");

        tableSql.append("-- Auto-Generated Schema by FractalX\n");
        tableSql.append("-- Service: ").append(module.getServiceName()).append("\n");
        tableSql.append("-- Database: ").append(dialect.name()).append("\n\n");

        // 2. Generate Tables
        try (Stream<Path> paths = Files.walk(srcMainJava)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        String content = readFile(path);
                        if (content.contains("@Entity")) {
                            generateTableSQL(content, localEntityMap, tableSql, constraintSql);
                        }
                    });

            if (constraintSql.length() > 0) {
                tableSql.append("\n-- -----------------------------------------------------\n");
                tableSql.append("-- Foreign Key Constraints (Only for Local Relationships)\n");
                tableSql.append("-- -----------------------------------------------------\n");
                tableSql.append(constraintSql);
            }

            // Re-enable Foreign Key Checks
            tableSql.append("\n\n").append(dialect.enableFkChecks());

            Path schemaPath = srcMainResources.resolve("schema.sql");
            Files.writeString(schemaPath, tableSql.toString());
            log.info("   ✓ Created schema.sql");

        } catch (IOException e) {
            log.error("Failed to generate schema script", e);
        }
    }

    private void writeSafeEmptyScript(Path srcMainResources) {
        try {
            Path schemaPath = srcMainResources.resolve("schema.sql");
            // Writing a comment ensures the file exists but does nothing
            Files.writeString(schemaPath, "-- No entities found. Schema generation skipped to prevent errors.");
        } catch (IOException e) {
            log.error("Could not write empty schema file", e);
        }
    }

    // --- Helper: Database Dialect Logic ---

    private enum DbDialect {
        MYSQL("SET FOREIGN_KEY_CHECKS = 0;", "SET FOREIGN_KEY_CHECKS = 1;"),
        H2("SET REFERENTIAL_INTEGRITY FALSE;", "SET REFERENTIAL_INTEGRITY TRUE;"),
        POSTGRES("SET session_replication_role = 'replica';", "SET session_replication_role = 'origin';");

        private final String disableCmd;
        private final String enableCmd;

        DbDialect(String disableCmd, String enableCmd) {
            this.disableCmd = disableCmd;
            this.enableCmd = enableCmd;
        }

        public String disableFkChecks() { return disableCmd; }
        public String enableFkChecks() { return enableCmd; }
    }

    private DbDialect detectDialect(String driverClassName) {
        if (driverClassName == null) return DbDialect.H2; // Default fallback to H2
        if (driverClassName.contains("mysql")) return DbDialect.MYSQL;
        if (driverClassName.contains("h2")) return DbDialect.H2;
        if (driverClassName.contains("postgresql")) return DbDialect.POSTGRES;
        return DbDialect.H2; // Fallback for unknown drivers
    }

    private void generateTableSQL(String javaContent, Map<String, String> localEntityMap, StringBuilder mainBuilder, StringBuilder constraintBuilder) {
        String tableName = extractTableName(javaContent);
        if (tableName == null) return;

        StringBuilder tempSb = new StringBuilder();

        tempSb.append("DROP TABLE IF EXISTS ").append(tableName).append(";\n");
        tempSb.append("CREATE TABLE ").append(tableName).append(" (\n");

        String[] lines = javaContent.split("\n");
        boolean isFirstField = true;
        int columnCount = 0;

        List<String> currentAnnotations = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.isEmpty() || line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) continue;

            if (line.startsWith("@")) {
                currentAnnotations.add(line);
                continue;
            }

            if (line.contains("class ") || line.contains("interface ")) {
                currentAnnotations.clear();
                continue;
            }

            if (!line.endsWith(";")) {
                if (line.contains("{")) currentAnnotations.clear();
                continue;
            }

            boolean hasModifier = line.startsWith("private ") || line.startsWith("protected ") || line.startsWith("public ");
            if (!hasModifier) {
                currentAnnotations.clear();
                continue;
            }

            if (line.contains(" static ") || line.contains(" transient ")) {
                currentAnnotations.clear();
                continue;
            }

            if (line.contains("(") && !line.contains("=")) {
                currentAnnotations.clear();
                continue;
            }

            if (line.contains("=")) {
                line = line.substring(0, line.indexOf("=")).trim();
            } else {
                line = line.replace(";", "").trim();
            }

            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                currentAnnotations.clear();
                continue;
            }

            String type = parts[parts.length - 2];
            String name = parts[parts.length - 1];

            if (name.contains(".")) {
                currentAnnotations.clear();
                continue;
            }

            boolean isId = currentAnnotations.stream().anyMatch(a -> a.contains("@Id"));
            boolean isOneToMany = currentAnnotations.stream().anyMatch(a -> a.contains("@OneToMany"));

            if (isOneToMany) {
                currentAnnotations.clear();
                continue;
            }

            boolean isRelation = currentAnnotations.stream().anyMatch(a ->
                    a.contains("@ManyToOne") || a.contains("@OneToOne")
            );

            String sqlType = mapToSqlType(type, isRelation);

            if (isRelation) {
                if (!name.toLowerCase().endsWith("id")) {
                    name = name + "_id";
                }

                if (localEntityMap.containsKey(type)) {
                    String targetTableName = localEntityMap.get(type);
                    String constraint = String.format("ALTER TABLE %s ADD CONSTRAINT fk_%s_%s FOREIGN KEY (%s) REFERENCES %s(id);\n",
                            tableName, tableName, name, camelToSnake(name), targetTableName);
                    constraintBuilder.append(constraint);
                }
            }

            if (!isFirstField) tempSb.append(",\n");
            tempSb.append("    ").append(camelToSnake(name)).append(" ").append(sqlType);

            if (isId) tempSb.append(" PRIMARY KEY");

            isFirstField = false;
            columnCount++;
            currentAnnotations.clear();
        }

        tempSb.append("\n);");

        if (columnCount > 0) {
            mainBuilder.append(tempSb).append("\n\n");
        }
    }

    private String mapToSqlType(String javaType, boolean isRelation) {
        if (isRelation) return "VARCHAR(255)";
        return switch (javaType) {
            case "Long", "long", "Integer", "int" -> "BIGINT";
            case "String" -> "VARCHAR(255)";
            case "Double", "double" -> "DOUBLE";
            case "BigDecimal" -> "DECIMAL(19,2)";
            case "Boolean", "boolean" -> "BOOLEAN";
            case "LocalDate", "Date" -> "DATE";
            case "LocalDateTime" -> "TIMESTAMP";
            default -> "VARCHAR(255)";
        };
    }

    private String extractClassName(String content) {
        if (content.contains("class ")) {
            int start = content.indexOf("class ") + 6;
            int end = content.indexOf(" ", start);
            if (end == -1) end = content.indexOf("{", start);
            return content.substring(start, end).trim();
        }
        return null;
    }

    private String extractTableName(String content) {
        if (content.contains("@Table(name = \"")) {
            int start = content.indexOf("@Table(name = \"") + 15;
            int end = content.indexOf("\"", start);
            return content.substring(start, end);
        }
        if (content.contains("class ")) {
            int start = content.indexOf("class ") + 6;
            int end = content.indexOf(" ", start);
            if (end == -1) end = content.indexOf("{", start);
            String className = content.substring(start, end).trim();
            return camelToSnake(className);
        }
        return null;
    }

    private String camelToSnake(String str) {
        return str.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    private String readFile(Path path) {
        try { return Files.readString(path); } catch (IOException e) { return ""; }
    }
}