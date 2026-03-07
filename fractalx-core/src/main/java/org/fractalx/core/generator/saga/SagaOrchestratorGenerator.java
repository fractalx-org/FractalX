package org.fractalx.core.generator.saga;

import org.fractalx.core.FractalxVersion;
import org.fractalx.core.datamanagement.DataReadmeGenerator;
import org.fractalx.core.model.FractalModule;
import org.fractalx.core.model.MethodParam;
import org.fractalx.core.model.SagaDefinition;
import org.fractalx.core.model.SagaStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates the {@code fractalx-saga-orchestrator} Spring Boot service.
 *
 * <p>For each detected {@link SagaDefinition} the generator produces:
 * <ul>
 *   <li>{@code SagaInstance} — JPA entity persisting execution state</li>
 *   <li>{@code SagaStatus} — enum: STARTED → IN_PROGRESS → DONE | COMPENSATING → FAILED</li>
 *   <li>{@code SagaInstanceRepository} — Spring Data repository</li>
 *   <li>{@code <SagaId>SagaService} — orchestrates forward steps and compensation</li>
 *   <li>{@code SagaController} — REST entry-points: start / status / list</li>
 *   <li>{@code pom.xml}, {@code Application.java}, {@code application.yml}</li>
 * </ul>
 *
 * <p>The orchestrator calls each participating service using NetScope gRPC clients
 * that are also generated into the orchestrator's source tree.
 */
