package org.fractalx.core.generator.registry;

import org.fractalx.core.config.FractalxConfig;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Orchestrates generation of the fractalx-registry service — a lightweight
 * self-contained service registry used by all generated microservices for
 * runtime service discovery.
 *
 * <p>Each generated service registers itself on startup via
 * {@code ServiceRegistrationStep} and the API Gateway resolves live host/port
 * values from this registry rather than from static YAML.
 */
public class RegistryServiceGenerator {

    private static final Logger log = LoggerFactory.getLogger(RegistryServiceGenerator.class);

    public static final int    REGISTRY_PORT = RegistryConfigGenerator.REGISTRY_PORT;
    public static final String REGISTRY_DIR  = "fractalx-registry";

    private final RegistryPomGenerator         pomGenerator;
    private final RegistryApplicationGenerator appGenerator;
    private final RegistryConfigGenerator      configGenerator;
    private final RegistryControllerGenerator  controllerGenerator;
    private final RegistryModelGenerator       modelGenerator;
    private final RegistryServiceClassGenerator serviceClassGenerator;

    public RegistryServiceGenerator() {
        this.pomGenerator          = new RegistryPomGenerator();
        this.appGenerator          = new RegistryApplicationGenerator();
        this.configGenerator       = new RegistryConfigGenerator();
        this.controllerGenerator   = new RegistryControllerGenerator();
        this.modelGenerator        = new RegistryModelGenerator();
        this.serviceClassGenerator = new RegistryServiceClassGenerator();
    }

    public void generate(List<FractalModule> modules, Path outputRoot) throws IOException {
        generate(modules, outputRoot, FractalxConfig.defaults());
    }

    public void generate(List<FractalModule> modules, Path outputRoot, FractalxConfig config) throws IOException {
        log.info("Generating fractalx-registry service...");

        Path registryRoot     = outputRoot.resolve(REGISTRY_DIR);
        Path srcMainJava      = registryRoot.resolve("src/main/java");
        Path srcMainResources = registryRoot.resolve("src/main/resources");

        Files.createDirectories(srcMainJava);
        Files.createDirectories(srcMainResources);

        pomGenerator.generate(registryRoot, config);
        appGenerator.generate(srcMainJava);
        configGenerator.generate(srcMainResources, modules);
        modelGenerator.generate(srcMainJava);
        serviceClassGenerator.generate(srcMainJava);
        controllerGenerator.generate(srcMainJava);

        log.info("Generated fractalx-registry on port {}", REGISTRY_PORT);
    }
}
