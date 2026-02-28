package org.fractalx.core.generator.registry

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that RegistryApplicationGenerator creates a valid Spring Boot
 * application class for the fractalx-registry service annotated with
 * @SpringBootApplication and @EnableScheduling.
 */
class RegistryApplicationGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    RegistryApplicationGenerator generator = new RegistryApplicationGenerator()

    private Path appFile() {
        srcMainJava.resolve("org/fractalx/registry/FractalRegistryApplication.java")
    }

    private String appContent() { Files.readString(appFile()) }

    def "FractalRegistryApplication.java is created at the correct path"() {
        when:
        generator.generate(srcMainJava)

        then:
        Files.exists(appFile())
    }

    def "application class is in the org.fractalx.registry package"() {
        when:
        generator.generate(srcMainJava)

        then:
        appContent().startsWith("package org.fractalx.registry;")
    }

    def "application class is annotated with @SpringBootApplication"() {
        when:
        generator.generate(srcMainJava)

        then:
        appContent().contains("@SpringBootApplication")
    }

    def "application class is annotated with @EnableScheduling for health polling"() {
        when:
        generator.generate(srcMainJava)

        then:
        appContent().contains("@EnableScheduling")
    }

    def "application class contains a standard SpringApplication.run main method"() {
        when:
        generator.generate(srcMainJava)

        then:
        def content = appContent()
        content.contains("public static void main(String[] args)")
        content.contains("SpringApplication.run(FractalRegistryApplication.class, args)")
    }

    def "application class is named FractalRegistryApplication"() {
        when:
        generator.generate(srcMainJava)

        then:
        appContent().contains("class FractalRegistryApplication")
    }
}
