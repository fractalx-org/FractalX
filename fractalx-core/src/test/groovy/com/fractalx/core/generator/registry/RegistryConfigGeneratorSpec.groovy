package com.fractalx.core.generator.registry

import com.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that RegistryConfigGenerator produces a valid application.yml for
 * the fractalx-registry service containing correct port, pre-seeded known
 * services, and actuator exposure config.
 */
class RegistryConfigGeneratorSpec extends Specification {

    @TempDir
    Path resourcesDir

    RegistryConfigGenerator generator = new RegistryConfigGenerator()

    FractalModule orderModule = FractalModule.builder()
        .serviceName("order-service")
        .packageName("com.example.order")
        .port(8081)
        .build()

    FractalModule paymentModule = FractalModule.builder()
        .serviceName("payment-service")
        .packageName("com.example.payment")
        .port(8082)
        .build()

    private String yml() { Files.readString(resourcesDir.resolve("application.yml")) }

    def "application.yml is created in the resources directory"() {
        when:
        generator.generate(resourcesDir, [orderModule])

        then:
        Files.exists(resourcesDir.resolve("application.yml"))
    }

    def "application.yml binds the registry to port 8761"() {
        when:
        generator.generate(resourcesDir, [orderModule])

        then:
        yml().contains("port: 8761")
    }

    def "application.yml sets spring.application.name to fractalx-registry"() {
        when:
        generator.generate(resourcesDir, [orderModule])

        then:
        yml().contains("name: fractalx-registry")
    }

    def "application.yml pre-seeds each provided service under known-services"() {
        when:
        generator.generate(resourcesDir, [orderModule, paymentModule])

        then:
        def content = yml()
        content.contains("order-service")
        content.contains("payment-service")
    }

    def "application.yml encodes each service's HTTP port correctly"() {
        when:
        generator.generate(resourcesDir, [orderModule, paymentModule])

        then:
        def content = yml()
        content.contains("port: 8081")
        content.contains("port: 8082")
    }

    def "application.yml encodes each service's gRPC port as HTTP port plus 10000"() {
        when:
        generator.generate(resourcesDir, [orderModule, paymentModule])

        then:
        def content = yml()
        content.contains("grpcPort: 18081")
        content.contains("grpcPort: 18082")
    }

    def "application.yml exposes actuator health and info endpoints"() {
        when:
        generator.generate(resourcesDir, [orderModule])

        then:
        yml().contains("health")
        yml().contains("info")
    }

    def "application.yml sets the evict-after-ms and poll-interval-ms parameters"() {
        when:
        generator.generate(resourcesDir, [orderModule])

        then:
        yml().contains("evict-after-ms")
        yml().contains("poll-interval-ms")
    }

    def "each service healthUrl points to the service's actuator health endpoint"() {
        when:
        generator.generate(resourcesDir, [orderModule])

        then:
        yml().contains("actuator/health")
    }
}
