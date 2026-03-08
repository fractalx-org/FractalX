package org.fractalx.core.datamanagement;

/**
 * Centralised naming utility for generated reference-validation method names.
 *
 * <p>Both {@link RelationshipDecoupler} (call-site injection into service classes) and
 * {@link ReferenceValidatorGenerator} (ReferenceValidator bean code generation) must
 * produce <em>identical</em> method names so that injected call sites resolve at compile
 * time.  Any rename here automatically updates both sides.
 */
class ValidationNaming {

    private ValidationNaming() {}

    /**
     * Validate method name for a singular remote ID field.
     * <p>Example: type {@code "Payment"} → {@code "validatePaymentExists"}
     */
    static String singleValidateMethod(String type) {
        return "validate" + cap(type) + "Exists";
    }

    /**
     * Validate method name for a collection remote ID field (produced by @ManyToMany decoupling).
     * <p>Example: type {@code "Course"} → {@code "validateAllCourseExist"}
     */
    static String collectionValidateMethod(String type) {
        return "validateAll" + cap(type) + "Exist";
    }

    // -------------------------------------------------------------------------

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
