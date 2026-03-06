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
 * Interactive CLI menu — shows the FRACTALX banner, lets the user navigate
 * commands with arrow keys, executes the selected one, then returns to the
 * menu to ask what's next. Choose Exit (or press q / Ctrl-C) to leave.
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

    // ── Menu options (Exit is rendered separately as the last item) ───────────

    private static final String[] NAMES = {
        "decompose", "verify", "start", "stop", "restart", "ps", "services", "logs"
    };

    private static final String[] DESCS = {
        "Decompose monolith into microservices",
        "Verify decomposition output",
        "Start all generated services",
        "Stop running services",
        "Restart services",
        "Show service process status",
        "List all generated services",
        "Stream a service log file"
    };

    private static final int NAME_W =
            Arrays.stream(NAMES).mapToInt(String::length).max().orElse(8) + 3;

    /** Sentinel index meaning the user chose "Exit". */
    private static final int EXIT_IDX = NAMES.length;
    /** Total selectable rows = commands + Exit. */
    private static final int TOTAL    = NAMES.length + 1;

    // ── Execute ───────────────────────────────────────────────────────────────

    @Override
    public void execute() throws MojoExecutionException {
        initCli();

        if (!ansi) {
            runNumberedMenuLoop();
            return;
        }

        enableRawMode();
        Runtime.getRuntime().addShutdownHook(new Thread(MenuMojo::restoreTerminal));

        try (FileInputStream tty = new FileInputStream("/dev/tty")) {
            int selected = 0;

            outer:
            while (true) {

                // ── Show menu on alternate screen ──────────────────────────────
                out.print(ALT_ON);
                out.flush();
                drawMenu(selected);

                // ── Navigation loop ────────────────────────────────────────────
                inner:
                while (true) {
                    int b = tty.read();

                    if (b == 27) {
                        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                        if (tty.available() > 0) {
                            int b2 = tty.read();
                            if (b2 == '[' && tty.available() > 0) {
                                int b3 = tty.read();
                                if      (b3 == 'A') selected = (selected - 1 + TOTAL) % TOTAL;
                                else if (b3 == 'B') selected = (selected + 1) % TOTAL;
                            }
                        } else {
                            // bare ESC → quit
                            out.print(ALT_OFF); out.flush();
                            break outer;
                        }
                    } else if (b == '\r' || b == '\n') {
                        break inner;                  // Enter → confirmed
                    } else if (b == 'q' || b == 3 || b == 4) {
                        out.print(ALT_OFF); out.flush();
                        break outer;
                    } else if (b == 'k') selected = (selected - 1 + TOTAL) % TOTAL;
                    else if  (b == 'j') selected = (selected + 1) % TOTAL;

                    drawMenu(selected);
                }

                // ── Exit alt screen before running ─────────────────────────────
                out.print(ALT_OFF);
                out.flush();

                if (selected == EXIT_IDX) break;       // Exit chosen

                // ── Run command on main screen ─────────────────────────────────
                try {
                    invokeCommand(NAMES[selected]);
                } catch (MojoExecutionException e) {
                    out.println();
                    warn("Command failed: " + e.getMessage());
                }

                // Sub-mojos may restore the terminal — re-enable raw mode so
                // the "press any key" read and next menu loop work correctly.
                enableRawMode();

                // ── "Press any key to return to menu" ──────────────────────────
                out.println();
                out.print("  " + a(DIM) + "\u21B5  press any key to return to menu" + a(RST));
                out.flush();
                int k = tty.read();
                out.println();
                if (k == 'q' || k == 3 || k == 4) break; // q/Ctrl-C here also exits
            }

        } catch (IOException ignored) {
            // no TTY — fall through
        } finally {
            restoreTerminal();
        }
    }

    // ── Menu renderer ─────────────────────────────────────────────────────────

    private void drawMenu(int selected) {
        StringBuilder sb = new StringBuilder();
        sb.append(HOME);

        sb.append("\r\033[2K\r\n");

        // FRACTALX banner — static at top, redrawn in-place so it never flickers
        for (int i = 0; i < BANNER.length; i++) {
            String color = i < BANNER_COLORS.length ? BANNER_COLORS[i] : "";
            sb.append("\r\033[2K").append(color).append(BANNER[i]).append(RST).append("\r\n");
        }

        sb.append("\r\033[2K\r\n");
        sb.append("\r\033[2K  ").append(DIM)
          .append("Interactive CLI  ").append(FractalxVersion.get()).append(RST).append("\r\n");
        sb.append("\r\033[2K\r\n");

        sb.append("\r\033[2K  ").append(DIM)
          .append("\u2191\u2193 navigate   Enter select   q quit").append(RST).append("\r\n");
        sb.append("\r\033[2K\r\n");

        // Command items
        for (int i = 0; i < NAMES.length; i++) {
            boolean sel    = (i == selected);
            String  cursor = sel ? GRN + "\u25B6" + RST : " ";
            String  name   = sel ? BLD + pad(NAMES[i], NAME_W) + RST
                                 : DIM + pad(NAMES[i], NAME_W) + RST;
            String  desc   = sel ? DESCS[i] : DIM + DESCS[i] + RST;
            sb.append("\r\033[2K  ").append(cursor).append("  ")
              .append(name).append("  ").append(desc).append("\r\n");
        }

        // Separator + Exit item
        sb.append("\r\033[2K\r\n");
        boolean exitSel = (selected == EXIT_IDX);
        String exitCursor = exitSel ? RED + "\u25B6" + RST : " ";
        String exitName   = exitSel ? RED + BLD + pad("exit", NAME_W) + RST
                                    : DIM + pad("exit", NAME_W) + RST;
        String exitDesc   = exitSel ? RED + "Exit interactive CLI" + RST
                                    : DIM + "Exit interactive CLI" + RST;
        sb.append("\r\033[2K  ").append(exitCursor).append("  ")
          .append(exitName).append("  ").append(exitDesc).append("\r\n");

        sb.append(CLR_END);
        out.print(sb);
        out.flush();
    }

    // ── Numbered fallback (no ANSI / dumb terminal) ───────────────────────────

    private void runNumberedMenuLoop() throws MojoExecutionException {
        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                printHeader("Interactive CLI");
                for (int i = 0; i < NAMES.length; i++) {
                    out.println("  [" + (i + 1) + "] " + pad(NAMES[i], NAME_W) + "  " + DESCS[i]);
                }
                out.println("  [0] exit              Exit interactive CLI");
                out.println();
                out.print("  Select (0 to exit): ");
                out.flush();

                if (!sc.hasNextInt()) break;
                int choice = sc.nextInt();
                if (choice == 0) break;
                if (choice >= 1 && choice <= NAMES.length) {
                    try {
                        invokeCommand(NAMES[choice - 1]);
                    } catch (MojoExecutionException e) {
                        warn("Command failed: " + e.getMessage());
                    }
                    out.println();
                }
            }
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
            case "logs"      -> new LogsMojo();
            default -> throw new MojoExecutionException("Unknown command: " + name);
        };

        // @Parameter defaultValue isn't applied for direct instantiation — inject manually.
        inject(mojo, "colorParam",      true);
        inject(mojo, "project",         project);
        inject(mojo, "outputDirectory", outputDirectory);
        inject(mojo, "sourceDirectory", sourceDirectory);  // silently ignored if absent
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
