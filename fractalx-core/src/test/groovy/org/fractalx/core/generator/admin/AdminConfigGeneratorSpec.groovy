package org.fractalx.core.generator.admin

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that AdminConfigGenerator produces:
 *   - application.yml — admin service wiring (port 9090, Jaeger, logger-service URL, registry)
 *   - alerting.yml    — 3 default alert rules + 4 notification channels (admin-ui, webhook, email, slack)
 */
class AdminConfigGeneratorSpec extends Specification {

    @TempDir
    Path resourcesDir

    AdminConfigGenerator generator = new AdminConfigGenerator()

    private String appYml()      { Files.readString(resourcesDir.resolve("application.yml")) }
    private String alertingYml() { Files.readString(resourcesDir.resolve("alerting.yml")) }

    // -------------------------------------------------------------------------
    // File existence
    // -------------------------------------------------------------------------

    def "application.yml is created"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        Files.exists(resourcesDir.resolve("application.yml"))
    }

    def "alerting.yml is created"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        Files.exists(resourcesDir.resolve("alerting.yml"))
    }

    // -------------------------------------------------------------------------
    // application.yml — service identity
    // -------------------------------------------------------------------------

    def "application.yml sets spring application name to admin-service on port 9090"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        def c = appYml()
        c.contains("name: admin-service")
        c.contains("port: 9090")
    }

    def "application.yml disables Thymeleaf template cache"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        appYml().contains("cache: false")
    }

    // -------------------------------------------------------------------------
    // application.yml — fractalx URLs
    // -------------------------------------------------------------------------

    def "application.yml includes fractalx.registry.url defaulting to localhost:8761"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        def c = appYml()
        c.contains("FRACTALX_REGISTRY_URL")
        c.contains("http://localhost:8761")
    }

    def "application.yml includes Jaeger query-url with JAEGER_QUERY_URL env var"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        def c = appYml()
        c.contains("jaeger:")
        c.contains("query-url:")
        c.contains("JAEGER_QUERY_URL")
        c.contains("http://localhost:16686")
    }

    def "application.yml includes logger-url pointing to logger-service on port 9099"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        def c = appYml()
        c.contains("logger-url:")
        c.contains("FRACTALX_LOGGER_URL")
        c.contains("http://localhost:9099")
    }

    def "application.yml exposes health, info, and metrics actuator endpoints"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        appYml().contains("include: health,info,metrics")
    }

    // -------------------------------------------------------------------------
    // alerting.yml — top-level
    // -------------------------------------------------------------------------

    def "alerting.yml has alerting enabled with 30-second eval interval"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        def c = alertingYml()
        c.contains("enabled: true")
        c.contains("eval-interval-ms: 30000")
    }

    // -------------------------------------------------------------------------
    // alerting.yml — alert rules
    // -------------------------------------------------------------------------

    def "alerting.yml defines service-down rule as CRITICAL requiring 2 consecutive failures"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        def c = alertingYml()
        c.contains("name: service-down")
        c.contains("condition: health")
        c.contains("threshold: 1")
        c.contains("severity: CRITICAL")
        c.contains("consecutive-failures: 2")
    }

    def "alerting.yml defines high-response-time rule as WARNING with 2000ms threshold"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        def c = alertingYml()
        c.contains("name: high-response-time")
        c.contains("condition: response-time")
        c.contains("threshold: 2000")
        c.contains("severity: WARNING")
    }

    def "alerting.yml defines error-rate rule as WARNING with 10% threshold"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        def c = alertingYml()
        c.contains("name: error-rate")
        c.contains("condition: error-rate")
        c.contains("threshold: 10")
        c.contains("severity: WARNING")
        c.contains("consecutive-failures: 3")
    }

    def "alerting.yml has exactly three alert rules defined"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        alertingYml().count("- name:") == 3
    }

    // -------------------------------------------------------------------------
    // alerting.yml — notification channels
    // -------------------------------------------------------------------------

    def "alerting.yml enables admin-ui channel by default"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        def c = alertingYml()
        c.contains("admin-ui:")
        // admin-ui block appears before the first disabled channel
        c.indexOf("admin-ui:") < c.indexOf("enabled: false")
    }

    def "alerting.yml defines webhook channel (disabled) with ALERT_WEBHOOK_URL env var"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        def c = alertingYml()
        c.contains("webhook:")
        c.contains("ALERT_WEBHOOK_URL")
    }

    def "alerting.yml defines email channel (disabled) with SMTP env vars"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        def c = alertingYml()
        c.contains("email:")
        c.contains("SMTP_HOST")
        c.contains("SMTP_PORT")
        c.contains("SMTP_FROM")
        c.contains("ALERT_EMAIL_TO")
    }

    def "alerting.yml defines slack channel (disabled) with SLACK_WEBHOOK_URL env var"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        def c = alertingYml()
        c.contains("slack:")
        c.contains("SLACK_WEBHOOK_URL")
    }

    def "webhook, email, and slack channels are all disabled by default"() {
        when:
        generator.generate(resourcesDir, null)

        then:
        // Three 'enabled: false' lines — one per channel (webhook, email, slack)
        alertingYml().count("enabled: false") == 3
    }
}
