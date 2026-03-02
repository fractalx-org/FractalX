package org.fractalx.core.generator.service;

import org.fractalx.core.generator.GenerationContext;
import org.fractalx.core.generator.ServiceFileGenerator;
import org.fractalx.core.model.FractalModule;
import org.fractalx.core.observability.ObservabilityInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Generates pom.xml for a microservice.
 */
public class PomGenerator implements ServiceFileGenerator {

    private static final Logger log = LoggerFactory.getLogger(PomGenerator.class);
    private static final String FRACTALX_RUNTIME_VERSION = "0.2.0-SNAPSHOT";

    private final ObservabilityInjector observabilityInjector;

    public PomGenerator(ObservabilityInjector observabilityInjector) {
        this.observabilityInjector = observabilityInjector;
    }

    @Override
    public void generate(GenerationContext context) throws IOException {
        FractalModule module = context.getModule();
        log.debug("Generating pom.xml for {}", module.getServiceName());
        Files.writeString(context.getServiceRoot().resolve("pom.xml"), buildPomContent(module));
    }

    private String buildPomContent(FractalModule module) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                         http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <groupId>org.fractalx.generated</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0.0-SNAPSHOT</version>
                    <packaging>jar</packaging>

                    <name>%s</name>
                    <description>Generated microservice by FractalX</description>

                    <properties>
                        <java.version>17</java.version>
                        <spring-boot.version>3.2.0</spring-boot.version>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>

                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-dependencies</artifactId>
                                <version>${spring-boot.version}</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>

                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>

                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-jpa</artifactId>
                        </dependency>

                        <dependency>
                            <groupId>org.fractalx</groupId>
                            <artifactId>netscope-server</artifactId>
                            <version>1.0.0</version>
                        </dependency>

                        <dependency>
                            <groupId>org.fractalx</groupId>
                            <artifactId>netscope-client</artifactId>
                            <version>1.0.0</version>
                        </dependency>

                        <dependency>
                            <groupId>org.fractalx</groupId>
                            <artifactId>fractalx-runtime</artifactId>
                            <version>%s</version>
                        </dependency>

                        <dependency>
                            <groupId>com.h2database</groupId>
                            <artifactId>h2</artifactId>
                            <scope>runtime</scope>
                        </dependency>

                        <dependency>
                            <groupId>org.flywaydb</groupId>
                            <artifactId>flyway-core</artifactId>
                        </dependency>

                        <!-- Resilience4j — circuit breaker, retry, time limiter for NetScope calls -->
                        <dependency>
                            <groupId>io.github.resilience4j</groupId>
                            <artifactId>resilience4j-spring-boot3</artifactId>
                            <version>2.1.0</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-aop</artifactId>
                        </dependency>

                        <!-- Actuator for health endpoints (used by registry health polling) -->
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-actuator</artifactId>
                        </dependency>
                        %s
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.11.0</version>
                                <configuration>
                                    <!-- Required by Spring 6.1+ for @PathVariable/@RequestParam name inference
                                         and by Spring Data JPA for @Query named parameter binding -->
                                    <parameters>true</parameters>
                                </configuration>
                            </plugin>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                                <version>${spring-boot.version}</version>
                                <executions>
                                    <execution>
                                        <goals>
                                            <goal>repackage</goal>
                                        </goals>
                                    </execution>
                                </executions>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(
                module.getServiceName(),
                module.getServiceName(),
                FRACTALX_RUNTIME_VERSION,
                observabilityInjector.getDependencies()
        );
    }
}
