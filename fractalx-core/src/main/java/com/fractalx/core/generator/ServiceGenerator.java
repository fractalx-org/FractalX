package com.fractalx.core.generator;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

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
            generateService(module);
        }

        log.info("Code generation complete!");
    }

    /**
     * Generate a single microservice project
     */
    private void generateService(FractalModule module) throws IOException {
        log.info("Generating service: {}", module.getServiceName());

        // Create service directory structure
        Path serviceRoot = outputRoot.resolve(module.getServiceName());
        Path srcMainJava = serviceRoot.resolve("src/main/java");
        Path srcMainResources = serviceRoot.resolve("src/main/resources");
        Path srcTestJava = serviceRoot.resolve("src/test/java");

        Files.createDirectories(srcMainJava);
        Files.createDirectories(srcMainResources);
        Files.createDirectories(srcTestJava);

        // Generate components
        PomGenerator pomGen = new PomGenerator();
        pomGen.generatePom(module, serviceRoot);

        ApplicationGenerator appGen = new ApplicationGenerator();
        appGen.generateApplicationClass(module, srcMainJava);

        ConfigurationGenerator configGen = new ConfigurationGenerator();
        configGen.generateApplicationYml(module, srcMainResources);

        CodeCopier codeCopier = new CodeCopier(sourceRoot);
        codeCopier.copyModuleCode(module, srcMainJava);

        log.info("✓ Generated: {}", module.getServiceName());
    }
}