package org.fractalx.core.generator.observability

import org.fractalx.core.generator.GenerationContext
import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that HealthMetricsStep generates ServiceHealthConfig.java for services
 * that have cross-service dependencies, and skips generation entirely when there
 * are none.
 *
 * Generated package: org.fractalx.generated.<serviceNameLower>
 */
class HealthMetricsStepSpec extends Specification {

    @TempDir
    Path serviceRoot

    HealthMetricsStep step = new HealthMetricsStep()

    FractalModule order = FractalModule.builder()
        .serviceName("order-service")
        .packageName("com.example.order")
        .port(8081)
        .dependencies(["PaymentService"])
        .build()

    FractalModule payment = FractalModule.builder()
        .serviceName("payment-service")
        .packageName("com.example.payment")
        .port(8082)
        .build()

    FractalModule standalone = FractalModule.builder()
        .serviceName("standalone-service")
        .packageName("com.example.standalone")
        .port(8090)
        .build()

    private GenerationContext ctx(FractalModule m, List<FractalModule> all = [m]) {
        Files.createDirectories(serviceRoot.resolve("src/main/java"))
        Files.createDirectories(serviceRoot.resolve("src/main/resources"))
        new GenerationContext(m, serviceRoot, serviceRoot, all)
    }

    private Path healthConfigFile(FractalModule m) {
        // Package: org.fractalx.generated.<serviceNameLower>
        String svcId = m.serviceName.split("-").collect { it.capitalize() }.join("").toLowerCase()
        serviceRoot.resolve("src/main/java/org/fractalx/generated/${svcId}/ServiceHealthConfig.java")
    }

    private String content(FractalModule m) { Files.readString(healthConfigFile(m)) }

    def "ServiceHealthConfig.java is created for a service with dependencies"() {
        when:
        step.generate(ctx(order, [order, payment]))

        then:
        Files.exists(healthConfigFile(order))
    }

    def "ServiceHealthConfig.java is NOT created for a service with no dependencies"() {
        when:
        step.generate(ctx(standalone))

        then:
        !Files.exists(healthConfigFile(standalone))
    }

    def "ServiceHealthConfig is a @Configuration class in the generated package"() {
        when:
        step.generate(ctx(order, [order, payment]))

        then:
        def c = content(order)
        c.contains("package org.fractalx.generated.orderservice")
        c.contains("@Configuration")
        c.contains("public class ServiceHealthConfig")
    }

    def "ServiceHealthConfig declares a @Bean HealthIndicator for each dependency"() {
        when:
        step.generate(ctx(order, [order, payment]))

        then:
        def c = content(order)
        c.contains("@Bean")
        c.contains("HealthIndicator")
        c.contains("payment-service")
    }

    def "HealthIndicator uses TCP connect to the dependency gRPC port (HTTP + 10000)"() {
        when:
        step.generate(ctx(order, [order, payment]))

        then:
        def c = content(order)
        // gRPC port = 8082 + 10000 = 18082
        c.contains("18082")
        c.contains("Socket")
    }

    def "HealthIndicator reports Health.up on successful connect"() {
        when:
        step.generate(ctx(order, [order, payment]))

        then:
        content(order).contains("Health.up()")
    }

    def "HealthIndicator reports Health.down on failure"() {
        when:
        step.generate(ctx(order, [order, payment]))

        then:
        content(order).contains("Health.down()")
    }

    def "ServiceHealthConfig registers a Micrometer gauge for dependency health"() {
        when:
        step.generate(ctx(order, [order, payment]))

        then:
        def c = content(order)
        (c.contains("MeterRegistry") || c.contains("MeterBinder") || c.contains("Gauge"))
        (c.contains("fractalx.service.dependency.up") || c.contains("dependency"))
    }
}
