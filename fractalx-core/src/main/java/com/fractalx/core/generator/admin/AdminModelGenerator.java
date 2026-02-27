package com.fractalx.core.generator.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates model classes for the admin service. */
class AdminModelGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminModelGenerator.class);

    void generate(Path srcMainJava, String basePackage) throws IOException {
        Path packagePath = AdminPackageUtil.createPackagePath(srcMainJava, basePackage + ".model");
        generateServiceInfo(packagePath);
        generateServiceDetail(packagePath);
        generateNetScopeLink(packagePath);
        generateSagaInfo(packagePath);
        log.debug("Generated admin model classes");
    }

    private void generateServiceInfo(Path pkg) throws IOException {
        String content = """
                package com.fractalx.admin.model;

                public class ServiceInfo {
                    private String name;
                    private String url;
                    private boolean running;

                    public ServiceInfo(String name, String url, boolean running) {
                        this.name    = name;
                        this.url     = url;
                        this.running = running;
                    }

                    public String  getName()    { return name; }
                    public void    setName(String name)       { this.name = name; }
                    public String  getUrl()     { return url; }
                    public void    setUrl(String url)         { this.url = url; }
                    public boolean isRunning()  { return running; }
                    public void    setRunning(boolean running){ this.running = running; }
                    public String  getStatus()      { return running ? "Running" : "Stopped"; }
                    public String  getStatusClass() { return running ? "success" : "danger"; }
                }
                """;
        Files.writeString(pkg.resolve("ServiceInfo.java"), content);
    }

    private void generateServiceDetail(Path pkg) throws IOException {
        String content = """
                package com.fractalx.admin.model;

                import java.util.List;
                import java.util.Map;

                /** Extended service metadata combining static config with live health status. */
                public class ServiceDetail {
                    private String              name;
                    private int                 port;
                    private int                 grpcPort;
                    private String              type;
                    private List<String>        dependencies;
                    private String              healthStatus;    // UP | DOWN | UNKNOWN
                    private Map<String, String> lifecycleCommands;

                    public String              getName()               { return name; }
                    public void                setName(String n)       { this.name = n; }
                    public int                 getPort()               { return port; }
                    public void                setPort(int p)          { this.port = p; }
                    public int                 getGrpcPort()           { return grpcPort; }
                    public void                setGrpcPort(int g)      { this.grpcPort = g; }
                    public String              getType()               { return type; }
                    public void                setType(String t)       { this.type = t; }
                    public List<String>        getDependencies()       { return dependencies; }
                    public void                setDependencies(List<String> d) { this.dependencies = d; }
                    public String              getHealthStatus()       { return healthStatus; }
                    public void                setHealthStatus(String h){ this.healthStatus = h; }
                    public Map<String, String> getLifecycleCommands()  { return lifecycleCommands; }
                    public void                setLifecycleCommands(Map<String, String> c) { this.lifecycleCommands = c; }
                }
                """;
        Files.writeString(pkg.resolve("ServiceDetail.java"), content);
    }

    private void generateNetScopeLink(Path pkg) throws IOException {
        String content = """
                package com.fractalx.admin.model;

                /** Represents a NetScope/gRPC communication link between two generated services. */
                public class NetScopeLink {
                    private String sourceService;
                    private String targetService;
                    private int    grpcPort;
                    private String protocol;

                    public NetScopeLink(String sourceService, String targetService, int grpcPort) {
                        this.sourceService = sourceService;
                        this.targetService = targetService;
                        this.grpcPort      = grpcPort;
                        this.protocol      = "NetScope/gRPC";
                    }

                    public String getSourceService() { return sourceService; }
                    public String getTargetService() { return targetService; }
                    public int    getGrpcPort()      { return grpcPort; }
                    public String getProtocol()      { return protocol; }
                }
                """;
        Files.writeString(pkg.resolve("NetScopeLink.java"), content);
    }

    private void generateSagaInfo(Path pkg) throws IOException {
        String content = """
                package com.fractalx.admin.model;

                import java.util.List;

                /** Describes a saga orchestration definition baked in at generation time. */
                public class SagaInfo {
                    private String       sagaId;
                    private String       orchestratedBy;
                    private List<String> steps;
                    private List<String> compensationSteps;
                    private boolean      enabled;

                    public String       getSagaId()           { return sagaId; }
                    public void         setSagaId(String s)   { this.sagaId = s; }
                    public String       getOrchestratedBy()   { return orchestratedBy; }
                    public void         setOrchestratedBy(String o) { this.orchestratedBy = o; }
                    public List<String> getSteps()            { return steps; }
                    public void         setSteps(List<String> s) { this.steps = s; }
                    public List<String> getCompensationSteps(){ return compensationSteps; }
                    public void         setCompensationSteps(List<String> c) { this.compensationSteps = c; }
                    public boolean      isEnabled()           { return enabled; }
                    public void         setEnabled(boolean e) { this.enabled = e; }
                }
                """;
        Files.writeString(pkg.resolve("SagaInfo.java"), content);
    }
}
