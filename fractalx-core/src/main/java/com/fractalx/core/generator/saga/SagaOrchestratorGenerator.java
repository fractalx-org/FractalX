package com.fractalx.core.generator.saga;

import com.fractalx.core.model.FractalModule;
import com.fractalx.core.model.SagaDefinition;
import com.fractalx.core.model.SagaStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
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
    private static final String BASE_PACKAGE         = "com.fractalx.generated.sagaorchestrator";
    private static final String FRACTALX_RUNTIME_VER = "0.2.0-SNAPSHOT";

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
        writeApplicationYml(resourcesDir);
        writeFlywayMigration(resourcesDir);

        // ── Shared model ─────────────────────────────────────────────────────
        writeFile(packagePath(basePkg, "model"), "SagaStatus.java",    buildSagaStatus());
        writeFile(packagePath(basePkg, "model"), "SagaInstance.java",  buildSagaInstance());
        writeFile(packagePath(basePkg, "repository"), "SagaInstanceRepository.java", buildRepository());

        // ── Per-saga service and NetScope clients ────────────────────────────
        for (SagaDefinition saga : sagas) {
            String serviceClass = saga.toClassName() + "SagaService";
            writeFile(packagePath(basePkg, "service"), serviceClass + ".java",
                    buildSagaService(saga));

            // Generate NetScope client interface for each participating service
            for (SagaStep step : saga.getSteps()) {
                String clientName = step.getBeanType() + "Client";
                writeFile(packagePath(basePkg, "client"), clientName + ".java",
                        buildNetScopeClient(step, modules));
            }
        }

        // ── Controller ───────────────────────────────────────────────────────
        writeFile(packagePath(basePkg, "controller"), "SagaController.java",
                buildSagaController(sagas));

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
                    <groupId>com.fractalx.generated</groupId>
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
                            <version>1.0.0</version>
                        </dependency>
                        <dependency>
                            <groupId>com.fractalx</groupId>
                            <artifactId>fractalx-runtime</artifactId>
                            <version>""").append(FRACTALX_RUNTIME_VER).append("""
                        </version>
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
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                                <version>${spring-boot.version}</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """);
        writeFile(serviceRoot, "pom.xml", sb.toString());
    }

    private void writeApplicationClass(Path srcMainJava) throws IOException {
        writeFile(packagePath(srcMainJava, BASE_PACKAGE),
                "SagaOrchestratorApplication.java",
                """
                package %s;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                import org.springframework.scheduling.annotation.EnableScheduling;
                import org.fractalx.netscope.client.annotation.EnableNetScopeClient;

                /**
                 * FractalX Saga Orchestrator — Auto-Generated.
                 * Coordinates distributed sagas across decomposed microservices.
                 */
                @SpringBootApplication
                @EnableScheduling
                @EnableNetScopeClient(basePackages = {"%s.client"})
                public class SagaOrchestratorApplication {
                    public static void main(String[] args) {
                        SpringApplication.run(SagaOrchestratorApplication.class, args);
                    }
                }
                """.formatted(BASE_PACKAGE, BASE_PACKAGE));
    }

    private void writeApplicationYml(Path resourcesDir) throws IOException {
        writeFile(resourcesDir, "application.yml",
                """
                spring:
                  application:
                    name: fractalx-saga-orchestrator
                  datasource:
                    url: jdbc:h2:mem:saga_db
                    driver-class-name: org.h2.Driver
                    username: sa
                    password:
                    hikari:
                      maximum-pool-size: 5
                      minimum-idle: 2
                  jpa:
                    hibernate:
                      ddl-auto: update
                    show-sql: false
                  h2:
                    console:
                      enabled: true
                  flyway:
                    enabled: true
                    locations: classpath:db/migration

                server:
                  port: 8099

                netscope:
                  client:
                    # TODO: Add participating service gRPC addresses here.
                    # Format:  servers.<service-name>.host / port
                    # Example:
                    #   servers:
                    #     payment-service:
                    #       host: localhost
                    #       port: 18082

                logging:
                  level:
                    com.fractalx: INFO
                    org.fractalx.netscope: DEBUG
                """);
    }

    private void writeFlywayMigration(Path resourcesDir) throws IOException {
        Path migDir = resourcesDir.resolve("db/migration");
        Files.createDirectories(migDir);
        writeFile(migDir, "V1__init_saga.sql",
                """
                -- FractalX Saga Orchestrator — Initial Schema
                CREATE TABLE IF NOT EXISTS saga_instance (
                    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
                    saga_id         VARCHAR(255) NOT NULL,
                    correlation_id  VARCHAR(36)  NOT NULL UNIQUE,
                    owner_service   VARCHAR(255),
                    status          VARCHAR(50)  NOT NULL,
                    current_step    VARCHAR(255),
                    payload         TEXT,
                    error_message   TEXT,
                    started_at      TIMESTAMP    NOT NULL,
                    updated_at      TIMESTAMP
                );

                CREATE INDEX IF NOT EXISTS idx_saga_status     ON saga_instance (status);
                CREATE INDEX IF NOT EXISTS idx_saga_id         ON saga_instance (saga_id);
                CREATE INDEX IF NOT EXISTS idx_saga_corr       ON saga_instance (correlation_id);
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
                }
                """.formatted(BASE_PACKAGE, BASE_PACKAGE, BASE_PACKAGE);
    }

    // =========================================================================
    // Per-saga service
    // =========================================================================

    private String buildSagaService(SagaDefinition saga) {
        String className = saga.toClassName() + "SagaService";
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(BASE_PACKAGE).append(".service;\n\n");
        sb.append("import ").append(BASE_PACKAGE).append(".model.*;\n");
        sb.append("import ").append(BASE_PACKAGE).append(".repository.SagaInstanceRepository;\n");

        // Import each participating client
        Set<String> clientImports = saga.getSteps().stream()
                .map(s -> BASE_PACKAGE + ".client." + s.getBeanType() + "Client")
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        clientImports.forEach(imp -> sb.append("import ").append(imp).append(";\n"));

        sb.append("""
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;
                import java.util.UUID;

                """);

        sb.append("/**\n");
        sb.append(" * Orchestrator for the '").append(saga.getSagaId()).append("' saga.\n");
        if (!saga.getDescription().isBlank()) {
            sb.append(" * ").append(saga.getDescription()).append("\n");
        }
        sb.append(" *\n");
        sb.append(" * <p>Steps (in execution order):\n");
        for (SagaStep step : saga.getSteps()) {
            sb.append(" * <ol><li>").append(step.getTargetServiceName()).append(" → ")
              .append(step.getMethodName());
            if (step.hasCompensation()) {
                sb.append(" (compensate: ").append(step.getCompensationMethodName()).append(")");
            }
            sb.append("</li></ol>\n");
        }
        sb.append(" *\n * Auto-generated by FractalX.\n */\n");

        sb.append("@Service\n");
        sb.append("@Transactional\n");
        sb.append("public class ").append(className).append(" {\n\n");

        sb.append("    private static final Logger log = LoggerFactory.getLogger(")
          .append(className).append(".class);\n\n");

        // Field injection for each unique client
        Set<String> injectedClients = new java.util.LinkedHashSet<>();
        for (SagaStep step : saga.getSteps()) {
            String clientType = step.getBeanType() + "Client";
            String fieldName  = Character.toLowerCase(clientType.charAt(0)) + clientType.substring(1);
            if (injectedClients.add(clientType)) {
                sb.append("    private final ").append(clientType).append(" ").append(fieldName).append(";\n");
            }
        }
        sb.append("    private final SagaInstanceRepository sagaRepository;\n\n");

        // Constructor
        sb.append("    public ").append(className).append("(");
        List<String> ctorParams = new ArrayList<>();
        for (SagaStep step : saga.getSteps()) {
            String clientType = step.getBeanType() + "Client";
            String fieldName  = Character.toLowerCase(clientType.charAt(0)) + clientType.substring(1);
            ctorParams.add(clientType + " " + fieldName);
        }
        ctorParams.add("SagaInstanceRepository sagaRepository");
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
        sb.append("    }\n\n");

        // start() method
        sb.append("    /**\n");
        sb.append("     * Starts a new saga execution.\n");
        sb.append("     * @param payload JSON-serialized input (caller responsibility to serialize)\n");
        sb.append("     * @return correlationId to track this execution\n");
        sb.append("     */\n");
        sb.append("    public String start(String payload) {\n");
        sb.append("        SagaInstance instance = new SagaInstance();\n");
        sb.append("        instance.setSagaId(\"").append(saga.getSagaId()).append("\");\n");
        sb.append("        instance.setCorrelationId(UUID.randomUUID().toString());\n");
        sb.append("        instance.setOwnerService(\"").append(saga.getOwnerServiceName()).append("\");\n");
        sb.append("        instance.setStatus(SagaStatus.STARTED);\n");
        sb.append("        instance.setPayload(payload);\n");
        sb.append("        sagaRepository.save(instance);\n\n");
        sb.append("        log.info(\"Starting saga '").append(saga.getSagaId())
          .append("' correlationId={}\", instance.getCorrelationId());\n\n");
        sb.append("        try {\n");
        sb.append("            instance.setStatus(SagaStatus.IN_PROGRESS);\n");

        // Forward steps
        for (SagaStep step : saga.getSteps()) {
            String clientField = Character.toLowerCase(step.getBeanType().charAt(0))
                               + step.getBeanType().substring(1) + "Client";
            sb.append("\n            // Step: ").append(step.getTargetServiceName())
              .append(" → ").append(step.getMethodName()).append("\n");
            sb.append("            instance.setCurrentStep(\"")
              .append(step.getTargetServiceName()).append(":").append(step.getMethodName()).append("\");\n");
            sb.append("            sagaRepository.save(instance);\n");
            sb.append("            // TODO: map payload to actual method parameters\n");
            sb.append("            ").append(clientField).append(".").append(step.getMethodName())
              .append("(/* TODO: parameters from payload */);\n");
        }

        sb.append("\n            instance.setStatus(SagaStatus.DONE);\n");
        sb.append("            instance.setCurrentStep(null);\n");
        sb.append("            sagaRepository.save(instance);\n");
        sb.append("            log.info(\"Saga '").append(saga.getSagaId())
          .append("' completed successfully correlationId={}\", instance.getCorrelationId());\n\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            log.error(\"Saga '").append(saga.getSagaId())
          .append("' failed at step={}\", instance.getCurrentStep(), e);\n");
        sb.append("            instance.setErrorMessage(e.getMessage());\n");
        sb.append("            compensate(instance);\n");
        sb.append("        }\n\n");
        sb.append("        return instance.getCorrelationId();\n");
        sb.append("    }\n\n");

        // compensate() method
        sb.append("    private void compensate(SagaInstance instance) {\n");
        sb.append("        instance.setStatus(SagaStatus.COMPENSATING);\n");
        sb.append("        sagaRepository.save(instance);\n\n");
        sb.append("        // Compensation runs in reverse step order\n");

        List<SagaStep> reversed = new ArrayList<>(saga.getSteps());
        java.util.Collections.reverse(reversed);
        for (SagaStep step : reversed) {
            if (step.hasCompensation()) {
                String clientField = Character.toLowerCase(step.getBeanType().charAt(0))
                                   + step.getBeanType().substring(1) + "Client";
                sb.append("        try {\n");
                sb.append("            // Compensate: ").append(step.getTargetServiceName())
                  .append(" → ").append(step.getCompensationMethodName()).append("\n");
                sb.append("            ").append(clientField).append(".")
                  .append(step.getCompensationMethodName()).append("(/* TODO: parameters */);\n");
                sb.append("        } catch (Exception compensationEx) {\n");
                sb.append("            log.error(\"Compensation step '")
                  .append(step.getCompensationMethodName())
                  .append("' failed — manual intervention may be needed\", compensationEx);\n");
                sb.append("        }\n\n");
            }
        }

        sb.append("        instance.setStatus(SagaStatus.FAILED);\n");
        sb.append("        sagaRepository.save(instance);\n");
        sb.append("        log.warn(\"Saga '").append(saga.getSagaId())
          .append("' compensated and marked FAILED correlationId={}\", instance.getCorrelationId());\n");
        sb.append("    }\n}\n");

        return sb.toString();
    }

    // =========================================================================
    // NetScope client in orchestrator
    // =========================================================================

    private String buildNetScopeClient(SagaStep step, List<FractalModule> modules) {
        int grpcPort = modules.stream()
                .filter(m -> m.getServiceName().equals(step.getTargetServiceName()))
                .map(m -> m.getPort() + 10000)
                .findFirst()
                .orElse(19000);

        return """
                package %s.client;

                import org.fractalx.netscope.client.annotation.NetScopeClient;

                /**
                 * NetScope client for %s.
                 * Connects to %s on gRPC port %d.
                 * Generated by FractalX Saga Orchestrator Generator.
                 *
                 * TODO: Add method signatures matching the %s bean's public methods.
                 */
                @NetScopeClient(server = "%s", beanName = "%s")
                public interface %sClient {

                    // TODO: Declare methods matching the remote %s bean.
                    // Example:
                    //   boolean %s(/* parameters */);

                }
                """.formatted(
                BASE_PACKAGE,
                step.getBeanType(), step.getTargetServiceName(), grpcPort,
                step.getBeanType(),
                step.getTargetServiceName(), step.getBeanType(),
                step.getBeanType(),
                step.getBeanType(),
                step.getMethodName()
        );
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
            sb.append("    public ResponseEntity<Map<String, String>> start")
              .append(saga.toClassName()).append("(@RequestBody String payload) {\n");
            sb.append("        String correlationId = ").append(svcField).append(".start(payload);\n");
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
