package com.fractalx.core.datamanagement;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * RENAMED: DistributedServiceHelper
 * Purpose: A central utility that orchestrates the upgrade of a service.
 * It calls the other helpers to apply Isolation, State, and Dependencies.
 */
public class DistributedServiceHelper {

    private static final Logger log = LoggerFactory.getLogger(DistributedServiceHelper.class);

    // Helpers (Updated to match your new file names)
    private final DataIsolationGenerator isolationGen;      // Matches File 1
    private final StateStoreGenerator stateGen;             // Matches File 4
    private final DependencyManager dependencyManager;      // Matches File 3
    private final DbConfigurationGenerator dbConfigGen;     // Matches File 2

    public DistributedServiceHelper() {
        this.isolationGen = new DataIsolationGenerator();
        this.stateGen = new StateStoreGenerator();
        this.dependencyManager = new DependencyManager();
        this.dbConfigGen = new DbConfigurationGenerator();
    }

    /**
     * The Main Hook: Upgrades the service with distributed features.
     */
    public void upgradeService(FractalModule module, Path sourceRoot, Path serviceRoot) throws IOException {

        Path srcMainJava = serviceRoot.resolve("src/main/java");
        Path srcMainResources = serviceRoot.resolve("src/main/resources");

        log.info("   ⚡ [Helper] Applying Distributed Features to {}...", module.getServiceName());

        // A. Apply Data Isolation (Task 1)
        // Was: dbScanHelper.generateScanConfig(...) -> OLD
        // Now: isolationGen.generateIsolationConfig(...) -> NEW
        isolationGen.generateIsolationConfig(module, srcMainJava);

        // B. Apply State Management (Task 3)
        // Was: stateConfigGen.generateRedisConfig(...) -> OLD
        // Now: stateGen.generateStateStoreConfig(...) -> NEW
        stateGen.generateStateStoreConfig(module, srcMainJava);

        // C. Inject Dependencies (Redis)
        // Was: pomInjector.addRedisDependency(...) -> OLD
        // Now: dependencyManager.provisionRedis(...) -> NEW
        dependencyManager.provisionRedis(module, serviceRoot);

        // D. Smart Configuration & MySQL Injection (Task 2 & Infrastructure)
        // This method name remained the same in File 2, so it's fine.
        String driverClass = dbConfigGen.generateDbConfig(module, sourceRoot, srcMainResources);

        if (driverClass != null && driverClass.contains("mysql")) {
            // Was: pomInjector.addMysqlDependency(...) -> OLD
            // Now: dependencyManager.provisionMySQL(...) -> NEW
            dependencyManager.provisionMySQL(module, serviceRoot);
        }
    }
}