package org.fractalx.initializr.generator;

import org.fractalx.initializr.InitializerContext;
import org.fractalx.initializr.model.EntitySpec;
import org.fractalx.initializr.model.ProjectSpec;
import org.fractalx.initializr.model.ServiceSpec;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Generates a {@code @RestController} with full CRUD endpoints per entity.
 */
public class ControllerGenerator implements InitializerFileGenerator {

    @Override
    public String label() { return "Controllers"; }

    @Override
    public void generate(InitializerContext ctx) throws IOException {
        ProjectSpec spec = ctx.spec();
        for (ServiceSpec svc : spec.getServices()) {
            for (EntitySpec entity : svc.getEntities()) {
                generateController(ctx, spec, svc, entity);
            }
        }
    }

    private void generateController(InitializerContext ctx, ProjectSpec spec,
                                     ServiceSpec svc, EntitySpec entity) throws IOException {
        String  svcPkg      = spec.resolvedPackage() + "." + svc.javaPackage();
        String  entityName  = entity.getName();
        String  varName     = entity.fieldName();
        String  className   = entityName + "Controller";
        String  serviceName = svc.classPrefix() + "Service";
        String  serviceVar  = Character.toLowerCase(serviceName.charAt(0)) + serviceName.substring(1);
        String  idType      = "mongodb".equalsIgnoreCase(svc.getDatabase()) ? "String" : "Long";
        String  basePath    = "/" + varName.toLowerCase() + "s";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(svcPkg).append(";\n\n");
        sb.append("import org.springframework.http.ResponseEntity;\n");
        sb.append("import org.springframework.web.bind.annotation.*;\n");
        sb.append("import jakarta.validation.Valid;\n");
        sb.append("import java.util.List;\n\n");

        sb.append("@RestController\n");
        sb.append("@RequestMapping(\"").append(basePath).append("\")\n");
        sb.append("public class ").append(className).append(" {\n\n");

        sb.append("    private final ").append(serviceName).append(" ").append(serviceVar).append(";\n\n");

        sb.append("    public ").append(className).append("(").append(serviceName).append(" ").append(serviceVar).append(") {\n");
        sb.append("        this.").append(serviceVar).append(" = ").append(serviceVar).append(";\n");
        sb.append("    }\n\n");

        // GET all
        sb.append("    @GetMapping\n");
        sb.append("    public ResponseEntity<List<").append(entityName).append(">> getAll() {\n");
        sb.append("        return ResponseEntity.ok(").append(serviceVar).append(".findAll").append(entityName).append("s());\n");
        sb.append("    }\n\n");

        // GET by id
        sb.append("    @GetMapping(\"/{id}\")\n");
        sb.append("    public ResponseEntity<").append(entityName).append("> getById(@PathVariable ").append(idType).append(" id) {\n");
        sb.append("        return ResponseEntity.ok(").append(serviceVar).append(".find").append(entityName).append("ById(id));\n");
        sb.append("    }\n\n");

        // POST create
        sb.append("    @PostMapping\n");
        sb.append("    public ResponseEntity<").append(entityName).append("> create(@Valid @RequestBody ").append(entityName).append(" ").append(varName).append(") {\n");
        sb.append("        return ResponseEntity.status(201).body(").append(serviceVar).append(".create").append(entityName).append("(").append(varName).append("));\n");
        sb.append("    }\n\n");

        // DELETE
        sb.append("    @DeleteMapping(\"/{id}\")\n");
        sb.append("    public ResponseEntity<Void> delete(@PathVariable ").append(idType).append(" id) {\n");
        sb.append("        ").append(serviceVar).append(".delete").append(entityName).append("(id);\n");
        sb.append("        return ResponseEntity.noContent().build();\n");
        sb.append("    }\n");
        sb.append("}\n");

        Path file = ctx.serviceSourceDir(svc).resolve(className + ".java");
        write(file, sb.toString());
    }
}
