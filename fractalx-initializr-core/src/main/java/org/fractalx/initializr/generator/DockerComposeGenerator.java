package org.fractalx.initializr.generator;

import org.fractalx.initializr.InitializerContext;
import org.fractalx.initializr.model.ProjectSpec;
import org.fractalx.initializr.model.ServiceSpec;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates {@code docker-compose.dev.yml} pre-wired with one database container per
 * unique database technology declared across all services.
 */
public class DockerComposeGenerator implements InitializerFileGenerator {

    @Override
    public String label() { return "docker-compose.dev.yml"; }

    @Override
    public void generate(InitializerContext ctx) throws IOException {
        ProjectSpec spec = ctx.spec();

        // Collect distinct DB technologies (excluding H2 — in-memory, no container needed)
        Set<String> dbs = new LinkedHashSet<>();
        for (ServiceSpec svc : spec.getServices()) {
            String db = svc.getDatabase().toLowerCase();
            if (!"h2".equals(db)) dbs.add(db);
        }

        if (dbs.isEmpty()) {
            // All services use H2 — generate a minimal compose with just pgAdmin placeholder
            write(ctx.outputRoot().resolve("docker-compose.dev.yml"),
                    "# All services use H2 in-memory — no database containers needed for local dev.\n"
                    + "services: {}\n");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# docker-compose.dev.yml — local development databases\n");
        sb.append("# Run: docker compose -f docker-compose.dev.yml up -d\n\n");
        sb.append("services:\n");

        boolean hasPostgres = dbs.contains("postgresql");
        boolean hasMysql    = dbs.contains("mysql");
        boolean hasMongo    = dbs.contains("mongodb");
        boolean hasRedis    = dbs.contains("redis");

        if (hasPostgres) {
            sb.append("  postgres:\n");
            sb.append("    image: postgres:16-alpine\n");
            sb.append("    environment:\n");
            sb.append("      POSTGRES_USER: fractalx\n");
            sb.append("      POSTGRES_PASSWORD: fractalx\n");
            sb.append("      POSTGRES_DB: fractalx_dev\n");
            sb.append("    ports:\n");
            sb.append("      - \"5432:5432\"\n");
            sb.append("    volumes:\n");
            sb.append("      - postgres_data:/var/lib/postgresql/data\n");
            sb.append("    healthcheck:\n");
            sb.append("      test: [\"CMD-SHELL\", \"pg_isready -U fractalx\"]\n");
            sb.append("      interval: 10s\n");
            sb.append("      timeout: 5s\n");
            sb.append("      retries: 5\n\n");

            // Create schemas for each postgres service
            sb.append("  postgres-init:\n");
            sb.append("    image: postgres:16-alpine\n");
            sb.append("    depends_on:\n");
            sb.append("      postgres:\n");
            sb.append("        condition: service_healthy\n");
            sb.append("    entrypoint: [\"/bin/sh\", \"-c\"]\n");
            sb.append("    command: |\n");
            sb.append("      psql -h postgres -U fractalx -c \"\n");
            for (ServiceSpec svc : spec.getServices()) {
                if ("postgresql".equalsIgnoreCase(svc.getDatabase())) {
                    sb.append("        CREATE DATABASE ").append(svc.resolvedSchema()).append(";\n");
                }
            }
            sb.append("      \"\n");
            sb.append("    environment:\n");
            sb.append("      PGPASSWORD: fractalx\n\n");
        }

        if (hasMysql) {
            sb.append("  mysql:\n");
            sb.append("    image: mysql:8.3\n");
            sb.append("    environment:\n");
            sb.append("      MYSQL_ROOT_PASSWORD: fractalx\n");
            sb.append("      MYSQL_USER: fractalx\n");
            sb.append("      MYSQL_PASSWORD: fractalx\n");
            sb.append("    ports:\n");
            sb.append("      - \"3306:3306\"\n");
            sb.append("    volumes:\n");
            sb.append("      - mysql_data:/var/lib/mysql\n");
            sb.append("    healthcheck:\n");
            sb.append("      test: [\"CMD\", \"mysqladmin\", \"ping\", \"-h\", \"localhost\"]\n");
            sb.append("      interval: 10s\n");
            sb.append("      retries: 5\n\n");
        }

        if (hasMongo) {
            sb.append("  mongodb:\n");
            sb.append("    image: mongo:7\n");
            sb.append("    environment:\n");
            sb.append("      MONGO_INITDB_ROOT_USERNAME: fractalx\n");
            sb.append("      MONGO_INITDB_ROOT_PASSWORD: fractalx\n");
            sb.append("    ports:\n");
            sb.append("      - \"27017:27017\"\n");
            sb.append("    volumes:\n");
            sb.append("      - mongo_data:/data/db\n\n");
        }

        if (hasRedis) {
            sb.append("  redis:\n");
            sb.append("    image: redis:7-alpine\n");
            sb.append("    ports:\n");
            sb.append("      - \"6379:6379\"\n");
            sb.append("    command: redis-server --appendonly yes\n");
            sb.append("    volumes:\n");
            sb.append("      - redis_data:/data\n\n");
        }

        sb.append("volumes:\n");
        if (hasPostgres) sb.append("  postgres_data:\n");
        if (hasMysql)    sb.append("  mysql_data:\n");
        if (hasMongo)    sb.append("  mongo_data:\n");
        if (hasRedis)    sb.append("  redis_data:\n");

        write(ctx.outputRoot().resolve("docker-compose.dev.yml"), sb.toString());
    }
}
