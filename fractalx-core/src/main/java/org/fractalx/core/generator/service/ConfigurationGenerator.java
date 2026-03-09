package org.fractalx.core.generator.service;

import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.fractalx.core.model.FractalModule;
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
        log.debug("Generating application YAMLs for {}", module.getServiceName());

        // Base config (profile-agnostic, references env vars)
        Files.writeString(context.getSrcMainResources().resolve("application.yml"),
                buildBaseYml(module, context.getFractalxConfig()));

        // Dev profile (localhost defaults sourced from FractalxConfig)
        Files.writeString(context.getSrcMainResources().resolve("application-dev.yml"),
                buildDevYml(module, context.getAllModules(), context.getFractalxConfig()));

        // Docker profile (all env-var driven, container-ready)
        Files.writeString(context.getSrcMainResources().resolve("application-docker.yml"),
                buildDockerYml(module, context.getAllModules()));
    }

    // -------------------------------------------------------------------------
    // Profile-specific YAML builders
    // -------------------------------------------------------------------------

    /** Base application.yml — references env vars, activates dev profile by default. */
    private String buildBaseYml(FractalModule module, org.fractalx.core.config.FractalxConfig cfg) {
        boolean tracingEnabled = cfg.isTracingEnabled(module.getServiceName());
        String otelBlock = tracingEnabled
                ? "    otel:\n      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:%s}\n".formatted(cfg.otelEndpoint())
                : "    otel:\n      enabled: false\n";
        String samplingProbability = tracingEnabled ? "1.0" : "0.0";

        return """
                spring:
                  application:
                    name: %s
                  profiles:
                    active: ${SPRING_PROFILES_ACTIVE:dev}

                server:
                  port: %d

                fractalx:
                  enabled: true
                  registry:
                    url: ${FRACTALX_REGISTRY_URL:%s}
                    enabled: true
                    host: ${FRACTALX_REGISTRY_HOST:localhost}
                  saga:
                    orchestrator:
                      url: ${FRACTALX_SAGA_ORCHESTRATOR_URL:http://localhost:8099}
                  observability:
                    tracing: %s
                    metrics: true
                    logger-url: ${FRACTALX_LOGGER_URL:%s/api/logs}
                %s
                netscope:
                  server:
                    grpc:
                      port: %d
                    security:
                      enabled: false

                management:
                  endpoints:
                    web:
                      exposure:
                        include: health,info,metrics,prometheus,refresh
                  endpoint:
                    health:
                      show-details: always
                      show-components: always
                  tracing:
                    sampling:
                      probability: %s

                logging:
                  level:
                    org.fractalx: DEBUG
                    org.fractalx.netscope: DEBUG
                """.formatted(module.getServiceName(), module.getPort(),
                cfg.registryUrl(), tracingEnabled, cfg.loggerUrl(),
                otelBlock, module.getPort() + GRPC_PORT_OFFSET, samplingProbability);
    }

    /** application-dev.yml — localhost hardcoded, H2 in-memory, suitable for local dev. */
    private String buildDevYml(FractalModule module, List<FractalModule> allModules,
                                org.fractalx.core.config.FractalxConfig cfg) {
        return """
                # Dev profile — all services run on localhost
                spring:
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
                      ddl-auto: update
                    show-sql: false
                    properties:
                      hibernate:
                        format_sql: false
                  h2:
                    console:
                      enabled: true
                  flyway:
                    enabled: true
                    locations: classpath:db/migration
                %s
                """.formatted(
                module.getServiceName().replace("-", "_"),
                buildClientServersConfig(module, allModules)
        );
    }

    /** application-docker.yml — all values driven by env vars, no hardcoded localhost. */
    private String buildDockerYml(FractalModule module, List<FractalModule> allModules) {
        StringBuilder sb = new StringBuilder("# Docker profile — all values from environment variables\n");
        sb.append("spring:\n");
        sb.append("  datasource:\n");
        sb.append("    url: ${DB_URL:jdbc:h2:mem:").append(module.getServiceName().replace("-", "_")).append("}\n");
        sb.append("    username: ${DB_USERNAME:sa}\n");
        sb.append("    password: ${DB_PASSWORD:}\n");
        sb.append("  flyway:\n");
        sb.append("    enabled: true\n");
        sb.append("    locations: classpath:db/migration\n");
        sb.append("fractalx:\n");
        sb.append("  observability:\n");
        sb.append("    otel:\n");
        sb.append("      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://jaeger:4317}\n");
        sb.append("    logger-url: ${FRACTALX_LOGGER_URL:http://logger-service:9099/api/logs}\n");

        List<String> deps = module.getDependencies();
        if (!deps.isEmpty()) {
            sb.append("netscope:\n  client:\n    servers:\n");
            for (String beanType : deps) {
                String targetServiceName = NetScopeClientGenerator.beanTypeToServiceName(beanType);
                allModules.stream()
                        .filter(m -> targetServiceName.equals(m.getServiceName()))
                        .findFirst()
                        .ifPresent(target -> {
                            String envPfx = targetServiceName.toUpperCase().replace("-", "_");
                            sb.append("      ").append(targetServiceName).append(":\n");
                            sb.append("        host: ${").append(envPfx).append("_HOST:")
                              .append(targetServiceName).append("}\n");
                            sb.append("        port: ${").append(envPfx).append("_GRPC_PORT:")
                              .append(target.getPort() + GRPC_PORT_OFFSET).append("}\n");
                        });
            }
        }
        return sb.toString();
    }

    /** Builds the legacy dev localhost {@code netscope.client.servers} block. */
    private String buildYml(FractalModule module, List<FractalModule> allModules) {
        // kept for backward-compat — not called by generate() anymore
        return buildBaseYml(module, org.fractalx.core.config.FractalxConfig.defaults());
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

        StringBuilder sb = new StringBuilder("netscope:\n  client:\n    servers:\n");
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
