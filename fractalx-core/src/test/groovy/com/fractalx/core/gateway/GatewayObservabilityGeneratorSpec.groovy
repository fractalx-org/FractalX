package com.fractalx.core.gateway

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that GatewayObservabilityGenerator creates TracingFilter and
 * RequestLoggingFilter in the gateway observability package with the
 * expected ordering and header propagation.
 */
class GatewayObservabilityGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    GatewayObservabilityGenerator generator = new GatewayObservabilityGenerator()

    private Path observabilityPkg() {
        srcMainJava.resolve("com/fractalx/gateway/observability")
    }

    private String tracingFilter()  { Files.readString(observabilityPkg().resolve("TracingFilter.java")) }
    private String loggingFilter()  { Files.readString(observabilityPkg().resolve("RequestLoggingFilter.java")) }

    def "TracingFilter.java is created in the observability package"() {
        when:
        generator.generate(srcMainJava)

        then:
        Files.exists(observabilityPkg().resolve("TracingFilter.java"))
    }

    def "RequestLoggingFilter.java is created in the observability package"() {
        when:
        generator.generate(srcMainJava)

        then:
        Files.exists(observabilityPkg().resolve("RequestLoggingFilter.java"))
    }

    def "TracingFilter is a @Component implementing GlobalFilter with order -99"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = tracingFilter()
        c.startsWith("package com.fractalx.gateway.observability;")
        c.contains("@Component")
        c.contains("implements GlobalFilter, Ordered")
        c.contains("-99")
    }

    def "TracingFilter generates a UUID X-Request-Id per request"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = tracingFilter()
        c.contains("UUID.randomUUID()")
        c.contains("X-Request-Id")
    }

    def "TracingFilter propagates X-Correlation-Id from upstream if present, else uses request ID"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = tracingFilter()
        c.contains("X-Correlation-Id")
    }

    def "TracingFilter echoes X-Request-Id in the response headers"() {
        when:
        generator.generate(srcMainJava)

        then:
        tracingFilter().contains("getResponse().getHeaders().set(\"X-Request-Id\"")
    }

    def "RequestLoggingFilter is a @Component implementing GlobalFilter with order -100"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = loggingFilter()
        c.startsWith("package com.fractalx.gateway.observability;")
        c.contains("@Component")
        c.contains("implements GlobalFilter, Ordered")
        c.contains("-100")
    }

    def "RequestLoggingFilter logs method, path, status, duration, and correlationId"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = loggingFilter()
        c.contains("method")
        c.contains("path")
        c.contains("status")
        c.contains("duration")
        c.contains("correlationId")
    }

    def "RequestLoggingFilter logs on both ingress and egress using doFinally"() {
        when:
        generator.generate(srcMainJava)

        then:
        loggingFilter().contains("doFinally")
    }
}
