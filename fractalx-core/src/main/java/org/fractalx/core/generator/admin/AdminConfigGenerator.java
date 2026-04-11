package org.fractalx.core.generator.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates application.yml and alerting.yml for the admin service. */
class AdminConfigGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminConfigGenerator.class);

    /**
     * Generates config files, optionally baking in database connection details.
     *
     * @param srcMainResources target resources directory
     * @param db               admin DB config read from {@code fractalx-config.yml}, or
     *                         {@code null} to generate env-var placeholders instead
     */
    void generate(Path srcMainResources, AdminDbConfig db) throws IOException {
        generateApplicationYml(srcMainResources);
        generateApplicationDbYml(srcMainResources, db);
        generateAlertingYml(srcMainResources);
        log.debug("Generated admin application.yml, application-db.yml, and alerting.yml");
    }

    private void generateApplicationYml(Path srcMainResources) throws IOException {
        String content = """
                spring:
                  application:
                    name: admin-service
                  thymeleaf:
                    cache: false
                  # ── Default (memory) mode: disable JPA/DataSource/Flyway autoconfiguration ──
                  # Activate db mode with: -Dspring.profiles.active=db (see application-db.yml)
                  autoconfigure:
                    exclude:
                      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
                      - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
                      - org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
                      - org.springframework.cloud.autoconfigure.LifecycleMvcEndpointAutoConfiguration
                      # FractalX services use gRPC/NetScope, not Feign. Excluding prevents startup failure
                      # when spring-cloud-context is not on the classpath (e.g. Spring Boot 4.x).
                      - org.springframework.cloud.openfeign.FeignAutoConfiguration
                  data:
                    jpa:
                      repositories:
                        enabled: false

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
                    org.fractalx.admin: DEBUG
                """;
        Files.writeString(srcMainResources.resolve("application.yml"), content);
    }

    private void generateApplicationDbYml(Path srcMainResources, AdminDbConfig db) throws IOException {
        // Datasource values: baked from fractalx-config.yml when available, else env-var placeholders
        final String sourceNote;
        final String urlVal;
        final String userVal;
        final String passVal;
        final String driverVal;

        if (db != null) {
            sourceNote = "# Source: fractalx-config.yml → fractalx.admin.datasource\n";
            urlVal     = db.url();
            userVal    = db.username() != null ? db.username() : "";
            passVal    = db.password() != null ? db.password() : "";
            driverVal  = db.driverClassName() != null ? db.driverClassName() : "org.h2.Driver";
        } else {
            sourceNote = "";
            urlVal     = "${ADMIN_DB_URL:jdbc:h2:./admin-service;DB_CLOSE_DELAY=-1;MODE=MySQL}";
            userVal    = "${ADMIN_DB_USERNAME:sa}";
            passVal    = "${ADMIN_DB_PASSWORD:}";
            driverVal  = "${ADMIN_DB_DRIVER:org.h2.Driver}";
        }

        String content = "# ================================================================\n"
                + "# FractalX Admin Service — Database Profile\n"
                + "# ================================================================\n"
                + sourceNote
                + "# Activate: -Dspring.profiles.active=db\n"
                + "#        or: SPRING_PROFILES_ACTIVE=db  (environment variable)\n"
                + "#        or: add spring.profiles.active=db in application.yml\n"
                + "#\n"
                + "# Supported databases:\n"
                + "#   H2 (default, embedded — no setup required):\n"
                + "#     url: jdbc:h2:./admin-service;DB_CLOSE_DELAY=-1;MODE=MySQL\n"
                + "#\n"
                + "#   MySQL 8+:\n"
                + "#     url: jdbc:mysql://localhost:3306/admin_db?useSSL=false&serverTimezone=UTC\n"
                + "#     driver-class-name: com.mysql.cj.jdbc.Driver\n"
                + "#     Also uncomment mysql-connector-j + flyway-mysql in pom.xml\n"
                + "#\n"
                + "#   PostgreSQL 15+:\n"
                + "#     url: jdbc:postgresql://localhost:5432/admin_db\n"
                + "#     driver-class-name: org.postgresql.Driver\n"
                + "#     Also uncomment postgresql driver in pom.xml\n"
                + "# ================================================================\n\n"
                + "spring:\n"
                + "  autoconfigure:\n"
                + "    exclude: []   # re-enable DataSource + JPA + Flyway (overrides base config)\n"
                + "  data:\n"
                + "    jpa:\n"
                + "      repositories:\n"
                + "        enabled: true\n"
                + "  datasource:\n"
                + "    url: " + urlVal + "\n"
                + "    username: " + userVal + "\n"
                + "    password: " + passVal + "\n"
                + "    driver-class-name: " + driverVal + "\n"
                + "  jpa:\n"
                + "    hibernate:\n"
                + "      ddl-auto: validate\n"
                + "    show-sql: false\n"
                + "    open-in-view: false\n"
                + "  flyway:\n"
                + "    enabled: true\n"
                + "    locations: classpath:db/migration\n"
                + "    baseline-on-migrate: true   # safe for databases with pre-existing schema\n";

        Files.writeString(srcMainResources.resolve("application-db.yml"), content);
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
