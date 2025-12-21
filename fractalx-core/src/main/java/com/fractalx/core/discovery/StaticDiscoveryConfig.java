package com.fractalx.core.discovery;

import org.yaml.snakeyaml.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Static service discovery configuration
 * Can load from YAML/JSON files or environment variables
 */
public class StaticDiscoveryConfig {
    private static final Logger log = LoggerFactory.getLogger(StaticDiscoveryConfig.class);

    private final Map<String, List<ServiceInstance>> staticConfig = new HashMap<>();
    private final List<String> configFiles = new ArrayList<>();
    private final Yaml yaml = new Yaml();

    public StaticDiscoveryConfig() {
        // Default locations
        configFiles.add("discovery-config.yml");
        configFiles.add("discovery-config.yaml");
        configFiles.add("discovery-config.json");
        configFiles.add("config/discovery.yml");
    }

    /**
     * Add custom config file
     */
    public void addConfigFile(String filePath) {
        configFiles.add(0, filePath); // Add to beginning for priority
    }

    /**
     * Load configuration from files
     */
    public void loadConfiguration() {
        staticConfig.clear();

        for (String configFile : configFiles) {
            try {
                InputStream inputStream = null;

                if (configFile.startsWith("classpath:")) {
                    // Handle classpath resources
                    String resourcePath = configFile.substring("classpath:".length());
                    if (resourcePath.startsWith("/")) {
                        resourcePath = resourcePath.substring(1);
                    }
                    inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);

                    if (inputStream == null) {
                        log.warn("Could not find config file in classpath: {}", resourcePath);
                        continue;
                    }
                } else {
                    // Handle file system paths
                    Path path = Paths.get(configFile);
                    if (!Files.exists(path)) {
                        log.warn("Config file does not exist: {}", configFile);
                        continue;
                    }
                    inputStream = Files.newInputStream(path);
                }

                Map<String, Object> config = yaml.load(inputStream);
                inputStream.close();

                parseYamlConfig(config);
                log.info("Loaded discovery config from: {}", configFile);

            } catch (Exception e) {
                log.error("Failed to load configuration from: {}", configFile, e);
            }
        }

        // Also load from environment variables
        loadFromEnvironment();

        log.info("Loaded static configuration for {} services", staticConfig.size());
    }

    private void parseYamlConfig(Map<String, Object> config) {
        if (config == null) {
            return;
        }

        // Get instances section
        Map<String, Object> instances = (Map<String, Object>) config.get("instances");

        if (instances != null) {
            for (Map.Entry<String, Object> entry : instances.entrySet()) {
                String serviceName = entry.getKey();
                Object serviceData = entry.getValue();
                List<ServiceInstance> serviceInstances = new ArrayList<>();

                if (serviceData instanceof List) {
                    // Original format: list of maps with host/port
                    List<Map<String, Object>> instanceList = (List<Map<String, Object>>) serviceData;

                    for (Map<String, Object> instanceData : instanceList) {
                        String host = (String) instanceData.get("host");
                        Object portObj = instanceData.get("port");

                        if (host != null && portObj != null) {
                            try {
                                int port;
                                if (portObj instanceof Integer) {
                                    port = (Integer) portObj;
                                } else if (portObj instanceof String) {
                                    port = Integer.parseInt((String) portObj);
                                } else {
                                    log.warn("Invalid port type for service {}: {}", serviceName, portObj);
                                    continue;
                                }

                                ServiceInstance instance = new ServiceInstance(serviceName, host, port);
                                serviceInstances.add(instance);
                                log.debug("Added static instance: {} -> {}:{}", serviceName, host, port);
                            } catch (NumberFormatException e) {
                                log.warn("Invalid port number for service {}: {}", serviceName, portObj);
                            }
                        }
                    }
                } else if (serviceData instanceof String) {
                    // New format: "host:port" string
                    String hostPort = (String) serviceData;
                    String[] parts = hostPort.split(":");

                    if (parts.length == 2) {
                        try {
                            String host = parts[0].trim();
                            int port = Integer.parseInt(parts[1].trim());

                            ServiceInstance instance = new ServiceInstance(serviceName, host, port);
                            serviceInstances.add(instance);
                            log.debug("Added static instance from string format: {} -> {}:{}", serviceName, host, port);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid host:port format for service {}: {}", serviceName, hostPort);
                        }
                    } else {
                        log.warn("Invalid host:port format for service {}: {}", serviceName, hostPort);
                    }
                } else if (serviceData instanceof Map) {
                    // Another possible format: single map
                    Map<String, Object> instanceData = (Map<String, Object>) serviceData;
                    String host = (String) instanceData.get("host");
                    Object portObj = instanceData.get("port");

                    if (host != null && portObj != null) {
                        try {
                            int port;
                            if (portObj instanceof Integer) {
                                port = (Integer) portObj;
                            } else if (portObj instanceof String) {
                                port = Integer.parseInt((String) portObj);
                            } else {
                                log.warn("Invalid port type for service {}: {}", serviceName, portObj);
                                continue;
                            }

                            ServiceInstance instance = new ServiceInstance(serviceName, host, port);
                            serviceInstances.add(instance);
                            log.debug("Added static instance from map format: {} -> {}:{}", serviceName, host, port);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid port number for service {}: {}", serviceName, portObj);
                        }
                    }
                } else {
                    log.warn("Unknown data format for service {}: {}", serviceName, serviceData);
                }

                if (!serviceInstances.isEmpty()) {
                    staticConfig.put(serviceName, serviceInstances);
                    log.info("Configured {} static instance(s) for service: {}",
                            serviceInstances.size(), serviceName);
                }
            }
        }
    }

    private void loadFromEnvironment() {
        // Load from environment variables like DISCOVERY_SERVICES=service1:host1:port1,service2:host2:port2
        String servicesEnv = System.getenv("DISCOVERY_SERVICES");
        if (servicesEnv != null && !servicesEnv.isEmpty()) {
            parseServicesFromEnv(servicesEnv);
        }
    }

    private void parseServicesFromEnv(String servicesEnv) {
        String[] serviceEntries = servicesEnv.split(",");
        for (String entry : serviceEntries) {
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                String serviceName = parts[0].trim();
                String host = parts[1].trim();
                int port = Integer.parseInt(parts[2].trim());

                staticConfig.computeIfAbsent(serviceName, k -> new ArrayList<>())
                        .add(new ServiceInstance(serviceName, host, port));

                log.info("Added static instance from environment: {} -> {}:{}", serviceName, host, port);
            }
        }
    }

    /**
     * Get static instances for a service
     */
    public List<ServiceInstance> getStaticInstances(String serviceName) {
        return staticConfig.getOrDefault(serviceName, new ArrayList<>());
    }

    /**
     * Get all statically configured services
     */
    public Set<String> getAllStaticServices() {
        return staticConfig.keySet();
    }

    /**
     * Clear all configuration
     */
    public void clear() {
        staticConfig.clear();
    }

    /**
     * Get all configured instances (for testing/debugging)
     */
    public Map<String, List<ServiceInstance>> getAllStaticInstances() {
        return new HashMap<>(staticConfig);
    }
}