package org.fractalx.maven;

import org.fractalx.initializr.ProjectInitializer;
import org.fractalx.initializr.SpecFileReader;
import org.fractalx.initializr.model.*;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Scaffolds a new FractalX-annotated modular monolith from scratch.
 *
 * <p>Two modes:
 * <ol>
 *   <li><b>Interactive wizard</b> — run in a TTY, prompts for project metadata,
 *       services, entities, sagas, infrastructure, and security.</li>
 *   <li><b>Spec-file</b> — {@code mvn fractalx:init -Dfractalx.spec=fractalx.yaml}
 *       reads a pre-authored YAML file and generates silently.</li>
 * </ol>
 *
 * <p>The generated project is placed in {@code fractalx.outputDir} (default: current
 * working directory / {@code <artifactId>}).
 */
@Mojo(name = "init", requiresProject = false)
public class InitMojo extends FractalxBaseMojo {

    /** Path to an existing {@code fractalx.yaml} spec file. Bypasses the wizard. */
    @Parameter(property = "fractalx.spec")
    private File specFile;

    /** Root directory where the project will be generated. */
    @Parameter(property = "fractalx.outputDir",
               defaultValue = "${user.dir}")
    private File outputDir;

    /** Skip interactive questionnaire and use all defaults. */
    @Parameter(property = "fractalx.interactive", defaultValue = "true")
    private boolean interactive;

    // =========================================================================
    @Override
    public void execute() throws MojoExecutionException {
        initCli();
        long t0 = System.currentTimeMillis();

        printHeader("Initializr");

        try {
            ProjectSpec spec;

            if (specFile != null && specFile.exists()) {
                info("Reading spec: " + specFile.getAbsolutePath());
                out.println();
                spec = new SpecFileReader().read(specFile.toPath());
            } else {
                boolean canAsk = ansi && interactive;
                spec = canAsk ? runWizard() : buildDefaultSpec();
            }

            Path outRoot = outputDir.toPath().resolve(spec.getArtifactId());
            if (Files.exists(outRoot)) {
                warn("Output directory already exists: " + outRoot.toAbsolutePath());
                warn("Files will be overwritten.");
                out.println();
            }

            runGeneration(spec, outRoot, t0);

        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Initialization failed", e);
        }
    }

    // =========================================================================
    // Interactive wizard
    // =========================================================================

    private ProjectSpec runWizard() throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        ProjectSpec spec = new ProjectSpec();

        // ── Project metadata ──────────────────────────────────────────────────
        section("Project");
        spec.setGroupId(ask(r,       "Group ID",            "com.example"));
        spec.setArtifactId(ask(r,    "Artifact ID",         "my-platform"));
        spec.setVersion(ask(r,       "Version",             "1.0.0-SNAPSHOT"));
        spec.setDescription(ask(r,   "Description",         "FractalX modular monolith"));
        spec.setJavaVersion(askChoice(r, "Java version",    new String[]{"17", "21"}, "17"));
        spec.setSpringBootVersion(ask(r, "Spring Boot version", "3.2.5"));
        out.println();

        // ── Security ──────────────────────────────────────────────────────────
        section("Security");
        SecuritySpec sec = new SecuritySpec();
        sec.setType(askChoice(r, "Authentication",
                new String[]{"none", "jwt", "oauth2", "apikey"}, "none"));
        spec.setSecurity(sec);
        out.println();

