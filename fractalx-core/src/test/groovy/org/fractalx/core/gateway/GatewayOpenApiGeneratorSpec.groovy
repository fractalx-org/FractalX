package org.fractalx.core.gateway

import org.fractalx.core.auth.AuthPattern
import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class GatewayOpenApiGeneratorSpec extends Specification {

    @TempDir
    Path gatewayRoot

    GatewayOpenApiGenerator generator = new GatewayOpenApiGenerator()

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

    private String openApi() {
        Files.readString(gatewayRoot.resolve("docs/openapi.yaml"))
    }

    def "generates docs directory with openapi.yaml"() {
        when:
        generator.generate(gatewayRoot, [order, payment])

        then:
        Files.exists(gatewayRoot.resolve("docs/openapi.yaml"))
    }

    def "openapi.yaml uses OpenAPI 3.0.3"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        openApi().contains('openapi: "3.0.3"')
    }

    def "openapi.yaml declares gateway server on port 9999"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        openApi().contains("url: \"http://localhost:9999\"")
    }

    def "openapi.yaml includes direct server for each service"() {
        when:
        generator.generate(gatewayRoot, [order, payment])

        then:
        openApi().contains("url: \"http://localhost:8081\"")
        openApi().contains("url: \"http://localhost:8082\"")
    }

    def "openapi.yaml declares a tag per service"() {
        when:
        generator.generate(gatewayRoot, [order, payment])

        then:
        openApi().contains("name: \"order-service\"")
        openApi().contains("name: \"payment-service\"")
    }

    def "openapi.yaml includes gateway health path"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        openApi().contains("/actuator/health:")
        openApi().contains("operationId: \"gateway-health\"")
    }

    def "openapi.yaml defines BearerAuth and ApiKeyAuth security schemes"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        def spec = openApi()
        spec.contains("BearerAuth:")
        spec.contains("scheme: bearer")
        spec.contains("ApiKeyAuth:")
        spec.contains("name: X-Api-Key")
    }

    def "openapi.yaml references GenericRequest and GenericResponse schemas"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        def spec = openApi()
        spec.contains("GenericRequest:")
        spec.contains("GenericResponse:")
    }

    def "no service paths emitted without monolith source"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        def spec = openApi()
        !spec.contains("/api/orders:")
    }

    // ── Controller-scanning mode (monolithSrc provided) ───────────────────────

    @TempDir
    Path monolithSrc

    private void writeController(String pkg, String className, String classPath,
                                 List<String[]> methods) {
        Path dir = monolithSrc.resolve(pkg.replace('.', '/'))
        Files.createDirectories(dir)
        def sb = new StringBuilder("package ${pkg};\n")
        sb.append("import org.springframework.web.bind.annotation.*;\n")
        sb.append("@RestController\n")
        sb.append("@RequestMapping(\"${classPath}\")\n")
        sb.append("public class ${className} {\n")
        methods.eachWithIndex { m, i ->
            sb.append("  @${m[0]}Mapping(\"${m[1]}\")\n")
            sb.append("  public Object method${i}() { return null; }\n")
        }
        sb.append("}\n")
        Files.writeString(dir.resolve("${className}.java"), sb.toString())
    }

    def "scanning mode: includes standard error response codes"() {
        given:
        writeController("com.example.order", "OrderController", "/api/orders",
            [["Get", ""], ["Post", ""]])

        when:
        generator.generate(gatewayRoot, [order], AuthPattern.none(), monolithSrc)

        then:
        def spec = openApi()
        spec.contains('"400"')
        spec.contains('"401"')
        spec.contains('"404"')
        spec.contains('"500"')
    }

    def "scanning mode: openapi uses actual scanned paths with correct HTTP methods"() {
        given:
        writeController("com.example.order", "OrderController", "/api/orders",
            [["Get", ""], ["Post", ""], ["Get", "/{id}"], ["Put", "/{id}"], ["Delete", "/{id}"],
             ["Patch", "/{id}/status"]])

        when:
        generator.generate(gatewayRoot, [order], AuthPattern.none(), monolithSrc)

        then:
        def spec = openApi()
        spec.contains("/api/orders:")
        spec.contains("/api/orders/{id}:")
        spec.contains("/api/orders/{id}/status:")
        spec.contains("    get:")
        spec.contains("    post:")
        spec.contains("    put:")
        spec.contains("    delete:")
        spec.contains("    patch:")
    }

    def "scanning mode: cross-resource endpoint appears under owning service tag"() {
        given:
        FractalModule customer = FractalModule.builder()
            .serviceName("customer-service")
            .packageName("com.example.customer")
            .port(8083)
            .build()

        writeController("com.example.order", "OrderController", "/api/orders",
            [["Get", ""], ["Post", ""], ["Get", "/{id}"]])
        writeController("com.example.order", "OrderCrossController", "/api/customers/{customerId}",
            [["Get", "/orders"]])
        writeController("com.example.customer", "CustomerController", "/api/customers",
            [["Get", ""], ["Post", ""], ["Get", "/{id}"]])

        when:
        generator.generate(gatewayRoot, [order, customer], AuthPattern.none(), monolithSrc)

        then:
        def spec = openApi()
        spec.contains("/api/customers/{id}/orders")
        spec.contains('tags: ["order-service"]')
    }

    def "scanning mode: no paths emitted when no controllers found in package"() {
        given: "monolithSrc exists but module package dir does not"
        Files.createDirectories(monolithSrc)

        when:
        generator.generate(gatewayRoot, [order], AuthPattern.none(), monolithSrc)

        then: "no service paths are emitted"
        def spec = openApi()
        !spec.contains("/api/orders:")
        // gateway health is still present
        spec.contains("/actuator/health:")
    }

    def "does not generate postman_collection.json"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        !Files.exists(gatewayRoot.resolve("docs/postman_collection.json"))
    }
}
