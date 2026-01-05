package com.fractalx.core.observability;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * RESEARCH COMPONENT: Centralized Log Aggregation Service.
 * Generates a standalone service that ingests logs from all microservices.
 */
public class LoggerServiceGenerator {

    private static final Logger log = LoggerFactory.getLogger(LoggerServiceGenerator.class);
    private static final String SERVICE_NAME = "logger-service";
    private static final int PORT = 9099;

    public void generate(Path outputRoot) throws IOException {
        log.info("Generating Centralized Logger Service...");

        Path serviceRoot = outputRoot.resolve(SERVICE_NAME);
        Path srcMainJava = serviceRoot.resolve("src/main/java/com/fractalx/logger");
        Path srcMainResources = serviceRoot.resolve("src/main/resources");

        Files.createDirectories(srcMainJava);
        Files.createDirectories(srcMainResources);

        generatePom(serviceRoot);
        generateApplication(srcMainJava);
        generateController(srcMainJava);
        generateRepository(srcMainJava); // Simple in-memory storage for MVP
        generateConfig(srcMainResources);

        log.info("✓ Generated Logger Service on port {}", PORT);
    }

    private void generatePom(Path root) throws IOException {
        String content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
            
                <groupId>com.fractalx.generated</groupId>
                <artifactId>logger-service</artifactId>
                <version>1.0.0-SNAPSHOT</version>
            
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.0</version>
                </parent>
            
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <optional>true</optional>
                    </dependency>
                </dependencies>
            </project>
            """;
        Files.writeString(root.resolve("pom.xml"), content);
    }

    private void generateConfig(Path resources) throws IOException {
        String content = """
            server:
              port: %d
            spring:
              application:
                name: logger-service
            """.formatted(PORT);
        Files.writeString(resources.resolve("application.yml"), content);
    }

    private void generateApplication(Path javaPath) throws IOException {
        String content = """
            package com.fractalx.logger;
            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            @SpringBootApplication
            public class LoggerApplication {
                public static void main(String[] args) {
                    SpringApplication.run(LoggerApplication.class, args);
                }
            }
            """;
        Files.writeString(javaPath.resolve("LoggerApplication.java"), content);
    }

    private void generateController(Path javaPath) throws IOException {
        String content = """
            package com.fractalx.logger;

            import org.springframework.web.bind.annotation.*;
            import java.util.List;
            import java.util.Map;
            import java.util.concurrent.ConcurrentHashMap;
            import java.time.LocalDateTime;

            @RestController
            @RequestMapping("/api/logs")
            @CrossOrigin(origins = "*") // Allow Admin Dashboard to fetch logs
            public class LogController {

                private final LogRepository repository;

                public LogController(LogRepository repository) {
                    this.repository = repository;
                }

                @PostMapping
                public void ingestLog(@RequestBody LogEntry entry) {
                    entry.setReceivedAt(LocalDateTime.now().toString());
                    repository.save(entry);
                    System.out.println("LOG INGESTED [" + entry.getService() + "]: " + entry.getMessage());
                }

                @GetMapping
                public List<LogEntry> getLogs(@RequestParam(required = false) String traceId) {
                    if (traceId != null) {
                        return repository.findByTraceId(traceId);
                    }
                    return repository.findAll();
                }
            }
            """;
        Files.writeString(javaPath.resolve("LogController.java"), content);
    }

    private void generateRepository(Path javaPath) throws IOException {
        // Generates a simple DTO and In-Memory Repository
        String content = """
            package com.fractalx.logger;

            import org.springframework.stereotype.Component;
            import java.util.*;
            import java.util.concurrent.CopyOnWriteArrayList;
            import java.util.stream.Collectors;

            class LogEntry {
                private String service;
                private String traceId;
                private String level;
                private String message;
                private String receivedAt;

                // Getters & Setters handled by Framework or manually added here
                public String getService() { return service; }
                public void setService(String s) { this.service = s; }
                public String getTraceId() { return traceId; }
                public void setTraceId(String t) { this.traceId = t; }
                public String getMessage() { return message; }
                public void setMessage(String m) { this.message = m; }
                public void setReceivedAt(String t) { this.receivedAt = t; }
                public String getReceivedAt() { return receivedAt; }
            }

            @Component
            class LogRepository {
                private final List<LogEntry> storage = new CopyOnWriteArrayList<>();

                public void save(LogEntry entry) {
                    storage.add(entry);
                    // Keep buffer small for demo
                    if (storage.size() > 1000) storage.remove(0);
                }

                public List<LogEntry> findAll() {
                    return new ArrayList<>(storage);
                }

                public List<LogEntry> findByTraceId(String traceId) {
                    return storage.stream()
                        .filter(l -> traceId.equals(l.getTraceId()))
                        .collect(Collectors.toList());
                }
            }
            """;
        Files.writeString(javaPath.resolve("LogRepository.java"), content);
    }
}