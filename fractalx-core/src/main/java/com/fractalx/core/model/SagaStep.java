package com.fractalx.core.model;

/**
 * Represents one step in a distributed saga — a single cross-module method call
 * with an optional compensation counterpart.
 *
 * <p>Steps are detected by {@code SagaAnalyzer} by scanning method call expressions
 * inside a {@code @DistributedSaga}-annotated method body that target a field whose
 * type is a cross-module dependency.
 */
public final class SagaStep {

    /** Simple type name of the bean called in this step (e.g., {@code "PaymentService"}). */
    private final String beanType;

    /** Derived service name (e.g., {@code "payment-service"}). */
    private final String targetServiceName;

    /** Name of the forward method being called (e.g., {@code "processPayment"}). */
    private final String methodName;

    /**
     * Name of the compensation method on the same bean, if discoverable.
     * Empty string if no matching compensation method was found.
     */
    private final String compensationMethodName;

    public SagaStep(String beanType,
                    String targetServiceName,
                    String methodName,
                    String compensationMethodName) {
        this.beanType              = beanType;
        this.targetServiceName     = targetServiceName;
        this.methodName            = methodName;
        this.compensationMethodName = compensationMethodName;
    }

    public String getBeanType()               { return beanType; }
    public String getTargetServiceName()      { return targetServiceName; }
    public String getMethodName()             { return methodName; }
    public String getCompensationMethodName() { return compensationMethodName; }

    public boolean hasCompensation() {
        return compensationMethodName != null && !compensationMethodName.isBlank();
    }

    @Override
    public String toString() {
        return "SagaStep{" + targetServiceName + "." + methodName
                + (hasCompensation() ? " ↩ " + compensationMethodName : "") + "}";
    }
}
