package com.fractalx.core.generator;

import com.fractalx.core.FractalModule;
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

    public ServiceGenerator(Path sourceRoot, Path outputRoot) {
        this.sourceRoot = sourceRoot;
        this.outputRoot = outputRoot;
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

        log.info("✓ Generated: {}", module.getServiceName());
    }

    private void generateStartScripts(List<FractalModule> modules) throws IOException {
        // Generate start-all.sh for Unix/Mac
        StringBuilder bashScript = new StringBuilder();
        bashScript.append("#!/bin/bash\n\n");
        bashScript.append("echo \"Starting all FractalX microservices...\"\n\n");

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

        bashScript.append("echo \"All services started. Check logs in *.log files\"\n");
        bashScript.append("echo \"To stop all services, run: ./stop-all.sh\"\n");

        Path bashScriptPath = outputRoot.resolve("start-all.sh");
        Files.writeString(bashScriptPath, bashScript.toString());
        bashScriptPath.toFile().setExecutable(true);

        // Generate stop-all.sh
        String stopScript = """
            #!/bin/bash
            
            echo "Stopping all FractalX microservices..."
            
            # Find and kill all Maven processes running our services
            pkill -f "spring-boot:run"
            
            echo "All services stopped."
            """;

        Path stopScriptPath = outputRoot.resolve("stop-all.sh");
        Files.writeString(stopScriptPath, stopScript);
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
        for (FractalModule module : modules) {
            readme.append(String.format("%s (:%d)\n", module.getServiceName(), module.getPort()));
            if (!module.getDependencies().isEmpty()) {
                readme.append("  └─ Dependencies: " + String.join(", ", module.getDependencies()) + "\n");
            }
        }
        readme.append("```\n");

        Path readmePath = outputRoot.resolve("README.md");
        Files.writeString(readmePath, readme.toString());

        log.info("✓ Generated README.md");
    }
}