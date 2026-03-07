package org.fractalx.core.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a decomposable module identified during analysis.
 * Use {@link Builder} to construct instances with validated state.
 */
public class FractalModule {

    private final String className;
    private final String packageName;
    private final String serviceName;
    private final int port;
    private final boolean independentDeployment;
    private final List<String> dependencies;
    private final List<String> ownedSchemas;
    /** Full import strings collected from every source file in this module's package tree. */
    private final Set<String> detectedImports;

    private FractalModule(Builder builder) {
        this.className = builder.className;
        this.packageName = builder.packageName;
        this.serviceName = builder.serviceName;
        this.port = builder.port;
        this.independentDeployment = builder.independentDeployment;
        this.dependencies = List.copyOf(builder.dependencies);
        this.ownedSchemas = List.copyOf(builder.ownedSchemas);
        this.detectedImports = Set.copyOf(builder.detectedImports);
    }

    public String getClassName()             { return className; }
    public String getPackageName()           { return packageName; }
    public String getServiceName()           { return serviceName; }
    public int    getPort()                  { return port; }
    public boolean isIndependentDeployment() { return independentDeployment; }
    public List<String> getDependencies()    { return dependencies; }
    public List<String> getOwnedSchemas()    { return ownedSchemas; }
    /** All Java import strings found across the module's source files. */
    public Set<String> getDetectedImports()  { return detectedImports; }

    @Override
    public String toString() {
        return "FractalModule{serviceName='" + serviceName + "', className='" + className
                + "', port=" + port + ", dependencies=" + dependencies + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String className;
        private String packageName;
        private String serviceName;
        private int port;
        private boolean independentDeployment = true;
        private final List<String> dependencies = new ArrayList<>();
        private final List<String> ownedSchemas = new ArrayList<>();
        private final Set<String> detectedImports = new LinkedHashSet<>();

        private Builder() {}

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder port(int port) {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535, got: " + port);
            }
            this.port = port;
            return this;
        }

        public Builder independentDeployment(boolean independentDeployment) {
            this.independentDeployment = independentDeployment;
            return this;
        }

        public Builder dependencies(List<String> dependencies) {
            this.dependencies.addAll(dependencies);
            return this;
        }

        public Builder ownedSchemas(List<String> ownedSchemas) {
            this.ownedSchemas.addAll(ownedSchemas);
            return this;
        }

        public Builder detectedImports(Set<String> imports) {
            this.detectedImports.addAll(imports);
            return this;
        }

        public FractalModule build() {
            if (serviceName == null || serviceName.isBlank()) {
                throw new IllegalStateException("serviceName is required");
            }
            return new FractalModule(this);
        }
    }
}
