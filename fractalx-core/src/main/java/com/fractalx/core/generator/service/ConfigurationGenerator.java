package com.fractalx.core.generator.service;

import com.fractalx.core.generator.GenerationContext;
import com.fractalx.core.generator.ServiceFileGenerator;
import com.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Generates application.yml for a microservice.
 */
public class ConfigurationGenerator implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationGenerator.class);

    @Override
    public void generate(GenerationContext context) throws IOException {
        FractalModule module = context.getModule();
        log.debug("Generating application.yml for {}", module.getServiceName());
        Files.writeString(context.getSrcMainResources().resolve("application.yml"), buildYml(module));
    }

    private String buildYml(FractalModule module) {
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

                fractalx:
                  enabled: true
                  observability:
                    tracing: true
                    metrics: true

                logging:
                  level:
                    com.fractalx: DEBUG
                    org.springframework.cloud.openfeign: DEBUG
                """.formatted(
                module.getServiceName(),
                module.getServiceName().replace("-", "_"),
                module.getPort()
        );
    }
}
