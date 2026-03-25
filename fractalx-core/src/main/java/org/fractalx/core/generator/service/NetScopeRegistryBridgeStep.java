package org.fractalx.core.generator.service;

import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Pipeline step that generates a NetScopeRegistryBridge in each service that has
 * gRPC dependencies. On startup it queries fractalx-registry for each peer's current
 * host and dynamically overrides the static YAML netscope client config, making
 * host resolution container-ready while preserving static YAML as a dev fallback.
 */
public class NetScopeRegistryBridgeStep implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(NetScopeRegistryBridgeStep.class);

    @Override
    public void generate(GenerationContext context) throws IOException {
        FractalModule module = context.getModule();
        if (module.getDependencies().isEmpty()) return;

        log.debug("Generating NetScope registry bridge for {}", module.getServiceName());

        String generatedPkg = context.servicePackage();
        Path pkgPath = resolvePackage(context.getSrcMainJava(), generatedPkg);

        generateBridge(pkgPath, generatedPkg, module.getDependencies());
        log.debug("Generated NetScopeRegistryBridge for {}", module.getServiceName());
    }

    private void generateBridge(Path pkgPath, String pkg, List<String> dependencies) throws IOException {
        StringBuilder peerLoops = new StringBuilder();
        for (String dep : dependencies) {
            peerLoops.append("""
                            resolveAndUpdate("%s");
                    """.formatted(beanTypeToServiceName(dep)));
        }

        String content = """
                package %s;

                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
                import org.springframework.core.env.ConfigurableEnvironment;
                import org.springframework.core.env.MapPropertySource;
                import org.springframework.stereotype.Component;
                import org.springframework.web.client.RestTemplate;

                import jakarta.annotation.PostConstruct;
                import java.util.HashMap;
                import java.util.Map;

                @Component
                @ConditionalOnProperty(name = "fractalx.registry.enabled", havingValue = "true", matchIfMissing = true)
                public class NetScopeRegistryBridge {

                    private static final Logger log = LoggerFactory.getLogger(NetScopeRegistryBridge.class);

                    private final ConfigurableEnvironment environment;
                    private final RestTemplate restTemplate = new RestTemplate();

                    @Value("${fractalx.registry.url:http://localhost:8761}")
                    private String registryUrl;

                    public NetScopeRegistryBridge(ConfigurableEnvironment environment) {
                        this.environment = environment;
                    }

                    @PostConstruct
                    public void resolvePeers() {
                        %s
                    }

                    private void resolveAndUpdate(String serviceName) {
                        for (int attempt = 1; attempt <= 5; attempt++) {
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> reg = restTemplate.getForObject(
                                        registryUrl + "/services/" + serviceName, Map.class);
                                if (reg != null && reg.containsKey("host")) {
                                    String host = (String) reg.get("host");
                                    int grpcPort = ((Number) reg.get("grpcPort")).intValue();
                                    Map<String, Object> props = new HashMap<>();
                                    props.put("netscope.client.servers." + serviceName + ".host", host);
                                    props.put("netscope.client.servers." + serviceName + ".port", grpcPort);
                                    environment.getPropertySources().addFirst(
                                            new MapPropertySource("fractalx-registry-" + serviceName, props));
                                    log.info("Resolved {} -> {}:{} via registry", serviceName, host, grpcPort);
                                    return;
                                }
                            } catch (Exception e) {
                                log.warn("Registry lookup for {} failed (attempt {}/5): {}",
                                        serviceName, attempt, e.getMessage());
                            }
                            try { Thread.sleep(2000L * attempt); }
                            catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                        }
                        log.warn("Could not resolve {} from registry — using static YAML fallback", serviceName);
                    }
                }
                """.formatted(pkg, peerLoops.toString());

        Files.writeString(pkgPath.resolve("NetScopeRegistryBridge.java"), content);
    }

    private Path resolvePackage(Path base, String pkg) throws IOException {
        Path p = base;
        for (String part : pkg.split("\\.")) p = p.resolve(part);
        Files.createDirectories(p);
        return p;
    }

    private String toJavaId(String serviceName) {
        StringBuilder sb = new StringBuilder();
        for (String part : serviceName.split("-")) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String beanTypeToServiceName(String beanType) {
        String name = beanType.replaceAll("(?i)(Service|Client)$", "");
        String kebab = name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
        return kebab + "-service";
    }
}
