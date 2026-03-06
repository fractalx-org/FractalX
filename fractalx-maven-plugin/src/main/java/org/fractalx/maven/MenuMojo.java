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
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Interactive CLI menu — shows the FRACTALX banner, lets the user navigate
 * commands with arrow keys, executes the selected one, then returns to ask
 * what's next. Start / stop / restart show a service sub-picker first.
 * Choose Exit (or press q / Ctrl-C) to leave.
 *
 * <pre>
 *   mvn fractalx:menu
 * </pre>
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

    // ── Menu options (Exit rendered separately as the last item) ──────────────

    private static final String[] NAMES = {
        "decompose", "verify", "start", "stop", "restart", "ps", "services"
    };

    private static final String[] DESCS = {
        "Decompose monolith into microservices",
        "Verify decomposition output",
        "Start generated services",
        "Stop running services",
        "Restart services",
        "Show service process status",
        "List all generated services"
    };

    private static final int NAME_W =
            Arrays.stream(NAMES).mapToInt(String::length).max().orElse(8) + 3;

    /** Sentinel index meaning "Exit". */
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

                // ── Main menu on alt screen ────────────────────────────────────
                out.print(ALT_ON);
                out.flush();
                drawMenu(selected);

                // ── Navigate ──────────────────────────────────────────────────
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
                            out.print(ALT_OFF); out.flush();
                            break outer;                    // bare ESC → exit menu
                        }
                    } else if (b == '\r' || b == '\n') {
                        break inner;                        // confirmed
                    } else if (b == 'q' || b == 3 || b == 4) {
                        out.print(ALT_OFF); out.flush();
                        break outer;
                    } else if (b == 'k') selected = (selected - 1 + TOTAL) % TOTAL;
                    else if  (b == 'j') selected = (selected + 1) % TOTAL;

                    drawMenu(selected);
                }

                // ── Exit chosen ────────────────────────────────────────────────
                if (selected == EXIT_IDX) {
                    out.print(ALT_OFF); out.flush();
                    break;
                }

                String commandName = NAMES[selected];

                // ── Service sub-picker for start / stop / restart ──────────────
                String service = "";
                if (isServiceCommand(commandName)) {
                    // Still on alt screen — draw the sub-picker over it.
                    String picked = pickService(tty, commandName);
                    if (picked == null) {
                        // User backed out → redraw main menu (continue outer re-enters ALT_ON)
                        continue outer;
                    }
                    service = picked;
                }

                // ── Exit alt screen, run command on main screen ────────────────
                out.print(ALT_OFF);
                out.flush();

                try {
                    invokeCommand(commandName, service);
                } catch (MojoExecutionException e) {
                    out.println();
                    warn("Command failed: " + e.getMessage());
                }

                // Sub-mojos may restore the terminal; re-enable raw mode for the
                // "press any key" read and the next menu loop.
                enableRawMode();

                out.println();
                out.print("  " + a(DIM) + "\u21B5  press any key to return to menu" + a(RST));
                out.flush();
                int k = tty.read();
                out.println();
                if (k == 'q' || k == 3 || k == 4) break;
            }

        } catch (IOException ignored) {
        } finally {
            restoreTerminal();
        }
    }

    // ── Main menu renderer ────────────────────────────────────────────────────

    private void drawMenu(int selected) {
        StringBuilder sb = new StringBuilder();
        sb.append(HOME);
        sb.append("\r\033[2K\r\n");

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

        for (int i = 0; i < NAMES.length; i++) {
            boolean sel    = (i == selected);
            String  cursor = sel ? GRN + "\u25B6" + RST : " ";
            String  name   = sel ? BLD + pad(NAMES[i], NAME_W) + RST
                                 : DIM + pad(NAMES[i], NAME_W) + RST;
            String  desc   = sel ? DESCS[i] : DIM + DESCS[i] + RST;
            sb.append("\r\033[2K  ").append(cursor).append("  ")
              .append(name).append("  ").append(desc).append("\r\n");
        }

        // Separator + Exit
        sb.append("\r\033[2K\r\n");
        boolean exitSel    = (selected == EXIT_IDX);
        String  exitCursor = exitSel ? RED + "\u25B6" + RST : " ";
        String  exitName   = exitSel ? RED + BLD + pad("exit", NAME_W) + RST
                                     : DIM + pad("exit", NAME_W) + RST;
        String  exitDesc   = exitSel ? RED + "Exit interactive CLI" + RST
                                     : DIM + "Exit interactive CLI" + RST;
        sb.append("\r\033[2K  ").append(exitCursor).append("  ")
          .append(exitName).append("  ").append(exitDesc).append("\r\n");

        sb.append(CLR_END);
        out.print(sb);
        out.flush();
    }

    // ── Service sub-picker (drawn on the already-active alt screen) ───────────

    private record SvcEntry(String name, boolean running) {}

    /**
     * Shows an "All services / pick one" selector on the alt screen.
     * Each service line shows ● (green, running) or ○ (dim, stopped).
     *
     * @return "" for All, service name for a specific service, null if user backed out.
     */
    private String pickService(FileInputStream tty, String commandName) throws IOException {
        List<SvcEntry> entries = discoverServiceEntries();

        // Option 0 = "All services", options 1..n = each service
        int total = entries.size() + 1;
        int optW  = entries.stream().mapToInt(e -> e.name().length()).max().orElse(12) + 3;
        int sel   = 0;
        String title = Character.toUpperCase(commandName.charAt(0))
                     + commandName.substring(1) + " — select service";

        drawSubPicker(entries, sel, optW, title);

        while (true) {
            int b = tty.read();

            if (b == 27) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                if (tty.available() > 0) {
                    int b2 = tty.read();
                    if (b2 == '[' && tty.available() > 0) {
                        int b3 = tty.read();
                        if      (b3 == 'A') sel = (sel - 1 + total) % total;
                        else if (b3 == 'B') sel = (sel + 1) % total;
                    }
                } else {
                    return null;                // bare ESC → back
                }
            } else if (b == '\r' || b == '\n') {
                break;                          // confirmed
            } else if (b == 'q' || b == 3 || b == 4) {
                return null;                    // back to main menu
            } else if (b == 'k') sel = (sel - 1 + total) % total;
            else if  (b == 'j') sel = (sel + 1) % total;

            drawSubPicker(entries, sel, optW, title);
        }

        return sel == 0 ? "" : entries.get(sel - 1).name();  // "" = All
    }

    private void drawSubPicker(List<SvcEntry> entries, int selected, int optW, String title) {
        // "●" U+25CF (filled circle) = running, "○" U+25CB (empty circle) = stopped
        final String DOT_UP   = GRN + "\u25CF" + RST;
        final String DOT_DOWN = DIM + "\u25CB" + RST;

        StringBuilder sb = new StringBuilder();
        sb.append(HOME);
        sb.append("\r\033[2K\r\n");

        for (int i = 0; i < BANNER.length; i++) {
            String color = i < BANNER_COLORS.length ? BANNER_COLORS[i] : "";
            sb.append("\r\033[2K").append(color).append(BANNER[i]).append(RST).append("\r\n");
        }

        sb.append("\r\033[2K\r\n");
        sb.append("\r\033[2K  ").append(DIM)
          .append(title).append("  ").append(FractalxVersion.get()).append(RST).append("\r\n");
        sb.append("\r\033[2K\r\n");
        sb.append("\r\033[2K  ").append(DIM)
          .append("\u2191\u2193 navigate   Enter select   ESC back   ")
          .append(GRN).append("\u25CF").append(DIM).append(" running  ")
          .append("\u25CB").append(" stopped").append(RST).append("\r\n");
        sb.append("\r\033[2K\r\n");

        // Row 0: "All services"
        {
            boolean isSel  = (selected == 0);
            String cursor  = isSel ? GRN + "\u25B6" + RST : " ";
            String name    = isSel ? BLD + pad("All services", optW) + RST
                                   : DIM + pad("All services", optW) + RST;
            sb.append("\r\033[2K  ").append(cursor).append("  ").append(name).append("\r\n");
        }

        // Rows 1..n: each service with status dot
        for (int i = 0; i < entries.size(); i++) {
            SvcEntry e    = entries.get(i);
            boolean isSel = (selected == i + 1);
            String cursor = isSel ? GRN + "\u25B6" + RST : " ";
            String dot    = e.running() ? DOT_UP : DOT_DOWN;
            String name   = isSel ? BLD + pad(e.name(), optW) + RST
                                  : (e.running() ? pad(e.name(), optW) : DIM + pad(e.name(), optW) + RST);
            String status = e.running() ? GRN + "running" + RST : DIM + "stopped" + RST;
            sb.append("\r\033[2K  ").append(cursor).append("  ")
              .append(dot).append(" ").append(name).append("  ").append(status).append("\r\n");
        }

        sb.append(CLR_END);
        out.print(sb);
        out.flush();
    }

    // ── Numbered fallback ─────────────────────────────────────────────────────

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
                if (choice < 1 || choice > NAMES.length) continue;

                String name    = NAMES[choice - 1];
                String service = "";

                if (isServiceCommand(name)) {
                    List<SvcEntry> svcs = discoverServiceEntries();
                    out.println();
                    out.println("  [0] All services");
                    for (int i = 0; i < svcs.size(); i++) {
                        SvcEntry e = svcs.get(i);
                        String status = e.running() ? " [running]" : " [stopped]";
                        out.println("  [" + (i + 1) + "] " + e.name() + status);
                    }
                    out.println();
                    out.print("  Select service (0 for all): ");
                    out.flush();
                    if (sc.hasNextInt()) {
                        int s = sc.nextInt();
                        if (s > 0 && s <= svcs.size()) service = svcs.get(s - 1).name();
                    }
                }

                try {
                    invokeCommand(name, service);
                } catch (MojoExecutionException e) {
                    warn("Command failed: " + e.getMessage());
                }
                out.println();
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isServiceCommand(String name) {
        return name.equals("start") || name.equals("stop") || name.equals("restart");
    }

    private List<SvcEntry> discoverServiceEntries() {
        if (outputDirectory == null || !outputDirectory.exists()) return List.of();
        List<SvcEntry> result = new ArrayList<>();
        try (var stream = Files.list(outputDirectory.toPath())) {
            stream.filter(Files::isDirectory)
                  .filter(d -> Files.exists(d.resolve("pom.xml")))
                  .sorted()
                  .forEach(d -> {
                      String name = d.getFileName().toString();
                      int port = readPortFromDir(d);
                      boolean running = port > 0 && isPortOpen(port);
                      result.add(new SvcEntry(name, running));
                  });
        } catch (IOException ignored) {}
        return result;
    }

    private int readPortFromDir(Path svcDir) {
        Path yml = svcDir.resolve("src/main/resources/application.yml");
        if (!Files.exists(yml)) return -1;
        try {
            for (String line : Files.readAllLines(yml)) {
                line = line.trim();
                if (line.startsWith("port:"))
                    return Integer.parseInt(line.substring("port:".length()).trim());
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private boolean isPortOpen(int port) {
        try (Socket s = new Socket("localhost", port)) {
            return true;
        } catch (IOException e) {
            return false;
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

    private void invokeCommand(String name, String service) throws MojoExecutionException {
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

        inject(mojo, "colorParam",      true);
        inject(mojo, "project",         project);
        inject(mojo, "outputDirectory", outputDirectory);
        inject(mojo, "sourceDirectory", sourceDirectory);
        inject(mojo, "service",         service);        // "" = all, name = specific
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
