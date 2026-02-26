package com.fractalx.core.generator.admin;

import com.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates the service topology model, provider, and REST controller for the admin
 * service. The module dependency graph is baked in at generation time so the admin
 * service can serve the topology without any live analysis.
 */
class AdminTopologyGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminTopologyGenerator.class);

    void generate(Path srcMainJava, String basePackage, List<FractalModule> modules) throws IOException {
        Path pkg = AdminPackageUtil.createPackagePath(srcMainJava, basePackage + ".topology");

        generateTopologyModel(pkg);
        generateTopologyProvider(pkg, modules);
        generateTopologyController(pkg, modules);

        log.debug("Generated admin topology");
    }

    private void generateTopologyModel(Path pkg) throws IOException {
        String content = """
                package com.fractalx.admin.topology;

                import java.util.List;

                public class TopologyGraph {

                    private final List<ServiceNode> nodes;
                    private final List<ServiceEdge> edges;

                    public TopologyGraph(List<ServiceNode> nodes, List<ServiceEdge> edges) {
                        this.nodes = nodes;
                        this.edges = edges;
                    }

                    public List<ServiceNode> getNodes() { return nodes; }
                    public List<ServiceEdge> getEdges() { return edges; }

                    public record ServiceNode(String id, String label, int port, String type) {}
                    public record ServiceEdge(String source, String target, String protocol) {}
                }
                """;
        Files.writeString(pkg.resolve("TopologyGraph.java"), content);
    }

    private void generateTopologyProvider(Path pkg, List<FractalModule> modules) throws IOException {
        StringBuilder nodes = new StringBuilder();
        StringBuilder edges = new StringBuilder();

        for (FractalModule m : modules) {
            nodes.append(String.format(
                    "        nodes.add(new TopologyGraph.ServiceNode(\"%s\", \"%s\", %d, \"microservice\"));\n",
                    m.getServiceName(), m.getServiceName(), m.getPort()));
            for (String dep : m.getDependencies()) {
                String target = dep.replaceAll("(?i)(Service|Client)$", "");
                target = target.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase() + "-service";
                edges.append(String.format(
                        "        edges.add(new TopologyGraph.ServiceEdge(\"%s\", \"%s\", \"grpc\"));\n",
                        m.getServiceName(), target));
            }
        }
        nodes.append("        nodes.add(new TopologyGraph.ServiceNode(\"fractalx-gateway\",   \"API Gateway\",     9999, \"gateway\"));\n");
        nodes.append("        nodes.add(new TopologyGraph.ServiceNode(\"fractalx-registry\",  \"Service Registry\", 8761, \"registry\"));\n");
        nodes.append("        nodes.add(new TopologyGraph.ServiceNode(\"admin-service\",       \"Admin Dashboard\",  9090, \"admin\"));\n");

        String content = """
                package com.fractalx.admin.topology;

                import org.springframework.stereotype.Component;

                import java.util.ArrayList;
                import java.util.List;

                /** Baked-in service topology graph — nodes and edges derived at generation time. */
                @Component
                public class ServiceTopologyProvider {

                    public TopologyGraph getTopology() {
                        List<TopologyGraph.ServiceNode> nodes = new ArrayList<>();
                        List<TopologyGraph.ServiceEdge> edges = new ArrayList<>();

                %s
                %s
                        return new TopologyGraph(nodes, edges);
                    }
                }
                """.formatted(nodes.toString(), edges.toString());
        Files.writeString(pkg.resolve("ServiceTopologyProvider.java"), content);
    }

    private void generateTopologyController(Path pkg, List<FractalModule> modules) throws IOException {
        StringBuilder healthChecks = new StringBuilder();
        for (FractalModule m : modules) {
            healthChecks.append(String.format(
                    "        summary.put(\"%s\", checkHealth(\"http://localhost:%d/actuator/health\"));\n",
                    m.getServiceName(), m.getPort()));
        }
        healthChecks.append("        summary.put(\"fractalx-registry\", checkHealth(\"http://localhost:8761/services/health\"));\n");
        healthChecks.append("        summary.put(\"fractalx-gateway\",  checkHealth(\"http://localhost:9999/actuator/health\"));\n");

        String content = """
                package com.fractalx.admin.topology;

                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;
                import org.springframework.web.client.RestTemplate;

                import java.util.LinkedHashMap;
                import java.util.Map;

                @RestController
                @RequestMapping("/api")
                public class TopologyController {

                    private final ServiceTopologyProvider topologyProvider;
                    private final RestTemplate restTemplate = new RestTemplate();

                    public TopologyController(ServiceTopologyProvider topologyProvider) {
                        this.topologyProvider = topologyProvider;
                    }

                    /** Returns the static service dependency graph (nodes + edges). */
                    @GetMapping("/topology")
                    public ResponseEntity<TopologyGraph> getTopology() {
                        return ResponseEntity.ok(topologyProvider.getTopology());
                    }

                    /** Polls live health from each known service and returns a name→status map. */
                    @GetMapping("/health/summary")
                    public ResponseEntity<Map<String, String>> getHealthSummary() {
                        Map<String, String> summary = new LinkedHashMap<>();
                %s
                        return ResponseEntity.ok(summary);
                    }

                    /** Proxies the fractalx-registry /services endpoint for the admin UI. */
                    @GetMapping("/services")
                    public ResponseEntity<Object> getLiveServices() {
                        try {
                            String registryUrl = System.getProperty("fractalx.registry.url",
                                    "http://localhost:8761");
                            return ResponseEntity.ok(
                                    restTemplate.getForObject(registryUrl + "/services", Object.class));
                        } catch (Exception e) {
                            return ResponseEntity.ok(
                                    Map.of("error", "Registry unavailable: " + e.getMessage()));
                        }
                    }

                    private String checkHealth(String healthUrl) {
                        try {
                            String resp = restTemplate.getForObject(healthUrl, String.class);
                            return (resp != null && resp.contains("UP")) ? "UP" : "DOWN";
                        } catch (Exception e) {
                            return "DOWN";
                        }
                    }
                }
                """.formatted(healthChecks.toString());
        Files.writeString(pkg.resolve("TopologyController.java"), content);
    }
}
