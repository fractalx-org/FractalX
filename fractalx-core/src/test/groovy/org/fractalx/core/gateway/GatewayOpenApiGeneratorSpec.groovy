package org.fractalx.core.gateway

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

    private String postman() {
        Files.readString(gatewayRoot.resolve("docs/postman_collection.json"))
    }

    def "generates docs directory with both output files"() {
        when:
        generator.generate(gatewayRoot, [order, payment])

        then:
        Files.exists(gatewayRoot.resolve("docs/openapi.yaml"))
        Files.exists(gatewayRoot.resolve("docs/postman_collection.json"))
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

    def "openapi.yaml generates CRUD paths for each service"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        def spec = openApi()
        spec.contains("/api/order:")
        spec.contains("/api/order/{id}:")
        spec.contains("operationId: \"order-service-list\"")
        spec.contains("operationId: \"order-service-create\"")
        spec.contains("operationId: \"order-service-get-by-id\"")
        spec.contains("operationId: \"order-service-update\"")
        spec.contains("operationId: \"order-service-delete\"")
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
        spec.contains("\$ref: \"#/components/schemas/GenericRequest\"")
        spec.contains("\$ref: \"#/components/schemas/GenericResponse\"")
    }

    def "openapi.yaml includes standard error response codes"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        def spec = openApi()
        spec.contains('"400"')
        spec.contains('"401"')
        spec.contains('"404"')
        spec.contains('"500"')
    }

    def "postman_collection.json is valid JSON structure with info block"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        def json = postman()
        json.contains('"name": "FractalX Generated API"')
        json.contains('"schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"')
        json.contains('"_postman_id"')
    }

    def "postman_collection.json declares gateway_url variable pointing to port 9999"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        def json = postman()
        json.contains('"gateway_url"')
        json.contains('"http://localhost:9999"')
    }

    def "postman_collection.json contains a folder per service"() {
        when:
        generator.generate(gatewayRoot, [order, payment])

        then:
        def json = postman()
        json.contains('"name": "Order Service"')
        json.contains('"name": "Payment Service"')
    }

    def "postman_collection.json contains Gateway health request"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        def json = postman()
        json.contains('"name": "Gateway"')
        json.contains('"name": "Gateway Health"')
        json.contains('/actuator/health')
    }

    def "postman_collection.json generates CRUD requests per service"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        def json = postman()
        json.contains('"name": "List Order Service"')
        json.contains('"name": "Create Order Service"')
        json.contains('"name": "Get Order Service by ID"')
        json.contains('"name": "Update Order Service"')
        json.contains('"name": "Delete Order Service"')
    }

    def "postman requests include inline test scripts"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        def json = postman()
        json.contains('"listen": "test"')
        json.contains('pm.test(')
        json.contains('pm.response.responseTime')
        json.contains('below(2000)')
    }

    def "postman requests include Authorization and Content-Type headers"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        def json = postman()
        json.contains('"Authorization"')
        json.contains('"Bearer {{auth_token}}"')
        json.contains('"Content-Type"')
        json.contains('"application/json"')
    }

    def "postman POST and PUT requests include raw JSON body"() {
        when:
        generator.generate(gatewayRoot, [order])

        then:
        def json = postman()
        json.contains('"mode": "raw"')
        json.contains('"language": "json"')
    }
}
