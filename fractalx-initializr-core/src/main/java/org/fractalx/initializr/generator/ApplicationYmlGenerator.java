package org.fractalx.initializr.generator;

import org.fractalx.initializr.InitializerContext;
import org.fractalx.initializr.model.ProjectSpec;
import org.fractalx.initializr.model.ServiceSpec;

import java.io.IOException;

/**
 * Generates {@code src/main/resources/application.yml} and {@code application-dev.yml}.
 */
public class ApplicationYmlGenerator implements InitializerFileGenerator {

    @Override
    public String label() { return "application.yml"; }

    @Override
    public void generate(InitializerContext ctx) throws IOException {
        ProjectSpec spec = ctx.spec();
        StringBuilder main = new StringBuilder();

        main.append("spring:\n");
        main.append("  application:\n");
        main.append("    name: ").append(spec.getArtifactId()).append("\n\n");

        // Datasource per service (multi-datasource pattern)
        for (ServiceSpec svc : spec.getServices()) {
            if (svc.usesJpa()) {
                main.append("  # ").append(svc.getName()).append(" datasource\n");
                main.append("  # (override per-service in fractalx-config.yml after decomposition)\n");
                main.append("  datasource:\n");
                switch (svc.getDatabase().toLowerCase()) {
                    case "postgresql" -> {
                        main.append("    url: jdbc:postgresql://localhost:5432/").append(svc.resolvedSchema()).append("\n");
                        main.append("    driver-class-name: org.postgresql.Driver\n");
                    }
                    case "mysql" -> {
                        main.append("    url: jdbc:mysql://localhost:3306/").append(svc.resolvedSchema()).append("\n");
                        main.append("    driver-class-name: com.mysql.cj.jdbc.Driver\n");
                    }
                    default -> { // h2
                        main.append("    url: jdbc:h2:mem:").append(svc.resolvedSchema()).append(";DB_CLOSE_DELAY=-1\n");
                        main.append("    driver-class-name: org.h2.Driver\n");
                    }
                }
                main.append("  jpa:\n");
                main.append("    hibernate:\n");
                main.append("      ddl-auto: ").append(svc.usesFlyway() ? "validate" : "create-drop").append("\n");
                main.append("    show-sql: false\n\n");
                break; // single datasource block in main config; per-service in fractalx-config.yml
            }
        }

        main.append("management:\n");
        main.append("  endpoints:\n");
        main.append("    web:\n");
        main.append("      exposure:\n");
        main.append("        include: health,info,metrics\n");
        main.append("  endpoint:\n");
        main.append("    health:\n");
        main.append("      show-details: when-authorized\n\n");

        main.append("logging:\n");
        main.append("  level:\n");
        main.append("    root: INFO\n");
        main.append("    ").append(spec.resolvedPackage()).append(": DEBUG\n");

        write(ctx.resourcesDir().resolve("application.yml"), main.toString());

        // Dev profile — relaxed security, verbose logging, H2 console
        StringBuilder dev = new StringBuilder();
        dev.append("spring:\n");
        dev.append("  h2:\n");
        dev.append("    console:\n");
        dev.append("      enabled: true\n");
        dev.append("      path: /h2-console\n\n");
        dev.append("logging:\n");
        dev.append("  level:\n");
        dev.append("    root: DEBUG\n");
        dev.append("    org.springframework.web: DEBUG\n");

        write(ctx.resourcesDir().resolve("application-dev.yml"), dev.toString());

        // fractalx-config.yml — service-specific datasource overrides (read by fractalx-core)
        StringBuilder fx = new StringBuilder();
        fx.append("# FractalX per-service datasource configuration.\n");
        fx.append("# After running mvn fractalx:decompose, each extracted service\n");
        fx.append("# receives the matching block as its application.yml datasource.\n\n");
        for (ServiceSpec svc : spec.getServices()) {
            fx.append(svc.getName()).append(":\n");
            fx.append("  datasource:\n");
            if ("postgresql".equalsIgnoreCase(svc.getDatabase())) {
                fx.append("    url: jdbc:postgresql://localhost:5432/").append(svc.resolvedSchema()).append("\n");
            } else if ("mysql".equalsIgnoreCase(svc.getDatabase())) {
                fx.append("    url: jdbc:mysql://localhost:3306/").append(svc.resolvedSchema()).append("\n");
            } else {
                fx.append("    url: jdbc:h2:mem:").append(svc.resolvedSchema()).append(";DB_CLOSE_DELAY=-1\n");
            }
            fx.append("\n");
        }
        write(ctx.resourcesDir().resolve("fractalx-config.yml"), fx.toString());
    }
}
