package org.fractalx.core.datamanagement

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that RelationshipDecoupler correctly transforms cross-module JPA
 * relationship fields in entity classes:
 *
 *  - @ManyToOne Payment payment  →  String paymentId
 *  - getter/setter signatures updated accordingly
 *  - remote entity imports removed
 *  - @OneToMany List<RemoteEntity> removed
 *  - local entities left untouched
 */
class RelationshipDecouplerSpec extends Specification {

    @TempDir
    Path serviceRoot

    RelationshipDecoupler decoupler = new RelationshipDecoupler()

    FractalModule module = FractalModule.builder()
        .serviceName("order-service")
        .packageName("org.fractalx.test.order")
        .port(8081)
        .build()

    // ── helpers ──────────────────────────────────────────────────────────────

    private void write(String relativePath, String content) {
        Path file = serviceRoot.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    private String read(String relativePath) {
        Files.readString(serviceRoot.resolve(relativePath))
    }

    // ── feature methods ───────────────────────────────────────────────────────

    def "@ManyToOne remote entity field is converted to String id field"() {
        given: "Order entity references remote Payment via @ManyToOne"
        write("Order.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import org.fractalx.test.payment.Payment;
            @Entity
            public class Order {
                @Id private Long id;
                @ManyToOne
                @JoinColumn(name = "payment_id")
                private Payment payment;
                public Payment getPayment() { return payment; }
                public void setPayment(Payment payment) { this.payment = payment; }
            }
        """)

        when: "Payment.java is NOT in serviceRoot — it is a remote entity"
        decoupler.transform(serviceRoot, module)

        then: "field type becomes String, name becomes paymentId"
        def result = read("Order.java")
        result.contains("String paymentId")
        !result.contains("Payment payment")
    }

    def "@ManyToOne and @JoinColumn annotations are removed after decoupling"() {
        given:
        write("Order.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import org.fractalx.test.payment.Payment;
            @Entity
            public class Order {
                @Id private Long id;
                @ManyToOne
                @JoinColumn(name = "payment_id")
                private Payment payment;
                public Payment getPayment() { return payment; }
                public void setPayment(Payment p) { this.payment = p; }
            }
        """)

        when:
        decoupler.transform(serviceRoot, module)

        then:
        def result = read("Order.java")
        !result.contains("@ManyToOne")
        !result.contains("@JoinColumn")
    }

    def "getter is updated to return String and renamed to getId style"() {
        given:
        write("Order.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import org.fractalx.test.payment.Payment;
            @Entity
            public class Order {
                @Id private Long id;
                @ManyToOne
                private Payment payment;
                public Payment getPayment() { return payment; }
                public void setPayment(Payment p) { this.payment = p; }
            }
        """)

        when:
        decoupler.transform(serviceRoot, module)

        then:
        def result = read("Order.java")
        result.contains("String getPaymentId()")
        !result.contains("Payment getPayment()")
    }

    def "setter is updated to accept String parameter"() {
        given:
        write("Order.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import org.fractalx.test.payment.Payment;
            @Entity
            public class Order {
                @Id private Long id;
                @ManyToOne
                private Payment payment;
                public Payment getPayment() { return payment; }
                public void setPayment(Payment payment) { this.payment = payment; }
            }
        """)

        when:
        decoupler.transform(serviceRoot, module)

        then:
        def result = read("Order.java")
        result.contains("void setPaymentId(String")
        !result.contains("void setPayment(Payment")
    }

    def "import for remote entity type is removed"() {
        given:
        write("Order.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import org.fractalx.test.payment.Payment;
            @Entity
            public class Order {
                @Id private Long id;
                @ManyToOne
                private Payment payment;
                public Payment getPayment() { return payment; }
                public void setPayment(Payment p) { this.payment = p; }
            }
        """)

        when:
        decoupler.transform(serviceRoot, module)

        then:
        !read("Order.java").contains("import org.fractalx.test.payment.Payment")
    }

    def "@OneToMany collection referencing a remote entity is removed"() {
        given: "OrderItem lives locally; Product does not — it is remote"
        write("OrderItem.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import org.fractalx.test.inventory.Product;
            import java.util.List;
            @Entity
            public class OrderItem {
                @Id private Long id;
                @OneToMany
                private List<Product> products;
            }
        """)

        when:
        decoupler.transform(serviceRoot, module)

        then: "the @OneToMany List<Product> field is removed"
        def result = read("OrderItem.java")
        !result.contains("List<Product>")
        !result.contains("@OneToMany")
    }

    def "local entity relationships are NOT modified"() {
        given: "Both Order and OrderItem are local @Entity classes"
        write("Order.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import java.util.List;
            @Entity
            public class Order {
                @Id private Long id;
                @OneToMany(mappedBy = "order")
                private List<OrderItem> items;
            }
        """)
        write("OrderItem.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            @Entity
            public class OrderItem {
                @Id private Long id;
                @ManyToOne
                private Order order;
            }
        """)

        when:
        decoupler.transform(serviceRoot, module)

        then: "no modification — Order and OrderItem are both local"
        read("Order.java").contains("List<OrderItem>")
        read("OrderItem.java").contains("Order order")
    }

    def "transformation is a no-op when there are no remote entity references"() {
        given: "simple entity with no cross-module relationships"
        def original = """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            @Entity
            public class Order {
                @Id private Long id;
                private String customerId;
                private String status;
            }
        """
        write("Order.java", original)

        when:
        decoupler.transform(serviceRoot, module)

        then: "file content is effectively unchanged"
        read("Order.java").contains("String customerId")
        read("Order.java").contains("String status")
        !read("Order.java").contains("paymentId")
    }
}
