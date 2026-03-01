package org.fractalx.core.verifier;

import org.fractalx.core.model.FractalModule;

import java.util.*;

/**
 * Builds a directed dependency graph from the decomposed modules and runs
 * structural analyses on it:
 *
 * <ul>
 *   <li><b>Cycle detection</b> — if A→B→C→A, all three services would dead-lock
 *       during startup and NetScope calls would form infinite loops</li>
 *   <li><b>Fan-out (efferent coupling)</b> — services calling too many peers
 *       are tightly coupled and fragile</li>
 *   <li><b>Fan-in (afferent coupling)</b> — services called by too many peers
 *       are bottlenecks; high fan-in is a stability risk</li>
 *   <li><b>Orphaned services</b> — services with no dependencies and no callers
 *       may be incorrectly isolated</li>
 * </ul>
 */
public class ServiceGraphAnalyzer {

    private static final int MAX_FAN_OUT = 5;
    private static final int MAX_FAN_IN  = 5;

    // ── Result model ──────────────────────────────────────────────────────────

    public enum FindingKind {
        CYCLE, HIGH_FAN_OUT, HIGH_FAN_IN, ORPHAN
    }

    public record Finding(FindingKind kind, String service, String detail) {
        public boolean isCritical() { return kind == FindingKind.CYCLE; }
    }

    public record GraphReport(
            List<Finding> findings,
            Map<String, Integer> fanOut,
            Map<String, Integer> fanIn,
            List<List<String>> cycles
    ) {
        public boolean hasCycles() { return !cycles.isEmpty(); }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public GraphReport analyse(List<FractalModule> modules) {
        // Build adjacency: serviceName → set of dependency serviceNames it calls
        Map<String, Set<String>> graph = buildGraph(modules);

        Map<String, Integer> fanOut = new LinkedHashMap<>();
        Map<String, Integer> fanIn  = new LinkedHashMap<>();
        graph.keySet().forEach(s -> { fanOut.put(s, 0); fanIn.put(s, 0); });

        graph.forEach((caller, deps) -> {
            fanOut.put(caller, deps.size());
            deps.forEach(dep -> fanIn.merge(dep, 1, Integer::sum));
        });

        List<List<String>> cycles = detectCycles(graph);
        List<Finding> findings    = new ArrayList<>();

        // Cycle findings
        for (List<String> cycle : cycles) {
            findings.add(new Finding(FindingKind.CYCLE,
                    String.join(" → ", cycle),
                    "Circular service dependency — remove or break with an event/saga"));
        }

        // Fan-out / fan-in
        fanOut.forEach((svc, n) -> {
            if (n > MAX_FAN_OUT)
                findings.add(new Finding(FindingKind.HIGH_FAN_OUT, svc,
                        "Calls " + n + " peers (threshold: " + MAX_FAN_OUT
                                + ") — consider splitting responsibilities"));
        });
        fanIn.forEach((svc, n) -> {
            if (n > MAX_FAN_IN)
                findings.add(new Finding(FindingKind.HIGH_FAN_IN, svc,
                        "Called by " + n + " peers (threshold: " + MAX_FAN_IN
                                + ") — bottleneck risk, consider caching or replication"));
        });

        // Orphans
        graph.forEach((svc, deps) -> {
            if (deps.isEmpty() && fanIn.getOrDefault(svc, 0) == 0 && modules.size() > 1)
                findings.add(new Finding(FindingKind.ORPHAN, svc,
                        "No callers and no dependencies — verify it was correctly decomposed"));
        });

        return new GraphReport(findings, fanOut, fanIn, cycles);
    }

    // ── Graph construction ────────────────────────────────────────────────────

    private Map<String, Set<String>> buildGraph(List<FractalModule> modules) {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        Map<String, String> classToService = new HashMap<>();

        // Build class→service lookup
        for (FractalModule m : modules) {
            graph.put(m.getServiceName(), new LinkedHashSet<>());
            if (m.getClassName() != null)
                classToService.put(m.getClassName(), m.getServiceName());
        }

        // Resolve dependencies
        for (FractalModule m : modules) {
            for (String dep : m.getDependencies()) {
                // dep is a bean class name like "PaymentService"
                String targetService = classToService.get(dep);
                if (targetService != null && !targetService.equals(m.getServiceName()))
                    graph.get(m.getServiceName()).add(targetService);
            }
        }
        return graph;
    }

    // ── Cycle detection (iterative DFS) ──────────────────────────────────────

    private List<List<String>> detectCycles(Map<String, Set<String>> graph) {
        List<List<String>> found = new ArrayList<>();
        Set<String> visited   = new HashSet<>();
        Set<String> inStack   = new HashSet<>();

        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                Deque<String> path = new ArrayDeque<>();
                dfs(node, graph, visited, inStack, path, found);
            }
        }
        return found;
    }

    private void dfs(String node, Map<String, Set<String>> graph,
                     Set<String> visited, Set<String> inStack,
                     Deque<String> path, List<List<String>> found) {
        visited.add(node);
        inStack.add(node);
        path.addLast(node);

        for (String neighbour : graph.getOrDefault(node, Set.of())) {
            if (!visited.contains(neighbour)) {
                dfs(neighbour, graph, visited, inStack, path, found);
            } else if (inStack.contains(neighbour)) {
                // Cycle detected — extract it from the path
                List<String> cycle = new ArrayList<>();
                boolean collect = false;
                for (String s : path) {
                    if (s.equals(neighbour)) collect = true;
                    if (collect) cycle.add(s);
                }
                cycle.add(neighbour); // close the loop
                found.add(cycle);
            }
        }

        path.removeLast();
        inStack.remove(node);
    }
}
