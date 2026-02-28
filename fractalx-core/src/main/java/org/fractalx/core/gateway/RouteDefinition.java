package org.fractalx.core.gateway;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an API route definition for the gateway
 */
public class RouteDefinition {
    private String id;
    private String path;
    private String method;
    private String serviceName;
    private int servicePort;
    private String destinationPath;
    private List<String> filters = new ArrayList<>();

    // Constructor
    public RouteDefinition(String serviceName, String path, String method, int servicePort) {
        this.serviceName = serviceName;
        this.path = path;
        this.method = method;
        this.servicePort = servicePort;
        this.id = serviceName + "-" + path.replace("/", "-") + "-" + method.toLowerCase();
        this.destinationPath = path;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public int getServicePort() { return servicePort; }
    public void setServicePort(int servicePort) { this.servicePort = servicePort; }

    public String getDestinationPath() { return destinationPath; }
    public void setDestinationPath(String destinationPath) { this.destinationPath = destinationPath; }

    public List<String> getFilters() { return filters; }
    public void setFilters(List<String> filters) { this.filters = filters; }

    @Override
    public String toString() {
        return String.format("Route[%s %s -> %s:%d%s]",
                method, path, serviceName, servicePort, destinationPath);
    }
}