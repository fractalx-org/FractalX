package org.fractalx.core.validation.rules

import org.fractalx.core.model.FractalModule
import org.fractalx.core.validation.ValidationContext
import org.fractalx.core.validation.ValidationSeverity
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class SagaIntegrityRuleSpec extends Specification {

    @TempDir Path tmp

    private static final def RULE = new SagaIntegrityRule()

    private FractalModule mod(String name, int port = 8081) {
        FractalModule.builder().serviceName(name).port(port)
                .className("com.example." + name.replace('-', '.') + ".Module")
                .packageName("com.example." + name.replace('-', '.'))
                .build()
    }

    private ValidationContext ctx(List<FractalModule> mods) {
        new ValidationContext(mods, tmp, null)
    }

    /** Writes a Java source file under tmp/src/main/java/... */
    private void writeJava(String pkg, String className, String body) {
        Path dir = tmp.resolve(pkg.replace('.', '/'))
        Files.createDirectories(dir)
        Files.writeString(dir.resolve(className + ".java"),
                "package $pkg;\n\nimport org.fractalx.annotations.DistributedSaga;\n\n"
                + "public class $className {\n$body\n}")
    }

    def "empty source root — no sagas — no issues"() {
        expect:
        RULE.validate(ctx([mod("order-service")])).isEmpty()
    }

    def "valid unique sagaId produces no error"() {
        given:
        writeJava("com.example.order", "OrderService", """
    @DistributedSaga(sagaId = "place-order-saga", timeout = 30000)
    public void placeOrder() {}
""")
        when:
        def issues = RULE.validate(ctx([mod("order-service")]))

        then:
        issues.findAll { it.ruleId() == "SAGA_NO_ID" || it.ruleId() == "SAGA_DUPLICATE_ID" }.isEmpty()
    }

    def "duplicate sagaId across two methods produces SAGA_DUPLICATE_ID error"() {
        given:
        writeJava("com.example.order", "OrderService", """
    @DistributedSaga(sagaId = "checkout-saga", timeout = 30000)
    public void placeOrder() {}

    @DistributedSaga(sagaId = "checkout-saga", timeout = 30000)
    public void reorder() {}
""")
        when:
        def issues = RULE.validate(ctx([mod("order-service")]))

        then:
        issues.any {
            it.ruleId() == "SAGA_DUPLICATE_ID" && it.severity() == ValidationSeverity.ERROR
        }
    }

    def "negative timeout produces SAGA_BAD_TIMEOUT error"() {
        given:
        writeJava("com.example.order", "OrderService", """
    @DistributedSaga(sagaId = "bad-timeout-saga", timeout = -1)
    public void placeOrder() {}
""")
        when:
        def issues = RULE.validate(ctx([mod("order-service")]))

        then:
        issues.any {
            it.ruleId() == "SAGA_BAD_TIMEOUT" && it.severity() == ValidationSeverity.ERROR
        }
    }

    def "zero timeout produces SAGA_BAD_TIMEOUT error"() {
        given:
        writeJava("com.example.order", "OrderService", """
    @DistributedSaga(sagaId = "zero-timeout-saga", timeout = 0)
    public void placeOrder() {}
""")
        when:
        def issues = RULE.validate(ctx([mod("order-service")]))

        then:
        issues.any { it.ruleId() == "SAGA_BAD_TIMEOUT" }
    }
}
