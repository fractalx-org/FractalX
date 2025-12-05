package com.fractalx.core.generator;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates pom.xml for microservices
 */
public class PomGenerator {

    private static final Logger log = LoggerFactory.getLogger(PomGenerator.class);

    public void generatePom(FractalModule module, Path serviceRoot) throws IOException {
        log.debug("Generating pom.xml for {}", module.getServiceName());

        String pomContent = generatePomContent(module);
        Path pomPath = serviceRoot.resolve("pom.xml");
        Files.writeString(pomPath, pomContent);
    }

    private String generatePomContent(FractalModule module) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
            
                <groupId>com.fractalx.generated</groupId>
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
                        <groupId>org.springframework.cloud</groupId>
                        <artifactId>spring-cloud-starter-openfeign</artifactId>
                        <version>4.1.0</version>
                    </dependency>
                    
                    <dependency>
                        <groupId>com.fractalx</groupId>
                        <artifactId>fractalx-runtime</artifactId>
                        <version>0.1.0-SNAPSHOT</version>
                    </dependency>
                    
                    <dependency>
                        <groupId>com.h2database</groupId>
                        <artifactId>h2</artifactId>
                        <scope>runtime</scope>
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
            """.formatted(
                module.getServiceName(),
                module.getServiceName()
        );
    }
}