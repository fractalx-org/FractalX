package org.fractalx.initializr;

import org.fractalx.initializr.generator.*;
import org.fractalx.initializr.model.ProjectSpec;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Orchestrates the full project-scaffold pipeline.
 *
 * <p>Each {@link InitializerFileGenerator} step is independent and composable —
 * the same pipeline pattern used by {@code ServiceGenerator} in fractalx-core.
 *
 * <p>Usage:
 * <pre>
 * ProjectSpec spec = new SpecFileReader().read(Path.of("fractalx.yaml"));
 * new ProjectInitializer()
 *     .setProgressCallbacks(label -> {}, label -> {})
 *     .initialize(spec, Path.of("my-platform"));
 * </pre>
 */
public class ProjectInitializer {

    private Consumer<String> onStart    = l -> {};
    private Consumer<String> onComplete = l -> {};

    public ProjectInitializer setProgressCallbacks(Consumer<String> onStart,
                                                    Consumer<String> onComplete) {
        this.onStart    = onStart;
        this.onComplete = onComplete;
        return this;
    }

    // ── Pipeline ──────────────────────────────────────────────────────────────

    private List<InitializerFileGenerator> buildPipeline(ProjectSpec spec) {
        return List.of(
                new RootPomWriter(),
                new ApplicationGenerator(),
                new ApplicationYmlGenerator(),
                new ModuleMarkerGenerator(),
                new EntityGenerator(),
                new RepositoryGenerator(),
                new ServiceClassGenerator(),
                new ControllerGenerator(),
                new FlywayMigrationGenerator(),
                new DockerComposeGenerator(),
                new GitHubActionsGenerator(),
                new FractalxSpecWriter(),
                new ReadmeGenerator()
        );
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public void initialize(ProjectSpec spec, Path outputRoot) throws IOException {
        InitializerContext ctx = new InitializerContext(spec, outputRoot);

        for (InitializerFileGenerator step : buildPipeline(spec)) {
            onStart.accept(step.label());
            step.generate(ctx);
            onComplete.accept(step.label());
        }
    }

    /** Returns the ordered list of step labels — used by the Dashboard to pre-populate rows. */
    public List<String> stepLabels(ProjectSpec spec) {
        return buildPipeline(spec).stream()
                .map(InitializerFileGenerator::label)
                .toList();
    }
}
