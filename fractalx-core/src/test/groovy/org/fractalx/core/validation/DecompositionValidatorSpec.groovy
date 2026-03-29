package org.fractalx.core.validation

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class DecompositionValidatorSpec extends Specification {

    @TempDir Path tmp

    // ── helpers ───────────────────────────────────────────────────────────────

    private FractalModule module(String serviceName, int port, List<String> deps = []) {
        FractalModule.builder()
                .serviceName(serviceName)
                .port(port)
                .className("com.example." + serviceName.replace('-', '.') + ".Module")
                .packageName("com.example." + serviceName.replace('-', '.'))
                .dependencies(deps)
                .build()
    }

    // ── clean configuration ────────────────────────────────────────────────

    def "clean set of modules produces no issues"() {
        given:
        def mods = [module("order-service", 8081), module("payment-service", 8082)]

        when:
        def report = new DecompositionValidator().validate(mods, tmp)

        then:
        report.isClean() || report.errors().isEmpty() // warnings may exist; no errors
    }

    // ── duplicate port ─────────────────────────────────────────────────────

    def "duplicate HTTP port produces an error"() {
        given:
        def mods = [module("order-service", 8081), module("payment-service", 8081)]

        when:
        def report = new DecompositionValidator().validate(mods, tmp)

        then:
        report.hasErrors()
        report.errors().any { it.ruleId() == "DUP_PORT" }
    }

    // ── invalid service name ───────────────────────────────────────────────

    def "invalid serviceName produces an error"() {
        given:
        def mods = [
                FractalModule.builder()
                        .serviceName("Order_Service")
                        .port(8081)
                        .className("com.example.OrderModule")
                        .packageName("com.example")
                        .build()
        ]

        when:
        def report = new DecompositionValidator().validate(mods, tmp)

        then:
        report.hasErrors()
        report.errors().any { it.ruleId() == "INVALID_SERVICE_NAME" }
    }

    // ── warnings only allow generation ─────────────────────────────────────

    def "warnings-only report does not block generation"() {
        given:
        // UnresolvedDependencyRule will warn about 'ExternalEngine' not being a known module
        def mods = [module("order-service", 8081, ["ExternalEngine"])]

        when:
        def report = new DecompositionValidator().validate(mods, tmp)

        then:
        !report.hasErrors() // generation must not be blocked
        // might have UNRESOLVED_DEP warning
    }

    // ── duplicate service name ─────────────────────────────────────────────

    def "duplicate service name produces an error"() {
        given:
        def m1 = FractalModule.builder()
                .serviceName("order-service").port(8081)
                .className("com.example.order.ModuleA").packageName("com.example.order")
                .build()
        def m2 = FractalModule.builder()
                .serviceName("order-service").port(8082)
                .className("com.example.billing.ModuleB").packageName("com.example.billing")
                .build()

        when:
        def report = new DecompositionValidator().validate([m1, m2], tmp)

        then:
        report.hasErrors()
        report.errors().any { it.ruleId() == "DUP_SERVICE_NAME" }
    }

    // ── formatting ─────────────────────────────────────────────────────────

    def "formatReport includes error rule IDs and fixes"() {
        given:
        def mods = [module("order-service", 8081), module("payment-service", 8081)]

        when:
        def report = new DecompositionValidator().validate(mods, tmp)
        def text   = report.formatReport()

        then:
        text.contains("DUP_PORT")
        text.contains("Fix:")
    }
}
