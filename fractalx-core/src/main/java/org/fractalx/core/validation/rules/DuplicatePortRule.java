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
        Map<Integer, List<FractalModule>> byHttp = groupByPort(mods, false);
        for (Map.Entry<Integer, List<FractalModule>> e : byHttp.entrySet()) {
            if (e.getValue().size() > 1) {
                String names = e.getValue().stream()
                        .map(FractalModule::getServiceName).reduce((a, b) -> a + ", " + b).orElse("");
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, ruleId(), e.getValue().get(0).getServiceName(),
                        "HTTP port " + e.getKey() + " is shared by: " + names,
                        "Assign unique HTTP ports in @DecomposableModule(port = ...)"));
            }
        }

        // --- gRPC–gRPC conflicts ---
        Map<Integer, List<FractalModule>> byGrpc = groupByPort(mods, true);
        for (Map.Entry<Integer, List<FractalModule>> e : byGrpc.entrySet()) {
            if (e.getValue().size() > 1) {
                String names = e.getValue().stream()
                        .map(FractalModule::getServiceName).reduce((a, b) -> a + ", " + b).orElse("");
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, ruleId(), e.getValue().get(0).getServiceName(),
                        "gRPC port " + e.getKey() + " conflicts between: " + names
                        + " (gRPC port = HTTP port + 10000)",
                        "Assign unique HTTP ports so the derived gRPC ports are also unique"));
            }
        }

        // --- HTTP–gRPC cross-conflicts ---
        for (FractalModule a : mods) {
            for (FractalModule b : mods) {
                if (a == b) continue;
                if (a.getPort() == b.grpcPort()) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.ERROR, ruleId(), a.getServiceName(),
                            a.getServiceName() + " HTTP port " + a.getPort()
                            + " collides with " + b.getServiceName() + " gRPC port " + b.grpcPort(),
                            "Change " + a.getServiceName() + "'s HTTP port so it does not equal "
                            + b.getServiceName() + "'s gRPC port (" + b.grpcPort() + ")"));
                }
            }
        }

        return issues;
    }

    private Map<Integer, List<FractalModule>> groupByPort(List<FractalModule> mods, boolean grpc) {
        Map<Integer, List<FractalModule>> map = new LinkedHashMap<>();
        for (FractalModule m : mods) {
            int port = grpc ? m.grpcPort() : m.getPort();
            map.computeIfAbsent(port, k -> new ArrayList<>()).add(m);
        }
        return map;
    }
}
