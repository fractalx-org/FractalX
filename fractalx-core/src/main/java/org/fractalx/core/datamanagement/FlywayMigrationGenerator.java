package org.fractalx.core.datamanagement;

import org.fractalx.core.model.FractalModule;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Generates a Flyway V1 migration scaffold for each microservice.
 *
 * <p>Scans the copied source for {@code @Entity} classes and generates a
 * {@code V1__init.sql} under {@code src/main/resources/db/migration/} that
 * contains {@code CREATE TABLE} stubs derived from the entity's fields.
 * Developers fill in constraints, indexes, and exact column types as needed.
 *
 * <p>The generated service's {@code application.yml} is also updated to point
 * Flyway at the correct migration location.
 */
public class FlywayMigrationGenerator {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationGenerator.class);

    private final JavaParser javaParser = new JavaParser();

    public void generateMigration(FractalModule module, Path serviceRoot) throws IOException {
        Path migrationDir = serviceRoot.resolve("src/main/resources/db/migration");
        Files.createDirectories(migrationDir);

        Path migrationFile = migrationDir.resolve("V1__init.sql");
        if (Files.exists(migrationFile)) {
            log.debug("Migration file already exists for {} – skipping", module.getServiceName());
            return;
        }

        List<EntityInfo> entities = scanEntities(serviceRoot.resolve("src/main/java"));
        Files.writeString(migrationFile, buildMigrationScript(module, entities));

        log.info("Generated Flyway migration: V1__init.sql for {}", module.getServiceName());
    }

    // -------------------------------------------------------------------------
    // Entity scanning
    // -------------------------------------------------------------------------

    private List<EntityInfo> scanEntities(Path srcMainJava) {
        List<EntityInfo> entities = new ArrayList<>();
        if (!Files.exists(srcMainJava)) return entities;

        try (Stream<Path> paths = Files.walk(srcMainJava)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    CompilationUnit cu = javaParser.parse(path).getResult().orElse(null);
                    if (cu == null) return;

                    for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                        if (hasAnnotation(cls, "Entity")) {
                            entities.add(extractEntityInfo(cls));
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (IOException e) {
            log.warn("Could not scan entities: {}", e.getMessage());
        }
        return entities;
    }

    private EntityInfo extractEntityInfo(ClassOrInterfaceDeclaration cls) {
        EntityInfo info = new EntityInfo();
        info.className = cls.getNameAsString();
        info.tableName = toSnakeCase(cls.getNameAsString());

        for (FieldDeclaration field : cls.getFields()) {
            if (hasAnnotation(field, "Id")) {
                info.fields.add(new ColumnInfo(
                        field.getVariable(0).getNameAsString(),
                        toSqlType(field.getElementType().asString()),
                        true
                ));
            } else if (!hasAnnotation(field, "Transient")
                    && !hasAnnotation(field, "OneToMany")
                    && !hasAnnotation(field, "ManyToMany")) {
                info.fields.add(new ColumnInfo(
                        toSnakeCase(field.getVariable(0).getNameAsString()),
                        toSqlType(field.getElementType().asString()),
                        false
                ));
            }
        }
        return info;
    }

    // -------------------------------------------------------------------------
    // SQL generation
    // -------------------------------------------------------------------------

    private String buildMigrationScript(FractalModule module, List<EntityInfo> entities) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- ============================================================\n");
        sb.append("-- FractalX Generated Migration — ").append(module.getServiceName()).append("\n");
        sb.append("-- V1: Initial schema\n");
        sb.append("--\n");
        sb.append("-- IMPORTANT: Review and adjust column types, constraints, and\n");
        sb.append("-- indexes before running in production. This is a scaffold only.\n");
        sb.append("-- Set spring.jpa.hibernate.ddl-auto=validate for production.\n");
        sb.append("-- ============================================================\n\n");

        if (entities.isEmpty()) {
            sb.append("-- No @Entity classes detected in this service.\n");
            sb.append("-- Add your CREATE TABLE statements here.\n");
        }

        for (EntityInfo entity : entities) {
            sb.append("-- Table: ").append(entity.tableName).append(" (from ").append(entity.className).append(")\n");
            sb.append("CREATE TABLE IF NOT EXISTS ").append(entity.tableName).append(" (\n");

            List<String> columnDefs = new ArrayList<>();
            for (ColumnInfo col : entity.fields) {
                String def = "    " + col.columnName + " " + col.sqlType;
                if (col.isPrimaryKey) {
                    def += " PRIMARY KEY";
                }
                columnDefs.add(def);
            }

            if (columnDefs.isEmpty()) {
                columnDefs.add("    -- TODO: Add column definitions");
            }

            sb.append(String.join(",\n", columnDefs));
            sb.append("\n);\n\n");
        }

        // Outbox table — always generated for saga/event support
        sb.append("-- Transactional outbox table (FractalX event publishing)\n");
        sb.append("CREATE TABLE IF NOT EXISTS fractalx_outbox (\n");
        sb.append("    id          BIGINT AUTO_INCREMENT PRIMARY KEY,\n");
        sb.append("    event_type  VARCHAR(255) NOT NULL,\n");
        sb.append("    aggregate_id VARCHAR(255) NOT NULL,\n");
        sb.append("    payload     TEXT,\n");
        sb.append("    published   BOOLEAN NOT NULL DEFAULT FALSE,\n");
        sb.append("    retry_count INT NOT NULL DEFAULT 0,\n");
        sb.append("    created_at  TIMESTAMP NOT NULL,\n");
        sb.append("    published_at TIMESTAMP\n");
        sb.append(");\n\n");

        sb.append("CREATE INDEX IF NOT EXISTS idx_outbox_unpublished ON fractalx_outbox (published, created_at);\n");

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private boolean hasAnnotation(com.github.javaparser.ast.nodeTypes.NodeWithAnnotations<?> node, String name) {
        return node.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals(name));
    }

    private String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([A-Z])", "_$1").toLowerCase().replaceFirst("^_", "");
    }

    private String toSqlType(String javaType) {
        return switch (javaType) {
            case "String" -> "VARCHAR(255)";
            case "Long", "long" -> "BIGINT";
            case "Integer", "int" -> "INT";
            case "Double", "double", "Float", "float" -> "DECIMAL(19,4)";
            case "Boolean", "boolean" -> "BOOLEAN";
            case "LocalDate" -> "DATE";
            case "LocalDateTime", "ZonedDateTime" -> "TIMESTAMP";
            case "UUID" -> "VARCHAR(36)";
            case "BigDecimal" -> "DECIMAL(19,4)";
            default -> "VARCHAR(255) -- TODO: verify type for " + javaType;
        };
    }

    // -------------------------------------------------------------------------
    // Inner data holders
    // -------------------------------------------------------------------------

    private static class EntityInfo {
        String className;
        String tableName;
        final List<ColumnInfo> fields = new ArrayList<>();
    }

    private static class ColumnInfo {
        final String columnName;
        final String sqlType;
        final boolean isPrimaryKey;

        ColumnInfo(String columnName, String sqlType, boolean isPrimaryKey) {
            this.columnName = columnName;
            this.sqlType = sqlType;
            this.isPrimaryKey = isPrimaryKey;
        }
    }
}
