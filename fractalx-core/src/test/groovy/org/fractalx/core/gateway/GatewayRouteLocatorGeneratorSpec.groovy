package org.fractalx.core.gateway

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that GatewayRouteLocatorGenerator creates DynamicRouteLocatorConfig
 * and RegistryRouteFetcher in the gateway routing package.
 */
class GatewayRouteLocatorGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    GatewayRouteLocatorGenerator generator = new GatewayRouteLocatorGenerator()

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

    private Path routingPkg() {
        srcMainJava.resolve("org/fractalx/gateway/routing")
    }

    private String routeLocator() {
        Files.readString(routingPkg().resolve("DynamicRouteLocatorConfig.java"))
    }

    private String routeFetcher() {
        Files.readString(routingPkg().resolve("RegistryRouteFetcher.java"))
    }

    def "DynamicRouteLocatorConfig.java is created in the routing package"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        Files.exists(routingPkg().resolve("DynamicRouteLocatorConfig.java"))
    }

    def "RegistryRouteFetcher.java is created in the routing package"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        Files.exists(routingPkg().resolve("RegistryRouteFetcher.java"))
    }

    def "DynamicRouteLocatorConfig is a @Configuration in the correct package"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = routeLocator()
        c.startsWith("package org.fractalx.gateway.routing;")
        c.contains("@Configuration")
    }

    def "DynamicRouteLocatorConfig declares a @Bean dynamicRouteLocator method"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = routeLocator()
        c.contains("@Bean")
        c.contains("dynamicRouteLocator()")
        c.contains("RouteLocator")
    }

    def "DynamicRouteLocatorConfig includes static fallback routes for each module"() {
        when:
        generator.generate(srcMainJava, [order, payment])

        then:
        def c = routeLocator()
        c.contains("order-service-static")
        c.contains("payment-service-static")
        c.contains("localhost:8081")
        c.contains("localhost:8082")
    }

    def "DynamicRouteLocatorConfig references RegistryRouteFetcher to fetch live routes"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = routeLocator()
        c.contains("RegistryRouteFetcher")
        c.contains("fetchRoutes")
    }

    def "RegistryRouteFetcher is a @Component in the correct package"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = routeFetcher()
        c.startsWith("package org.fractalx.gateway.routing;")
        c.contains("@Component")
    }

    def "RegistryRouteFetcher reads registry URL from fractalx.registry.url property"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        routeFetcher().contains("fractalx.registry.url")
    }

    def "RegistryRouteFetcher calls GET /services on the registry"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        routeFetcher().contains("/services")
    }

    def "RegistryRouteFetcher skips services that are not UP"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        routeFetcher().contains("\"UP\"")
    }
}
