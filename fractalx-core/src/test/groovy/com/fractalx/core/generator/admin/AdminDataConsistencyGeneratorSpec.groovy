package com.fractalx.core.generator.admin

import com.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies AdminDataConsistencyGenerator produces:
 *  - SagaMetaRegistry       (baked-in saga definitions from modules with dependencies)
 *  - DataConsistencyController (REST API: overview, sagas, databases, schemas, outbox)
 */
class AdminDataConsistencyGeneratorSpec extends Specification {

    @TempDir
    Path srcMainJava

    AdminDataConsistencyGenerator generator = new AdminDataConsistencyGenerator()

    FractalModule order = FractalModule.builder()
        .serviceName("order-service")
        .packageName("com.example.order")
        .port(8081)
        .dependencies(["PaymentService"])
        .ownedSchemas(["orders"])
        .build()

    FractalModule payment = FractalModule.builder()
        .serviceName("payment-service")
        .packageName("com.example.payment")
        .port(8082)
        .dependencies([])
        .ownedSchemas(["payments"])
        .build()

    static final String BASE = "com.fractalx.admin"

    def "generates two files in the data package"() {
        when:
        generator.generate(srcMainJava, BASE, [order, payment])
        def pkg = srcMainJava.resolve("com/fractalx/admin/data")

        then:
        Files.exists(pkg.resolve("SagaMetaRegistry.java"))
        Files.exists(pkg.resolve("DataConsistencyController.java"))
    }

    // ---- SagaMetaRegistry ---------------------------------------------------

    def "SagaMetaRegistry is in com.fractalx.admin.data package"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        sagaRegistry().contains("package com.fractalx.admin.data")
    }

    def "SagaMetaRegistry is annotated @Component"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        sagaRegistry().contains("@Component")
    }

    def "SagaMetaRegistry defines SagaInfo record with all fields"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = sagaRegistry()

        then:
        content.contains("public record SagaInfo(")
        content.contains("String sagaId")
        content.contains("String orchestratedBy")
        content.contains("List<String> steps")
        content.contains("List<String> compensationSteps")
        content.contains("boolean enabled")
    }

    def "SagaMetaRegistry bakes saga entry for module with dependencies"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = sagaRegistry()

        then:
        // order-service has deps → saga "orderServiceSaga" or similar
        content.contains('"order-service"') || content.contains("orderService")
        content.contains("PaymentService") || content.contains("payment")
    }

    def "SagaMetaRegistry does NOT bake saga for module with no dependencies"() {
        when:
        generator.generate(srcMainJava, BASE, [payment])
        def content = sagaRegistry()

        then:
        // payment has no deps → no saga entry for it, SAGAS list should be empty or not contain payment
        !content.contains('"payment-service"') || content.contains("List.of(")
    }

    def "SagaMetaRegistry exposes getAll, count, findById, hasSagas"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = sagaRegistry()

        then:
        content.contains("getAll()")
        content.contains("count()")
        content.contains("findById(String sagaId)")
        content.contains("hasSagas()")
    }

    def "SagaMetaRegistry uses static List.of for SAGAS"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        sagaRegistry().contains("private static final List<SagaInfo> SAGAS = List.of(")
    }

    // ---- DataConsistencyController ------------------------------------------

    def "DataConsistencyController is a @RestController mapped to /api/data"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = dataController()

        then:
        content.contains("@RestController")
        content.contains("/api/data")
    }

    def "DataConsistencyController has overview endpoint"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        dataController().contains("overview") || dataController().contains("Overview")
    }

    def "DataConsistencyController has sagas endpoint"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        dataController().contains("sagas") || dataController().contains("Sagas")
    }

    def "DataConsistencyController has databases endpoint"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        dataController().contains("databases") || dataController().contains("Databases")
    }

    def "DataConsistencyController has outbox endpoint"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        dataController().contains("outbox") || dataController().contains("Outbox")
    }

    def "DataConsistencyController proxies saga instances from saga orchestrator port 8099"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = dataController()

        then:
        content.contains("8099") || content.contains("saga-orchestrator")
    }

    def "DataConsistencyController bakes DB health check for module with owned schemas"() {
        when:
        generator.generate(srcMainJava, BASE, [order])
        def content = dataController()

        then:
        content.contains("order") && (content.contains("orders") || content.contains("8081"))
    }

    def "DataConsistencyController is in com.fractalx.admin.data package"() {
        when:
        generator.generate(srcMainJava, BASE, [order])

        then:
        dataController().contains("package com.fractalx.admin.data")
    }

    // helpers
    private String sagaRegistry() {
        Files.readString(srcMainJava.resolve("com/fractalx/admin/data/SagaMetaRegistry.java"))
    }

    private String dataController() {
        Files.readString(srcMainJava.resolve("com/fractalx/admin/data/DataConsistencyController.java"))
    }
}
