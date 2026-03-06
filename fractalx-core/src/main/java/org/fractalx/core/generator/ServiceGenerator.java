package org.fractalx.core.generator;

import org.fractalx.core.config.FractalxConfig;
import org.fractalx.core.config.FractalxConfigReader;
import org.fractalx.core.datamanagement.DistributedServiceHelper;
import org.fractalx.core.datamanagement.RepositoryAnalyzer;
import org.fractalx.core.datamanagement.SagaAnalyzer;
import org.fractalx.core.gateway.GatewayGenerator;
import org.fractalx.core.generator.admin.AdminServiceGenerator;
import org.fractalx.core.generator.registry.RegistryServiceGenerator;
import org.fractalx.core.generator.observability.OtelConfigStep;
import org.fractalx.core.generator.observability.HealthMetricsStep;
import org.fractalx.core.generator.resilience.ResilienceConfigStep;
import org.fractalx.core.generator.saga.SagaOrchestratorGenerator;
import org.fractalx.core.generator.service.ApplicationGenerator;
import org.fractalx.core.generator.service.ConfigurationGenerator;
import org.fractalx.core.generator.service.CorrelationIdGenerator;
import org.fractalx.core.generator.service.NetScopeClientGenerator;
import org.fractalx.core.generator.service.NetScopeRegistryBridgeStep;
import org.fractalx.core.generator.service.PomGenerator;
import org.fractalx.core.generator.service.ServiceRegistrationStep;
import org.fractalx.core.generator.transformation.AnnotationRemover;
import org.fractalx.core.generator.transformation.CodeCopier;
import org.fractalx.core.generator.transformation.CodeTransformer;
import org.fractalx.core.generator.transformation.FileCleanupStep;
import org.fractalx.core.generator.transformation.ImportCleaner;
import org.fractalx.core.generator.transformation.ImportPreserver;
import org.fractalx.core.generator.transformation.NetScopeClientWiringStep;
import org.fractalx.core.generator.transformation.NetScopeServerAnnotationStep;
import org.fractalx.core.generator.transformation.SagaMethodTransformer;
import org.fractalx.core.model.FractalModule;
import org.fractalx.core.model.SagaDefinition;
import org.fractalx.core.observability.LoggerServiceGenerator;
import org.fractalx.core.observability.ObservabilityInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Orchestrates generation of all microservice projects from analyzed modules.
 *
 * <p>Each generation step implements {@link ServiceFileGenerator} and receives a
 * {@link GenerationContext}. New steps can be added to the pipeline in the
 * constructor without modifying this class.
 */
public class ServiceGenerator {

    private static final Logger log = LoggerFactory.getLogger(ServiceGenerator.class);
    private static final int    GATEWAY_PORT = 9999;
    private static final String GATEWAY_DIR  = "fractalx-gateway";

    private final Path sourceRoot;
    private final Path outputRoot;

    // Ordered list of generation steps — open for extension, closed for modification
    private final List<ServiceFileGenerator> pipeline;

    private final ObservabilityInjector     observabilityInjector;
    private final DistributedServiceHelper  distributedServiceHelper;
    private final AdminServiceGenerator     adminServiceGenerator;
    private final SagaOrchestratorGenerator sagaOrchestratorGenerator;
    private final SagaAnalyzer              sagaAnalyzer;
    private final RepositoryAnalyzer        repositoryAnalyzer;
    private final RegistryServiceGenerator  registryServiceGenerator;
    private final DockerComposeGenerator    dockerComposeGenerator;

    private java.util.function.Consumer<String> onStepStart    = lbl -> {};
    private java.util.function.Consumer<String> onStepComplete = lbl -> {};

    public void setProgressCallbacks(java.util.function.Consumer<String> onStart,
                                     java.util.function.Consumer<String> onComplete) {
        this.onStepStart    = onStart;
        this.onStepComplete = onComplete;
    }

    public ServiceGenerator(Path sourceRoot, Path outputRoot) {
        this.sourceRoot = sourceRoot;
        this.outputRoot = outputRoot;

        this.observabilityInjector     = new ObservabilityInjector();
        this.distributedServiceHelper  = new DistributedServiceHelper();
        this.adminServiceGenerator     = new AdminServiceGenerator();
        this.sagaOrchestratorGenerator = new SagaOrchestratorGenerator();
        this.sagaAnalyzer              = new SagaAnalyzer();
        this.repositoryAnalyzer        = new RepositoryAnalyzer();
        this.registryServiceGenerator  = new RegistryServiceGenerator();
        this.dockerComposeGenerator    = new DockerComposeGenerator();

        this.pipeline = buildPipeline();
    }

