package org.fractalx.core.verifier

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class ApiConventionCheckerSpec extends Specification {

    @TempDir
    Path tempDir

    ApiConventionChecker checker = new ApiConventionChecker()

    private FractalModule module(String name, String pkg = "com.example") {
        FractalModule.builder()
                .serviceName(name)
                .className(name)
                .packageName(pkg)
                .port(8081)
                .build()
    }

    private Path javaFile(String serviceName, String fileName, String content) {
        Path dir = tempDir.resolve(serviceName).resolve("src/main/java")
        Files.createDirectories(dir)
        Path file = dir.resolve(fileName)
        Files.writeString(file, content)
        file
    }

    def "returns empty list when output dir does not exist"() {
        given:
        def modules = [module("order-service")]

        when:
        def result = checker.check(tempDir.resolve("nonexistent"), modules)

        then:
        result.isEmpty()
    }

    def "returns empty list when no Controller files present"() {
        given:
        def m = module("order-service")
        Path dir = tempDir.resolve("order-service/src/main/java")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("OrderService.java"), """
            package com.example;
            public class OrderService {}
        """)

        when:
        def result = checker.check(tempDir, [m])

        then:
        result.isEmpty()
    }

    def "passes when controller uses typed mappings and has class-level @RequestMapping"() {
        given:
        def m = module("order-service")
        javaFile("order-service", "OrderController.java", """
            package com.example;
            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/orders")
            public class OrderController {
                @GetMapping("/{id}")
                public String getOrder(@PathVariable String id) { return id; }

                @PostMapping
                public String createOrder(@RequestBody String body) { return body; }

                @DeleteMapping("/{id}")
                public void deleteOrder(@PathVariable String id) {}
            }
        """)

        when:
        def result = checker.check(tempDir, [m])

        then:
        result.isEmpty()
    }

    def "flags bare @RequestMapping on method"() {
        given:
        def m = module("order-service")
        javaFile("order-service", "OrderController.java", """
            package com.example;
            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/orders")
            public class OrderController {
                @RequestMapping(value = "/foo", method = RequestMethod.GET)
                public String getFoo() { return "foo"; }
            }
        """)

        when:
        def result = checker.check(tempDir, [m])

        then:
        result.any { it.kind() == ApiConventionChecker.ViolationKind.BARE_REQUEST_MAPPING }
    }

    def "flags @RestController without class-level @RequestMapping"() {
        given:
        def m = module("order-service")
        javaFile("order-service", "OrderController.java", """
            package com.example;
            import org.springframework.web.bind.annotation.*;

            @RestController
            public class OrderController {
                @GetMapping("/orders")
                public String list() { return "[]"; }
            }
        """)

        when:
        def result = checker.check(tempDir, [m])

        then:
        result.any { it.kind() == ApiConventionChecker.ViolationKind.MISSING_CLASS_MAPPING }
    }

    def "flags @DeleteMapping with @RequestBody parameter"() {
        given:
        def m = module("order-service")
        javaFile("order-service", "OrderController.java", """
            package com.example;
            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/orders")
            public class OrderController {
                @DeleteMapping
                public void delete(@RequestBody String body) {}
            }
        """)

        when:
        def result = checker.check(tempDir, [m])

        then:
        result.any { it.kind() == ApiConventionChecker.ViolationKind.DELETE_WITH_BODY }
    }

    def "flags @GetMapping with non-readOnly @Transactional"() {
        given:
        def m = module("order-service")
        javaFile("order-service", "OrderController.java", """
            package com.example;
            import org.springframework.web.bind.annotation.*;
            import org.springframework.transaction.annotation.Transactional;

            @RestController
            @RequestMapping("/orders")
            public class OrderController {
                @GetMapping("/{id}")
                @Transactional
                public String get(@PathVariable String id) { return id; }
            }
        """)

        when:
        def result = checker.check(tempDir, [m])

        then:
        result.any { it.kind() == ApiConventionChecker.ViolationKind.MUTATING_READ_VERB }
    }

    def "BARE_REQUEST_MAPPING and MISSING_CLASS_MAPPING violations are critical"() {
        expect:
        new ApiConventionChecker.ApiViolation(
            ApiConventionChecker.ViolationKind.BARE_REQUEST_MAPPING,
            "svc", Path.of("X.java"), "Cls", "method", "detail").isCritical()
        new ApiConventionChecker.ApiViolation(
            ApiConventionChecker.ViolationKind.MISSING_CLASS_MAPPING,
            "svc", Path.of("X.java"), "Cls", null, "detail").isCritical()
        !new ApiConventionChecker.ApiViolation(
            ApiConventionChecker.ViolationKind.DELETE_WITH_BODY,
            "svc", Path.of("X.java"), "Cls", "method", "detail").isCritical()
    }

    def "toString includes service name and detail"() {
        given:
        def v = new ApiConventionChecker.ApiViolation(
            ApiConventionChecker.ViolationKind.BARE_REQUEST_MAPPING,
            "my-service", Path.of("Foo.java"), "FooController", "doFoo", "use typed mapping")

        when:
        def s = v.toString()

        then:
        s.contains("my-service")
        s.contains("FooController")
        s.contains("[FAIL]")
    }

    def "skips non-java files gracefully"() {
        given:
        def m = module("order-service")
        Path dir = tempDir.resolve("order-service/src/main/java")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("OrderController.java"), "not valid java {{{{")

        when:
        def result = checker.check(tempDir, [m])

        then:
        notThrown(Exception)
    }
}
