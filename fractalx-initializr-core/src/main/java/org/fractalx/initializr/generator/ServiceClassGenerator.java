package org.fractalx.initializr.generator;

import org.fractalx.initializr.InitializerContext;
import org.fractalx.initializr.model.EntitySpec;
import org.fractalx.initializr.model.ProjectSpec;
import org.fractalx.initializr.model.SagaSpec;
import org.fractalx.initializr.model.SagaStepSpec;
import org.fractalx.initializr.model.ServiceSpec;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates one {@code @Service} class per service.
 * If sagas are defined, the owning service's orchestrator method is annotated with
 * {@code @DistributedSaga} so the decomposer can wire saga orchestration.
 */
public class ServiceClassGenerator implements InitializerFileGenerator {

    @Override
    public String label() { return "Service classes"; }

    @Override
    public void generate(InitializerContext ctx) throws IOException {
        ProjectSpec       spec  = ctx.spec();
        List<SagaSpec>    sagas = spec.getSagas();

        for (ServiceSpec svc : spec.getServices()) {
            generateServiceClass(ctx, spec, svc, sagas);
        }
    }

    private void generateServiceClass(InitializerContext ctx, ProjectSpec spec,
                                       ServiceSpec svc, List<SagaSpec> sagas) throws IOException {
        String svcPkg    = spec.resolvedPackage() + "." + svc.javaPackage();
        String className = svc.classPrefix() + "Service";

        List<SagaSpec> ownedSagas = sagas.stream()
                .filter(s -> svc.getName().equals(s.getOwner()))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(svcPkg).append(";\n\n");
        sb.append("import org.springframework.stereotype.Service;\n");
        sb.append("import org.springframework.transaction.annotation.Transactional;\n");
        if (!ownedSagas.isEmpty()) {
            sb.append("import org.fractalx.annotations.DistributedSaga;\n");
        }
        for (EntitySpec entity : svc.getEntities()) {
            sb.append("import ").append(svcPkg).append(".").append(entity.getName()).append("Repository;\n");
        }
        sb.append("\n");

        sb.append("@Service\n");
        sb.append("public class ").append(className).append(" {\n\n");

        // Repository fields
        for (EntitySpec entity : svc.getEntities()) {
            String repoType  = entity.getName() + "Repository";
            String fieldName = Character.toLowerCase(repoType.charAt(0)) + repoType.substring(1);
            sb.append("    private final ").append(repoType).append(" ").append(fieldName).append(";\n");
        }

        // Constructor injection
        if (!svc.getEntities().isEmpty()) {
            sb.append("\n    public ").append(className).append("(");
            List<EntitySpec> entities = svc.getEntities();
            for (int i = 0; i < entities.size(); i++) {
                String repoType  = entities.get(i).getName() + "Repository";
                String fieldName = Character.toLowerCase(repoType.charAt(0)) + repoType.substring(1);
                sb.append(repoType).append(" ").append(fieldName);
                if (i < entities.size() - 1) sb.append(", ");
            }
            sb.append(") {\n");
            for (EntitySpec entity : entities) {
                String repoType  = entity.getName() + "Repository";
                String fieldName = Character.toLowerCase(repoType.charAt(0)) + repoType.substring(1);
                sb.append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n");
            }
            sb.append("    }\n");
        }
        sb.append("\n");

        // Basic CRUD for each entity
        for (EntitySpec entity : svc.getEntities()) {
            String typeName = entity.getName();
            String varName  = entity.fieldName();
            String repoVar  = Character.toLowerCase(typeName.charAt(0)) + typeName + "Repository";
            String idType   = "mongodb".equalsIgnoreCase(svc.getDatabase()) ? "String" : "Long";

            sb.append("    @Transactional(readOnly = true)\n");
            sb.append("    public java.util.List<").append(typeName).append("> findAll").append(typeName).append("s() {\n");
            sb.append("        return ").append(repoVar).append(".findAll();\n");
            sb.append("    }\n\n");

            sb.append("    @Transactional(readOnly = true)\n");
            sb.append("    public ").append(typeName).append(" find").append(typeName).append("ById(").append(idType).append(" id) {\n");
            sb.append("        return ").append(repoVar).append(".findById(id)\n");
            sb.append("                .orElseThrow(() -> new IllegalArgumentException(\"")
              .append(typeName).append(" not found: \" + id));\n");
            sb.append("    }\n\n");

            sb.append("    @Transactional\n");
            sb.append("    public ").append(typeName).append(" create").append(typeName)
              .append("(").append(typeName).append(" ").append(varName).append(") {\n");
            sb.append("        return ").append(repoVar).append(".save(").append(varName).append(");\n");
            sb.append("    }\n\n");

            sb.append("    @Transactional\n");
            sb.append("    public void delete").append(typeName).append("(").append(idType).append(" id) {\n");
            sb.append("        ").append(repoVar).append(".deleteById(id);\n");
            sb.append("    }\n\n");
        }

        // Saga orchestrator methods
        for (SagaSpec saga : ownedSagas) {
            String methodName = toCamelCase(saga.getId());
            sb.append("    @DistributedSaga(\n");
            sb.append("        sagaId = \"").append(saga.getId()).append("\",\n");
            if (!saga.getCompensationMethod().isBlank()) {
                sb.append("        compensationMethod = \"").append(saga.getCompensationMethod()).append("\",\n");
            }
            sb.append("        timeout = ").append(saga.getTimeoutMs()).append("\n");
            sb.append("    )\n");
            sb.append("    @Transactional\n");
            sb.append("    public void ").append(methodName).append("() {\n");
            sb.append("        // TODO: implement saga orchestration\n");
            for (SagaStepSpec step : saga.getSteps()) {
                sb.append("        // Step → ").append(step.getService())
                  .append(".").append(step.getMethod()).append("()\n");
            }
            sb.append("    }\n\n");

            if (!saga.getCompensationMethod().isBlank()) {
                sb.append("    @Transactional\n");
                sb.append("    public void ").append(saga.getCompensationMethod()).append("() {\n");
                sb.append("        // TODO: implement compensation / rollback logic\n");
                sb.append("    }\n\n");
            }
        }

        sb.append("}\n");

        Path file = ctx.serviceSourceDir(svc).resolve(className + ".java");
        write(file, sb.toString());
    }

    private String toCamelCase(String kebab) {
        String[] parts = kebab.split("-");
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty())
                sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
