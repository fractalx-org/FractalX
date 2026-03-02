package org.fractalx.core.generator.resilience

import org.fractalx.core.config.FractalxConfig
import org.fractalx.core.generator.GenerationContext
import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that ResilienceConfigStep generates a NetScopeResilienceConfig
 * @Configuration class and appends correct Resilience4j YAML blocks for
 * each cross-module dependency (circuit breaker, retry, time limiter).
 */
class ResilienceConfigStepSpec extends Specification {

    @TempDir
    Path serviceRoot

    ResilienceConfigStep step = new ResilienceConfigStep()

    FractalModule moduleWithDeps = FractalModule.builder()
        .serviceName("order-service")
        .packageName("com.example.order")
        .port(8081)
        .dependencies(["PaymentService", "InventoryService"])
        .build()

    FractalModule moduleNoDeps = FractalModule.builder()
        .serviceName("payment-service")
        .packageName("com.example.payment")
        .port(8082)
        .build()

    private GenerationContext ctx(FractalModule m) {
        def resourcesDir = serviceRoot.resolve("src/main/resources")
        Files.createDirectories(resourcesDir)
        // Write a base application.yml so the step can append to it
        Files.writeString(resourcesDir.resolve("application.yml"), "server:\n  port: 8081\n")
        new GenerationContext(m, serviceRoot, serviceRoot, [m], FractalxConfig.defaults(), [])
    }

    private Path configFile() {
        serviceRoot.resolve("src/main/java/org/fractalx/generated/orderservice/NetScopeResilienceConfig.java")
    }

    private String javaContent()  { Files.readString(configFile()) }
    private String yamlContent()  { Files.readString(serviceRoot.resolve("src/main/resources/application.yml")) }

    def "NetScopeResilienceConfig.java is created for a service with dependencies"() {
        when:
        step.generate(ctx(moduleWithDeps))

        then:
        Files.exists(configFile())
    }

    def "NetScopeResilienceConfig.java is NOT created for a service without dependencies"() {
        when:
        step.generate(ctx(moduleNoDeps))

        then:
        !Files.exists(serviceRoot.resolve(
            "src/main/java/org/fractalx/generated/paymentservice/NetScopeResilienceConfig.java"))
    }

    def "config class is a @Configuration in the correct package"() {
        when:
        step.generate(ctx(moduleWithDeps))

        then:
        def c = javaContent()
        c.startsWith("package org.fractalx.generated.orderservice;")
        c.contains("@Configuration")
    }

    def "config class defines a @Bean CircuitBreaker for each dependency"() {
        when:
        step.generate(ctx(moduleWithDeps))

        then:
        def c = javaContent()
        c.contains("payment-service")
        c.contains("inventory-service")
        c.contains("CircuitBreaker")
        c.contains("@Bean")
    }

    def "Resilience4j YAML blocks are appended to application.yml"() {
        when:
        step.generate(ctx(moduleWithDeps))

        then:
        yamlContent().contains("resilience4j:")
    }

    def "circuit breaker YAML block is generated for each dependency"() {
        when:
        step.generate(ctx(moduleWithDeps))

        then:
        def yml = yamlContent()
        yml.contains("circuitbreaker:")
        yml.contains("payment-service:")
        yml.contains("inventory-service:")
        yml.contains("failure-rate-threshold: 50")
        yml.contains("wait-duration-in-open-state: 30s")
        yml.contains("sliding-window-size: 10")
    }

    def "retry YAML block is generated for each dependency"() {
        when:
        step.generate(ctx(moduleWithDeps))

        then:
        def yml = yamlContent()
        yml.contains("retry:")
        yml.contains("max-attempts: 3")
        yml.contains("wait-duration: 100ms")
        yml.contains("exponential-backoff-multiplier: 2")
    }

    def "time limiter YAML block is generated for each dependency"() {
        when:
        step.generate(ctx(moduleWithDeps))

        then:
        def yml = yamlContent()
        yml.contains("timelimiter:")
        yml.contains("timeout-duration: 2s")
    }

    def "pre-existing application.yml content is preserved when appending"() {
        when:
        step.generate(ctx(moduleWithDeps))

        then:
        yamlContent().contains("server:")
        yamlContent().contains("port: 8081")
    }

    def "no YAML is appended when the service has no dependencies"() {
        when:
        step.generate(ctx(moduleNoDeps))

        then:
        !yamlContent().contains("resilience4j:")
    }
}
