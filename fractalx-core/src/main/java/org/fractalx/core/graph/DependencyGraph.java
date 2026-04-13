package org.fractalx.core.graph;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class DependencyGraph {

    private final Map<String, GraphNode> nodes;
    private final List<GraphEdge> edges;
    private final Map<String, List<GraphEdge>> outgoing;
    private final Map<String, List<GraphEdge>> incoming;

    private DependencyGraph(Map<String, GraphNode> nodes, List<GraphEdge> edges) {
        this.nodes = Map.copyOf(nodes);
        this.edges = List.copyOf(edges);

        Map<String, List<GraphEdge>> out = new HashMap<>();
        Map<String, List<GraphEdge>> in = new HashMap<>();
        for (GraphEdge e : edges) {
            out.computeIfAbsent(e.sourceNode(), k -> new ArrayList<>()).add(e);
            in.computeIfAbsent(e.targetNode(), k -> new ArrayList<>()).add(e);
        }
        this.outgoing = Map.copyOf(out);
        this.incoming = Map.copyOf(in);
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public Optional<GraphNode> node(String fqcn) {
        return Optional.ofNullable(nodes.get(fqcn));
    }

    public Collection<GraphNode> allNodes() {
        return nodes.values();
    }

    public List<GraphEdge> allEdges() {
        return edges;
    }

    // ── Edge queries ───────────────────────────────────────────────────────

    public List<GraphEdge> edgesFrom(String fqcn) {
        return outgoing.getOrDefault(fqcn, List.of());
    }

    public List<GraphEdge> edgesTo(String fqcn) {
        return incoming.getOrDefault(fqcn, List.of());
    }

    // ── Structural queries ─────────────────────────────────────────────────

    public List<GraphNode> calleesOf(String fqcn) {
        return resolveTargets(fqcn, EdgeKind.METHOD_CALL);
    }

    public List<GraphNode> callersOf(String fqcn) {
        return resolveSources(fqcn, EdgeKind.METHOD_CALL);
    }

    public List<GraphNode> fieldDependenciesOf(String fqcn) {
        return resolveTargets(fqcn, EdgeKind.FIELD_REFERENCE);
    }

    public List<GraphNode> implementorsOf(String fqcn) {
        return resolveSources(fqcn, EdgeKind.IMPLEMENTS);
    }

    public List<GraphNode> subclassesOf(String fqcn) {
        return resolveSources(fqcn, EdgeKind.EXTENDS);
    }

    // ── Filtering ──────────────────────────────────────────────────────────

    public List<GraphNode> nodesMatching(Predicate<GraphNode> predicate) {
        return nodes.values().stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    public List<GraphNode> nodesWithAnnotation(String annotationSimpleName) {
        return nodesMatching(n -> n.annotations().contains(annotationSimpleName));
    }

    public Map<String, List<GraphNode>> nodesByPackage() {
        return nodes.values().stream()
                .collect(Collectors.groupingBy(GraphNode::packageName));
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private List<GraphNode> resolveTargets(String fqcn, EdgeKind kind) {
        return edgesFrom(fqcn).stream()
                .filter(e -> e.kind() == kind)
                .map(e -> nodes.get(e.targetNode()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<GraphNode> resolveSources(String fqcn, EdgeKind kind) {
        return edgesTo(fqcn).stream()
                .filter(e -> e.kind() == kind)
                .map(e -> nodes.get(e.sourceNode()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ── Builder ────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
        private final List<GraphEdge> edges = new ArrayList<>();

        private Builder() {}

        public Builder addNode(GraphNode node) {
            nodes.put(node.fqcn(), node);
            return this;
        }

        public Builder addEdge(GraphEdge edge) {
            edges.add(edge);
            return this;
        }

        public DependencyGraph build() {
            return new DependencyGraph(nodes, edges);
        }
    }
}
