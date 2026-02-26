package com.fractalx.core.generator.admin;

import com.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Orchestrates generation of the admin service project.
 *
 * <p>Each concern is delegated to a focused sub-generator:
 * <ul>
 *   <li>{@link AdminPomGenerator} — pom.xml</li>
 *   <li>{@link AdminAppGenerator} — Spring Boot Application class</li>
 *   <li>{@link AdminSecurityConfigGenerator} — Spring Security config</li>
 *   <li>{@link AdminWebConfigGenerator} — MVC web config</li>
 *   <li>{@link AdminControllerGenerator} — Dashboard controller</li>
 *   <li>{@link AdminModelGenerator} — ServiceInfo model</li>
 *   <li>{@link AdminConfigGenerator} — application.yml</li>
 *   <li>{@link AdminTemplateGenerator} — Thymeleaf HTML templates</li>
 *   <li>{@link AdminStaticAssetsGenerator} — CSS/JS static files</li>
 * </ul>
 */
public class AdminServiceGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceGenerator.class);

    private static final int    ADMIN_PORT         = 9090;
    private static final String ADMIN_SERVICE_NAME = "admin-service";
    private static final String BASE_PACKAGE       = "com.fractalx.admin";

    private final AdminPomGenerator           pomGenerator;
    private final AdminAppGenerator           appGenerator;
    private final AdminSecurityConfigGenerator securityConfigGenerator;
    private final AdminWebConfigGenerator     webConfigGenerator;
    private final AdminControllerGenerator    controllerGenerator;
    private final AdminModelGenerator         modelGenerator;
    private final AdminConfigGenerator        configGenerator;
    private final AdminTemplateGenerator      templateGenerator;
    private final AdminStaticAssetsGenerator  staticAssetsGenerator;
    private final AdminTopologyGenerator      topologyGenerator;

    public AdminServiceGenerator() {
        this.pomGenerator            = new AdminPomGenerator();
        this.appGenerator            = new AdminAppGenerator();
        this.securityConfigGenerator = new AdminSecurityConfigGenerator();
        this.webConfigGenerator      = new AdminWebConfigGenerator();
        this.controllerGenerator     = new AdminControllerGenerator();
        this.modelGenerator          = new AdminModelGenerator();
        this.configGenerator         = new AdminConfigGenerator();
        this.templateGenerator       = new AdminTemplateGenerator();
        this.staticAssetsGenerator   = new AdminStaticAssetsGenerator();
        this.topologyGenerator       = new AdminTopologyGenerator();
    }

    public void generateAdminService(List<FractalModule> modules, Path outputRoot) throws IOException {
        log.info("Generating Admin Service...");

        Path serviceRoot  = outputRoot.resolve(ADMIN_SERVICE_NAME);
        Path srcMainJava  = serviceRoot.resolve("src/main/java");
        Path srcMainRes   = serviceRoot.resolve("src/main/resources");
        Path staticPath   = srcMainRes.resolve("static");
        Path templatesPath= srcMainRes.resolve("templates");

        Files.createDirectories(srcMainJava);
        Files.createDirectories(srcMainRes);
        Files.createDirectories(staticPath.resolve("css"));
        Files.createDirectories(staticPath.resolve("js"));
        Files.createDirectories(templatesPath);

        pomGenerator.generate(serviceRoot);
        appGenerator.generate(srcMainJava, BASE_PACKAGE);
        securityConfigGenerator.generate(srcMainJava, BASE_PACKAGE);
        webConfigGenerator.generate(srcMainJava, BASE_PACKAGE);
        controllerGenerator.generate(srcMainJava, BASE_PACKAGE, modules);
        modelGenerator.generate(srcMainJava, BASE_PACKAGE);
        configGenerator.generate(srcMainRes);
        templateGenerator.generate(templatesPath);
        staticAssetsGenerator.generate(staticPath);
        topologyGenerator.generate(srcMainJava, BASE_PACKAGE, modules);

        log.info("Generated Admin Service on port {}", ADMIN_PORT);
    }
}
