package org.fractalx.core.graph

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class GraphBuilderSpec extends Specification {

    @TempDir
    Path sourceRoot

    // ── Basic node creation ────────────────────────────────────────────────

    def "builds a node for a simple class"() {
        given:
        writeJavaFile("com/example", "Order.java", """
            package com.example;
            public class Order {
                private String id;
            }
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        graph.node("com.example.Order").isPresent()
        graph.node("com.example.Order").get().kind() == NodeKind.CLASS
        graph.node("com.example.Order").get().simpleName() == "Order"
        graph.node("com.example.Order").get().packageName() == "com.example"
    }

    def "builds a node for an interface"() {
        given:
        writeJavaFile("com/example", "OrderRepository.java", """
            package com.example;
            public interface OrderRepository {
                Order findById(String id);
            }
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        graph.node("com.example.OrderRepository").isPresent()
        graph.node("com.example.OrderRepository").get().kind() == NodeKind.INTERFACE
    }

    def "builds a node for an enum"() {
        given:
        writeJavaFile("com/example", "Status.java", """
            package com.example;
            public enum Status { ACTIVE, INACTIVE }
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        graph.node("com.example.Status").isPresent()
        graph.node("com.example.Status").get().kind() == NodeKind.ENUM
    }

    def "builds a node for a record"() {
        given:
        writeJavaFile("com/example", "OrderRequest.java", """
            package com.example;
            public record OrderRequest(String customerId, double amount) {}
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        graph.node("com.example.OrderRequest").isPresent()
        graph.node("com.example.OrderRequest").get().kind() == NodeKind.RECORD
    }

    def "builds a node for an annotation"() {
        given:
        writeJavaFile("com/example", "Audited.java", """
            package com.example;
            public @interface Audited {}
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        graph.node("com.example.Audited").isPresent()
        graph.node("com.example.Audited").get().kind() == NodeKind.ANNOTATION
    }

    // ── EXTENDS edges ──────────────────────────────────────────────────────

    def "detects class inheritance as EXTENDS edge"() {
        given:
        writeJavaFile("com/example", "BaseEntity.java", """
            package com.example;
            public class BaseEntity { }
        """)
        writeJavaFile("com/example", "Order.java", """
            package com.example;
            public class Order extends BaseEntity { }
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        def edges = graph.edgesFrom("com.example.Order")
        edges.any { it.kind() == EdgeKind.EXTENDS && it.targetNode() == "com.example.BaseEntity" }
    }

    // ── IMPLEMENTS edges ───────────────────────────────────────────────────

    def "detects interface implementation as IMPLEMENTS edge"() {
        given:
        writeJavaFile("com/example", "Payable.java", """
            package com.example;
            public interface Payable {
                void pay();
            }
        """)
        writeJavaFile("com/example", "Invoice.java", """
            package com.example;
            public class Invoice implements Payable {
                public void pay() {}
            }
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        def edges = graph.edgesFrom("com.example.Invoice")
        edges.any { it.kind() == EdgeKind.IMPLEMENTS && it.targetNode() == "com.example.Payable" }
    }

    // ── FIELD_REFERENCE edges ──────────────────────────────────────────────

    def "detects field type as FIELD_REFERENCE edge"() {
        given:
        writeJavaFile("com/example", "PaymentService.java", """
            package com.example;
            public class PaymentService {}
        """)
        writeJavaFile("com/example", "OrderService.java", """
            package com.example;
            public class OrderService {
                private PaymentService paymentService;
            }
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        def edges = graph.edgesFrom("com.example.OrderService")
        edges.any {
            it.kind() == EdgeKind.FIELD_REFERENCE &&
            it.targetNode() == "com.example.PaymentService" &&
            it.detail() == "paymentService"
        }
    }

    def "does not create FIELD_REFERENCE for JDK types"() {
        given:
        writeJavaFile("com/example", "OrderService.java", """
            package com.example;
            public class OrderService {
                private String name;
                private int count;
                private java.util.List<String> items;
            }
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        graph.edgesFrom("com.example.OrderService").isEmpty()
    }

    // ── CONSTRUCTOR_PARAM edges ────────────────────────────────────────────

    def "detects constructor parameter types as CONSTRUCTOR_PARAM edges"() {
        given:
        writeJavaFile("com/example", "PaymentService.java", """
            package com.example;
            public class PaymentService {}
        """)
        writeJavaFile("com/example", "NotificationService.java", """
            package com.example;
            public class NotificationService {}
        """)
        writeJavaFile("com/example", "OrderService.java", """
            package com.example;
            public class OrderService {
                private final PaymentService paymentService;
                private final NotificationService notificationService;

                public OrderService(PaymentService paymentService,
                                    NotificationService notificationService) {
                    this.paymentService = paymentService;
                    this.notificationService = notificationService;
                }
            }
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        def edges = graph.edgesFrom("com.example.OrderService")
                .findAll { it.kind() == EdgeKind.CONSTRUCTOR_PARAM }
        edges.size() == 2
        edges.any { it.targetNode() == "com.example.PaymentService" }
        edges.any { it.targetNode() == "com.example.NotificationService" }
    }

    // ── ANNOTATION edges ───────────────────────────────────────────────────

    def "detects class-level annotations"() {
        given:
        writeJavaFile("com/example", "Audited.java", """
            package com.example;
            public @interface Audited {}
        """)
        writeJavaFile("com/example", "Order.java", """
            package com.example;
            @Audited
            public class Order {}
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        def node = graph.node("com.example.Order").get()
        node.annotations().contains("Audited")
    }

    // ── Cross-package resolution ───────────────────────────────────────────

    def "resolves types across packages via import"() {
        given:
        writeJavaFile("com/example/order", "OrderService.java", """
            package com.example.order;
            import com.example.payment.PaymentService;
            public class OrderService {
                private PaymentService paymentService;
            }
        """)
        writeJavaFile("com/example/payment", "PaymentService.java", """
            package com.example.payment;
            public class PaymentService {}
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        def edges = graph.edgesFrom("com.example.order.OrderService")
        edges.any {
            it.kind() == EdgeKind.FIELD_REFERENCE &&
            it.targetNode() == "com.example.payment.PaymentService"
        }
    }

    def "resolves types within same package without import"() {
        given:
        writeJavaFile("com/example", "Customer.java", """
            package com.example;
            public class Customer {}
        """)
        writeJavaFile("com/example", "Order.java", """
            package com.example;
            public class Order {
                private Customer customer;
            }
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        def edges = graph.edgesFrom("com.example.Order")
        edges.any {
            it.kind() == EdgeKind.FIELD_REFERENCE &&
            it.targetNode() == "com.example.Customer"
        }
    }

    // ── Multi-file graph ───────────────────────────────────────────────────

    def "builds complete graph from multiple files across packages"() {
        given:
        writeJavaFile("com/example/model", "Customer.java", """
            package com.example.model;
            public class Customer {
                private String name;
            }
        """)
        writeJavaFile("com/example/repo", "CustomerRepository.java", """
            package com.example.repo;
            public interface CustomerRepository {
                com.example.model.Customer findById(String id);
            }
        """)
        writeJavaFile("com/example/service", "CustomerService.java", """
            package com.example.service;
            import com.example.repo.CustomerRepository;
            public class CustomerService {
                private final CustomerRepository repository;
                public CustomerService(CustomerRepository repository) {
                    this.repository = repository;
                }
            }
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        graph.allNodes().size() == 3

        and: "CustomerService depends on CustomerRepository via field and constructor"
        def svcEdges = graph.edgesFrom("com.example.service.CustomerService")
        svcEdges.any { it.kind() == EdgeKind.FIELD_REFERENCE && it.targetNode() == "com.example.repo.CustomerRepository" }
        svcEdges.any { it.kind() == EdgeKind.CONSTRUCTOR_PARAM && it.targetNode() == "com.example.repo.CustomerRepository" }
    }

    // ── Edge cases ─────────────────────────────────────────────────────────

    def "handles empty source root gracefully"() {
        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        graph.allNodes().isEmpty()
        graph.allEdges().isEmpty()
    }

    def "ignores non-Java files"() {
        given:
        Files.writeString(sourceRoot.resolve("readme.txt"), "not java")

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        graph.allNodes().isEmpty()
    }

    def "multiple classes in same file all become nodes"() {
        given:
        writeJavaFile("com/example", "Models.java", """
            package com.example;
            public class Models {
                public static class OrderDTO {}
                public static class CustomerDTO {}
            }
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        graph.node("com.example.Models").isPresent()
        graph.node("com.example.Models.OrderDTO").isPresent()
        graph.node("com.example.Models.CustomerDTO").isPresent()
    }

    // ── Graph structural queries ───────────────────────────────────────────

    def "subclassesOf works with GraphBuilder-produced graph"() {
        given:
        writeJavaFile("com/example", "BaseEntity.java", """
            package com.example;
            public class BaseEntity {}
        """)
        writeJavaFile("com/example", "Order.java", """
            package com.example;
            public class Order extends BaseEntity {}
        """)
        writeJavaFile("com/example", "Product.java", """
            package com.example;
            public class Product extends BaseEntity {}
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        graph.subclassesOf("com.example.BaseEntity").size() == 2
    }

    def "implementorsOf works with GraphBuilder-produced graph"() {
        given:
        writeJavaFile("com/example", "Auditable.java", """
            package com.example;
            public interface Auditable {}
        """)
        writeJavaFile("com/example", "Order.java", """
            package com.example;
            public class Order implements Auditable {}
        """)

        when:
        def graph = new GraphBuilder().build(sourceRoot)

        then:
        graph.implementorsOf("com.example.Auditable").size() == 1
        graph.implementorsOf("com.example.Auditable")[0].fqcn() == "com.example.Order"
    }

    // ── Determinism ────────────────────────────────────────────────────────

    def "same source produces identical graph — deterministic"() {
        given:
        writeJavaFile("com/example", "A.java", """
            package com.example;
            public class A { private B b; }
        """)
        writeJavaFile("com/example", "B.java", """
            package com.example;
            public class B extends A {}
        """)

        when:
        def graph1 = new GraphBuilder().build(sourceRoot)
        def graph2 = new GraphBuilder().build(sourceRoot)

        then:
        graph1.allNodes().size() == graph2.allNodes().size()
        graph1.allEdges().size() == graph2.allEdges().size()
        graph1.allNodes().collect { it.fqcn() }.sort() == graph2.allNodes().collect { it.fqcn() }.sort()
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void writeJavaFile(String packagePath, String fileName, String content) {
        def dir = sourceRoot.resolve(packagePath)
        Files.createDirectories(dir)
        Files.writeString(dir.resolve(fileName), content.stripIndent().trim())
    }
}
