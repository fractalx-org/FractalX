package com.fractalx.core.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;

import java.util.Map;

/**
 * Initializes and manages the discovery system
 */
public class DiscoveryInitializer {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryInitializer.class);

    private final DiscoveryRegistry registry;
    private final DiscoveryClient client;
    private final StaticDiscoveryConfig staticConfig;

    private boolean initialized = false;
    private String selfServiceName;
    private String selfHost;
    private int selfPort;

    public DiscoveryInitializer() {
        this.registry = new DiscoveryRegistry();
        this.staticConfig = new StaticDiscoveryConfig();
        this.client = new DiscoveryClient(registry, staticConfig);
    }

    public DiscoveryInitializer(long heartbeatInterval, long instanceTtl) {
        this.registry = new DiscoveryRegistry(heartbeatInterval, instanceTtl);
        this.staticConfig = new StaticDiscoveryConfig();
        this.client = new DiscoveryClient(registry, staticConfig);
    }

    /**
     * Initialize discovery with self-registration
     */
    public void initialize(String serviceName, String host, int port) {
        if (initialized) {
            log.warn("Discovery already initialized");
            return;
        }

        this.selfServiceName = serviceName;
        this.selfHost = host;
        this.selfPort = port;

        // Load static configuration
        staticConfig.loadConfiguration();

        // Self-register
        registerSelf();

        // Register static instances
        registerStaticInstances();

        initialized = true;
        log.info("Discovery initialized for service: {} at {}:{}",
                serviceName, host, port);
    }

    private void registerSelf() {
        ServiceInstance selfInstance = new ServiceInstance(selfServiceName, selfHost, selfPort);
        selfInstance.getMetadata().put("self", "true");
        registry.register(selfInstance);
    }

    private void registerStaticInstances() {
        for (String serviceName : staticConfig.getAllStaticServices()) {
            for (ServiceInstance instance : staticConfig.getStaticInstances(serviceName)) {
                // Don't re-register self
                if (!(instance.getServiceName().equals(selfServiceName) &&
                        instance.getHost().equals(selfHost) &&
                        instance.getPort() == selfPort)) {
                    registry.register(instance);
                }
            }
        }
    }

    /**
     * Send heartbeat to keep registration alive
     */
    public void sendHeartbeat() {
        String instanceId = generateInstanceId(selfServiceName, selfHost, selfPort);
        registry.heartbeat(instanceId);
        log.trace("Heartbeat sent for instance: {}", instanceId);
    }

    private String generateInstanceId(String serviceName, String host, int port) {
        return serviceName + "-" + host + "-" + port;
    }

    /**
     * Deregister self
     */
    public void deregisterSelf() {
        String instanceId = generateInstanceId(selfServiceName, selfHost, selfPort);
        registry.deregister(instanceId);
        log.info("Deregistered self: {} -> {}:{}",
                selfServiceName, selfHost, selfPort);
    }

    /**
     * Get discovery client
     */
    public DiscoveryClient getClient() {
        return client;
    }

    /**
     * Get discovery registry
     */
    public DiscoveryRegistry getRegistry() {
        return registry;
    }

    /**
     * Get static config
     */
    public StaticDiscoveryConfig getStaticConfig() {
        return staticConfig;
    }

    /**
     * Cleanup on shutdown
     */
    @PreDestroy
    public void cleanup() {
        deregisterSelf();
        registry.shutdown();
        log.info("Discovery system cleaned up");
    }

    /**
     * Health check
     */
    public boolean isHealthy() {
        return initialized &&
                registry.getInstances(selfServiceName).stream()
                        .anyMatch(instance -> instance.getStatus().equals("UP"));
    }
}