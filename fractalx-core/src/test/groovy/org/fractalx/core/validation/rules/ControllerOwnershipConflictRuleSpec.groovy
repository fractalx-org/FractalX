package org.fractalx.core.validation.rules

import org.fractalx.core.model.FractalModule
import org.fractalx.core.validation.ValidationContext
import org.fractalx.core.validation.ValidationSeverity
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class ControllerOwnershipConflictRuleSpec extends Specification {

    @TempDir Path tmp

    private static final def RULE = new ControllerOwnershipConflictRule()

    // ── helpers ───────────────────────────────────────────────────────────────

    private FractalModule mod(String serviceName, String packageName, int port = 8081) {
        FractalModule.builder()
                .serviceName(serviceName)
                .packageName(packageName)
                .port(port)
                .className(packageName + ".Module")
                .build()
    }

    private ValidationContext ctx(List<FractalModule> mods) {
        new ValidationContext(mods, tmp, null)
    }

    /** Writes a .java file into the temp source root, mirroring the package structure. */
    private void writeJava(String packageName, String className, String body) {
        Path dir = tmp.resolve(packageName.replace('.', '/'))
        Files.createDirectories(dir)
        Files.writeString(dir.resolve(className + ".java"), body)
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    def "clean modules with no URL conflicts produce no issues"() {
        given: "order module exposes GET /api/orders/{id}, customer module exposes GET /api/customers/{id}"
        writeJava("com.example.order", "OrderController", """
package com.example.order;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping("/{id}")
    public String getOrder(@PathVariable String id) {
        return id;
    }

    @GetMapping
    public String listOrders() {
        return "orders";
    }
}
""")
        writeJava("com.example.customer", "CustomerController", """
package com.example.customer;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @GetMapping("/{id}")
    public String getCustomer(@PathVariable String id) {
        return id;
    }

    @GetMapping
    public String listCustomers() {
        return "customers";
    }
}
""")
        def orderMod    = mod("order-service",    "com.example.order",    8081)
        def customerMod = mod("customer-service", "com.example.customer", 8082)

        when:
        def issues = RULE.validate(ctx([orderMod, customerMod]))

        then:
        issues.findAll { it.ruleId() == "CTRL_CONFLICT" }.isEmpty()
    }

    def "two modules exposing the same METHOD+path produces CTRL_CONFLICT ERROR"() {
        given: "both order and payment modules expose POST /api/payments"
        writeJava("com.example.order", "OrderPaymentController", """
package com.example.order;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/payments")
public class OrderPaymentController {

    @PostMapping
    public String createPayment() {
        return "payment";
    }
}
""")
        writeJava("com.example.payment", "PaymentController", """
package com.example.payment;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @PostMapping
    public String createPayment() {
        return "payment";
    }
}
""")
        def orderMod   = mod("order-service",   "com.example.order",   8081)
        def paymentMod = mod("payment-service", "com.example.payment", 8082)

        when:
        def issues = RULE.validate(ctx([orderMod, paymentMod]))

        then:
        def conflict = issues.find { it.ruleId() == "CTRL_CONFLICT" }
        conflict != null
        conflict.severity() == ValidationSeverity.ERROR
        conflict.message().contains("POST /api/payments")
        conflict.message().contains("already owned by")
        conflict.fix().contains("single module package")
    }

    def "cross-resource endpoint owned by one module is not a conflict"() {
        given: """
            order-service has GET /api/customers/{id}/orders  (cross-resource — but only in order module)
            customer-service has GET /api/customers/{id}      (a different, non-overlapping path)
            These are DIFFERENT paths so they must NOT trigger CTRL_CONFLICT.
        """
        writeJava("com.example.order", "CustomerOrderController", """
package com.example.order;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/customers")
public class CustomerOrderController {

    @GetMapping("/{customerId}/orders")
    public String getOrdersForCustomer(@PathVariable String customerId) {
        return customerId;
    }
}
""")
        writeJava("com.example.customer", "CustomerController", """
package com.example.customer;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @GetMapping("/{id}")
    public String getCustomer(@PathVariable String id) {
        return id;
    }
}
""")
        def orderMod    = mod("order-service",    "com.example.order",    8081)
        def customerMod = mod("customer-service", "com.example.customer", 8082)

        when:
        def issues = RULE.validate(ctx([orderMod, customerMod]))

        then: "GET /api/customers/{customerId}/orders differs from GET /api/customers/{id} — no conflict"
        issues.findAll { it.ruleId() == "CTRL_CONFLICT" }.isEmpty()
    }
}
