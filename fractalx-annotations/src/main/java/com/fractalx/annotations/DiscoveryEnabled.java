package com.fractalx.annotations;

import java.lang.annotation.*;

/**
 * Enables FractalX service discovery for a service
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DiscoveryEnabled {

    /**
     * Discovery mode (STATIC, DYNAMIC, HYBRID)
     */
    Mode mode() default Mode.HYBRID;

    /**
     * Heartbeat interval in milliseconds
     */
    long heartbeatInterval() default 30000;

    /**
     * Instance TTL in milliseconds
     */
    long instanceTtl() default 90000;

    /**
     * Auto-register with discovery
     */
    boolean autoRegister() default true;

    /**
     * Static configuration file path
     */
    String configFile() default "";

    enum Mode {
        STATIC,      // Only static configuration
        DYNAMIC,     // Only dynamic registration
        HYBRID       // Both static and dynamic (default)
    }
}