package org.fractalx.core.generator.service

import org.fractalx.core.config.FractalxConfig
import org.fractalx.core.generator.GenerationContext
import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that CorrelationIdGenerator produces logback-spring.xml
 * with the correct correlation ID log pattern.
 */
class CorrelationIdGeneratorSpec extends Specification {

    @TempDir
    Path serviceRoot

    CorrelationIdGenerator generator = new CorrelationIdGenerator()

    FractalModule module = FractalModule.builder()
        .serviceName("order-service")
        .packageName("org.fractalx.test.order")
        .port(8081)
        .dependencies([])
        .build()

    private GenerationContext ctx() {
        def resourcesDir = serviceRoot.resolve("src/main/resources")
        Files.createDirectories(resourcesDir)
        new GenerationContext(module, serviceRoot, serviceRoot, [], FractalxConfig.defaults(), [])
    }

    private String logback() {
        Files.readString(serviceRoot.resolve("src/main/resources/logback-spring.xml"))
    }

    def "logback-spring.xml is generated"() {
        when:
        generator.generate(ctx())

        then:
        Files.exists(serviceRoot.resolve("src/main/resources/logback-spring.xml"))
    }

    def "logback-spring.xml includes correlationId MDC key in log pattern"() {
        when:
        generator.generate(ctx())

        then:
        logback().contains("%X{correlationId")
    }

    def "logback-spring.xml includes CONSOLE and FILE appenders"() {
        when:
        generator.generate(ctx())

        then:
        def content = logback()
        content.contains("CONSOLE")
        content.contains("FILE")
    }

    def "logback-spring.xml includes the service name in file appender path"() {
        when:
        generator.generate(ctx())

        then:
        logback().contains("order-service")
    }

    def "generator is idempotent — skips if logback-spring.xml already exists"() {
        given: "resources dir already exists with a custom logback file"
        def context = ctx()  // creates resources dir as side effect
        def existing = serviceRoot.resolve("src/main/resources/logback-spring.xml")
        Files.writeString(existing, "<!-- custom -->")

        when:
        generator.generate(context)

        then: "existing file is preserved"
        Files.readString(existing) == "<!-- custom -->"
    }
}
