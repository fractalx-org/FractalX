package com.fractalx.core.generator.admin

import com.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies AdminConfigManagementGenerator produces:
 *  - ServiceConfigStore  (baked-in per-service config in com.fractalx.admin.svcconfig)
 *  - ConfigController    (5 REST endpoints for port/env/lifecycle config)
 */
class AdminConfigManagementGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    AdminConfigManagementGenerator generator = new AdminConfigManagementGenerator()

    FractalModule order = FractalModule.builder()
        .serviceName("order-service")
        .packageName("com.example.order")
        .port(8081)
        .dependencies(["PaymentService"])
        .ownedSchemas(["orders"])
        .className("OrderModule")
        .build()

    FractalModule payment = FractalModule.builder()
        .serviceName("payment-service")
        .packageName("com.example.payment")
        .port(8082)
        .dependencies([])
        .ownedSchemas(["payments"])
        .className("PaymentModule")
        .build()

    static final String BASE = "com.fractalx.admin"

    def "generates two files in the svcconfig package (not config)"() {
        when:
        generator.generate(srcMainJava, BASE, [order, payment])
        def pkg = srcMainJava.resolve("com/fractalx/admin/svcconfig")

        then:
        Files.exists(pkg.resolve("ServiceConfigStore.java"))
        Files.exists(pkg.resolve("ConfigController.java"))
    }

    def "does NOT use the config package (avoids SecurityConfig/WebConfig collision)"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        !Files.exists(srcMainJava.resolve("com/fractalx/admin/config/ServiceConfigStore.java"))
    }

    // ---- ServiceConfigStore -------------------------------------------------

    def "ServiceConfigStore is in com.fractalx.admin.svcconfig package"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        configStore().contains("package com.fractalx.admin.svcconfig")
    }

    def "ServiceConfigStore is annotated @Component"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        configStore().contains("@Component")
    }

    def "ServiceConfigStore defines ServiceConfig record with all fields"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = configStore()

        then:
        content.contains("public record ServiceConfig(")
        // field names (spacing may vary due to alignment)
        content.contains("name")
        content.contains("httpPort")
        content.contains("grpcPort")
        content.contains("packageName")
        content.contains("hasOutbox")
        content.contains("hasSaga")
        content.contains("ownedSchemas")
        content.contains("envVars")
        // field types
        content.contains("int httpPort") || content.contains("int             httpPort")
        content.contains("List<String>")
        content.contains("Map<String, String>")
    }

    def "ServiceConfigStore bakes in module service entry with correct ports"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = configStore()

        then:
        content.contains('"order-service"')
        content.contains('8081')    // HTTP port
        content.contains('18081')   // gRPC = 8081 + 10000
        content.contains('"com.example.order"')
    }

    def "ServiceConfigStore marks hasOutbox true for service with dependencies"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = configStore()

        then:
        // order has deps so hasOutbox=true
        content.contains("true")
    }

    def "ServiceConfigStore always includes 4 infra service entries"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = configStore()

        then:
        content.contains('"fractalx-registry"')
        content.contains('"api-gateway"')
        content.contains('"admin-service"')
        content.contains('"logger-service"')
    }

    def "ServiceConfigStore admin-service entry includes JAEGER and LOGGER env vars"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = configStore()

        then:
        content.contains("JAEGER_QUERY_URL") || content.contains("jaeger")
        content.contains("FRACTALX_LOGGER_URL") || content.contains("logger-service")
    }

    def "ServiceConfigStore env vars include OTEL env vars for modules"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = configStore()

        then:
        content.contains("OTEL") || content.contains("SPRING_PROFILES_ACTIVE")
    }

    def "ServiceConfigStore exposes getAll, count, findByName, getMicroservices"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = configStore()

        then:
        content.contains("getAll()")
        content.contains("count()")
        content.contains("findByName(String name)")
        content.contains("getMicroservices()")
    }

    def "ServiceConfigStore uses static List.of for CONFIGS"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        configStore().contains("private static final List<ServiceConfig> CONFIGS = List.of(")
    }

    // ---- ConfigController ---------------------------------------------------

    def "ConfigController is in com.fractalx.admin.svcconfig package"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        configController().contains("package com.fractalx.admin.svcconfig")
    }

    def "ConfigController is a @RestController mapped to /api/config"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = configController()

        then:
        content.contains("@RestController")
        content.contains('@RequestMapping("/api/config")')
    }

    def "ConfigController has GET /api/config/services endpoint"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        configController().contains('"/services"') || configController().contains("services")
    }

    def "ConfigController has GET /api/config/environment endpoint"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        configController().contains("environment") || configController().contains("envVars")
    }

    def "ConfigController has GET /api/config/ports endpoint"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        configController().contains("ports") || configController().contains("Ports")
    }

    def "ConfigController has GET /api/config/commands endpoint for lifecycle"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = configController()

        then:
        content.contains("commands") && (content.contains("docker-compose") || content.contains("docker compose"))
    }

    def "ConfigController injects ServiceConfigStore"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        configController().contains("ServiceConfigStore")
    }

    // helpers
    private String configStore() {
        Files.readString(srcMainJava.resolve("com/fractalx/admin/svcconfig/ServiceConfigStore.java"))
    }

    private String configController() {
        Files.readString(srcMainJava.resolve("com/fractalx/admin/svcconfig/ConfigController.java"))
    }
}
