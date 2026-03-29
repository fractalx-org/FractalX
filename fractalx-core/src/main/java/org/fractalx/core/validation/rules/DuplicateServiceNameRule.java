package org.fractalx.core.validation.rules;

import org.fractalx.core.model.FractalModule;
import org.fractalx.core.validation.ValidationContext;
import org.fractalx.core.validation.ValidationIssue;
import org.fractalx.core.validation.ValidationRule;
import org.fractalx.core.validation.ValidationSeverity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ensures no two modules declare the same {@code serviceName}.
 *
 * <p>Duplicate service names cause every downstream artefact to collide:
 * the same Docker container name, the same Maven {@code artifactId}, the same
 * Spring application name, and the same entry in the service registry.
 * Generation would silently overwrite the first service's output directory with
 * the second service's files.
 */
public class DuplicateServiceNameRule implements ValidationRule {

    @Override
    public String ruleId() { return "DUP_SERVICE_NAME"; }

    @Override
    public List<ValidationIssue> validate(ValidationContext ctx) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Group modules by service name
        Map<String, List<FractalModule>> byName = new LinkedHashMap<>();
        for (FractalModule m : ctx.modules()) {
            byName.computeIfAbsent(m.getServiceName(), k -> new ArrayList<>()).add(m);
        }

        for (Map.Entry<String, List<FractalModule>> entry : byName.entrySet()) {
            if (entry.getValue().size() > 1) {
                String name   = entry.getKey();
                String classes = entry.getValue().stream()
                        .map(FractalModule::getClassName).reduce((a, b) -> a + ", " + b).orElse("");
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, ruleId(), name,
                        "serviceName '" + name + "' is declared by " + entry.getValue().size()
                        + " classes: " + classes,
                        "Give each module a unique serviceName in @DecomposableModule"));
            }
        }
        return issues;
    }
}
