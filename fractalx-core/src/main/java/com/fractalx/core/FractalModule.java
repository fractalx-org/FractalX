package com.fractalx.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a decomposable module in the FractalX framework
 */
public class FractalModule {
    private String className;
    private String packageName;
    private String serviceName;
    private int port;
    private boolean independentDeployment;
    private List<String> dependencies = new ArrayList<>();
    private List<String> ownedSchemas = new ArrayList<>();

    // Getters and setters
    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isIndependentDeployment() {
        return independentDeployment;
    }

    public void setIndependentDeployment(boolean independentDeployment) {
        this.independentDeployment = independentDeployment;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public List<String> getOwnedSchemas() {
        return ownedSchemas;
    }

    public void setOwnedSchemas(List<String> ownedSchemas) {
        this.ownedSchemas = ownedSchemas;
    }

    @Override
    public String toString() {
        return "FractalModule{" +
                "serviceName='" + serviceName + '\'' +
                ", className='" + className + '\'' +
                ", port=" + port +
                ", dependencies=" + dependencies +
                '}';
    }
}