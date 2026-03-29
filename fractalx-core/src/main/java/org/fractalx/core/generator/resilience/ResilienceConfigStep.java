package org.fractalx.core.generator.resilience;

import org.fractalx.core.config.FractalxConfig;
import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.fractalx.core.generator.service.NetScopeClientGenerator;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Pipeline step that generates Resilience4j configuration for each service.
 * Produces a {@code NetScopeResilienceConfig} @Configuration class and appends
 * resilience4j YAML blocks to the service's application.yml.
 *
 * <p>Each cross-service dependency gets:
 * <ul>
 *   <li>CircuitBreaker — 50% failure threshold, 30s wait, 5 calls in HALF_OPEN</li>
 *   <li>Retry — 3 attempts with exponential backoff (100ms base)</li>
 *   <li>TimeLimiter — 2s timeout per call</li>
 * </ul>
 */
public class ResilienceConfigStep implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfigStep.class);

    @Override
    public void generate(GenerationContext context) throws IOException {
        if (!context.getFractalxConfig().features().resilience()) return;
        FractalModule module = context.getModule();
        if (module.getDependencies().isEmpty()) {
            log.debug("Skipping resilience config for {} — no cross-service dependencies", module.getServiceName());
            return;
        }
        log.debug("Generating resilience config for {}", module.getServiceName());

        String generatedPkg = context.servicePackage();
        Path pkgPath = resolvePackage(context.getSrcMainJava(), generatedPkg);

        FractalxConfig.ResilienceDefaults r = context.getFractalxConfig().resilience();
        generateResilienceConfig(pkgPath, generatedPkg, module.getDependencies());
        appendResilienceYaml(context.getSrcMainResources(), module.getDependencies(), r);

        log.debug("Generated resilience config for {}", module.getServiceName());
    }

    private void generateResilienceConfig(Path pkgPath, String pkg, List<String> dependencies) throws IOException {
        StringBuilder cbBeans = new StringBuilder();
        for (String dep : dependencies) {
            String svcName  = NetScopeClientGenerator.beanTypeToServiceName(dep);
            String beanName = toCamelCase(svcName);
            cbBeans.append("""

                        @Bean
                        public CircuitBreaker %sCb(CircuitBreakerRegistry registry) {
                            return registry.circuitBreaker("%s");
                        }
                    """.formatted(beanName, svcName));
        }

        String content = """
                package %s;

                import io.github.resilience4j.circuitbreaker.CircuitBreaker;
                import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                @Configuration
                public class NetScopeResilienceConfig {
                    %s
                }
                """.formatted(pkg, cbBeans.toString());

        Files.writeString(pkgPath.resolve("NetScopeResilienceConfig.java"), content);
    }

    private void appendResilienceYaml(Path resourcesPath, List<String> dependencies,
                                       FractalxConfig.ResilienceDefaults r) throws IOException {
        if (dependencies.isEmpty()) return;

        StringBuilder yaml = new StringBuilder("\nresilience4j:\n");
        yaml.append("  circuitbreaker:\n    instances:\n");
        for (String dep : dependencies) {
            String svc = NetScopeClientGenerator.beanTypeToServiceName(dep);
            yaml.append("      ").append(svc).append(":\n");
            yaml.append("        failure-rate-threshold: ").append(r.failureRateThreshold()).append("\n");
            yaml.append("        wait-duration-in-open-state: ").append(r.waitDurationInOpenState()).append("\n");
            yaml.append("        permitted-number-of-calls-in-half-open-state: ").append(r.permittedCallsInHalfOpenState()).append("\n");
            yaml.append("        sliding-window-size: ").append(r.slidingWindowSize()).append("\n");
            yaml.append("        register-health-indicator: true\n");
        }
        yaml.append("  retry:\n    instances:\n");
        for (String dep : dependencies) {
            String svc = NetScopeClientGenerator.beanTypeToServiceName(dep);
            yaml.append("      ").append(svc).append(":\n");
            yaml.append("        max-attempts: ").append(r.retryMaxAttempts()).append("\n");
            yaml.append("        wait-duration: ").append(r.retryWaitDuration()).append("\n");
            yaml.append("        exponential-backoff-multiplier: 2\n");
        }
        yaml.append("  timelimiter:\n    instances:\n");
        for (String dep : dependencies) {
            String svc = NetScopeClientGenerator.beanTypeToServiceName(dep);
            yaml.append("      ").append(svc).append(":\n");
            yaml.append("        timeout-duration: ").append(r.timeoutDuration()).append("\n");
        }

        Path ymlPath = resourcesPath.resolve("application.yml");
        if (Files.exists(ymlPath)) {
            Files.writeString(ymlPath, Files.readString(ymlPath) + yaml);
        } else {
            Files.writeString(ymlPath, yaml.toString());
        }
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

    private String toCamelCase(String name) {
        String[] parts = name.split("-");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty())
                sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

}
