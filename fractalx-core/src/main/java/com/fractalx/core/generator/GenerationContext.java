package com.fractalx.core.generator;

import com.fractalx.core.model.FractalModule;

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

    public GenerationContext(FractalModule module,
                             Path sourceRoot,
                             Path serviceRoot,
                             List<FractalModule> allModules) {
        this.module = module;
        this.sourceRoot = sourceRoot;
        this.serviceRoot = serviceRoot;
        this.allModules = List.copyOf(allModules);
    }

    public FractalModule getModule()              { return module; }
    public Path getSourceRoot()                   { return sourceRoot; }
    public Path getServiceRoot()                  { return serviceRoot; }
    public List<FractalModule> getAllModules()     { return allModules; }

    /** Resolves {@code src/main/java} under the service root. */
    public Path getSrcMainJava()      { return serviceRoot.resolve("src/main/java"); }

    /** Resolves {@code src/main/resources} under the service root. */
    public Path getSrcMainResources() { return serviceRoot.resolve("src/main/resources"); }
}
