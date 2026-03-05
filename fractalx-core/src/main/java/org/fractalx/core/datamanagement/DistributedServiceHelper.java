package org.fractalx.core.datamanagement;

import org.fractalx.core.model.FractalModule;
import org.fractalx.core.model.SagaDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Central orchestrator for applying distributed systems capabilities to each
 * generated microservice.
 *
 * <p>Coordinates in order:
 * <ol>
 *   <li>Data isolation ({@link DataIsolationGenerator})</li>
 *   <li>Database configuration ({@link DbConfigurationGenerator})</li>
 *   <li>Driver provisioning ({@link DependencyManager})</li>
 *   <li>Flyway migration scaffold ({@link FlywayMigrationGenerator})</li>
 *   <li>Transactional outbox ({@link OutboxGenerator})</li>
 *   <li>Reference validator ({@link ReferenceValidatorGenerator})</li>
 *   <li>Data README ({@link DataReadmeGenerator})</li>
 * </ol>
 */
public class DistributedServiceHelper {

    private static final Logger log = LoggerFactory.getLogger(DistributedServiceHelper.class);

    private final DataIsolationGenerator    isolationGen;
    private final DependencyManager         dependencyManager;
    private final DbConfigurationGenerator  dbConfigGen;
    private final FlywayMigrationGenerator  flywayGen;
    private final OutboxGenerator           outboxGen;
    private final ReferenceValidatorGenerator referenceValidatorGen;
    private final DataReadmeGenerator       dataReadmeGen;

    public DistributedServiceHelper() {
        this.isolationGen           = new DataIsolationGenerator();
        this.dependencyManager      = new DependencyManager();
        this.dbConfigGen            = new DbConfigurationGenerator();
        this.flywayGen              = new FlywayMigrationGenerator();
        this.outboxGen              = new OutboxGenerator();
        this.referenceValidatorGen  = new ReferenceValidatorGenerator();
        this.dataReadmeGen          = new DataReadmeGenerator();
    }

    /**
     * Upgrades a generated service by applying all distributed systems features.
     *
     * @param module          the module being upgraded
     * @param sourceRoot      the monolith source root (for reading original DB config)
     * @param serviceRoot     the generated service root directory
     * @param sagaDefinitions all sagas detected in the monolith (may be empty)
     */
    public void upgradeService(FractalModule module, Path sourceRoot, Path serviceRoot,
                               List<SagaDefinition> sagaDefinitions) throws IOException {
        Path srcMainJava      = serviceRoot.resolve("src/main/java");
        Path srcMainResources = serviceRoot.resolve("src/main/resources");

        log.info("⚡ [Distributed] Applying distributed features to {}...", module.getServiceName());

        // 1. Enforce data isolation (dual-package @EntityScan + @EnableJpaRepositories)
        isolationGen.generateIsolationConfig(module, srcMainJava);

        // 2. Detect & apply database configuration (fractalx-config.yml → application.yml)
        String driverClass = dbConfigGen.generateDbConfig(module, sourceRoot, srcMainResources);

        // 3. Provision database driver dependency in pom.xml
        if (driverClass != null) {
            if (driverClass.contains("mysql")) {
                dependencyManager.provisionMySQL(module, serviceRoot);
                log.info("   ✓ Provisioned MySQL driver for {}", module.getServiceName());
            } else if (driverClass.contains("postgresql")) {
                dependencyManager.provisionPostgreSQL(module, serviceRoot);
                log.info("   ✓ Provisioned PostgreSQL driver for {}", module.getServiceName());
            }
        }

        // 4. Generate Flyway V1 migration scaffold
        flywayGen.generateMigration(module, serviceRoot);

        // 5. Generate transactional outbox (for services with cross-module deps or sagas)
        if (!module.getDependencies().isEmpty()) {
            outboxGen.generateOutbox(module, serviceRoot, sagaDefinitions);
        }

        // 6. Generate reference validators for decoupled foreign keys
        referenceValidatorGen.generateReferenceValidator(module, serviceRoot);

        // 7. Generate DATA_README.md
        dataReadmeGen.generateServiceDataReadme(module, serviceRoot, driverClass, sagaDefinitions);

        log.info("   ✓ [Distributed] Upgrade complete for {}", module.getServiceName());
    }
}
