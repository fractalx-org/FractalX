package org.fractalx.core.generator.admin

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that AdminIncidentGenerator produces all 3 required classes
 * in the org.fractalx.admin.incidents package with the expected structure
 * and REST endpoint mappings.
 */
class AdminIncidentGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    AdminIncidentGenerator generator = new AdminIncidentGenerator()
    String basePackage = "org.fractalx.admin"

    private Path incidentsPkg() {
        srcMainJava.resolve("org/fractalx/admin/incidents")
    }

    private String read(String name) {
        Files.readString(incidentsPkg().resolve(name))
    }

    def "all 3 incident files are created"() {
        when:
        generator.generate(srcMainJava, basePackage)

        then:
        ["Incident.java", "IncidentStore.java", "IncidentController.java"].every {
            Files.exists(incidentsPkg().resolve(it))
        }
    }

    def "Incident is a record with severity and status fields"() {
        when:
        generator.generate(srcMainJava, basePackage)

        then:
        def c = read("Incident.java")
        c.contains("package org.fractalx.admin.incidents")
        c.contains("record Incident")
        c.contains("severity")
        c.contains("status")
        c.contains("createdAt")
        c.contains("resolvedAt")
    }

    def "Incident record has withStatus and withUpdate transition methods"() {
        when:
        generator.generate(srcMainJava, basePackage)

        then:
        def c = read("Incident.java")
        c.contains("withStatus")
        c.contains("withUpdate")
    }

    def "IncidentStore is a @Component with ConcurrentHashMap storage"() {
        when:
        generator.generate(srcMainJava, basePackage)

        then:
        def c = read("IncidentStore.java")
        c.contains("@Component")
        c.contains("ConcurrentHashMap")
    }

    def "IncidentStore provides getAll, getOpen, findById, save, delete, and stats methods"() {
        when:
        generator.generate(srcMainJava, basePackage)

        then:
        def c = read("IncidentStore.java")
        c.contains("getAll")
        c.contains("getOpen")
        c.contains("findById")
        c.contains("save(")
        c.contains("delete(")
        c.contains("stats(")
    }

    def "IncidentStore starts empty"() {
        when:
        generator.generate(srcMainJava, basePackage)

        then:
        def c = read("IncidentStore.java")
        c.contains("ConcurrentHashMap")
        !c.contains("UUID.randomUUID")
        c.contains("public IncidentStore() {}")
    }

    def "IncidentController is a @RestController mapped to /api/incidents"() {
        when:
        generator.generate(srcMainJava, basePackage)

        then:
        def c = read("IncidentController.java")
        c.contains("@RestController")
        c.contains("/api/incidents")
    }

    def "IncidentController exposes GET /api/incidents and GET /api/incidents/open"() {
        when:
        generator.generate(srcMainJava, basePackage)

        then:
        def c = read("IncidentController.java")
        c.contains("getAll")
        c.contains("/open")
        c.contains("getOpen")
    }

    def "IncidentController exposes GET /api/incidents/stats"() {
        when:
        generator.generate(srcMainJava, basePackage)

        then:
        def c = read("IncidentController.java")
        c.contains("/stats")
        c.contains("stats(")
    }

    def "IncidentController exposes POST to create incidents"() {
        when:
        generator.generate(srcMainJava, basePackage)

        then:
        def c = read("IncidentController.java")
        c.contains("@PostMapping")
        c.contains("create(")
        c.contains("UUID.randomUUID")
    }

    def "IncidentController exposes PUT /{id}/status to transition incident state"() {
        when:
        generator.generate(srcMainJava, basePackage)

        then:
        def c = read("IncidentController.java")
        c.contains("/status")
        c.contains("updateStatus")
        c.contains("withStatus")
    }

    def "IncidentController exposes DELETE /{id} to remove incidents"() {
        when:
        generator.generate(srcMainJava, basePackage)

        then:
        def c = read("IncidentController.java")
        c.contains("@DeleteMapping")
        c.contains("delete(")
        c.contains("ResponseEntity.notFound")
    }

    def "IncidentController defaults severity to P3 when not provided"() {
        when:
        generator.generate(srcMainJava, basePackage)

        then:
        def c = read("IncidentController.java")
        c.contains("P3")
    }
}
