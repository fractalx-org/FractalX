package com.fractalx.annotations;

import java.lang.annotation.*;

/**
 * Configures service discovery parameters
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DiscoveryConfig {

    /**
     * Service name for discovery
     */
    String serviceName() default "";

    /**
     * Service host (auto-detected if empty)
     */
    String host() default "";

    /**
     * Service port
     */
    int port() default 0;

    /**
     * Health check endpoint
     */
    String healthEndpoint() default "/actuator/health";

    /**
     * Discovery endpoint
     */
    String discoveryEndpoint() default "/actuator/discovery";

    /**
     * Enable/disable load balancing
     */
    boolean loadBalancing() default true;

    /**
     * Enable/disable failover
     */
    boolean failover() default true;

    /**
     * Retry count for failed calls
     */
    int retryCount() default 3;
}