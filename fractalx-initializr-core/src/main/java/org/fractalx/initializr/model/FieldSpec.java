package org.fractalx.initializr.model;

/**
 * A single field on an entity, e.g. {@code orderNumber: String}.
 */
public class FieldSpec {

    private String name;
    /** Java type as a string: String / Long / Integer / BigDecimal / Boolean /
     *  LocalDateTime / UUID / Double — or any custom enum name. */
    private String type = "String";

    // ── Derived helpers ────────────────────────────────────────────────────────

    /** Returns the import statement needed for this type, or empty string if none. */
    public String resolvedImport() {
        return switch (type) {
            case "BigDecimal"     -> "java.math.BigDecimal";
            case "LocalDateTime"  -> "java.time.LocalDateTime";
            case "LocalDate"      -> "java.time.LocalDate";
            case "UUID"           -> "java.util.UUID";
            default               -> "";
        };
    }

    /** True if this type requires an explicit import. */
    public boolean needsImport() {
        return !resolvedImport().isEmpty();
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public String getName()          { return name; }
    public void   setName(String v)  { this.name = v; }

    public String getType()          { return type; }
    public void   setType(String v)  { this.type = v; }
}
