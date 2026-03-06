package org.fractalx.core.generator.service

import org.fractalx.core.FractalxVersion
import org.fractalx.core.config.FractalxConfig
import org.fractalx.core.generator.GenerationContext
import org.fractalx.core.model.FractalModule
import org.fractalx.core.observability.ObservabilityInjector
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class PomGeneratorSpec extends Specification {

    @TempDir
    Path outputRoot

    ObservabilityInjector observabilityInjector = new ObservabilityInjector()
    PomGenerator generator = new PomGenerator(observabilityInjector)

    FractalModule module = FractalModule.builder()
        .serviceName("order-service")
        .packageName("com.example.order")
        .port(8081)
        .build()

    private String pom() {
        def serviceRoot = outputRoot.resolve("order-service")
        Files.createDirectories(serviceRoot)
        def ctx = new GenerationContext(module, outputRoot, serviceRoot,
                [module], FractalxConfig.defaults(), [])
        generator.generate(ctx)
        Files.readString(serviceRoot.resolve("pom.xml"))
    }

    def "generated pom uses the current fractalx-runtime version"() {
        when:
        def content = pom()

        then:
        def runtimeBlock = content.find(
            /(?s)<artifactId>fractalx-runtime<\/artifactId>\s*<version>([^<]+)<\/version>/) { _, v -> v }
        runtimeBlock == FractalxVersion.get()
    }

    def "netscope-server version is 1.0.1"() {
        when:
        def content = pom()

        then:
        def netscopeServerBlock = content.find(
            /(?s)<artifactId>netscope-server<\/artifactId>\s*<version>([^<]+)<\/version>/) { _, v -> v }
        netscopeServerBlock == "1.0.1"
    }

    def "netscope-client version is 1.0.1"() {
        when:
        def content = pom()

        then:
        def netscopeClientBlock = content.find(
            /(?s)<artifactId>netscope-client<\/artifactId>\s*<version>([^<]+)<\/version>/) { _, v -> v }
        netscopeClientBlock == "1.0.1"
    }

    def "generated pom sets service artifactId"() {
        when:
        def content = pom()

        then:
        content.contains("<artifactId>order-service</artifactId>")
    }

    def "generated pom includes spring-boot-starter-web"() {
        when:
        def content = pom()

        then:
        content.contains("<artifactId>spring-boot-starter-web</artifactId>")
    }

    def "generated pom includes spring-boot-maven-plugin repackage"() {
        when:
        def content = pom()

        then:
        content.contains("<goal>repackage</goal>")
    }
}
