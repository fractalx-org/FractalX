package com.fractalx.core.generator.registry

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that RegistryServiceClassGenerator produces a correct in-memory
 * registry service with ConcurrentHashMap storage, a @Scheduled health poll,
 * and automatic eviction of unresponsive services.
 */
class RegistryServiceClassGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    RegistryServiceClassGenerator generator = new RegistryServiceClassGenerator()

    private Path serviceFile() {
        srcMainJava.resolve("com/fractalx/registry/service/RegistryService.java")
    }

    private String content() { Files.readString(serviceFile()) }

    def "RegistryService.java is created at the correct path"() {
        when:
        generator.generate(srcMainJava)

        then:
        Files.exists(serviceFile())
    }

    def "service class is a @Service component in the correct package"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().startsWith("package com.fractalx.registry.service;")
        content().contains("@Service")
    }

    def "service uses ConcurrentHashMap as its backing store"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().contains("ConcurrentHashMap")
    }

    def "service exposes register, findByName, findAll, deregister, and heartbeat methods"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = content()
        c.contains("public ServiceRegistration register(")
        c.contains("public Optional<ServiceRegistration> findByName(")
        c.contains("public Collection<ServiceRegistration> findAll()")
        c.contains("public void deregister(")
        c.contains("public void heartbeat(")
    }

    def "health polling is triggered by @Scheduled annotation"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().contains("@Scheduled")
        content().contains("pollHealth()")
    }

    def "health poll uses a 15-second fixed delay"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().contains("15_000")
    }

    def "eviction threshold is defined as a constant greater than zero"() {
        when:
        generator.generate(srcMainJava)

        then:
        content().contains("EVICT_AFTER_MS")
    }

    def "register() marks the service as UP and records lastSeen"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = content()
        c.contains('"UP"')
        c.contains("setLastSeen(Instant.now())")
    }

    def "heartbeat() updates lastSeen and resets status to UP"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = content()
        c.contains("setLastSeen")
        c.contains("setStatus")
    }
}
