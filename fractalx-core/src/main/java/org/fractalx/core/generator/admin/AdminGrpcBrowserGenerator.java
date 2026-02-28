package org.fractalx.core.generator.admin;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates the gRPC / NetScope Browser subsystem for the admin service.
 *
 * <p>Produces 1 class in {@code org.fractalx.admin.grpc}:
 * <ul>
 *   <li>{@code GrpcBrowserController} — REST API: /api/grpc/**</li>
 * </ul>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/grpc/services            — microservices that have a gRPC port</li>
 *   <li>GET  /api/grpc/connections         — all NetScope/gRPC dependency links</li>
 *   <li>GET  /api/grpc/{service}/deps      — upstream + downstream deps for one service</li>
 *   <li>POST /api/grpc/ping               — TCP-ping {host, port} → {reachable, latencyMs}</li>
 * </ul>
 */
class AdminGrpcBrowserGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminGrpcBrowserGenerator.class);

    void generate(Path srcMainJava, String basePackage, List<FractalModule> modules) throws IOException {
        Path pkg = AdminPackageUtil.createPackagePath(srcMainJava, basePackage + ".grpc");
        generateGrpcBrowserController(pkg);
        log.debug("Generated admin gRPC browser subsystem ({} modules)", modules.size());
    }

    // -------------------------------------------------------------------------

    private void generateGrpcBrowserController(Path pkg) throws IOException {
        Files.writeString(pkg.resolve("GrpcBrowserController.java"), """
                package org.fractalx.admin.grpc;

                import org.fractalx.admin.services.ServiceMetaRegistry;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.web.bind.annotation.*;

                import java.net.InetSocketAddress;
                import java.net.Socket;
                import java.util.*;
                import java.util.stream.Collectors;

                /**
                 * gRPC / NetScope Browser — exposes service gRPC port information and
                 * dependency graphs. Also provides a TCP-level ping for reachability checks.
                 */
                @RestController
                @RequestMapping("/api/grpc")
                public class GrpcBrowserController {

                    private static final Logger log = LoggerFactory.getLogger(GrpcBrowserController.class);
                    private final ServiceMetaRegistry registry;

                    public GrpcBrowserController(ServiceMetaRegistry registry) {
                        this.registry = registry;
                    }

                    /** All services that expose a gRPC port (grpcPort > 0). */
                    @GetMapping("/services")
                    public List<Map<String, Object>> services() {
                        return registry.getAll().stream()
                                .filter(s -> s.grpcPort() > 0)
                                .map(s -> {
                                    Map<String, Object> m = new LinkedHashMap<>();
                                    m.put("name",      s.name());
                                    m.put("httpPort",  s.port());
                                    m.put("grpcPort",  s.grpcPort());
                                    m.put("type",      s.type());
                                    m.put("deps",      s.dependencies());
                                    m.put("package",   s.packageName());
                                    return m;
                                })
                                .sorted(Comparator.comparing(m -> String.valueOf(m.get("name"))))
                                .collect(Collectors.toList());
                    }

                    /** All NetScope connections (who calls whom, over which gRPC port). */
                    @GetMapping("/connections")
                    public List<Map<String, Object>> connections() {
                        List<Map<String, Object>> result = new ArrayList<>();
                        for (ServiceMetaRegistry.ServiceMeta svc : registry.getAll()) {
                            if (svc.dependencies().isEmpty()) continue;
                            List<Map<String, Object>> deps = new ArrayList<>();
                            for (String dep : svc.dependencies()) {
                                registry.findByName(dep).ifPresent(target -> {
                                    if (target.grpcPort() > 0) {
                                        Map<String, Object> d = new LinkedHashMap<>();
                                        d.put("name",     target.name());
                                        d.put("grpcPort", target.grpcPort());
                                        d.put("httpPort", target.port());
                                        d.put("protocol", "NetScope/gRPC");
                                        deps.add(d);
                                    }
                                });
                            }
                            if (!deps.isEmpty()) {
                                Map<String, Object> entry = new LinkedHashMap<>();
                                entry.put("service",      svc.name());
                                entry.put("httpPort",     svc.port());
                                entry.put("dependencies", deps);
                                result.add(entry);
                            }
                        }
                        return result;
                    }

                    /** Upstream + downstream gRPC dependencies for one service. */
                    @GetMapping("/{service}/deps")
                    public Map<String, Object> serviceDeps(@PathVariable String service) {
                        ServiceMetaRegistry.ServiceMeta meta = registry.findByName(service).orElse(null);
                        if (meta == null) return Map.of("error", "Unknown service: " + service);

                        // downstream: what this service calls
                        List<Map<String, Object>> downstream = new ArrayList<>();
                        for (String dep : meta.dependencies()) {
                            registry.findByName(dep).ifPresent(t -> {
                                Map<String, Object> d = new LinkedHashMap<>();
                                d.put("name",     t.name());
                                d.put("grpcPort", t.grpcPort());
                                d.put("direction","DOWNSTREAM");
                                downstream.add(d);
                            });
                        }

                        // upstream: who calls this service
                        List<Map<String, Object>> upstream = new ArrayList<>();
                        for (ServiceMetaRegistry.ServiceMeta caller : registry.getAll()) {
                            if (caller.dependencies().contains(service)) {
                                Map<String, Object> u = new LinkedHashMap<>();
                                u.put("name",      caller.name());
                                u.put("httpPort",  caller.port());
                                u.put("direction", "UPSTREAM");
                                upstream.add(u);
                            }
                        }

                        Map<String, Object> out = new LinkedHashMap<>();
                        out.put("service",    service);
                        out.put("grpcPort",   meta.grpcPort());
                        out.put("httpPort",   meta.port());
                        out.put("downstream", downstream);
                        out.put("upstream",   upstream);
                        return out;
                    }

                    /** TCP-level reachability ping for a gRPC port. */
                    @PostMapping("/ping")
                    public Map<String, Object> ping(@RequestBody Map<String, Object> req) {
                        String host = String.valueOf(req.getOrDefault("host", "localhost"));
                        int port = 0;
                        try { port = Integer.parseInt(String.valueOf(req.get("port"))); }
                        catch (Exception e) { return Map.of("error", "Invalid port"); }
                        long start = System.currentTimeMillis();
                        try (Socket s = new Socket()) {
                            s.connect(new InetSocketAddress(host, port), 2000);
                            long ms = System.currentTimeMillis() - start;
                            return Map.of("reachable", true, "latencyMs", ms,
                                    "host", host, "port", port);
                        } catch (Exception e) {
                            long ms = System.currentTimeMillis() - start;
                            log.debug("gRPC ping {}:{} failed: {}", host, port, e.getMessage());
                            return Map.of("reachable", false, "latencyMs", ms,
                                    "host", host, "port", port, "error", e.getMessage());
                        }
                    }
                }
                """);
    }
}
