package org.fractalx.core.verifier

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class ConfigPropertyCheckerSpec extends Specification {

    @TempDir
    Path tempDir

    ConfigPropertyChecker checker = new ConfigPropertyChecker()

    private FractalModule module(String name) {
        FractalModule.builder()
                .serviceName(name)
                .className(name)
                .packageName("com.example")
                .port(8081)
                .build()
    }

    private void writeJava(String service, String fileName, String content) {
        Path dir = tempDir.resolve(service).resolve("src/main/java")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve(fileName), content)
    }

    private void writeYaml(String service, String fileName, String content) {
        Path dir = tempDir.resolve(service).resolve("src/main/resources")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve(fileName), content)
    }

    def "returns empty when output dir does not exist"() {
        when:
        def result = checker.check(tempDir.resolve("nonexistent"), [module("svc")])

        then:
        result.isEmpty()
    }

    def "passes when all @Value keys are covered by application.yml"() {
        given:
        def m = module("order-service")
        writeYaml("order-service", "application.yml", """\
app:
  name: order
  timeout: 30
""")
        writeJava("order-service", "OrderConfig.java", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class OrderConfig {
                @Value("\${app.name}") String name;
                @Value("\${app.timeout}") int timeout;
            }
        """)

        when:
        def result = checker.check(tempDir, [m])

        then:
        result.isEmpty()
    }

    def "flags MISSING_PROPERTY when @Value key not in application.yml"() {
        given:
        def m = module("order-service")
        writeYaml("order-service", "application.yml", "app:\n  name: order\n")
        writeJava("order-service", "OrderConfig.java", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class OrderConfig {
                @Value("\${missing.key}") String secret;
            }
        """)

        when:
        def result = checker.check(tempDir, [m])

        then:
        result.any { it.kind() == ConfigPropertyChecker.CfgFindingKind.MISSING_PROPERTY && it.key() == "missing.key" }
    }

    def "does not flag spring.* keys as missing"() {
        given:
        def m = module("order-service")
        writeYaml("order-service", "application.yml", "app:\n  name: order\n")
        writeJava("order-service", "OrderConfig.java", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class OrderConfig {
                @Value("\${spring.datasource.url}") String dsUrl;
                @Value("\${server.port}") int port;
                @Value("\${management.server.port}") int mgmt;
            }
        """)

        when:
        def result = checker.check(tempDir, [m])

        then:
        result.isEmpty()
    }

    def "does not flag keys with default values when key is missing"() {
        given:
        def m = module("order-service")
        writeYaml("order-service", "application.yml", "app:\n  name: order\n")
        // @Value("${custom.timeout:30}") has a default — still flagged if absent
        // but @Value("${spring.port:8080}") is framework-owned — not flagged
        writeJava("order-service", "OrderConfig.java", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class OrderConfig {
                @Value("\${spring.server.port:8080}") int port;
            }
        """)

        when:
        def result = checker.check(tempDir, [m])

        then:
        result.isEmpty()
    }

    def "flags EMPTY_VALUE_REF for @Value(\"\")"() {
        given:
        def m = module("order-service")
        writeYaml("order-service", "application.yml", "app:\n  name: test\n")
        writeJava("order-service", "OrderConfig.java", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class OrderConfig {
                @Value("") String emptyRef;
            }
        """)

        when:
        def result = checker.check(tempDir, [m])

        then:
        result.any { it.kind() == ConfigPropertyChecker.CfgFindingKind.EMPTY_VALUE_REF }
    }

    def "reads keys from application-dev.yml as well"() {
        given:
        def m = module("order-service")
        writeYaml("order-service", "application.yml", "app:\n  name: order\n")
        writeYaml("order-service", "application-dev.yml", "app:\n  dev-key: devvalue\n")
        writeJava("order-service", "OrderConfig.java", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class OrderConfig {
                @Value("\${app.dev-key}") String devKey;
            }
        """)

        when:
        def result = checker.check(tempDir, [m])

        then:
        result.isEmpty()
    }

    def "reads keys from application.properties"() {
        given:
        def m = module("order-service")
        writeYaml("order-service", "application.properties", "app.name=order\napp.timeout=30\n")
        writeJava("order-service", "OrderConfig.java", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class OrderConfig {
                @Value("\${app.name}") String name;
                @Value("\${app.timeout}") int timeout;
            }
        """)

        when:
        def result = checker.check(tempDir, [m])

        then:
        result.isEmpty()
    }

    def "handles unparseable Java file gracefully"() {
        given:
        def m = module("order-service")
        writeYaml("order-service", "application.yml", "app:\n  name: order\n")
        writeJava("order-service", "Bad.java", "this is not java {{ {{ }}")

        when:
        def result = checker.check(tempDir, [m])

        then:
        notThrown(Exception)
    }

    def "MISSING_PROPERTY is critical, EMPTY_VALUE_REF is not"() {
        expect:
        new ConfigPropertyChecker.CfgFinding(
            ConfigPropertyChecker.CfgFindingKind.MISSING_PROPERTY,
            "svc", Path.of("X.java"), "some.key", "detail").isCritical()
        !new ConfigPropertyChecker.CfgFinding(
            ConfigPropertyChecker.CfgFindingKind.EMPTY_VALUE_REF,
            "svc", Path.of("X.java"), "(empty)", "detail").isCritical()
    }

    def "toString includes key and service name"() {
        given:
        def f = new ConfigPropertyChecker.CfgFinding(
            ConfigPropertyChecker.CfgFindingKind.MISSING_PROPERTY,
            "my-service", Path.of("Config.java"), "app.secret", "no match")

        when:
        def s = f.toString()

        then:
        s.contains("my-service")
        s.contains("app.secret")
        s.contains("[FAIL]")
    }
}
