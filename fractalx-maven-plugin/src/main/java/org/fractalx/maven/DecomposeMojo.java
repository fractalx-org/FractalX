package org.fractalx.maven;

import org.fractalx.core.ModuleAnalyzer;
import org.fractalx.core.model.FractalModule;
import org.fractalx.core.generator.ServiceGenerator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Decomposes a modular monolith into microservices.
 *
 * <p>Progress is shown on an <em>alternate screen buffer</em> (the technique
 * used by vim, less, htop, etc.) so the dashboard is fully in-place — nothing
 * scrolls during generation.  When generation completes (or fails) the plugin
 * returns to the normal screen and prints a concise summary.
 */
@Mojo(name = "decompose", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class DecomposeMojo extends AbstractMojo {

    // ── ANSI ─────────────────────────────────────────────────────────────────
    private static final String RST = "\033[0m";
    private static final String BLD = "\033[1m";
    private static final String DIM = "\033[2m";
    private static final String GRN = "\033[32m";
    private static final String CYN = "\033[36m";
    private static final String YLW = "\033[33m";
    private static final String RED = "\033[31m";
    private static final String WHT = "\033[97m";

    // Braille spinner — identical to Claude Code / Vercel CLI
    private static final String[] SPIN = {
        "\u280B","\u2819","\u2839","\u2838","\u283C",
        "\u2834","\u2826","\u2827","\u2807","\u280F"
    };

    // Enter / leave the xterm alternate screen buffer
    private static final String ALT_ON  = "\033[?1049h";
    private static final String ALT_OFF = "\033[?1049l";
    // Move to top-left of current screen
    private static final String HOME    = "\033[H";
    // Erase from cursor to end of screen
    private static final String CLR_END = "\033[0J";

    private static final int GATEWAY_PORT  = 9999;
    private static final int ADMIN_PORT    = 8080;
    private static final int REGISTRY_PORT = 8761;

    // ── Parameters ───────────────────────────────────────────────────────────
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "fractalx.sourceDirectory",
               defaultValue = "${project.build.sourceDirectory}")
    private File sourceDirectory;

    @Parameter(property = "fractalx.outputDirectory",
               defaultValue = "${project.basedir}/fractalx-output")
    private File outputDirectory;

    @Parameter(property = "fractalx.skip",    defaultValue = "false")
    private boolean skip;

    @Parameter(property = "fractalx.generate", defaultValue = "true")
    private boolean generate;

    @Parameter(property = "fractalx.color",   defaultValue = "true")
    private boolean colorParam;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean     ansi;
    private PrintStream out = System.out;

    // =========================================================================
    @Override
    public void execute() throws MojoExecutionException {
        ansi = colorParam && detectTty();
        long t0 = System.currentTimeMillis();

        if (skip) { out.println(a(DIM) + "  Skipped." + a(RST)); return; }

        // ── Normal screen: header + module list ───────────────────────────────
        out.println();
        out.println("  " + a(BLD) + a(WHT) + "\u2042" + a(RST)
                + "  " + a(BLD) + "FractalX" + a(RST)
                + a(DIM) + "  Decomposition Engine  0.3.2" + a(RST));
        out.println();

        try {
            Path sourcePath = sourceDirectory.toPath();

            out.println(a(DIM) + "  Inspecting " + a(RST) + sourceDirectory.getAbsolutePath());
            out.println();

            List<FractalModule> modules = new ModuleAnalyzer().analyzeProject(sourcePath);

            if (modules.isEmpty()) {
                out.println("  " + a(YLW) + "\u26A0" + a(RST)
                        + a(DIM) + "  No @DecomposableModule classes found." + a(RST));
                return;
            }

            for (FractalModule m : modules) {
                String deps = m.getDependencies().isEmpty() ? ""
                        : a(DIM) + "  \u2192  " + a(RST) + String.join(", ", m.getDependencies());
                out.println("  " + a(GRN) + "\u25AA" + a(RST)
                        + "  " + a(BLD) + m.getServiceName() + a(RST)
                        + a(DIM) + "  :" + m.getPort() + a(RST) + deps);
            }
            out.println();

            if (generate) {
                runGeneration(sourcePath, modules, t0);
            } else {
                out.println(a(DIM) + "  Generation skipped (fractalx.generate=false)." + a(RST));
            }

        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Decomposition failed", e);
        }
    }

    // =========================================================================
    // Generation — dashboard lives on the alternate screen
    // =========================================================================

    private void runGeneration(Path srcPath, List<FractalModule> modules, long t0) throws Exception {

        List<String> labels = new ArrayList<>();
        labels.add("fractalx-registry");
        modules.forEach(m -> labels.add(m.getServiceName()));
        if (modules.size() > 1) labels.add("fractalx-gateway");
        labels.add("fractalx-admin");
        labels.add("fractalx-saga-orchestrator");
        labels.add("docker-compose + scripts");

        // ── Enter alternate screen ────────────────────────────────────────────
        if (ansi) { out.print(ALT_ON); out.flush(); }

        Dashboard        dash   = new Dashboard(labels, out, ansi);
        ServiceGenerator gen    = new ServiceGenerator(srcPath, outputDirectory.toPath());
        String[]         active = { null };

        gen.setProgressCallbacks(
            label -> { active[0] = label; dash.onStart(label); },
            label -> { dash.onDone(label); active[0] = null;   }
        );

        dash.render();   // draw all rows; start 80-ms ticker

        try {
            gen.generateServices(modules);
        } catch (Exception e) {
            String step = active[0] != null ? active[0] : "generation";
            dash.onFail(step, e.getMessage());
            if (ansi) {
                // Give the user a moment to read the error on the alt screen,
                // then return to the normal screen.
                try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
                out.print(ALT_OFF);
                out.flush();
            }
            throw new MojoExecutionException("Failed at: " + step, e);
        }

        dash.finish();

        // Brief pause so the user sees the completed dashboard before it vanishes
        if (ansi) {
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            out.print(ALT_OFF);   // ← back to the normal screen
            out.flush();
        }

        out.println();
        long totalMs = System.currentTimeMillis() - t0;
        printSummary(modules, outputDirectory.toPath(), totalMs);
    }

    // =========================================================================
    // Summary on the normal screen — Vercel style
    // =========================================================================

    private void printSummary(List<FractalModule> modules, Path outDir, long totalMs) {
        int pw = Math.max(22, modules.stream()
                .mapToInt(m -> m.getServiceName().length()).max().orElse(0) + 2);

        section("Microservices");
        for (FractalModule m : modules)
            link(pw, m.getServiceName(), "http://localhost:" + m.getPort());
        out.println();

        section("Infrastructure");
        if (modules.size() > 1) link(pw, "fractalx-gateway",  "http://localhost:" + GATEWAY_PORT);
        link(pw, "fractalx-admin",    "http://localhost:" + ADMIN_PORT);
        link(pw, "fractalx-registry", "http://localhost:" + REGISTRY_PORT);
        out.println();

        section("Get started");
        cmd("cd " + outDir.toAbsolutePath());
        cmd("./start-all.sh");
        cmd("docker-compose up -d");
        out.println();
        out.println("  " + a(DIM) + "Docs  \u2192  " + a(RST)
                + outDir.toAbsolutePath() + "/README.md");
        out.println();

        String t = totalMs >= 1000 ? String.format("%.1fs", totalMs / 1000.0) : totalMs + "ms";
        out.println("  " + a(GRN) + "\u2713" + a(RST) + "  " + a(BLD) + "Done" + a(RST)
                + "  " + a(DIM) + "[" + t + "]" + a(RST));
        out.println();
    }

    private void section(String title) {
        out.println("  " + a(BLD) + a(WHT) + "\u2042  " + title + a(RST));
        out.println();
    }

    private void link(int pw, String label, String url) {
        out.println("  " + a(DIM) + pad(label, pw) + a(RST)
                + a(CYN) + "  \u2192  " + a(RST) + url);
    }

    private void cmd(String command) {
        out.println("  " + a(DIM) + "$" + a(RST) + "  " + command);
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private String a(String c)             { return ansi ? c : ""; }
    private static String pad(String s, int w) { return s.length() >= w ? s : s + " ".repeat(w - s.length()); }

    private boolean detectTty() {
        if (System.getenv("NO_COLOR")    != null) return false;
        if ("dumb".equals(System.getenv("TERM"))) return false;
        if (System.getenv("FORCE_COLOR") != null) return true;
        return System.console() != null;
    }

    // =========================================================================
    // Dashboard — full alternate-screen TUI
    //
    // On every 80-ms tick the ticker calls drawAll(), which:
    //   1. Jumps to the top-left of the (alternate) screen  [ESC[H]
    //   2. Overwrites every row with the current step state
    //   3. Clears any leftover content below                [ESC[0J]
    //
    // Because we always go back to the same absolute position, nothing
    // can scroll the dashboard — this is identical to how vim/less work.
    // =========================================================================

    private final class Dashboard {

        private enum Status { PENDING, RUNNING, DONE, SKIPPED, ERROR }

        private final class Step {
            final    String  label;
            volatile Status  status   = Status.PENDING;
            volatile long    startMs;
            volatile long    elapsedMs;
            volatile String  errorMsg;
            Step(String l) { label = l; }
        }

        private final List<Step>  steps;
        private final PrintStream ps;
        private final boolean     clr;
        private volatile int      frame = 0;
        private Thread            ticker;

        Dashboard(List<String> labels, PrintStream ps, boolean clr) {
            this.steps = labels.stream().map(Step::new).collect(Collectors.toList());
            this.ps    = ps;
            this.clr   = clr;
        }

        // ── Public API ────────────────────────────────────────────────────────

        void render() {
            if (!clr) return;
            drawAll();
            ticker = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    frame++;
                    synchronized (ps) { drawAll(); }
                    try { Thread.sleep(80); } catch (InterruptedException e) { break; }
                }
            });
            ticker.setDaemon(true);
            ticker.start();
        }

        void onStart(String label) {
            int i = find(label); if (i < 0) return;
            steps.get(i).status  = Status.RUNNING;
            steps.get(i).startMs = System.currentTimeMillis();
            // ticker picks up the RUNNING state within ≤80 ms
            if (!clr) ps.println("  " + ca(CYN) + SPIN[0] + ca(RST) + "  " + label);
        }

        void onDone(String label) {
            int i = find(label); if (i < 0) return;
            Step s    = steps.get(i);
            s.elapsedMs = System.currentTimeMillis() - s.startMs;
            s.status    = Status.DONE;
            if (clr) synchronized (ps) { drawAll(); }
            else ps.println("  " + ca(GRN) + "\u25AA" + ca(RST) + "  " + label
                    + "  " + ca(DIM) + "[" + fmt(s.elapsedMs) + "]" + ca(RST));
        }

        void onFail(String label, String msg) {
            stopTicker();
            int i = find(label);
            if (i >= 0) {
                Step s    = steps.get(i);
                s.errorMsg  = msg;
                s.elapsedMs = System.currentTimeMillis() - s.startMs;
                s.status    = Status.ERROR;
            }
            if (clr) {
                synchronized (ps) {
                    drawAll();
                    // Print error below the step list (within the alt screen)
                    ps.println();
                    ps.println("  " + ca(RED) + "\u2718" + ca(RST)
                            + "  " + ca(RED) + ca(BLD) + "Error" + ca(RST));
                    if (msg != null && !msg.isBlank())
                        ps.println("  " + ca(DIM) + "  " + msg + ca(RST));
                }
            } else {
                ps.println("  " + ca(RED) + "\u2718" + ca(RST) + "  " + label + "  [failed]");
                if (msg != null && !msg.isBlank())
                    ps.println("  " + ca(DIM) + "  " + msg + ca(RST));
            }
            ps.flush();
        }

        void finish() {
            stopTicker();
            synchronized (ps) {
                for (Step s : steps)
                    if (s.status == Status.PENDING) s.status = Status.SKIPPED;
                if (clr) drawAll();
            }
            ps.flush();
        }

        // ── Core renderer ─────────────────────────────────────────────────────

        /**
         * Jump to the top-left of the screen and overwrite every line.
         * Must be called inside {@code synchronized(ps)}.
         */
        private void drawAll() {
            StringBuilder sb = new StringBuilder();

            // ── Go to origin ──────────────────────────────────────────────────
            sb.append(HOME);

            // ── Header (2 lines) ──────────────────────────────────────────────
            sb.append("\r\033[2K\r\n");
            sb.append("\r\033[2K  ")
              .append(ca(BLD)).append(ca(WHT)).append("\u2042").append(ca(RST))
              .append("  ").append(ca(BLD)).append("FractalX").append(ca(RST))
              .append(ca(DIM)).append("  Decomposition Engine  0.3.2").append(ca(RST))
              .append("\r\n");
            sb.append("\r\033[2K\r\n");

            // ── Step rows ─────────────────────────────────────────────────────
            for (Step s : steps) {
                sb.append("\r\033[2K").append(row(s)).append("\r\n");
            }

            // ── Clear anything below (e.g. stray logger output) ───────────────
            sb.append(CLR_END);

            ps.print(sb);
            ps.flush();
        }

        // ── Row renderer ──────────────────────────────────────────────────────

        private String row(Step s) {
            switch (s.status) {
                case PENDING:
                    return "  " + ca(DIM) + "\u25AB  " + s.label + ca(RST);
                case RUNNING:
                    return "  " + ca(CYN) + SPIN[frame % SPIN.length] + ca(RST)
                            + "  " + s.label
                            + ca(DIM) + "..." + ca(RST);
                case DONE:
                    return "  " + ca(GRN) + "\u25AA" + ca(RST)
                            + "  " + s.label
                            + "  " + ca(DIM) + "[" + fmt(s.elapsedMs) + "]" + ca(RST);
                case SKIPPED:
                    return "  " + ca(DIM) + "\u2013  " + s.label + "  skipped" + ca(RST);
                case ERROR:
                    return "  " + ca(RED) + "\u2718" + ca(RST)
                            + "  " + ca(RED) + s.label + ca(RST)
                            + "  " + ca(DIM) + "[failed]" + ca(RST);
                default: return "";
            }
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private int find(String label) {
            for (int i = 0; i < steps.size(); i++)
                if (steps.get(i).label.equals(label)) return i;
            return -1;
        }

        private void stopTicker() {
            if (ticker != null) {
                ticker.interrupt();
                try { ticker.join(300); } catch (InterruptedException ignored) {}
                ticker = null;
            }
        }

        private String fmt(long ms) {
            return ms >= 1000 ? String.format("%.1fs", ms / 1000.0) : ms + "ms";
        }

        private String ca(String code) { return clr ? code : ""; }
    }
}
