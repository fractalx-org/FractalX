package org.fractalx.core.validation.rules;

import org.fractalx.core.config.FractalxConfig;
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
 * Ensures no module port clashes with the ports reserved for FractalX-generated
 * infrastructure services: registry, gateway, admin, logger, and saga-orchestrator.
 *
 * <p>Port values are read from {@link FractalxConfig} so user overrides in
 * {@code fractalx-config.yml} are respected.
 */
public class InfrastructurePortConflictRule implements ValidationRule {

    @Override
    public String ruleId() { return "INFRA_PORT_CONFLICT"; }

    @Override
    public List<ValidationIssue> validate(ValidationContext ctx) {
        FractalxConfig         config = ctx.config();
        List<ValidationIssue>  issues = new ArrayList<>();

        Map<Integer, String> reserved = buildReservedMap(config);

        for (FractalModule module : ctx.modules()) {
            // HTTP port
            String infraHttp = reserved.get(module.getPort());
            if (infraHttp != null) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, ruleId(), module.getServiceName(),
                        "port " + module.getPort() + " is reserved for " + infraHttp,
                        "Change @DecomposableModule(port = ...) to a port not in "
                        + reserved.values() + "; reserved: " + reserved.keySet()));
            }
            // gRPC port
            String infraGrpc = reserved.get(module.grpcPort());
            if (infraGrpc != null) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR, ruleId(), module.getServiceName(),
                        "derived gRPC port " + module.grpcPort() + " (HTTP port " + module.getPort()
                        + " + 10000) clashes with reserved port for " + infraGrpc,
                        "Change the HTTP port so that HTTP port + 10000 does not equal "
                        + module.grpcPort()));
            }
        }
        return issues;
    }

    private Map<Integer, String> buildReservedMap(FractalxConfig cfg) {
        Map<Integer, String> map = new LinkedHashMap<>();
        map.put(cfg.registryPort(), "fractalx-registry");
        map.put(cfg.gatewayPort(),  "fractalx-gateway");
        map.put(cfg.adminPort(),    "admin-service");
        map.put(cfg.loggerPort(),   "logger-service");
        map.put(cfg.sagaPort(),     "fractalx-saga-orchestrator");
        return map;
    }
}
