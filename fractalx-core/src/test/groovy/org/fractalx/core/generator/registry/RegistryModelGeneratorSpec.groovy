package org.fractalx.core.generator.registry

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that RegistryModelGenerator produces a correct ServiceRegistration
 * model class with all required fields, accessors, and a getBaseUrl() helper.
 */
class RegistryModelGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    RegistryModelGenerator generator = new RegistryModelGenerator()

    private Path modelFile() {
        srcMainJava.resolve("org/fractalx/registry/model/ServiceRegistration.java")
    }

    private String content() { Files.readString(modelFile()) }

    def "ServiceRegistration.java is created at the correct path"() {
        when:
        generator.generate(srcMainJava)

        then:
        Files.exists(modelFile())
    }

    def "model is in the org.fractalx.registry.model package"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().startsWith("package org.fractalx.registry.model;")
    }

    def "model declares all required fields for service registration"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = content()
        c.contains("String name")
        c.contains("String host")
        c.contains("int port")
        c.contains("int grpcPort")
        c.contains("String healthUrl")
        c.contains("String status")
        c.contains("Instant lastSeen")
        c.contains("Instant registeredAt")
    }

    def "model default constructor initialises status to UNKNOWN"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().contains('"UNKNOWN"')
    }

    def "model provides a convenience constructor accepting all core fields"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().contains("ServiceRegistration(String name, String host, int port, int grpcPort, String healthUrl)")
    }

    def "model provides getters and setters for all fields"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = content()
        ["getName", "getHost", "getPort", "getGrpcPort", "getHealthUrl",
         "getStatus", "getLastSeen", "getRegisteredAt",
         "setName", "setHost", "setPort", "setGrpcPort", "setHealthUrl",
         "setStatus", "setLastSeen", "setRegisteredAt"].every { c.contains(it) }
    }

    def "model provides a getBaseUrl() method returning http://host:port"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = content()
        c.contains("getBaseUrl()")
        c.contains("http://")
    }

    def "name and host fields are annotated with @NotBlank so empty probe requests return HTTP 400"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = content()
        c.contains("import jakarta.validation.constraints.NotBlank")
        c.contains("@NotBlank")
    }
}
