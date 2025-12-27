package com.fractalx.core.generator;

import com.fractalx.core.FractalModule;
import com.fractalx.core.gateway.GatewayGenerator;
import com.fractalx.core.datamanagement.DistributedServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates separate microservice projects from analyzed modules
 */
public class ServiceGenerator {

    private static final Logger log = LoggerFactory.getLogger(ServiceGenerator.class);

    private final Path sourceRoot;
    private final Path outputRoot;

    private final DistributedServiceHelper distributedGen;

    public ServiceGenerator(Path sourceRoot, Path outputRoot) {
        this.sourceRoot = sourceRoot;
        this.outputRoot = outputRoot;
        this.distributedGen = new DistributedServiceHelper();
    }

    /**
     * Generate all microservice projects
     */
    public void generateServices(List<FractalModule> modules) throws IOException {
        log.info("Starting code generation for {} modules", modules.size());

        // Create output directory
        Files.createDirectories(outputRoot);

        for (FractalModule module : modules) {
            generateService(module, modules);
        }

        // Generate API Gateway if we have multiple services
        if (modules.size() > 1) {
            generateApiGateway(modules);
        }

        // Generate Admin Service
        AdminServiceGenerator adminGenerator = new AdminServiceGenerator();
        adminGenerator.generateAdminService(modules, outputRoot);

        // Generate start scripts
        generateStartScripts(modules);

        log.info("Code generation complete!");
    }

    /**
     * Generate a single microservice project
     */
    private void generateService(FractalModule module, List<FractalModule> allModules) throws IOException {
        log.info("Generating service: {}", module.getServiceName());

        // Create service directory structure
        Path serviceRoot = outputRoot.resolve(module.getServiceName());
        Path srcMainJava = serviceRoot.resolve("src/main/java");
        Path srcMainResources = serviceRoot.resolve("src/main/resources");
        Path srcTestJava = serviceRoot.resolve("src/test/java");

        Files.createDirectories(srcMainJava);
        Files.createDirectories(srcMainResources);
        Files.createDirectories(srcTestJava);

        // Step 1: Generate POM
        PomGenerator pomGen = new PomGenerator();
        pomGen.generatePom(module, serviceRoot);

        // Step 2: Generate Application class
        ApplicationGenerator appGen = new ApplicationGenerator();
        appGen.generateApplicationClass(module, srcMainJava);

        // Step 3: Generate configuration
        ConfigurationGenerator configGen = new ConfigurationGenerator();
        configGen.generateApplicationYml(module, srcMainResources);

        // Step 4: Copy module code
        CodeCopier codeCopier = new CodeCopier(sourceRoot);
        codeCopier.copyModuleCode(module, srcMainJava);

        // Step 5: Transform code using AST (remove annotations, clean imports)
        CodeTransformer transformer = new CodeTransformer();
        transformer.transformCode(serviceRoot, module);

        // Step 6: Generate Feign clients for cross-service communication
        FeignClientGenerator feignGen = new FeignClientGenerator();
        feignGen.generateFeignClients(module, srcMainJava, allModules);

        // Step 7: Injecting Database and State Management for Distributed Systems
        distributedGen.upgradeService(module, sourceRoot, serviceRoot);

        log.info("✓ Generated: {}", module.getServiceName());
    }

    /**
     * Generate API Gateway for all services
     */
    private void generateApiGateway(List<FractalModule> modules) throws IOException {
        log.info("Generating API Gateway for {} services", modules.size());

        try {
            GatewayGenerator gatewayGen = new GatewayGenerator(sourceRoot, outputRoot);
            gatewayGen.generateGateway(modules);

            log.info("✓ Generated API Gateway");
            log.info("  Gateway URL: http://localhost:9999");
            // REMOVED: Swagger UI and Health Check logs

            // Update README to include gateway info
            updateReadmeWithGatewayInfo(modules);

        } catch (Exception e) {
            log.error("Failed to generate API Gateway: {}", e.getMessage(), e);
            log.warn("Continuing without API Gateway...");
        }
    }

