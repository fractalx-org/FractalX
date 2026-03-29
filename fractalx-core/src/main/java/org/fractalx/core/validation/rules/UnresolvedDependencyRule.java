package org.fractalx.core.validation.rules;

import org.fractalx.core.generator.service.NetScopeClientGenerator;
import org.fractalx.core.model.FractalModule;
import org.fractalx.core.validation.ValidationContext;
import org.fractalx.core.validation.ValidationIssue;
import org.fractalx.core.validation.ValidationRule;
import org.fractalx.core.validation.ValidationSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Warns when a module lists a dependency that cannot be mapped to any known
 * {@code @DecomposableModule} service name.
 *
 * <p>If dependency type {@code BillingEngine} is detected (or declared) but no
 * module has {@code serviceName = "billing-service"} (the canonical conversion),
 * {@code NetScopeClientGenerator} will silently skip that client, leaving the
 * field unresolved at runtime.
 *
 * <p>This is a WARNING (not ERROR) because the dependency might be an
 * infrastructure bean or an intentionally external service not tracked by FractalX.
 */
public class UnresolvedDependencyRule implements ValidationRule {

    @Override
    public String ruleId() { return "UNRESOLVED_DEP"; }

    @Override
    public List<ValidationIssue> validate(ValidationContext ctx) {
        List<ValidationIssue> issues = new ArrayList<>();

        Set<String> knownServiceNames = ctx.modules().stream()
                .map(FractalModule::getServiceName)
                .collect(Collectors.toSet());

        for (FractalModule module : ctx.modules()) {
            for (String depBeanType : module.getDependencies()) {
                String resolved = NetScopeClientGenerator.beanTypeToServiceName(depBeanType);
                if (!knownServiceNames.contains(resolved)) {
                    issues.add(new ValidationIssue(
                            ValidationSeverity.WARNING, ruleId(), module.getServiceName(),
                            "dependency '" + depBeanType + "' resolves to service name '"
                            + resolved + "' but no @DecomposableModule with that name was found",
                            "Either annotate the target class with @DecomposableModule(serviceName = \""
                            + resolved + "\", ...) or declare the dependency explicitly in "
                            + "@DecomposableModule(dependencies = {" + depBeanType + ".class, ...})"));
                }
            }
        }
        return issues;
    }
}
