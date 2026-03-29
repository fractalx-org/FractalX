package org.fractalx.core.validation.rules

import org.fractalx.core.model.FractalModule
import org.fractalx.core.validation.ValidationContext
import org.fractalx.core.validation.ValidationSeverity
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Path

class ServiceNameFormatRuleSpec extends Specification {

    @TempDir Path tmp

    private static final def RULE = new ServiceNameFormatRule()

    private FractalModule mod(String name) {
        FractalModule.builder().serviceName(name).port(8081)
                .className("com.example.Module").packageName("com.example").build()
    }

    private ValidationContext ctx(String name) {
        new ValidationContext([mod(name)], tmp, null)
    }

    // ── valid names ──────────────────────────────────────────────────────────

    @Unroll
    def "valid serviceName '#name' produces no error"() {
        expect:
        RULE.validate(new ValidationContext([mod(name)], tmp, null)).isEmpty()

        where:
        name << ["order-service", "payment-service", "a", "abc123", "my-cool-svc"]
    }

    // ── invalid names ────────────────────────────────────────────────────────

    @Unroll
    def "invalid serviceName '#name' produces INVALID_SERVICE_NAME error"() {
        when:
        def issues = RULE.validate(ctx(name))

        then:
        issues.any { it.severity() == ValidationSeverity.ERROR && it.ruleId() == "INVALID_SERVICE_NAME" }

        where:
        name << [
                "Order_Service",   // underscore
                "OrderService",    // uppercase
                "1order-service",  // starts with digit
                "order service",   // space
        ]
    }

    def "serviceName longer than 63 chars produces error"() {
        given:
        String longName = "a" * 64

        when:
        def issues = RULE.validate(ctx(longName))

        then:
        issues.any { it.ruleId() == "INVALID_SERVICE_NAME" }
    }

    def "serviceName of exactly 63 chars starting with lowercase is valid"() {
        given:
        String name = "a" + "b" * 62   // 63 chars

        expect:
        RULE.validate(ctx(name)).isEmpty()
    }

    def "error message contains the offending name"() {
        when:
        def issues = RULE.validate(ctx("Order_Service"))

        then:
        issues[0].message().contains("Order_Service")
        issues[0].fix().contains("order-service")
    }
}
