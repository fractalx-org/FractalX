package org.fractalx.core.gateway

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that GatewayRateLimiterGenerator creates RateLimitConfig and
 * RateLimitFilter in the gateway ratelimit package with the expected
 * sliding-window implementation and response headers.
 */
class GatewayRateLimiterGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    GatewayRateLimiterGenerator generator = new GatewayRateLimiterGenerator()

    FractalModule order = FractalModule.builder()
        .serviceName("order-service")
        .packageName("com.example.order")
        .port(8081)
        .build()

    private Path ratelimitPkg() {
        srcMainJava.resolve("org/fractalx/gateway/ratelimit")
    }

    private String rateLimitConfig() { Files.readString(ratelimitPkg().resolve("RateLimitConfig.java")) }
    private String rateLimitFilter() { Files.readString(ratelimitPkg().resolve("RateLimitFilter.java")) }

    def "RateLimitConfig.java is created in the ratelimit package"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        Files.exists(ratelimitPkg().resolve("RateLimitConfig.java"))
    }

    def "RateLimitFilter.java is created in the ratelimit package"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        Files.exists(ratelimitPkg().resolve("RateLimitFilter.java"))
    }

    def "RateLimitConfig is a @ConfigurationProperties class with the rate-limit prefix"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = rateLimitConfig()
        c.startsWith("package org.fractalx.gateway.ratelimit;")
        c.contains("@ConfigurationProperties")
        c.contains("fractalx.gateway.rate-limit")
    }

    def "RateLimitConfig has a default of 100 requests per second"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        rateLimitConfig().contains("defaultRps = 100")
    }

    def "RateLimitConfig supports per-service overrides via a Map"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = rateLimitConfig()
        c.contains("Map")
        c.contains("perService")
    }

    def "RateLimitFilter is a @Component implementing GlobalFilter with order -80"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = rateLimitFilter()
        c.startsWith("package org.fractalx.gateway.ratelimit;")
        c.contains("@Component")
        c.contains("implements GlobalFilter, Ordered")
        c.contains("-80")
    }

    def "RateLimitFilter uses ConcurrentHashMap for the sliding window"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        rateLimitFilter().contains("ConcurrentHashMap")
    }

    def "RateLimitFilter returns HTTP 429 when rate limit is exceeded"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        rateLimitFilter().contains("TOO_MANY_REQUESTS")
    }

    def "RateLimitFilter sets Retry-After and X-RateLimit headers on 429 response"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        def c = rateLimitFilter()
        c.contains("Retry-After")
        c.contains("X-RateLimit-Limit")
        c.contains("X-RateLimit-Remaining")
    }

    def "RateLimitFilter respects X-Forwarded-For header for IP extraction"() {
        when:
        generator.generate(srcMainJava, [order])

        then:
        rateLimitFilter().contains("X-Forwarded-For")
    }
}
