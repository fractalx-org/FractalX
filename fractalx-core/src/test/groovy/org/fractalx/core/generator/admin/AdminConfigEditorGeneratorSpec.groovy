package org.fractalx.core.generator.admin

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that AdminConfigEditorGenerator produces ConfigEditorController
 * in the org.fractalx.admin.svcconfig package with the expected REST endpoints
 * for reading configs, managing overrides, diffing, and hot-reloading.
 */
class AdminConfigEditorGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    AdminConfigEditorGenerator generator = new AdminConfigEditorGenerator()
    String basePackage = "org.fractalx.admin"

    FractalModule order = FractalModule.builder()
        .serviceName("order-service")
        .packageName("com.example.order")
        .port(8081)
        .build()

    FractalModule payment = FractalModule.builder()
        .serviceName("payment-service")
        .packageName("com.example.payment")
        .port(8082)
        .build()

    private Path svcConfigPkg() {
        srcMainJava.resolve("org/fractalx/admin/svcconfig")
    }

    private String readController() {
        Files.readString(svcConfigPkg().resolve("ConfigEditorController.java"))
    }

    def "ConfigEditorController.java is created in svcconfig package"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        Files.exists(svcConfigPkg().resolve("ConfigEditorController.java"))
    }

    def "ConfigEditorController is in org.fractalx.admin.svcconfig package"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        readController().contains("package org.fractalx.admin.svcconfig")
    }

    def "ConfigEditorController is a @RestController mapped to /api/config/editor"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = readController()
        c.contains("@RestController")
        c.contains("/api/config/editor")
    }

    def "ConfigEditorController exposes GET /all and GET /{name}"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = readController()
        c.contains("/all")
        c.contains("getAll") || c.contains("all(")
        c.contains("one(")   || c.contains("findByName")
    }

    def "ConfigEditorController exposes GET /overrides endpoint"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = readController()
        c.contains("/overrides")
        c.contains("overrides")
    }

    def "ConfigEditorController exposes POST /override to store key-value overrides"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = readController()
        c.contains("/override")
        c.contains("setOverride") || c.contains("override(")
        c.contains("service")
        c.contains("key")
        c.contains("value")
    }

    def "ConfigEditorController exposes DELETE /override/{service}/{key}"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = readController()
        c.contains("@DeleteMapping")
        c.contains("removeOverride") || c.contains("remove")
    }

    def "ConfigEditorController exposes GET /diff comparing overrides to base config"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = readController()
        c.contains("/diff")
        c.contains("diff(")
        c.contains("baseValue") || c.contains("newValue")
    }

    def "ConfigEditorController exposes POST /reload/{service} for hot-reload"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = readController()
        c.contains("/reload")
        c.contains("reload(")
        c.contains("actuator/refresh")
    }

    def "ConfigEditorController uses in-memory ConcurrentHashMap for overrides"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = readController()
        c.contains("ConcurrentHashMap")
        c.contains("overrides")
    }

    def "ConfigEditorController injects ServiceConfigStore"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        readController().contains("ServiceConfigStore")
    }

    def "ConfigEditorController sets connect and read timeouts on RestTemplate"() {
        when:
        generator.generate(srcMainJava, basePackage, [order])

        then:
        def c = readController()
        c.contains("ConnectTimeout") || c.contains("setConnectTimeout")
        c.contains("ReadTimeout")    || c.contains("setReadTimeout")
    }

    def "generator works with multiple modules"() {
        when:
        generator.generate(srcMainJava, basePackage, [order, payment])

        then:
        Files.exists(svcConfigPkg().resolve("ConfigEditorController.java"))
    }
}
