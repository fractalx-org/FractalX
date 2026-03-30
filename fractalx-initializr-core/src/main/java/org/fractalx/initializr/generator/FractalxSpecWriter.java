package org.fractalx.initializr.generator;

import org.fractalx.initializr.InitializerContext;
import org.fractalx.initializr.model.*;

import java.io.IOException;

/**
 * Writes the resolved {@code fractalx.yaml} spec back into the generated project root.
 * This makes the project fully reproducible: {@code mvn fractalx:init --spec fractalx.yaml}
 * regenerates the scaffold without losing customisations.
 */
public class FractalxSpecWriter implements InitializerFileGenerator {

    @Override
    public String label() { return "fractalx.yaml"; }

    @Override
    public void generate(InitializerContext ctx) throws IOException {
        ProjectSpec spec = ctx.spec();
        StringBuilder sb = new StringBuilder();

        sb.append("# fractalx.yaml — FractalX project specification\n");
        sb.append("# Re-generate scaffold: mvn fractalx:init --spec fractalx.yaml\n\n");

        sb.append("project:\n");
        sb.append("  groupId: ").append(spec.getGroupId()).append("\n");
        sb.append("  artifactId: ").append(spec.getArtifactId()).append("\n");
        sb.append("  version: \"").append(spec.getVersion()).append("\"\n");
        sb.append("  javaVersion: \"").append(spec.getJavaVersion()).append("\"\n");
        sb.append("  springBootVersion: ").append(spec.getSpringBootVersion()).append("\n");
        sb.append("  description: \"").append(spec.getDescription()).append("\"\n\n");

        sb.append("services:\n");
        for (ServiceSpec svc : spec.getServices()) {
            sb.append("  - name: ").append(svc.getName()).append("\n");
            sb.append("    port: ").append(svc.getPort()).append("\n");
            sb.append("    database: ").append(svc.getDatabase()).append("\n");
            sb.append("    schema: ").append(svc.resolvedSchema()).append("\n");
            if (!svc.getDescription().isBlank()) {
                sb.append("    description: \"").append(svc.getDescription()).append("\"\n");
            }
            if (!svc.getDependencies().isEmpty()) {
                sb.append("    dependencies:\n");
                for (String dep : svc.getDependencies()) {
                    sb.append("      - ").append(dep).append("\n");
                }
            }
            if (!svc.getEntities().isEmpty()) {
                sb.append("    entities:\n");
                for (EntitySpec entity : svc.getEntities()) {
                    sb.append("      - name: ").append(entity.getName()).append("\n");
                    if (!entity.getFields().isEmpty()) {
                        sb.append("        fields:\n");
                        for (FieldSpec f : entity.getFields()) {
                            sb.append("          - ").append(f.getName()).append(": ").append(f.getType()).append("\n");
                        }
                    }
                    if (!entity.getCrossServiceIds().isEmpty()) {
                        sb.append("        crossServiceIds:\n");
                        for (CrossServiceIdSpec xref : entity.getCrossServiceIds()) {
                            sb.append("          - fieldName: ").append(xref.getFieldName()).append("\n");
                            sb.append("            service: ").append(xref.getService()).append("\n");
                        }
                    }
                }
            }
            sb.append("\n");
        }

        if (!spec.getSagas().isEmpty()) {
            sb.append("sagas:\n");
            for (SagaSpec saga : spec.getSagas()) {
                sb.append("  - id: ").append(saga.getId()).append("\n");
                sb.append("    owner: ").append(saga.getOwner()).append("\n");
                if (!saga.getDescription().isBlank()) {
                    sb.append("    description: \"").append(saga.getDescription()).append("\"\n");
                }
                if (!saga.getCompensationMethod().isBlank()) {
                    sb.append("    compensationMethod: ").append(saga.getCompensationMethod()).append("\n");
                }
                sb.append("    timeoutMs: ").append(saga.getTimeoutMs()).append("\n");
                if (!saga.getSteps().isEmpty()) {
                    sb.append("    steps:\n");
                    for (SagaStepSpec step : saga.getSteps()) {
                        sb.append("      - service: ").append(step.getService()).append("\n");
                        sb.append("        method: ").append(step.getMethod()).append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        InfraSpec infra = spec.getInfrastructure();
        sb.append("infrastructure:\n");
        sb.append("  gateway: ").append(infra.isGateway()).append("\n");
        sb.append("  admin: ").append(infra.isAdmin()).append("\n");
        sb.append("  serviceRegistry: ").append(infra.isServiceRegistry()).append("\n");
        sb.append("  docker: ").append(infra.isDocker()).append("\n");
        sb.append("  kubernetes: ").append(infra.isKubernetes()).append("\n");
        sb.append("  ci: ").append(infra.getCi()).append("\n\n");

        sb.append("security:\n");
        sb.append("  type: ").append(spec.getSecurity().getType()).append("\n");

        write(ctx.outputRoot().resolve("fractalx.yaml"), sb.toString());
    }
}
