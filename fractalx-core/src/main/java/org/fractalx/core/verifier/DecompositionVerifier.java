package org.fractalx.core.verifier;

import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Statically verifies the output of {@code fractalx:decompose} without starting any services.
 *
 * <p>Checks performed:
 * <ol>
 *   <li>Infrastructure — registry, gateway, admin, logger, start/stop scripts, docker-compose</li>
 *   <li>Per-service — directory, pom.xml, Dockerfile, application.yml, correct port, main class</li>
 *   <li>NetScope wiring — @NetScopeClient present when module has dependencies;
 *       @NetworkPublic present when module is called by others</li>
 *   <li>Docker — docker-compose.yml references every service</li>
 * </ol>
 */
public class DecompositionVerifier {

    private static final Logger log = LoggerFactory.getLogger(DecompositionVerifier.class);

    // ── Result model ──────────────────────────────────────────────────────────

    public enum Status { PASS, WARN, FAIL }

    public record CheckResult(String category, String description, Status status, String detail) {}

    public record VerificationReport(List<CheckResult> results, int pass, int warn, int fail) {
        public boolean hasFailures() { return fail > 0; }
        public boolean hasWarnings() { return warn > 0; }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public VerificationReport verify(Path outputDir, List<FractalModule> modules) {
        List<CheckResult> results = new ArrayList<>();

        checkInfrastructure(outputDir, results);

        for (FractalModule module : modules) {
            checkModule(outputDir, module, modules, results);
        }

        checkNetScopeWiring(outputDir, modules, results);
        checkDocker(outputDir, modules, results);

        int pass = count(results, Status.PASS);
        int warn = count(results, Status.WARN);
        int fail = count(results, Status.FAIL);
        return new VerificationReport(results, pass, warn, fail);
    }

    // ── Infrastructure checks ─────────────────────────────────────────────────

    private void checkInfrastructure(Path out, List<CheckResult> results) {
        String cat = "Infrastructure";
        pass(results, cat, "fractalx-registry directory generated",
                Files.isDirectory(out.resolve("fractalx-registry")));
        pass(results, cat, "fractalx-gateway directory generated",
                Files.isDirectory(out.resolve("fractalx-gateway")));
        pass(results, cat, "admin-service directory generated",
                Files.isDirectory(out.resolve("admin-service")));
        pass(results, cat, "logger-service directory generated",
                Files.isDirectory(out.resolve("logger-service")));
        pass(results, cat, "docker-compose.yml generated",
                Files.exists(out.resolve("docker-compose.yml")));
        pass(results, cat, "start-all.sh generated",
                Files.exists(out.resolve("start-all.sh")));
        pass(results, cat, "stop-all.sh generated",
                Files.exists(out.resolve("stop-all.sh")));
    }

    // ── Per-service checks ────────────────────────────────────────────────────

    private void checkModule(Path out, FractalModule module,
                             List<FractalModule> allModules, List<CheckResult> results) {
        String name = module.getServiceName();
        Path svcDir = out.resolve(name);
        String cat  = "Service: " + name;

        boolean dirExists = Files.isDirectory(svcDir);
        pass(results, cat, "Service directory exists (port " + module.getPort() + ")", dirExists);
        if (!dirExists) return; // can't check anything else

        pass(results, cat, "pom.xml generated",
                Files.exists(svcDir.resolve("pom.xml")));
        pass(results, cat, "Dockerfile generated",
                Files.exists(svcDir.resolve("Dockerfile")));

        Path appYml = svcDir.resolve("src/main/resources/application.yml");
        pass(results, cat, "application.yml generated", Files.exists(appYml));

        if (Files.exists(appYml)) {
            checkApplicationYml(appYml, module, cat, results);
        }

        checkMainClass(svcDir, module, cat, results);
        checkNetScopePerService(svcDir, module, allModules, cat, results);
    }

    private void checkApplicationYml(Path appYml, FractalModule module,
                                     String cat, List<CheckResult> results) {
        try {
            String yml = Files.readString(appYml);
            pass(results, cat, "Port " + module.getPort() + " in application.yml",
                    yml.contains(String.valueOf(module.getPort())));
            pass(results, cat, "spring.application.name set in application.yml",
                    yml.contains("application.name") || yml.contains(module.getServiceName()));
        } catch (IOException e) {
            warn(results, cat, "Could not read application.yml: " + e.getMessage());
        }
    }

    private void checkMainClass(Path svcDir, FractalModule module,
                                String cat, List<CheckResult> results) {
        Path srcJava = svcDir.resolve("src/main/java");
        if (!Files.isDirectory(srcJava)) {
            fail(results, cat, "src/main/java directory missing", "");
            return;
        }
        boolean found = containsText(srcJava, "@SpringBootApplication");
        pass(results, cat, "Main application class (@SpringBootApplication) generated", found);
    }

