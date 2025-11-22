package com.fractalx.runtime;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for FractalX runtime components
 * This will be automatically picked up by Spring Boot applications
 * that include fractalx-runtime as a dependency
 */
@AutoConfiguration
@EnableConfigurationProperties(FractalXProperties.class)
@ComponentScan(basePackages = "com.fractalx.runtime")
public class FractalXRuntimeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FractalServiceRegistry fractalServiceRegistry() {
        return new FractalServiceRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceContextPropagator traceContextPropagator() {
        return new TraceContextPropagator();
    }
}