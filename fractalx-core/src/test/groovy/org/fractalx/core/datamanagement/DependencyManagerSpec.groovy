package org.fractalx.core.datamanagement

import org.fractalx.core.model.FractalModule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class DependencyManagerSpec extends Specification {

    @TempDir
    Path serviceRoot

    DependencyManager manager = new DependencyManager()

    FractalModule module = FractalModule.builder()
        .serviceName("user-service")
        .packageName("com.budgetapp.user")
        .port(8082)
        .build()

    private static final String BASE_POM = """\
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
</project>
"""

    private void writePom(String content = BASE_POM) {
        Files.writeString(serviceRoot.resolve("pom.xml"), content)
    }

    private void writeJavaFile(String relativePath, String content) {
        Path file = serviceRoot.resolve("src/main/java").resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    private String readPom() {
        Files.readString(serviceRoot.resolve("pom.xml"))
    }

    def "provisions Lombok when generated service source contains import lombok.*"() {
        given: "a pom.xml without Lombok and a Java file that imports lombok.Data"
        writePom()
        writeJavaFile("com/budgetapp/budget/dto/CreateBudgetRequest.java", """\
package com.budgetapp.budget.dto;

import lombok.Data;

@Data
public class CreateBudgetRequest {
    private String name;
}
""")

        when:
        manager.provisionImpliedDependencies(module, serviceRoot)

        then:
        readPom().contains("org.projectlombok")
    }

    def "provisions spring-boot-starter-validation when source contains import jakarta.validation.*"() {
        given: "a pom.xml without validation starter and a Java file that imports jakarta.validation"
        writePom()
        writeJavaFile("com/budgetapp/budget/dto/CreateBudgetRequest.java", """\
package com.budgetapp.budget.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public class CreateBudgetRequest {
    @NotNull
    @NotBlank
    private String name;
}
""")

        when:
        manager.provisionImpliedDependencies(module, serviceRoot)

        then:
        readPom().contains("spring-boot-starter-validation")
    }

    def "does not provision Lombok when already present in pom.xml (idempotent)"() {
        given: "a pom.xml that already contains Lombok and a Java file importing lombok"
        String pomWithLombok = BASE_POM.replace("</dependencies>", """\
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>""")
        writePom(pomWithLombok)
        writeJavaFile("com/budgetapp/user/service/UserService.java", """\
package com.budgetapp.user.service;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserService {}
""")

        when:
        manager.provisionImpliedDependencies(module, serviceRoot)

        then: "only one occurrence of org.projectlombok in pom.xml"
        readPom().count("org.projectlombok") == 1
    }
}
