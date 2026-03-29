package org.fractalx.core.validation.rules

import org.fractalx.core.model.FractalModule
import org.fractalx.core.validation.ValidationContext
import org.fractalx.core.validation.ValidationSeverity
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class CircularDependencyRuleSpec extends Specification {

    @TempDir Path tmp

    private static final def RULE = new CircularDependencyRule()

    /**
     * Creates a module whose dependency list is expressed as bean type names.
     * ServiceGraphAnalyzer resolves them by matching the dependency string to the
     * simple class name extracted from each module's className field.
     *
     * e.g. mod("payment-service", ...) gets className "com.example.PaymentService"
     *      so classToService["PaymentService"] = "payment-service"
     *      and a dependency list of ["PaymentService"] on order-service correctly
     *      creates the edge order-service → payment-service.
     */
    private FractalModule mod(String serviceName, int port, List<String> depBeanTypes = []) {
        // Derive a simple class name from the service name so ServiceGraphAnalyzer
        // can resolve dep strings back to services.
        // "payment-service" → "PaymentService"
        String simpleName = serviceName.split("-").collect { it.capitalize() }.join("")
        FractalModule.builder().serviceName(serviceName).port(port)
                .className("com.example." + simpleName)
                .packageName("com.example")
                .dependencies(depBeanTypes)
                .build()
    }

    private ValidationContext ctx(List<FractalModule> mods) {
        new ValidationContext(mods, tmp, null)
    }

    def "no cycle in linear chain produces no error"() {
        given:
        // order → payment (no cycle)
        // mod("payment-service") → className "com.example.PaymentService" → simpleName "PaymentService"
        def mods = [
                mod("order-service",   8081, ["PaymentService"]),
                mod("payment-service", 8082)
        ]

        expect:
        RULE.validate(ctx(mods)).findAll { it.ruleId() == "CIRCULAR_DEP" }.isEmpty()
    }

    def "direct two-node cycle produces CIRCULAR_DEP error"() {
        given:
        // order → payment, payment → order
        // mod("order-service")   → simpleName "OrderService"
        // mod("payment-service") → simpleName "PaymentService"
        def mods = [
                mod("order-service",   8081, ["PaymentService"]),
                mod("payment-service", 8082, ["OrderService"])
        ]

        when:
        def issues = RULE.validate(ctx(mods))

        then:
        issues.any { it.severity() == ValidationSeverity.ERROR && it.ruleId() == "CIRCULAR_DEP" }
    }

    def "transitive three-node cycle is detected"() {
        given:
        // A → B → C → A
        // mod("alpha-service") → "AlphaService"
        // mod("beta-service")  → "BetaService"
        // mod("gamma-service") → "GammaService"
        def mods = [
                mod("alpha-service", 8081, ["BetaService"]),
                mod("beta-service",  8082, ["GammaService"]),
                mod("gamma-service", 8083, ["AlphaService"])
        ]

        when:
        def issues = RULE.validate(ctx(mods))

        then:
        issues.any { it.ruleId() == "CIRCULAR_DEP" }
    }

    def "independent services with no deps produce no cycle error"() {
        given:
        def mods = [mod("alpha-service", 8081), mod("beta-service", 8082)]

        expect:
        RULE.validate(ctx(mods)).findAll { it.ruleId() == "CIRCULAR_DEP" }.isEmpty()
    }
}
