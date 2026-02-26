package com.fractalx.core.gateway

import com.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that GatewaySecurityGenerator creates all five security files
 * (GatewayAuthProperties, GatewaySecurityConfig, JwtBearerFilter,
 * ApiKeyFilter, BasicAuthGatewayFilter) in the gateway security package.
 */
class GatewaySecurityGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    GatewaySecurityGenerator generator = new GatewaySecurityGenerator()

    FractalModule order = FractalModule.builder()
        .serviceName("order-service")
        .packageName("com.example.order")
        .port(8081)
        .build()

    private Path securityPkg() {
        srcMainJava.resolve("com/fractalx/gateway/security")
    }

    private String authProperties() { Files.readString(securityPkg().resolve("GatewayAuthProperties.java")) }
    private String securityConfig() { Files.readString(securityPkg().resolve("GatewaySecurityConfig.java")) }
    private String jwtFilter()      { Files.readString(securityPkg().resolve("JwtBearerFilter.java")) }
    private String apiKeyFilter()   { Files.readString(securityPkg().resolve("ApiKeyFilter.java")) }
    private String basicFilter()    { Files.readString(securityPkg().resolve("BasicAuthGatewayFilter.java")) }

    def "all five security files are created"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        Files.exists(securityPkg().resolve("GatewayAuthProperties.java"))
        Files.exists(securityPkg().resolve("GatewaySecurityConfig.java"))
        Files.exists(securityPkg().resolve("JwtBearerFilter.java"))
        Files.exists(securityPkg().resolve("ApiKeyFilter.java"))
        Files.exists(securityPkg().resolve("BasicAuthGatewayFilter.java"))
    }

    def "GatewayAuthProperties is a @ConfigurationProperties class with the correct prefix"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = authProperties()
        c.startsWith("package com.fractalx.gateway.security;")
        c.contains("@ConfigurationProperties")
        c.contains("fractalx.gateway.security")
    }

    def "GatewayAuthProperties declares all four auth mechanism nested classes"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = authProperties()
        c.contains("class Bearer")
        c.contains("class OAuth2")
        c.contains("class Basic")
        c.contains("class ApiKey")
    }

    def "all auth mechanisms are disabled by default"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        authProperties().count("enabled = false") >= 4
    }

    def "GatewaySecurityConfig enables WebFlux security and wires all filters"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = securityConfig()
        c.contains("@EnableWebFluxSecurity")
        c.contains("JwtBearerFilter")
        c.contains("ApiKeyFilter")
        c.contains("BasicAuthGatewayFilter")
    }

    def "JwtBearerFilter implements GlobalFilter with order -90"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = jwtFilter()
        c.contains("implements GlobalFilter, Ordered")
        c.contains("-90")
    }

    def "JwtBearerFilter injects X-User-Id and X-User-Roles headers on success"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = jwtFilter()
        c.contains("X-User-Id")
        c.contains("X-User-Roles")
        c.contains("X-Auth-Method")
    }

    def "ApiKeyFilter implements GlobalFilter with order -95"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = apiKeyFilter()
        c.contains("implements GlobalFilter, Ordered")
        c.contains("-95")
    }

    def "ApiKeyFilter accepts key from X-Api-Key header or api_key query param"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = apiKeyFilter()
        c.contains("X-Api-Key")
        c.contains("api_key")
    }

    def "BasicAuthGatewayFilter implements GlobalFilter with order -85"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = basicFilter()
        c.contains("implements GlobalFilter, Ordered")
        c.contains("-85")
    }

    def "BasicAuthGatewayFilter decodes Base64 Basic Auth credentials"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = basicFilter()
        c.contains("Base64")
        c.contains("Basic ")
    }
}
