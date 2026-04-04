package org.fractalx.initializr;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.fractalx.initializr.model.ProjectSpec;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Reads a {@code fractalx.yaml} spec file and deserialises it into a {@link ProjectSpec}.
 *
 * <p>Spec shape:
 * <pre>
 * project:
 *   groupId: com.acme
 *   artifactId: my-platform
 *   javaVersion: "21"
 *   springBootVersion: 3.3.2
 *
 * services:
 *   - name: order-service
 *     port: 8081
 *     database: postgresql
 *     ...
 *
 * sagas:
 *   - id: place-order-saga
 *     ...
 *
 * infrastructure:
 *   gateway: true
 *   docker: true
 *   ci: github-actions
 *
 * security:
 *   type: jwt
 * </pre>
 */
public class SpecFileReader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Wrapper POJO that matches the top-level keys in fractalx.yaml.
     * The {@code project} key maps to a {@link ProjectSpec} that also
     * carries the nested {@code services}, {@code sagas}, etc.
     */
    private static class SpecFile {
        public ProjectSpec project;
        // services / sagas / infrastructure / security can live at the top level
        // OR nested under "project". We support both via merging.
        public java.util.List<org.fractalx.initializr.model.ServiceSpec>  services;
        public java.util.List<org.fractalx.initializr.model.SagaSpec>     sagas;
        public org.fractalx.initializr.model.InfraSpec                    infrastructure;
        public org.fractalx.initializr.model.SecuritySpec                 security;
    }

    public ProjectSpec read(Path specFile) throws IOException {
        SpecFile raw = YAML_MAPPER.readValue(specFile.toFile(), SpecFile.class);

        // Merge: top-level project block wins for metadata; top-level lists win for content
        ProjectSpec spec = (raw.project != null) ? raw.project : new ProjectSpec();

        if (raw.services       != null) spec.setServices(raw.services);
        if (raw.sagas          != null) spec.setSagas(raw.sagas);
        if (raw.infrastructure != null) spec.setInfrastructure(raw.infrastructure);
        if (raw.security       != null) spec.setSecurity(raw.security);

        return spec;
    }
}
