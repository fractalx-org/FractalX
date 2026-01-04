package com.fractalx.core.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Lightweight, in-memory service discovery registry
 */
public class DiscoveryRegistry {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryRegistry.class);

    // Service name -> List of instances
    private final Map<String, List<ServiceInstance>> registry = new ConcurrentHashMap<>();
    private final Map<String, ServiceInstance> instanceMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler;
    private final long heartbeatInterval;
    private final long instanceTtl;

    private boolean autoCleanupEnabled = true;
    private boolean autoRegisterEnabled = true;

    public DiscoveryRegistry() {
        this(30000, 90000); // 30s heartbeat, 90s TTL
    }

    public DiscoveryRegistry(long heartbeatInterval, long instanceTtl) {
        this.heartbeatInterval = heartbeatInterval;
        this.instanceTtl = instanceTtl;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        if (autoCleanupEnabled) {
            startCleanupTask();
        }
    }

    /**
     * Register a service instance
     */
    public synchronized void register(ServiceInstance instance) {
        String serviceName = instance.getServiceName();
        String instanceId = instance.getInstanceId();

        // ADD NULL CHECK
        if (instanceId == null || serviceName == null) {
            log.error("Cannot register instance with null ID or service name: {}", instance);
            return;
        }

        log.info("Registering service instance: {} -> {}:{}",
                serviceName, instance.getHost(), instance.getPort());

        // Remove old instance if exists
        deregister(instanceId);

        // Add to registry
        registry.computeIfAbsent(serviceName, k -> new ArrayList<>()).add(instance);
        instanceMap.put(instanceId, instance);

        log.debug("Service {} now has {} instances", serviceName,
                registry.get(serviceName).size());
    }

    /**
     * Deregister a service instance
     */
    public synchronized void deregister(String instanceId) {
        ServiceInstance instance = instanceMap.remove(instanceId);
        if (instance != null) {
            String serviceName = instance.getServiceName();
            List<ServiceInstance> instances = registry.get(serviceName);
            if (instances != null) {
                instances.removeIf(inst -> inst.getInstanceId().equals(instanceId));
                log.info("Deregistered instance: {} -> {}:{}",
                        serviceName, instance.getHost(), instance.getPort());

                if (instances.isEmpty()) {
                    registry.remove(serviceName);
                }
            }
        }
    }

    /**
     * Update heartbeat for an instance
     */
    public synchronized void heartbeat(String instanceId) {
        ServiceInstance instance = instanceMap.get(instanceId);
        if (instance != null) {
            instance.updateHeartbeat();
            log.trace("Heartbeat received for instance: {}", instanceId);
        }
    }

    /**
     * Get all instances for a service
     */
    public List<ServiceInstance> getInstances(String serviceName) {
        List<ServiceInstance> instances = registry.getOrDefault(serviceName, new ArrayList<>());

        // Filter out expired instances
        if (autoCleanupEnabled) {
            instances = instances.stream()
                    .filter(instance -> !instance.isExpired(instanceTtl))
                    .collect(Collectors.toList());
        }

        return Collections.unmodifiableList(instances);
    }

    /**
     * Get all registered services
     */
    public Set<String> getAllServices() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    /**
     * Get a healthy instance (simple round-robin)
     */
    public ServiceInstance getHealthyInstance(String serviceName) {
        List<ServiceInstance> instances = getInstances(serviceName);
        if (instances.isEmpty()) {
            return null;
        }

        // Simple round-robin (for demo - in production use better load balancing)
        int index = new Random().nextInt(instances.size());
        return instances.get(index);
    }

    /**
     * Get instance by ID
     */
    public ServiceInstance getInstance(String instanceId) {
        return instanceMap.get(instanceId);
    }

    /**
     * Check if service is available
     */
    public boolean isServiceAvailable(String serviceName) {
        return !getInstances(serviceName).isEmpty();
    }

    /**
     * Get all instances (for monitoring)
     */
    public List<ServiceInstance> getAllInstances() {
        return new ArrayList<>(instanceMap.values());
    }

    /**
     * Clear expired instances
     */
    public synchronized void cleanupExpiredInstances() {
        long currentTime = System.currentTimeMillis();
        List<String> expiredInstances = new ArrayList<>();

        for (ServiceInstance instance : instanceMap.values()) {
            if (instance.isExpired(instanceTtl)) {
                expiredInstances.add(instance.getInstanceId());
                log.warn("Instance expired: {} (last heartbeat: {}ms ago)",
                        instance.getInstanceId(), currentTime - instance.getLastHeartbeat());
            }
        }

        for (String instanceId : expiredInstances) {
            deregister(instanceId);
        }
    }

    /**
     * Start cleanup task
     */
    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredInstances();
            } catch (Exception e) {
                log.error("Error in cleanup task", e);
            }
        }, instanceTtl / 2, instanceTtl / 2, TimeUnit.MILLISECONDS);
    }

    /**
     * Shutdown registry
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        registry.clear();
        instanceMap.clear();
    }

    // Configuration methods
    public void setAutoCleanupEnabled(boolean enabled) {
        this.autoCleanupEnabled = enabled;
    }

    public void setAutoRegisterEnabled(boolean enabled) {
        this.autoRegisterEnabled = enabled;
    }

    public int getTotalInstances() {
        return instanceMap.size();
    }

    public int getTotalServices() {
        return registry.size();
    }

    // getter methods for heartbeat interval and instance TTL
    public long getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public long getInstanceTtl() {
        return instanceTtl;
    }

    // getter for auto cleanup status
    public boolean isAutoCleanupEnabled() {
        return autoCleanupEnabled;
    }

    // getter for auto register status
    public boolean isAutoRegisterEnabled() {
        return autoRegisterEnabled;
    }
}