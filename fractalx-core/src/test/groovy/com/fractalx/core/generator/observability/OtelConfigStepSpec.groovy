package com.fractalx.core.generator.observability

import com.fractalx.core.generator.GenerationContext
import com.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that OtelConfigStep generates a correctly structured OtelConfig.java
 * per service with the expected OpenTelemetry SDK wiring.
 *
 * Generated package: com.fractalx.generated.<serviceNameLower>
 * (e.g., "order-service" → "com.fractalx.generated.orderservice")
 */
class OtelConfigStepSpec extends Specification {

    @TempDir
    Path serviceRoot

    OtelConfigStep step = new OtelConfigStep()

    FractalModule module = FractalModule.builder()
        .serviceName("order-service")
        .packageName("com.example.order")
        .port(8081)
        .build()

    private GenerationContext ctx(FractalModule m) {
        Files.createDirectories(serviceRoot.resolve("src/main/java"))
        Files.createDirectories(serviceRoot.resolve("src/main/resources"))
        new GenerationContext(m, serviceRoot, serviceRoot, [m])
    }

    /** OtelConfigStep generates into com.fractalx.generated.<serviceNameLower> */
    private Path otelConfigFile() {
        serviceRoot.resolve("src/main/java/com/fractalx/generated/orderservice/OtelConfig.java")
    }

    private String content() { Files.readString(otelConfigFile()) }

    def "OtelConfig.java is created in the com.fractalx.generated package"() {
        when:
        step.generate(ctx(module))

        then:
        Files.exists(otelConfigFile())
    }

    def "OtelConfig is a @Configuration class in the generated package"() {
        when:
        step.generate(ctx(module))

        then:
        def c = content()
        c.contains("package com.fractalx.generated.orderservice")
        c.contains("@Configuration")
        c.contains("public class OtelConfig")
    }

    def "OtelConfig declares a @Bean that returns OpenTelemetry"() {
        when:
        step.generate(ctx(module))

        then:
        def c = content()
        c.contains("@Bean")
        c.contains("OpenTelemetry")
    }

    def "OtelConfig reads the OTLP endpoint from fractalx.observability.otel.endpoint"() {
        when:
        step.generate(ctx(module))

        then:
        content().contains("fractalx.observability.otel.endpoint")
    }

    def "OtelConfig uses OtlpGrpcSpanExporter for exporting spans"() {
        when:
        step.generate(ctx(module))

        then:
        content().contains("OtlpGrpcSpanExporter")
    }

    def "OtelConfig configures BatchSpanProcessor"() {
        when:
        step.generate(ctx(module))

        then:
        content().contains("BatchSpanProcessor")
    }

    def "OtelConfig sets SERVICE_NAME resource attribute from the spring application name"() {
        when:
        step.generate(ctx(module))

        then:
        def c = content()
        c.contains("spring.application.name")
        c.contains("SERVICE_NAME")
    }

    def "OtelConfig registers W3CTraceContextPropagator for inter-service trace propagation"() {
        when:
        step.generate(ctx(module))

        then:
        content().contains("W3CTraceContextPropagator")
    }

    def "OtelConfig calls buildAndRegisterGlobal so the SDK is globally accessible"() {
        when:
        step.generate(ctx(module))

        then:
        content().contains("buildAndRegisterGlobal")
    }
}
