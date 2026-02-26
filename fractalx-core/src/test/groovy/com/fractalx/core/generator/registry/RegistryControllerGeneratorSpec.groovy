package com.fractalx.core.generator.registry

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that RegistryControllerGenerator produces a correct REST controller
 * exposing the full service registration CRUD API including heartbeat and
 * health summary endpoints.
 */
class RegistryControllerGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    RegistryControllerGenerator generator = new RegistryControllerGenerator()

    private Path controllerFile() {
        srcMainJava.resolve("com/fractalx/registry/controller/RegistryController.java")
    }

    private String content() { Files.readString(controllerFile()) }

    def "RegistryController.java is created at the correct path"() {
        when:
        generator.generate(srcMainJava)

        then:
        Files.exists(controllerFile())
    }

    def "controller is in the com.fractalx.registry.controller package"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().startsWith("package com.fractalx.registry.controller;")
    }

    def "controller is annotated with @RestController and maps to /services"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().contains("@RestController")
        content().contains("/services")
    }

    def "controller exposes a POST /services registration endpoint"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().contains("@PostMapping")
        content().contains("register(")
        content().contains("@RequestBody")
    }

    def "controller exposes a GET /services list endpoint"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().contains("@GetMapping")
        content().contains("getAll()")
    }

    def "controller exposes a GET /services/{name} lookup endpoint"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().contains("@PathVariable")
        content().contains("getByName(")
        content().contains("notFound()")
    }

    def "controller exposes a DELETE /services/{name}/deregister endpoint"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().contains("@DeleteMapping")
        content().contains("deregister(")
        content().contains("noContent()")
    }

    def "controller exposes a POST /services/{name}/heartbeat endpoint"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().contains("heartbeat(")
    }

    def "controller exposes a GET /services/health summary endpoint"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().contains("health()")
        content().contains("registeredServices")
        content().contains("upServices")
    }

    def "controller delegates to RegistryService via constructor injection"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().contains("RegistryService registryService")
        content().contains("this.registryService = registryService")
    }
}
