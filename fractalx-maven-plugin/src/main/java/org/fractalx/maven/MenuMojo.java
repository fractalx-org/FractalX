package org.fractalx.maven;

import org.fractalx.core.FractalxVersion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Scanner;

/**
 * Interactive CLI menu — shows the FRACTALX banner once, then lets the user
 * navigate commands with arrow keys and execute the selected one.
 *
 * <pre>
 *   mvn fractalx:menu
 * </pre>
 *
 * Controls: ↑↓ (or j/k) to move, Enter to select, q or Ctrl-C to quit.
 */
@Mojo(name = "menu")
public class MenuMojo extends FractalxBaseMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "fractalx.sourceDirectory",
               defaultValue = "${project.build.sourceDirectory}")
    private File sourceDirectory;

    @Parameter(property = "fractalx.outputDirectory",
               defaultValue = "${project.basedir}/fractalx-output")
    private File outputDirectory;

    // ── Menu options ──────────────────────────────────────────────────────────

    private static final String[] NAMES = {
        "decompose", "verify", "start", "stop", "restart", "ps", "services"
    };

    private static final String[] DESCS = {
        "Decompose monolith into microservices",
        "Verify decomposition output",
        "Start all generated services",
        "Stop running services",
        "Restart services",
        "Show service process status",
        "List all generated services"
    };

    private static final int NAME_W =
            Arrays.stream(NAMES).mapToInt(String::length).max().orElse(8) + 3;

    // ── Execute ───────────────────────────────────────────────────────────────

    @Override
    public void execute() throws MojoExecutionException {
        initCli();

        if (!ansi) {
            runNumberedMenu();
            return;
        }

        out.print(ALT_ON);
        out.flush();

        boolean rawEnabled = enableRawMode();
        if (rawEnabled) {
            Runtime.getRuntime().addShutdownHook(new Thread(MenuMojo::restoreTerminal));
        }

        int selected = 0;
        try (FileInputStream tty = new FileInputStream("/dev/tty")) {
            drawMenu(selected);

            while (true) {
                int b = tty.read();

                if (b == 27) {                           // ESC / arrow key
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                    if (tty.available() > 0) {
                        int b2 = tty.read();
                        if (b2 == '[' && tty.available() > 0) {
                            int b3 = tty.read();
                            if      (b3 == 'A') selected = (selected - 1 + NAMES.length) % NAMES.length;
                            else if (b3 == 'B') selected = (selected + 1) % NAMES.length;
                        }
                        // any other ESC sequence — ignore
                    } else {
                        selected = -1; break;            // bare ESC → quit
                    }
                } else if (b == '\r' || b == '\n') {     // Enter → confirm
                    break;
                } else if (b == 'q' || b == 3 || b == 4) { // q / Ctrl-C / Ctrl-D → quit
                    selected = -1; break;
                } else if (b == 'k') {
                    selected = (selected - 1 + NAMES.length) % NAMES.length;
                } else if (b == 'j') {
                    selected = (selected + 1) % NAMES.length;
                }

                drawMenu(selected);
            }

        } catch (IOException e) {
            selected = -1;   // no TTY available
        } finally {
            restoreTerminal();
            out.print(ALT_OFF);
            out.flush();
        }

        if (selected >= 0) {
            invokeCommand(NAMES[selected]);
        }
    }

    // ── Menu renderer ─────────────────────────────────────────────────────────

    private void drawMenu(int selected) {
        StringBuilder sb = new StringBuilder();
        sb.append(HOME);

        // blank line
        sb.append("\r\033[2K\r\n");

        // FRACTALX banner — rendered once at the top, never re-announced
        for (int i = 0; i < BANNER.length; i++) {
            String color = i < BANNER_COLORS.length ? BANNER_COLORS[i] : "";
            sb.append("\r\033[2K").append(color).append(BANNER[i]).append(RST).append("\r\n");
        }

        // subtitle
        sb.append("\r\033[2K\r\n");
        sb.append("\r\033[2K  ").append(DIM)
          .append("Interactive CLI  ").append(FractalxVersion.get()).append(RST).append("\r\n");
        sb.append("\r\033[2K\r\n");

        // navigation hint
        sb.append("\r\033[2K  ").append(DIM)
          .append("\u2191\u2193 navigate   Enter select   q quit").append(RST).append("\r\n");
        sb.append("\r\033[2K\r\n");

        // menu items
        for (int i = 0; i < NAMES.length; i++) {
            boolean sel    = (i == selected);
            String  cursor = sel ? GRN + "\u25B6" + RST : " ";
            String  name   = sel
                    ? BLD + pad(NAMES[i], NAME_W) + RST
                    : DIM + pad(NAMES[i], NAME_W) + RST;
            String  desc   = sel ? DESCS[i] : DIM + DESCS[i] + RST;
            sb.append("\r\033[2K  ").append(cursor).append("  ")
              .append(name).append("  ").append(desc).append("\r\n");
        }

        sb.append(CLR_END);
        out.print(sb);
        out.flush();
    }

    // ── Numbered fallback (no ANSI / dumb terminal) ───────────────────────────

    private void runNumberedMenu() throws MojoExecutionException {
        printHeader("Interactive CLI");
        for (int i = 0; i < NAMES.length; i++) {
            out.println("  [" + (i + 1) + "] " + pad(NAMES[i], NAME_W) + "  " + DESCS[i]);
        }
        out.println();
        out.print("  Select (1-" + NAMES.length + "): ");
        out.flush();
        try (Scanner sc = new Scanner(System.in)) {
            if (sc.hasNextInt()) {
                int choice = sc.nextInt();
                if (choice >= 1 && choice <= NAMES.length) {
                    invokeCommand(NAMES[choice - 1]);
                }
            }
        } catch (Exception e) {
            warn("Could not read input.");
        }
    }

    // ── Raw-mode helpers ──────────────────────────────────────────────────────

    private static boolean enableRawMode() {
        try {
            new ProcessBuilder("sh", "-c", "stty -echo -icanon min 1 < /dev/tty")
                    .redirectErrorStream(true).start().waitFor();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void restoreTerminal() {
        try {
            new ProcessBuilder("sh", "-c", "stty echo icanon < /dev/tty")
                    .redirectErrorStream(true).start().waitFor();
        } catch (Exception ignored) {}
    }

    // ── Mojo dispatcher ───────────────────────────────────────────────────────

    private void invokeCommand(String name) throws MojoExecutionException {
        FractalxBaseMojo mojo = switch (name) {
            case "decompose" -> new DecomposeMojo();
            case "verify"    -> new VerifyMojo();
            case "start"     -> new StartMojo();
            case "stop"      -> new StopMojo();
            case "restart"   -> new RestartMojo();
            case "ps"        -> new PsMojo();
            case "services"  -> new ServicesMojo();
            default -> throw new MojoExecutionException("Unknown command: " + name);
        };

        // colorParam is @Parameter(defaultValue="true") — not injected by Maven when
        // instantiated directly, so inject it explicitly so initCli() detects the TTY.
        inject(mojo, "colorParam", true);
        inject(mojo, "project",         project);
        inject(mojo, "outputDirectory", outputDirectory);
        inject(mojo, "sourceDirectory", sourceDirectory);   // ignored silently if absent
        try {
            mojo.execute();
        } catch (MojoFailureException e) {
            throw new MojoExecutionException("Command failed: " + name, e);
        }
    }

    private static void inject(Object target, String fieldName, Object value) {
        if (value == null) return;
        Class<?> cls = target.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            } catch (Exception e) {
                break;
            }
        }
    }
}
