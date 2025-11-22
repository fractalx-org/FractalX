package com.fractalx.maven;

import com.fractalx.core.ModuleAnalyzer;
import com.fractalx.core.FractalModule;
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

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("FractalX decomposition skipped");
            return;
        }

        getLog().info("=".repeat(60));
        getLog().info("FractalX Module Analyzer");
        getLog().info("=".repeat(60));
        getLog().info("Source directory: " + sourceDirectory.getAbsolutePath());
        getLog().info("Output directory: " + outputDirectory.getAbsolutePath());
        getLog().info("");

        try {
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

            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            getLog().info("=".repeat(60));
            getLog().info("Analysis complete! Code generation coming soon...");
            getLog().info("=".repeat(60));

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to analyze modules", e);
        }
    }
}