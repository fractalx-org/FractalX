package org.fractalx.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Displays help information about available FractalX Maven plugin goals.
 *
 * <pre>
 *   mvn fractalx:help
 * </pre>
 */
@Mojo(name = "help")
public class HelpMojo extends FractalxBaseMojo {

    @Override
    public void execute() throws MojoExecutionException {
        initCli();

        out.println();
        out.println(a(BLD) + "FractalX Maven Plugin 0.2.4" + a(RST));
        out.println(a(DIM) + "Available goals:" + a(RST));
        out.println();

        // Core goals
        printGoal("fractalx:init", "Initialize a new FractalX project");
        printGoal("fractalx:decompose", "Decompose monolith into microservices");
        printGoal("fractalx:verify", "Verify the decomposition and generated services");
        printGoal("fractalx:menu", "Interactive decomposition menu");
        out.println();

        // Service lifecycle goals
        out.println(a(DIM) + "Service lifecycle:" + a(RST));
        printGoal("fractalx:services", "List all generated services with ports");
        printGoal("fractalx:start", "Start all or a named service [-Dfractalx.service=<name>]");
        printGoal("fractalx:stop", "Stop all or a named service [-Dfractalx.service=<name>]");
        printGoal("fractalx:restart", "Restart a service [-Dfractalx.service=<name>]");
        printGoal("fractalx:ps", "Show running status of all services");
        printGoal("fractalx:smoke-test", "Build, start, and health-check every service");
        out.println();

        // Help
        printGoal("fractalx:help", "Display this help message");
        out.println();
    }

    /**
     * Prints a single goal with its description.
     */
    private void printGoal(String goal, String description) {
        out.println("  " + a(GRN) + goal + a(RST)
                + "  " + a(DIM) + description + a(RST));
    }
}