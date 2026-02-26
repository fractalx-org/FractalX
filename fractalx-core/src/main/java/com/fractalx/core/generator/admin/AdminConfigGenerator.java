package com.fractalx.core.generator.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates application.yml and alerting.yml for the admin service. */
class AdminConfigGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminConfigGenerator.class);

    void generate(Path srcMainResources) throws IOException {
        generateApplicationYml(srcMainResources);
        generateAlertingYml(srcMainResources);
        log.debug("Generated admin application.yml and alerting.yml");
    }

    private void generateApplicationYml(Path srcMainResources) throws IOException {
        String content = """
                spring:
                  application:
                    name: admin-service
                  thymeleaf:
                    cache: false

                server:
                  port: 9090

                fractalx:
                  registry:
                    url: ${FRACTALX_REGISTRY_URL:http://localhost:8761}
                  observability:
                    jaeger:
                      query-url: ${JAEGER_QUERY_URL:http://localhost:16686}
                    logger-url: ${FRACTALX_LOGGER_URL:http://localhost:9099}

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
    }

    private void generateAlertingYml(Path srcMainResources) throws IOException {
        String content = """
                # FractalX Alert Manager Configuration
                # All channels are disabled by default — enable selectively.
                fractalx:
                  alerting:
                    enabled: true
                    eval-interval-ms: 30000
                    rules:
                      - name: service-down
                        condition: health
                        threshold: 1
                        severity: CRITICAL
                        enabled: true
                        consecutive-failures: 2
                      - name: high-response-time
                        condition: response-time
                        threshold: 2000
                        severity: WARNING
                        enabled: true
                        consecutive-failures: 3
                      - name: error-rate
                        condition: error-rate
                        threshold: 10
                        severity: WARNING
                        enabled: true
                        consecutive-failures: 3
                    channels:
                      admin-ui:
                        enabled: true
                      webhook:
                        enabled: false
                        url: ${ALERT_WEBHOOK_URL:}
                      email:
                        enabled: false
                        smtp-host: ${SMTP_HOST:}
                        smtp-port: ${SMTP_PORT:587}
                        from: ${SMTP_FROM:}
                        to: ${ALERT_EMAIL_TO:}
                      slack:
                        enabled: false
                        webhook-url: ${SLACK_WEBHOOK_URL:}
                """;
        Files.writeString(srcMainResources.resolve("alerting.yml"), content);
    }
}
