package com.fractalx.runtime;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Propagates trace context across service boundaries
 */
@Component
public class TraceContextPropagator {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String MODULE_KEY = "module";

    public String startTrace(String moduleName) {
        String traceId = UUID.randomUUID().toString();
        MDC.put(TRACE_ID_KEY, traceId);
        MDC.put(MODULE_KEY, moduleName);
        return traceId;
    }

    public void continueTrace(String traceId, String moduleName) {
        MDC.put(TRACE_ID_KEY, traceId);
        MDC.put(MODULE_KEY, moduleName);
    }

    public String getCurrentTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public void clearTrace() {
        MDC.clear();
    }
}
