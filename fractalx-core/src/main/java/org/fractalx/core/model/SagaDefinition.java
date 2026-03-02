package org.fractalx.core.model;

import java.util.List;
import java.util.ArrayList;

/**
 * Captures the full definition of a distributed saga detected during source analysis.
 *
 * <p>Produced by {@code SagaAnalyzer} for every method annotated with
 * {@code @DistributedSaga}. Consumed by {@code SagaOrchestratorGenerator} to
 * generate a dedicated orchestrator service with per-saga state management,
 * forward execution, and compensation logic.
 */
public final class SagaDefinition {

    /** Unique saga identifier from {@code @DistributedSaga#sagaId()}. */
    private final String sagaId;

    /** Name of the service module that owns the saga method. */
    private final String ownerServiceName;

    /** Simple name of the class containing the saga method. */
    private final String ownerClassName;

    /** Name of the {@code @DistributedSaga}-annotated method. */
    private final String methodName;

    /**
     * Ordered list of cross-service calls detected inside the saga method body.
     * The orchestrator calls them in this order and compensates in reverse.
     */
    private final List<SagaStep> steps;

    /** Overall compensation method name from {@code @DistributedSaga#compensationMethod()}. */
    private final String compensationMethod;

    /** Timeout in milliseconds from {@code @DistributedSaga#timeout()}. */
    private final long timeoutMs;

    /** Human-readable description from {@code @DistributedSaga#description()}. */
    private final String description;

    /**
     * The parameters of the {@code @DistributedSaga}-annotated method itself.
     * Used to generate a typed payload DTO that the orchestrator can deserialize
     * from the JSON body passed to {@code start(String payload)}.
     */
    private final List<MethodParam> sagaMethodParams;

    public SagaDefinition(String sagaId,
                          String ownerServiceName,
                          String ownerClassName,
                          String methodName,
                          List<SagaStep> steps,
                          String compensationMethod,
                          long timeoutMs,
                          String description,
                          List<MethodParam> sagaMethodParams) {
        this.sagaId             = sagaId;
        this.ownerServiceName   = ownerServiceName;
        this.ownerClassName     = ownerClassName;
        this.methodName         = methodName;
        this.steps              = List.copyOf(steps);
        this.compensationMethod = compensationMethod;
        this.timeoutMs          = timeoutMs;
        this.description        = description;
        this.sagaMethodParams   = List.copyOf(sagaMethodParams);
    }

    public String getSagaId()                    { return sagaId; }
    public String getOwnerServiceName()          { return ownerServiceName; }
    public String getOwnerClassName()            { return ownerClassName; }
    public String getMethodName()                { return methodName; }
    public List<SagaStep> getSteps()             { return steps; }
    public String getCompensationMethod()        { return compensationMethod; }
    public long getTimeoutMs()                   { return timeoutMs; }
    public String getDescription()               { return description; }
    public List<MethodParam> getSagaMethodParams(){ return sagaMethodParams; }

    /** Derives a PascalCase class name from the sagaId. Example: {@code "place-order-saga" → "PlaceOrderSaga"}. */
    public String toClassName() {
        StringBuilder sb = new StringBuilder();
        for (String part : sagaId.split("[-_]")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "SagaDefinition{sagaId='" + sagaId + "', owner=" + ownerServiceName
                + ", steps=" + steps.size() + "}";
    }
}
