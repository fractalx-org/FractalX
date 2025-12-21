//package com.fractalx.testapp.discovery;
//
//import org.springframework.web.bind.annotation.*;
//import java.util.*;
//
//@RestController
//@RequestMapping("/discovery")
//public class DiscoveryRestController {
//
//    private final Map<String, List<ServiceInstance>> registry = new HashMap<>();
//    private final long startTime = System.currentTimeMillis();
//
//    // Get all services
//    @GetMapping("/services")
//    public Map<String, List<ServiceInstance>> getAllServices() {
//        return registry;
//    }
//
//    // Get instances for a specific service
//    @GetMapping("/services/{serviceName}")
//    public List<ServiceInstance> getServiceInstances(@PathVariable String serviceName) {
//        return registry.getOrDefault(serviceName, Collections.emptyList());
//    }
//
//    // Register a new service instance
//    @PostMapping("/register")
//    public String registerInstance(@RequestBody ServiceInstance instance) {
//        registry.computeIfAbsent(instance.getServiceName(), k -> new ArrayList<>())
//                .add(instance);
//        return "Registered: " + instance.getServiceName() + " at " +
//                instance.getHost() + ":" + instance.getPort();
//    }
//
//    // Deregister a service instance
//    @DeleteMapping("/deregister/{instanceId}")
//    public String deregisterInstance(@PathVariable String instanceId) {
//        registry.values().forEach(list ->
//                list.removeIf(instance -> instance.getInstanceId().equals(instanceId))
//        );
//        return "Deregistered instance: " + instanceId;
//    }
//
//    // Check if service is available
//    @GetMapping("/health/{serviceName}")
//    public Map<String, Object> checkServiceHealth(@PathVariable String serviceName) {
//        List<ServiceInstance> instances = registry.getOrDefault(serviceName, Collections.emptyList());
//        boolean available = !instances.isEmpty();
//        int instanceCount = instances.size();
//
//        return Map.of(
//                "service", serviceName,
//                "available", available,
//                "instanceCount", instanceCount,
//                "timestamp", System.currentTimeMillis()
//        );
//    }
//
//    // Send heartbeat for this service
//    @PostMapping("/heartbeat")
//    public String sendHeartbeat() {
//        return "Heartbeat sent at: " + System.currentTimeMillis();
//    }
//
//    // Get registry statistics
//    @GetMapping("/stats")
//    public Map<String, Object> getStats() {
//        int totalServices = registry.size();
//        int totalInstances = registry.values().stream()
//                .mapToInt(List::size)
//                .sum();
//
//        return Map.of(
//                "totalServices", totalServices,
//                "totalInstances", totalInstances,
//                "uptime", System.currentTimeMillis() - startTime
//        );
//    }
//
//    // Simple ServiceInstance class - defined as inner class
//    public static class ServiceInstance {
//        private String instanceId;
//        private String serviceName;
//        private String host;
//        private int port;
//        private boolean healthy;
//        private Map<String, String> metadata;
//
//        public ServiceInstance() {}
//
//        public ServiceInstance(String instanceId, String serviceName, String host, int port) {
//            this.instanceId = instanceId;
//            this.serviceName = serviceName;
//            this.host = host;
//            this.port = port;
//            this.healthy = true;
//        }
//
//        // Getters and setters
//        public String getInstanceId() { return instanceId; }
//        public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
//
//        public String getServiceName() { return serviceName; }
//        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
//
//        public String getHost() { return host; }
//        public void setHost(String host) { this.host = host; }
//
//        public int getPort() { return port; }
//        public void setPort(int port) { this.port = port; }
//
//        public boolean isHealthy() { return healthy; }
//        public void setHealthy(boolean healthy) { this.healthy = healthy; }
//
//        public Map<String, String> getMetadata() { return metadata; }
//        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
//    }
//}

package com.fractalx.testapp.discovery;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/discovery")
public class DiscoveryRestController {

    private final Map<String, List<ServiceInstance>> registry = new HashMap<>();
    private final long startTime = System.currentTimeMillis();

    // Get all services
    @GetMapping("/services")
    public Map<String, List<ServiceInstance>> getAllServices() {
        return registry;
    }

    // Get instances for a specific service - FIXED: Add explicit parameter name
    @GetMapping("/services/{serviceName}")
    public List<ServiceInstance> getServiceInstances(@PathVariable("serviceName") String serviceName) {
        return registry.getOrDefault(serviceName, Collections.emptyList());
    }

    // Register a new service instance
    @PostMapping("/register")
    public String registerInstance(@RequestBody ServiceInstance instance) {
        registry.computeIfAbsent(instance.getServiceName(), k -> new ArrayList<>())
                .add(instance);
        return "Registered: " + instance.getServiceName() + " at " +
                instance.getHost() + ":" + instance.getPort();
    }

    // Deregister a service instance - FIXED: Add explicit parameter name
    @DeleteMapping("/deregister/{instanceId}")
    public String deregisterInstance(@PathVariable("instanceId") String instanceId) {
        boolean removed = false;
        for (List<ServiceInstance> instances : registry.values()) {
            removed = instances.removeIf(instance -> instance.getInstanceId().equals(instanceId));
            if (removed) {
                break;
            }
        }
        return removed ? "Deregistered instance: " + instanceId : "Instance not found: " + instanceId;
    }

    // Check if service is available - FIXED: Add explicit parameter name
    @GetMapping("/health/{serviceName}")
    public Map<String, Object> checkServiceHealth(@PathVariable("serviceName") String serviceName) {
        List<ServiceInstance> instances = registry.getOrDefault(serviceName, Collections.emptyList());
        boolean available = !instances.isEmpty();
        int instanceCount = instances.size();

        return Map.of(
                "service", serviceName,
                "available", available,
                "instanceCount", instanceCount,
                "timestamp", System.currentTimeMillis()
        );
    }

    // Send heartbeat for this service
    @PostMapping("/heartbeat")
    public String sendHeartbeat() {
        return "Heartbeat sent at: " + System.currentTimeMillis();
    }

    // Get registry statistics
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        int totalServices = registry.size();
        int totalInstances = registry.values().stream()
                .mapToInt(List::size)
                .sum();

        return Map.of(
                "totalServices", totalServices,
                "totalInstances", totalInstances,
                "uptime", System.currentTimeMillis() - startTime
        );
    }

    // Add JsonAutoDetect annotation and JsonInclude
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ServiceInstance {
        private String instanceId;
        private String serviceName;
        private String host;
        private int port;
        private boolean healthy = true;
        private Map<String, String> metadata;

        // Default constructor
        public ServiceInstance() {}

        // Parameterized constructor
        public ServiceInstance(String instanceId, String serviceName, String host, int port) {
            this.instanceId = instanceId;
            this.serviceName = serviceName;
            this.host = host;
            this.port = port;
            this.healthy = true;
        }

        // Getters and setters
        public String getInstanceId() { return instanceId; }
        public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }

        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    }
}