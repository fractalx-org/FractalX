package org.fractalx.core.generator.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates Spring MVC web configuration for the admin service. */
class AdminWebConfigGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminWebConfigGenerator.class);

    void generate(Path srcMainJava, String basePackage) throws IOException {
        Path packagePath = AdminPackageUtil.createPackagePath(srcMainJava, basePackage + ".config");
        String content = """
                package org.fractalx.admin.config;

                import org.springframework.context.annotation.Configuration;
                import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
                import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

                @Configuration
                public class WebConfig implements WebMvcConfigurer {

                    @Override
                    public void addViewControllers(ViewControllerRegistry registry) {
                        registry.addViewController("/").setViewName("redirect:/dashboard");
                        registry.addViewController("/login").setViewName("login");
                    }
                }
                """;
        Files.writeString(packagePath.resolve("WebConfig.java"), content);
        log.debug("Generated WebConfig");
    }
}
