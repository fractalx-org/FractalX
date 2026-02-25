package com.fractalx.core.generator.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates application.yml for the admin service. */
class AdminConfigGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminConfigGenerator.class);

    void generate(Path srcMainResources) throws IOException {
        String content = """
                spring:
                  application:
                    name: admin-service
                  thymeleaf:
                    cache: false

                server:
                  port: 9090

                management:
                  endpoints:
                    web:
                      exposure:
                        include: health,info,metrics

                logging:
                  level:
                    com.fractalx.admin: DEBUG
                """;
        Files.writeString(srcMainResources.resolve("application.yml"), content);
        log.debug("Generated admin application.yml");
    }
}
