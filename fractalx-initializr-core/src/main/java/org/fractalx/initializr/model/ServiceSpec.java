package org.fractalx.initializr.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a single bounded-context service within the monolith.
 */
public class ServiceSpec {

    /** Kebab-case service name, e.g. {@code order-service} */
    private String name;
    private int    port        = 8080;
    private String description = "";

    /**
     * Database technology: {@code h2} | {@code postgresql} | {@code mysql} |
     * {@code mongodb} | {@code redis}. Defaults to H2 for zero-config local dev.
     */
    private String database = "h2";

    /** Schema / database name owned by this service, e.g. {@code order_db}. */
    private String schema;

    /** API style: {@code rest} | {@code grpc} | {@code rest+grpc}. */
    private String apiStyle = "rest";

    private boolean adminEnabled        = false;
    private boolean independentDeployment = true;

    private List<EntitySpec> entities     = new ArrayList<>();
    /** Names of other services this service calls, e.g. {@code ["payment-service"]}. */
    private List<String>     dependencies = new ArrayList<>();

    // ── Derived helpers ────────────────────────────────────────────────────────

    /**
     * Java sub-package: strips "-service" suffix and converts to lowercase,
     * e.g. {@code order-service} → {@code order}.
     */
    public String javaPackage() {
        return name.replace("-service", "").replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    /**
     * Class-name prefix: capitalises the first letter of each word,
     * e.g. {@code order-service} → {@code Order}.
     */
    public String classPrefix() {
        String[] parts = name.replace("-service", "").split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts)
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase());
        return sb.toString();
    }

    /** Whether this service uses JPA (non-MongoDB, non-Redis). */
    public boolean usesJpa() {
        return !"mongodb".equalsIgnoreCase(database) && !"redis".equalsIgnoreCase(database);
    }

    /** Whether Flyway should be included (JPA + non-H2). */
    public boolean usesFlyway() {
        return usesJpa() && !"h2".equalsIgnoreCase(database);
    }

    /** Resolved schema name: explicit {@code schema} or derived from javaPackage. */
    public String resolvedSchema() {
        if (schema != null && !schema.isBlank()) return schema;
        return javaPackage() + "_db";
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public String getName()              { return name; }
    public void   setName(String v)      { this.name = v; }

    public int    getPort()              { return port; }
    public void   setPort(int v)         { this.port = v; }

    public String getDescription()       { return description; }
    public void   setDescription(String v) { this.description = v; }

    public String getDatabase()          { return database; }
    public void   setDatabase(String v)  { this.database = v; }

    public String getSchema()            { return schema; }
    public void   setSchema(String v)    { this.schema = v; }

    public String getApiStyle()          { return apiStyle; }
    public void   setApiStyle(String v)  { this.apiStyle = v; }

    public boolean isAdminEnabled()       { return adminEnabled; }
    public void    setAdminEnabled(boolean v) { this.adminEnabled = v; }

    public boolean isIndependentDeployment()       { return independentDeployment; }
    public void    setIndependentDeployment(boolean v) { this.independentDeployment = v; }

    public List<EntitySpec> getEntities()          { return entities; }
    public void             setEntities(List<EntitySpec> v) { this.entities = v; }

    public List<String>     getDependencies()      { return dependencies; }
    public void             setDependencies(List<String> v) { this.dependencies = v; }
}
