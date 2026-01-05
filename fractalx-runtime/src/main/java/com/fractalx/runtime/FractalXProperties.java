package com.fractalx.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for FractalX runtime
 */
@ConfigurationProperties(prefix = "fractalx")
public class FractalXProperties {

    /**
     * Enable/disable FractalX runtime features
     */
    private boolean enabled = true;

    /**
     * Service registry configuration
     */
    private Registry registry = new Registry();

    /**
     * Observability configuration
     */
    private Observability observability = new Observability();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public Observability getObservability() {
        return observability;
    }

    public void setObservability(Observability observability) {
        this.observability = observability;
    }

    public static class Registry {
        private String type = "simple"; // simple, consul, eureka
        private String url;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class Observability {
        private boolean tracing = true;
        private boolean metrics = true;
        private String exporterUrl;
        private String loggerUrl = "http://localhost:9099/api/logs";

        public String getLoggerUrl() { return loggerUrl; }

        public void setLoggerUrl(String url) { this.loggerUrl = url; }

        public boolean isTracing() {
            return tracing;
        }

        public void setTracing(boolean tracing) {
            this.tracing = tracing;
        }

        public boolean isMetrics() {
            return metrics;
        }

        public void setMetrics(boolean metrics) {
            this.metrics = metrics;
        }

        public String getExporterUrl() {
            return exporterUrl;
        }

        public void setExporterUrl(String exporterUrl) {
            this.exporterUrl = exporterUrl;
        }
    }
}