package com.fractalx.core.datamanagement;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * RENAMED: DependencyManager
 * Research Area: Dependency Management (Task 2)
 * Purpose: Provisions required infrastructure libraries (Redis, MySQL)
 * by integrating them into the build definition (pom.xml).
 */
public class DependencyManager {

    private static final Logger log = LoggerFactory.getLogger(DependencyManager.class);

    public void provisionRedis(FractalModule module, Path serviceRoot) {
        // 8 spaces for parent, 12 spaces for children (Standard Maven Formatting)
        String dependency = """
        <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-data-redis</artifactId>
                </dependency>""";

        injectDependency(module, serviceRoot, "spring-boot-starter-data-redis", dependency);
    }

    public void provisionMySQL(FractalModule module, Path serviceRoot) {
        String dependency = """
        <dependency>
                    <groupId>com.mysql</groupId>
                    <artifactId>mysql-connector-j</artifactId>
                    <scope>runtime</scope>
                </dependency>""";

        injectDependency(module, serviceRoot, "mysql-connector-j", dependency);
    }

    private void injectDependency(FractalModule module, Path serviceRoot, String checkString, String rawXml) {
        try {
            Path pomPath = serviceRoot.resolve("pom.xml");
            if (!Files.exists(pomPath)) return;

            String content = Files.readString(pomPath);
            if (content.contains(checkString)) return;

            // Find the closing tag
            int lastIndex = content.lastIndexOf("</dependencies>");
            if (lastIndex == -1) return;

            // Clean up trailing whitespace to ensure clean insertion
            String start = content.substring(0, lastIndex).stripTrailing();
            String end = content.substring(lastIndex);

            // Construct the clean block
            String formattedBlock = "\n\n        " + rawXml;
            String newContent = start + formattedBlock + "\n    " + end;

            Files.writeString(pomPath, newContent);
            log.info("➕ [DependencyManager] Provisioned {} for {}", checkString, module.getServiceName());

        } catch (IOException e) {
            log.error("Failed to update pom.xml for " + module.getServiceName(), e);
        }
    }
}