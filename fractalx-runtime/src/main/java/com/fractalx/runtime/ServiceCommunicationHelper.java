package com.fractalx.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Helper for inter-service communication in FractalX
 */
@Component
public class ServiceCommunicationHelper {

    private static final Logger log = LoggerFactory.getLogger(ServiceCommunicationHelper.class);
    private final FractalServiceRegistry registry;
    private final RestTemplate restTemplate;

    public ServiceCommunicationHelper(FractalServiceRegistry registry) {
        this.registry = registry;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Get the base URL for a service
     */
    public String getServiceUrl(String serviceName) {
        FractalServiceRegistry.ServiceEndpoint endpoint = registry.getService(serviceName);
        if (endpoint == null) {
            throw new IllegalStateException("Service not found: " + serviceName);
        }
        return endpoint.getUrl();
    }

    /**
     * Check if a service is available
     */
    public boolean isServiceAvailable(String serviceName) {
        return registry.isServiceRegistered(serviceName);
    }
}