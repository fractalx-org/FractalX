package com.fractalx.core.gateway

import com.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that GatewayObservabilityGenerator creates three filters in the
 * gateway observability package: RequestLoggingFilter (order -100), TracingFilter
 * (order -99), and GatewayMetricsFilter (order -98).
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
    private String metricsFilter()  { Files.readString(observabilityPkg().resolve("GatewayMetricsFilter.java")) }

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

    // -------------------------------------------------------------------------
    // GatewayMetricsFilter (order -98)
    // -------------------------------------------------------------------------

    def "GatewayMetricsFilter.java is created in the observability package"() {
        when:
        generator.generate(srcMainJava)

        then:
        Files.exists(observabilityPkg().resolve("GatewayMetricsFilter.java"))
    }

    def "GatewayMetricsFilter is a @Component implementing GlobalFilter with order -98"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = metricsFilter()
        c.startsWith("package com.fractalx.gateway.observability;")
        c.contains("@Component")
        c.contains("implements GlobalFilter, Ordered")
        c.contains("-98")
    }

    def "GatewayMetricsFilter receives MeterRegistry via constructor injection"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = metricsFilter()
        c.contains("MeterRegistry meterRegistry")
        c.contains("this.meterRegistry = meterRegistry")
    }

    def "GatewayMetricsFilter records gateway.requests.total counter with service, method, status tags"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = metricsFilter()
        c.contains("Counter.builder(\"gateway.requests.total\")")
        c.contains(".tag(\"service\"")
        c.contains(".tag(\"method\"")
        c.contains(".tag(\"status\"")
        c.contains(".increment()")
    }

    def "GatewayMetricsFilter records gateway.requests.duration timer with service tag"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = metricsFilter()
        c.contains("Timer.builder(\"gateway.requests.duration\")")
        c.contains(".tag(\"service\"")
        c.contains(".record(duration, TimeUnit.MILLISECONDS)")
    }

    def "GatewayMetricsFilter extracts service name from URL path second segment"() {
        when:
        generator.generate(srcMainJava)

        then:
        def c = metricsFilter()
        c.contains("extractServiceName")
        c.contains("path.split(\"/\")")
        c.contains("\"unknown\"")
    }

    def "GatewayMetricsFilter records metrics in doFinally after response is available"() {
        when:
        generator.generate(srcMainJava)

        then:
        metricsFilter().contains("doFinally")
    }

    def "GatewayMetricsFilter is also created when generate is called with module list"() {
        given:
        def modules = [
            FractalModule.builder().serviceName("order-service").packageName("com.example.order").port(8081).build()
        ]

        when:
        generator.generate(srcMainJava, modules)

        then:
        Files.exists(observabilityPkg().resolve("GatewayMetricsFilter.java"))
    }
}
