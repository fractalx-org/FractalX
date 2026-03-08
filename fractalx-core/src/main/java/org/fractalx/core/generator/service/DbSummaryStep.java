package org.fractalx.core.generator.service;

import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Pipeline step that generates a {@code DbSummaryController} in each service.
 *
 * <p>The controller exposes {@code GET /api/internal/db-summary} which returns
 * row counts for every JPA entity registered in the service's
 * {@link jakarta.persistence.EntityManager}, plus the JDBC URL and driver class name.
 * The admin service polls this endpoint to populate the Database Health table.
 */
public class DbSummaryStep implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(DbSummaryStep.class);

    @Override
    public void generate(GenerationContext context) throws IOException {
        FractalModule module = context.getModule();
        Set<String> imports = module.getDetectedImports();
        boolean hasJpa = imports != null && imports.stream().anyMatch(i ->
                i.startsWith("jakarta.persistence") ||
                i.startsWith("javax.persistence") ||
                i.startsWith("org.springframework.data.jpa") ||
                i.startsWith("org.springframework.data.repository"));
        if (!hasJpa) {
            log.debug("No JPA entities in {} — skipping DbSummaryController", module.getServiceName());
            return;
        }
        String basePackage = "org.fractalx.generated." + toJavaId(module.getServiceName()).toLowerCase();
        Path pkgPath = resolvePackage(context.getSrcMainJava(), basePackage);

        Files.writeString(pkgPath.resolve("DbSummaryController.java"), buildContent(basePackage));
        log.debug("Generated DbSummaryController for {}", module.getServiceName());
    }

    private String buildContent(String pkg) {
        return """
                package %s;

                import jakarta.persistence.EntityManager;
                import jakarta.persistence.PersistenceContext;
                import jakarta.persistence.metamodel.EntityType;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;

                import java.util.*;

                /**
                 * Internal endpoint consumed by the FractalX admin service.
                 * Returns entity row counts + datasource metadata for the Database Health table.
                 *
                 * <p>Not part of the public API — secured at network level by default.
                 */
                @RestController
                @RequestMapping("/api/internal")
                public class DbSummaryController {

                    @PersistenceContext
                    private EntityManager entityManager;

                    @Value("${spring.datasource.url:unknown}")
                    private String datasourceUrl;

                    @Value("${spring.datasource.driver-class-name:unknown}")
                    private String driverClassName;

                    @Value("${spring.datasource.username:sa}")
                    private String datasourceUsername;

                    @Value("${spring.application.name:unknown}")
                    private String serviceName;

                    /**
                     * Returns row counts for every JPA entity and datasource metadata.
                     *
                     * <pre>
                     * {
                     *   "service": "order-service",
                     *   "datasourceUrl": "jdbc:h2:mem:orderdb",
                     *   "driverClassName": "org.h2.Driver",
                     *   "username": "sa",
                     *   "isH2": true,
                     *   "entityCounts": {
                     *     "Order": 12,
                     *     "OrderItem": 34
                     *   },
                     *   "totalRows": 46
                     * }
                     * </pre>
                     */
                    @GetMapping("/db-summary")
                    public ResponseEntity<Map<String, Object>> getDbSummary() {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("service", serviceName);
                        result.put("datasourceUrl", datasourceUrl);
                        result.put("driverClassName", driverClassName);
                        result.put("username", datasourceUsername);
                        result.put("isH2", datasourceUrl != null && datasourceUrl.startsWith("jdbc:h2"));

                        Map<String, Long> entityCounts = new LinkedHashMap<>();
                        long totalRows = 0;

                        try {
                            Set<EntityType<?>> entities = entityManager.getMetamodel().getEntities();
                            for (EntityType<?> entity : entities) {
                                String entityName = entity.getName();
                                try {
                                    Long count = entityManager
                                            .createQuery("SELECT COUNT(e) FROM " + entityName + " e", Long.class)
                                            .getSingleResult();
                                    entityCounts.put(entityName, count);
                                    totalRows += count;
                                } catch (Exception ex) {
                                    entityCounts.put(entityName, -1L);
                                }
                            }
                        } catch (Exception ex) {
                            result.put("error", ex.getMessage());
                        }

                        result.put("entityCounts", entityCounts);
                        result.put("totalRows", totalRows);
                        return ResponseEntity.ok(result);
                    }
                }
                """.formatted(pkg);
    }

    private Path resolvePackage(Path base, String pkg) throws IOException {
        Path p = base;
        for (String part : pkg.split("\\.")) p = p.resolve(part);
        Files.createDirectories(p);
        return p;
    }

    private String toJavaId(String serviceName) {
        StringBuilder sb = new StringBuilder();
        for (String part : serviceName.split("-")) {
            if (!part.isEmpty())
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
