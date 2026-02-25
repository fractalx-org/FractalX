package com.fractalx.core.generator.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates static CSS/JS assets for the admin service. */
class AdminStaticAssetsGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminStaticAssetsGenerator.class);

    void generate(Path staticPath) throws IOException {
        String customCss = """
                /* Custom styles for FractalX Admin */
                .stat-card {
                    transition: all 0.3s ease;
                }
                .stat-card:hover {
                    box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.15);
                }
                """;
        Files.writeString(staticPath.resolve("css").resolve("custom.css"), customCss);
        log.debug("Generated custom.css");
    }
}
