package org.fractalx.maven;

import org.fractalx.core.FractalxVersion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class for all FractalX Mojos.
 *
 * <p>Provides shared ANSI terminal utilities, the FRACTALX ASCII banner,
 * the braille-spinner Dashboard (alternate-screen TUI), and Vercel-style
 * summary helpers so every command has a consistent look and feel.
 */
public abstract class FractalxBaseMojo extends AbstractMojo {

    // ── ANSI ─────────────────────────────────────────────────────────────────
    protected static final String RST = "\033[0m";
    protected static final String BLD = "\033[1m";
    protected static final String DIM = "\033[2m";
    protected static final String GRN = "\033[32m";
    protected static final String CYN = "\033[36m";
    protected static final String YLW = "\033[33m";
    protected static final String RED = "\033[31m";
    protected static final String WHT = "\033[97m";

    // ── Sunset palette (256-colour) ───────────────────────────────────────────
    private static final String S1 = "\033[38;5;69m";   // cornflower blue
    private static final String S2 = "\033[38;5;33m";   // sky blue
    private static final String S3 = "\033[38;5;220m";  // golden yellow
    private static final String S4 = "\033[38;5;214m";  // orange-yellow
    private static final String S5 = "\033[38;5;208m";  // orange
    private static final String S6 = "\033[38;5;255m";  // near-white

    // ── Braille spinner — identical to Claude Code / Vercel CLI ──────────────
    protected static final String[] SPIN = {
        "\u280B","\u2819","\u2839","\u2838","\u283C",
        "\u2834","\u2826","\u2827","\u2807","\u280F"
    };

    // ── Alternate screen buffer ───────────────────────────────────────────────
    protected static final String ALT_ON  = "\033[?1049h";
    protected static final String ALT_OFF = "\033[?1049l";
    protected static final String HOME    = "\033[H";
    protected static final String CLR_END = "\033[0J";

    // ── FRACTALX block banner ─────────────────────────────────────────────────
    protected static final String[] BANNER = {
        "\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2557\u2588\u2588\u2588\u2588\u2588\u2588\u2557  \u2588\u2588\u2588\u2588\u2588\u2557  \u2588\u2588\u2588\u2588\u2588\u2588\u2557\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2557     \u2588\u2588\u2557  \u2588\u2588\u2557",
        "\u2588\u2588\u2554\u2550\u2550\u2550\u2550\u255d\u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557\u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557\u2588\u2588\u2554\u2550\u2550\u2550\u2550\u255d\u255a\u2550\u2550\u2588\u2588\u2554\u2550\u2550\u255d\u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557\u2588\u2588\u2551     \u255a\u2588\u2588\u2557\u2588\u2588\u2554\u255d",
        "\u2588\u2588\u2588\u2588\u2588\u2557  \u2588\u2588\u2588\u2588\u2588\u2588\u2554\u255d\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2551\u2588\u2588\u2551        \u2588\u2588\u2551   \u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2551\u2588\u2588\u2551      \u255a\u2588\u2588\u2588\u2554\u255d ",
        "\u2588\u2588\u2554\u2550\u2550\u255d  \u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557\u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2551\u2588\u2588\u2551        \u2588\u2588\u2551   \u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2551\u2588\u2588\u2551      \u2588\u2588\u2554\u2588\u2588\u2557 ",
        "\u2588\u2588\u2551     \u2588\u2588\u2551  \u2588\u2588\u2551\u2588\u2588\u2551  \u2588\u2588\u2551\u255a\u2588\u2588\u2588\u2588\u2588\u2588\u2557   \u2588\u2588\u2551   \u2588\u2588\u2551  \u2588\u2588\u2551\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2557\u2588\u2588\u2554\u255d \u2588\u2588\u2557",
        "\u255a\u2550\u255d     \u255a\u2550\u255d  \u255a\u2550\u255d\u255a\u2550\u255d  \u255a\u2550\u255d \u255a\u2550\u2550\u2550\u2550\u2550\u255d   \u255a\u2550\u255d   \u255a\u2550\u255d  \u255a\u2550\u255d\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u255d\u255a\u2550\u255d  \u255a\u2550\u255d"
    };
    protected static final String[] BANNER_COLORS = { S1, S2, S3, S4, S5, S6 };

