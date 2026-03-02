package org.fractalx.core.generator;

import org.fractalx.core.config.FractalxConfig;
import org.fractalx.core.model.FractalModule;
import org.fractalx.core.model.SagaDefinition;

import java.nio.file.Path;
import java.util.List;

/**
 * Carries all inputs needed by a {@link ServiceFileGenerator} step.
 * Provides convenience accessors for common sub-directories.
 */
public final class GenerationContext {

    private final FractalModule module;
    private final Path sourceRoot;
    private final Path serviceRoot;
    private final List<FractalModule> allModules;
    private final FractalxConfig fractalxConfig;
    private final List<SagaDefinition> sagaDefinitions;

    public GenerationContext(FractalModule module,
                             Path sourceRoot,
                             Path serviceRoot,
                             List<FractalModule> allModules,
                             FractalxConfig fractalxConfig,
                             List<SagaDefinition> sagaDefinitions) {
        this.module = module;
        this.sourceRoot = sourceRoot;
        this.serviceRoot = serviceRoot;
        this.allModules = List.copyOf(allModules);
        this.fractalxConfig = fractalxConfig;
        this.sagaDefinitions = List.copyOf(sagaDefinitions);
    }

    public FractalModule getModule()                   { return module; }
    public Path getSourceRoot()                        { return sourceRoot; }
    public Path getServiceRoot()                       { return serviceRoot; }
    public List<FractalModule> getAllModules()          { return allModules; }
    public FractalxConfig getFractalxConfig()          { return fractalxConfig; }
    public List<SagaDefinition> getSagaDefinitions()   { return sagaDefinitions; }

    /** Resolves {@code src/main/java} under the service root. */
    public Path getSrcMainJava()      { return serviceRoot.resolve("src/main/java"); }

    /** Resolves {@code src/main/resources} under the service root. */
    public Path getSrcMainResources() { return serviceRoot.resolve("src/main/resources"); }
}
