package org.fractalx.core.datamanagement

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that SagaAnalyzer:
 *  - detects @DistributedSaga-annotated methods and produces SagaDefinition objects
 *  - correctly extracts sagaId, compensationMethod, timeout, and description
 *  - identifies ordered cross-module call steps within the saga method body
 *  - deduplicates repeated calls to the same beanType#method combination
 *  - assigns the owner service based on the class package
 *  - returns an empty list when no @DistributedSaga annotations are found
 *  - skips sagas with a blank sagaId (logs a warning instead)
 */
class SagaAnalyzerSpec extends Specification {

    @TempDir
    Path sourceRoot

    SagaAnalyzer analyzer = new SagaAnalyzer()

    // ── helpers ──────────────────────────────────────────────────────────────

    private void write(String relativePath, String content) {
        Path file = sourceRoot.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    private FractalModule orderModule() {
        FractalModule.builder()
            .serviceName("order-service")
            .packageName("org.fractalx.test.order")
            .port(8081)
            .dependencies(["PaymentService", "InventoryService"])
            .build()
    }

    private FractalModule paymentModule() {
        FractalModule.builder()
            .serviceName("payment-service")
            .packageName("org.fractalx.test.payment")
            .port(8082)
            .build()
    }

    private FractalModule inventoryModule() {
        FractalModule.builder()
            .serviceName("inventory-service")
            .packageName("org.fractalx.test.inventory")
            .port(8083)
            .build()
    }

    // ── feature methods ───────────────────────────────────────────────────────

    def "detects @DistributedSaga annotation and returns a SagaDefinition"() {
        given:
        write("org/fractalx/test/order/OrderService.java", """
            package org.fractalx.test.order;
            import org.fractalx.annotations.DistributedSaga;
            public class OrderService {
                private PaymentService paymentService;
                @DistributedSaga(sagaId = "place-order-saga", compensationMethod = "cancelOrder")
                public void placeOrder(String customerId) {
                    paymentService.processPayment(customerId, 100.0);
                }
                public void cancelOrder(String customerId) {}
            }
        """)

        when:
        def sagas = analyzer.analyzeSagas(sourceRoot, [orderModule(), paymentModule()])

        then:
        sagas.size() == 1
        sagas[0].sagaId == "place-order-saga"
    }

    def "extracts sagaId, compensationMethod, timeout, and description from annotation"() {
        given:
        write("org/fractalx/test/order/OrderService.java", """
            package org.fractalx.test.order;
            import org.fractalx.annotations.DistributedSaga;
            public class OrderService {
                private PaymentService paymentService;
                @DistributedSaga(
                    sagaId = "checkout-saga",
                    compensationMethod = "rollbackCheckout",
                    timeout = 60000,
                    description = "End-to-end checkout flow"
                )
                public void checkout(String customerId) {
                    paymentService.processPayment(customerId, 50.0);
                }
                public void rollbackCheckout(String customerId) {}
            }
        """)

        when:
        def sagas = analyzer.analyzeSagas(sourceRoot, [orderModule(), paymentModule()])

        then:
        def saga = sagas[0]
        saga.sagaId            == "checkout-saga"
        saga.compensationMethod == "rollbackCheckout"
        saga.timeoutMs         == 60000L
        saga.description       == "End-to-end checkout flow"
    }

    def "detects cross-module calls as saga steps in source order"() {
        given:
        write("org/fractalx/test/order/OrderService.java", """
            package org.fractalx.test.order;
            import org.fractalx.annotations.DistributedSaga;
            public class OrderService {
                private InventoryService inventoryService;
                private PaymentService paymentService;
                @DistributedSaga(sagaId = "place-order-saga", compensationMethod = "cancel")
                public void placeOrder(String id) {
                    inventoryService.reserveStock(id, 1);
                    paymentService.processPayment(id, 99.0);
                }
                public void cancel(String id) {}
            }
        """)

        when:
        def sagas = analyzer.analyzeSagas(
            sourceRoot, [orderModule(), paymentModule(), inventoryModule()])

        then:
        def steps = sagas[0].steps
        steps.size() == 2
        steps[0].beanType      == "InventoryService"
        steps[0].targetServiceName == "inventory-service"
        steps[0].methodName    == "reserveStock"
        steps[1].beanType      == "PaymentService"
        steps[1].targetServiceName == "payment-service"
        steps[1].methodName    == "processPayment"
    }

    def "repeated calls to the same beanType+method are deduplicated"() {
        given:
        write("org/fractalx/test/order/OrderService.java", """
            package org.fractalx.test.order;
            import org.fractalx.annotations.DistributedSaga;
            public class OrderService {
                private PaymentService paymentService;
                @DistributedSaga(sagaId = "pay-saga", compensationMethod = "cancel")
                public void pay(String id) {
                    paymentService.processPayment(id, 10.0);
                    paymentService.processPayment(id, 20.0); // duplicate call — must be deduped
                }
                public void cancel(String id) {}
            }
        """)

        when:
        def sagas = analyzer.analyzeSagas(sourceRoot, [orderModule(), paymentModule()])

        then: "only one step for processPayment"
        sagas[0].steps.size() == 1
        sagas[0].steps[0].methodName == "processPayment"
    }

    def "returns empty list when no @DistributedSaga annotations exist in source"() {
        given:
        write("org/fractalx/test/order/OrderService.java", """
            package org.fractalx.test.order;
            public class OrderService {
                public void placeOrder(String id) {}
            }
        """)

        when:
        def sagas = analyzer.analyzeSagas(sourceRoot, [orderModule()])

        then:
        sagas.isEmpty()
    }

    def "saga with blank sagaId is skipped and not included in results"() {
        given: "annotation with an empty sagaId"
        write("org/fractalx/test/order/OrderService.java", """
            package org.fractalx.test.order;
            import org.fractalx.annotations.DistributedSaga;
            public class OrderService {
                @DistributedSaga(sagaId = "")
                public void placeOrder() {}
            }
        """)

        when:
        def sagas = analyzer.analyzeSagas(sourceRoot, [orderModule()])

        then:
        sagas.isEmpty()
    }

    def "owner service is resolved from the class package"() {
        given:
        write("org/fractalx/test/order/OrderService.java", """
            package org.fractalx.test.order;
            import org.fractalx.annotations.DistributedSaga;
            public class OrderService {
                private PaymentService paymentService;
                @DistributedSaga(sagaId = "place-order-saga", compensationMethod = "cancel")
                public void placeOrder(String id) {
                    paymentService.processPayment(id, 100.0);
                }
                public void cancel(String id) {}
            }
        """)

        when:
        def sagas = analyzer.analyzeSagas(sourceRoot, [orderModule(), paymentModule()])

        then:
        sagas[0].ownerServiceName == "order-service"
    }

    def "compensation method is resolved from actual target bean methods (cancel prefix)"() {
        given: "PaymentService declares cancelProcessPayment — the analyzer should find it"
        write("org/fractalx/test/payment/PaymentService.java", """
            package org.fractalx.test.payment;
            public class PaymentService {
                public void processPayment(String id, double amount) {}
                public void cancelProcessPayment(String id, double amount) {}
            }
        """)
        write("org/fractalx/test/order/OrderService.java", """
            package org.fractalx.test.order;
            import org.fractalx.annotations.DistributedSaga;
            public class OrderService {
                private PaymentService paymentService;
                @DistributedSaga(sagaId = "pay-saga", compensationMethod = "cancelOrder")
                public void placeOrder(String id) {
                    paymentService.processPayment(id, 99.0);
                }
                public void cancelOrder(String id) {}
            }
        """)

        when:
        def sagas = analyzer.analyzeSagas(sourceRoot, [orderModule(), paymentModule()])

        then:
        def step = sagas[0].steps[0]
        step.methodName             == "processPayment"
        step.compensationMethodName == "cancelProcessPayment"
        step.hasCompensation()
    }

    def "compensation method is resolved via alternative prefixes (refund prefix)"() {
        given: "PaymentService uses refund* — analyzer should prefer it over cancel*"
        write("org/fractalx/test/payment/PaymentService.java", """
            package org.fractalx.test.payment;
            public class PaymentService {
                public void processPayment(String id, double amount) {}
                public void refundProcessPayment(String id, double amount) {}
            }
        """)
        write("org/fractalx/test/order/OrderService.java", """
            package org.fractalx.test.order;
            import org.fractalx.annotations.DistributedSaga;
            public class OrderService {
                private PaymentService paymentService;
                @DistributedSaga(sagaId = "pay-saga", compensationMethod = "cancelOrder")
                public void placeOrder(String id) {
                    paymentService.processPayment(id, 99.0);
                }
                public void cancelOrder(String id) {}
            }
        """)

        when:
        def sagas = analyzer.analyzeSagas(sourceRoot, [orderModule(), paymentModule()])

        then:
        def step = sagas[0].steps[0]
        step.compensationMethodName == "refundProcessPayment"
        step.hasCompensation()
    }

    def "compensation is empty and hasCompensation false when no matching method exists on target bean"() {
        given: "InventoryService has no cancel/rollback/etc. method for getProduct"
        write("org/fractalx/test/inventory/InventoryService.java", """
            package org.fractalx.test.inventory;
            public class InventoryService {
                public void getProduct(String id) {}
            }
        """)
        write("org/fractalx/test/order/OrderService.java", """
            package org.fractalx.test.order;
            import org.fractalx.annotations.DistributedSaga;
            public class OrderService {
                private InventoryService inventoryService;
                @DistributedSaga(sagaId = "order-saga", compensationMethod = "cancel")
                public void placeOrder(String id) {
                    inventoryService.getProduct(id);
                }
                public void cancel(String id) {}
            }
        """)

        when:
        def sagas = analyzer.analyzeSagas(sourceRoot, [orderModule(), inventoryModule()])

        then:
        def step = sagas[0].steps[0]
        step.compensationMethodName == ""
        !step.hasCompensation()
    }

    def "ownerClassName is the simple name of the annotated class"() {
        given:
        write("org/fractalx/test/order/OrderService.java", """
            package org.fractalx.test.order;
            import org.fractalx.annotations.DistributedSaga;
            public class OrderService {
                private PaymentService paymentService;
                @DistributedSaga(sagaId = "place-order-saga", compensationMethod = "cancel")
                public void placeOrder(String id) {
                    paymentService.processPayment(id, 100.0);
                }
                public void cancel(String id) {}
            }
        """)

        when:
        def sagas = analyzer.analyzeSagas(sourceRoot, [orderModule(), paymentModule()])

        then:
        sagas[0].ownerClassName == "OrderService"
        sagas[0].methodName     == "placeOrder"
    }
}
