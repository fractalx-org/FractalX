package com.fractalx.runtime;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Propagates correlation context across service boundaries via SLF4J MDC.
 * The correlation ID uniquely identifies a logical request chain across all services.
 */
@Component
public class TraceContextPropagator {

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String MODULE_KEY = "module";

    public String startTrace(String moduleName) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID_KEY, correlationId);
        MDC.put(MODULE_KEY, moduleName);
        return correlationId;
    }

    public void continueTrace(String correlationId, String moduleName) {
        MDC.put(CORRELATION_ID_KEY, correlationId);
        MDC.put(MODULE_KEY, moduleName);
    }

    public String getCurrentCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    /** @deprecated Use {@link #getCurrentCorrelationId()} */
    @Deprecated
    public String getCurrentTraceId() {
        return getCurrentCorrelationId();
    }

    public void clearTrace() {
        MDC.clear();
    }
}
