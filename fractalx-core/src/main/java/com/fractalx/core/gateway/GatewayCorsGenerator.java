package com.fractalx.core.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates the CORS global configuration for the API Gateway. */
public class GatewayCorsGenerator {

    private static final Logger log = LoggerFactory.getLogger(GatewayCorsGenerator.class);

    public void generate(Path srcMainJava) throws IOException {
        Path pkg = createPkg(srcMainJava, "com/fractalx/gateway/cors");

        String content = """
                package com.fractalx.gateway.cors;

                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.web.cors.CorsConfiguration;
                import org.springframework.web.cors.reactive.CorsWebFilter;
                import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

                import java.util.Arrays;
                import java.util.List;

                /**
                 * Global CORS filter for the generated API Gateway.
                 *
                 * Configure via:
                 * <pre>
                 * fractalx:
                 *   gateway:
                 *     cors:
                 *       allowed-origins: http://localhost:3000,http://localhost:4200
                 *       allowed-methods: GET,POST,PUT,DELETE,PATCH,OPTIONS
                 *       allow-credentials: true
                 *       max-age: 3600
                 * </pre>
                 */
                @Configuration
                public class GatewayCorsConfig {

                    @Value("${fractalx.gateway.cors.allowed-origins:http://localhost:3000,http://localhost:4200,http://localhost:8080}")
                    private String allowedOriginsRaw;

                    @Value("${fractalx.gateway.cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
                    private String allowedMethodsRaw;

                    @Value("${fractalx.gateway.cors.allow-credentials:true}")
                    private boolean allowCredentials;

                    @Value("${fractalx.gateway.cors.max-age:3600}")
                    private long maxAge;

                    @Bean
                    public CorsWebFilter corsWebFilter() {
                        CorsConfiguration config = new CorsConfiguration();
                        config.setAllowedOrigins(Arrays.asList(allowedOriginsRaw.split(",")));
                        config.setAllowedMethods(Arrays.asList(allowedMethodsRaw.split(",")));
                        config.setAllowedHeaders(List.of("*"));
                        config.setExposedHeaders(List.of(
                                "X-Request-Id", "X-Correlation-Id", "X-Auth-Method",
                                "X-RateLimit-Limit", "X-RateLimit-Remaining",
                                "Location", "Content-Disposition"));
                        config.setAllowCredentials(allowCredentials);
                        config.setMaxAge(maxAge);

                        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                        source.registerCorsConfiguration("/**", config);
                        return new CorsWebFilter(source);
                    }
                }
                """;
        Files.writeString(pkg.resolve("GatewayCorsConfig.java"), content);
        log.info("Generated gateway CORS config");
    }

    private Path createPkg(Path base, String pkg) throws IOException {
        Path p = base;
        for (String part : pkg.split("/")) p = p.resolve(part);
        Files.createDirectories(p);
        return p;
    }
}
