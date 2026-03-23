package org.fractalx.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Restarts generated services (stop then start).
 *
 * <pre>
 *   mvn fractalx:restart                                     # restart all
 *   mvn fractalx:restart -Dfractalx.service=order-service   # restart one
 * </pre>
 */
@Mojo(name = "restart")
public class RestartMojo extends FractalxBaseMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "fractalx.outputDirectory",
               defaultValue = "${project.basedir}/microservices")
    private File outputDirectory;

    /** Optional: name of a single service to restart. If blank, restarts all. */
    @Parameter(property = "fractalx.service", defaultValue = "")
    private String service;

    @Override
    public void execute() throws MojoExecutionException {
        initCli();
        long t0 = System.currentTimeMillis();

        printHeader("Restart");

        if (!outputDirectory.exists()) {
            warn("Output directory not found: " + outputDirectory.getAbsolutePath());
            warn("Run 'mvn fractalx:decompose' first.");
            return;
        }

        // Delegate to stop then run, sharing outputDirectory + service params
        StopMojo  stop = new StopMojo();
        StartMojo run  = new StartMojo();

        copySharedState(stop);
        copySharedState(run);
        injectField(stop, "outputDirectory", outputDirectory);
        injectField(run,  "outputDirectory", outputDirectory);
        injectField(stop, "service",         service);
        injectField(run,  "service",         service);

        stop.execute();
        run.execute();

        out.println();
        done(System.currentTimeMillis() - t0);
    }

    private void copySharedState(FractalxBaseMojo target) {
        // Share the already-initialised ansi + out stream
        target.ansi = this.ansi;
        target.out  = this.out;
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            // best-effort
        }
    }
}
