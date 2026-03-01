package org.fractalx.core.verifier

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class DockerfileValidatorSpec extends Specification {

    @TempDir
    Path tempDir

    DockerfileValidator validator = new DockerfileValidator()

    private FractalModule module(String name) {
        FractalModule.builder()
                .serviceName(name)
                .className(name)
                .packageName("com.example")
                .port(8081)
                .build()
    }

    private void writeDockerfile(String serviceName, String content) {
        Path dir = tempDir.resolve(serviceName)
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("Dockerfile"), content)
    }

    def "flags MISSING_DOCKERFILE when Dockerfile absent"() {
        given:
        def m = module("order-service")
        Files.createDirectories(tempDir.resolve("order-service"))

        when:
        def result = validator.validate(tempDir, [m])

        then:
        result.size() == 1
        result[0].kind() == DockerfileValidator.DockerFindingKind.MISSING_DOCKERFILE
        result[0].isCritical()
    }

    def "passes a well-formed multi-stage Dockerfile"() {
        given:
        def m = module("order-service")
        writeDockerfile("order-service", """\
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./mvnw -q package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
USER 1001:1001
ENTRYPOINT ["java","-jar","app.jar"]
""")

        when:
        def result = validator.validate(tempDir, [m])

        then:
        result.isEmpty()
    }

    def "flags SINGLE_STAGE_BUILD when only one FROM present"() {
        given:
        def m = module("order-service")
        writeDockerfile("order-service", """\
FROM eclipse-temurin:17-jre-alpine
COPY app.jar app.jar
USER 1001
ENTRYPOINT ["java","-jar","app.jar"]
""")

        when:
        def result = validator.validate(tempDir, [m])

        then:
        result.any { it.kind() == DockerfileValidator.DockerFindingKind.SINGLE_STAGE_BUILD }
    }

    def "flags RUNNING_AS_ROOT when no USER instruction"() {
        given:
        def m = module("order-service")
        writeDockerfile("order-service", """\
FROM eclipse-temurin:17-jdk AS builder
RUN echo build

FROM eclipse-temurin:17-jre
COPY app.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
""")

        when:
        def result = validator.validate(tempDir, [m])

        then:
        result.any { it.kind() == DockerfileValidator.DockerFindingKind.RUNNING_AS_ROOT }
        result.any { it.kind() == DockerfileValidator.DockerFindingKind.RUNNING_AS_ROOT && it.isCritical() }
    }

    def "flags LATEST_IMAGE_TAG when :latest used in FROM"() {
        given:
        def m = module("order-service")
        writeDockerfile("order-service", """\
FROM eclipse-temurin:latest AS builder
RUN echo build

FROM eclipse-temurin:latest
COPY app.jar app.jar
USER 1001
ENTRYPOINT ["java","-jar","app.jar"]
""")

        when:
        def result = validator.validate(tempDir, [m])

        then:
        result.any { it.kind() == DockerfileValidator.DockerFindingKind.LATEST_IMAGE_TAG }
    }

    def "flags REMOTE_ADD when ADD with http URL used"() {
        given:
        def m = module("order-service")
        writeDockerfile("order-service", """\
FROM eclipse-temurin:17-jdk AS builder
ADD http://example.com/agent.jar /agent.jar

FROM eclipse-temurin:17-jre
COPY --from=builder /agent.jar /agent.jar
COPY app.jar app.jar
USER 1001
ENTRYPOINT ["java","-jar","app.jar"]
""")

        when:
        def result = validator.validate(tempDir, [m])

        then:
        result.any { it.kind() == DockerfileValidator.DockerFindingKind.REMOTE_ADD }
        result.any { it.kind() == DockerfileValidator.DockerFindingKind.REMOTE_ADD && it.isCritical() }
    }

    def "MISSING_DOCKERFILE and RUNNING_AS_ROOT are critical"() {
        expect:
        new DockerfileValidator.DockerFinding(
            DockerfileValidator.DockerFindingKind.MISSING_DOCKERFILE,
            "svc", Path.of("dir"), "missing").isCritical()
        new DockerfileValidator.DockerFinding(
            DockerfileValidator.DockerFindingKind.RUNNING_AS_ROOT,
            "svc", Path.of("Dockerfile"), "no user").isCritical()
    }

    def "toString includes service name and level"() {
        given:
        def f = new DockerfileValidator.DockerFinding(
            DockerfileValidator.DockerFindingKind.SINGLE_STAGE_BUILD,
            "my-svc", Path.of("Dockerfile"), "single stage")

        when:
        def s = f.toString()

        then:
        s.contains("my-svc")
        s.contains("[WARN]")
        s.contains("Dockerfile")
    }

    def "reports MISSING_DOCKERFILE when output dir does not exist"() {
        when:
        def result = validator.validate(tempDir.resolve("nonexistent"), [module("svc")])

        then:
        result.size() == 1
        result[0].kind() == DockerfileValidator.DockerFindingKind.MISSING_DOCKERFILE
    }
}
