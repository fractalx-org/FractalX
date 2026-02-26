package com.fractalx.core.generator.admin

import com.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that AdminTopologyGenerator produces TopologyGraph model,
 * ServiceTopologyProvider with baked-in nodes/edges, and TopologyController
 * with topology, health summary, and services proxy endpoints.
 */
class AdminTopologyGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    AdminTopologyGenerator generator = new AdminTopologyGenerator()

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
        .build()

    private static final String BASE_PKG = "com.fractalx.admin"

    private Path topologyPkg() {
        srcMainJava.resolve("com/fractalx/admin/topology")
    }

    private String topologyGraph()    { Files.readString(topologyPkg().resolve("TopologyGraph.java")) }
    private String topologyProvider() { Files.readString(topologyPkg().resolve("ServiceTopologyProvider.java")) }
    private String topologyCtrl()     { Files.readString(topologyPkg().resolve("TopologyController.java")) }

    def "TopologyGraph.java is created in the topology package"() {
        when:
        generator.generate(srcMainJava, BASE_PKG, [order])

        then:
        Files.exists(topologyPkg().resolve("TopologyGraph.java"))
    }

    def "ServiceTopologyProvider.java is created in the topology package"() {
        when:
        generator.generate(srcMainJava, BASE_PKG, [order])

        then:
        Files.exists(topologyPkg().resolve("ServiceTopologyProvider.java"))
    }

    def "TopologyController.java is created in the topology package"() {
        when:
        generator.generate(srcMainJava, BASE_PKG, [order])

        then:
        Files.exists(topologyPkg().resolve("TopologyController.java"))
    }

    def "TopologyGraph defines ServiceNode and ServiceEdge record types"() {
        when:
        generator.generate(srcMainJava, BASE_PKG, [order])

        then:
        def c = topologyGraph()
        c.startsWith("package com.fractalx.admin.topology;")
        c.contains("record ServiceNode")
        c.contains("record ServiceEdge")
    }

    def "TopologyGraph provides getNodes() and getEdges() accessors"() {
        when:
        generator.generate(srcMainJava, BASE_PKG, [order])

        then:
        def c = topologyGraph()
        c.contains("getNodes()")
        c.contains("getEdges()")
    }

    def "ServiceTopologyProvider is a @Component that bakes in all module nodes"() {
        when:
        generator.generate(srcMainJava, BASE_PKG, [order, payment])

        then:
        def c = topologyProvider()
        c.contains("@Component")
        c.contains("order-service")
        c.contains("payment-service")
    }

    def "ServiceTopologyProvider includes gateway, registry, and admin nodes"() {
        when:
        generator.generate(srcMainJava, BASE_PKG, [order])

        then:
        def c = topologyProvider()
        c.contains("fractalx-gateway")
        c.contains("fractalx-registry")
        c.contains("admin-service")
    }

    def "ServiceTopologyProvider bakes in gRPC edges for module dependencies"() {
        when:
        generator.generate(srcMainJava, BASE_PKG, [order, payment])

        then:
        def c = topologyProvider()
        c.contains("grpc")
        c.contains("order-service")
        c.contains("payment-service")
    }

    def "TopologyController is a @RestController mapped to /api"() {
        when:
        generator.generate(srcMainJava, BASE_PKG, [order])

        then:
        def c = topologyCtrl()
        c.startsWith("package com.fractalx.admin.topology;")
        c.contains("@RestController")
        c.contains("@RequestMapping(\"/api\")")
    }

    def "TopologyController exposes GET /topology endpoint"() {
        when:
        generator.generate(srcMainJava, BASE_PKG, [order])

        then:
        def c = topologyCtrl()
        c.contains("/topology")
        c.contains("getTopology()")
    }

    def "TopologyController exposes GET /health/summary endpoint"() {
        when:
        generator.generate(srcMainJava, BASE_PKG, [order])

        then:
        def c = topologyCtrl()
        c.contains("/health/summary")
        c.contains("getHealthSummary()")
    }

    def "TopologyController exposes GET /services endpoint proxying the registry"() {
        when:
        generator.generate(srcMainJava, BASE_PKG, [order])

        then:
        def c = topologyCtrl()
        c.contains("/services")
        c.contains("getLiveServices()")
    }

    def "health summary includes health check for each generated service"() {
        when:
        generator.generate(srcMainJava, BASE_PKG, [order, payment])

        then:
        def c = topologyCtrl()
        c.contains("localhost:8081")
        c.contains("localhost:8082")
        c.contains("/actuator/health")
    }
}
