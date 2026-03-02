package org.fractalx.core.model;

/**
 * Represents a single method parameter — its Java type (simple name) and variable name.
 *
 * <p>Used by {@link SagaDefinition} to record the parent saga method's signature and by
 * {@link SagaStep} to record each step's call-site argument names, so the
 * {@code SagaOrchestratorGenerator} can emit properly typed client calls.
 */
public final class MethodParam {

    private final String type;
    private final String name;

    public MethodParam(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getType() { return type; }
    public String getName() { return name; }

    @Override
    public String toString() {
        return type + " " + name;
    }
}