public class SagaOrchestratorGenerator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestratorGenerator.class);

    private static final String ORCHESTRATOR_NAME    = "fractalx-saga-orchestrator";
    private static final int    ORCHESTRATOR_PORT    = 8099;
    private static final String BASE_PACKAGE         = "org.fractalx.generated.sagaorchestrator";
    private static final String FRACTALX_RUNTIME_VER = FractalxVersion.get();

    public void generateOrchestratorService(List<FractalModule> modules,
                                            List<SagaDefinition> sagas,
                                            Path outputRoot) throws IOException {
        if (sagas.isEmpty()) {
            log.info("No @DistributedSaga definitions found — saga orchestrator not generated");
            return;
        }

        log.info("Generating saga orchestrator service with {} saga(s)...", sagas.size());

        Path serviceRoot = outputRoot.resolve(ORCHESTRATOR_NAME);
        Path srcMainJava = serviceRoot.resolve("src/main/java");
        Path resourcesDir = serviceRoot.resolve("src/main/resources");
        Files.createDirectories(srcMainJava);
        Files.createDirectories(resourcesDir);
        Files.createDirectories(serviceRoot.resolve("src/test/java"));

        Path basePkg = packagePath(srcMainJava, BASE_PACKAGE);
        Files.createDirectories(packagePath(basePkg, "model"));
        Files.createDirectories(packagePath(basePkg, "repository"));
        Files.createDirectories(packagePath(basePkg, "service"));
        Files.createDirectories(packagePath(basePkg, "controller"));
        Files.createDirectories(packagePath(basePkg, "client"));

        // ── Infrastructure files ─────────────────────────────────────────────
        writePom(serviceRoot, modules);
        writeApplicationClass(srcMainJava);
        writeApplicationYml(resourcesDir, modules, sagas);
        writeFlywayMigration(resourcesDir);

        // ── Shared model ─────────────────────────────────────────────────────
        writeFile(packagePath(basePkg, "model"), "SagaStatus.java",    buildSagaStatus());
        writeFile(packagePath(basePkg, "model"), "SagaInstance.java",  buildSagaInstance());
        writeFile(packagePath(basePkg, "repository"), "SagaInstanceRepository.java", buildRepository());

        // ── Per-saga service ─────────────────────────────────────────────────
        for (SagaDefinition saga : sagas) {
            String serviceClass = saga.toClassName() + "SagaService";
            writeFile(packagePath(basePkg, "service"), serviceClass + ".java",
                    buildSagaService(saga));
        }

        // ── NetScope client interfaces — aggregated across ALL sagas ─────────
        // Collect every step and its params globally so that when multiple sagas
        // call the same service (e.g. PayrollService is used by both
        // onboard-employee-saga AND approve-leave-saga), all methods end up in
        // one interface file instead of the last saga overwriting the first.
        java.util.Map<String, List<SagaStep>> globalStepsByBean = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.Map<String, MethodParam>> globalParamsByBean =
                new java.util.LinkedHashMap<>();
        for (SagaDefinition saga : sagas) {
            List<MethodParam> allSagaParams = new ArrayList<>(saga.getSagaMethodParams());
            allSagaParams.addAll(saga.getExtraLocalVars());
            for (SagaStep step : saga.getSteps()) {
                globalStepsByBean.computeIfAbsent(step.getBeanType(), k -> new ArrayList<>()).add(step);
                java.util.Map<String, MethodParam> paramMap = globalParamsByBean
                        .computeIfAbsent(step.getBeanType(), k -> new java.util.LinkedHashMap<>());
                // Deduplicate params by name across sagas (same name → same type in well-formed sagas)
                for (MethodParam p : allSagaParams) {
                    paramMap.put(p.getName(), p);
                }
            }
        }
        for (java.util.Map.Entry<String, List<SagaStep>> entry : globalStepsByBean.entrySet()) {
            List<MethodParam> mergedParams = new ArrayList<>(
                    globalParamsByBean.get(entry.getKey()).values());
            writeFile(packagePath(basePkg, "client"), entry.getKey() + "Client.java",
                    buildNetScopeClient(entry.getKey(), entry.getValue(), modules, mergedParams));
        }

        // ── Controller ───────────────────────────────────────────────────────
        writeFile(packagePath(basePkg, "controller"), "SagaController.java",
                buildSagaController(sagas));

        // ── Observability (OTel + correlation tracing) ───────────────────────
        writeFile(basePkg, "OtelConfig.java",               buildOtelConfig());
        writeFile(basePkg, "CorrelationTracingConfig.java", buildCorrelationTracingConfig());
        writeFile(basePkg, "TracingExclusionConfig.java",   buildTracingExclusionConfig());

        // ── README ────────────────────────────────────────────────────────────
        new DataReadmeGenerator().generateSagaOrchestratorReadme(sagas, modules, serviceRoot);

        log.info("Generated saga orchestrator: {} (HTTP :{})", ORCHESTRATOR_NAME, ORCHESTRATOR_PORT);
    }

    // =========================================================================
    // Infrastructure
    // =========================================================================

    private void writePom(Path serviceRoot, List<FractalModule> modules) throws IOException {
        // Collect unique participating services for NetScope client deps
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                         http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.fractalx.generated</groupId>
                    <artifactId>fractalx-saga-orchestrator</artifactId>
                    <version>1.0.0-SNAPSHOT</version>
                    <name>FractalX Saga Orchestrator</name>
                    <description>Auto-generated distributed saga orchestrator by FractalX</description>

                    <properties>
                        <java.version>17</java.version>
                        <spring-boot.version>3.2.0</spring-boot.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>

                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-dependencies</artifactId>
                                <version>${spring-boot.version}</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-jpa</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.fractalx</groupId>
                            <artifactId>netscope-client</artifactId>
                            <version>1.0.1</version>
                        </dependency>
                        <dependency>
                            <groupId>org.fractalx</groupId>
                            <artifactId>fractalx-runtime</artifactId>
                            <version>__FX_VERSION__</version>
                        </dependency>
                        <dependency>
                            <groupId>com.h2database</groupId>
                            <artifactId>h2</artifactId>
                            <scope>runtime</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.flywaydb</groupId>
                            <artifactId>flyway-core</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-actuator</artifactId>
                        </dependency>

                        <!-- Distributed tracing: Micrometer OTel bridge + OTLP gRPC exporter -->
                        <dependency>
                            <groupId>io.micrometer</groupId>
                            <artifactId>micrometer-tracing-bridge-otel</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>io.opentelemetry</groupId>
                            <artifactId>opentelemetry-exporter-otlp</artifactId>
                            <version>1.32.0</version>
                        </dependency>
                        <dependency>
                            <groupId>io.opentelemetry.semconv</groupId>
                            <artifactId>opentelemetry-semconv</artifactId>
                            <version>1.21.0-alpha</version>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.11.0</version>
                                <configuration>
                                    <parameters>true</parameters>
                                </configuration>
                            </plugin>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                                <version>${spring-boot.version}</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """);
        writeFile(serviceRoot, "pom.xml",
                sb.toString().replace("__FX_VERSION__", FractalxVersion.get()));
    }

    private void writeApplicationClass(Path srcMainJava) throws IOException {
        writeFile(packagePath(srcMainJava, BASE_PACKAGE),
                "SagaOrchestratorApplication.java",
                """
                package %s;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.ComponentScan;
                import org.springframework.scheduling.annotation.EnableScheduling;
                import org.springframework.web.client.RestTemplate;
                import org.fractalx.netscope.client.annotation.EnableNetScopeClient;

                /**
                 * FractalX Saga Orchestrator — Auto-Generated.
                 * Coordinates distributed sagas across decomposed microservices.
                 *
                 * <p>{@code org.fractalx.runtime} is included in the component scan so that
                 * FractalX runtime beans ({@code TraceFilter}, {@code NetScopeGrpcInterceptorConfigurer},
                 * {@code NetScopeContextInterceptor}) are registered and wire the correlation ID
                 * into outbound gRPC metadata automatically.
                 */
                @SpringBootApplication
                @EnableScheduling
                @ComponentScan(basePackages = {"%s", "org.fractalx.runtime"})
                @EnableNetScopeClient(basePackages = {"%s.client"})
                public class SagaOrchestratorApplication {
                    public static void main(String[] args) {
                        SpringApplication.run(SagaOrchestratorApplication.class, args);
                    }

                    @Bean
                    public RestTemplate restTemplate() {
                        return new RestTemplate();
                    }
                }
                """.formatted(BASE_PACKAGE, BASE_PACKAGE, BASE_PACKAGE));
    }

    private void writeApplicationYml(Path resourcesDir,
                                      List<FractalModule> modules,
                                      List<SagaDefinition> sagas) throws IOException {
        // Collect the set of service names that participate in any saga
        Set<String> participatingServices = new java.util.LinkedHashSet<>();
        for (SagaDefinition saga : sagas) {
            for (SagaStep step : saga.getSteps()) {
                participatingServices.add(step.getTargetServiceName());
            }
        }

        // Build the netscope.client.servers block with concrete gRPC ports
        StringBuilder serversBlock = new StringBuilder();
        for (String serviceName : participatingServices) {
            int grpcPort = modules.stream()
                    .filter(m -> m.getServiceName().equals(serviceName))
                    .map(m -> m.getPort() + 10000)
                    .findFirst()
                    .orElse(19000);
            serversBlock.append("      ").append(serviceName).append(":\n");
            serversBlock.append("        host: localhost\n");
            serversBlock.append("        port: ").append(grpcPort).append("\n");
        }

        // Build saga owner service callback URLs so the orchestrator can notify them on completion
        StringBuilder ownerUrlsBlock = new StringBuilder();
        for (SagaDefinition saga : sagas) {
            int ownerPort = modules.stream()
                    .filter(m -> m.getServiceName().equals(saga.getOwnerServiceName()))
                    .map(FractalModule::getPort)
                    .findFirst()
                    .orElse(8080);
            // 6-space indent: owner-urls is at 4 spaces, its children must be at 6
            ownerUrlsBlock.append("      ").append(saga.getSagaId()).append(": ")
                    .append("${").append(saga.getSagaId().toUpperCase().replace("-", "_"))
                    .append("_OWNER_URL:http://localhost:").append(ownerPort).append("}\n");
        }

        writeFile(resourcesDir, "application.yml",
                "spring:\n"
                + "  application:\n"
                + "    name: fractalx-saga-orchestrator\n"
                + "  datasource:\n"
                + "    url: jdbc:h2:mem:saga_db\n"
                + "    driver-class-name: org.h2.Driver\n"
                + "    username: sa\n"
                + "    password:\n"
                + "    hikari:\n"
                + "      maximum-pool-size: 5\n"
                + "      minimum-idle: 2\n"
                + "  jpa:\n"
                + "    hibernate:\n"
                + "      ddl-auto: validate\n"
                + "    show-sql: false\n"
                + "  h2:\n"
                + "    console:\n"
                + "      enabled: true\n"
                + "  flyway:\n"
                + "    enabled: true\n"
                + "    locations: classpath:db/migration\n"
                + "\n"
                + "server:\n"
                + "  port: 8099\n"
                + "\n"
                + "fractalx:\n"
                + "  observability:\n"
                + "    tracing: true\n"
                + "    metrics: true\n"
                + "    logger-url: ${FRACTALX_LOGGER_URL:http://localhost:9099/api/logs}\n"
                + "    otel:\n"
                + "      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}\n"
                + "  saga:\n"
                + "    owner-urls:\n"
                + ownerUrlsBlock
                + "\n"
                + "netscope:\n"
                + "  client:\n"
                + "    servers:\n"
                + serversBlock
                + "\n"
                + "management:\n"
                + "  endpoints:\n"
                + "    web:\n"
                + "      exposure:\n"
                + "        include: health,info,metrics,prometheus\n"
                + "  endpoint:\n"
                + "    health:\n"
                + "      show-details: always\n"
                + "  tracing:\n"
                + "    sampling:\n"
                + "      probability: 1.0\n"
                + "\n"
                + "logging:\n"
                + "  level:\n"
                + "    org.fractalx: INFO\n"
                + "    org.fractalx.netscope: DEBUG\n");
    }

    private void writeFlywayMigration(Path resourcesDir) throws IOException {
        Path migDir = resourcesDir.resolve("db/migration");
        Files.createDirectories(migDir);
        writeFile(migDir, "V1__init_saga.sql",
                """
                -- FractalX Saga Orchestrator — Initial Schema
                CREATE TABLE IF NOT EXISTS saga_instance (
                    id                           BIGINT AUTO_INCREMENT PRIMARY KEY,
                    saga_id                      VARCHAR(255) NOT NULL,
                    correlation_id               VARCHAR(36)  NOT NULL UNIQUE,
                    owner_service                VARCHAR(255),
                    status                       VARCHAR(50)  NOT NULL,
                    current_step                 VARCHAR(255),
                    payload                      TEXT,
                    error_message                TEXT,
                    started_at                   TIMESTAMP    NOT NULL,
                    updated_at                   TIMESTAMP,
                    owner_notified               BOOLEAN      NOT NULL DEFAULT FALSE,
                    notification_retry_count     INT          NOT NULL DEFAULT 0,
                    last_notification_attempt    TIMESTAMP
                );

                CREATE INDEX IF NOT EXISTS idx_saga_status          ON saga_instance (status);
                CREATE INDEX IF NOT EXISTS idx_saga_id              ON saga_instance (saga_id);
                CREATE INDEX IF NOT EXISTS idx_saga_corr            ON saga_instance (correlation_id);
                CREATE INDEX IF NOT EXISTS idx_saga_notify_pending  ON saga_instance (owner_notified, status);
                """);
    }

    // =========================================================================
    // Model classes
    // =========================================================================

    private String buildSagaStatus() {
        return """
                package %s.model;

                /**
                 * Lifecycle states of a saga execution instance.
                 * Generated by FractalX.
                 */
                public enum SagaStatus {
                    /** Saga was received and persisted, not yet executing. */
                    STARTED,
                    /** One or more steps have completed successfully. */
                    IN_PROGRESS,
                    /** All steps completed successfully. */
                    DONE,
                    /** A step failed; compensation is running in reverse order. */
                    COMPENSATING,
                    /** Saga completed with failure after compensation. */
                    FAILED
                }
                """.formatted(BASE_PACKAGE);
    }

    private String buildSagaInstance() {
        return """
                package %s.model;

                import jakarta.persistence.*;
                import java.time.LocalDateTime;

                /**
                 * Persisted state of a single saga execution.
                 * Generated by FractalX.
                 */
                @Entity
                @Table(name = "saga_instance")
                public class SagaInstance {

                    @Id
                    @GeneratedValue(strategy = GenerationType.IDENTITY)
                    private Long id;

                    /** Logical saga type identifier (from @DistributedSaga#sagaId). */
                    @Column(nullable = false)
                    private String sagaId;

                    /** Unique execution correlation ID (UUID). */
                    @Column(nullable = false, unique = true)
                    private String correlationId;

                    /** Service that initiated this saga. */
                    private String ownerService;

                    @Enumerated(EnumType.STRING)
                    @Column(nullable = false)
                    private SagaStatus status;

                    /** Name of the step currently executing (e.g., "payment-service:processPayment"). */
                    private String currentStep;

                    /** JSON-serialized input payload. */
                    @Column(columnDefinition = "TEXT")
                    private String payload;

                    /** Error message if the saga failed. */
                    @Column(columnDefinition = "TEXT")
                    private String errorMessage;

                    @Column(nullable = false)
                    private LocalDateTime startedAt;

                    private LocalDateTime updatedAt;

                    /** Whether the owner service has been successfully notified of the final outcome. */
                    @Column(nullable = false)
                    private boolean ownerNotified = false;

                    /** How many times notification has been attempted (capped at MAX_NOTIFICATION_RETRIES). */
                    @Column(nullable = false)
                    private int notificationRetryCount = 0;

                    /** Timestamp of the most recent notification attempt (null = never attempted). */
                    private LocalDateTime lastNotificationAttempt;

                    @PrePersist
                    protected void onCreate() { startedAt = LocalDateTime.now(); updatedAt = startedAt; }

                    @PreUpdate
                    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

                    // Getters and setters
                    public Long getId()                  { return id; }
                    public String getSagaId()            { return sagaId; }
                    public void   setSagaId(String v)    { sagaId = v; }
                    public String getCorrelationId()     { return correlationId; }
                    public void   setCorrelationId(String v) { correlationId = v; }
                    public String getOwnerService()      { return ownerService; }
                    public void   setOwnerService(String v)  { ownerService = v; }
                    public SagaStatus getStatus()        { return status; }
                    public void   setStatus(SagaStatus v){ status = v; }
                    public String getCurrentStep()       { return currentStep; }
                    public void   setCurrentStep(String v)   { currentStep = v; }
                    public String getPayload()           { return payload; }
                    public void   setPayload(String v)   { payload = v; }
                    public String getErrorMessage()      { return errorMessage; }
                    public void   setErrorMessage(String v)  { errorMessage = v; }
                    public LocalDateTime getStartedAt()  { return startedAt; }
                    public LocalDateTime getUpdatedAt()  { return updatedAt; }
                    public boolean isOwnerNotified()          { return ownerNotified; }
                    public void    setOwnerNotified(boolean v){ ownerNotified = v; }
                    public int  getNotificationRetryCount()   { return notificationRetryCount; }
                    public void setNotificationRetryCount(int v) { notificationRetryCount = v; }
                    public LocalDateTime getLastNotificationAttempt() { return lastNotificationAttempt; }
                    public void setLastNotificationAttempt(LocalDateTime v) { lastNotificationAttempt = v; }
                }
                """.formatted(BASE_PACKAGE);
    }

    private String buildRepository() {
        return """
                package %s.repository;

                import %s.model.SagaInstance;
                import %s.model.SagaStatus;
                import org.springframework.data.jpa.repository.JpaRepository;
                import java.util.List;
                import java.util.Optional;

                /**
                 * Repository for saga execution state.
                 * Generated by FractalX.
                 */
                public interface SagaInstanceRepository extends JpaRepository<SagaInstance, Long> {
                    Optional<SagaInstance> findByCorrelationId(String correlationId);
                    List<SagaInstance> findBySagaId(String sagaId);
                    List<SagaInstance> findByStatus(SagaStatus status);
                    /** Used by the notification retry poller to find sagas whose owner wasn't notified yet. */
                    List<SagaInstance> findByOwnerNotifiedFalseAndStatusIn(List<SagaStatus> statuses);
                }
                """.formatted(BASE_PACKAGE, BASE_PACKAGE, BASE_PACKAGE);
    }

    // =========================================================================
    // Per-saga service
    // =========================================================================

    private String buildSagaService(SagaDefinition saga) {
        String className    = saga.toClassName() + "SagaService";
        String payloadClass = saga.toClassName() + "Payload";
        List<MethodParam> params = saga.getSagaMethodParams();
        List<MethodParam> extraVars = saga.getExtraLocalVars();

        // Build a name→type lookup for resolving call-site args — includes extra local vars
        java.util.Map<String, String> paramTypeMap = new java.util.LinkedHashMap<>();
        for (MethodParam p : params) {
            paramTypeMap.put(p.getName(), p.getType());
        }
        for (MethodParam extra : extraVars) {
            paramTypeMap.put(extra.getName(), extra.getType());
        }

        // All payload fields = method params + extra local vars
        List<MethodParam> allPayloadFields = new ArrayList<>(params);
        allPayloadFields.addAll(extraVars);

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(BASE_PACKAGE).append(".service;\n\n");
        sb.append("import ").append(BASE_PACKAGE).append(".model.*;\n");
        sb.append("import ").append(BASE_PACKAGE).append(".repository.SagaInstanceRepository;\n");

        // Import each participating client
        Set<String> clientImports = saga.getSteps().stream()
                .map(s -> BASE_PACKAGE + ".client." + s.getBeanType() + "Client")
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        clientImports.forEach(imp -> sb.append("import ").append(imp).append(";\n"));

        sb.append("import com.fasterxml.jackson.databind.ObjectMapper;\n");
        sb.append("import org.slf4j.Logger;\n");
        sb.append("import org.slf4j.LoggerFactory;\n");
        sb.append("import org.slf4j.MDC;\n");
        sb.append("import org.springframework.beans.factory.annotation.Value;\n");
        sb.append("import org.springframework.http.HttpEntity;\n");
        sb.append("import org.springframework.http.HttpHeaders;\n");
        sb.append("import org.springframework.stereotype.Service;\n");
        sb.append("import org.springframework.transaction.annotation.Transactional;\n");
        sb.append("import org.springframework.web.client.RestTemplate;\n");
        sb.append("import java.util.UUID;\n");

        // Emit imports for non-trivial payload field types (both method params and extra vars)
        for (MethodParam p : allPayloadFields) {
            String fqn = javaImportFor(p.getType());
            if (fqn != null) sb.append("import ").append(fqn).append(";\n");
        }
        sb.append("\n");

        sb.append("/**\n");
        sb.append(" * Orchestrator for the '").append(saga.getSagaId()).append("' saga.\n");
        if (!saga.getDescription().isBlank()) {
            sb.append(" * ").append(saga.getDescription()).append("\n");
        }
        sb.append(" *\n * <p>Steps (in execution order):\n");
        for (SagaStep step : saga.getSteps()) {
            sb.append(" * <ol><li>").append(step.getTargetServiceName()).append(" → ")
              .append(step.getMethodName());
            if (step.hasCompensation()) {
                sb.append(" (compensate: ").append(step.getCompensationMethodName()).append(")");
            }
            sb.append("</li></ol>\n");
        }
        sb.append(" *\n * Auto-generated by FractalX.\n */\n");

        sb.append("@Service\n@Transactional\n");
        sb.append("public class ").append(className).append(" {\n\n");

        sb.append("    private static final Logger log = LoggerFactory.getLogger(")
          .append(className).append(".class);\n\n");

        // Fields
        Set<String> injectedClients = new java.util.LinkedHashSet<>();
        for (SagaStep step : saga.getSteps()) {
            String clientType = step.getBeanType() + "Client";
            String fieldName  = Character.toLowerCase(clientType.charAt(0)) + clientType.substring(1);
            if (injectedClients.add(clientType)) {
                sb.append("    private final ").append(clientType).append(" ").append(fieldName).append(";\n");
            }
        }
        sb.append("    private final SagaInstanceRepository sagaRepository;\n");
        sb.append("    private final ObjectMapper objectMapper;\n");
        sb.append("    private final RestTemplate restTemplate;\n\n");
        sb.append("    @Value(\"${fractalx.saga.owner-urls.")
          .append(saga.getSagaId()).append(":http://localhost:8080}\")\n");
        sb.append("    private String ownerServiceBaseUrl;\n\n");

        // Constructor
        sb.append("    public ").append(className).append("(");
        List<String> ctorParams = new ArrayList<>();
        Set<String> seenCtorTypes = new java.util.LinkedHashSet<>();
        for (SagaStep step : saga.getSteps()) {
            String clientType = step.getBeanType() + "Client";
            String fieldName  = Character.toLowerCase(clientType.charAt(0)) + clientType.substring(1);
            if (seenCtorTypes.add(clientType)) {
                ctorParams.add(clientType + " " + fieldName);
            }
        }
        ctorParams.add("SagaInstanceRepository sagaRepository");
        ctorParams.add("ObjectMapper objectMapper");
        ctorParams.add("RestTemplate restTemplate");
        sb.append(String.join(", ", ctorParams)).append(") {\n");
        Set<String> assigned = new java.util.LinkedHashSet<>();
        for (SagaStep step : saga.getSteps()) {
            String clientType = step.getBeanType() + "Client";
            String fieldName  = Character.toLowerCase(clientType.charAt(0)) + clientType.substring(1);
            if (assigned.add(fieldName)) {
                sb.append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n");
            }
        }
        sb.append("        this.sagaRepository = sagaRepository;\n");
        sb.append("        this.objectMapper = objectMapper;\n");
        sb.append("        this.restTemplate = restTemplate;\n");
        sb.append("    }\n\n");

        // start() method — deserializes payload, runs steps with typed args
        sb.append("    /**\n");
        sb.append("     * Starts a new saga execution.\n");
        sb.append("     * @param payload           JSON body matching {@link ").append(payloadClass).append("}\n");
        sb.append("     * @param incomingCorrelationId optional X-Correlation-Id from the caller;\n");
        sb.append("     *                          reused if present, otherwise a new UUID is generated\n");
        sb.append("     * @return correlationId to track this execution\n");
        sb.append("     */\n");
        sb.append("    public String start(String payload, String incomingCorrelationId) {\n");

        if (!params.isEmpty()) {
            sb.append("        ").append(payloadClass).append(" p;\n");
            sb.append("        try {\n");
            sb.append("            p = objectMapper.readValue(payload, ").append(payloadClass).append(".class);\n");
            sb.append("        } catch (Exception e) {\n");
            sb.append("            throw new IllegalArgumentException(\"Invalid payload for saga '")
              .append(saga.getSagaId()).append("': \" + e.getMessage(), e);\n");
            sb.append("        }\n\n");
        }

        sb.append("        // Reuse the incoming correlationId to keep the distributed trace intact,\n");
        sb.append("        // or generate a fresh UUID if the caller did not supply one.\n");
        sb.append("        String correlationId = (incomingCorrelationId != null && !incomingCorrelationId.isBlank())\n");
        sb.append("                ? incomingCorrelationId\n");
        sb.append("                : UUID.randomUUID().toString();\n\n");

        sb.append("        SagaInstance instance = new SagaInstance();\n");
        sb.append("        instance.setSagaId(\"").append(saga.getSagaId()).append("\");\n");
        sb.append("        instance.setCorrelationId(correlationId);\n");
        sb.append("        instance.setOwnerService(\"").append(saga.getOwnerServiceName()).append("\");\n");
        sb.append("        instance.setStatus(SagaStatus.STARTED);\n");
        sb.append("        instance.setPayload(payload);\n");
        sb.append("        sagaRepository.save(instance);\n\n");
        sb.append("        log.info(\"Starting saga '").append(saga.getSagaId())
          .append("' correlationId={}\", instance.getCorrelationId());\n\n");
        // Propagate the saga correlationId to all outbound gRPC calls via MDC
        sb.append("        MDC.put(\"correlationId\", instance.getCorrelationId());\n");
        sb.append("        // Tracks index of last step that completed — only those steps are compensated on failure\n");
        sb.append("        int lastCompletedStep = -1;\n");
        sb.append("        try {\n");
        sb.append("            instance.setStatus(SagaStatus.IN_PROGRESS);\n");

        for (int i = 0; i < saga.getSteps().size(); i++) {
            SagaStep step = saga.getSteps().get(i);
            String clientField = Character.toLowerCase(step.getBeanType().charAt(0))
                               + step.getBeanType().substring(1) + "Client";
            sb.append("\n            // Step ").append(i).append(": ")
              .append(step.getTargetServiceName()).append(" → ").append(step.getMethodName()).append("\n");
            sb.append("            instance.setCurrentStep(\"")
              .append(step.getTargetServiceName()).append(":").append(step.getMethodName()).append("\");\n");
            sb.append("            sagaRepository.save(instance);\n");
            sb.append("            ").append(clientField).append(".").append(step.getMethodName()).append("(");
            sb.append(buildCallArgs(step.getCallArguments(), paramTypeMap, params));
            sb.append(");\n");
            sb.append("            lastCompletedStep = ").append(i).append(";\n");
        }

        sb.append("\n            instance.setStatus(SagaStatus.DONE);\n");
        sb.append("            instance.setCurrentStep(null);\n");
        sb.append("            sagaRepository.save(instance);\n");
        sb.append("            log.info(\"Saga '").append(saga.getSagaId())
          .append("' completed successfully correlationId={}\", instance.getCorrelationId());\n\n");
        sb.append("            // Notify owner service so it can finalize business state\n");
        sb.append("            // (e.g. mark Order as CONFIRMED). Override via fractalx.saga.owner-urls.")
          .append(saga.getSagaId()).append(" property.\n");
        sb.append("            notifyOwnerComplete(instance);\n\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            log.error(\"Saga '").append(saga.getSagaId())
          .append("' failed at step={} lastCompleted={}\", instance.getCurrentStep(), lastCompletedStep, e);\n");
        sb.append("            instance.setErrorMessage(e.getMessage());\n");
        sb.append("            compensate(instance, lastCompletedStep");
        if (!params.isEmpty()) sb.append(", p");
        sb.append(");\n");
        sb.append("        } finally {\n");
        sb.append("            MDC.remove(\"correlationId\");\n");
        sb.append("        }\n\n");
        sb.append("        return instance.getCorrelationId();\n");
        sb.append("    }\n\n");

        // compensate() method — only compensates steps up to lastCompletedStep (inclusive) in reverse
        String compensateParam = params.isEmpty() ? "" : ", " + payloadClass + " p";
        sb.append("    private void compensate(SagaInstance instance, int lastCompletedStep")
          .append(compensateParam).append(") {\n");
        sb.append("        instance.setStatus(SagaStatus.COMPENSATING);\n");
        sb.append("        sagaRepository.save(instance);\n");
        sb.append("        MDC.put(\"correlationId\", instance.getCorrelationId());\n\n");
        sb.append("        // Compensation runs ONLY for steps that actually completed (lastCompletedStep={})\n");
        sb.append("        log.info(\"Compensating saga '").append(saga.getSagaId())
          .append("' — reverting {} completed step(s)\", lastCompletedStep + 1);\n\n");

        // Generate the compensation array (steps in reverse order)
        // We emit them in reverse source order but guard each with the step index check
        List<SagaStep> stepsForComp = new ArrayList<>(saga.getSteps());
        java.util.Collections.reverse(stepsForComp);
        for (int i = stepsForComp.size() - 1; i >= 0; i--) {
            SagaStep step = stepsForComp.get(i);
            // The "reversed" step at position i in reversed list is at index (size-1-i) in the original
            int originalIdx = stepsForComp.size() - 1 - i;
            if (step.hasCompensation()) {
                String clientField = Character.toLowerCase(step.getBeanType().charAt(0))
                                   + step.getBeanType().substring(1) + "Client";
                sb.append("        if (lastCompletedStep >= ").append(originalIdx).append(") {\n");
                sb.append("            try {\n");
                sb.append("                // Compensate step ").append(originalIdx).append(": ")
                  .append(step.getTargetServiceName()).append(" → ").append(step.getCompensationMethodName()).append("\n");
                sb.append("                ").append(clientField).append(".")
                  .append(step.getCompensationMethodName()).append("(");
                sb.append(buildCallArgs(step.getCallArguments(), paramTypeMap, params));
                sb.append(");\n");
                sb.append("                log.info(\"Compensated step ").append(originalIdx)
                  .append(" (").append(step.getCompensationMethodName()).append(")\");\n");
                sb.append("            } catch (Exception compensationEx) {\n");
                sb.append("                log.error(\"Compensation step '")
                  .append(step.getCompensationMethodName())
                  .append("' failed — manual intervention required\", compensationEx);\n");
                sb.append("            }\n");
                sb.append("        }\n");
            }
        }

        sb.append("\n        instance.setStatus(SagaStatus.FAILED);\n");
        sb.append("        sagaRepository.save(instance);\n");
        sb.append("        log.warn(\"Saga '").append(saga.getSagaId())
          .append("' compensated and marked FAILED correlationId={}\", instance.getCorrelationId());\n");
        sb.append("        // Notify owner service so it can handle the failure (e.g. mark Order as CANCELLED)\n");
        sb.append("        notifyOwnerFailed(instance);\n");
        sb.append("    }\n\n");

        // notifyOwnerComplete helper
        sb.append("    /**\n");
        sb.append("     * Notifies the owner service that the saga completed successfully.\n");
        sb.append("     * Calls {@code POST {ownerServiceBaseUrl}/internal/saga-complete/{correlationId}}\n");
        sb.append("     * with the full saga payload as the request body.\n");
        sb.append("     *\n");
        sb.append("     * <p>The owner service should implement\n");
        sb.append("     * {@code POST /internal/saga-complete/{correlationId}} to update its\n");
        sb.append("     * business state (e.g. mark Order as CONFIRMED). Failures are logged but\n");
        sb.append("     * do NOT roll back the saga — the saga itself is already DONE.\n");
        sb.append("     */\n");
        sb.append("    /**\n");
        sb.append("     * Notifies the owner service that the saga FAILED after compensation.\n");
        sb.append("     * Calls {@code POST {ownerServiceBaseUrl}/internal/saga-failed/{correlationId}}\n");
        sb.append("     * so the owner can revert its own state (e.g. cancel / delete the Order).\n");
        sb.append("     */\n");
        sb.append("    void notifyOwnerFailed(SagaInstance instance) {\n");
        sb.append("        String url = ownerServiceBaseUrl + \"/internal/saga-failed/\"\n");
        sb.append("                + instance.getCorrelationId();\n");
        sb.append("        try {\n");
        sb.append("            HttpHeaders headers = new HttpHeaders();\n");
        sb.append("            headers.set(\"Content-Type\", \"application/json\");\n");
        sb.append("            if (instance.getCorrelationId() != null) {\n");
        sb.append("                headers.set(\"X-Correlation-Id\", instance.getCorrelationId());\n");
        sb.append("            }\n");
        // Merge the saga payload (contains orderId, etc.) with errorMessage so the owner
        // service can identify and cancel the specific aggregate (e.g., Order).
        sb.append("            String sagaPayload = instance.getPayload() != null ? instance.getPayload() : \"{}\";\n");
        sb.append("            String escapedError = instance.getErrorMessage() != null\n");
        sb.append("                    ? instance.getErrorMessage().replace(\"\\\\\\\\\", \"\\\\\\\\\\\\\\\\\").replace(\"\\\"\", \"\\\\\\\"\") : \"\";\n");
        sb.append("            String failureBody = sagaPayload.endsWith(\"}\")\n");
        sb.append("                    ? sagaPayload.substring(0, sagaPayload.length() - 1)\n");
        sb.append("                            + \",\\\"errorMessage\\\":\\\"\" + escapedError + \"\\\"}\"\n");
        sb.append("                    : \"{\\\"errorMessage\\\":\\\"\" + escapedError + \"\\\"}\";\n");
        sb.append("            HttpEntity<String> request = new HttpEntity<>(failureBody, headers);\n");
        sb.append("            restTemplate.postForObject(url, request, String.class);\n");
        sb.append("            instance.setOwnerNotified(true);\n");
        sb.append("            sagaRepository.save(instance);\n");
        sb.append("            log.info(\"Notified owner service of saga FAILURE: url={} correlationId={}\",\n");
        sb.append("                    url, instance.getCorrelationId());\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            instance.setNotificationRetryCount(instance.getNotificationRetryCount() + 1);\n");
        sb.append("            instance.setLastNotificationAttempt(java.time.LocalDateTime.now());\n");
        sb.append("            sagaRepository.save(instance);\n");
        sb.append("            log.warn(\"Failed to notify owner service of saga failure: url={} attempt={} error={}\",\n");
        sb.append("                    url, instance.getNotificationRetryCount(), e.getMessage());\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("    void notifyOwnerComplete(SagaInstance instance) {\n");
        sb.append("        String url = ownerServiceBaseUrl + \"/internal/saga-complete/\"\n");
        sb.append("                + instance.getCorrelationId();\n");
        sb.append("        try {\n");
        sb.append("            HttpHeaders headers = new HttpHeaders();\n");
        sb.append("            headers.set(\"Content-Type\", \"application/json\");\n");
        sb.append("            if (instance.getCorrelationId() != null) {\n");
        sb.append("                headers.set(\"X-Correlation-Id\", instance.getCorrelationId());\n");
        sb.append("            }\n");
        sb.append("            HttpEntity<String> request = new HttpEntity<>(instance.getPayload(), headers);\n");
        sb.append("            restTemplate.postForObject(url, request, String.class);\n");
        sb.append("            instance.setOwnerNotified(true);\n");
        sb.append("            sagaRepository.save(instance);\n");
        sb.append("            log.info(\"Notified owner service of saga completion: url={} correlationId={}\",\n");
        sb.append("                    url, instance.getCorrelationId());\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            instance.setNotificationRetryCount(instance.getNotificationRetryCount() + 1);\n");
        sb.append("            instance.setLastNotificationAttempt(java.time.LocalDateTime.now());\n");
        sb.append("            sagaRepository.save(instance);\n");
        sb.append("            log.warn(\"Failed to notify owner service of saga completion: url={} attempt={} error={}\",\n");
        sb.append("                    url, instance.getNotificationRetryCount(), e.getMessage());\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        // Payload DTO record — mirrors the parent saga method's parameter list + extra local vars
        if (!allPayloadFields.isEmpty()) {
            sb.append("    /**\n");
            sb.append("     * Typed payload DTO for the '").append(saga.getSagaId()).append("' saga.\n");
            sb.append("     * Serialise this as JSON and POST to {@code /saga/")
              .append(saga.getSagaId()).append("/start}.\n");
            if (!extraVars.isEmpty()) {
                sb.append("     * Extra fields (local vars needed by steps): ")
                  .append(extraVars.stream().map(MethodParam::getName)
                          .collect(java.util.stream.Collectors.joining(", ")))
                  .append("\n");
            }
            sb.append("     */\n");
            sb.append("    public record ").append(payloadClass).append("(\n");
            List<String> recordComponents = new ArrayList<>();
            for (MethodParam mp : allPayloadFields) {
                recordComponents.add("            " + mp.getType() + " " + mp.getName());
            }
            sb.append(String.join(",\n", recordComponents));
            sb.append("\n    ) {}\n");
        }

        // retryPendingNotifications — @Scheduled poller that retries owner notifications
        // that failed during the initial attempt (e.g. owner service was down).
        sb.append("    private static final int MAX_NOTIFICATION_RETRIES = 10;\n\n");
        sb.append("    /**\n");
        sb.append("     * Retries owner-service notifications that failed during the initial attempt.\n");
        sb.append("     *\n");
        sb.append("     * <p>Runs every 2 seconds. Finds saga instances in DONE or FAILED state whose\n");
        sb.append("     * {@code ownerNotified} flag is still {@code false} (meaning the HTTP callback\n");
        sb.append("     * to the owner service has not succeeded yet). Retries up to\n");
        sb.append("     * {@code MAX_NOTIFICATION_RETRIES} times, after which the failure is logged\n");
        sb.append("     * as a dead-letter requiring manual intervention.\n");
        sb.append("     */\n");
        sb.append("    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 2000)\n");
        sb.append("    @Transactional\n");
        sb.append("    public void retryPendingNotifications() {\n");
        sb.append("        java.util.List<SagaInstance> pending = sagaRepository\n");
        sb.append("                .findByOwnerNotifiedFalseAndStatusIn(\n");
        sb.append("                        java.util.List.of(SagaStatus.DONE, SagaStatus.FAILED));\n");
        sb.append("        if (pending.isEmpty()) return;\n\n");
        sb.append("        for (SagaInstance instance : pending) {\n");
        sb.append("            if (instance.getNotificationRetryCount() >= MAX_NOTIFICATION_RETRIES) {\n");
        sb.append("                log.error(\"Saga notification dead-letter: correlationId={} sagaId={} status={} \"\n");
        sb.append("                        + \"— exceeded {} retries. Manual intervention required.\",\n");
        sb.append("                        instance.getCorrelationId(), instance.getSagaId(),\n");
        sb.append("                        instance.getStatus(), MAX_NOTIFICATION_RETRIES);\n");
        sb.append("                continue;\n");
        sb.append("            }\n");
        sb.append("            if (instance.getStatus() == SagaStatus.DONE) {\n");
        sb.append("                notifyOwnerComplete(instance);\n");
        sb.append("            } else {\n");
        sb.append("                notifyOwnerFailed(instance);\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Builds the argument list string for a step call.
     * If args were captured from the call site, emits {@code p.argName()} accessors.
     * Falls back to positional params from the saga method signature if no args were captured.
     */
    private String buildCallArgs(List<String> callArgs,
                                  java.util.Map<String, String> paramTypeMap,
                                  List<MethodParam> sagaParams) {
        if (callArgs.isEmpty()) {
            // No captured args — emit positional references to all saga params
            if (sagaParams.isEmpty()) return "/* TODO: add parameters */";
            return sagaParams.stream()
                    .map(p -> "p." + p.getName() + "()")
                    .collect(java.util.stream.Collectors.joining(", "));
        }
        // Map each captured arg expression: if it's a known param name, use p.name(), else emit as-is
        return callArgs.stream()
                .map(arg -> paramTypeMap.containsKey(arg) ? "p." + arg + "()" : arg)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * Returns the fully-qualified import for a simple Java type name, or null
     * if the type doesn't need an explicit import (primitive wrappers, String, etc.).
     */
    private String javaImportFor(String simpleType) {
        return switch (simpleType) {
            case "BigDecimal"     -> "java.math.BigDecimal";
            case "LocalDate"      -> "java.time.LocalDate";
            case "LocalDateTime"  -> "java.time.LocalDateTime";
            case "ZonedDateTime"  -> "java.time.ZonedDateTime";
            case "UUID"           -> "java.util.UUID";
            case "List"           -> "java.util.List";
            case "Map"            -> "java.util.Map";
            default               -> null; // String, Long, Integer, Double, Boolean — no import needed
        };
    }

    // =========================================================================
    // NetScope client in orchestrator
    // =========================================================================

    private String buildNetScopeClient(String beanType,
                                        List<SagaStep> steps,
                                        List<FractalModule> modules,
                                        List<MethodParam> sagaMethodParams) {
        String targetServiceName = steps.get(0).getTargetServiceName();
        int grpcPort = modules.stream()
                .filter(m -> m.getServiceName().equals(targetServiceName))
                .map(m -> m.getPort() + 10000)
                .findFirst()
                .orElse(19000);

        // Build name→type lookup from the parent saga method's params
        java.util.Map<String, String> paramTypeMap = new java.util.LinkedHashMap<>();
        for (MethodParam p : sagaMethodParams) {
            paramTypeMap.put(p.getName(), p.getType());
        }

        // Emit one typed method declaration per unique forward + compensation method
        Set<String> seen = new java.util.LinkedHashSet<>();
        StringBuilder methods = new StringBuilder();
        for (SagaStep step : steps) {
            if (seen.add(step.getMethodName())) {
                methods.append("\n    // Forward step\n");
                methods.append("    void ").append(step.getMethodName()).append("(");
                methods.append(buildTypedParamList(step.getCallArguments(), paramTypeMap, sagaMethodParams));
                methods.append(");\n");
            }
            if (step.hasCompensation() && seen.add(step.getCompensationMethodName())) {
                methods.append("\n    // Compensation for ").append(step.getMethodName()).append("\n");
                methods.append("    void ").append(step.getCompensationMethodName()).append("(");
                // Compensation uses same params as the forward step
                methods.append(buildTypedParamList(step.getCallArguments(), paramTypeMap, sagaMethodParams));
                methods.append(");\n");
            }
        }

        // Build the full imports block: NetScopeClient + any standard-library types used in
        // method signatures (BigDecimal, LocalDate, UUID, etc.).
        StringBuilder importsB = new StringBuilder();
        importsB.append("import org.fractalx.netscope.client.annotation.NetScopeClient;\n");
        Set<String> addedFqns = new java.util.LinkedHashSet<>();
        for (MethodParam mp : sagaMethodParams) {
            for (String token : mp.getType().replaceAll("[<>,?\\[\\]]", " ").split("\\s+")) {
                token = token.trim();
                if (token.isEmpty()) continue;
                String fqn = javaImportFor(token);
                if (fqn != null && addedFqns.add(fqn)) {
                    importsB.append("import ").append(fqn).append(";\n");
                }
            }
        }
        // stripTrailing so the template's blank line before /** is the only separator
        String importsStr = importsB.toString().stripTrailing();

        return """
                package %s.client;

                %s

                /**
                 * NetScope client for %s.
                 * Connects to %s on gRPC port %d.
                 * Generated by FractalX Saga Orchestrator Generator.
                 */
                @NetScopeClient(server = "%s", beanName = "%s")
                public interface %sClient {
                %s
                }
                """.formatted(
                BASE_PACKAGE,
                importsStr,
                beanType, targetServiceName, grpcPort,
                targetServiceName, beanType,
                beanType,
                methods
        );
    }

    /**
     * Builds a typed parameter list string for a method declaration.
     * Resolves each argument name to its type from the parent saga method params.
     * Example: {@code ["productId", "quantity"]} → {@code "Long productId, Integer quantity"}.
     */
    private String buildTypedParamList(List<String> argNames,
                                        java.util.Map<String, String> paramTypeMap,
                                        List<MethodParam> sagaParams) {
        if (argNames.isEmpty()) {
            if (sagaParams.isEmpty()) return "";
            return sagaParams.stream()
                    .map(p -> p.getType() + " " + p.getName())
                    .collect(java.util.stream.Collectors.joining(", "));
        }
        List<String> parts = new ArrayList<>();
        for (String arg : argNames) {
            String type = paramTypeMap.getOrDefault(arg, "Object /* unknown type */");
            parts.add(type + " " + arg);
        }
        return String.join(", ", parts);
    }

    // =========================================================================
    // Controller
    // =========================================================================

    private String buildSagaController(List<SagaDefinition> sagas) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(BASE_PACKAGE).append(".controller;\n\n");
        sb.append("import ").append(BASE_PACKAGE).append(".model.*;\n");
        sb.append("import ").append(BASE_PACKAGE).append(".repository.SagaInstanceRepository;\n");

        for (SagaDefinition saga : sagas) {
            sb.append("import ").append(BASE_PACKAGE).append(".service.")
              .append(saga.toClassName()).append("SagaService;\n");
        }

        sb.append("""
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                import java.util.List;
                import java.util.Map;
                import org.slf4j.MDC;

                /**
                 * REST API for saga orchestration.
                 * Generated by FractalX.
                 */
                @RestController
                @RequestMapping("/saga")
                public class SagaController {

                """);

        for (SagaDefinition saga : sagas) {
            String svcField = Character.toLowerCase(saga.toClassName().charAt(0))
                            + saga.toClassName().substring(1) + "SagaService";
            sb.append("    private final ").append(saga.toClassName()).append("SagaService ")
              .append(svcField).append(";\n");
        }
        sb.append("    private final SagaInstanceRepository sagaRepository;\n\n");

        // Constructor
        sb.append("    public SagaController(");
        List<String> params = new ArrayList<>();
        for (SagaDefinition saga : sagas) {
            String svcType  = saga.toClassName() + "SagaService";
            String svcField = Character.toLowerCase(saga.toClassName().charAt(0))
                            + saga.toClassName().substring(1) + "SagaService";
            params.add(svcType + " " + svcField);
        }
        params.add("SagaInstanceRepository sagaRepository");
        sb.append(String.join(", ", params)).append(") {\n");
        for (SagaDefinition saga : sagas) {
            String svcField = Character.toLowerCase(saga.toClassName().charAt(0))
                            + saga.toClassName().substring(1) + "SagaService";
            sb.append("        this.").append(svcField).append(" = ").append(svcField).append(";\n");
        }
        sb.append("        this.sagaRepository = sagaRepository;\n");
        sb.append("    }\n\n");

        // Per-saga start endpoint
        for (SagaDefinition saga : sagas) {
            String svcField = Character.toLowerCase(saga.toClassName().charAt(0))
                            + saga.toClassName().substring(1) + "SagaService";
            sb.append("    /** Start '").append(saga.getSagaId()).append("' saga. */\n");
            sb.append("    @PostMapping(\"/").append(saga.getSagaId()).append("/start\")\n");
            sb.append("    public ResponseEntity<Map<String, String>> start").append(saga.toClassName())
              .append("(@RequestBody String payload,\n");
            sb.append("            @RequestHeader(value = \"X-Correlation-Id\", required = false)")
              .append(" String incomingCorrelationId) {\n");
            sb.append("        if (incomingCorrelationId != null && !incomingCorrelationId.isBlank()) {\n");
            sb.append("            MDC.put(\"correlationId\", incomingCorrelationId);\n");
            sb.append("        }\n");
            sb.append("        String correlationId = ").append(svcField)
              .append(".start(payload, incomingCorrelationId);\n");
            sb.append("        return ResponseEntity.accepted().body(Map.of(\"correlationId\", correlationId));\n");
            sb.append("    }\n\n");
        }

        // Status endpoint
        sb.append("    /** Get status of any saga instance by correlationId. */\n");
        sb.append("    @GetMapping(\"/status/{correlationId}\")\n");
        sb.append("    public ResponseEntity<SagaInstance> status(@PathVariable String correlationId) {\n");
        sb.append("        return sagaRepository.findByCorrelationId(correlationId)\n");
        sb.append("                .map(ResponseEntity::ok)\n");
        sb.append("                .orElse(ResponseEntity.notFound().build());\n");
        sb.append("    }\n\n");

        // List all
        sb.append("    /** List all saga instances. */\n");
        sb.append("    @GetMapping\n");
        sb.append("    public List<SagaInstance> listAll() {\n");
        sb.append("        return sagaRepository.findAll();\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    // =========================================================================
    // Observability helpers
    // =========================================================================

    private String buildOtelConfig() {
        return """
                package org.fractalx.generated.sagaorchestrator;

                import io.opentelemetry.api.OpenTelemetry;
                import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
                import io.opentelemetry.api.common.Attributes;
                import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
                import io.opentelemetry.context.propagation.ContextPropagators;
                import io.opentelemetry.context.propagation.TextMapPropagator;
                import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
                import io.opentelemetry.sdk.OpenTelemetrySdk;
                import io.opentelemetry.sdk.resources.Resource;
                import io.opentelemetry.sdk.trace.SdkTracerProvider;
                import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
                import io.opentelemetry.semconv.ResourceAttributes;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                @Configuration
                public class OtelConfig {

                    @Bean
                    @ConditionalOnMissingBean(OpenTelemetry.class)
                    public OpenTelemetry openTelemetry(
                            @Value("${fractalx.observability.otel.endpoint:http://localhost:4317}") String endpoint) {

                        Resource resource = Resource.getDefault()
                                .merge(Resource.create(Attributes.of(
                                        ResourceAttributes.SERVICE_NAME, "fractalx-saga-orchestrator")));

                        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                                .setEndpoint(endpoint)
                                .build();

                        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                                .setResource(resource)
                                .build();

                        return OpenTelemetrySdk.builder()
                                .setTracerProvider(tracerProvider)
                                .setPropagators(ContextPropagators.create(
                                        TextMapPropagator.composite(
                                                W3CTraceContextPropagator.getInstance(),
                                                W3CBaggagePropagator.getInstance())))
                                .buildAndRegisterGlobal();
                    }
                }
                """;
    }

    private String buildCorrelationTracingConfig() {
        return """
                package org.fractalx.generated.sagaorchestrator;

                import io.micrometer.tracing.Tracer;
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.servlet.http.HttpServletResponse;
                import org.slf4j.MDC;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.lang.NonNull;
                import org.springframework.web.servlet.HandlerInterceptor;
                import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
                import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

                @Configuration
                public class CorrelationTracingConfig implements WebMvcConfigurer {

                    private final Tracer tracer;

                    public CorrelationTracingConfig(Tracer tracer) {
                        this.tracer = tracer;
                    }

                    @Override
                    public void addInterceptors(@NonNull InterceptorRegistry registry) {
                        registry.addInterceptor(new HandlerInterceptor() {
                            @Override
                            public boolean preHandle(@NonNull HttpServletRequest request,
                                                     @NonNull HttpServletResponse response,
                                                     @NonNull Object handler) {
                                String correlationId = MDC.get("correlationId");
                                if (correlationId != null && !correlationId.isBlank()) {
                                    io.micrometer.tracing.Span span = tracer.currentSpan();
                                    if (span != null) {
                                        span.tag("correlation.id", correlationId);
                                    }
                                }
                                return true;
                            }
                        });
                    }
                }
                """;
    }

    private String buildTracingExclusionConfig() {
        return """
                package org.fractalx.generated.sagaorchestrator;

                import io.micrometer.observation.ObservationPredicate;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.http.server.observation.ServerRequestObservationContext;

                @Configuration
                public class TracingExclusionConfig {

                    @Bean
                    public ObservationPredicate noActuatorTracing() {
                        return (name, context) -> {
                            if (context instanceof ServerRequestObservationContext sroc) {
                                return !sroc.getCarrier().getRequestURI().startsWith("/actuator");
                            }
                            return true;
                        };
                    }

                    @Bean
                    public ObservationPredicate noScheduledTaskTracing() {
                        return (name, context) -> !name.startsWith("tasks.scheduled");
                    }
                }
                """;
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private Path packagePath(Path root, String packageName) {
        Path p = root;
        for (String part : packageName.split("\\.")) {
            p = p.resolve(part);
        }
        return p;
    }

    private void writeFile(Path dir, String filename, String content) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(filename), content);
    }
}
