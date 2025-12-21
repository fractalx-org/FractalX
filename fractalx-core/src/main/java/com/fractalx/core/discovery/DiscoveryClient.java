package com.fractalx.core.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client for service discovery with load balancing capabilities
 */
public class DiscoveryClient {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryClient.class);

    private final DiscoveryRegistry registry;
    private final StaticDiscoveryConfig staticConfig;

    // Simple round-robin counters
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public DiscoveryClient(DiscoveryRegistry registry, StaticDiscoveryConfig staticConfig) {
        this.registry = registry;
        this.staticConfig = staticConfig;
    }

    /**
     * Get service URL with load balancing
     */
    public String getServiceUrl(String serviceName) {
        ServiceInstance instance = getHealthyInstance(serviceName);
        if (instance == null) {
            throw new IllegalStateException("No healthy instances available for service: " + serviceName);
        }
        return instance.getUrl();
    }

    /**
     * Get healthy instance with load balancing
     */
    public ServiceInstance getHealthyInstance(String serviceName) {
        // First try dynamic registry
        List<ServiceInstance> instances = registry.getInstances(serviceName);

        // Fall back to static config if dynamic registry is empty
        if (instances.isEmpty()) {
            instances = staticConfig.getStaticInstances(serviceName);
            if (!instances.isEmpty()) {
                log.debug("Using static configuration for service: {}", serviceName);
            }
        }

        if (instances.isEmpty()) {
            return null;
        }

        // Simple round-robin load balancing
        AtomicInteger counter = counters.computeIfAbsent(
                serviceName, k -> new AtomicInteger(0));
        int index = Math.abs(counter.getAndIncrement() % instances.size());

        return instances.get(index);
    }

    /**
     * Check if service is available
     */
    public boolean isServiceAvailable(String serviceName) {
        return !registry.getInstances(serviceName).isEmpty() ||
                !staticConfig.getStaticInstances(serviceName).isEmpty();
    }

    /**
     * Get all available services
     */
    public List<String> getAllAvailableServices() {
        return List.copyOf(registry.getAllServices());
    }

    /**
     * Refresh static configuration
     */
    public void refreshStaticConfig() {
        staticConfig.loadConfiguration();
    }
}