    // ── Parameters ───────────────────────────────────────────────────────────
    @Parameter(property = "fractalx.color", defaultValue = "true")
    private boolean colorParam;

    // ── State ─────────────────────────────────────────────────────────────────
    protected boolean     ansi;
    protected PrintStream out = System.out;

    /** Call at the start of every {@code execute()} to initialise ANSI detection. */
    protected void initCli() {
        ansi = colorParam && detectTty();
    }

    // ── Banner ────────────────────────────────────────────────────────────────

    /** Prints the full FRACTALX block banner with sunset gradient. */
    protected void printBanner() {
        for (int i = 0; i < BANNER.length; i++) {
            String color = (ansi && i < BANNER_COLORS.length) ? BANNER_COLORS[i] : "";
            out.println(color + BANNER[i] + (ansi ? RST : ""));
        }
        out.println();
    }

    /**
     * Prints the banner followed by a one-line subtitle (command name + version).
     * Typical usage: {@code printHeader("Decomposition Engine");}
     */
    protected void printHeader(String subtitle) {
        out.println();
        printBanner();
        out.println("  " + a(DIM) + subtitle + "  " + FractalxVersion.get() + a(RST));
        out.println();
    }

    // ── Summary helpers (Vercel-style) ────────────────────────────────────────

    protected void section(String title) {
        out.println("  " + a(BLD) + a(WHT) + "\u25F1  " + title + a(RST));
        out.println();
    }

    protected void link(int pw, String label, String url) {
        out.println("  " + a(DIM) + pad(label, pw) + a(RST)
                + a(CYN) + "  \u2192  " + a(RST) + url);
    }

    protected void infoRow(int pw, String label, String value) {
        out.println("  " + a(DIM) + pad(label, pw) + a(RST) + "  " + value);
    }

    protected void cmd(String command) {
        out.println("  " + a(DIM) + "$" + a(RST) + "  " + command);
    }

    protected void done(long ms) {
        String t = ms >= 1000 ? String.format("%.1fs", ms / 1000.0) : ms + "ms";
        out.println("  " + a(GRN) + "\u2713" + a(RST)
                + "  " + a(BLD) + "Done" + a(RST)
                + "  " + a(DIM) + "[" + t + "]" + a(RST));
        out.println();
    }

    protected void warn(String msg) {
        out.println("  " + a(YLW) + "\u26A0" + a(RST) + "  " + a(DIM) + msg + a(RST));
    }

    // ── Low-level helpers ─────────────────────────────────────────────────────

    protected String a(String c) { return ansi ? c : ""; }
    protected static String pad(String s, int w) {
        return s.length() >= w ? s : s + " ".repeat(w - s.length());
    }
    protected static String fmt(long ms) {
        return ms >= 1000 ? String.format("%.1fs", ms / 1000.0) : ms + "ms";
    }