        // ── Services ──────────────────────────────────────────────────────────
        section("Services");
        int defaultPort = 8081;
        List<ServiceSpec> services = new ArrayList<>();
        while (true) {
            out.println("  " + a(DIM) + "─────────────────────────────" + a(RST));
            String svcName = ask(r, "Service name (blank to finish)",
                    services.isEmpty() ? "order-service" : "");
            if (svcName.isBlank()) break;

            ServiceSpec svc = new ServiceSpec();
            svc.setName(svcName);
            svc.setPort(Integer.parseInt(ask(r, "Port", String.valueOf(defaultPort++))));
            svc.setDescription(ask(r, "Description", ""));
            svc.setDatabase(askChoice(r, "Database",
                    new String[]{"h2", "postgresql", "mysql", "mongodb", "redis"}, "h2"));

            // Entities
            List<EntitySpec> entities = new ArrayList<>();
            while (askYesNo(r, "Add an entity to " + svcName, !entities.isEmpty() ? false : true)) {
                EntitySpec entity = new EntitySpec();
                entity.setName(ask(r, "Entity name (PascalCase)", ""));
                if (entity.getName().isBlank()) break;

                List<FieldSpec> fields = new ArrayList<>();
                while (askYesNo(r, "Add a field to " + entity.getName(), !fields.isEmpty() ? false : true)) {
                    FieldSpec field = new FieldSpec();
                    field.setName(ask(r, "Field name", ""));
                    if (field.getName().isBlank()) break;
                    field.setType(ask(r, "Field type", "String"));
                    fields.add(field);
                }
                entity.setFields(fields);
                entities.add(entity);
            }
            svc.setEntities(entities);
            services.add(svc);
        }
        spec.setServices(services);
        out.println();

        // ── Dependencies ─────────────────────────────────────────────────────
        if (services.size() > 1) {
            section("Service Dependencies");
            for (ServiceSpec svc : services) {
                List<String> candidates = services.stream()
                        .filter(s -> !s.getName().equals(svc.getName()))
                        .map(ServiceSpec::getName)
                        .toList();
                List<String> deps = new ArrayList<>();
                for (String candidate : candidates) {
                    if (askYesNo(r, "Does " + svc.getName() + " call " + candidate, false)) {
                        deps.add(candidate);
                    }
                }
                svc.setDependencies(deps);
            }
            out.println();
        }

        // ── Sagas ─────────────────────────────────────────────────────────────
        List<SagaSpec> sagas = new ArrayList<>();
        if (!services.isEmpty() && askYesNo(r, "Define distributed sagas", false)) {
            section("Sagas");
            while (true) {
                String sagaId = ask(r, "Saga ID (blank to finish)", "");
                if (sagaId.isBlank()) break;

                SagaSpec saga = new SagaSpec();
                saga.setId(sagaId);
                saga.setOwner(askChoice(r, "Owner service",
                        services.stream().map(ServiceSpec::getName).toArray(String[]::new),
                        services.get(0).getName()));
                saga.setCompensationMethod(ask(r, "Compensation method (blank = none)", ""));
                saga.setTimeoutMs(Long.parseLong(ask(r, "Timeout (ms)", "30000")));

                List<SagaStepSpec> steps = new ArrayList<>();
                out.println("  " + a(DIM) + "Add steps (blank service name to finish):" + a(RST));
                while (true) {
                    String stepSvc = ask(r, "Step service", "");
                    if (stepSvc.isBlank()) break;
                    String stepMethod = ask(r, "Method", "");
                    SagaStepSpec step = new SagaStepSpec();
                    step.setService(stepSvc);
                    step.setMethod(stepMethod);
                    steps.add(step);
                }
                saga.setSteps(steps);
                sagas.add(saga);
            }
        }
        spec.setSagas(sagas);

        // ── Infrastructure ────────────────────────────────────────────────────
        section("Infrastructure");
        InfraSpec infra = new InfraSpec();
        infra.setGateway(askYesNo(r,         "Generate API Gateway",              services.size() > 1));
        infra.setAdmin(askYesNo(r,           "Generate Admin dashboard",          true));
        infra.setServiceRegistry(askYesNo(r, "Generate Service registry",         true));
        infra.setDocker(askYesNo(r,          "Generate docker-compose.dev.yml",   true));
        infra.setKubernetes(askYesNo(r,      "Generate Kubernetes manifests",     false));
        boolean ci = askYesNo(r,             "Generate GitHub Actions CI",        true);
        infra.setCi(ci ? "github-actions" : "none");
        spec.setInfrastructure(infra);
        out.println();

