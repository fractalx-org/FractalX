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
        decoupler.transform(serviceRoot, module, "org.fractalx.test.order")

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
        decoupler.transform(serviceRoot, module, "org.fractalx.test.order")

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
        decoupler.transform(serviceRoot, module, "org.fractalx.test.order")

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
        decoupler.transform(serviceRoot, module, "org.fractalx.test.order")

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
        decoupler.transform(serviceRoot, module, "org.fractalx.test.order")

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
        decoupler.transform(serviceRoot, module, "org.fractalx.test.order")

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
        decoupler.transform(serviceRoot, module, "org.fractalx.test.order")

        then: "no modification — Order and OrderItem are both local"
        read("Order.java").contains("List<OrderItem>")
        read("OrderItem.java").contains("Order order")
    }

    def "@ManyToMany remote entity field is converted to @ElementCollection List<String> courseIds"() {
        given: "Student entity references remote Course via @ManyToMany"
        write("Student.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import org.fractalx.test.lms.Course;
            import java.util.List;
            @Entity
            public class Student {
                @Id private Long id;
                @ManyToMany
                private List<Course> courses;
            }
        """)

        when: "Course.java is NOT in serviceRoot — it is a remote entity"
        decoupler.transform(serviceRoot, module, "org.fractalx.test.order")

        then:
        def result = read("Student.java")
        result.contains("List<String>")
        result.contains("courseIds")
        result.contains("@ElementCollection")
        !result.contains("@ManyToMany")
        !result.contains("List<Course>")
    }

    def "@ManyToMany getter is renamed and returns List<String>"() {
        given:
        write("Student.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import org.fractalx.test.lms.Course;
            import java.util.List;
            @Entity
            public class Student {
                @Id private Long id;
                @ManyToMany
                private List<Course> courses;
                public List<Course> getCourses() { return courses; }
                public void setCourses(List<Course> courses) { this.courses = courses; }
            }
        """)

        when:
        decoupler.transform(serviceRoot, module, "org.fractalx.test.order")

        then:
        def result = read("Student.java")
        result.contains("getCourseIds()")
        result.contains("setCourseIds(")
        !result.contains("getCourses()")
        !result.contains("setCourses(List<Course>")
    }

    def "Lombok @Data Request class: getters inferred from fields for setter call-site rewrite"() {
        given: """Order entity with @ManyToOne Payment (remote).
               CreateOrderRequest uses Lombok @Data — no explicit getters in the AST.
               The service passes the 'payment' method param to order.setPayment();
               after decoupling, Case B should rewrite it to order.setPaymentId(request.getPaymentId())
               using the getter inferred from the @Data-annotated field."""
        write("Order.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import org.fractalx.test.payment.Payment;
            @Entity
            public class Order {
                @Id private Long id;
                @ManyToOne
                private Payment payment;
            }
        """)
        write("CreateOrderRequest.java", """
            package org.fractalx.test.order;
            import lombok.Data;
            @Data
            public class CreateOrderRequest {
                private String paymentId;
            }
        """)
        // The service receives a Payment param (remote entity passed in by caller) and a
        // CreateOrderRequest that holds the canonical paymentId.
        // order.setPayment(payment) — 'payment' is a NameExpr NOT in remoteVarToIdVar
        // (it's a method param, not a locally-declared var), so Case B fires.
        write("OrderService.java", """
            package org.fractalx.test.order;
            import org.springframework.stereotype.Service;
            import org.fractalx.test.payment.Payment;
            @Service
            public class OrderService {
                private final OrderRepository orderRepository;
                public OrderService(OrderRepository orderRepository) {
                    this.orderRepository = orderRepository;
                }
                public Order create(Payment payment, CreateOrderRequest request) {
                    Order order = new Order();
                    order.setPayment(payment);
                    orderRepository.save(order);
                    return order;
                }
            }
        """)

        when:
        decoupler.transform(serviceRoot, module, "org.fractalx.test.order")

        then: "call-site rewrite uses getPaymentId() inferred from the Lombok @Data field"
        def svc = read("OrderService.java")
        svc.contains("getPaymentId()")
        svc.contains("setPaymentId(")
    }

    def "chained access on decoupled @ManyToMany collection gets DECOUPLING WARNING comment"() {
        given: "Student entity references remote Course via @ManyToMany; service chains on getCourses()"
        write("Student.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import org.fractalx.test.lms.Course;
            import java.util.List;
            @Entity
            public class Student {
                @Id private Long id;
                @ManyToMany
                private List<Course> courses;
            }
        """)
        write("StudentService.java", """
            package org.fractalx.test.order;
            import org.springframework.stereotype.Service;
            @Service
            public class StudentService {
                private final StudentRepository studentRepository;
                public StudentService(StudentRepository studentRepository) {
                    this.studentRepository = studentRepository;
                }
                public boolean hasMathCourse(Student student) {
                    return student.getCourses().stream().anyMatch(c -> c.getTitle().equals("Math"));
                }
            }
        """)

        when:
        decoupler.transform(serviceRoot, module, "org.fractalx.test.order")

        then: "getCourses() renamed to getCourseIds() and a DECOUPLING WARNING comment is added"
        def svc = read("StudentService.java")
        svc.contains("getCourseIds()")
        svc.contains("DECOUPLING WARNING")
        !svc.contains("getCourses()")
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
        decoupler.transform(serviceRoot, module, "org.fractalx.test.order")

        then: "file content is effectively unchanged"
        read("Order.java").contains("String customerId")
        read("Order.java").contains("String status")
        !read("Order.java").contains("paymentId")
    }
}
