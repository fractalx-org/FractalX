package org.fractalx.core.generator.admin

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that AdminGrpcBrowserGenerator produces GrpcBrowserController
 * in the org.fractalx.admin.grpc package with the expected REST endpoints.
 */
class AdminGrpcBrowserGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    AdminGrpcBrowserGenerator generator = new AdminGrpcBrowserGenerator()
    String basePackage = "org.fractalx.admin"

    FractalModule order = FractalModule.builder()
        .serviceName("order-service")
        .packageName("com.example.order")
        .port(8081)
        .build()

    FractalModule payment = FractalModule.builder()
        .serviceName("payment-service")
        .packageName("com.example.payment")
        .port(8082)
        .build()

    private Path grpcPkg() {
        srcMainJava.resolve("org/fractalx/admin/grpc")
    }

    private String read(String name) {
        Files.readString(grpcPkg().resolve(name))
    }

    def "GrpcBrowserController.java is created"() {
        when:
        generator.generate(srcMainJava, basePackage, [order, payment])

        then:
        Files.exists(grpcPkg().resolve("GrpcBrowserController.java"))
    }

    def "GrpcBrowserController is a @RestController mapped to /api/grpc"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("GrpcBrowserController.java")
        c.contains("@RestController")
        c.contains("/api/grpc")
    }

    def "GrpcBrowserController is in org.fractalx.admin.grpc package"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        read("GrpcBrowserController.java").contains("package org.fractalx.admin.grpc")
    }

    def "GrpcBrowserController exposes GET /services filtering by grpcPort > 0"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("GrpcBrowserController.java")
        c.contains("/services")
        c.contains("grpcPort")
        c.contains("filter")
    }

    def "GrpcBrowserController exposes GET /connections listing NetScope links"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("GrpcBrowserController.java")
        c.contains("/connections")
        c.contains("connections")
    }

    def "GrpcBrowserController exposes GET /{service}/deps with upstream and downstream"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("GrpcBrowserController.java")
        c.contains("/deps")
        c.contains("downstream")
        c.contains("upstream")
    }

    def "GrpcBrowserController exposes POST /ping with TCP reachability check"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("GrpcBrowserController.java")
        c.contains("@PostMapping")
        c.contains("/ping")
        c.contains("reachable")
        c.contains("latencyMs")
        c.contains("Socket")
    }

    def "GrpcBrowserController uses 2-second socket timeout for ping"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("GrpcBrowserController.java")
        c.contains("2000")
    }

    def "generator works with a single module"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        Files.exists(grpcPkg().resolve("GrpcBrowserController.java"))
    }

    def "generator works with multiple modules"() {
        when:
        generator.generate(srcMainJava, basePackage, [order, payment])

        then:
        Files.exists(grpcPkg().resolve("GrpcBrowserController.java"))
    }

    def "GrpcBrowserController injects ServiceMetaRegistry"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = read("GrpcBrowserController.java")
        c.contains("ServiceMetaRegistry")
    }
}
