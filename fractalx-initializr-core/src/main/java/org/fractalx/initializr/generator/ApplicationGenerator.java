package org.fractalx.initializr.generator;

import org.fractalx.initializr.InitializerContext;
import org.fractalx.initializr.model.ProjectSpec;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Generates the root {@code @SpringBootApplication} class.
 */
public class ApplicationGenerator implements InitializerFileGenerator {

    @Override
    public String label() { return "Application.java"; }

    @Override
    public void generate(InitializerContext ctx) throws IOException {
        ProjectSpec spec = ctx.spec();
        String pkg       = spec.resolvedPackage();
        String className = spec.applicationClassName();

        String src = "package " + pkg + ";\n\n"
                + "import org.springframework.boot.SpringApplication;\n"
                + "import org.springframework.boot.autoconfigure.SpringBootApplication;\n\n"
                + "@SpringBootApplication\n"
                + "public class " + className + " {\n\n"
                + "    public static void main(String[] args) {\n"
                + "        SpringApplication.run(" + className + ".class, args);\n"
                + "    }\n"
                + "}\n";

        Path file = ctx.outputRoot()
                .resolve("src/main/java")
                .resolve(spec.packagePath())
                .resolve(className + ".java");
        write(file, src);

        // Test class
        String test = "package " + pkg + ";\n\n"
                + "import org.junit.jupiter.api.Test;\n"
                + "import org.springframework.boot.test.context.SpringBootTest;\n\n"
                + "@SpringBootTest\n"
                + "class " + className + "Tests {\n\n"
                + "    @Test\n"
                + "    void contextLoads() {\n"
                + "    }\n"
                + "}\n";

        Path testFile = ctx.outputRoot()
                .resolve("src/test/java")
                .resolve(spec.packagePath())
                .resolve(className + "Tests.java");
        write(testFile, test);
    }
}
