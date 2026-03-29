package org.fractalx.core.validation.rules

import org.fractalx.core.model.FractalModule
import org.fractalx.core.validation.ValidationContext
import org.fractalx.core.validation.ValidationSeverity
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class DuplicatePortRuleSpec extends Specification {

    @TempDir Path tmp

    private static final def RULE = new DuplicatePortRule()

    private FractalModule mod(String name, int port) {
        FractalModule.builder().serviceName(name).port(port)
                .className("com.example.Module").packageName("com.example").build()
    }

    private ValidationContext ctx(List<FractalModule> mods) {
        new ValidationContext(mods, tmp, null)
    }

    def "no conflict when ports are distinct"() {
        given:
        def mods = [mod("order-service", 8081), mod("payment-service", 8082)]

        expect:
        RULE.validate(ctx(mods)).isEmpty()
    }

    def "same HTTP port produces ERROR"() {
        given:
        def mods = [mod("order-service", 8081), mod("payment-service", 8081)]

        when:
        def issues = RULE.validate(ctx(mods))

        then:
        issues.size() >= 1
        issues[0].severity() == ValidationSeverity.ERROR
        issues[0].ruleId() == "DUP_PORT"
        issues[0].message().contains("8081")
    }

    def "same gRPC port (derived) produces ERROR"() {
        given:
        // HTTP 8081 → gRPC 18081; HTTP 8081 shared → gRPC port conflict too
        def mods = [mod("a-service", 8081), mod("b-service", 8081)]

        when:
        def issues = RULE.validate(ctx(mods))

        then:
        // Should find HTTP conflict AND gRPC conflict (and possibly cross-conflict)
        issues.any { it.ruleId() == "DUP_PORT" && it.message().contains("18081") }
    }

    def "HTTP port of one module equals gRPC port of another produces ERROR"() {
        given:
        // Module A: HTTP 18082 (which equals Module B's gRPC: 8082 + 10000)
        def mods = [mod("a-service", 18082), mod("b-service", 8082)]

        when:
        def issues = RULE.validate(ctx(mods))

        then:
        issues.any { it.ruleId() == "DUP_PORT" && it.message().contains("18082") }
    }

    def "three distinct ports produce no conflict"() {
        given:
        def mods = [mod("a-service", 8081), mod("b-service", 8082), mod("c-service", 8083)]

        expect:
        RULE.validate(ctx(mods)).isEmpty()
    }
}
