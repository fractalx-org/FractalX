//package com.fractalx.core.discovery;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.client.RestTemplate;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.MediaType;
//import org.springframework.http.HttpEntity;
//
//import javax.annotation.PreDestroy;
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * Initializes and manages the discovery system
// */
//public class DiscoveryInitializer {
//    private static final Logger log = LoggerFactory.getLogger(DiscoveryInitializer.class);
//
//    private final DiscoveryRegistry registry;
//    private final DiscoveryClient client;
//    private final StaticDiscoveryConfig staticConfig;
//
//    private boolean initialized = false;
//    private String selfServiceName;
//    private String selfHost;
//    private int selfPort;
//    private boolean registered = false;
//    private String registryUrl = "http://localhost:8761";
//
//    // Heartbeat scheduler
//    private java.util.concurrent.ScheduledExecutorService heartbeatScheduler;
//
//    public DiscoveryInitializer() {
//        this.registry = new DiscoveryRegistry();
//        this.staticConfig = new StaticDiscoveryConfig();
//        this.client = new DiscoveryClient(registry, staticConfig);
//        this.heartbeatScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
//    }
//
//    public DiscoveryInitializer(long heartbeatInterval, long instanceTtl) {
//        this.registry = new DiscoveryRegistry(heartbeatInterval, instanceTtl);
//        this.staticConfig = new StaticDiscoveryConfig();
//        this.client = new DiscoveryClient(registry, staticConfig);
//        this.heartbeatScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
//    }
//
//    /**
//     * Set registry URL
//     */
//    public void setRegistryUrl(String registryUrl) {
//        this.registryUrl = registryUrl;
//    }
//
//    /**
//     * Initialize discovery with self-registration
//     */
//    public void initialize(String serviceName, String host, int port) {
//        if (initialized) {
//            log.warn("Discovery already initialized");
//            return;
//        }
//
//        this.selfServiceName = serviceName;
//        this.selfHost = host;
//        this.selfPort = port;
//
//        // Load static configuration
//        staticConfig.loadConfiguration();
//
//        // Self-register
//        registerSelf();
//
//        // Register static instances
//        registerStaticInstances();
//
//        // Start heartbeat scheduler
//        startHeartbeatScheduler();
//
//        initialized = true;
//        log.info("Discovery initialized for service: {} at {}:{}",
//                serviceName, host, port);
//    }
//
//    private void registerSelf() {
//        ServiceInstance selfInstance = new ServiceInstance(selfServiceName, selfHost, selfPort);
//        selfInstance.getMetadata().put("self", "true");
//        selfInstance.getMetadata().put("startTime", String.valueOf(System.currentTimeMillis()));
//        registry.register(selfInstance);
//        log.info("Self-registered: {} at {}:{}", selfServiceName, selfHost, selfPort);
//    }
//
//    private void registerStaticInstances() {
//        for (String serviceName : staticConfig.getAllStaticServices()) {
//            for (ServiceInstance instance : staticConfig.getStaticInstances(serviceName)) {
//                // Don't re-register self
//                if (!(instance.getServiceName().equals(selfServiceName) &&
//                        instance.getHost().equals(selfHost) &&
//                        instance.getPort() == selfPort)) {
//                    registry.register(instance);
//                    log.debug("Registered static instance: {} -> {}:{}",
//                            instance.getServiceName(), instance.getHost(), instance.getPort());
//                }
//            }
//        }
//    }
//
//    /**
//     * Send heartbeat to keep registration alive
//     */
//    public void sendHeartbeat() {
//        String instanceId = generateInstanceId(selfServiceName, selfHost, selfPort);
//        registry.heartbeat(instanceId);
//        log.trace("Heartbeat sent for instance: {}", instanceId);
//    }
//
//    /**
//     * Start automatic heartbeat scheduler
//     */
//    private void startHeartbeatScheduler() {
//        long heartbeatInterval = registry.getHeartbeatInterval();
//        heartbeatScheduler.scheduleAtFixedRate(() -> {
//            try {
//                sendHeartbeat();
//            } catch (Exception e) {
//                log.error("Error sending heartbeat", e);
//            }
//        }, heartbeatInterval, heartbeatInterval, java.util.concurrent.TimeUnit.MILLISECONDS);
//        log.info("Started heartbeat scheduler with interval: {}ms", heartbeatInterval);
//    }
//
//    private String generateInstanceId(String serviceName, String host, int port) {
//        return serviceName + "-" + host + "-" + port;
//    }
//
//    /**
//     * Deregister self
//     */
//    public void deregisterSelf() {
//        String instanceId = generateInstanceId(selfServiceName, selfHost, selfPort);
//        registry.deregister(instanceId);
//        log.info("Deregistered self: {} -> {}:{}",
//                selfServiceName, selfHost, selfPort);
//    }
//
//    /**
//     * Get discovery client
//     */
//    public DiscoveryClient getClient() {
//        return client;
//    }
//
//    /**
//     * Get discovery registry
//     */
//    public DiscoveryRegistry getRegistry() {
//        return registry;
//    }
//
//    /**
//     * Get static config
//     */
//    public StaticDiscoveryConfig getStaticConfig() {
//        return staticConfig;
//    }
//
//    /**
//     * Cleanup on shutdown
//     */
//    @PreDestroy
//    public void cleanup() {
//        deregisterSelf();
//        registry.shutdown();
//        heartbeatScheduler.shutdown();
//        try {
//            if (!heartbeatScheduler.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
//                heartbeatScheduler.shutdownNow();
//            }
//        } catch (InterruptedException e) {
//            heartbeatScheduler.shutdownNow();
//            Thread.currentThread().interrupt();
//        }
//        log.info("Discovery system cleaned up");
//    }
//
//    /**
//     * Health check
//     */
//    public boolean isHealthy() {
//        return initialized &&
//                registry.getInstances(selfServiceName).stream()
//                        .anyMatch(instance -> instance.getStatus().equals("UP"));
//    }
//
//    /**
//     * Get service info for current instance
//     */
//    public ServiceInstance getSelfInstance() {
//        String instanceId = generateInstanceId(selfServiceName, selfHost, selfPort);
//        return registry.getInstance(instanceId);
//    }
//
//    /**
//     * Register with central discovery service
//     */
//    public void registerWithDiscoveryService() {
//        try {
//            log.info("Attempting to register {} with discovery service at {}",
//                    selfServiceName, registryUrl);
//
//            // Create registration request with proper headers
//            Map<String, Object> payload = new HashMap<>();
//            payload.put("serviceName", selfServiceName);
//            payload.put("host", selfHost);
//            payload.put("port", selfPort);
//            payload.put("status", "UP");
//
//            // Make HTTP POST to discovery service
//            String fullRegistryUrl = registryUrl + "/api/discovery/register";
//            RestTemplate restTemplate = new RestTemplate();
//
//            // Create proper headers
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_JSON);
//
//            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
//
//            ResponseEntity<String> response = restTemplate.postForEntity(
//                    fullRegistryUrl,
//                    request,
//                    String.class
//            );
//
//            // Use the is2xxSuccessful() method which works in both Spring 5 and 6
//            if (response.getStatusCode().is2xxSuccessful()) {
//                log.info("Successfully registered {} with discovery service at {}",
//                        selfServiceName, registryUrl);
//                registered = true;
//            } else {
//                log.warn("Failed to register {}: HTTP {}",
//                        selfServiceName, response.getStatusCode().value());
//            }
//        } catch (Exception e) {
//            log.error("Error registering with discovery service: {}", e.getMessage());
//            log.debug("Registration error details:", e);
//        }
//    }
//
//    /**
//     * Check if registered with discovery service
//     */
//    public boolean isRegistered() {
//        return registered;
//    }
//}

