package com.fractalx.core.gateway;

import com.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates pom.xml for API Gateway
 */
public class GatewayPomGenerator {
    private static final Logger log = LoggerFactory.getLogger(GatewayPomGenerator.class);

    public void generatePom(Path gatewayRoot, List<FractalModule> modules) throws IOException {
        log.debug("Generating gateway pom.xml");

        String dependencies = generateServiceDependencies(modules);

        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
            
                <groupId>com.fractalx.gateway</groupId>
                <artifactId>fractalx-api-gateway</artifactId>
                <version>1.0.0-SNAPSHOT</version>
                <packaging>jar</packaging>
            
                <name>FractalX API Gateway</name>
                <description>Auto-generated API Gateway for FractalX microservices</description>
            
                <properties>
                    <java.version>17</java.version>
                    <spring-boot.version>3.2.0</spring-boot.version>
                    <spring-cloud.version>2023.0.0</spring-cloud.version>
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
                        <dependency>
                            <groupId>org.springframework.cloud</groupId>
                            <artifactId>spring-cloud-dependencies</artifactId>
                            <version>${spring-cloud.version}</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            
                <dependencies>
                    <!-- Spring Cloud Gateway -->
                    <dependency>
                        <groupId>org.springframework.cloud</groupId>
                        <artifactId>spring-cloud-starter-gateway</artifactId>
                    </dependency>
                    
                    <!-- REMOVED: Eureka client dependency -->
                    
                    <!-- Actuator for health checks -->
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-actuator</artifactId>
                    </dependency>
                </dependencies>
            
                <build>
                    <plugins>
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
            """;

        Path pomPath = gatewayRoot.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);
        log.info("✓ Generated gateway pom.xml");
    }

    /**
     * Generate dependencies for all services
     */
    private String generateServiceDependencies(List<FractalModule> modules) {
        StringBuilder deps = new StringBuilder();

        for (FractalModule module : modules) {
            deps.append(String.format("""
                    <dependency>
                        <groupId>com.fractalx.generated</groupId>
                        <artifactId>%s</artifactId>
                        <version>1.0.0-SNAPSHOT</version>
                        <scope>provided</scope>
                    </dependency>
                    """, module.getServiceName()));
        }

        return deps.toString();
    }
}