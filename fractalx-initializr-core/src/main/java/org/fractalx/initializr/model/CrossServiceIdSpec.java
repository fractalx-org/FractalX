package org.fractalx.initializr.model;

/**
 * Represents a cross-service ID reference on an entity field.
 * Instead of {@code @ManyToOne Customer customer}, the generator emits
 * {@code private String customerId;} — safe for decomposition from day one.
 *
 * <pre>
 * crossServiceIds:
 *   - fieldName: customerId
 *     service: customer-service
 * </pre>
 */
public class CrossServiceIdSpec {

    /** Field name in the entity, e.g. {@code customerId}. */
    private String fieldName;
    /** Owning service, e.g. {@code customer-service}. */
    private String service;

    public String getFieldName()         { return fieldName; }
    public void   setFieldName(String v) { this.fieldName = v; }

    public String getService()           { return service; }
    public void   setService(String v)   { this.service = v; }
}