package com.fractalx.core.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;

import javax.annotation.PreDestroy;
import java.util.HashMap;
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
    private boolean registered = false;
    private String registryUrl = "http://localhost:8761";

    // Heartbeat scheduler
    private java.util.concurrent.ScheduledExecutorService heartbeatScheduler;

    public DiscoveryInitializer() {
        this.registry = new DiscoveryRegistry();
        this.staticConfig = new StaticDiscoveryConfig();
        this.client = new DiscoveryClient(registry, staticConfig);
        this.heartbeatScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    }

    public DiscoveryInitializer(long heartbeatInterval, long instanceTtl) {
        this.registry = new DiscoveryRegistry(heartbeatInterval, instanceTtl);
        this.staticConfig = new StaticDiscoveryConfig();
        this.client = new DiscoveryClient(registry, staticConfig);
        this.heartbeatScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Set registry URL
     */
    public void setRegistryUrl(String registryUrl) {
        this.registryUrl = registryUrl;
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

        // Start heartbeat scheduler
        startHeartbeatScheduler();

        // Register with central discovery service
        registerWithDiscoveryService();

        initialized = true;
        log.info("Discovery initialized for service: {} at {}:{}",
                serviceName, host, port);
    }

    private void registerSelf() {
        ServiceInstance selfInstance = new ServiceInstance(selfServiceName, selfHost, selfPort);
        selfInstance.getMetadata().put("self", "true");
        selfInstance.getMetadata().put("startTime", String.valueOf(System.currentTimeMillis()));
        registry.register(selfInstance);
        log.info("Self-registered: {} at {}:{}", selfServiceName, selfHost, selfPort);
    }

    private void registerStaticInstances() {
        for (String serviceName : staticConfig.getAllStaticServices()) {
            for (ServiceInstance instance : staticConfig.getStaticInstances(serviceName)) {
                // Don't re-register self
                if (!(instance.getServiceName().equals(selfServiceName) &&
                        instance.getHost().equals(selfHost) &&
                        instance.getPort() == selfPort)) {
                    registry.register(instance);
                    log.debug("Registered static instance: {} -> {}:{}",
                            instance.getServiceName(), instance.getHost(), instance.getPort());
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

    /**
     * Start automatic heartbeat scheduler
     */
    private void startHeartbeatScheduler() {
        long heartbeatInterval = registry.getHeartbeatInterval();
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                sendHeartbeat();
            } catch (Exception e) {
                log.error("Error sending heartbeat", e);
            }
        }, heartbeatInterval, heartbeatInterval, java.util.concurrent.TimeUnit.MILLISECONDS);
        log.info("Started heartbeat scheduler with interval: {}ms", heartbeatInterval);
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
        heartbeatScheduler.shutdown();
        try {
            if (!heartbeatScheduler.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                heartbeatScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
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

    /**
     * Get service info for current instance
     */
    public ServiceInstance getSelfInstance() {
        String instanceId = generateInstanceId(selfServiceName, selfHost, selfPort);
        return registry.getInstance(instanceId);
    }

    /**
     * Register with central discovery service
     */
    public void registerWithDiscoveryService() {
        try {
            log.info("Attempting to register {} with discovery service at {}",
                    selfServiceName, registryUrl);

            // Create registration request with proper headers
            Map<String, Object> payload = new HashMap<>();
            payload.put("serviceName", selfServiceName);
            payload.put("host", selfHost);
            payload.put("port", selfPort);
            payload.put("status", "UP");

            // Make HTTP POST to discovery service
            String fullRegistryUrl = registryUrl + "/api/discovery/register";
            RestTemplate restTemplate = new RestTemplate();

            // Create proper headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    fullRegistryUrl,
                    request,
                    String.class
            );

            // Use the is2xxSuccessful() method which works in both Spring 5 and 6
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully registered {} with discovery service at {}",
                        selfServiceName, registryUrl);
                registered = true;
            } else {
                log.warn("Failed to register {}: HTTP {}",
                        selfServiceName, response.getStatusCode().value());
            }
        } catch (Exception e) {
            log.error("Error registering with discovery service: {}", e.getMessage());
            log.debug("Registration error details:", e);
        }
    }

    /**
     * Check if registered with discovery service
     */
    public boolean isRegistered() {
        return registered;
    }
}