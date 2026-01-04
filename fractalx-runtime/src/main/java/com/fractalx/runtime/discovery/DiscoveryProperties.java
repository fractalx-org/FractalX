package com.fractalx.runtime.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for FractalX Discovery
 */
@ConfigurationProperties(prefix = "fractalx.discovery")
public class DiscoveryProperties {

    private boolean enabled = true;
    private String host = "localhost";
    private long heartbeatInterval = 30000; // 30 seconds
    private long instanceTtl = 90000; // 90 seconds
    private String configFile = "";
    private Mode mode = Mode.HYBRID;
    private boolean autoRegister = true;
    private String registryUrl = "http://localhost:8761";

    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public long getHeartbeatInterval() { return heartbeatInterval; }
    public void setHeartbeatInterval(long heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }

    public long getInstanceTtl() { return instanceTtl; }
    public void setInstanceTtl(long instanceTtl) { this.instanceTtl = instanceTtl; }

    public String getConfigFile() { return configFile; }
    public void setConfigFile(String configFile) { this.configFile = configFile; }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public boolean isAutoRegister() { return autoRegister; }
    public void setAutoRegister(boolean autoRegister) { this.autoRegister = autoRegister; }

    public String getRegistryUrl() { return registryUrl; }
    public void setRegistryUrl(String registryUrl) { this.registryUrl = registryUrl; }

    public enum Mode {
        STATIC, DYNAMIC, HYBRID
    }
}