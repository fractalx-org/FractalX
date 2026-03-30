package org.fractalx.initializr.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level project specification — maps directly to fractalx.yaml {@code project} block.
 */
public class ProjectSpec {

    private String groupId         = "com.example";
    private String artifactId      = "my-platform";
    private String version         = "1.0.0-SNAPSHOT";
    private String javaVersion     = "17";
    private String springBootVersion = "3.2.5";
    private String packageName;   // derived from groupId + artifactId if null
    private String description     = "FractalX modular monolith";

    private List<ServiceSpec>  services       = new ArrayList<>();
    private List<SagaSpec>     sagas          = new ArrayList<>();
    private InfraSpec          infrastructure = new InfraSpec();
    private SecuritySpec       security       = new SecuritySpec();

    // ── Derived helpers ────────────────────────────────────────────────────────

    /**
     * Base Java package: uses explicit {@code packageName} if set, otherwise
     * derives it as {@code groupId + "." + sanitize(artifactId)}.
     */
    public String resolvedPackage() {
        if (packageName != null && !packageName.isBlank()) return packageName;
        String suffix = artifactId.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return groupId + "." + suffix;
    }

    /** e.g. "com/example/myplatform" */
    public String packagePath() {
        return resolvedPackage().replace('.', '/');
    }

    /** Application class name: capitalised artifact words, e.g. "MyPlatformApplication" */
    public String applicationClassName() {
        String[] parts = artifactId.split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase());
        }
        return sb.append("Application").toString();
    }

    // ── Getters / setters (Jackson-friendly) ──────────────────────────────────

    public String getGroupId()               { return groupId; }
    public void   setGroupId(String v)       { this.groupId = v; }

    public String getArtifactId()            { return artifactId; }
    public void   setArtifactId(String v)    { this.artifactId = v; }

    public String getVersion()               { return version; }
    public void   setVersion(String v)       { this.version = v; }

    public String getJavaVersion()           { return javaVersion; }
    public void   setJavaVersion(String v)   { this.javaVersion = v; }

    public String getSpringBootVersion()     { return springBootVersion; }
    public void   setSpringBootVersion(String v) { this.springBootVersion = v; }

    public String getPackageName()           { return packageName; }
    public void   setPackageName(String v)   { this.packageName = v; }

    public String getDescription()           { return description; }
    public void   setDescription(String v)   { this.description = v; }

    public List<ServiceSpec> getServices()         { return services; }
    public void              setServices(List<ServiceSpec> v) { this.services = v; }

    public List<SagaSpec>    getSagas()            { return sagas; }
    public void              setSagas(List<SagaSpec> v) { this.sagas = v; }

    public InfraSpec         getInfrastructure()   { return infrastructure; }
    public void              setInfrastructure(InfraSpec v) { this.infrastructure = v; }

    public SecuritySpec      getSecurity()         { return security; }
    public void              setSecurity(SecuritySpec v) { this.security = v; }
}
