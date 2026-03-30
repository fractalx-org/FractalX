package org.fractalx.initializr.generator;

import org.fractalx.initializr.InitializerContext;
import org.fractalx.initializr.model.EntitySpec;
import org.fractalx.initializr.model.ProjectSpec;
import org.fractalx.initializr.model.ServiceSpec;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Generates a Spring Data repository interface per entity.
 * Uses {@code JpaRepository} for relational DBs and {@code MongoRepository} for MongoDB.
 */
public class RepositoryGenerator implements InitializerFileGenerator {

    @Override
    public String label() { return "Repositories"; }

    @Override
    public void generate(InitializerContext ctx) throws IOException {
        ProjectSpec spec = ctx.spec();

        for (ServiceSpec svc : spec.getServices()) {
            for (EntitySpec entity : svc.getEntities()) {
                generateRepository(ctx, spec, svc, entity);
            }
        }
    }

    private void generateRepository(InitializerContext ctx, ProjectSpec spec,
                                     ServiceSpec svc, EntitySpec entity) throws IOException {
        boolean isMongo  = "mongodb".equalsIgnoreCase(svc.getDatabase());
        String  svcPkg   = spec.resolvedPackage() + "." + svc.javaPackage();
        String  repoName = entity.getName() + "Repository";
        String  idType   = isMongo ? "String" : "Long";

        String repoInterface = isMongo
                ? "org.springframework.data.mongodb.repository.MongoRepository"
                : "org.springframework.data.jpa.repository.JpaRepository";

        String src = "package " + svcPkg + ";\n\n"
                + "import " + repoInterface + ";\n"
                + "import org.springframework.stereotype.Repository;\n\n"
                + "@Repository\n"
                + "public interface " + repoName + " extends "
                + (isMongo ? "MongoRepository" : "JpaRepository")
                + "<" + entity.getName() + ", " + idType + "> {\n"
                + "    // Add custom query methods here\n"
                + "}\n";

        Path file = ctx.serviceSourceDir(svc).resolve(repoName + ".java");
        write(file, src);
    }
}
