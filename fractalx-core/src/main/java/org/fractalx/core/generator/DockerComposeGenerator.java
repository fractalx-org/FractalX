package org.fractalx.core.generator;

import org.fractalx.core.config.FractalxConfig;
import org.fractalx.core.generator.registry.RegistryServiceGenerator;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates docker-compose.yml and per-service Dockerfiles for fully container-ready
 * deployment. Services use the {@code docker} Spring profile which substitutes
 * environment variables for all hardcoded localhost references.
 */
public class DockerComposeGenerator {

    private static final Logger log           = LoggerFactory.getLogger(DockerComposeGenerator.class);
    private static final int GATEWAY_PORT    = 9999;
    private static final int ADMIN_PORT      = 9090;
    private static final int LOGGER_PORT     = 9099;
    private static final int JAEGER_UI_PORT  = 16686;
    private static final int JAEGER_OTLP_PORT = 4317;

    public void generate(List<FractalModule> modules, Path outputRoot,
                         boolean hasSagaOrchestrator, FractalxConfig config) throws IOException {
        generateDockerCompose(modules, outputRoot, hasSagaOrchestrator, config);
        generateDockerfiles(modules, outputRoot, hasSagaOrchestrator, config);
        log.info("Generated docker-compose.yml and Dockerfiles");
    }

    // -------------------------------------------------------------------------

    private void generateDockerCompose(List<FractalModule> modules, Path outputRoot,
                                        boolean hasSagaOrchestrator, FractalxConfig config) throws IOException {
        StringBuilder services = new StringBuilder();

        int regPort = config.registryPort();

        // Jaeger all-in-one (no registry dependency — starts independently)
        services.append("  jaeger:\n");
        services.append("    image: ").append(config.dockerImages().jaegerImage()).append("\n");
        services.append("    environment:\n");
        services.append("      - COLLECTOR_OTLP_ENABLED=true\n");
        services.append("      - MEMORY_MAX_TRACES=50000\n");
        services.append("    ports:\n");
        services.append("      - \"").append(JAEGER_UI_PORT).append(":").append(JAEGER_UI_PORT).append("\"\n");
        services.append("      - \"").append(JAEGER_OTLP_PORT).append(":").append(JAEGER_OTLP_PORT).append("\"\n");
        services.append("      - \"4318:4318\"\n");
        services.append("    healthcheck:\n");
        services.append("      test: [\"CMD\", \"wget\", \"--spider\", \"-q\", \"http://localhost:")
                .append(JAEGER_UI_PORT).append("\"]\n");
        services.append("      interval: 10s\n      timeout: 5s\n      retries: 3\n\n");

        // Logger service — depends on registry so it can self-register on startup
        services.append("  logger-service:\n");
        services.append("    build:\n      context: ./logger-service\n      dockerfile: Dockerfile\n");
        services.append("    ports:\n      - \"").append(config.loggerPort()).append(":").append(config.loggerPort()).append("\"\n");
        services.append("    environment:\n");
        services.append("      - SPRING_PROFILES_ACTIVE=docker\n");
        services.append("      - FRACTALX_REGISTRY_URL=http://fractalx-registry:").append(regPort).append("\n");
        services.append("      - OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:").append(JAEGER_OTLP_PORT).append("\n");
        services.append("      - OTEL_SERVICE_NAME=logger-service\n");
        services.append("    healthcheck:\n");
        services.append("      test: [\"CMD\", \"wget\", \"--spider\", \"-q\",");
        services.append(" \"http://localhost:").append(config.loggerPort()).append("/actuator/health\"]\n");
        services.append("      interval: 10s\n      timeout: 5s\n      retries: 3\n");
        services.append("    depends_on:\n      fractalx-registry:\n        condition: service_healthy\n\n");

        // fractalx-registry (must start first)
        services.append("  fractalx-registry:\n");
        services.append("    build:\n      context: ./fractalx-registry\n      dockerfile: Dockerfile\n");
        services.append("    ports:\n      - \"").append(regPort).append(":").append(regPort).append("\"\n");
        services.append("    healthcheck:\n");
        services.append("      test: [\"CMD\", \"curl\", \"-f\", \"http://localhost:")
                .append(regPort).append("/services/health\"]\n");
        services.append("      interval: 15s\n      timeout: 5s\n      retries: 3\n\n");

        // Generated microservices
        for (FractalModule m : modules) {
            int grpcPort = m.grpcPort();
            services.append("  ").append(m.getServiceName()).append(":\n");
            services.append("    build:\n      context: ./").append(m.getServiceName())
                    .append("\n      dockerfile: Dockerfile\n");
            services.append("    ports:\n");
            services.append("      - \"").append(m.getPort()).append(":").append(m.getPort()).append("\"\n");
            services.append("      - \"").append(grpcPort).append(":").append(grpcPort).append("\"\n");
            services.append("    environment:\n");
            services.append("      - SPRING_PROFILES_ACTIVE=docker\n");
            services.append("      - FRACTALX_REGISTRY_URL=http://fractalx-registry:").append(regPort).append("\n");
            services.append("      - FRACTALX_REGISTRY_HOST=").append(m.getServiceName()).append("\n");
            services.append("      - OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:").append(JAEGER_OTLP_PORT).append("\n");
            services.append("      - OTEL_SERVICE_NAME=").append(m.getServiceName()).append("\n");
            services.append("      - FRACTALX_LOGGER_URL=http://logger-service:").append(config.loggerPort()).append("/api/logs\n");
            for (String dep : m.getDependencies()) {
                String peer   = beanTypeToServiceName(dep);
                String envPfx = peer.toUpperCase().replace("-", "_");
                int peerGrpc  = portForService(peer, modules) + FractalModule.GRPC_PORT_OFFSET;
                services.append("      - ").append(envPfx).append("_HOST=").append(peer).append("\n");
                services.append("      - ").append(envPfx).append("_GRPC_PORT=").append(peerGrpc).append("\n");
            }
            services.append("    depends_on:\n      fractalx-registry:\n        condition: service_healthy\n\n");
        }

        // Saga orchestrator (optional)
        if (hasSagaOrchestrator) {
            services.append("  fractalx-saga-orchestrator:\n");
            services.append("    build:\n      context: ./fractalx-saga-orchestrator\n      dockerfile: Dockerfile\n");
            services.append("    ports:\n      - \"8099:8099\"\n");
            services.append("    environment:\n");
            services.append("      - SPRING_PROFILES_ACTIVE=docker\n");
            services.append("      - FRACTALX_REGISTRY_URL=http://fractalx-registry:").append(regPort).append("\n");
            services.append("      - OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:").append(JAEGER_OTLP_PORT).append("\n");
            services.append("      - OTEL_SERVICE_NAME=fractalx-saga-orchestrator\n");
            services.append("      - FRACTALX_LOGGER_URL=http://logger-service:").append(config.loggerPort()).append("/api/logs\n");
            services.append("    depends_on:\n      fractalx-registry:\n        condition: service_healthy\n\n");
        }

        // Admin service
        services.append("  admin-service:\n");
        services.append("    build:\n      context: ./admin-service\n      dockerfile: Dockerfile\n");
        services.append("    ports:\n      - \"").append(config.adminPort()).append(":").append(config.adminPort()).append("\"\n");
        services.append("    environment:\n");
        services.append("      - SPRING_PROFILES_ACTIVE=docker\n");
        services.append("      - FRACTALX_REGISTRY_URL=http://fractalx-registry:").append(regPort).append("\n");
        services.append("      - JAEGER_QUERY_URL=http://jaeger:").append(JAEGER_UI_PORT).append("\n");
        services.append("      - FRACTALX_LOGGER_URL=http://logger-service:").append(config.loggerPort()).append("/api/logs\n");
        services.append("    depends_on:\n      fractalx-registry:\n        condition: service_healthy\n\n");

        // API Gateway
        services.append("  fractalx-gateway:\n");
        services.append("    build:\n      context: ./fractalx-gateway\n      dockerfile: Dockerfile\n");
        services.append("    ports:\n      - \"").append(config.gatewayPort()).append(":").append(config.gatewayPort()).append("\"\n");
        services.append("    environment:\n");
        services.append("      - SPRING_PROFILES_ACTIVE=docker\n");
        services.append("      - FRACTALX_REGISTRY_URL=http://fractalx-registry:").append(regPort).append("\n");
        services.append("      - OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:").append(JAEGER_OTLP_PORT).append("\n");
        services.append("      - OTEL_SERVICE_NAME=fractalx-gateway\n");
        services.append("    depends_on:\n      fractalx-registry:\n        condition: service_healthy\n");

        String compose = "version: '3.9'\n\n# Auto-generated by FractalX — edit as needed\nservices:\n" + services;
        Files.writeString(outputRoot.resolve("docker-compose.yml"), compose);
    }

