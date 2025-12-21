package com.fractalx.core.discovery;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a service instance in the discovery registry
 */
public class ServiceInstance {
    private String instanceId;
    private String serviceName;
    private String host;
    private int port;
    private String status; // UP, DOWN, STARTING
    private long lastHeartbeat;
    private Map<String, String> metadata = new HashMap<>();

    public ServiceInstance() {}

    public ServiceInstance(String serviceName, String host, int port) {
        this.instanceId = generateInstanceId(serviceName, host, port);
        this.serviceName = serviceName;
        this.host = host;
        this.port = port;
        this.status = "UP";
        this.lastHeartbeat = System.currentTimeMillis();
    }

    private String generateInstanceId(String serviceName, String host, int port) {
        return serviceName + "-" + host + "-" + port;
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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public String getUrl() {
        return "http://" + host + ":" + port;
    }

    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
        this.status = "UP";
    }

    public boolean isExpired(long ttlMillis) {
        return (System.currentTimeMillis() - lastHeartbeat) > ttlMillis;
    }

    @Override
    public String toString() {
        return "ServiceInstance{" +
                "serviceName='" + serviceName + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", status='" + status + '\'' +
                '}';
    }
}