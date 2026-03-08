package org.fractalx.core.datamanagement

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that FlywayMigrationGenerator:
 *  - creates V1__init.sql at the correct Flyway migration path
 *  - emits CREATE TABLE IF NOT EXISTS for each @Entity found in the service source
 *  - always includes the fractalx_outbox table + index regardless of entity count
 *  - respects idempotency (never overwrites an existing migration file)
 *  - maps Java field types to correct SQL column types
 */
class FlywayMigrationGeneratorSpec extends Specification {

    @TempDir
    Path serviceRoot

    FlywayMigrationGenerator generator = new FlywayMigrationGenerator()

    FractalModule module = FractalModule.builder()
        .serviceName("order-service")
        .packageName("org.fractalx.test.order")
        .port(8081)
        .build()

    // ── helpers ──────────────────────────────────────────────────────────────

    private void writeEntity(String relativePath, String content) {
        Path file = serviceRoot.resolve("src/main/java").resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    private String sql() {
        Files.readString(serviceRoot.resolve("src/main/resources/db/migration/V1__init.sql"))
    }

    // ── feature methods ───────────────────────────────────────────────────────

    def "V1__init.sql is created at the Flyway migration path"() {
        when:
        generator.generateMigration(module, serviceRoot)

        then:
        Files.exists(serviceRoot.resolve("src/main/resources/db/migration/V1__init.sql"))
    }

    def "SQL contains CREATE TABLE IF NOT EXISTS for each @Entity class"() {
        given:
        writeEntity("org/fractalx/test/order/Order.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            @Entity
            public class Order {
                @Id private Long id;
                private String customerId;
            }
        """)
        writeEntity("org/fractalx/test/order/OrderItem.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            @Entity
            public class OrderItem {
                @Id private Long id;
                private Integer quantity;
            }
        """)

        when:
        generator.generateMigration(module, serviceRoot)

        then:
        def content = sql()
        content.contains("CREATE TABLE IF NOT EXISTS order")
        content.contains("CREATE TABLE IF NOT EXISTS order_item")
    }

    def "SQL always contains the fractalx_outbox table even when there are no entities"() {
        when: "no @Entity classes in service source"
        generator.generateMigration(module, serviceRoot)

        then:
        sql().contains("CREATE TABLE IF NOT EXISTS fractalx_outbox")
    }

    def "SQL contains an index on the outbox table for the unpublished+created_at columns"() {
        when:
        generator.generateMigration(module, serviceRoot)

        then:
        sql().contains("CREATE INDEX IF NOT EXISTS idx_outbox_unpublished")
    }

    def "does not overwrite an existing V1__init.sql (idempotent)"() {
        given: "a pre-existing migration file"
        def migDir = serviceRoot.resolve("src/main/resources/db/migration")
        Files.createDirectories(migDir)
        Files.writeString(migDir.resolve("V1__init.sql"), "-- custom migration")

        when:
        generator.generateMigration(module, serviceRoot)

        then: "original content is preserved"
        sql() == "-- custom migration"
    }

    def "SQL header contains the service name"() {
        when:
        generator.generateMigration(module, serviceRoot)

        then:
        sql().contains("order-service")
    }

    @Unroll
    def "Java type '#javaType' maps to SQL type '#expectedSql'"() {
        given: "entity with a field of each Java type"
        writeEntity("org/fractalx/test/order/TypeEntity.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import java.math.BigDecimal;
            import java.time.*;
            @Entity
            public class TypeEntity {
                @Id private Long id;
                private $javaType value;
            }
        """)

        when:
        generator.generateMigration(module, serviceRoot)

        then:
        sql().contains(expectedSql)

        where:
        javaType        | expectedSql
        "String"        | "VARCHAR(255)"
        "Long"          | "BIGINT"
        "Integer"       | "INT"
        "Boolean"       | "BOOLEAN"
        "LocalDateTime" | "TIMESTAMP"
        "BigDecimal"    | "DECIMAL(19,4)"
    }

    def "local @ManyToMany generates a join table in the migration"() {
        given: "Student entity has @ManyToMany to a local Course entity"
        writeEntity("org/fractalx/test/order/Student.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import java.util.List;
            @Entity
            public class Student {
                @Id private Long id;
                @ManyToMany
                private List<Course> courses;
            }
        """)

        when:
        generator.generateMigration(module, serviceRoot)

        then: "a join table is emitted alongside the entity table"
        def content = sql()
        content.contains("CREATE TABLE IF NOT EXISTS student_courses")
        content.contains("student_id BIGINT")
        content.contains("course_id BIGINT")
    }

    def "@ElementCollection List<String> field generates an element collection table"() {
        given: "Student entity has @ElementCollection List<String> courseIds (post-decoupling)"
        writeEntity("org/fractalx/test/order/Student.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            import java.util.List;
            @Entity
            public class Student {
                @Id private Long id;
                @ElementCollection
                private List<String> courseIds;
            }
        """)

        when:
        generator.generateMigration(module, serviceRoot)

        then: "an element collection table is emitted"
        def content = sql()
        content.contains("CREATE TABLE IF NOT EXISTS student_course_ids")
        content.contains("course_ids VARCHAR(255)")
    }

    def "entity class name is converted to snake_case table name"() {
        given:
        writeEntity("org/fractalx/test/order/OrderLineItem.java", """
            package org.fractalx.test.order;
            import jakarta.persistence.*;
            @Entity
            public class OrderLineItem {
                @Id private Long id;
            }
        """)

        when:
        generator.generateMigration(module, serviceRoot)

        then:
        sql().contains("order_line_item")
    }
}