    /**
     * Update README with gateway information
     */
    private void updateReadmeWithGatewayInfo(List<FractalModule> modules) throws IOException {
        Path readmePath = outputRoot.resolve("README.md");

        if (!Files.exists(readmePath)) {
            log.warn("README.md not found, skipping gateway info update");
            return;
        }

        String existingContent = Files.readString(readmePath);

        // Create simplified gateway section
        StringBuilder gatewaySection = new StringBuilder();
        gatewaySection.append("\n## API Gateway (Static Routing)\n\n");
        gatewaySection.append("FractalX generates a simple API Gateway that provides a unified entry point.\n\n");

        gatewaySection.append("### Gateway Features\n\n");
        gatewaySection.append("- **Unified API**: Single entry point at `http://localhost:9999`\n");
        gatewaySection.append("- **Static Routing**: Direct routing to services based on port numbers\n");
        gatewaySection.append("- **No Service Discovery**: Simple configuration without Eureka\n");
        gatewaySection.append("- **Path Rewriting**: Strip service prefixes from URLs\n\n");

        gatewaySection.append("### Access Services Through Gateway\n\n");
        gatewaySection.append("All services are accessible through the gateway:\n\n");
        gatewaySection.append("```\n");
        gatewaySection.append("Direct URL:  http://localhost:{port}/api/{endpoint}\n");
        gatewaySection.append("Gateway URL: http://localhost:9999/api/{service-name}/{endpoint}\n");
        gatewaySection.append("```\n\n");

        gatewaySection.append("**Examples:**\n\n");
        for (FractalModule module : modules) {
            String servicePath = module.getServiceName().replace("-service", "");
            gatewaySection.append(String.format("- **%s Service**\n", module.getServiceName()));
            gatewaySection.append(String.format("  - Direct: `http://localhost:%d/api/%s/health`\n",
                    module.getPort(), servicePath));
            gatewaySection.append(String.format("  - Gateway: `http://localhost:9999/api/%s/health`\n\n",
                    servicePath));
        }

        gatewaySection.append("### Start Gateway with Services\n\n");
        gatewaySection.append("The gateway is included in the `start-all.sh` script.\n\n");

        gatewaySection.append("**Note:** The gateway uses static routing (no service discovery).\n");
        gatewaySection.append("Services must be started on their configured ports.\n");

        // Insert gateway section after services section
        String updatedContent = existingContent.replace(
                "## Services\n\n",
                "## Services\n\n" + gatewaySection.toString() + "\n"
        );

        Files.writeString(readmePath, updatedContent);
        log.info("✓ Updated README with gateway information");
    }

