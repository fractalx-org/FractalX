package org.fractalx.core.datamanagement

import org.fractalx.core.model.FractalModule
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that DbConfigurationGenerator:
 *  - reads per-service datasource config from fractalx-config.yml
 *  - falls back to a default H2 config when no custom config is found
 *  - extracts the driver class name from custom config entries
 *  - forces ddl-auto = update in all cases
 *  - merges config into application.yml without destroying pre-existing keys
 */
class DbConfigurationGeneratorSpec extends Specification {

    @TempDir
    Path tempRoot

    DbConfigurationGenerator generator = new DbConfigurationGenerator()

    // layout mirrors real usage:
    //   monolith source root  → tempRoot/src/main/java
    //   monolith resources    → tempRoot/src/main/resources   (fractalx-config.yml lives here)
    //   service resources     → tempRoot/generated/src/main/resources  (application.yml written here)

    Path sourceRoot          // tempRoot/src/main/java
    Path monolithResources   // tempRoot/src/main/resources
    Path serviceResources    // tempRoot/generated/src/main/resources

    def setup() {
        sourceRoot        = tempRoot.resolve("src/main/java")
        monolithResources = tempRoot.resolve("src/main/resources")
        serviceResources  = tempRoot.resolve("generated/src/main/resources")
        Files.createDirectories(sourceRoot)
        Files.createDirectories(monolithResources)
        Files.createDirectories(serviceResources)
    }

    private FractalModule orderModule() {
        FractalModule.builder()
            .serviceName("order-service")
            .packageName("org.fractalx.test.order")
            .port(8081)
            .build()
    }

    private Map<String, Object> readGeneratedYaml() {
        def file = serviceResources.resolve("application.yml")
        new Yaml().load(Files.newInputStream(file)) as Map<String, Object>
    }

    // ── feature methods ───────────────────────────────────────────────────────

    def "reads datasource config from fractalx-config.yml for the matching service"() {
        given:
        Files.writeString(monolithResources.resolve("fractalx-config.yml"), """
            fractalx:
              services:
                order-service:
                  datasource:
                    url: jdbc:h2:mem:order_test
                    username: sa
                    password: ""
                    driver-class-name: org.h2.Driver
        """)

        when:
        generator.generateDbConfig(orderModule(), sourceRoot, serviceResources)

        then:
        def yaml = readGeneratedYaml()
        def ds = (yaml.spring as Map)?.datasource as Map
        ds?.url == "jdbc:h2:mem:order_test"
        ds?.username == "sa"
    }

    def "falls back to default H2 config when fractalx-config.yml has no entry for the service"() {
        given: "config file exists but has no entry for order-service"
        Files.writeString(monolithResources.resolve("fractalx-config.yml"), """
            fractalx:
              services:
                payment-service:
                  datasource:
                    url: jdbc:h2:mem:payment_db
        """)

        when:
        generator.generateDbConfig(orderModule(), sourceRoot, serviceResources)

        then: "a default H2 url is generated from the service name"
        def yaml = readGeneratedYaml()
        def ds = (yaml.spring as Map)?.datasource as Map
        ds?.url?.toString()?.contains("order_service") ||
        ds?.url?.toString()?.contains("h2")
    }

    def "falls back to default H2 config when fractalx-config.yml does not exist"() {
        when: "no config file present at all"
        generator.generateDbConfig(orderModule(), sourceRoot, serviceResources)

        then:
        def yaml = readGeneratedYaml()
        def ds = (yaml.spring as Map)?.datasource as Map
        ds != null
        ds["driver-class-name"] == "org.h2.Driver"
    }

    def "ddl-auto is forced to 'update' regardless of config source"() {
        given:
        Files.writeString(monolithResources.resolve("fractalx-config.yml"), """
            fractalx:
              services:
                order-service:
                  datasource:
                    url: jdbc:postgresql://localhost:5432/order_db
                    driver-class-name: org.postgresql.Driver
                    username: postgres
                    password: secret
                  jpa:
                    hibernate:
                      ddl-auto: validate
        """)

        when:
        generator.generateDbConfig(orderModule(), sourceRoot, serviceResources)

        then:
        def yaml = readGeneratedYaml()
        def hibernate = ((yaml.spring as Map)?.jpa as Map)?.hibernate as Map
        hibernate?.get("ddl-auto") == "update"
    }

    def "driver class name is returned when present in custom config"() {
        given:
        Files.writeString(monolithResources.resolve("fractalx-config.yml"), """
            fractalx:
              services:
                order-service:
                  datasource:
                    url: jdbc:mysql://localhost:3306/orders
                    driver-class-name: com.mysql.cj.jdbc.Driver
                    username: root
                    password: ""
        """)

        when:
        def driverClass = generator.generateDbConfig(orderModule(), sourceRoot, serviceResources)

        then:
        driverClass == "com.mysql.cj.jdbc.Driver"
    }

    def "returns null driver class when service has no custom datasource config"() {
        when: "no fractalx-config.yml at all → default H2 path"
        def driverClass = generator.generateDbConfig(orderModule(), sourceRoot, serviceResources)

        then:
        driverClass == null
    }

    def "merges datasource into existing application.yml without removing pre-existing keys"() {
        given: "a pre-existing application.yml with a server.port key"
        Files.writeString(serviceResources.resolve("application.yml"), """
            server:
              port: 8081
        """)

        when:
        generator.generateDbConfig(orderModule(), sourceRoot, serviceResources)

        then: "server.port is preserved alongside the new spring.datasource"
        def yaml = readGeneratedYaml()
        (yaml.server as Map)?.port == 8081
        (yaml.spring as Map)?.datasource != null
    }
}
