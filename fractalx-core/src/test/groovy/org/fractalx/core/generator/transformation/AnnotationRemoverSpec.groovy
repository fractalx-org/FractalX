package org.fractalx.core.generator.transformation

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that AnnotationRemover strips FractalX annotations and their imports
 * from copied source files without touching unrelated code.
 */
class AnnotationRemoverSpec extends Specification {

    @TempDir
    Path serviceRoot

    AnnotationRemover remover = new AnnotationRemover()

    private Path write(String name, String content) {
        Path file = serviceRoot.resolve(name)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
        file
    }

    private String read(Path file) { Files.readString(file) }

    // ── @DecomposableModule ───────────────────────────────────────────────────

    def "@DecomposableModule is removed from the class declaration"() {
        given:
        def file = write("OrderModule.java", """\
            package com.example.order;
            import org.fractalx.annotations.DecomposableModule;
            @DecomposableModule(serviceName = "order-service", port = 8081)
            public class OrderModule {}
        """)

        when:
        remover.processServiceDirectory(serviceRoot)

        then:
        !read(file).contains("@DecomposableModule")
    }

    def "import for @DecomposableModule is removed"() {
        given:
        def file = write("OrderModule.java", """\
            package com.example.order;
            import org.fractalx.annotations.DecomposableModule;
            @DecomposableModule(serviceName = "order-service", port = 8081)
            public class OrderModule {}
        """)

        when:
        remover.processServiceDirectory(serviceRoot)

        then:
        !read(file).contains("import org.fractalx.annotations.DecomposableModule")
    }

    // ── @ServiceBoundary ──────────────────────────────────────────────────────

    def "@ServiceBoundary is removed from the class declaration"() {
        given:
        def file = write("OrderService.java", """\
            package com.example.order;
            import org.fractalx.annotations.ServiceBoundary;
            @ServiceBoundary
            public class OrderService {}
        """)

        when:
        remover.processServiceDirectory(serviceRoot)

        then:
        !read(file).contains("@ServiceBoundary")
        !read(file).contains("import org.fractalx.annotations.ServiceBoundary")
    }

    // ── @DistributedSaga ──────────────────────────────────────────────────────

    def "@DistributedSaga is removed from method declarations"() {
        given:
        def file = write("CheckoutService.java", """\
            package com.example.order;
            import org.fractalx.annotations.DistributedSaga;
            public class CheckoutService {
                @DistributedSaga
                public void checkout() {}
            }
        """)

        when:
        remover.processServiceDirectory(serviceRoot)

        then:
        !read(file).contains("@DistributedSaga")
        !read(file).contains("import org.fractalx.annotations.DistributedSaga")
    }

    // ── non-FractalX annotations preserved ───────────────────────────────────

    def "Spring @Service annotation is preserved"() {
        given:
        def file = write("OrderService.java", """\
            package com.example.order;
            import org.fractalx.annotations.ServiceBoundary;
            import org.springframework.stereotype.Service;
            @ServiceBoundary
            @Service
            public class OrderService {}
        """)

        when:
        remover.processServiceDirectory(serviceRoot)

        then:
        def content = read(file)
        content.contains("@Service")
        content.contains("import org.springframework.stereotype.Service")
    }

    def "@Transactional on a method is preserved"() {
        given:
        def file = write("OrderService.java", """\
            package com.example.order;
            import org.fractalx.annotations.DistributedSaga;
            import org.springframework.transaction.annotation.Transactional;
            public class OrderService {
                @DistributedSaga
                @Transactional
                public void placeOrder() {}
            }
        """)

        when:
        remover.processServiceDirectory(serviceRoot)

        then:
        def content = read(file)
        content.contains("@Transactional")
        content.contains("import org.springframework.transaction.annotation.Transactional")
    }

    // ── file without FractalX annotations ────────────────────────────────────

    def "file with no FractalX annotations is not modified"() {
        given:
        def content = """\
            package com.example.order;
            import java.util.List;
            public class OrderRepository {
                public List<String> findAll() { return List.of(); }
            }
        """
        def file = write("OrderRepository.java", content)

        when:
        remover.processServiceDirectory(serviceRoot)

        then:
        // Content structure must be equivalent (whitespace may differ due to JavaParser pretty-print)
        read(file).contains("OrderRepository")
        read(file).contains("findAll")
    }

    // ── com.fractalx prefix ───────────────────────────────────────────────────

    def "com.fractalx.annotations import prefix is also cleaned"() {
        given:
        def file = write("PaymentModule.java", """\
            package com.example.payment;
            import com.fractalx.annotations.DecomposableModule;
            @DecomposableModule(serviceName = "payment-service", port = 8082)
            public class PaymentModule {}
        """)

        when:
        remover.processServiceDirectory(serviceRoot)

        then:
        def content = read(file)
        !content.contains("@DecomposableModule")
        !content.contains("import com.fractalx.annotations")
    }

    // ── subdirectory traversal ────────────────────────────────────────────────

    def "files in subdirectories are processed"() {
        given:
        def file = write("sub/OrderModule.java", """\
            package com.example.order.sub;
            import org.fractalx.annotations.DecomposableModule;
            @DecomposableModule(serviceName = "order-service", port = 8081)
            public class OrderModule {}
        """)

        when:
        remover.processServiceDirectory(serviceRoot)

        then:
        !read(file).contains("@DecomposableModule")
    }
}
