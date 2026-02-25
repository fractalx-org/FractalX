package com.fractalx.core.generator.service;

import com.fractalx.core.generator.GenerationContext;
import com.fractalx.core.generator.ServiceFileGenerator;
import com.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Generates application.yml for a microservice.
 */
public class ConfigurationGenerator implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationGenerator.class);

    /** gRPC port offset applied on top of the HTTP port. */
    private static final int GRPC_PORT_OFFSET = 10000;

    @Override
    public void generate(GenerationContext context) throws IOException {
        FractalModule module = context.getModule();
        log.debug("Generating application.yml for {}", module.getServiceName());
        Files.writeString(context.getSrcMainResources().resolve("application.yml"),
                buildYml(module, context.getAllModules()));
    }

    private String buildYml(FractalModule module, List<FractalModule> allModules) {
        return """
                spring:
                  application:
                    name: %s
                  datasource:
                    url: jdbc:h2:mem:%s
                    driver-class-name: org.h2.Driver
                    username: sa
                    password:
                    hikari:
                      maximum-pool-size: 10
                      minimum-idle: 5
                      connection-timeout: 30000
                      idle-timeout: 600000
                  jpa:
                    hibernate:
                      # Use 'validate' in production when Flyway manages schema migrations.
                      # Set to 'create-drop' for local testing without Flyway.
                      ddl-auto: update
                    show-sql: true
                    properties:
                      hibernate:
                        format_sql: true
                  h2:
                    console:
                      enabled: true
                  flyway:
                    enabled: true
                    locations: classpath:db/migration

                server:
                  port: %d

                fractalx:
                  enabled: true
                  observability:
                    tracing: true
                    metrics: true

                netscope:
                  server:
                    grpc:
                      port: %d
                    security:
                      enabled: false
                %s
                logging:
                  level:
                    com.fractalx: DEBUG
                    org.fractalx.netscope: DEBUG
                """.formatted(
                module.getServiceName(),
                module.getServiceName().replace("-", "_"),
                module.getPort(),
                module.getPort() + GRPC_PORT_OFFSET,
                buildClientServersConfig(module, allModules)
        );
    }

    /**
     * Builds the {@code netscope.client.servers} YAML block for modules that
     * have cross-module dependencies. Returns an empty string if there are none.
     */
    private String buildClientServersConfig(FractalModule module, List<FractalModule> allModules) {
        List<String> deps = module.getDependencies();
        if (deps.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("  client:\n    servers:\n");
        for (String beanType : deps) {
            String targetServiceName = NetScopeClientGenerator.beanTypeToServiceName(beanType);
            allModules.stream()
                    .filter(m -> targetServiceName.equals(m.getServiceName()))
                    .findFirst()
                    .ifPresent(target -> {
                        sb.append("      ").append(targetServiceName).append(":\n");
                        sb.append("        host: localhost\n");
                        sb.append("        port: ").append(target.getPort() + GRPC_PORT_OFFSET).append("\n");
                    });
        }
        return sb.toString();
    }
}