    protected boolean detectTty() {
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
    // can scroll the dashboard — identical to how vim/less/htop work.
    // =========================================================================

    protected final class Dashboard {

        private enum Status { PENDING, RUNNING, DONE, WARNING, SKIPPED, ERROR }

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
        private final String      subtitle;
        private volatile int      frame  = 0;
        private Thread            ticker;

        Dashboard(List<String> labels, PrintStream ps, boolean clr, String subtitle) {
            this.steps    = labels.stream().map(Step::new).collect(Collectors.toList());
            this.ps       = ps;
            this.clr      = clr;
            this.subtitle = subtitle;
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
            if (!clr) ps.println("  " + cc(CYN) + SPIN[0] + cc(RST) + "  " + label);
        }

        void onDone(String label) {
            int i = find(label); if (i < 0) return;
            Step s    = steps.get(i);
            s.elapsedMs = System.currentTimeMillis() - s.startMs;
            s.status    = Status.DONE;
            if (clr) synchronized (ps) { drawAll(); }
            else ps.println("  " + cc(GRN) + "\u25AA" + cc(RST) + "  " + label
                    + "  " + cc(DIM) + "[" + fmt(s.elapsedMs) + "]" + cc(RST));
        }

        /** Level completed but with check failures — yellow ⚠, does NOT stop the ticker. */
        void onWarn(String label, String detail) {
            int i = find(label); if (i < 0) return;
            Step s    = steps.get(i);
            s.elapsedMs = System.currentTimeMillis() - s.startMs;
            s.errorMsg  = detail;
            s.status    = Status.WARNING;
            if (clr) synchronized (ps) { drawAll(); }
            else ps.println("  " + cc(YLW) + "\u26A0" + cc(RST) + "  " + label
                    + "  " + cc(DIM) + detail + cc(RST));
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
                    ps.println();
                    ps.println("  " + cc(RED) + "\u2718" + cc(RST)
                            + "  " + cc(RED) + cc(BLD) + "Error" + cc(RST));
                    if (msg != null && !msg.isBlank())
                        ps.println("  " + cc(DIM) + "  " + msg + cc(RST));
                }
            } else {
                ps.println("  " + cc(RED) + "\u2718" + cc(RST) + "  " + label + "  [failed]");
                if (msg != null && !msg.isBlank())
                    ps.println("  " + cc(DIM) + "  " + msg + cc(RST));
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

        private void drawAll() {
            StringBuilder sb = new StringBuilder();
            sb.append(HOME);

            // ── Full FRACTALX banner with sunset gradient ──────────────────────
            sb.append("\r\033[2K\r\n");
            for (int i = 0; i < BANNER.length; i++) {
                String color = (clr && i < BANNER_COLORS.length) ? BANNER_COLORS[i] : "";
                sb.append("\r\033[2K").append(color).append(BANNER[i])
                  .append(clr ? RST : "").append("\r\n");
            }

            // ── Subtitle line ─────────────────────────────────────────────────
            sb.append("\r\033[2K\r\n");
            sb.append("\r\033[2K  ")
              .append(cc(DIM)).append(subtitle)
              .append("  ").append(FractalxVersion.get()).append(cc(RST))
              .append("\r\n");
            sb.append("\r\033[2K\r\n");

            // ── Step rows ─────────────────────────────────────────────────────
            for (Step s : steps) {
                sb.append("\r\033[2K").append(stepRow(s)).append("\r\n");
            }
            sb.append(CLR_END);
            ps.print(sb);
            ps.flush();
        }

        private String stepRow(Step s) {
            switch (s.status) {
                case PENDING: return "  " + cc(DIM) + "\u25AB  " + s.label + cc(RST);
                case RUNNING: return "  " + cc(CYN) + SPIN[frame % SPIN.length] + cc(RST)
                        + "  " + s.label + cc(DIM) + "..." + cc(RST);
                case DONE:    return "  " + cc(GRN) + "\u25AA" + cc(RST)
                        + "  " + s.label + "  " + cc(DIM) + "[" + fmt(s.elapsedMs) + "]" + cc(RST);
                case WARNING: return "  " + cc(YLW) + "\u26A0" + cc(RST)
                        + "  " + s.label
                        + (s.errorMsg != null ? "  " + cc(DIM) + s.errorMsg + cc(RST) : "")
                        + "  " + cc(DIM) + "[" + fmt(s.elapsedMs) + "]" + cc(RST);
                case SKIPPED: return "  " + cc(DIM) + "\u2013  " + s.label + "  skipped" + cc(RST);
                case ERROR:   return "  " + cc(RED) + "\u2718" + cc(RST)
                        + "  " + cc(RED) + s.label + cc(RST) + "  " + cc(DIM) + "[failed]" + cc(RST);
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

        private String cc(String code) { return clr ? code : ""; }
    }
}
