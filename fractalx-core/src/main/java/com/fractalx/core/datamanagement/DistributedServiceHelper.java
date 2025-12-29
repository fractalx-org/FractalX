package com.fractalx.core.datamanagement;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Central orchestrator for applying distributed systems capabilities.
 * Coordinates data isolation, state management, dependency injection, database configuration,
 * and schema script generation.
 */
public class DistributedServiceHelper {

    private static final Logger log = LoggerFactory.getLogger(DistributedServiceHelper.class);

    private final DataIsolationGenerator isolationGen;
//    private final StateStoreGenerator stateGen;
    private final DependencyManager dependencyManager;
    private final DbConfigurationGenerator dbConfigGen;
    private final DataReadmeGenerator dataReadmeGen;

    public DistributedServiceHelper() {
        this.isolationGen = new DataIsolationGenerator();
//        this.stateGen = new StateStoreGenerator();
        this.dependencyManager = new DependencyManager();
        this.dbConfigGen = new DbConfigurationGenerator();
        this.dataReadmeGen = new DataReadmeGenerator();
    }

    /**
     * Upgrades a generated service by applying all necessary distributed features.
     */
    public void upgradeService(FractalModule module, Path sourceRoot, Path serviceRoot) throws IOException {

        Path srcMainJava = serviceRoot.resolve("src/main/java");
        Path srcMainResources = serviceRoot.resolve("src/main/resources");

        log.info("   ⚡ [Distributed] Applying distributed features to {}...", module.getServiceName());

        // 1. Enforce Data Isolation
        isolationGen.generateIsolationConfig(module, srcMainJava);

        // 2. Configure State Management (Redis)
//        stateGen.generateStateStoreConfig(module, srcMainJava);

        // 3. Provision Infrastructure Dependencies
//        dependencyManager.provisionRedis(module, serviceRoot);

        // 4. Generate Database Configuration & Provision Drivers
        String driverClass = dbConfigGen.generateDbConfig(module, sourceRoot, srcMainResources);

        if (driverClass != null) {
            if (driverClass.contains("mysql")) {
                dependencyManager.provisionMySQL(module, serviceRoot);
            } else if (driverClass.contains("postgresql")) {
                dependencyManager.provisionPostgreSQL(module, serviceRoot);
            }
        }

        // 5. Generate Data README
        dataReadmeGen.generateServiceDataReadme(module, serviceRoot, driverClass);
    }
}