    // ── NetScope per-service checks ───────────────────────────────────────────

    private void checkNetScopePerService(Path svcDir, FractalModule module,
                                         List<FractalModule> allModules,
                                         String cat, List<CheckResult> results) {
        Path srcJava = svcDir.resolve("src/main/java");
        if (!Files.isDirectory(srcJava)) return;

        // If this module has outgoing dependencies → needs @NetScopeClient
        if (!module.getDependencies().isEmpty()) {
            boolean hasClient = containsText(srcJava, "@NetScopeClient");
            pass(results, cat,
                    "@NetScopeClient interface generated (deps: " + module.getDependencies() + ")",
                    hasClient);
        }

        // If other modules depend on this one → needs @NetworkPublic on exposed methods
        boolean isCalledByOthers = allModules.stream()
                .filter(m -> !m.getServiceName().equals(module.getServiceName()))
                .anyMatch(m -> m.getDependencies().stream()
                        .anyMatch(dep -> module.getServiceName().startsWith(
                                dep.toLowerCase().replace("service", "").trim())));

        if (isCalledByOthers) {
            boolean hasPublic = containsText(srcJava, "@NetworkPublic");
            pass(results, cat,
                    "@NetworkPublic annotation on exposed methods (called by others)",
                    hasPublic);
        }
    }

    // ── NetScope wiring checks (cross-service) ────────────────────────────────

    private void checkNetScopeWiring(Path out, List<FractalModule> modules,
                                     List<CheckResult> results) {
        for (FractalModule caller : modules) {
            if (caller.getDependencies().isEmpty()) continue;
            Path callerSrc = out.resolve(caller.getServiceName()).resolve("src/main/java");
            if (!Files.isDirectory(callerSrc)) continue;

            for (String dep : caller.getDependencies()) {
                // find the module whose class name matches the dependency field type
                modules.stream()
                        .filter(m -> !m.getServiceName().equals(caller.getServiceName()))
                        .filter(m -> dep.contains(m.getClassName() != null
                                ? m.getClassName() : m.getServiceName()))
                        .findFirst()
                        .ifPresent(target -> {
                            String cat = "NetScope: " + caller.getServiceName()
                                    + " → " + target.getServiceName();
                            boolean clientPresent = containsText(callerSrc, "@NetScopeClient");
                            pass(results, cat,
                                    "Client interface generated in caller service", clientPresent);

                            Path targetSrc = out.resolve(target.getServiceName())
                                    .resolve("src/main/java");
                            if (Files.isDirectory(targetSrc)) {
                                boolean serverPresent = containsText(targetSrc, "@NetworkPublic");
                                pass(results, cat,
                                        "@NetworkPublic present in target service", serverPresent);
                            }
                        });
            }
        }
    }

    // ── Docker checks ─────────────────────────────────────────────────────────

    private void checkDocker(Path out, List<FractalModule> modules, List<CheckResult> results) {
        Path compose = out.resolve("docker-compose.yml");
        if (!Files.exists(compose)) return; // already flagged in infrastructure

        try {
            String content = Files.readString(compose);
            for (FractalModule m : modules) {
                pass(results, "Docker",
                        m.getServiceName() + " referenced in docker-compose.yml",
                        content.contains(m.getServiceName()));
            }
            pass(results, "Docker", "Jaeger referenced in docker-compose.yml",
                    content.contains("jaeger"));
            pass(results, "Docker", "logger-service referenced in docker-compose.yml",
                    content.contains("logger-service"));
        } catch (IOException e) {
            warn(results, "Docker", "Could not read docker-compose.yml: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean containsText(Path dir, String text) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .anyMatch(p -> {
                        try { return Files.readString(p).contains(text); }
                        catch (IOException e) { return false; }
                    });
        } catch (IOException e) {
            log.debug("Could not walk {}: {}", dir, e.getMessage());
            return false;
        }
    }

    private void pass(List<CheckResult> r, String cat, String desc, boolean condition) {
        r.add(new CheckResult(cat, desc,
                condition ? Status.PASS : Status.FAIL,
                condition ? "" : "not found in generated output"));
    }

    private void warn(List<CheckResult> r, String cat, String detail) {
        r.add(new CheckResult(cat, detail, Status.WARN, detail));
    }

    private void fail(List<CheckResult> r, String cat, String desc, String detail) {
        r.add(new CheckResult(cat, desc, Status.FAIL, detail));
    }

    private int count(List<CheckResult> results, Status status) {
        return (int) results.stream().filter(r -> r.status() == status).count();
    }
}
