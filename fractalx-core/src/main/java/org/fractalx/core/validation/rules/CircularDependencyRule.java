package org.fractalx.core.validation.rules;

import org.fractalx.core.validation.ValidationContext;
import org.fractalx.core.validation.ValidationIssue;
import org.fractalx.core.validation.ValidationRule;
import org.fractalx.core.validation.ValidationSeverity;
import org.fractalx.core.verifier.ServiceGraphAnalyzer;
import org.fractalx.core.verifier.ServiceGraphAnalyzer.GraphReport;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects circular dependencies in the service dependency graph.
 *
 * <p>A cycle (e.g. {@code order-service → payment-service → order-service}) causes:
 * <ul>
 *   <li>Docker Compose startup deadlock — all services in the cycle wait for each
 *       other to become healthy before they start.</li>
 *   <li>Infinite NetScope call loops at runtime if any service initialises a
 *       remote call during its {@code @PostConstruct} phase.</li>
 * </ul>
 *
 * <p>Reuses {@link ServiceGraphAnalyzer} from the verifier package so the cycle
 * detection algorithm is shared between the pre-generation validator and the
 * post-generation verify step.
 */
public class CircularDependencyRule implements ValidationRule {

    @Override
    public String ruleId() { return "CIRCULAR_DEP"; }

    @Override
    public List<ValidationIssue> validate(ValidationContext ctx) {
        List<ValidationIssue> issues = new ArrayList<>();

        GraphReport report = new ServiceGraphAnalyzer().analyse(ctx.modules());

        for (ServiceGraphAnalyzer.Finding f : report.findings()) {
            if (f.kind() == ServiceGraphAnalyzer.FindingKind.CYCLE) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, ruleId(), f.service(),
                        "circular dependency detected: " + f.detail(),
                        "Extract shared state into a new @DecomposableModule, or break the cycle "
                        + "by reversing one of the dependency edges (e.g. use an event/outbox "
                        + "instead of a direct NetScope call)"));
            }
        }
        return issues;
    }
}
