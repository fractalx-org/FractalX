package com.fractalx.core.generator.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates the Spring Boot Application class for the fractalx-registry service. */
class RegistryApplicationGenerator {

    private static final Logger log = LoggerFactory.getLogger(RegistryApplicationGenerator.class);

    void generate(Path srcMainJava) throws IOException {
        Path pkg = createPkg(srcMainJava, "com/fractalx/registry");

        String content = """
                package com.fractalx.registry;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                import org.springframework.scheduling.annotation.EnableScheduling;

                @SpringBootApplication
                @EnableScheduling
                public class FractalRegistryApplication {

                    public static void main(String[] args) {
                        SpringApplication.run(FractalRegistryApplication.class, args);
                    }
                }
                """;

        Files.writeString(pkg.resolve("FractalRegistryApplication.java"), content);
        log.debug("Generated FractalRegistryApplication");
    }

    private Path createPkg(Path base, String pkg) throws IOException {
        Path p = base;
        for (String part : pkg.split("/")) p = p.resolve(part);
        Files.createDirectories(p);
        return p;
    }
}
