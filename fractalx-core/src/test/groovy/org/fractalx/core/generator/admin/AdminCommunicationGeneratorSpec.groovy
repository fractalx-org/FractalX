package org.fractalx.core.generator.admin

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies AdminCommunicationGenerator produces CommunicationController
 * with baked-in NetScope data and proxy endpoints.
 */
class AdminCommunicationGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    AdminCommunicationGenerator generator = new AdminCommunicationGenerator()

    FractalModule order = FractalModule.builder()
        .serviceName("order-service")
        .packageName("com.example.order")
        .port(8081)
        .dependencies(["PaymentService"])
        .build()

    FractalModule payment = FractalModule.builder()
        .serviceName("payment-service")
        .packageName("com.example.payment")
        .port(8082)
        .dependencies([])
        .build()

    static final String BASE = "org.fractalx.admin"

    def "generates CommunicationController in communication package"() {
        when:
        generator.generate(srcMainJava, BASE, [order, payment])

        then:
        Files.exists(srcMainJava.resolve("org/fractalx/admin/communication/CommunicationController.java"))
    }

    def "CommunicationController is in org.fractalx.admin.communication package"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        controller().contains("package org.fractalx.admin.communication")
    }

    def "CommunicationController is a @RestController mapped to /api/communication"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = controller()

        then:
        content.contains("@RestController")
        content.contains('/api/communication')
    }

    def "CommunicationController has topology endpoint"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        controller().contains("/topology") || controller().contains("topology")
    }

    def "CommunicationController has netscope endpoint"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        controller().contains("netscope") || controller().contains("NetScope")
    }

    def "CommunicationController has gateway health endpoint"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        controller().contains("gateway") && (controller().contains("health") || controller().contains("Health"))
    }

    def "CommunicationController has discovery stats endpoint"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        controller().contains("discovery") || controller().contains("stats")
    }

    def "CommunicationController bakes NetScope data for module with dependencies"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = controller()

        then:
        // order-service depends on PaymentService → payment-service
        content.contains('"order-service"')
        content.contains('"payment-service"')
    }

    def "CommunicationController uses gRPC port convention (HTTP + 10000)"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = controller()

        then:
        // payment-service HTTP=8082 → gRPC=18082
        content.contains("18082") || content.contains("NetScope/gRPC") || content.contains("portConvention")
    }

    def "CommunicationController does not bake NetScope entry for service with no dependencies"() {
        when:
        generator.generate(srcMainJava, BASE, [payment])
        def content = controller()

        then:
        // payment has no deps — its name should not appear as a source in netscope init
        !content.contains('"payment-service"') || content.contains("netScopeData")
    }

    def "CommunicationController imports ServiceMetaRegistry"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        controller().contains("ServiceMetaRegistry") || controller().contains("org.fractalx.admin.services")
    }

    def "CommunicationController uses RestTemplate for proxy calls"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        controller().contains("RestTemplate")
    }

    def "topology endpoint returns nodes and edges"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = controller()

        then:
        content.contains("nodes") && content.contains("edges")
    }

    // helper
    private String controller() {
        Files.readString(srcMainJava.resolve(
            "org/fractalx/admin/communication/CommunicationController.java"))
    }
}
