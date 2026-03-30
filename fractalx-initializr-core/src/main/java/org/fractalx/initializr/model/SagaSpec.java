package org.fractalx.initializr.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a distributed saga spanning multiple services.
 *
 * <pre>
 * sagas:
 *   - id: place-order-saga
 *     owner: order-service
 *     steps:
 *       - service: inventory-service
 *         method: reserveStock
 *       - service: payment-service
 *         method: processPayment
 *     compensationMethod: cancelOrder
 *     timeoutMs: 60000
 * </pre>
 */
public class SagaSpec {

    private String            id;
    private String            owner;
    private String            description        = "";
    private List<SagaStepSpec> steps             = new ArrayList<>();
    private String            compensationMethod = "";
    private long              timeoutMs          = 30000;

    // ── Getters / setters ─────────────────────────────────────────────────────

    public String getId()                        { return id; }
    public void   setId(String v)                { this.id = v; }

    public String getOwner()                     { return owner; }
    public void   setOwner(String v)             { this.owner = v; }

    public String getDescription()               { return description; }
    public void   setDescription(String v)       { this.description = v; }

    public List<SagaStepSpec> getSteps()         { return steps; }
    public void               setSteps(List<SagaStepSpec> v) { this.steps = v; }

    public String getCompensationMethod()        { return compensationMethod; }
    public void   setCompensationMethod(String v){ this.compensationMethod = v; }

    public long   getTimeoutMs()                 { return timeoutMs; }
    public void   setTimeoutMs(long v)           { this.timeoutMs = v; }
}
