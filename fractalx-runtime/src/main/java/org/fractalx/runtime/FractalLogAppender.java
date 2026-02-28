package org.fractalx.runtime;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.MDC;
import org.springframework.web.client.RestTemplate;

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

            // Capture Correlation ID for cross-service log correlation
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                payload.put("correlationId", correlationId);
            }

            // Send async (fire and forget)
            restTemplate.postForLocation(serverUrl, payload);
        } catch (Exception e) {
            // Fail silently to avoid infinite logging loops
        }
    }
}