    private void generateDockerfiles(List<FractalModule> modules, Path outputRoot,
                                      boolean hasSagaOrchestrator, FractalxConfig config) throws IOException {
        String dockerfile = """
                # Auto-generated by FractalX
                FROM __MAVEN_IMAGE__ AS build

                WORKDIR /app

                COPY pom.xml .
                RUN mvn -B dependency:resolve dependency:resolve-plugins

                COPY src ./src
                RUN mvn -B package -DskipTests -q

                FROM __JRE_IMAGE__

                WORKDIR /app

                RUN groupadd -r fractalx && useradd -r -g fractalx fractalx && mkdir -p /app/logs && chown fractalx:fractalx /app/logs

                COPY --from=build --chown=fractalx:fractalx /app/target/*.jar app.jar

                USER fractalx

                ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
                """
                .replace("__MAVEN_IMAGE__", config.dockerImages().mavenBuildImage())
                .replace("__JRE_IMAGE__", config.dockerImages().jreRuntimeImage());

        List<String> serviceDirs = new ArrayList<>();
        modules.forEach(m -> serviceDirs.add(m.getServiceName()));
        serviceDirs.add("fractalx-registry");
        serviceDirs.add("fractalx-gateway");
        serviceDirs.add("admin-service");
        serviceDirs.add("logger-service");
        if (hasSagaOrchestrator) serviceDirs.add("fractalx-saga-orchestrator");

        for (String dir : serviceDirs) {
            Path serviceDir = outputRoot.resolve(dir);
            if (Files.exists(serviceDir)) {
                Files.writeString(serviceDir.resolve("Dockerfile"), dockerfile);
            }
        }
    }

    private String beanTypeToServiceName(String beanType) {
        String name  = beanType.replaceAll("(?i)(Service|Client)$", "");
        String kebab = name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
        return kebab + "-service";
    }

    private int portForService(String serviceName, List<FractalModule> modules) {
        return modules.stream()
                .filter(m -> m.getServiceName().equals(serviceName))
                .findFirst().map(FractalModule::getPort).orElse(8080);
    }
}
