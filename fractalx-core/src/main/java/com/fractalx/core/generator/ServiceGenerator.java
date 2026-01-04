package com.fractalx.core.generator;

import com.fractalx.core.FractalModule;
import com.fractalx.core.gateway.GatewayGenerator;
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

    private boolean discoveryEnabled = true;
    private int discoveryPort = 8761;
    private int gatewayPort = 9999;
    private int adminPort = 9090;

    public ServiceGenerator(Path sourceRoot, Path outputRoot) {
        this.sourceRoot = sourceRoot;
        this.outputRoot = outputRoot;
    }

    // Configuration setters
    public void setDiscoveryEnabled(boolean discoveryEnabled) {
        this.discoveryEnabled = discoveryEnabled;
    }

    public void setDiscoveryPort(int discoveryPort) {
        this.discoveryPort = discoveryPort;
    }

    public void setGatewayPort(int gatewayPort) {
        this.gatewayPort = gatewayPort;
    }

    public void setAdminPort(int adminPort) {
        this.adminPort = adminPort;
    }

    /**
     * Generate all microservice projects
     */
    public void generateServices(List<FractalModule> modules) throws IOException {
        log.info("Starting code generation for {} modules", modules.size());

        // Create output directory
        Files.createDirectories(outputRoot);

        // Step 1: Generate the standalone discovery service (if enabled)
        if (discoveryEnabled) {
            generateDiscoveryService(modules);
        }

        // Step 2: Generate all regular services
        for (FractalModule module : modules) {
            generateService(module, modules);
        }

        // Step 3: Generate discovery configuration for services
        DiscoveryConfigGenerator discoveryGen = new DiscoveryConfigGenerator();
        discoveryGen.generateDiscoveryConfig(modules, outputRoot);

        // Step 4: Generate API Gateway if we have multiple services
        if (modules.size() > 1) {
            generateApiGateway(modules);
        }

        // Step 5: Generate Admin Service
        AdminServiceGenerator adminGenerator = new AdminServiceGenerator();
        adminGenerator.setAdminPort(adminPort);
        adminGenerator.generateAdminService(modules, outputRoot);

        // Step 6: Generate start scripts
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

        // Step 4: Generate discovery configuration
        generateDiscoveryConfig(module, srcMainResources, allModules);

        // Step 5: Generate discovery client
        DiscoveryClientGenerator discoveryGen = new DiscoveryClientGenerator();
        discoveryGen.generateDiscoveryClient(module, srcMainJava);

        // Step 6: Copy module code
        CodeCopier codeCopier = new CodeCopier(sourceRoot);
        codeCopier.copyModuleCode(module, srcMainJava);

        // Step 7: Transform code using AST (remove annotations, clean imports)
        CodeTransformer transformer = new CodeTransformer();
        transformer.transformCode(serviceRoot, module);

        // Step 8: Generate Feign clients for cross-service communication
        FeignClientGenerator feignGen = new FeignClientGenerator();
        feignGen.generateFeignClients(module, srcMainJava, allModules);

        log.info("✓ Generated: {}", module.getServiceName());
    }

    private void generateDiscoveryConfig(FractalModule module, Path srcMainResources,
                                         List<FractalModule> allModules) throws IOException {
        log.debug("Generating discovery config for: {}", module.getServiceName());

        // Generate discovery-config.yml
        String discoveryConfig = generateDiscoveryConfigYml(module, allModules);
        Path discoveryConfigPath = srcMainResources.resolve("discovery-config.yml");
        Files.writeString(discoveryConfigPath, discoveryConfig);

        // Generate bootstrap configuration
        String bootstrapConfig = generateBootstrapConfig(module);
        Path bootstrapPath = srcMainResources.resolve("bootstrap.yml");
        Files.writeString(bootstrapPath, bootstrapConfig);
    }

    private String generateDiscoveryConfigYml(FractalModule module, List<FractalModule> allModules) {
        StringBuilder yml = new StringBuilder();
        yml.append("# Discovery Configuration for ").append(module.getServiceName()).append("\n");
        yml.append("# Auto-generated by FractalX Framework\n\n");

        yml.append("fractalx:\n");
        yml.append("  discovery:\n");
        yml.append("    enabled: true\n");
        yml.append("    mode: HYBRID\n");
        yml.append("    registry-url: http://localhost:8761\n");
        yml.append("    heartbeat-interval: 30000\n");
        yml.append("    instance-ttl: 90000\n\n");

        yml.append("# Static service instances (for fallback)\n");
        yml.append("instances:\n");

        // Include all services in static config for redundancy
        for (FractalModule m : allModules) {
            yml.append("  ").append(m.getServiceName()).append(":\n");
            yml.append("    - host: localhost\n");
            yml.append("      port: ").append(m.getPort()).append("\n");

            // FIX: Add dependencies as metadata
            if (!m.getDependencies().isEmpty()) {
                yml.append("      metadata:\n");
                yml.append("        dependencies:\n");
                for (String dep : m.getDependencies()) {
                    yml.append("          - ").append(dep).append("\n");
                }
            }
            yml.append("\n");
        }

        return yml.toString();
    }

    private String generateBootstrapConfig(FractalModule module) {
        return String.format("""
            # Bootstrap configuration for %s
            spring:
              application:
                name: %s
              cloud:
                discovery:
                  enabled: true
                  service-id: %s
                  
            # Initial service registry
            service:
              registry:
                initial-delay: 5000
                retry-count: 3
                retry-delay: 2000
            """, module.getServiceName(), module.getServiceName(), module.getServiceName());
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

    //-----------------------------------------------------------------

    /**
     * Generate standalone discovery service
     */
    private void generateDiscoveryService(List<FractalModule> modules) throws IOException {
        log.info("Generating Discovery Service on port {}...", discoveryPort);

        // Create discovery service directory
        Path discoveryRoot = outputRoot.resolve("discovery-service");
        Path srcMainJava = discoveryRoot.resolve("src/main/java");
        Path srcMainResources = discoveryRoot.resolve("src/main/resources");

        Files.createDirectories(srcMainJava);
        Files.createDirectories(srcMainResources);

        // Generate POM for discovery service
        generateDiscoveryPom(discoveryRoot, modules);

        // Generate Application class
        generateDiscoveryApplication(srcMainJava);

        // Generate configuration
        generateDiscoveryConfig(srcMainResources, modules);

        // Generate start scripts (cross-platform)
        generateDiscoveryStartScripts(discoveryRoot, modules);

        log.info("✓ Generated Discovery Service on port 8761");
    }

    private void generateDiscoveryPom(Path discoveryRoot, List<FractalModule> modules) throws IOException {
        String pomContent = generateDiscoveryPomContent(modules);
        Files.writeString(discoveryRoot.resolve("pom.xml"), pomContent);
    }

    private String generateDiscoveryPomContent(List<FractalModule> modules) {
        StringBuilder pom = new StringBuilder();
        pom.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        pom.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        pom.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        pom.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        pom.append("    <modelVersion>4.0.0</modelVersion>\n");
        pom.append("\n");
        pom.append("    <groupId>com.fractalx.generated</groupId>\n");
        pom.append("    <artifactId>discovery-service</artifactId>\n");
        pom.append("    <version>1.0.0-SNAPSHOT</version>\n");
        pom.append("    <packaging>jar</packaging>\n");
        pom.append("\n");
        pom.append("    <name>FractalX Discovery Service</name>\n");
        pom.append("    <description>Service discovery for FractalX microservices</description>\n");
        pom.append("\n");
        pom.append("    <properties>\n");
        pom.append("        <java.version>17</java.version>\n");
        pom.append("        <spring-boot.version>3.2.0</spring-boot.version>\n");
        pom.append("        <maven.compiler.source>17</maven.compiler.source>\n");
        pom.append("        <maven.compiler.target>17</maven.compiler.target>\n");
        pom.append("        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n");
        pom.append("    </properties>\n");
        pom.append("\n");
        pom.append("    <dependencyManagement>\n");
        pom.append("        <dependencies>\n");
        pom.append("            <dependency>\n");
        pom.append("                <groupId>org.springframework.boot</groupId>\n");
        pom.append("                <artifactId>spring-boot-dependencies</artifactId>\n");
        pom.append("                <version>${spring-boot.version}</version>\n");
        pom.append("                <type>pom</type>\n");
        pom.append("                <scope>import</scope>\n");
        pom.append("            </dependency>\n");
        pom.append("        </dependencies>\n");
        pom.append("    </dependencyManagement>\n");
        pom.append("\n");
        pom.append("    <dependencies>\n");
        pom.append("        <dependency>\n");
        pom.append("            <groupId>org.springframework.boot</groupId>\n");
        pom.append("            <artifactId>spring-boot-starter-web</artifactId>\n");
        pom.append("        </dependency>\n");
        pom.append("\n");
        pom.append("        <dependency>\n");
        pom.append("            <groupId>com.fractalx</groupId>\n");
        pom.append("            <artifactId>fractalx-core</artifactId>\n");
        pom.append("            <version>0.1.0-SNAPSHOT</version>\n");
        pom.append("        </dependency>\n");
        pom.append("\n");
        pom.append("        <dependency>\n");
        pom.append("            <groupId>org.springframework.boot</groupId>\n");
        pom.append("            <artifactId>spring-boot-starter-actuator</artifactId>\n");
        pom.append("        </dependency>\n");
        pom.append("\n");
        pom.append("        <dependency>\n");
        pom.append("            <groupId>org.yaml</groupId>\n");
        pom.append("            <artifactId>snakeyaml</artifactId>\n");
        pom.append("            <version>2.2</version>\n");
        pom.append("        </dependency>\n");
        pom.append("    </dependencies>\n");
        pom.append("\n");
        pom.append("    <build>\n");
        pom.append("        <plugins>\n");
        pom.append("            <plugin>\n");
        pom.append("                <groupId>org.springframework.boot</groupId>\n");
        pom.append("                <artifactId>spring-boot-maven-plugin</artifactId>\n");
        pom.append("                <version>${spring-boot.version}</version>\n");
        pom.append("            </plugin>\n");
        pom.append("        </plugins>\n");
        pom.append("    </build>\n");
        pom.append("</project>\n");

        return pom.toString();
    }

    private void generateDiscoveryApplication(Path srcMainJava) throws IOException {
        Path packagePath = srcMainJava.resolve("com/fractalx/discovery");
        Files.createDirectories(packagePath);

        String appContent = """
        package com.fractalx.discovery;
        
        import com.fractalx.core.discovery.DiscoveryServiceApplication;
        import org.springframework.boot.SpringApplication;
        import org.springframework.boot.autoconfigure.SpringBootApplication;
        
        @SpringBootApplication
        public class DiscoveryApplication {
            
            public static void main(String[] args) {
                SpringApplication.run(DiscoveryServiceApplication.class, args);
            }
        }
        """;

        Files.writeString(packagePath.resolve("DiscoveryApplication.java"), appContent);
    }

    private void generateDiscoveryConfig(Path srcMainResources, List<FractalModule> modules) throws IOException {
        // Generate application.yml
        String appYml = generateDiscoveryAppYml(modules);
        Files.writeString(srcMainResources.resolve("application.yml"), appYml);

        // Generate static-services.yml
        String staticServices = generateStaticServicesYml(modules);
        Files.writeString(srcMainResources.resolve("static-services.yml"), staticServices);
    }

    private String generateDiscoveryAppYml(List<FractalModule> modules) {
        StringBuilder yml = new StringBuilder();
        yml.append("spring:\n");
        yml.append("  application:\n");
        yml.append("    name: discovery-service\n");
        yml.append("\n");
        yml.append("server:\n");
        yml.append("  port: 8761\n");
        yml.append("\n");
        yml.append("fractalx:\n");
        yml.append("  discovery:\n");
        yml.append("    enabled: true\n");
        yml.append("    mode: DYNAMIC\n");
        yml.append("    host: localhost\n");
        yml.append("    port: 8761\n");
        yml.append("    heartbeat-interval: 30000\n");
        yml.append("    instance-ttl: 90000\n");
        yml.append("    auto-cleanup: true\n");
        yml.append("    auto-register: true\n");
        yml.append("\n");
        yml.append("# Static configuration for initial bootstrap\n");
        yml.append("discovery:\n");
        yml.append("  static-config:\n");
        yml.append("    enabled: true\n");
        yml.append("    file: classpath:static-services.yml\n");
        yml.append("\n");
        yml.append("management:\n");
        yml.append("  endpoints:\n");
        yml.append("    web:\n");
        yml.append("      exposure:\n");
        yml.append("        include: health,info,metrics,discovery\n");
        yml.append("  endpoint:\n");
        yml.append("    health:\n");
        yml.append("      show-details: always\n");
        yml.append("\n");
        yml.append("logging:\n");
        yml.append("  level:\n");
        yml.append("    com.fractalx.core.discovery: DEBUG\n");
        yml.append("    com.fractalx.discovery: INFO\n");

        return yml.toString();
    }

    private String generateStaticServicesYml(List<FractalModule> modules) {
        StringBuilder yml = new StringBuilder();
        yml.append("# Static Services Configuration\n");
        yml.append("# Used for initial discovery service bootstrap\n");
        yml.append("\n");
        yml.append("instances:\n");
        yml.append("  discovery-service:\n");
        yml.append("    - host: localhost\n");
        yml.append("      port: 8761\n");
        yml.append("      metadata:\n");
        yml.append("        role: registry\n");
        yml.append("        version: \"1.0\"\n");
        yml.append("\n");

        // Add all generated services
        for (FractalModule module : modules) {
            yml.append("  ").append(module.getServiceName()).append(":\n");
            yml.append("    - host: localhost\n");
            yml.append("      port: ").append(module.getPort()).append("\n");
            yml.append("      metadata:\n");
            yml.append("        generated: true\n");
            yml.append("        framework: fractalx\n");
            yml.append("\n");
        }

        // Add gateway if it will be generated
        yml.append("  fractalx-gateway:\n");
        yml.append("    - host: localhost\n");
        yml.append("      port: 9999\n");
        yml.append("      metadata:\n");
        yml.append("        role: gateway\n");
        yml.append("        version: \"1.0\"\n");

        return yml.toString();
    }

    private void generateDiscoveryStartScripts(Path discoveryRoot, List<FractalModule> modules) throws IOException {
        // Generate start.sh (Unix/Linux/Mac)
        String startSh = """
        #!/bin/bash
        
        echo "=========================================="
        echo "Starting FractalX Discovery Service"
        echo "=========================================="
        echo "Port: 8761"
        echo "Health Check: http://localhost:8761/api/discovery/health"
        echo "Services Endpoint: http://localhost:8761/api/discovery/services"
        echo ""
        
        mvn spring-boot:run \
          -Dspring-boot.run.jvmArguments="-Dserver.port=8761" \
          -Dspring-boot.run.arguments="--server.port=8761"
        """;

        Files.writeString(discoveryRoot.resolve("start.sh"), startSh);
        discoveryRoot.resolve("start.sh").toFile().setExecutable(true);

        // Generate start.bat (Windows)
        String startBat = """
        @echo off
        echo ==========================================
        echo Starting FractalX Discovery Service
        echo ==========================================
        echo Port: 8761
        echo Health Check: http://localhost:8761/api/discovery/health
        echo Services Endpoint: http://localhost:8761/api/discovery/services
        echo.
        
        mvn spring-boot:run ^
          -Dspring-boot.run.jvmArguments="-Dserver.port=8761" ^
          -Dspring-boot.run.arguments="--server.port=8761"
        """;

        Files.writeString(discoveryRoot.resolve("start.bat"), startBat);
    }

}