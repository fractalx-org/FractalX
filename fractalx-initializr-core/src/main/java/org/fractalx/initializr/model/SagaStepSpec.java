package org.fractalx.initializr.model;

/** A single step in a {@link SagaSpec}. */
public class SagaStepSpec {

    private String service;
    private String method;

    public String getService()           { return service; }
    public void   setService(String v)   { this.service = v; }

    public String getMethod()            { return method; }
    public void   setMethod(String v)    { this.method = v; }
}
