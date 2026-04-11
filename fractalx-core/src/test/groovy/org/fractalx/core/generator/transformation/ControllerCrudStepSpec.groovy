package org.fractalx.core.generator.transformation

import org.fractalx.core.config.FractalxConfig
import org.fractalx.core.generator.GenerationContext
import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies ControllerCrudStep adds missing flat POST/PUT endpoints even when
 * nested mappings exist for the same resource.
 */
class ControllerCrudStepSpec extends Specification {

    @TempDir
    Path serviceRoot

    FractalModule module = FractalModule.builder()
            .serviceName("order-service")
            .packageName("com.example.order")
            .port(8081)
            .build()

    ControllerCrudStep step = new ControllerCrudStep()

    private GenerationContext ctx() {
        Path srcMainJava = serviceRoot.resolve("src/main/java")
        Files.createDirectories(srcMainJava)
        return new GenerationContext(module, srcMainJava, serviceRoot, [module], FractalxConfig.defaults(), [])
    }

    private Path writeFile(String relPath, String content) {
        Path srcMainJava = serviceRoot.resolve("src/main/java")
        Path file = srcMainJava.resolve(relPath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
        file
    }

    private void writeOrderService() {
        writeFile("com/example/order/OrderService.java", """
            package com.example.order;
            import org.springframework.stereotype.Service;
            @Service
            public class OrderService {
                private final OrderRepository orderRepository;
                public OrderService(OrderRepository orderRepository) {
                    this.orderRepository = orderRepository;
                }
                public java.util.List<Order> findAll() { return orderRepository.findAll(); }
            }
            """.stripIndent())
    }

    def "adds flat POST even when nested @PostMapping exists on a different path"() {
        given: "controller with nested POST and no flat POST"
        writeOrderService()
        Path ctrl = writeFile("com/example/order/OrderController.java", """
            package com.example.order;
            import java.util.List;
            import org.springframework.web.bind.annotation.*;
            @RestController
            @RequestMapping("/api")
            public class OrderController {
                private final OrderService orderService;
                public OrderController(OrderService orderService) { this.orderService = orderService; }

                @GetMapping("/orders")
                public List<Order> listOrders() { return orderService.findAll(); }

                @PostMapping("/customers/{customerId}/orders")
                public Order createNested(@PathVariable Long customerId, @RequestBody Order order) {
                    return order;
                }

                @PutMapping("/orders/{id}")
                public Order updateOrder(@PathVariable Long id, @RequestBody Order order) {
                    return order;
                }
            }
            """.stripIndent())

        when:
        step.generate(ctx())

        then: "flat POST(/orders) is added"
        def src = Files.readString(ctrl)
        src.contains('@PostMapping("/orders")')
        // nested POST retained
        src.contains('@PostMapping("/customers/{customerId}/orders")')
        // existing flat PUT untouched
        src.contains('@PutMapping("/orders/{id}")')
    }

    def "does not add flat POST when one already exists on the target path"() {
        given:
        writeOrderService()
        Path ctrl = writeFile("com/example/order/OrderController.java", """
            package com.example.order;
            import java.util.List;
            import org.springframework.web.bind.annotation.*;
            @RestController
            @RequestMapping("/api")
            public class OrderController {
                private final OrderService orderService;
                public OrderController(OrderService orderService) { this.orderService = orderService; }

                @GetMapping("/orders")
                public List<Order> listOrders() { return orderService.findAll(); }

                @PostMapping("/orders")
                public Order createFlat(@RequestBody Order order) { return order; }

                @PutMapping("/orders/{id}")
                public Order updateOrder(@PathVariable Long id, @RequestBody Order order) { return order; }
            }
            """.stripIndent())

        when:
        step.generate(ctx())

        then: "no duplicate POST added"
        def src = Files.readString(ctrl)
        // exactly one @PostMapping("/orders") remains
        src.count('@PostMapping("/orders")') == 1
    }

    def "adds both flat POST and flat PUT when controller only has GET"() {
        given:
        writeOrderService()
        Path ctrl = writeFile("com/example/order/OrderController.java", """
            package com.example.order;
            import java.util.List;
            import org.springframework.web.bind.annotation.*;
            @RestController
            @RequestMapping("/api")
            public class OrderController {
                private final OrderService orderService;
                public OrderController(OrderService orderService) { this.orderService = orderService; }

                @GetMapping("/orders")
                public List<Order> listOrders() { return orderService.findAll(); }
            }
            """.stripIndent())

        when:
        step.generate(ctx())

        then:
        def src = Files.readString(ctrl)
        src.contains('@PostMapping("/orders")')
        src.contains('@PutMapping("/orders/{id}")')
    }
}
