package org.fractalx.core.validation;

/**
 * Severity level of a decomposition validation issue.
 *
 * <p>ERROR-level issues block generation entirely — they represent configurations
 * that would definitely produce a broken microservice output (e.g., two services
 * sharing the same port, or a circular startup dependency).
 *
 * <p>WARNING-level issues allow generation to proceed but are surfaced prominently
 * on the console so developers can address them before shipping to production.
 */
public enum ValidationSeverity {

    /**
     * Blocks decomposition. The generated output would be broken or would fail
     * at runtime if generation were allowed to continue.
     */
    ERROR,

    /**
     * Generation continues, but the issue may cause incorrect behaviour or
     * hard-to-diagnose problems at runtime. Developers should review the
     * suggested fix before deploying.
     */
    WARNING
}
