package com.fractalx.core.generator

import com.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that DockerComposeGenerator produces a correctly structured
 * docker-compose.yml with registry health-checks, service dependencies,
 * environment variables, and per-service Dockerfiles.
 */
class DockerComposeGeneratorSpec extends Specification {

    @TempDir
    Path outputRoot

    DockerComposeGenerator generator = new DockerComposeGenerator()

    FractalModule order = FractalModule.builder()
        .serviceName("order-service")
        .packageName("com.example.order")
        .port(8081)
        .build()

    FractalModule payment = FractalModule.builder()
        .serviceName("payment-service")
        .packageName("com.example.payment")
        .port(8082)
        .build()

    private String compose() { Files.readString(outputRoot.resolve("docker-compose.yml")) }

    def "docker-compose.yml is created at the output root"() {
        when:
        generator.generate([order], outputRoot, false)

        then:
        Files.exists(outputRoot.resolve("docker-compose.yml"))
    }

    def "docker-compose.yml is version 3.9"() {
        when:
        generator.generate([order], outputRoot, false)

        then:
        compose().startsWith("version: '3.9'")
    }

    def "fractalx-registry service is included with a health check"() {
        when:
        generator.generate([order], outputRoot, false)

        then:
        def c = compose()
        c.contains("fractalx-registry:")
        c.contains("healthcheck:")
        c.contains("/services/health")
    }

    def "registry is exposed on port 8761"() {
        when:
        generator.generate([order], outputRoot, false)

        then:
        compose().contains("8761:8761")
    }

    def "generated services depend on fractalx-registry being healthy"() {
        when:
        generator.generate([order, payment], outputRoot, false)

        then:
        def c = compose()
        c.contains("depends_on:")
        c.contains("condition: service_healthy")
    }

    def "each service exposes both HTTP port and gRPC port (HTTP + 10000)"() {
        when:
        generator.generate([order], outputRoot, false)

        then:
        def c = compose()
        c.contains("8081:8081")
        c.contains("18081:18081")
    }

    def "services have SPRING_PROFILES_ACTIVE=docker environment variable"() {
        when:
        generator.generate([order], outputRoot, false)

        then:
        compose().contains("SPRING_PROFILES_ACTIVE=docker")
    }

    def "services have FRACTALX_REGISTRY_URL environment variable"() {
        when:
        generator.generate([order], outputRoot, false)

        then:
        compose().contains("FRACTALX_REGISTRY_URL=http://fractalx-registry:8761")
    }

    def "saga orchestrator service is included when hasSagaOrchestrator is true"() {
        when:
        generator.generate([order], outputRoot, true)

        then:
        compose().contains("fractalx-saga-orchestrator:")
    }

    def "saga orchestrator service is NOT included when hasSagaOrchestrator is false"() {
        when:
        generator.generate([order], outputRoot, false)

        then:
        !compose().contains("fractalx-saga-orchestrator:")
    }

    def "admin-service and fractalx-gateway are always included"() {
        when:
        generator.generate([order], outputRoot, false)

        then:
        def c = compose()
        c.contains("admin-service:")
        c.contains("fractalx-gateway:")
    }

    def "Dockerfile is created for known service directories that exist"() {
        given:
        Files.createDirectories(outputRoot.resolve("order-service"))

        when:
        generator.generate([order], outputRoot, false)

        then:
        Files.exists(outputRoot.resolve("order-service/Dockerfile"))
    }

    def "Dockerfile uses multi-stage build from maven:3.9-eclipse-temurin-17"() {
        given:
        Files.createDirectories(outputRoot.resolve("order-service"))

        when:
        generator.generate([order], outputRoot, false)

        then:
        def dockerfile = Files.readString(outputRoot.resolve("order-service/Dockerfile"))
        dockerfile.contains("FROM maven:3.9-eclipse-temurin-17 AS build")
        dockerfile.contains("FROM eclipse-temurin:17-jre-alpine")
    }
}
