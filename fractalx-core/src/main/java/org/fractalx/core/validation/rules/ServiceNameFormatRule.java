package org.fractalx.core.validation.rules;

import org.fractalx.core.validation.ValidationContext;
import org.fractalx.core.validation.ValidationIssue;
import org.fractalx.core.validation.ValidationRule;
import org.fractalx.core.validation.ValidationSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates that every {@code serviceName} declared in {@code @DecomposableModule}
 * is a valid DNS-1123 hostname label.
 *
 * <p>A valid service name must:
 * <ul>
 *   <li>Start with a lowercase letter</li>
 *   <li>Contain only lowercase letters, digits, and hyphens</li>
 *   <li>Be at most 63 characters long</li>
 * </ul>
 *
 * <p>This matters because the service name is used as a Maven {@code artifactId},
 * Docker container name, and Spring property key — all of which have the same
 * DNS-1123 constraint.
 */
public class ServiceNameFormatRule implements ValidationRule {

    private static final Pattern VALID = Pattern.compile("^[a-z][a-z0-9-]{0,62}$");

    @Override
    public String ruleId() { return "INVALID_SERVICE_NAME"; }

    @Override
    public List<ValidationIssue> validate(ValidationContext ctx) {
        List<ValidationIssue> issues = new ArrayList<>();
        for (var module : ctx.modules()) {
            String name = module.getServiceName();
            if (name == null || name.isBlank()) {
                // FractalModule.Builder already throws for blank — this is a safety net
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, ruleId(), "(unknown)",
                        "serviceName is null or blank",
                        "Add serviceName to @DecomposableModule, e.g. @DecomposableModule(serviceName = \"my-service\", ...)"));
            } else if (!VALID.matcher(name).matches()) {
                String suggestion = name.toLowerCase()
                        .replace('_', '-')
                        .replaceAll("[^a-z0-9-]", "")
                        .replaceAll("^[^a-z]+", "");
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, ruleId(), name,
                        "'" + name + "' is not a valid DNS-1123 service name "
                        + "(must match ^[a-z][a-z0-9-]{0,62}$)",
                        "Rename to a lowercase, hyphen-separated name, e.g. '"
                        + (suggestion.isEmpty() ? "my-service" : suggestion) + "'"));
            }
        }
        return issues;
    }
}
