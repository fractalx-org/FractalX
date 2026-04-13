package org.fractalx.core.graph;

import java.nio.file.Path;
import java.util.Set;

public record GraphNode(
        String fqcn,
        String simpleName,
        NodeKind kind,
        Set<String> annotations,
        Set<String> implementedInterfaces,
        String superclass,
        String packageName,
        Path sourceFile
) {
    public GraphNode {
        annotations = Set.copyOf(annotations);
        implementedInterfaces = Set.copyOf(implementedInterfaces);
    }
}
