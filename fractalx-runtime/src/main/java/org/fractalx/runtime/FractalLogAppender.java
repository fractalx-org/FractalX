package org.fractalx.runtime;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.MDC;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared Appender included in fractalx-runtime.
 * Automatically ships logs to the Logger Service.
 */
public class FractalLogAppender extends AppenderBase<ILoggingEvent> {

    private final RestTemplate restTemplate = new RestTemplate();
    private String serverUrl;
    private String serviceName;

    public FractalLogAppender(String serverUrl, String serviceName) {
        this.serverUrl = serverUrl;
        this.serviceName = serviceName;
    }

    @Override
    protected void append(ILoggingEvent event) {
        try {
            // Build JSON payload
            Map<String, String> payload = new HashMap<>();
            payload.put("service", serviceName);
            payload.put("level", event.getLevel().toString());
            payload.put("message", event.getFormattedMessage());
            payload.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());

            // Correlation ID — prefer explicit header value, fall back to Micrometer traceId
            String correlationId = MDC.get("correlationId");
            if (correlationId == null) correlationId = MDC.get("traceId");
            if (correlationId != null) payload.put("correlationId", correlationId);

            // Span context from Micrometer Tracing
            String spanId = MDC.get("spanId");
            if (spanId != null) payload.put("spanId", spanId);

            String parentSpanId = MDC.get("parentSpanId");
            if (parentSpanId != null) payload.put("parentSpanId", parentSpanId);

            // Send async (fire and forget)
            restTemplate.postForLocation(serverUrl, payload);
        } catch (Exception e) {
            // Fail silently to avoid infinite logging loops
        }
    }
}