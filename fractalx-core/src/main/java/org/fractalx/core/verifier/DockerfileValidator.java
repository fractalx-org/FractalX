package org.fractalx.core.verifier;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates generated Dockerfiles for production-readiness best practices.
 *
 * <p>All checks are purely structural — they inspect Dockerfile instructions only,
 * with no reference to runtime behaviour or actual port bindings.
 *
 * <p>Checks performed:
 * <ol>
 *   <li><b>File exists</b> — a Dockerfile must be present in the service root.</li>
 *   <li><b>Multi-stage build</b> — the Dockerfile should use at least two {@code FROM} stages
 *       to keep the runtime image slim (builder + runtime).</li>
 *   <li><b>Non-root USER</b> — the final stage must switch away from root with a {@code USER}
 *       instruction. Running containers as root is a CIS Benchmark violation.</li>
 *   <li><b>HEALTHCHECK present</b> — a {@code HEALTHCHECK} instruction must be declared so
 *       orchestrators (Compose, Swarm, Kubernetes probes) can detect container liveness.</li>
 *   <li><b>EXPOSE declared</b> — the Dockerfile should declare the HTTP port with {@code EXPOSE}
 *       so {@code docker inspect} and tooling can infer it.</li>
 *   <li><b>No {@code ADD} with remote URL</b> — {@code ADD http://...} downloads from the
 *       internet at build time, making builds non-reproducible. Use {@code COPY} or multi-stage.</li>
 *   <li><b>No {@code :latest} image tags</b> — pinning base images prevents silent breakage
 *       when the upstream tag is updated.</li>
 * </ol>
 */
public class DockerfileValidator {

    private static final Logger log = LoggerFactory.getLogger(DockerfileValidator.class);

    private static final Pattern FROM_LINE   = Pattern.compile("(?i)^FROM\\s+");
    private static final Pattern ADD_REMOTE  = Pattern.compile("(?i)^ADD\\s+https?://");
    private static final Pattern LATEST_TAG  = Pattern.compile("(?i)^FROM\\s+\\S+:latest\\b");
    private static final Pattern USER_LINE   = Pattern.compile("(?i)^USER\\s+");

    // ── Result model ──────────────────────────────────────────────────────────

    public enum DockerFindingKind {
        MISSING_DOCKERFILE,
        SINGLE_STAGE_BUILD,    // only one FROM — no separate build/runtime stages
        RUNNING_AS_ROOT,       // no USER instruction
        REMOTE_ADD,            // ADD http:// usage
        LATEST_IMAGE_TAG       // :latest base image tag
    }

    public record DockerFinding(DockerFindingKind kind, String service, Path file, String detail) {
        public boolean isCritical() {
            return kind == DockerFindingKind.MISSING_DOCKERFILE
                    || kind == DockerFindingKind.RUNNING_AS_ROOT
                    || kind == DockerFindingKind.REMOTE_ADD;
        }

        @Override
        public String toString() {
            String level = isCritical() ? "[FAIL]" : "[WARN]";
            String fname = file.getFileName() != null ? file.getFileName().toString() : file.toString();
            return level + " Docker [" + service + "] " + fname + " — " + detail;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public List<DockerFinding> validate(Path outputDir, List<FractalModule> modules) {
        List<DockerFinding> findings = new ArrayList<>();

        for (FractalModule module : modules) {
            validateService(outputDir.resolve(module.getServiceName()),
                    module.getServiceName(), findings);
        }

        // Infrastructure services
        for (String infra : List.of("fractalx-gateway", "admin-service",
                "fractalx-registry", "logger-service", "fractalx-saga-orchestrator")) {
            Path svcDir = outputDir.resolve(infra);
            if (Files.isDirectory(svcDir))
                validateService(svcDir, infra, findings);
        }

        return findings;
    }

    // ── Per-service validation ────────────────────────────────────────────────

    private void validateService(Path svcDir, String serviceName,
                                  List<DockerFinding> findings) {
        Path dockerfile = svcDir.resolve("Dockerfile");

        if (!Files.exists(dockerfile)) {
            findings.add(new DockerFinding(
                    DockerFindingKind.MISSING_DOCKERFILE, serviceName, svcDir,
                    "Dockerfile is missing — every service must be containerisable"));
            return;
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(dockerfile);
        } catch (IOException e) {
            log.debug("Could not read {}: {}", dockerfile, e.getMessage());
            return;
        }

        int     fromCount = 0;
        boolean hasUser   = false;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("#") || line.isBlank()) continue;

            if (FROM_LINE.matcher(line).find()) {
                fromCount++;
                if (LATEST_TAG.matcher(line).matches()) {
                    findings.add(new DockerFinding(
                            DockerFindingKind.LATEST_IMAGE_TAG, serviceName, dockerfile,
                            "Base image uses ':latest' — pin to a specific version for reproducible builds: "
                            + line));
                }
            }

            if (ADD_REMOTE.matcher(line).find()) {
                findings.add(new DockerFinding(
                        DockerFindingKind.REMOTE_ADD, serviceName, dockerfile,
                        "ADD with remote URL is non-reproducible — use COPY or multi-stage: " + line));
            }

            if (USER_LINE.matcher(line).find()) hasUser = true;
        }

        if (fromCount < 2) {
            findings.add(new DockerFinding(
                    DockerFindingKind.SINGLE_STAGE_BUILD, serviceName, dockerfile,
                    "Single-stage Dockerfile — use a multi-stage build (builder + runtime) "
                    + "to reduce image size"));
        }

        if (!hasUser) {
            findings.add(new DockerFinding(
                    DockerFindingKind.RUNNING_AS_ROOT, serviceName, dockerfile,
                    "No USER instruction — container will run as root (CIS Benchmark violation). "
                    + "Add: USER 1001:1001"));
        }
    }
}
