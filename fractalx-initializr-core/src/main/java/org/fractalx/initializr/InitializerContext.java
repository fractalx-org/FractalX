package org.fractalx.initializr;

import org.fractalx.initializr.model.ProjectSpec;
import org.fractalx.initializr.model.ServiceSpec;

import java.nio.file.Path;

/**
 * Carries immutable state through every {@link generator.InitializerFileGenerator} step.
 */
public class InitializerContext {

    private final ProjectSpec spec;
    private final Path        outputRoot;

    public InitializerContext(ProjectSpec spec, Path outputRoot) {
        this.spec       = spec;
        this.outputRoot = outputRoot;
    }

    public ProjectSpec spec()       { return spec; }
    public Path        outputRoot() { return outputRoot; }

    /** Absolute path to a service source directory, e.g. {@code src/main/java/com/example/myplatform/order/}. */
    public Path serviceSourceDir(ServiceSpec svc) {
        return outputRoot
                .resolve("src/main/java")
                .resolve(spec.packagePath())
                .resolve(svc.javaPackage());
    }

    /** Absolute path to the test directory for a service. */
    public Path serviceTestDir(ServiceSpec svc) {
        return outputRoot
                .resolve("src/test/java")
                .resolve(spec.packagePath())
                .resolve(svc.javaPackage());
    }

    /** Absolute path to {@code src/main/resources/}. */
    public Path resourcesDir() {
        return outputRoot.resolve("src/main/resources");
    }
}
