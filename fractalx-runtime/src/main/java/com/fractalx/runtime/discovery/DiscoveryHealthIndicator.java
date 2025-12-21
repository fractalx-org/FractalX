package com.fractalx.runtime.discovery;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for discovery system
 */
@Component
public class DiscoveryHealthIndicator implements HealthIndicator {

    private final com.fractalx.core.discovery.DiscoveryInitializer discoveryInitializer;

    public DiscoveryHealthIndicator(com.fractalx.core.discovery.DiscoveryInitializer discoveryInitializer) {
        this.discoveryInitializer = discoveryInitializer;
    }

    @Override
    public Health health() {
        if (discoveryInitializer == null) {
            return Health.down()
                    .withDetail("message", "Discovery not initialized")
                    .build();
        }

        if (discoveryInitializer.isHealthy()) {
            return Health.up()
                    .withDetail("message", "Discovery system is healthy")
                    .withDetail("initialized", true)
                    .build();
        } else {
            return Health.down()
                    .withDetail("message", "Discovery system is unhealthy")
                    .withDetail("initialized", true)
                    .build();
        }
    }
}