package com.fractalx.core.generator.registry

import com.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that RegistryServiceGenerator orchestrates all sub-generators to
 * produce a complete, runnable fractalx-registry Spring Boot project.
 */
class RegistryServiceGeneratorSpec extends Specification {

    @TempDir
    Path outputRoot

    RegistryServiceGenerator generator = new RegistryServiceGenerator()

    FractalModule order = FractalModule.builder()
        .serviceName("order-service")
        .packageName("com.example.order")
        .port(8081)
        .build()

    FractalModule payment = FractalModule.builder()
        .serviceName("payment-service")
        .packageName("com.example.payment")
        .port(8082)
        .build()

    private Path registryRoot() { outputRoot.resolve("fractalx-registry") }

    def "generator creates the fractalx-registry directory"() {
        when:
        generator.generate([order, payment], outputRoot)

        then:
        Files.isDirectory(registryRoot())
    }

    def "generator creates src/main/java and src/main/resources directories"() {
        when:
        generator.generate([order], outputRoot)

        then:
        Files.isDirectory(registryRoot().resolve("src/main/java"))
        Files.isDirectory(registryRoot().resolve("src/main/resources"))
    }

    def "generator produces a pom.xml at the registry root"() {
        when:
        generator.generate([order], outputRoot)

        then:
        Files.exists(registryRoot().resolve("pom.xml"))
    }

    def "generator produces the FractalRegistryApplication main class"() {
        when:
        generator.generate([order], outputRoot)

        then:
        Files.exists(registryRoot().resolve(
            "src/main/java/com/fractalx/registry/FractalRegistryApplication.java"))
    }

    def "generator produces application.yml"() {
        when:
        generator.generate([order], outputRoot)

        then:
        Files.exists(registryRoot().resolve("src/main/resources/application.yml"))
    }

    def "generator produces the ServiceRegistration model class"() {
        when:
        generator.generate([order], outputRoot)

        then:
        Files.exists(registryRoot().resolve(
            "src/main/java/com/fractalx/registry/model/ServiceRegistration.java"))
    }

    def "generator produces the RegistryService class"() {
        when:
        generator.generate([order], outputRoot)

        then:
        Files.exists(registryRoot().resolve(
            "src/main/java/com/fractalx/registry/service/RegistryService.java"))
    }

    def "generator produces the RegistryController class"() {
        when:
        generator.generate([order], outputRoot)

        then:
        Files.exists(registryRoot().resolve(
            "src/main/java/com/fractalx/registry/controller/RegistryController.java"))
    }

    def "REGISTRY_PORT constant is 8761"() {
        expect:
        RegistryServiceGenerator.REGISTRY_PORT == 8761
    }

    def "REGISTRY_DIR constant is fractalx-registry"() {
        expect:
        RegistryServiceGenerator.REGISTRY_DIR == "fractalx-registry"
    }

    def "generated registry application.yml contains all provided service names"() {
        when:
        generator.generate([order, payment], outputRoot)

        then:
        def yml = Files.readString(registryRoot().resolve("src/main/resources/application.yml"))
        yml.contains("order-service")
        yml.contains("payment-service")
    }
}
