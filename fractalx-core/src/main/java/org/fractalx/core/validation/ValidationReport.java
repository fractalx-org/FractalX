package org.fractalx.core.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated result of running all {@link ValidationRule}s against a set of modules.
 *
 * <p>If {@link #hasErrors()} returns {@code true} the caller <em>must</em> abort
 * decomposition and surface the error list to the developer.
 */
public final class ValidationReport {

    /**
     * Pre-partitioned view of the report — avoids repeated stream passes when
     * the caller needs both error and warning lists (e.g. {@code DecomposeMojo}).
     */
    public record Partition(List<ValidationIssue> errors, List<ValidationIssue> warnings) {}

    private final List<ValidationIssue> issues;

    public ValidationReport(List<ValidationIssue> issues) {
        this.issues = List.copyOf(issues);
    }

    /** @return all issues (both errors and warnings). */
    public List<ValidationIssue> issues() { return issues; }

    /** @return only ERROR-severity issues. */
    public List<ValidationIssue> errors() {
        return issues.stream().filter(ValidationIssue::isError).toList();
    }

    /** @return only WARNING-severity issues. */
    public List<ValidationIssue> warnings() {
        return issues.stream().filter(ValidationIssue::isWarning).toList();
    }

    /** @return {@code true} if any ERROR-level issue was found — generation must be blocked. */
    public boolean hasErrors() {
        return issues.stream().anyMatch(ValidationIssue::isError);
    }

    /** @return {@code true} if there are no issues at all. */
    public boolean isClean() { return issues.isEmpty(); }

    /**
     * Splits issues into errors and warnings in a single pass.
     * Use this when you need both lists to avoid iterating the issue list multiple times.
     */
    public Partition partition() {
        List<ValidationIssue> errors   = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();
        for (ValidationIssue issue : issues) {
            if (issue.isError()) errors.add(issue);
            else                 warnings.add(issue);
        }
        return new Partition(List.copyOf(errors), List.copyOf(warnings));
    }

    /**
     * Formats all issues into a multi-line string suitable for console output.
     * Errors appear first, then warnings.
     */
    public String formatReport() {
        if (issues.isEmpty()) return "  No decomposition issues found.";
        Partition p   = partition();
        StringBuilder sb  = new StringBuilder();
        String        sep = "─".repeat(58);
        sb.append(sep).append('\n');
        sb.append(" FractalX Decomposition Validation — ")
          .append(p.errors().size()).append(" error(s), ")
          .append(p.warnings().size()).append(" warning(s)\n");
        sb.append(sep).append('\n');
        for (ValidationIssue e : p.errors()) {
            sb.append("[ERROR] [").append(e.ruleId()).append("] ")
              .append(e.moduleName()).append(": ").append(e.message()).append('\n');
            sb.append("        Fix: ").append(e.fix()).append('\n');
        }
        for (ValidationIssue w : p.warnings()) {
            sb.append("[WARN]  [").append(w.ruleId()).append("] ")
              .append(w.moduleName()).append(": ").append(w.message()).append('\n');
            sb.append("        Fix: ").append(w.fix()).append('\n');
        }
        sb.append(sep);
        return sb.toString();
    }
}
