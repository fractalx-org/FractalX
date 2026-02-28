package org.fractalx.runtime;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ComponentScan(basePackages = "org.fractalx.runtime")
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

    /**
     * Auto-registers the Centralized Log Appender
     * if fractalx.observability.metrics is enabled.
     */
    @Bean
    @ConditionalOnProperty(prefix = "fractalx.observability", name = "metrics", havingValue = "true", matchIfMissing = true)
    public FractalLogConfigurator fractalLogConfigurator(
            FractalXProperties properties,
            @Value("${spring.application.name:unknown-service}") String appName) {
        return new FractalLogConfigurator(properties, appName);
    }

    /**
     * Inner class to handle Logback configuration safely
     */
    public static class FractalLogConfigurator {

        private final FractalXProperties properties;
        private final String appName;

        public FractalLogConfigurator(FractalXProperties properties, String appName) {
            this.properties = properties;
            this.appName = appName;
        }

        @PostConstruct
        public void init() {
            String url = properties.getObservability().getLoggerUrl();
            if (url == null || url.isEmpty()) return;

            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            // Create our custom appender
            FractalLogAppender appender = new FractalLogAppender(url, appName);
            appender.setContext(context);
            appender.setName("FRACTAL_LOG_SHIPPER");
            appender.start();

            // Attach to Root Logger
            Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.addAppender(appender);

            System.out.println("FractalX Observability: Centralized Logging Enabled -> " + url);
        }
    }
}