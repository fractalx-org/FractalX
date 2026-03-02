package org.fractalx.core.model;

import java.util.List;

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

    /**
     * The argument expressions from the call-site in the saga method body.
     * Usually these are parameter names from the parent saga method
     * (e.g., {@code ["productId", "quantity"]} for {@code reserveStock(productId, quantity)}).
     * Used by the generator to wire actual arguments instead of TODO stubs.
     */
    private final List<String> callArguments;

    public SagaStep(String beanType,
                    String targetServiceName,
                    String methodName,
                    String compensationMethodName,
                    List<String> callArguments) {
        this.beanType               = beanType;
        this.targetServiceName      = targetServiceName;
        this.methodName             = methodName;
        this.compensationMethodName = compensationMethodName;
        this.callArguments          = List.copyOf(callArguments);
    }

    public String getBeanType()               { return beanType; }
    public String getTargetServiceName()      { return targetServiceName; }
    public String getMethodName()             { return methodName; }
    public String getCompensationMethodName() { return compensationMethodName; }
    public List<String> getCallArguments()    { return callArguments; }

    public boolean hasCompensation() {
        return compensationMethodName != null && !compensationMethodName.isBlank();
    }

    @Override
    public String toString() {
        return "SagaStep{" + targetServiceName + "." + methodName
                + (hasCompensation() ? " ↩ " + compensationMethodName : "") + "}";
    }
}
