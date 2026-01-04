package com.fractalx.core.generator;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generator for standalone discovery service
 */
public class DiscoveryServiceGenerator {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryServiceGenerator.class);

    public void generate(Path outputRoot, List<FractalModule> modules) throws IOException {
        log.info("Generating FractalX Discovery Service...");

        Path discoveryDir = outputRoot.resolve("discovery-service");
        Path srcMainJava = discoveryDir.resolve("src/main/java");
        Path srcMainResources = discoveryDir.resolve("src/main/resources");

        // Clean and create directories
        if (Files.exists(discoveryDir)) {
            deleteDirectory(discoveryDir);
        }

        Files.createDirectories(srcMainJava);
        Files.createDirectories(srcMainResources);

        // Generate all files
        generatePom(discoveryDir, modules);
        generateApplicationClass(srcMainJava);
        generateApplicationConfig(srcMainResources, modules);
        generateStaticServicesConfig(srcMainResources, modules);
        generateStartScripts(discoveryDir);

        log.info("✓ Discovery Service generated at: {}", discoveryDir.toAbsolutePath());
    }

    private void generatePom(Path discoveryDir, List<FractalModule> modules) throws IOException {
        // ... (use the POM content from above)
    }

    private void generateApplicationClass(Path srcMainJava) throws IOException {
        // ... (use the application class content from above)
    }

    private void generateApplicationConfig(Path srcMainResources, List<FractalModule> modules) throws IOException {
        // ... (use the YAML configs from above)
    }

    private void generateStaticServicesConfig(Path srcMainResources, List<FractalModule> modules) throws IOException {
        // ... (use the static services config from above)
    }

    private void generateStartScripts(Path discoveryDir) throws IOException {
        // Generate cross-platform scripts
        generateUnixScript(discoveryDir);
        generateWindowsScript(discoveryDir);
    }

    private void generateUnixScript(Path discoveryDir) throws IOException {
        String script = """
            #!/bin/bash
            
            echo "=========================================="
            echo "FractalX Discovery Service"
            echo "=========================================="
            echo ""
            echo "To start: mvn spring-boot:run"
            echo ""
            echo "API Endpoints:"
            echo "  Health:    http://localhost:8761/api/discovery/health"
            echo "  Services:  http://localhost:8761/api/discovery/services"
            echo "  Stats:     http://localhost:8761/api/discovery/stats"
            echo ""
            """;

        Files.writeString(discoveryDir.resolve("README.md"), script);
    }

    private void generateWindowsScript(Path discoveryDir) throws IOException {
        String script = """
            @echo off
            echo ==========================================
            echo FractalX Discovery Service
            echo ==========================================
            echo.
            echo To start: mvn spring-boot:run
            echo.
            echo API Endpoints:
            echo   Health:    http://localhost:8761/api/discovery/health
            echo   Services:  http://localhost:8761/api/discovery/services
            echo   Stats:     http://localhost:8761/api/discovery/stats
            echo.
            pause
            """;

        Files.writeString(discoveryDir.resolve("start-windows.bat"), script);
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    deleteDirectory(entry);
                }
            }
        }
        Files.delete(path);
    }
}