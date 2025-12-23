package com.fractalx.core.generator;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates application.yml configuration for microservices
 */
public class ConfigurationGenerator {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationGenerator.class);

    public void generateApplicationYml(FractalModule module, Path srcMainResources) throws IOException {
        log.debug("Generating application.yml for {}", module.getServiceName());

        String ymlContent = generateYmlContent(module);
        Path ymlPath = srcMainResources.resolve("application.yml");
        Files.writeString(ymlPath, ymlContent);
    }

    private String generateYmlContent(FractalModule module) {
        return """
            spring:
              application:
                name: %s
              datasource:
                url: jdbc:h2:mem:%s
                driver-class-name: org.h2.Driver
                username: sa
                password:
              jpa:
                hibernate:
                  ddl-auto: update
                show-sql: true
                properties:
                  hibernate:
                    format_sql: true
              h2:
                console:
                  enabled: true
            
            server:
              port: %d
            
            # FractalX Discovery Configuration
            fractalx:
              enabled: true
              discovery:
                enabled: true
                mode: HYBRID
                host: localhost
                port: %d
                heartbeat-interval: 30000
                instance-ttl: 90000
                config-file: classpath:discovery-config.yml
                auto-register: true
              observability:
                tracing: true
                metrics: true
            
            # Service Registry Configuration
            service:
              registry:
                url: http://localhost:8761
                enabled: true
                health-check-interval: 30000
            
            # Actuator endpoints for service discovery
            management:
              endpoints:
                web:
                  exposure:
                    include: health,info,metrics,discovery
              endpoint:
                health:
                  show-details: always
                discovery:
                  enabled: true
            
            logging:
              level:
                com.fractalx: DEBUG
                org.springframework.cloud.openfeign: DEBUG
                com.fractalx.core.discovery: INFO
            """.formatted(
                module.getServiceName(),
                module.getServiceName().replace("-", "_"),
                module.getPort(),
                module.getPort()
        );
    }
}