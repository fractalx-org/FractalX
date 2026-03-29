package org.fractalx.core.validation;

/**
 * A single validation finding produced by a {@link ValidationRule}.
 *
 * @param severity   whether this issue blocks generation or is advisory
 * @param ruleId     short all-caps identifier, e.g. {@code "DUP_PORT"}, used in log output
 * @param moduleName the service name of the module that triggered the issue
 *                   (use {@code "—"} when the issue is cross-module)
 * @param message    human-readable description of the problem
 * @param fix        actionable suggestion telling the developer what to change
 */
public record ValidationIssue(
        ValidationSeverity severity,
        String             ruleId,
        String             moduleName,
        String             message,
        String             fix
) {
    public boolean isError()   { return severity == ValidationSeverity.ERROR;   }
    public boolean isWarning() { return severity == ValidationSeverity.WARNING; }
}
