package org.fractalx.initializr.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a JPA entity (or MongoDB document) within a service.
 */
public class EntitySpec {

    /** Pascal-case entity name, e.g. {@code Order}. */
    private String name;
    private List<FieldSpec>          fields         = new ArrayList<>();
    /** Cross-service ID references: field name → owning service name. */
    private List<CrossServiceIdSpec> crossServiceIds = new ArrayList<>();

    // ── Derived helpers ────────────────────────────────────────────────────────

    /** Returns the field name in camelCase, e.g. {@code Order} → {@code order}. */
    public String fieldName() {
        if (name == null || name.isEmpty()) return "";
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public String getName()                  { return name; }
    public void   setName(String v)          { this.name = v; }

    public List<FieldSpec>          getFields()          { return fields; }
    public void                     setFields(List<FieldSpec> v) { this.fields = v; }

    public List<CrossServiceIdSpec> getCrossServiceIds()  { return crossServiceIds; }
    public void                     setCrossServiceIds(List<CrossServiceIdSpec> v) { this.crossServiceIds = v; }
}
