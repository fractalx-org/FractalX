package com.fractalx.core.datamanagement

import com.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that RepositoryAnalyzer correctly:
 *  - maps Spring Data repository interfaces to their owning modules
 *  - detects cross-boundary field and constructor injection violations
 *  - reports no violations when repos stay within their module
 */
class RepositoryAnalyzerSpec extends Specification {

    @TempDir
    Path sourceRoot

    RepositoryAnalyzer analyzer = new RepositoryAnalyzer()

    // ── helpers ──────────────────────────────────────────────────────────────

    private void write(String relativePath, String content) {
        Path file = sourceRoot.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    private FractalModule module(String serviceName, String packageName) {
        FractalModule.builder()
            .serviceName(serviceName)
            .packageName(packageName)
            .port(8081)
            .build()
    }

    // ── feature methods ───────────────────────────────────────────────────────

    def "discovers JpaRepository interface as owned by the matching module"() {
        given:
        write("com/fractalx/test/order/OrderRepository.java", """
            package com.fractalx.test.order;
            import org.springframework.data.jpa.repository.JpaRepository;
            public interface OrderRepository extends JpaRepository<Object, Long> {}
        """)
        def mod = module("order-service", "com.fractalx.test.order")

        when:
        def report = analyzer.analyze(sourceRoot, [mod])

        then:
        report.ownership["OrderRepository"] == "order-service"
        !report.hasViolations()
    }

    def "discovers CrudRepository interface as a Spring Data repository"() {
        given:
        write("com/fractalx/test/payment/PaymentRepository.java", """
            package com.fractalx.test.payment;
            import org.springframework.data.repository.CrudRepository;
            public interface PaymentRepository extends CrudRepository<Object, Long> {}
        """)
        def mod = module("payment-service", "com.fractalx.test.payment")

        when:
        def report = analyzer.analyze(sourceRoot, [mod])

        then:
        report.ownership["PaymentRepository"] == "payment-service"
    }

    def "reports violation when a repository is field-injected in a foreign module"() {
        given: "OrderRepository in order package"
        write("com/fractalx/test/order/OrderRepository.java", """
            package com.fractalx.test.order;
            import org.springframework.data.jpa.repository.JpaRepository;
            public interface OrderRepository extends JpaRepository<Object, Long> {}
        """)

        and: "PaymentService in payment package incorrectly injects OrderRepository"
        write("com/fractalx/test/payment/PaymentService.java", """
            package com.fractalx.test.payment;
            import com.fractalx.test.order.OrderRepository;
            public class PaymentService {
                private OrderRepository orderRepository;
            }
        """)

        def orderMod   = module("order-service",   "com.fractalx.test.order")
        def paymentMod = module("payment-service",  "com.fractalx.test.payment")

        when:
        def report = analyzer.analyze(sourceRoot, [orderMod, paymentMod])

        then:
        report.hasViolations()
        report.violations.any { v ->
            v.repositoryName == "OrderRepository" &&
            v.ownerModule    == "order-service"   &&
            v.usedInModule   == "payment-service"
        }
    }

    def "reports violation when a repository is constructor-injected in a foreign module"() {
        given:
        write("com/fractalx/test/order/OrderRepository.java", """
            package com.fractalx.test.order;
            import org.springframework.data.jpa.repository.JpaRepository;
            public interface OrderRepository extends JpaRepository<Object, Long> {}
        """)
        write("com/fractalx/test/inventory/InventoryService.java", """
            package com.fractalx.test.inventory;
            import com.fractalx.test.order.OrderRepository;
            public class InventoryService {
                public InventoryService(OrderRepository repo) {}
            }
        """)

        def orderMod     = module("order-service",     "com.fractalx.test.order")
        def inventoryMod = module("inventory-service", "com.fractalx.test.inventory")

        when:
        def report = analyzer.analyze(sourceRoot, [orderMod, inventoryMod])

        then:
        report.hasViolations()
        report.violations.any { it.repositoryName == "OrderRepository" && it.usedInModule == "inventory-service" }
    }

    def "returns empty report when no Spring Data repositories exist in source"() {
        given:
        write("com/fractalx/test/order/OrderService.java", """
            package com.fractalx.test.order;
            public class OrderService {}
        """)
        def mod = module("order-service", "com.fractalx.test.order")

        when:
        def report = analyzer.analyze(sourceRoot, [mod])

        then:
        report.ownership.isEmpty()
        !report.hasViolations()
    }

    def "no violation when repo is injected only within its own module"() {
        given:
        write("com/fractalx/test/order/OrderRepository.java", """
            package com.fractalx.test.order;
            import org.springframework.data.jpa.repository.JpaRepository;
            public interface OrderRepository extends JpaRepository<Object, Long> {}
        """)
        write("com/fractalx/test/order/OrderService.java", """
            package com.fractalx.test.order;
            public class OrderService {
                private OrderRepository orderRepository;
            }
        """)
        def mod = module("order-service", "com.fractalx.test.order")

        when:
        def report = analyzer.analyze(sourceRoot, [mod])

        then:
        !report.hasViolations()
    }

    def "ownership map contains one entry per discovered repository"() {
        given:
        write("com/fractalx/test/order/OrderRepository.java", """
            package com.fractalx.test.order;
            import org.springframework.data.jpa.repository.JpaRepository;
            public interface OrderRepository extends JpaRepository<Object, Long> {}
        """)
        write("com/fractalx/test/payment/PaymentRepository.java", """
            package com.fractalx.test.payment;
            import org.springframework.data.jpa.repository.JpaRepository;
            public interface PaymentRepository extends JpaRepository<Object, Long> {}
        """)
        def orderMod   = module("order-service",   "com.fractalx.test.order")
        def paymentMod = module("payment-service",  "com.fractalx.test.payment")

        when:
        def report = analyzer.analyze(sourceRoot, [orderMod, paymentMod])

        then:
        report.ownership.size() == 2
        report.ownership.keySet().containsAll(["OrderRepository", "PaymentRepository"])
    }
}