    private List<ServiceFileGenerator> buildPipeline() {
        ObservabilityInjector injector = this.observabilityInjector;

        return List.of(
                new PomGenerator(injector),
                new ApplicationGenerator(),
                new ConfigurationGenerator(),
                context -> injector.patchConfigurationFile(context.getSrcMainResources()),
                new CodeCopier(),
                new CodeTransformer(
                        new AnnotationRemover(),
                        new org.fractalx.core.datamanagement.RelationshipDecoupler(),
                        new ImportPreserver(),
                        new ImportCleaner()
                ),
                new FileCleanupStep(List.of("PaymentClientImpl.java")),
                new NetScopeServerAnnotationStep(),
                new NetScopeClientGenerator(),
                new NetScopeClientWiringStep(),
                new SagaMethodTransformer(),    // replaces cross-service calls with outboxPublisher.publish()
                context -> distributedServiceHelper.upgradeService(
                        context.getModule(), context.getSourceRoot(), context.getServiceRoot(),
                        context.getSagaDefinitions()),
                new CorrelationIdGenerator(),    // generates logback-spring.xml with %X{correlationId}
                new OtelConfigStep(),
                new HealthMetricsStep(),
                new ServiceRegistrationStep(),
                new NetScopeRegistryBridgeStep(),
                new ResilienceConfigStep()
        );
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void generateServices(List<FractalModule> modules) throws IOException {
        log.debug("Starting code generation for {} modules", modules.size());
        FractalxConfig fractalxConfig = new FractalxConfigReader()
                .read(sourceRoot.resolve("src/main/resources"));
        Files.createDirectories(outputRoot);

        // Run repository boundary analysis before generation (warnings only — never fails)
        RepositoryAnalyzer.RepositoryReport repoReport = repositoryAnalyzer.analyze(sourceRoot, modules);
        if (repoReport.hasViolations()) {
            log.warn("Repository boundary violations detected — review DATA_README.md for guidance");
        }

        // Detect @DistributedSaga definitions across all modules
        List<SagaDefinition> sagaDefinitions = sagaAnalyzer.analyzeSagas(sourceRoot, modules);

        // Generate the service registry first so it is available for all other generators
        onStepStart.accept("fractalx-registry");
        registryServiceGenerator.generate(modules, outputRoot);
        onStepComplete.accept("fractalx-registry");

        for (FractalModule module : modules) {
            onStepStart.accept(module.getServiceName());
            generateService(module, modules, fractalxConfig, sagaDefinitions);
            onStepComplete.accept(module.getServiceName());
        }

        new LoggerServiceGenerator().generate(outputRoot);

        if (modules.size() > 1) {
            onStepStart.accept("fractalx-gateway");
            generateApiGateway(modules, fractalxConfig);
            onStepComplete.accept("fractalx-gateway");
        }

        onStepStart.accept("fractalx-admin");
        adminServiceGenerator.generateAdminService(modules, outputRoot, sourceRoot, fractalxConfig, sagaDefinitions);
        onStepComplete.accept("fractalx-admin");

        boolean hasSagas = !sagaDefinitions.isEmpty();
        if (hasSagas) {
            onStepStart.accept("fractalx-saga-orchestrator");
            sagaOrchestratorGenerator.generateOrchestratorService(modules, sagaDefinitions, outputRoot);
            onStepComplete.accept("fractalx-saga-orchestrator");
        } else {
            sagaOrchestratorGenerator.generateOrchestratorService(modules, sagaDefinitions, outputRoot);
        }

        onStepStart.accept("docker-compose + scripts");
        dockerComposeGenerator.generate(modules, outputRoot, hasSagas);
        generateStartScripts(modules, sagaDefinitions);
        onStepComplete.accept("docker-compose + scripts");

        log.debug("Code generation complete!");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void generateService(FractalModule module, List<FractalModule> allModules,
                                  FractalxConfig fractalxConfig,
                                  List<SagaDefinition> sagaDefinitions) throws IOException {
        log.debug("Generating service: {}", module.getServiceName());

        Path serviceRoot = outputRoot.resolve(module.getServiceName());
        Files.createDirectories(serviceRoot.resolve("src/main/java"));
        Files.createDirectories(serviceRoot.resolve("src/main/resources"));
        Files.createDirectories(serviceRoot.resolve("src/test/java"));

        GenerationContext context = new GenerationContext(
                module, sourceRoot, serviceRoot, allModules, fractalxConfig, sagaDefinitions);

        for (ServiceFileGenerator step : pipeline) {
            step.generate(context);
        }

        log.debug("Generated: {}", module.getServiceName());
    }

    private void generateApiGateway(List<FractalModule> modules,
                                     FractalxConfig fractalxConfig) throws IOException {
        log.debug("Generating API Gateway for {} services", modules.size());
        try {
            new GatewayGenerator(sourceRoot, outputRoot, fractalxConfig).generateGateway(modules);
            log.debug("Generated API Gateway — http://localhost:{}", GATEWAY_PORT);
            updateReadmeWithGatewayInfo(modules);
        } catch (Exception e) {
            log.error("Failed to generate API Gateway: {}", e.getMessage(), e);
            log.warn("Continuing without API Gateway...");
        }
    }

    private void generateStartScripts(List<FractalModule> modules,
                                       List<SagaDefinition> sagaDefinitions) throws IOException {
        Path gatewayPath = outputRoot.resolve(GATEWAY_DIR);

        generateStartScript(modules, gatewayPath, !sagaDefinitions.isEmpty());
        generateStopScript(gatewayPath, !sagaDefinitions.isEmpty());
        generateReadme(modules, !sagaDefinitions.isEmpty());

        log.debug("Generated start scripts");
    }

    private void generateStartScript(List<FractalModule> modules, Path gatewayPath,
                                      boolean hasSagaOrchestrator) throws IOException {
        StringBuilder script = new StringBuilder();
        script.append("#!/bin/bash\n\n");
        script.append("echo \"Starting all FractalX microservices...\"\n\n");
        script.append("# Start fractalx-registry first\n");
        script.append("echo \"Starting fractalx-registry on port 8761...\"\n");
        script.append("cd fractalx-registry && mvn spring-boot:run > ../fractalx-registry.log 2>&1 &\n");
        script.append("cd ..\n");
        script.append("echo \"Waiting 5s for registry to become ready...\"\n");
        script.append("sleep 5\n\n");
        script.append("# Start all microservices\n");

        for (FractalModule module : modules) {
            script.append(String.format("echo \"Starting %s on port %d...\"\n",
                    module.getServiceName(), module.getPort()));
            script.append(String.format("cd %s && mvn spring-boot:run > ../%s.log 2>&1 &\n",
                    module.getServiceName(), module.getServiceName()));
            script.append("cd ..\n\n");
        }

        if (hasSagaOrchestrator) {
            script.append("echo \"Starting Saga Orchestrator...\"\n");
            script.append("cd fractalx-saga-orchestrator && mvn spring-boot:run > ../fractalx-saga-orchestrator.log 2>&1 &\n");
            script.append("cd ..\n\n");
        }

        if (Files.exists(gatewayPath)) {
            script.append("echo \"All services and gateway started successfully!\"\n");
            script.append(String.format("echo \"Gateway URL: http://localhost:%d\"\n", GATEWAY_PORT));
        } else {
            script.append("echo \"All services started. Check logs in *.log files\"\n");
        }

        script.append("echo \"To stop all services, run: ./stop-all.sh\"\n\n");
        script.append("echo \"Service URLs:\"\n");
        for (FractalModule module : modules) {
            script.append(String.format("echo \"  %-20s http://localhost:%d\"\n",
                    module.getServiceName() + ":", module.getPort()));
        }
        if (hasSagaOrchestrator) {
            script.append(String.format("echo \"  %-20s http://localhost:8099\"\n", "saga-orchestrator:"));
        }

        Path scriptPath = outputRoot.resolve("start-all.sh");
        Files.writeString(scriptPath, script.toString());
        scriptPath.toFile().setExecutable(true);
    }

    private void generateStopScript(Path gatewayPath, boolean hasSagaOrchestrator) throws IOException {
        StringBuilder script = new StringBuilder();
        script.append("#!/bin/bash\n\n");
        script.append("echo \"Stopping all FractalX microservices...\"\n\n");

        if (Files.exists(gatewayPath)) {
            script.append("echo \"Stopping API Gateway...\"\n");
            script.append(String.format("pkill -f \"%s\"\n", GATEWAY_DIR));
            script.append("sleep 2\n\n");
        }

        if (hasSagaOrchestrator) {
            script.append("echo \"Stopping Saga Orchestrator...\"\n");
            script.append("pkill -f \"fractalx-saga-orchestrator\" || true\n");
            script.append("sleep 1\n\n");
        }

        script.append("echo \"Stopping all services...\"\n");
        script.append("pkill -f \"spring-boot:run\"\n\n");
        script.append("echo \"All services stopped.\"\n");

        Path scriptPath = outputRoot.resolve("stop-all.sh");
        Files.writeString(scriptPath, script.toString());
        scriptPath.toFile().setExecutable(true);
    }

    private void generateReadme(List<FractalModule> modules, boolean hasSagaOrchestrator) throws IOException {
        StringBuilder readme = new StringBuilder();
        readme.append("# FractalX Generated Microservices\n\n");
        readme.append("This directory contains microservices generated by FractalX.\n\n");

        readme.append("## Services\n\n");
        for (FractalModule module : modules) {
            readme.append(String.format("- **%s**: http://localhost:%d\n",
                    module.getServiceName(), module.getPort()));
        }
        if (hasSagaOrchestrator) {
            readme.append("- **fractalx-saga-orchestrator**: http://localhost:8099\n");
        }

        readme.append("\n## Quick Start\n\n```bash\n./start-all.sh\n```\n\n");

        readme.append("### Start services individually\n\n");
        for (int i = 0; i < modules.size(); i++) {
            FractalModule module = modules.get(i);
            readme.append(String.format("```bash\n# Terminal %d - Start %s\ncd %s\nmvn spring-boot:run\n```\n\n",
                    i + 1, module.getServiceName(), module.getServiceName()));
        }

        readme.append("## Testing\n\n");
        for (FractalModule module : modules) {
            readme.append(String.format("```bash\n# Test %s health\ncurl http://localhost:%d/actuator/health\n```\n\n",
                    module.getServiceName(), module.getPort()));
        }

        readme.append("## Stopping Services\n\n```bash\n./stop-all.sh\n```\n\n");

        readme.append("## Service Architecture\n\n```\n");
        readme.append("Standalone Services (No Service Discovery)\n");
        readme.append("└── Static Gateway Routing\n");
        for (FractalModule module : modules) {
            readme.append(String.format("    ├── %s (:%d)\n", module.getServiceName(), module.getPort()));
            if (!module.getDependencies().isEmpty()) {
                readme.append(String.format("    │   └─ Dependencies: %s\n",
                        String.join(", ", module.getDependencies())));
            }
        }
        readme.append(String.format("    └── Gateway → http://localhost:%d\n", GATEWAY_PORT));
        readme.append("```\n");

        Files.writeString(outputRoot.resolve("README.md"), readme.toString());
        log.debug("Generated README.md");
    }

    private void updateReadmeWithGatewayInfo(List<FractalModule> modules) throws IOException {
        Path readmePath = outputRoot.resolve("README.md");
        if (!Files.exists(readmePath)) {
            log.warn("README.md not found, skipping gateway info update");
            return;
        }

        StringBuilder gatewaySection = new StringBuilder();
        gatewaySection.append("\n## API Gateway (Static Routing)\n\n");
        gatewaySection.append("FractalX generates a simple API Gateway providing a unified entry point.\n\n");
        gatewaySection.append(String.format(
                "- **Unified API**: Single entry point at `http://localhost:%d`\n", GATEWAY_PORT));
        gatewaySection.append("- **Static Routing**: Direct routing based on configured port numbers\n\n");

        gatewaySection.append("**Examples:**\n\n");
        for (FractalModule module : modules) {
            String servicePath = module.getServiceName().replace("-service", "");
            gatewaySection.append(String.format("- **%s**\n", module.getServiceName()));
            gatewaySection.append(String.format("  - Direct:  `http://localhost:%d/api/%s/health`\n",
                    module.getPort(), servicePath));
            gatewaySection.append(String.format("  - Gateway: `http://localhost:%d/api/%s/health`\n\n",
                    GATEWAY_PORT, servicePath));
        }

        String existing = Files.readString(readmePath);
        String updated  = existing.replace(
                "## Services\n\n",
                "## Services\n\n" + gatewaySection + "\n"
        );
        Files.writeString(readmePath, updated);
        log.debug("Updated README with gateway information");
    }
}