    private void generateStartScripts(List<FractalModule> modules) throws IOException {
        // Generate start-all.sh for Unix/Mac
        StringBuilder bashScript = new StringBuilder();
        bashScript.append("#!/bin/bash\n\n");
        bashScript.append("echo \"Starting all FractalX microservices...\"\n\n");

        // Start API Gateway first if it exists
        Path gatewayPath = outputRoot.resolve("fractalx-gateway");
        if (Files.exists(gatewayPath)) {
            bashScript.append("echo \"✅ All services and gateway started successfully!\"\n");
            bashScript.append("echo \"\"\n");
            bashScript.append("echo \"=========================================\"\n");
            bashScript.append("echo \"🔗 Gateway URL: http://localhost:9999\"\n");
            bashScript.append("echo \"=========================================\"\n");
        }

        bashScript.append("# Start all microservices\n");

        for (FractalModule module : modules) {
            bashScript.append(String.format(
                    "echo \"Starting %s on port %d...\"\n",
                    module.getServiceName(),
                    module.getPort()
            ));
            bashScript.append(String.format(
                    "cd %s && mvn spring-boot:run > ../%s.log 2>&1 &\n",
                    module.getServiceName(),
                    module.getServiceName()
            ));
            bashScript.append("cd ..\n\n");
        }

        if (Files.exists(gatewayPath)) {
            bashScript.append("echo \"✅ All services and gateway started successfully!\"\n");
            bashScript.append("echo \"\"\n");
            bashScript.append("echo \"=========================================\"\n");
            bashScript.append("echo \"🔗 Gateway URL: http://localhost:9999\"\n"); // CHANGED PORT
            // REMOVED: API Docs and Health Check messages
            bashScript.append("echo \"=========================================\"\n");
        } else {
            bashScript.append("echo \"All services started. Check logs in *.log files\"\n");
        }

        bashScript.append("echo \"To stop all services, run: ./stop-all.sh\"\n");

        // Show service URLs
        bashScript.append("\necho \"\"\n");
        bashScript.append("echo \"Service URLs:\"\n");
        for (FractalModule module : modules) {
            bashScript.append(String.format("echo \"  %-20s http://localhost:%d\"\n",
                    module.getServiceName() + ":", module.getPort()));
        }

        Path bashScriptPath = outputRoot.resolve("start-all.sh");
        Files.writeString(bashScriptPath, bashScript.toString());
        bashScriptPath.toFile().setExecutable(true);

        // Generate stop-all.sh with gateway support
        StringBuilder stopScript = new StringBuilder();
        stopScript.append("#!/bin/bash\n\n");
        stopScript.append("echo \"Stopping all FractalX microservices...\"\n\n");

        if (Files.exists(gatewayPath)) {
            stopScript.append("# Stop API Gateway\n");
            stopScript.append("echo \"Stopping API Gateway...\"\n");
            stopScript.append("pkill -f \"fractalx-gateway\"\n");
            stopScript.append("sleep 2\n\n");
        }

        stopScript.append("# Stop all microservices\n");
        stopScript.append("echo \"Stopping all services...\"\n");
        stopScript.append("pkill -f \"spring-boot:run\"\n\n");

        stopScript.append("echo \"✅ All services stopped.\"\n");

        Path stopScriptPath = outputRoot.resolve("stop-all.sh");
        Files.writeString(stopScriptPath, stopScript.toString());
        stopScriptPath.toFile().setExecutable(true);

        // Generate README
        generateReadme(modules);

        log.info("✓ Generated start scripts");
    }

    private void generateReadme(List<FractalModule> modules) throws IOException {
        StringBuilder readme = new StringBuilder();
        readme.append("# FractalX Generated Microservices\n\n");
        readme.append("This directory contains microservices generated by FractalX framework.\n\n");

        readme.append("## Services\n\n");
        for (FractalModule module : modules) {
            readme.append(String.format("- **%s**: http://localhost:%d\n",
                    module.getServiceName(), module.getPort()));
        }

        readme.append("\n## Quick Start\n\n");
        readme.append("### Start all services with script\n\n");
        readme.append("```bash\n");
        readme.append("./start-all.sh\n");
        readme.append("```\n\n");

        readme.append("### Start services individually\n\n");
        for (FractalModule module : modules) {
            readme.append(String.format("```bash\n# Terminal %d - Start %s\n",
                    modules.indexOf(module) + 1, module.getServiceName()));
            readme.append(String.format("cd %s\n", module.getServiceName()));
            readme.append("mvn spring-boot:run\n```\n\n");
        }

        readme.append("## Testing\n\n");
        for (FractalModule module : modules) {
            readme.append(String.format("```bash\n# Test %s health\n",
                    module.getServiceName()));
            readme.append(String.format("curl http://localhost:%d/actuator/health\n```\n\n",
                    module.getPort()));
        }

        readme.append("## Stopping Services\n\n");
        readme.append("```bash\n");
        readme.append("./stop-all.sh\n");
        readme.append("```\n\n");

        readme.append("## Service Architecture\n\n");
        readme.append("```\n");
        readme.append("Standalone Services (No Service Discovery)\n");
        readme.append("└── Static Gateway Routing\n");
        for (FractalModule module : modules) {
            readme.append(String.format("    ├── %s (:%d)\n", module.getServiceName(), module.getPort()));
            if (!module.getDependencies().isEmpty()) {
                readme.append("    │   └─ Dependencies: " + String.join(", ", module.getDependencies()) + "\n");
            }
        }
        readme.append("    └── Gateway → http://localhost:9999\n");
        readme.append("```\n");

        Path readmePath = outputRoot.resolve("README.md");
        Files.writeString(readmePath, readme.toString());

        log.info("✓ Generated README.md");
    }
}