package org.fractalx.core.generator.observability;

import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pipeline step that generates an {@code OtelConfig} Spring {@code @Configuration} class
 * in each service's generated package.
 *
 * <p>The generated class creates an {@link io.opentelemetry.sdk.OpenTelemetrySdk} bean that:
 * <ul>
 *   <li>Exports spans via OTLP/gRPC to the configured endpoint (default: Jaeger on :4317)</li>
 *   <li>Propagates W3C {@code traceparent} + {@code baggage} headers across service calls</li>
 *   <li>Tags every span with the service name via {@code ResourceAttributes.SERVICE_NAME}</li>
 * </ul>
 *
 * <p>Configuration property: {@code fractalx.observability.otel.endpoint}
 * (env: {@code OTEL_EXPORTER_OTLP_ENDPOINT})
 */
public class OtelConfigStep implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(OtelConfigStep.class);

    @Override
    public void generate(GenerationContext context) throws IOException {
        FractalModule module = context.getModule();
        log.debug("Generating OtelConfig for {}", module.getServiceName());

        String pkg     = "org.fractalx.generated." + toJavaId(module.getServiceName()).toLowerCase();
        Path   pkgPath = resolvePackage(context.getSrcMainJava(), pkg);

        Files.writeString(pkgPath.resolve("OtelConfig.java"), buildContent(pkg));
        log.debug("Generated OtelConfig for {}", module.getServiceName());
    }

    private String buildContent(String pkg) {
        return """
                package %s;

                import io.opentelemetry.api.OpenTelemetry;
                import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
                import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
                import io.opentelemetry.context.propagation.ContextPropagators;
                import io.opentelemetry.context.propagation.TextMapPropagator;
                import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
                import io.opentelemetry.sdk.OpenTelemetrySdk;
                import io.opentelemetry.sdk.resources.Resource;
                import io.opentelemetry.sdk.trace.SdkTracerProvider;
                import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
                import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
                import io.opentelemetry.api.common.Attributes;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                /**
                 * OpenTelemetry SDK configuration for this service.
                 *
                 * <p>Spans are exported via OTLP/gRPC to Jaeger (or any compatible OTLP collector).
                 * Configure the endpoint with:
                 * <pre>
                 * fractalx:
                 *   observability:
                 *     otel:
                 *       endpoint: http://jaeger:4317
                 * </pre>
                 * or via environment variable {@code OTEL_EXPORTER_OTLP_ENDPOINT}.
                 */
                @Configuration
                public class OtelConfig {

                    @Value("${spring.application.name}")
                    private String serviceName;

                    @Bean
                    public OpenTelemetry openTelemetry(
                            @Value("${fractalx.observability.otel.endpoint:http://localhost:4317}") String endpoint) {

                        Resource resource = Resource.getDefault()
                                .merge(Resource.create(Attributes.of(
                                        ResourceAttributes.SERVICE_NAME, serviceName)));

                        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                                .setEndpoint(endpoint)
                                .build();

                        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                                .setResource(resource)
                                .build();

                        return OpenTelemetrySdk.builder()
                                .setTracerProvider(tracerProvider)
                                .setPropagators(ContextPropagators.create(
                                        TextMapPropagator.composite(
                                                W3CTraceContextPropagator.getInstance(),
                                                W3CBaggagePropagator.getInstance())))
                                .buildAndRegisterGlobal();
                    }
                }
                """.formatted(pkg);
    }

    private Path resolvePackage(Path base, String pkg) throws IOException {
        Path p = base;
        for (String part : pkg.split("\\.")) p = p.resolve(part);
        Files.createDirectories(p);
        return p;
    }

    private String toJavaId(String serviceName) {
        StringBuilder sb = new StringBuilder();
        for (String part : serviceName.split("-")) {
            if (!part.isEmpty())
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
