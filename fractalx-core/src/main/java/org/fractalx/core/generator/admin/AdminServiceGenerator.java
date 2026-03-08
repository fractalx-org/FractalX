package org.fractalx.core.generator.admin;

import org.fractalx.core.model.FractalModule;
import org.fractalx.core.model.SagaDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Orchestrates generation of the admin service project.
 *
 * <p>Each concern is delegated to a focused sub-generator:
 * <ul>
 *   <li>{@link AdminPomGenerator} — pom.xml</li>
 *   <li>{@link AdminAppGenerator} — Spring Boot Application class</li>
 *   <li>{@link AdminSecurityConfigGenerator} — Spring Security (UserStore-backed, role-based)</li>
 *   <li>{@link AdminWebConfigGenerator} — MVC web config</li>
 *   <li>{@link AdminControllerGenerator} — Dashboard controller</li>
 *   <li>{@link AdminModelGenerator} — ServiceInfo, ServiceDetail, NetScopeLink, SagaInfo models</li>
 *   <li>{@link AdminConfigGenerator} — application.yml + alerting.yml</li>
 *   <li>{@link AdminTemplateGenerator} — Thymeleaf HTML templates (9-section dashboard)</li>
 *   <li>{@link AdminStaticAssetsGenerator} — CSS/JS static files</li>
 *   <li>{@link AdminTopologyGenerator} — topology graph + health/services REST API</li>
 *   <li>{@link AdminObservabilityGenerator} — alert system + observability REST API</li>
 *   <li>{@link AdminServicesDetailGenerator} — ServiceMetaRegistry, DeploymentTracker, ServicesController</li>
 *   <li>{@link AdminCommunicationGenerator} — CommunicationController (NetScope, gateway, discovery)</li>
 *   <li>{@link AdminDataConsistencyGenerator} — SagaMetaRegistry, DataConsistencyController</li>
 *   <li>{@link AdminUserManagementGenerator} — UserStoreService interface, UserStore (memory), AdminUser entity, UserController</li>
 *   <li>{@link AdminDatabaseGenerator} — JPA repos + JpaUserStore + JpaSettingsStore + Flyway SQL (db profile)</li>
 *   <li>{@link AdminConfigManagementGenerator} — ServiceConfigStore, ConfigController</li>
 *   <li>{@link AdminAnalyticsGenerator} — MetricsHistoryStore, MetricsCollector, AnalyticsController (/api/analytics/**)</li>
 *   <li>{@link AdminApiExplorerGenerator} — ApiExplorerController (/api/explorer/**)</li>
 *   <li>{@link AdminIncidentGenerator} — Incident, IncidentStore, IncidentController (/api/incidents/**)</li>
 *   <li>{@link AdminGrpcBrowserGenerator} — GrpcBrowserController (/api/grpc/**)</li>
 *   <li>{@link AdminConfigEditorGenerator} — ConfigEditorController (/api/config/editor/**)</li>
 * </ul>
 */
public class AdminServiceGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceGenerator.class);

    private static final int    ADMIN_PORT         = 9090;
    private static final String ADMIN_SERVICE_NAME = "admin-service";
    private static final String BASE_PACKAGE       = "org.fractalx.admin";

    private final AdminPomGenerator               pomGenerator;
    private final AdminAppGenerator               appGenerator;
    private final AdminSecurityConfigGenerator    securityConfigGenerator;
    private final AdminWebConfigGenerator         webConfigGenerator;
    private final AdminControllerGenerator        controllerGenerator;
    private final AdminModelGenerator             modelGenerator;
    private final AdminConfigGenerator            configGenerator;
    private final AdminTemplateGenerator          templateGenerator;
    private final AdminStaticAssetsGenerator      staticAssetsGenerator;
    private final AdminTopologyGenerator          topologyGenerator;
    private final AdminObservabilityGenerator     observabilityGenerator;
    private final AdminServicesDetailGenerator    servicesDetailGenerator;
    private final AdminCommunicationGenerator     communicationGenerator;
    private final AdminDataConsistencyGenerator   dataConsistencyGenerator;
    private final AdminUserManagementGenerator    userManagementGenerator;
    private final AdminDatabaseGenerator          databaseGenerator;
    private final AdminConfigManagementGenerator  configManagementGenerator;
    private final AdminAnalyticsGenerator         analyticsGenerator;
    private final AdminApiExplorerGenerator       apiExplorerGenerator;
    private final AdminIncidentGenerator          incidentGenerator;
    private final AdminGrpcBrowserGenerator       grpcBrowserGenerator;
    private final AdminConfigEditorGenerator      configEditorGenerator;

    public AdminServiceGenerator() {
        this.pomGenerator             = new AdminPomGenerator();
        this.appGenerator             = new AdminAppGenerator();
        this.securityConfigGenerator  = new AdminSecurityConfigGenerator();
        this.webConfigGenerator       = new AdminWebConfigGenerator();
        this.controllerGenerator      = new AdminControllerGenerator();
        this.modelGenerator           = new AdminModelGenerator();
        this.configGenerator          = new AdminConfigGenerator();
        this.templateGenerator        = new AdminTemplateGenerator();
        this.staticAssetsGenerator    = new AdminStaticAssetsGenerator();
        this.topologyGenerator        = new AdminTopologyGenerator();
        this.observabilityGenerator   = new AdminObservabilityGenerator();
        this.servicesDetailGenerator  = new AdminServicesDetailGenerator();
        this.communicationGenerator   = new AdminCommunicationGenerator();
        this.dataConsistencyGenerator = new AdminDataConsistencyGenerator();
        this.userManagementGenerator  = new AdminUserManagementGenerator();
        this.databaseGenerator        = new AdminDatabaseGenerator();
        this.configManagementGenerator= new AdminConfigManagementGenerator();
        this.analyticsGenerator       = new AdminAnalyticsGenerator();
        this.apiExplorerGenerator     = new AdminApiExplorerGenerator();
        this.incidentGenerator        = new AdminIncidentGenerator();
        this.grpcBrowserGenerator     = new AdminGrpcBrowserGenerator();
        this.configEditorGenerator    = new AdminConfigEditorGenerator();
    }

    public void generateAdminService(List<FractalModule> modules, Path outputRoot, Path sourceRoot)
            throws IOException {
        generateAdminService(modules, outputRoot, sourceRoot,
                org.fractalx.core.config.FractalxConfig.defaults(), List.of());
    }

    public void generateAdminService(List<FractalModule> modules, Path outputRoot, Path sourceRoot,
                                      org.fractalx.core.config.FractalxConfig fractalxConfig)
            throws IOException {
        generateAdminService(modules, outputRoot, sourceRoot, fractalxConfig, List.of());
    }

    public void generateAdminService(List<FractalModule> modules, Path outputRoot, Path sourceRoot,
                                      org.fractalx.core.config.FractalxConfig fractalxConfig,
                                      List<SagaDefinition> sagaDefinitions)
            throws IOException {
        log.info("Generating Admin Service...");

        Path serviceRoot   = outputRoot.resolve(ADMIN_SERVICE_NAME);
        Path srcMainJava   = serviceRoot.resolve("src/main/java");
        Path srcMainRes    = serviceRoot.resolve("src/main/resources");
        Path staticPath    = srcMainRes.resolve("static");
        Path templatesPath = srcMainRes.resolve("templates");

        Files.createDirectories(srcMainJava);
        Files.createDirectories(srcMainRes);
        Files.createDirectories(staticPath.resolve("css"));
        Files.createDirectories(staticPath.resolve("js"));
        Files.createDirectories(templatesPath);

        // Read optional admin DB config from fractalx-config.yml (null if absent)
        AdminDbConfig dbConfig = AdminDbConfig.readFrom(sourceRoot);

        // Core infrastructure
        pomGenerator.generate(serviceRoot);
        appGenerator.generate(srcMainJava, BASE_PACKAGE);
        securityConfigGenerator.generate(srcMainJava, BASE_PACKAGE);
        webConfigGenerator.generate(srcMainJava, BASE_PACKAGE);
        controllerGenerator.generate(srcMainJava, BASE_PACKAGE, modules);
        modelGenerator.generate(srcMainJava, BASE_PACKAGE);
        configGenerator.generate(srcMainRes, dbConfig);
        staticAssetsGenerator.generate(staticPath);

        // Service topology + existing observability
        topologyGenerator.generate(srcMainJava, BASE_PACKAGE, modules);
        observabilityGenerator.generate(srcMainJava, BASE_PACKAGE, modules);

        // New enhanced sub-systems
        servicesDetailGenerator.generate(srcMainJava, BASE_PACKAGE, modules, sagaDefinitions);
        communicationGenerator.generate(srcMainJava, BASE_PACKAGE, modules);
        dataConsistencyGenerator.generate(srcMainJava, BASE_PACKAGE, modules, sagaDefinitions, fractalxConfig, outputRoot);
        userManagementGenerator.generate(srcMainJava, BASE_PACKAGE);
        databaseGenerator.generate(srcMainJava, BASE_PACKAGE);
        configManagementGenerator.generate(srcMainJava, BASE_PACKAGE, modules, fractalxConfig);
        analyticsGenerator.generate(srcMainJava, BASE_PACKAGE, modules);
        apiExplorerGenerator.generate(srcMainJava, BASE_PACKAGE, modules);
        incidentGenerator.generate(srcMainJava, BASE_PACKAGE);
        grpcBrowserGenerator.generate(srcMainJava, BASE_PACKAGE, modules);
        configEditorGenerator.generate(srcMainJava, BASE_PACKAGE, modules);

        // Template last (depends on all sub-systems being set up first)
        templateGenerator.generate(templatesPath, modules);

        log.info("Generated Admin Service on port {}", ADMIN_PORT);
    }
}
