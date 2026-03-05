package org.fractalx.runtime;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
                // Also add to response header for visibility
                httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
                chain.doFilter(request, response);
            } finally {
                MDC.remove(CORRELATION_ID_KEY);
            }
        } else {
            chain.doFilter(request, response);
        }
    }
}
