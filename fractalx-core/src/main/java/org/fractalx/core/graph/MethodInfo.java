package org.fractalx.core.graph;

import java.util.List;
import java.util.Set;

/**
 * Method-level metadata captured from the AST. Enables structural queries
 * on method annotations, return types, string literals, and intra-method
 * call expressions — eliminating heuristics that previously re-walked the AST.
 */
public record MethodInfo(
        String name,
        Set<String> annotations,
        String returnType,
        List<String> parameterTypes,
        Set<String> stringLiterals,
        List<String> bodyMethodCalls
) {
    public MethodInfo {
        annotations = Set.copyOf(annotations);
        parameterTypes = List.copyOf(parameterTypes);
        stringLiterals = Set.copyOf(stringLiterals);
        bodyMethodCalls = List.copyOf(bodyMethodCalls);
    }
}
