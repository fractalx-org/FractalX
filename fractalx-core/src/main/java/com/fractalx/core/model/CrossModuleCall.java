package com.fractalx.core.model;

import java.util.List;

/**
 * Represents a detected cross-module method call between two services.
 *
 * <p>Populated during source analysis when a decomposable module's bean is called
 * by another module. Used to drive NetScope server annotation and client interface
 * generation in the code generation pipeline.
 */
public final class CrossModuleCall {

    /** Simple class name of the target Spring bean (e.g., {@code "PaymentService"}). */
    private final String targetBeanType;

    /** Service name of the module that owns the bean (e.g., {@code "payment-service"}). */
    private final String targetServiceName;

    /** Name of the method being invoked (e.g., {@code "processPayment"}). */
    private final String methodName;

    /** Return type as a string (e.g., {@code "boolean"}, {@code "void"}). */
    private final String returnType;

    /**
     * Method parameter declarations in {@code "Type name"} format
     * (e.g., {@code ["String customerId", "Double amount"]}).
     */
    private final List<String> parameters;

    public CrossModuleCall(String targetBeanType,
                           String targetServiceName,
                           String methodName,
                           String returnType,
                           List<String> parameters) {
        this.targetBeanType    = targetBeanType;
        this.targetServiceName = targetServiceName;
        this.methodName        = methodName;
        this.returnType        = returnType;
        this.parameters        = List.copyOf(parameters);
    }

    public String getTargetBeanType()    { return targetBeanType; }
    public String getTargetServiceName() { return targetServiceName; }
    public String getMethodName()        { return methodName; }
    public String getReturnType()        { return returnType; }
    public List<String> getParameters()  { return parameters; }

    @Override
    public String toString() {
        return "CrossModuleCall{" + targetServiceName + "." + targetBeanType
                + "#" + methodName + "(" + String.join(", ", parameters) + "): " + returnType + "}";
    }
}
