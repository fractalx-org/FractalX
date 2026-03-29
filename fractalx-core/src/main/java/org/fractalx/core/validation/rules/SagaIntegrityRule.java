package org.fractalx.core.validation.rules;

import org.fractalx.core.datamanagement.SagaAnalyzer;
import org.fractalx.core.model.SagaDefinition;
import org.fractalx.core.validation.ValidationContext;
import org.fractalx.core.validation.ValidationIssue;
import org.fractalx.core.validation.ValidationRule;
import org.fractalx.core.validation.ValidationSeverity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates the integrity of {@code @DistributedSaga}-annotated methods.
 *
 * <p>Three error conditions are checked:
 * <ul>
 *   <li><b>SAGA_NO_ID:</b> A {@code @DistributedSaga} annotation has no {@code sagaId}
 *       — the saga orchestrator cannot track saga instances without a stable ID.</li>
 *   <li><b>SAGA_DUPLICATE_ID:</b> Two saga methods declare the same {@code sagaId}
 *       — the orchestrator would merge their state and corrupt both sagas.</li>
 *   <li><b>SAGA_BAD_TIMEOUT:</b> The {@code timeout} value is ≤ 0 — sagas with
 *       non-positive timeouts never time out, causing orphaned saga instances.</li>
 * </ul>
 */
public class SagaIntegrityRule implements ValidationRule {

    @Override
    public String ruleId() { return "SAGA_INTEGRITY"; }

    @Override
    public List<ValidationIssue> validate(ValidationContext ctx) throws IOException {
        List<ValidationIssue> issues = new ArrayList<>();

        List<SagaDefinition> sagas = new SagaAnalyzer()
                .analyzeSagas(ctx.sourceRoot(), ctx.modules());

        if (sagas.isEmpty()) return issues;

        Map<String, List<SagaDefinition>> byId = new LinkedHashMap<>();

        for (SagaDefinition saga : sagas) {
            String id = saga.getSagaId();

            // SAGA_NO_ID
            if (id == null || id.isBlank()) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, "SAGA_NO_ID", saga.getOwnerServiceName(),
                        "@DistributedSaga on " + saga.getOwnerClassName() + "." + saga.getMethodName()
                        + " has no sagaId",
                        "Add sagaId = \"" + saga.getMethodName().toLowerCase().replace("_", "-")
                        + "-saga\" to the @DistributedSaga annotation"));
                continue; // don't check other constraints without an id
            }

            // SAGA_BAD_TIMEOUT
            if (saga.getTimeoutMs() <= 0) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, "SAGA_BAD_TIMEOUT", saga.getOwnerServiceName(),
                        "@DistributedSaga(sagaId = \"" + id + "\") has timeout " + saga.getTimeoutMs()
                        + " ms — must be > 0",
                        "Set a positive timeout, e.g. @DistributedSaga(sagaId = \"" + id
                        + "\", timeout = 30000)"));
            }

            byId.computeIfAbsent(id, k -> new ArrayList<>()).add(saga);
        }

        // SAGA_DUPLICATE_ID
        for (Map.Entry<String, List<SagaDefinition>> entry : byId.entrySet()) {
            if (entry.getValue().size() > 1) {
                String locations = entry.getValue().stream()
                        .map(s -> s.getOwnerClassName() + "." + s.getMethodName())
                        .reduce((String a, String b) -> a + ", " + b).orElse("");
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, "SAGA_DUPLICATE_ID", "—",
                        "sagaId \"" + entry.getKey() + "\" is declared in " + entry.getValue().size()
                        + " methods: " + locations,
                        "Each @DistributedSaga must have a globally unique sagaId"));
            }
        }

        return issues;
    }
}
