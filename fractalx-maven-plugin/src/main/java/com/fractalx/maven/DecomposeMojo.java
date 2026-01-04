package com.fractalx.maven;

import com.fractalx.core.ModuleAnalyzer;
import com.fractalx.core.FractalModule;
import com.fractalx.core.generator.ServiceGenerator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Maven goal to analyze and decompose modular monolith
 */
@Mojo(name = "decompose", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class DecomposeMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "fractalx.sourceDirectory",
            defaultValue = "${project.build.sourceDirectory}")
    private File sourceDirectory;

    @Parameter(property = "fractalx.outputDirectory",
            defaultValue = "${project.build.directory}/generated-services")
    private File outputDirectory;

    @Parameter(property = "fractalx.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "fractalx.generate", defaultValue = "true")
    private boolean generate;

    // NEW: Parameter to include discovery service
    @Parameter(property = "fractalx.includeDiscoveryService",
            defaultValue = "true")
    private boolean includeDiscoveryService;

    // NEW: Parameter for discovery service port
    @Parameter(property = "fractalx.discoveryPort",
            defaultValue = "8761")
    private int discoveryPort;

    // NEW: Parameter for API Gateway port
    @Parameter(property = "fractalx.gatewayPort",
            defaultValue = "9999")
    private int gatewayPort;

    // NEW: Parameter for admin service port
    @Parameter(property = "fractalx.adminPort",
            defaultValue = "9090")
    private int adminPort;

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("FractalX decomposition skipped");
            return;
        }

        getLog().info("=".repeat(60));
        getLog().info("FractalX Module Analyzer & Generator");
        getLog().info("=".repeat(60));
        getLog().info("Source directory: " + sourceDirectory.getAbsolutePath());
        getLog().info("Output directory: " + outputDirectory.getAbsolutePath());
        getLog().info("Discovery Service: " + (includeDiscoveryService ? "Enabled (port: " + discoveryPort + ")" : "Disabled"));
        getLog().info("");

        try {
            // Step 1: Analyze modules
            ModuleAnalyzer analyzer = new ModuleAnalyzer();
            Path sourcePath = sourceDirectory.toPath();

            List<FractalModule> modules = analyzer.analyzeProject(sourcePath);

            if (modules.isEmpty()) {
                getLog().warn("No decomposable modules found!");
                getLog().warn("Make sure your classes are annotated with @DecomposableModule");
                return;
            }

            getLog().info("Found " + modules.size() + " decomposable module(s):");
            getLog().info("");

            for (FractalModule module : modules) {
                getLog().info("  📦 " + module.getServiceName());
                getLog().info("     Class: " + module.getClassName());
                getLog().info("     Port: " + module.getPort());
                getLog().info("     Independent Deployment: " + module.isIndependentDeployment());

                if (!module.getDependencies().isEmpty()) {
                    getLog().info("     Dependencies: " + module.getDependencies());
                }

                getLog().info("");
            }

            // Step 2: Generate services
            if (generate) {
                getLog().info("=".repeat(60));
                getLog().info("Starting Code Generation...");
                getLog().info("=".repeat(60));

                // Create generator with configuration
                ServiceGenerator generator = new ServiceGenerator(
                        sourcePath,
                        outputDirectory.toPath()
                );

                // Pass configuration to generator
                generator.setDiscoveryEnabled(includeDiscoveryService);
                generator.setDiscoveryPort(discoveryPort);
                generator.setGatewayPort(gatewayPort);
                generator.setAdminPort(adminPort);

                generator.generateServices(modules);

                getLog().info("");
                getLog().info("=".repeat(60));
                getLog().info("✅ Code generation complete!");
                getLog().info("=".repeat(60));
                getLog().info("Generated services location: " + outputDirectory.getAbsolutePath());
                getLog().info("");

                // Show service information
                getLog().info("Services Generated:");
                if (includeDiscoveryService) {
                    getLog().info("  🔍 Discovery Service: http://localhost:" + discoveryPort);
                }
                for (FractalModule module : modules) {
                    getLog().info("  📦 " + module.getServiceName() + ": http://localhost:" + module.getPort());
                }
                getLog().info("  🌐 API Gateway: http://localhost:" + gatewayPort);
                getLog().info("  👨‍💼 Admin Service: http://localhost:" + adminPort);
                getLog().info("");

                getLog().info("Next steps:");
                getLog().info("  1. cd " + outputDirectory.getAbsolutePath());
                getLog().info("  2. Start services in order:");
                if (includeDiscoveryService) {
                    getLog().info("     a. cd discovery-service && mvn spring-boot:run");
                }
                getLog().info("     b. cd <service-name> && mvn spring-boot:run");
                getLog().info("     c. cd fractalx-gateway && mvn spring-boot:run");
            } else {
                getLog().info("=".repeat(60));
                getLog().info("Code generation skipped (fractalx.generate=false)");
                getLog().info("=".repeat(60));
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to decompose modules", e);
        }
    }
}