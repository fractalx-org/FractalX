package org.fractalx.core.datamanagement

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that DataIsolationGenerator generates a valid IsolationConfig.java
 * that restricts JPA scanning to both the generated package and the original
 * monolith source package (dual-package scan).
 */
class DataIsolationGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    DataIsolationGenerator generator = new DataIsolationGenerator()

    FractalModule module = FractalModule.builder()
        .serviceName("order-service")
        .packageName("org.fractalx.testapp.order")
        .port(8081)
        .build()

    // ── helpers ──────────────────────────────────────────────────────────────

    private String isolationConfigContent() {
        // generated under org/fractalx/generated/orderservice/config/IsolationConfig.java
        def configPath = srcMainJava
            .resolve("org/fractalx/generated/orderservice/config/IsolationConfig.java")
        Files.readString(configPath)
    }

    // ── feature methods ───────────────────────────────────────────────────────

    def "IsolationConfig.java is created at the correct path under the generated package"() {
        when:
        generator.generateIsolationConfig(module, srcMainJava, "org.fractalx.generated.orderservice")

        then:
        def expected = srcMainJava
            .resolve("org/fractalx/generated/orderservice/config/IsolationConfig.java")
        Files.exists(expected)
    }

    def "IsolationConfig contains @EnableJpaRepositories with both generated and source packages"() {
        when:
        generator.generateIsolationConfig(module, srcMainJava, "org.fractalx.generated.orderservice")

        then:
        def content = isolationConfigContent()
        content.contains("@EnableJpaRepositories")
        content.contains("org.fractalx.generated.orderservice")  // generated package
        content.contains("org.fractalx.testapp.order")            // original source package
    }

    def "IsolationConfig contains @EntityScan with both generated and source packages"() {
        when:
        generator.generateIsolationConfig(module, srcMainJava, "org.fractalx.generated.orderservice")

        then:
        def content = isolationConfigContent()
        content.contains("@EntityScan")
        content.contains("org.fractalx.generated.orderservice")
        content.contains("org.fractalx.testapp.order")
    }

    def "service name hyphens are stripped when building the generated package name"() {
        given: "service name with multiple hyphens"
        def hyphenated = FractalModule.builder()
            .serviceName("my-complex-service")
            .packageName("com.example.myservice")
            .port(8082)
            .build()

        when:
        generator.generateIsolationConfig(hyphenated, srcMainJava, "org.fractalx.generated.mycomplexservice")

        then:
        def config = srcMainJava
            .resolve("org/fractalx/generated/mycomplexservice/config/IsolationConfig.java")
        Files.exists(config)
        Files.readString(config).contains("org.fractalx.generated.mycomplexservice")
    }

    def "IsolationConfig is annotated with @Configuration"() {
        when:
        generator.generateIsolationConfig(module, srcMainJava, "org.fractalx.generated.orderservice")

        then:
        isolationConfigContent().contains("@Configuration")
    }
}
