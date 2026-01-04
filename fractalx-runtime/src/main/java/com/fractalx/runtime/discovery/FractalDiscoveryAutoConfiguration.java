package com.fractalx.runtime.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PreDestroy;

/**
 * Auto-configuration for FractalX Discovery
 */
@AutoConfiguration
@EnableConfigurationProperties(DiscoveryProperties.class)
@EnableScheduling
@ConditionalOnProperty(prefix = "fractalx.discovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FractalDiscoveryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FractalDiscoveryAutoConfiguration.class);

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${fractalx.discovery.host:localhost}")
    private String serviceHost;

    private com.fractalx.core.discovery.DiscoveryInitializer discoveryInitializer;

    @Bean
    @ConditionalOnMissingBean
    public com.fractalx.core.discovery.DiscoveryInitializer discoveryInitializer(DiscoveryProperties properties) {
        log.info("Initializing FractalX Discovery for service: {}", serviceName);

        discoveryInitializer = new com.fractalx.core.discovery.DiscoveryInitializer(
                properties.getHeartbeatInterval(),
                properties.getInstanceTtl()
        );

        discoveryInitializer.initialize(serviceName, serviceHost, serverPort);

        // Register with discovery service
        if (properties.isAutoRegister()) {
            log.info("Auto-registering service {} with discovery at {}",
                    serviceName, properties.getRegistryUrl());
            discoveryInitializer.registerWithDiscoveryService();
        }

        return discoveryInitializer;
    }

    /**
     * Scheduled heartbeat
     */
    @Scheduled(fixedDelayString = "${fractalx.discovery.heartbeat-interval:30000}")
    public void sendHeartbeat() {
        if (discoveryInitializer != null) {
            discoveryInitializer.sendHeartbeat();
        }
    }

    @PreDestroy
    public void cleanup() {
        if (discoveryInitializer != null) {
            discoveryInitializer.cleanup();
        }
    }
}