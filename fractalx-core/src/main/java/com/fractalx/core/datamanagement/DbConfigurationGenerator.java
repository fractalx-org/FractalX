package com.fractalx.core.datamanagement;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Generates application.yml for microservices.
 * Reads configuration from the Monolith first; falls back to default H2 if missing.
 */
public class DbConfigurationGenerator {

    private static final Logger log = LoggerFactory.getLogger(DbConfigurationGenerator.class);

    public String generateDbConfig(FractalModule module, Path sourceRoot, Path serviceResourcesPath) {
        // 1. Read config from Monolith
        Map<String, Object> customConfig = readConfigFromMonolith(module.getServiceName(), sourceRoot);

        if (customConfig != null) {
            log.info("⚙️ [Config] Found Custom Database Config for '{}'. Overwriting default...", module.getServiceName());

            // 2. Generate new YAML content
            String newContent = reconstructYaml(module, customConfig);

            // 3. Write to application.yml
            try {
                Path ymlPath = serviceResourcesPath.resolve("application.yml");
                Files.writeString(ymlPath, newContent);
                log.info("   ✓ Updated application.yml with custom configuration.");

                // Return driver class name for dependency injection
                Map<String, Object> datasource = (Map<String, Object>) customConfig.get("datasource");
                return (String) datasource.get("driver-class-name");

            } catch (IOException e) {
                log.error("Failed to write config", e);
            }
        } else {
            log.debug("   ℹ No custom config found for '{}'. Keeping default H2.", module.getServiceName());
        }
        return null;
    }

    private Map<String, Object> readConfigFromMonolith(String serviceName, Path sourceRoot) {
        try {
            Path resourcesDir = sourceRoot.getParent().resolve("resources");
            Path yamlPath = resourcesDir.resolve("application.yml");

            if (!Files.exists(yamlPath)) return null;

            Yaml yaml = new Yaml();
            try (FileInputStream inputStream = new FileInputStream(yamlPath.toFile())) {
                Map<String, Object> root = yaml.load(inputStream);

                if (root == null || !root.containsKey("fractalx")) return null;

                Map<String, Object> fractalx = (Map<String, Object>) root.get("fractalx");
                if (fractalx == null || !fractalx.containsKey("modules")) return null;

                Map<String, Object> modules = (Map<String, Object>) fractalx.get("modules");
                if (modules == null) return null;

                return (Map<String, Object>) modules.get(serviceName);
            }
        } catch (Exception e) {
            log.warn("Could not parse Monolith application.yml: {}", e.getMessage());
            return null;
        }
    }

    private String reconstructYaml(FractalModule module, Map<String, Object> userConfig) {
        Yaml yaml = new Yaml();

        // 1. Process Datasource
        Map<String, Object> datasource = (Map<String, Object>) userConfig.get("datasource");

        if (datasource != null) {
            String driver = (String) datasource.get("driver-class-name");
            String url = (String) datasource.get("url");

            // Auto-append createDatabaseIfNotExist for MySQL to prevent startup errors
            if (driver != null && driver.contains("mysql") && url != null) {
                if (!url.contains("createDatabaseIfNotExist=true")) {
                    String separator = url.contains("?") ? "&" : "?";
                    String newUrl = url + separator + "createDatabaseIfNotExist=true";
                    datasource.put("url", newUrl);
                    log.info("   ✨ Auto-appended 'createDatabaseIfNotExist=true' to JDBC URL");
                }
            }
        }

        // 2. Process JPA Configuration
        Map<String, Object> jpaConfig = (Map<String, Object>) userConfig.get("jpa");
        String jpaBlock = yaml.dump(jpaConfig);

        boolean modified = false;

        // Force 'validate' mode to ensure DB matches generated schema.sql script
        if (jpaBlock.contains("ddl-auto: update")) {
            jpaBlock = jpaBlock.replace("ddl-auto: update", "ddl-auto: validate");
            modified = true;
        } else if (jpaBlock.contains("ddl-auto: create-drop")) {
            jpaBlock = jpaBlock.replace("ddl-auto: create-drop", "ddl-auto: validate");
            modified = true;
        }

        String jpaSection;
        if (modified) {
            String comment = """
                # [FRACTALX] Forced 'validate' mode to respect generated schema.sql.""";
            jpaSection = comment + "\n" + indent(jpaBlock, 4);
        } else {
            jpaSection = indent(jpaBlock, 4);
        }

        String datasourceBlock = yaml.dump(datasource);

        return """
            spring:
              application:
                name: %s
              
              sql:
                init:
                  mode: always
                  platform: mysql
                  
              datasource:
            %s
              jpa:
            %s
            
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
                indent(datasourceBlock, 4),
                jpaSection,
                module.getPort() > 0 ? module.getPort() : 8080
        );
    }

    private String indent(String text, int spaces) {
        if (text == null) return "";
        String indent = " ".repeat(spaces);
        return indent + text.replace("\n", "\n" + indent).trim();
    }
}