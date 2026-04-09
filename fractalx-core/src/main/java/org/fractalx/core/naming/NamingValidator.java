package org.fractalx.core.naming;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-pipeline validation that checks all inter-module dependency names can be
 * resolved before any generation step runs.
 *
 * <p>Call {@link #validate(List)} immediately after modules are analysed and
 * before the first {@link org.fractalx.core.generator.ServiceFileGenerator} step.
 * Unresolvable dependencies are logged as warnings — generation proceeds with
 * those wiring steps silently skipped (never a broken build).
 */
public final class NamingValidator {

    private static final Logger log = LoggerFactory.getLogger(NamingValidator.class);

    private final NameResolver resolver;

    public NamingValidator(NameResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Validates that every dependency declared by every module can be resolved
     * to a known module via {@link NameResolver#findModule}.
     *
     * <p>Warnings are emitted for each unresolvable dependency; no exception is thrown.
     */
    public void validate(List<FractalModule> modules) {
        List<String> warnings = new ArrayList<>();

        for (FractalModule module : modules) {
            for (String dep : module.getDependencies()) {
                String derivedName = resolver.beanTypeToServiceName(dep);
                FractalModule target = resolver.findModule(derivedName, modules);
                if (target == null) {
                    warnings.add(String.format(
                            "  [%s] dependency '%s' → '%s' cannot be resolved to any module. " +
                            "Ensure the bean type name maps (via normalization) to a declared service.",
                            module.getServiceName(), dep, derivedName));
                }
            }
        }

        if (!warnings.isEmpty()) {
            log.warn("FractalX naming validation: {} unresolvable dependency(ies) detected " +
                     "— wiring steps for these will be skipped:", warnings.size());
            warnings.forEach(log::warn);
            log.warn("Tip: verify that bean type names match service names after applying " +
                     "fractalx.naming.aggregate-class-suffixes normalization.");
        } else {
            log.debug("Naming validation passed — all {} module dependencies resolvable", modules.size());
        }
    }
}
