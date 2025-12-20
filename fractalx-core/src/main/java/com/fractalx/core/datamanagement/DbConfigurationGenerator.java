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
 * INTELLIGENT: Checks the Monolith's config for overrides (e.g. MySQL) first.
 * FALLBACK: If no config found, it leaves the default H2 file alone.
 */
public class DbConfigurationGenerator {

    private static final Logger log = LoggerFactory.getLogger(DbConfigurationGenerator.class);

    public String generateDbConfig(FractalModule module, Path sourceRoot, Path serviceResourcesPath) {
        // 1. Try to read the Monolith's application.yml
        Map<String, Object> customConfig = readConfigFromMonolith(module.getServiceName(), sourceRoot);

        if (customConfig != null) {
            log.info("⚙️ [SmartConfig] Found Custom Database Config for '{}'. Overwriting default...", module.getServiceName());

            // 2. Generate the new content (MySQL/Postgres/etc)
            String newContent = reconstructYaml(module, customConfig);

            // 3. Overwrite the existing H2 file
            try {
                Path ymlPath = serviceResourcesPath.resolve("application.yml");
                Files.writeString(ymlPath, newContent);
                log.info("   ✓ Updated application.yml with custom configuration.");

                // EXTRACT DRIVER CLASS to decide which dependency to inject
                Map<String, Object> datasource = (Map<String, Object>) customConfig.get("datasource");
                return (String) datasource.get("driver-class-name");

            } catch (IOException e) {
                log.error("Failed to write smart config", e);
            }
        } else {
            log.debug("   ℹ No custom config found for '{}'. Keeping default H2.", module.getServiceName());
        }
        return null;
    }

    private Map<String, Object> readConfigFromMonolith(String serviceName, Path sourceRoot) {
        try {
            // Path to monolith's src/main/resources/application.yml
            Path resourcesDir = sourceRoot.getParent().resolve("resources");
            Path yamlPath = resourcesDir.resolve("application.yml");

            if (!Files.exists(yamlPath)) return null;

            Yaml yaml = new Yaml();
            try (FileInputStream inputStream = new FileInputStream(yamlPath.toFile())) {
                Map<String, Object> root = yaml.load(inputStream);

                // Navigate: fractalx -> modules -> [serviceName]
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

        // 1. Get the Datasource Map
        Map<String, Object> datasource = (Map<String, Object>) userConfig.get("datasource");

        // 2. SMART FIX: Automatically append 'createDatabaseIfNotExist' for MySQL
        if (datasource != null) {
            String driver = (String) datasource.get("driver-class-name");
            String url = (String) datasource.get("url");

            if (driver != null && driver.contains("mysql") && url != null) {
                if (!url.contains("createDatabaseIfNotExist=true")) {
                    // Check if URL already has other parameters (?)
                    String separator = url.contains("?") ? "&" : "?";
                    String newUrl = url + separator + "createDatabaseIfNotExist=true";

                    // Update the map
                    datasource.put("url", newUrl);
                    log.info("   ✨ Auto-appended 'createDatabaseIfNotExist=true' to JDBC URL");
                }
            }
        }

        // Extract the user's specific blocks
        String datasourceBlock = yaml.dump(userConfig.get("datasource"));
        String jpaBlock = yaml.dump(userConfig.get("jpa"));

        // Build the final YAML string
        return """
            spring:
              application:
                name: %s
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
                indent(jpaBlock, 4),
                module.getPort() > 0 ? module.getPort() : 8080
        );
    }

    private String indent(String text, int spaces) {
        if (text == null) return "";
        String indent = " ".repeat(spaces);
        return indent + text.replace("\n", "\n" + indent).trim();
    }
}