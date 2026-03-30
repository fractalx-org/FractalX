package org.fractalx.initializr.model;

/**
 * Infrastructure components to generate alongside the monolith source.
 */
public class InfraSpec {

    private boolean gateway         = true;
    private boolean admin           = true;
    private boolean serviceRegistry = true;
    private boolean docker          = true;
    private boolean kubernetes      = false;
    /** CI provider: {@code github-actions} | {@code none}. */
    private String  ci              = "github-actions";

    public boolean isGateway()               { return gateway; }
    public void    setGateway(boolean v)     { this.gateway = v; }

    public boolean isAdmin()                 { return admin; }
    public void    setAdmin(boolean v)       { this.admin = v; }

    public boolean isServiceRegistry()       { return serviceRegistry; }
    public void    setServiceRegistry(boolean v) { this.serviceRegistry = v; }

    public boolean isDocker()                { return docker; }
    public void    setDocker(boolean v)      { this.docker = v; }

    public boolean isKubernetes()            { return kubernetes; }
    public void    setKubernetes(boolean v)  { this.kubernetes = v; }

    public String  getCi()                   { return ci; }
    public void    setCi(String v)           { this.ci = v; }

    public boolean isGithubActions() {
        return "github-actions".equalsIgnoreCase(ci);
    }
}
