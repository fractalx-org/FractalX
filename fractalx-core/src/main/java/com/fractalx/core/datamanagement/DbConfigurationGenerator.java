package com.fractalx.core.datamanagement;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates/Updates application.yml for microservices.
 * INTELLIGENT MERGE: Reads the existing config (Service Discovery/Actuator)
 * and only updates the Database/JPA sections to avoid overwriting other settings.
 */
public class DbConfigurationGenerator {

    private static final Logger log = LoggerFactory.getLogger(DbConfigurationGenerator.class);

    public String generateDbConfig(FractalModule module, Path sourceRoot, Path serviceResourcesPath) {
        // 1. Read the Existing YAML (Preserving Member's work)
        Path ymlPath = serviceResourcesPath.resolve("application.yml");
        Map<String, Object> existingConfig = readYaml(ymlPath);

        if (existingConfig == null) {
            existingConfig = new HashMap<>(); // Safety fallback
        }

        // 2. Read Monolith Config (Your Logic)
        Map<String, Object> monolithConfig = readConfigFromMonolith(module.getServiceName(), sourceRoot);
        String driverClassName = null;

        try {
            // 3. Prepare the New DB Config (Merge Logic)
            if (monolithConfig != null) {
                log.info("⚙️ [Config] Merging Custom Database Config for '{}'...", module.getServiceName());

                // Get Driver for dependencies
                Map<String, Object> datasource = (Map<String, Object>) monolithConfig.get("datasource");
                if (datasource != null) {
                    driverClassName = (String) datasource.get("driver-class-name");
                }

                // MERGE: Update existing config with Monolith DB settings
                mergeDbConfig(existingConfig, monolithConfig, module);

            } else {
                log.info("ℹ️ [Config] No custom config found. Merging Default H2 Config for '{}'.", module.getServiceName());
                // MERGE: Update existing config with H2 defaults
                mergeDefaultH2(existingConfig, module);
            }

            // 4. Write back to file (Preserving Member's work + Your DB Config)
            writeYaml(ymlPath, existingConfig);
            log.info("   ✓ Updated application.yml (Merged DB + Discovery)");

            return driverClassName;

        } catch (IOException e) {
            log.error("Failed to write application.yml", e);
            return null;
        }
    }

    // --- MERGE LOGIC ---

    private void mergeDbConfig(Map<String, Object> target, Map<String, Object> source, FractalModule module) {
        // Ensure 'spring' key exists
        Map<String, Object> spring = (Map<String, Object>) target.computeIfAbsent("spring", k -> new HashMap<>());

        // 1. Inject Datasource
        Map<String, Object> sourceDs = (Map<String, Object>) source.get("datasource");
        processDatasourceUrl(sourceDs); // Handle MySQL param injection
        spring.put("datasource", sourceDs);

        // 2. Inject JPA (Force Validate)
        Map<String, Object> sourceJpa = (Map<String, Object>) source.get("jpa");
        if (sourceJpa == null) sourceJpa = new HashMap<>();
        forceValidateMode(sourceJpa);
        spring.put("jpa", sourceJpa);

        // 3. Inject SQL Init (Platform detection)
        Map<String, Object> sql = new HashMap<>();
        Map<String, Object> init = new HashMap<>();
        init.put("mode", "always");
        init.put("platform", detectPlatform((String) sourceDs.get("driver-class-name")));
        sql.put("init", init);
        spring.put("sql", sql);
    }

    private void mergeDefaultH2(Map<String, Object> target, FractalModule module) {
        Map<String, Object> spring = (Map<String, Object>) target.computeIfAbsent("spring", k -> new HashMap<>());

        // Datasource
        Map<String, Object> datasource = new HashMap<>();
        // FIX: Matches your sample format: jdbc:h2:mem:order_service
        String dbName = module.getServiceName().replace("-", "_");

        datasource.put("url", "jdbc:h2:mem:" + dbName);
        datasource.put("driver-class-name", "org.h2.Driver");
        datasource.put("username", "sa");
        datasource.put("password", "");
        spring.put("datasource", datasource);

        // SQL Init
        Map<String, Object> sql = new HashMap<>();
        Map<String, Object> init = new HashMap<>();
        init.put("mode", "always");
        init.put("platform", "h2");
        sql.put("init", init);
        spring.put("sql", sql);

        // JPA
        Map<String, Object> jpa = new HashMap<>();
        // Note: In your sample, you didn't have 'database-platform', but it's good practice.
        // I will stick to your sample structure closely.
        jpa.put("show-sql", true);

        Map<String, Object> hibernate = new HashMap<>();
        hibernate.put("ddl-auto", "validate"); // Force validate
        jpa.put("hibernate", hibernate);

        // Add formatting property from your sample
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> hibProps = new HashMap<>();
        hibProps.put("format_sql", true);
        properties.put("hibernate", hibProps);
        jpa.put("properties", properties);

        spring.put("jpa", jpa);

        // H2 Console (from your sample)
        Map<String, Object> h2 = new HashMap<>();
        Map<String, Object> console = new HashMap<>();
        console.put("enabled", true);
        h2.put("console", console);
        spring.put("h2", h2);
    }

    // --- HELPERS ---

    private void processDatasourceUrl(Map<String, Object> datasource) {
        if (datasource == null) return;
        String driver = (String) datasource.get("driver-class-name");
        String url = (String) datasource.get("url");

        if (driver != null && driver.contains("mysql") && url != null) {
            if (!url.contains("createDatabaseIfNotExist=true")) {
                String separator = url.contains("?") ? "&" : "?";
                datasource.put("url", url + separator + "createDatabaseIfNotExist=true");
            }
        }
    }

    private void forceValidateMode(Map<String, Object> jpaConfig) {
        // We check if 'hibernate' block exists, create if not
        Map<String, Object> hibernate = (Map<String, Object>) jpaConfig.get("hibernate");
        if (hibernate == null) {
            hibernate = new HashMap<>();
            jpaConfig.put("hibernate", hibernate);
        }
        // Force validate to ensure schema.sql is respected
        hibernate.put("ddl-auto", "validate");
    }

    private String detectPlatform(String driver) {
        if (driver == null) return "all";
        if (driver.contains("mysql")) return "mysql";
        if (driver.contains("postgresql")) return "postgresql";
        if (driver.contains("h2")) return "h2";
        return "all";
    }

    // --- IO & YAML UTILS ---

    private Map<String, Object> readYaml(Path path) {
        if (!Files.exists(path)) return new HashMap<>();
        try (FileInputStream inputStream = new FileInputStream(path.toFile())) {
            return new Yaml().load(inputStream);
        } catch (Exception e) {
            log.warn("Could not parse existing YAML: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private void writeYaml(Path path, Map<String, Object> data) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);

        Yaml yaml = new Yaml(options);
        String output = yaml.dump(data);
        Files.writeString(path, output);
    }

    // Existing monolith reader logic
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
}