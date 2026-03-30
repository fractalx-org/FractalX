package org.fractalx.initializr.generator;

import org.fractalx.initializr.InitializerContext;
import org.fractalx.initializr.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates one {@code @Entity} (or {@code @Document} for MongoDB) class per declared entity,
 * plus any required enum types.
 *
 * <p>Cross-service ID references are emitted as plain {@code String} ID fields — never as
 * {@code @ManyToOne} — so decomposition works without {@code RelationshipDecoupler} intervention.
 */
public class EntityGenerator implements InitializerFileGenerator {

    @Override
    public String label() { return "Entities"; }

    @Override
    public void generate(InitializerContext ctx) throws IOException {
        ProjectSpec spec = ctx.spec();

        for (ServiceSpec svc : spec.getServices()) {
            for (EntitySpec entity : svc.getEntities()) {
                generateEntity(ctx, spec, svc, entity);
                generateEnumsIfNeeded(ctx, spec, svc, entity);
            }
        }
    }

    // ── Entity class ──────────────────────────────────────────────────────────

    private void generateEntity(InitializerContext ctx, ProjectSpec spec,
                                 ServiceSpec svc, EntitySpec entity) throws IOException {
        boolean isMongo = "mongodb".equalsIgnoreCase(svc.getDatabase());
        String  svcPkg  = spec.resolvedPackage() + "." + svc.javaPackage();

        Set<String> imports = new LinkedHashSet<>();
        if (isMongo) {
            imports.add("org.springframework.data.annotation.Id");
            imports.add("org.springframework.data.mongodb.core.mapping.Document");
        } else {
            imports.add("jakarta.persistence.Entity");
            imports.add("jakarta.persistence.GeneratedValue");
            imports.add("jakarta.persistence.GenerationType");
            imports.add("jakarta.persistence.Id");
            imports.add("jakarta.persistence.Table");
        }

        // Collect field type imports
        for (FieldSpec f : entity.getFields()) {
            String imp = f.resolvedImport();
            if (!imp.isEmpty()) imports.add(imp);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(svcPkg).append(";\n\n");
        imports.forEach(i -> sb.append("import ").append(i).append(";\n"));
        sb.append("\n");

        if (isMongo) {
            sb.append("@Document(collection = \"").append(entity.getName().toLowerCase()).append("s\")\n");
        } else {
            sb.append("@Entity\n");
            sb.append("@Table(name = \"").append(toSnakeCase(entity.getName())).append("s\")\n");
        }
        sb.append("public class ").append(entity.getName()).append(" {\n\n");

        // ID field
        if (isMongo) {
            sb.append("    @Id\n");
            sb.append("    private String id;\n\n");
        } else {
            sb.append("    @Id\n");
            sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
            sb.append("    private Long id;\n\n");
        }

        // Declared fields
        for (FieldSpec f : entity.getFields()) {
            sb.append("    private ").append(f.getType()).append(" ").append(f.getName()).append(";\n");
        }

        // Cross-service ID fields (String instead of @ManyToOne)
        if (!entity.getCrossServiceIds().isEmpty()) {
            sb.append("\n    // Cross-service references — stored as IDs, not foreign keys\n");
            for (CrossServiceIdSpec xref : entity.getCrossServiceIds()) {
                sb.append("    // References ").append(xref.getService()).append("\n");
                sb.append("    private String ").append(xref.getFieldName()).append(";\n");
            }
        }

        // Getters + setters
        sb.append("\n");
        appendGetterSetter(sb, isMongo ? "String" : "Long", "id");
        for (FieldSpec f : entity.getFields()) {
            appendGetterSetter(sb, f.getType(), f.getName());
        }
        for (CrossServiceIdSpec xref : entity.getCrossServiceIds()) {
            appendGetterSetter(sb, "String", xref.getFieldName());
        }

        sb.append("}\n");

        Path file = ctx.serviceSourceDir(svc).resolve(entity.getName() + ".java");
        write(file, sb.toString());
    }

    // ── Enum generation ───────────────────────────────────────────────────────

    private void generateEnumsIfNeeded(InitializerContext ctx, ProjectSpec spec,
                                        ServiceSpec svc, EntitySpec entity) throws IOException {
        for (FieldSpec f : entity.getFields()) {
            // Heuristic: if the type ends in Status, Type, State, or Kind, treat as enum
            String t = f.getType();
            if (t.endsWith("Status") || t.endsWith("Type") || t.endsWith("State") || t.endsWith("Kind")) {
                generateEnum(ctx, spec, svc, t);
            }
        }
    }

    private void generateEnum(InitializerContext ctx, ProjectSpec spec,
                               ServiceSpec svc, String enumName) throws IOException {
        Path enumFile = ctx.serviceSourceDir(svc).resolve(enumName + ".java");
        if (enumFile.toFile().exists()) return; // already generated

        String svcPkg = spec.resolvedPackage() + "." + svc.javaPackage();
        String[] values = defaultValuesFor(enumName);

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(svcPkg).append(";\n\n");
        sb.append("public enum ").append(enumName).append(" {\n");
        for (int i = 0; i < values.length; i++) {
            sb.append("    ").append(values[i]);
            sb.append(i < values.length - 1 ? "," : "").append("\n");
        }
        sb.append("}\n");

        write(enumFile, sb.toString());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void appendGetterSetter(StringBuilder sb, String type, String name) {
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        sb.append("    public ").append(type).append(" get").append(cap)
          .append("() { return ").append(name).append("; }\n");
        sb.append("    public void set").append(cap)
          .append("(").append(type).append(" ").append(name)
          .append(") { this.").append(name).append(" = ").append(name).append("; }\n\n");
    }

    private String toSnakeCase(String s) {
        return s.replaceAll("([A-Z])", "_$1").toLowerCase().replaceFirst("^_", "");
    }

    private String[] defaultValuesFor(String enumName) {
        if (enumName.endsWith("Status")) return new String[]{"PENDING", "ACTIVE", "COMPLETED", "CANCELLED"};
        if (enumName.endsWith("Type"))   return new String[]{"STANDARD", "PREMIUM", "ENTERPRISE"};
        if (enumName.endsWith("State"))  return new String[]{"INITIAL", "PROCESSING", "DONE", "FAILED"};
        return new String[]{"DEFAULT"};
    }
}
