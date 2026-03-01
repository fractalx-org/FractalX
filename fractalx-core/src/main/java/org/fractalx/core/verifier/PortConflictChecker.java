package org.fractalx.core.verifier;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects port conflicts in the generated service mesh.
 *
 * <p>Two types of conflict are checked:
 * <ol>
 *   <li><b>HTTP port conflicts</b> — two services sharing the same HTTP port</li>
 *   <li><b>gRPC port conflicts</b> — two services sharing the same gRPC port
 *       (convention: HTTP port + 10000)</li>
 * </ol>
 *
 * <p>Ports are read from the {@link FractalModule} metadata for user services.
 * For infrastructure services (registry, gateway, admin, logger), the generator
 * bakes fixed well-known ports which are also checked:
 * <ul>
 *   <li>fractalx-registry — 8761</li>
 *   <li>fractalx-gateway  — 9999</li>
 *   <li>admin-service     — 9090</li>
 *   <li>logger-service    — 9099</li>
 *   <li>fractalx-saga-orchestrator — 8099</li>
 * </ul>
 */
public class PortConflictChecker {

    private static final Logger log = LoggerFactory.getLogger(PortConflictChecker.class);

    private static final Pattern SERVER_PORT_PATTERN =
            Pattern.compile("server\\.port\\s*:\\s*(\\d+)");

    // Well-known ports baked into infrastructure services by the generators
    private static final Map<String, Integer> INFRA_HTTP_PORTS = Map.of(
            "fractalx-registry",          8761,
            "fractalx-gateway",           9999,
            "admin-service",              9090,
            "logger-service",             9099,
            "fractalx-saga-orchestrator", 8099
    );

    public record Conflict(
            String service1, int port1,
            String service2, int port2,
            String protocol
    ) {
        @Override
        public String toString() {
            return "[FAIL] Port conflict (" + protocol + " port " + port1 + "): "
                    + service1 + " and " + service2 + " both use port " + port1;
        }
    }

    /**
     * Checks for HTTP and gRPC port conflicts across all services.
     *
     * @param outputDir root generated-services directory
     * @param modules   all decomposed modules
     * @return list of conflicts (empty = no conflicts)
     */
    public List<Conflict> check(Path outputDir, List<FractalModule> modules) {
        // service-name → http port
        Map<String, Integer> httpPorts = new LinkedHashMap<>();

        // Seed with infrastructure ports
        INFRA_HTTP_PORTS.forEach((svc, port) -> {
            if (Files.isDirectory(outputDir.resolve(svc))) {
                httpPorts.put(svc, port);
            }
        });

        // Add user module ports (prefer reading from generated application.yml
        // as the module metadata might differ from what was actually baked in)
        for (FractalModule module : modules) {
            int port = readBakedPort(outputDir, module).orElse(module.getPort());
            httpPorts.put(module.getServiceName(), port);
        }

        List<Conflict> conflicts = new ArrayList<>();
        detectConflicts(httpPorts, "HTTP",  conflicts);
        detectConflicts(grpcPorts(httpPorts), "gRPC", conflicts);
        return conflicts;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void detectConflicts(Map<String, Integer> portMap, String protocol,
                                 List<Conflict> conflicts) {
        // port → first service that claimed it
        Map<Integer, String> seen = new LinkedHashMap<>();
        portMap.forEach((svc, port) -> {
            if (seen.containsKey(port)) {
                conflicts.add(new Conflict(seen.get(port), port, svc, port, protocol));
            } else {
                seen.put(port, svc);
            }
        });
    }

    /** Derives gRPC ports using the HTTP + 10000 convention. */
    private Map<String, Integer> grpcPorts(Map<String, Integer> httpPorts) {
        Map<String, Integer> grpc = new LinkedHashMap<>();
        httpPorts.forEach((svc, http) -> grpc.put(svc, http + 10000));
        return grpc;
    }

    /**
     * Tries to read the actual {@code server.port} from the generated
     * {@code application.yml} to catch any override made during generation.
     */
    private Optional<Integer> readBakedPort(Path outputDir, FractalModule module) {
        Path yml = outputDir.resolve(module.getServiceName())
                .resolve("src/main/resources/application.yml");
        if (!Files.exists(yml)) return Optional.empty();
        try {
            String content = Files.readString(yml);
            Matcher m = SERVER_PORT_PATTERN.matcher(content);
            if (m.find()) return Optional.of(Integer.parseInt(m.group(1)));
        } catch (IOException | NumberFormatException e) {
            log.debug("Could not read port from {}: {}", yml, e.getMessage());
        }
        return Optional.empty();
    }
}
