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
    Path tempDir

    ObservabilityInjector observabilityInjector = new ObservabilityInjector()
    PomGenerator generator = new PomGenerator(observabilityInjector)

    /**
     * Directory layout that mirrors real decompose output:
     *   tempDir/               ← "monolith root" — monolith pom.xml lives here
     *   tempDir/fractalx-output/              ← outputRoot
     *   tempDir/fractalx-output/order-service/ ← serviceRoot
     *
     * PomGenerator derives monolithRoot as serviceRoot.getParent().getParent()
     */
    private Path outputRoot()  { tempDir.resolve("fractalx-output") }
    private Path serviceRoot() { outputRoot().resolve("order-service") }

    /** Write a minimal monolith pom.xml with the given dependency XML blocks. */
    private void writeMonolithPom(String... depBlocks) {
        String deps = depBlocks.join("\n")
        Files.writeString(tempDir.resolve("pom.xml"), """
            <project>
                <groupId>com.example</groupId>
                <artifactId>monolith</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    ${deps}
                </dependencies>
            </project>
        """)
    }

    private String generate(FractalModule module) {
        def svcRoot = serviceRoot()
        Files.createDirectories(svcRoot)
        def ctx = new GenerationContext(module, outputRoot(), svcRoot,
                [module], FractalxConfig.defaults(), [])
        generator.generate(ctx)
        Files.readString(svcRoot.resolve("pom.xml"))
    }

    private FractalModule baseModule(Set<String> imports = []) {
        FractalModule.builder()
            .serviceName("order-service")
            .packageName("com.example.order")
            .port(8081)
            .detectedImports(imports)
            .build()
    }

    // ── Always-present deps ────────────────────────────────────────────────────

    def "generated pom uses the current fractalx-runtime version"() {
        when:
        def content = generate(baseModule())

        then:
        def v = content.find(/(?s)<artifactId>fractalx-runtime<\/artifactId>\s*<version>([^<]+)<\/version>/) { _, v -> v }
        v == FractalxVersion.get()
    }

    def "netscope-server version is 1.0.1"() {
        when:
        def content = generate(baseModule())

        then:
        def v = content.find(/(?s)<artifactId>netscope-server<\/artifactId>\s*<version>([^<]+)<\/version>/) { _, v -> v }
        v == "1.0.1"
    }

    def "netscope-client version is 1.0.1"() {
        when:
        def content = generate(baseModule())

        then:
        def v = content.find(/(?s)<artifactId>netscope-client<\/artifactId>\s*<version>([^<]+)<\/version>/) { _, v -> v }
        v == "1.0.1"
    }

    def "generated pom sets service artifactId"() {
        when:
        def content = generate(baseModule())

        then:
        content.contains("<artifactId>order-service</artifactId>")
    }

    def "generated pom always includes spring-boot-starter-web"() {
        when:
        def content = generate(baseModule())

        then:
        content.contains("<artifactId>spring-boot-starter-web</artifactId>")
    }

    def "generated pom includes spring-boot-maven-plugin repackage"() {
        when:
        def content = generate(baseModule())

        then:
        content.contains("<goal>repackage</goal>")
    }

    // ── Monolith dep cloning ───────────────────────────────────────────────────

    def "deps from monolith pom are cloned into service pom"() {
        given:
        writeMonolithPom("""
            <dependency>
                <groupId>com.google.guava</groupId>
                <artifactId>guava</artifactId>
                <version>32.0.1-jre</version>
            </dependency>
        """)

        when:
        def content = generate(baseModule())

        then:
        content.contains("guava")
    }

    def "no monolith pom produces pom with only fractalx required deps"() {
        // no pom.xml written to tempDir
        when:
        def content = generate(baseModule())

        then:
        content.contains("spring-boot-starter-web")
        content.contains("fractalx-runtime")
        !content.contains("guava")
    }

    // ── Pruning ────────────────────────────────────────────────────────────────

    def "JPA dep kept when jakarta.persistence import detected"() {
        given:
        writeMonolithPom("""
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-data-jpa</artifactId>
            </dependency>
            <dependency>
                <groupId>com.h2database</groupId>
                <artifactId>h2</artifactId>
                <scope>runtime</scope>
            </dependency>
        """)

        when:
        def content = generate(baseModule(["jakarta.persistence.Entity"] as Set))

        then:
        content.contains("spring-boot-starter-data-jpa")
        content.contains("<artifactId>h2</artifactId>")
    }

    def "JPA dep pruned when no persistence imports detected"() {
        given:
        writeMonolithPom("""
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-data-jpa</artifactId>
            </dependency>
        """)

        when:
        def content = generate(baseModule()) // no imports

        then:
        !content.contains("spring-boot-starter-data-jpa")
    }

    def "mysql dep kept when com.mysql import detected"() {
        given:
        writeMonolithPom("""
            <dependency>
                <groupId>com.mysql</groupId>
                <artifactId>mysql-connector-j</artifactId>
                <scope>runtime</scope>
            </dependency>
        """)

        when:
        def content = generate(baseModule(["com.mysql.cj.jdbc.Driver"] as Set))

        then:
        content.contains("mysql-connector-j")
    }

    def "mysql dep pruned when no mysql import detected"() {
        given:
        writeMonolithPom("""
            <dependency>
                <groupId>com.mysql</groupId>
                <artifactId>mysql-connector-j</artifactId>
                <scope>runtime</scope>
            </dependency>
        """)

        when:
        def content = generate(baseModule()) // no imports

        then:
        !content.contains("mysql-connector-j")
    }

    def "kafka dep kept when spring kafka import detected"() {
        given:
        writeMonolithPom("""
            <dependency>
                <groupId>org.springframework.kafka</groupId>
                <artifactId>spring-kafka</artifactId>
            </dependency>
        """)

        when:
        def content = generate(baseModule(["org.springframework.kafka.annotation.KafkaListener"] as Set))

        then:
        content.contains("spring-kafka")
    }

    def "unknown dep (no prune rule) is always kept"() {
        given:
        writeMonolithPom("""
            <dependency>
                <groupId>com.fasterxml.jackson.core</groupId>
                <artifactId>jackson-databind</artifactId>
            </dependency>
        """)

        when:
        def content = generate(baseModule()) // no imports — still kept

        then:
        content.contains("jackson-databind")
    }

    def "FractalX-managed deps in monolith pom are not duplicated"() {
        given:
        writeMonolithPom("""
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-web</artifactId>
            </dependency>
        """)

        when:
        def content = generate(baseModule())

        then:
        // Should appear exactly once
        content.count("spring-boot-starter-web") == 1
    }
}
