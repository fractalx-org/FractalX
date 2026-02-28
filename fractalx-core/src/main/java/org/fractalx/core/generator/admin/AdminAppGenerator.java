package org.fractalx.core.generator.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates the Spring Boot Application class for the admin service. */
class AdminAppGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminAppGenerator.class);

    void generate(Path srcMainJava, String basePackage) throws IOException {
        Path packagePath = AdminPackageUtil.createPackagePath(srcMainJava, basePackage);
        String content = """
                package org.fractalx.admin;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                import org.springframework.scheduling.annotation.EnableScheduling;

                @SpringBootApplication
                @EnableScheduling
                public class AdminServiceApplication {

                    public static void main(String[] args) {
                        SpringApplication.run(AdminServiceApplication.class, args);
                    }
                }
                """;
        Files.writeString(packagePath.resolve("AdminServiceApplication.java"), content);
        log.debug("Generated AdminServiceApplication");
    }
}
