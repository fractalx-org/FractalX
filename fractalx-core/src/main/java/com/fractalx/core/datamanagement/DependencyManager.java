package com.fractalx.core.datamanagement;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Provisions required infrastructure libraries (Redis, MySQL)
 * by injecting dependencies directly into the service's pom.xml.
 */
public class DependencyManager {

    private static final Logger log = LoggerFactory.getLogger(DependencyManager.class);

//    public void provisionRedis(FractalModule module, Path serviceRoot) {
//        String dependency = """
//        <dependency>
//                    <groupId>org.springframework.boot</groupId>
//                    <artifactId>spring-boot-starter-data-redis</artifactId>
//                </dependency>""";
//
//        injectDependency(module, serviceRoot, "spring-boot-starter-data-redis", dependency);
//    }

    public void provisionMySQL(FractalModule module, Path serviceRoot) {
        String dependency = """
        <dependency>
                    <groupId>com.mysql</groupId>
                    <artifactId>mysql-connector-j</artifactId>
                    <scope>runtime</scope>
                </dependency>""";

        injectDependency(module, serviceRoot, "mysql-connector-j", dependency);
    }

    public void provisionPostgreSQL(FractalModule module, Path serviceRoot) {
        String dependency = """
        <dependency>
                    <groupId>org.postgresql</groupId>
                    <artifactId>postgresql</artifactId>
                    <scope>runtime</scope>
                </dependency>""";
        injectDependency(module, serviceRoot, "postgresql", dependency);
    }

    private void injectDependency(FractalModule module, Path serviceRoot, String checkString, String rawXml) {
        try {
            Path pomPath = serviceRoot.resolve("pom.xml");
            if (!Files.exists(pomPath)) return;

            String content = Files.readString(pomPath);
            // Prevent duplicate entries
            if (content.contains(checkString)) return;

            // Locate the end of the dependencies block
            int lastIndex = content.lastIndexOf("</dependencies>");
            if (lastIndex == -1) return;

            // Prepare the clean insertion point
            String start = content.substring(0, lastIndex).stripTrailing();
            String end = content.substring(lastIndex);

            // Insert the new dependency block
            String formattedBlock = "\n\n        " + rawXml;
            String newContent = start + formattedBlock + "\n    " + end;

            Files.writeString(pomPath, newContent);
            log.info("➕ [Dependency] Provisioned {} for {}", checkString, module.getServiceName());

        } catch (IOException e) {
            log.error("Failed to update pom.xml for " + module.getServiceName(), e);
        }
    }
}