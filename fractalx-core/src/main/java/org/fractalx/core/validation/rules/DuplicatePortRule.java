package org.fractalx.core.validation.rules;

import org.fractalx.core.model.FractalModule;
import org.fractalx.core.validation.ValidationContext;
import org.fractalx.core.validation.ValidationIssue;
import org.fractalx.core.validation.ValidationRule;
import org.fractalx.core.validation.ValidationSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Detects port conflicts between decomposed microservices.
 *
 * <p>Three classes of conflict are checked:
 * <ol>
 *   <li><b>HTTP–HTTP:</b> Two modules declare the same HTTP port — both would fail to start.</li>
 *   <li><b>gRPC–gRPC:</b> Two modules derive the same gRPC port (HTTP + 10 000).</li>
 *   <li><b>HTTP–gRPC cross:</b> One module's HTTP port equals another module's gRPC port —
 *       the gRPC server of one and the HTTP server of the other would fight over the same
 *       OS port.</li>
 * </ol>
 */
public class DuplicatePortRule implements ValidationRule {

    @Override
    public String ruleId() { return "DUP_PORT"; }

    @Override
    public List<ValidationIssue> validate(ValidationContext ctx) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<FractalModule>   mods   = ctx.modules();

        // --- HTTP–HTTP conflicts ---
        Map<Integer, List<FractalModule>> byHttp = mods.stream()
                .collect(Collectors.groupingBy(FractalModule::getPort));
        byHttp.forEach((port, group) -> {
            if (group.size() > 1) {
                String names = group.stream()
                        .map(FractalModule::getServiceName)
                        .collect(Collectors.joining(", "));
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, ruleId(), group.get(0).getServiceName(),
                        "HTTP port " + port + " is shared by: " + names,
                        "Assign unique HTTP ports in @DecomposableModule(port = ...)"));
            }
        });

        // --- gRPC–gRPC conflicts ---
        Map<Integer, List<FractalModule>> byGrpc = mods.stream()
                .collect(Collectors.groupingBy(FractalModule::grpcPort));
        byGrpc.forEach((port, group) -> {
            if (group.size() > 1) {
                String names = group.stream()
                        .map(FractalModule::getServiceName)
                        .collect(Collectors.joining(", "));
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, ruleId(), group.get(0).getServiceName(),
                        "gRPC port " + port + " conflicts between: " + names
                        + " (gRPC port = HTTP port + 10000)",
                        "Assign unique HTTP ports so the derived gRPC ports are also unique"));
            }
        });

        // --- HTTP–gRPC cross-conflicts (each pair checked once: i < j) ---
        for (int i = 0; i < mods.size(); i++) {
            for (int j = i + 1; j < mods.size(); j++) {
                FractalModule a = mods.get(i);
                FractalModule b = mods.get(j);
                if (a.getPort() == b.grpcPort()) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR, ruleId(), a.getServiceName(),
                            a.getServiceName() + " HTTP port " + a.getPort()
                            + " collides with " + b.getServiceName() + " gRPC port " + b.grpcPort(),
                            "Change " + a.getServiceName() + "'s HTTP port so it does not equal "
                            + b.getServiceName() + "'s gRPC port (" + b.grpcPort() + ")"));
                }
                if (b.getPort() == a.grpcPort()) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR, ruleId(), b.getServiceName(),
                            b.getServiceName() + " HTTP port " + b.getPort()
                            + " collides with " + a.getServiceName() + " gRPC port " + a.grpcPort(),
                            "Change " + b.getServiceName() + "'s HTTP port so it does not equal "
                            + a.getServiceName() + "'s gRPC port (" + a.grpcPort() + ")"));
                }
            }
        }

        return issues;
    }
}
