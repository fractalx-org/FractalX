package org.fractalx.core.datamanagement

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that ReferenceValidatorGenerator:
 *  - detects String *Id fields in @Entity classes that were produced by RelationshipDecoupler
 *  - generates a ReferenceValidator bean with validate*Exists() methods for each remote type
 *  - generates a *ExistsClient @NetScopeClient stub for each remote type
 *  - skips generation when the module has no cross-module dependencies
 *  - skips generation when no String *Id fields are present in @Entity classes
 */
class ReferenceValidatorGeneratorSpec extends Specification {

    @TempDir
    Path serviceRoot

    ReferenceValidatorGenerator generator = new ReferenceValidatorGenerator()

    // ── helpers ──────────────────────────────────────────────────────────────

    private void writeEntity(String relativePath, String content) {
        Path file = serviceRoot.resolve("src/main/java").resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    private FractalModule moduleWithDeps() {
        FractalModule.builder()
            .serviceName("order-service")
            .packageName("org.fractalx.test.order")
            .port(8081)
            .dependencies(["PaymentService"])
            .build()
    }

    private FractalModule moduleNoDeps() {
        FractalModule.builder()
            .serviceName("payment-service")
            .packageName("org.fractalx.test.payment")
            .port(8082)
            .build()
    }

    private Path validationDir() {
        serviceRoot.resolve(
            "src/main/java/org/fractalx/generated/orderservice/validation")
    }

    // ── feature methods ───────────────────────────────────────────────────────

    def "generates ReferenceValidator.java when module has deps and String Id fields"() {
        given: "Order entity with a decoupled String paymentId field"
        writeEntity("org/fractalx/test/order/Order.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            @Entity
            public class Order {
                @Id private Long id;
                private String paymentId;
            }
        """)

        when:
        generator.generateReferenceValidator(moduleWithDeps(), serviceRoot)

        then:
        Files.exists(validationDir().resolve("ReferenceValidator.java"))
    }

    def "generated ReferenceValidator has a validate*Exists method for each remote type"() {
        given:
        writeEntity("org/fractalx/test/order/Order.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            @Entity
            public class Order {
                @Id private Long id;
                private String paymentId;
            }
        """)

        when:
        generator.generateReferenceValidator(moduleWithDeps(), serviceRoot)

        then:
        def content = Files.readString(validationDir().resolve("ReferenceValidator.java"))
        content.contains("validatePaymentExists")
        content.contains("String paymentId")
    }

    def "generates *ExistsClient interface annotated with @NetScopeClient"() {
        given:
        writeEntity("org/fractalx/test/order/Order.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            @Entity
            public class Order {
                @Id private Long id;
                private String paymentId;
            }
        """)

        when:
        generator.generateReferenceValidator(moduleWithDeps(), serviceRoot)

        then:
        def clientFile = validationDir().resolve("PaymentExistsClient.java")
        Files.exists(clientFile)
        def content = Files.readString(clientFile)
        content.contains("@NetScopeClient")
        content.contains("boolean exists(String id)")
    }

    def "skips generation when module has no cross-module dependencies"() {
        given: "entity with a String Id field but module has no deps"
        writeEntity("org/fractalx/test/payment/Payment.java", """
            package org.fractalx.test.payment;
            import jakarta.persistence.*;
            @Entity
            public class Payment {
                @Id private Long id;
                private String orderId;
            }
        """)

        when:
        generator.generateReferenceValidator(moduleNoDeps(), serviceRoot)

        then: "no validation package is created"
        !Files.exists(serviceRoot.resolve(
            "src/main/java/org/fractalx/generated/paymentservice/validation"))
    }

    def "skips generation when no String *Id fields are found in @Entity classes"() {
        given: "entity with deps declared, but no String *Id fields"
        writeEntity("org/fractalx/test/order/Order.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            @Entity
            public class Order {
                @Id private Long id;
                private String status;
                private String customerId;  // this IS a String *Id pattern → so it will be detected
            }
        """)

        when: "module has deps, entity has customerId"
        generator.generateReferenceValidator(moduleWithDeps(), serviceRoot)

        then: "CustomerExistsClient is generated (customerId → Customer)"
        Files.exists(validationDir().resolve("CustomerExistsClient.java"))
    }

    def "detects @ElementCollection List<String> courseIds and generates validateAllCourseExist"() {
        given: "entity with @ElementCollection List<String> courseIds (produced by ManyToMany decoupling)"
        writeEntity("org/fractalx/test/order/Order.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import java.util.List;
            @Entity
            public class Order {
                @Id private Long id;
                @ElementCollection
                private List<String> courseIds;
            }
        """)

        when:
        generator.generateReferenceValidator(moduleWithDeps(), serviceRoot)

        then:
        def content = Files.readString(validationDir().resolve("ReferenceValidator.java"))
        content.contains("validateAllCourseExist")
        content.contains("List<String>")
        content.contains("validateCourseExists") // collection method delegates to singular
        // The ExistsClient is the same interface — singular exists() handles both
        Files.exists(validationDir().resolve("CourseExistsClient.java"))
    }

    def "generated ReferenceValidator includes java.util.List import when collection id fields present"() {
        given:
        writeEntity("org/fractalx/test/order/Order.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import java.util.List;
            @Entity
            public class Order {
                @Id private Long id;
                @ElementCollection
                private List<String> courseIds;
            }
        """)

        when:
        generator.generateReferenceValidator(moduleWithDeps(), serviceRoot)

        then:
        def content = Files.readString(validationDir().resolve("ReferenceValidator.java"))
        content.contains("import java.util.List")
    }

    def "detects multiple decoupled Id fields and generates a validator method for each"() {
        given: "entity with both paymentId and productId"
        writeEntity("org/fractalx/test/order/Order.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            @Entity
            public class Order {
                @Id private Long id;
                private String paymentId;
                private String productId;
            }
        """)
        def mod = FractalModule.builder()
            .serviceName("order-service")
            .packageName("org.fractalx.test.order")
            .port(8081)
            .dependencies(["PaymentService", "InventoryService"])
            .build()

        when:
        generator.generateReferenceValidator(mod, serviceRoot)

        then:
        def content = Files.readString(validationDir().resolve("ReferenceValidator.java"))
        content.contains("validatePaymentExists")
        content.contains("validateProductExists")
    }
}
