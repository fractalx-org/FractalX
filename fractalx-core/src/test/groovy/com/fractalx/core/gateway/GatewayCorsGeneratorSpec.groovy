package com.fractalx.core.gateway

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that GatewayCorsGenerator creates GatewayCorsConfig with a
 * @Bean CorsWebFilter, config-driven allowed origins/methods, and the
 * required exposed response headers.
 */
class GatewayCorsGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    GatewayCorsGenerator generator = new GatewayCorsGenerator()

    private Path corsPkg() {
        srcMainJava.resolve("com/fractalx/gateway/cors")
    }

    private String corsConfig() {
        Files.readString(corsPkg().resolve("GatewayCorsConfig.java"))
    }

    def "GatewayCorsConfig.java is created in the cors package"() {
        when:
        generator.generate(srcMainJava)

        then:
        Files.exists(corsPkg().resolve("GatewayCorsConfig.java"))
    }

    def "GatewayCorsConfig is a @Configuration in the correct package"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = corsConfig()
        c.startsWith("package com.fractalx.gateway.cors;")
        c.contains("@Configuration")
    }

    def "GatewayCorsConfig declares a @Bean CorsWebFilter"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = corsConfig()
        c.contains("@Bean")
        c.contains("CorsWebFilter")
    }

    def "CORS config reads allowed-origins from fractalx.gateway.cors.allowed-origins property"() {
        when:
        generator.generate(srcMainJava)

        then:
        corsConfig().contains("fractalx.gateway.cors.allowed-origins")
    }

    def "CORS config reads allowed-methods from property"() {
        when:
        generator.generate(srcMainJava)

        then:
        corsConfig().contains("fractalx.gateway.cors.allowed-methods")
    }

    def "CORS config exposes tracing and auth headers in responses"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = corsConfig()
        c.contains("X-Request-Id")
        c.contains("X-Trace-Id")
        c.contains("X-Auth-Method")
    }

    def "CORS config exposes rate limit headers in responses"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = corsConfig()
        c.contains("X-RateLimit-Limit")
        c.contains("X-RateLimit-Remaining")
    }

    def "CORS config supports allow-credentials and max-age properties"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = corsConfig()
        c.contains("allow-credentials")
        c.contains("max-age")
    }

    def "CORS config registers mapping on all paths"() {
        when:
        generator.generate(srcMainJava)

        then:
        corsConfig().contains("/**")
    }
}
