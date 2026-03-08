package org.fractalx.runtime;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that captures X-Correlation-Id from incoming HTTP requests and populates the MDC.
 * If missing, generates a new one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter implements Filter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ID_KEY = "correlationId";

    /** Injected when micrometer-tracing is on the classpath (Spring Boot 3 default). Null-safe. */
    @Nullable
    @Autowired(required = false)
    private Tracer tracer;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            try {
                MDC.put(CORRELATION_ID_KEY, correlationId);
                // Echo back to caller so they can track the ID
                httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
                // Tag the active Micrometer/OTel span so Jaeger can index and search by correlationId
                if (tracer != null) {
                    io.micrometer.tracing.Span span = tracer.currentSpan();
                    if (span != null) {
                        span.tag("correlationId", correlationId);
                    }
                }
                chain.doFilter(request, response);
            } finally {
                MDC.remove(CORRELATION_ID_KEY);
            }
        } else {
            chain.doFilter(request, response);
        }
    }
}
