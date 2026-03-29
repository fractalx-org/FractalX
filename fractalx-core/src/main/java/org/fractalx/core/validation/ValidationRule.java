package org.fractalx.core.validation;

import java.io.IOException;
import java.util.List;

/**
 * Strategy interface for a single decomposition validation rule.
 *
 * <p>Rules are invoked by {@link DecompositionValidator} before code generation
 * starts. Each rule inspects the module list (and optionally the source tree)
 * and returns zero or more {@link ValidationIssue}s.
 *
 * <p>Rules must be <em>stateless</em>: they receive all required context through
 * {@link ValidationContext} and must not cache state between invocations.
 *
 * <p>Rule implementations <strong>must not throw</strong> unchecked exceptions —
 * {@link DecompositionValidator} wraps each rule call in a try/catch so a broken
 * rule never silently blocks decomposition.
 */
public interface ValidationRule {

    /**
     * Short all-caps identifier shown in console output, e.g. {@code "DUP_PORT"}.
     * Must be unique across all registered rules.
     */
    String ruleId();

    /**
     * Run the rule against the given context and return any issues found.
     *
     * @param ctx shared context (modules, source root, config)
     * @return list of issues — empty if the rule passes
     * @throws IOException if source-tree inspection fails
     */
    List<ValidationIssue> validate(ValidationContext ctx) throws IOException;
}