        return spec;
    }

    // =========================================================================
    // Generation — uses the Dashboard TUI
    // =========================================================================

    private void runGeneration(ProjectSpec spec, Path outRoot, long t0) throws Exception {
        ProjectInitializer initializer = new ProjectInitializer();
        List<String>       labels      = initializer.stepLabels(spec);
        String[]           active      = {null};

        if (ansi) { out.print(ALT_ON); out.flush(); }

        Dashboard dash = new Dashboard(labels, out, ansi, "Initializr");
        initializer.setProgressCallbacks(
            label -> { active[0] = label; dash.onStart(label); },
            label -> { dash.onDone(label); active[0] = null;   }
        );
        dash.render();

        try {
            initializer.initialize(spec, outRoot);
        } catch (Exception e) {
            String step = active[0] != null ? active[0] : "generation";
            dash.onFail(step, e.getMessage());
            if (ansi) {
                try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
                out.print(ALT_OFF); out.flush();
            }
            throw new MojoExecutionException("Failed at: " + step, e);
        }

        dash.finish();

        if (ansi) {
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            out.print(ALT_OFF); out.flush();
        }

        out.println();
        printSummary(spec, outRoot, System.currentTimeMillis() - t0);
    }

    // =========================================================================
    // Summary
    // =========================================================================

    private void printSummary(ProjectSpec spec, Path outRoot, long ms) {
        int pw = Math.max(20, spec.getServices().stream()
                .mapToInt(s -> s.getName().length()).max().orElse(0) + 2);

        section("Services");
        for (ServiceSpec svc : spec.getServices()) {
            link(pw, svc.getName(), "http://localhost:" + svc.getPort());
        }
        out.println();

        section("Next steps");
        cmd("cd " + outRoot.toAbsolutePath());
        if (spec.getInfrastructure().isDocker()) {
            cmd("docker compose -f docker-compose.dev.yml up -d   # start databases");
        }
        cmd("mvn spring-boot:run -Dspring-boot.run.profiles=dev  # run the monolith");
        cmd("mvn fractalx:decompose                               # extract microservices");
        out.println();
        out.println("  " + a(DIM) + "Spec  \u2192  " + a(RST) + outRoot.toAbsolutePath() + "/fractalx.yaml");
        out.println("  " + a(DIM) + "Docs  \u2192  " + a(RST) + outRoot.toAbsolutePath() + "/README.md");
        out.println();

        done(ms);
    }

    // =========================================================================
    // Questionnaire helpers
    // =========================================================================

    private ProjectSpec buildDefaultSpec() {
        ProjectSpec spec = new ProjectSpec();
        ServiceSpec svc  = new ServiceSpec();
        svc.setName("example-service");
        svc.setPort(8081);
        spec.setServices(List.of(svc));
        return spec;
    }

    private String ask(BufferedReader r, String question, String defaultVal) {
        out.print("  " + a(CYN) + "?" + a(RST)
                + "  " + question
                + (defaultVal.isEmpty() ? "" : "  " + a(DIM) + "[" + defaultVal + "]" + a(RST))
                + "  \u203A  ");
        out.flush();
        try {
            String line = r.readLine();
            return (line == null || line.isBlank()) ? defaultVal : line.trim();
        } catch (IOException e) {
            return defaultVal;
        }
    }

    private boolean askYesNo(BufferedReader r, String question, boolean defaultYes) {
        String hint = defaultYes ? "Y/n" : "y/N";
        out.print("  " + a(CYN) + "?" + a(RST)
                + "  " + question
                + "  " + a(DIM) + "[" + hint + "]" + a(RST)
                + "  \u203A  ");
        out.flush();
        try {
            String line = r.readLine();
            if (line == null || line.isBlank()) return defaultYes;
            String l = line.trim().toLowerCase();
            return l.startsWith("y") || (defaultYes && !l.startsWith("n"));
        } catch (IOException e) {
            return defaultYes;
        }
    }

    private String askChoice(BufferedReader r, String question, String[] choices, String defaultVal) {
        String choiceList = String.join(" / ", choices);
        out.print("  " + a(CYN) + "?" + a(RST)
                + "  " + question
                + "  " + a(DIM) + "[" + choiceList + "]  default: " + defaultVal + a(RST)
                + "  \u203A  ");
        out.flush();
        try {
            String line = r.readLine();
            if (line == null || line.isBlank()) return defaultVal;
            String input = line.trim().toLowerCase();
            for (String c : choices) {
                if (c.equalsIgnoreCase(input)) return c;
            }
            return defaultVal;
        } catch (IOException e) {
            return defaultVal;
        }
    }
}
