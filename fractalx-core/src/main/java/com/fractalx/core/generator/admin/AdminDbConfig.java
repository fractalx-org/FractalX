package com.fractalx.core.generator.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Holds admin service database configuration read from {@code fractalx-config.yml}
 * before decomposition runs.
 *
 * <p>Expected structure in {@code fractalx-config.yml}:
 * <pre>
 * fractalx:
 *   admin:
 *     datasource:
 *       url: jdbc:mysql://localhost:3306/admin_db
 *       username: admin_user
 *       password: secret
 *       driver-class-name: com.mysql.cj.jdbc.Driver
 * </pre>
 *
 * <p>{@code driver-class-name} is optional — Spring Boot auto-detects the driver from the JDBC URL.
 * {@code password} defaults to empty string if omitted.
 *
 * <p>Returns {@code null} from {@link #readFrom(Path)} if the config file is absent or the
 * {@code fractalx.admin.datasource} key is missing.
 */
record AdminDbConfig(String url, String username, String password, String driverClassName) {

    private static final Logger log = LoggerFactory.getLogger(AdminDbConfig.class);

    /**
     * Reads admin datasource config from {@code fractalx-config.yml} located in the monolith's
     * {@code src/main/resources} sibling directory. Returns {@code null} if the file is absent
     * or the {@code fractalx.admin.datasource} block is missing.
     *
     * @param sourceRoot path to the monolith's {@code src/main/java} directory (same convention
     *                   used by {@code DbConfigurationGenerator})
     */
    @SuppressWarnings("unchecked")
    static AdminDbConfig readFrom(Path sourceRoot) {
        if (sourceRoot == null) return null;

        Path resourcesDir = sourceRoot.getParent().resolve("resources");
        Path configFile   = resourcesDir.resolve("fractalx-config.yml");

        if (!Files.exists(configFile)) {
            log.debug("fractalx-config.yml not found at {} — admin DB config will use env-var placeholders",
                    configFile);
            return null;
        }

        try (FileInputStream fis = new FileInputStream(configFile.toFile())) {
            Map<String, Object> root = new Yaml().load(fis);
            if (root == null) return null;

            Map<String, Object> fractalx = (Map<String, Object>) root.get("fractalx");
            if (fractalx == null) return null;

            Map<String, Object> admin = (Map<String, Object>) fractalx.get("admin");
            if (admin == null) return null;

            Map<String, Object> ds = (Map<String, Object>) admin.get("datasource");
            if (ds == null) return null;

            String url    = (String) ds.get("url");
            if (url == null) return null;   // url is the minimum required field

            String username       = (String) ds.get("username");
            String password       = (String) ds.get("password");
            String driverClassName = (String) ds.get("driver-class-name");

            log.info("Found admin datasource config in fractalx-config.yml (url: {}, driver: {})",
                    url, driverClassName != null ? driverClassName : "auto-detect");
            return new AdminDbConfig(url, username, password, driverClassName);

        } catch (Exception e) {
            log.warn("Could not parse fractalx-config.yml for admin datasource config: {}", e.getMessage());
            return null;
        }
    }
}
