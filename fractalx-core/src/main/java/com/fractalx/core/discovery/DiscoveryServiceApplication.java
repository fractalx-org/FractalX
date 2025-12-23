package com.fractalx.core.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

/**
 * Standalone discovery service that can be run independently
 */
@SpringBootApplication
@RestController
@RequestMapping("/api/discovery")
public class DiscoveryServiceApplication {

    private final DiscoveryInitializer discoveryInitializer;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiscoveryServiceApplication.class);

    public DiscoveryServiceApplication() {
        // Default configuration: 30s heartbeat, 90s TTL
        this.discoveryInitializer = new DiscoveryInitializer(30000, 90000);

        // Initialize with discovery service info
        // Note: Port will be set from application.properties
        this.discoveryInitializer.initialize("discovery-service", "localhost", 8761);
    }

    @PostConstruct
    public void initialize() {
        try {
            discoveryInitializer.initialize("discovery-service", "localhost", 8761);
        } catch (Exception e) {
            log.error("Failed to initialize discovery service", e);
            throw new RuntimeException("Failed to initialize discovery service", e);
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServiceApplication.class, args);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "discovery-service");
        health.put("timestamp", System.currentTimeMillis());
        health.put("initialized", discoveryInitializer != null);
        return health;
    }

    @GetMapping("/services")
    public Map<String, List<ServiceInstance>> getAllServices() {
        Map<String, List<ServiceInstance>> services = new HashMap<>();
        for (String serviceName : discoveryInitializer.getRegistry().getAllServices()) {
            services.put(serviceName, discoveryInitializer.getRegistry().getInstances(serviceName));
        }
        return services;
    }

    @GetMapping("/services/{serviceName}")
    public List<ServiceInstance> getServiceInstances(@PathVariable String serviceName) {
        return discoveryInitializer.getRegistry().getInstances(serviceName);
    }

    @PostMapping("/register")
    public Map<String, Object> registerInstance(@RequestBody ServiceInstance instance) {
        discoveryInitializer.getRegistry().register(instance);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Service registered successfully");
        response.put("instanceId", instance.getInstanceId());
        response.put("serviceName", instance.getServiceName());
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @PostMapping("/deregister")
    public Map<String, Object> deregisterInstance(@RequestBody Map<String, String> request) {
        String instanceId = request.get("instanceId");
        discoveryInitializer.getRegistry().deregister(instanceId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Service deregistered successfully");
        response.put("instanceId", instanceId);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalServices", discoveryInitializer.getRegistry().getTotalServices());
        stats.put("totalInstances", discoveryInitializer.getRegistry().getTotalInstances());

        // Safely get uptime
        ServiceInstance selfInstance = discoveryInitializer.getSelfInstance();
        if (selfInstance != null && selfInstance.getMetadata() != null) {
            String startTimeStr = selfInstance.getMetadata().get("startTime");
            if (startTimeStr != null) {
                try {
                    long startTime = Long.parseLong(startTimeStr);
                    stats.put("uptime", System.currentTimeMillis() - startTime);
                } catch (NumberFormatException e) {
                    stats.put("uptime", "N/A");
                }
            } else {
                stats.put("uptime", "N/A");
            }
        } else {
            stats.put("uptime", "N/A");
        }

        return stats;
    }

    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("heartbeatInterval", discoveryInitializer.getRegistry().getHeartbeatInterval());
        config.put("instanceTtl", discoveryInitializer.getRegistry().getInstanceTtl());
        return config;
    }
}