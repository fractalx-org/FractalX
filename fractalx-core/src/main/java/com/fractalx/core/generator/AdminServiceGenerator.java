package com.fractalx.core.generator;

import com.fractalx.core.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates the Admin Service for monitoring and managing microservices
 */
public class AdminServiceGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceGenerator.class);

    private static final int DEFAULT_ADMIN_PORT = 9090;
    private static final String ADMIN_SERVICE_NAME = "admin-service";
    private static final String BASE_PACKAGE = "com.fractalx.admin";

    public void generateAdminService(List<FractalModule> modules, Path outputRoot) throws IOException {
        log.info("Generating Admin Service...");

        Path serviceRoot = outputRoot.resolve(ADMIN_SERVICE_NAME);
        Path srcMainJava = serviceRoot.resolve("src/main/java");
        Path srcMainResources = serviceRoot.resolve("src/main/resources");
        Path srcMainResourcesStatic = srcMainResources.resolve("static");
        Path srcMainResourcesTemplates = srcMainResources.resolve("templates");

        // Create directory structure
        Files.createDirectories(srcMainJava);
        Files.createDirectories(srcMainResources);
        Files.createDirectories(srcMainResourcesStatic.resolve("css"));
        Files.createDirectories(srcMainResourcesStatic.resolve("js"));
        Files.createDirectories(srcMainResourcesTemplates);

        // Generate components
        generatePom(serviceRoot);
        generateApplicationClass(srcMainJava);
        generateSecurityConfig(srcMainJava);
        generateWebConfig(srcMainJava);
        generateControllers(srcMainJava, modules);
        generateModels(srcMainJava);
        generateApplicationYml(srcMainResources, modules);
        generateHtmlTemplates(srcMainResourcesTemplates, modules);
        generateStaticAssets(srcMainResourcesStatic);

        log.info("✓ Generated Admin Service on port {}", DEFAULT_ADMIN_PORT);
    }

    private void generatePom(Path serviceRoot) throws IOException {
        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
            
                <groupId>com.fractalx.generated</groupId>
                <artifactId>admin-service</artifactId>
                <version>1.0.0-SNAPSHOT</version>
                <packaging>jar</packaging>
            
                <name>FractalX Admin Service</name>
                <description>Admin dashboard for FractalX microservices</description>
            
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
                    <!-- Spring Boot Starters -->
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-thymeleaf</artifactId>
                    </dependency>
                    
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-security</artifactId>
                    </dependency>
                    
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-actuator</artifactId>
                    </dependency>
                    
                    <!-- FractalX Runtime -->
                    <dependency>
                        <groupId>com.fractalx</groupId>
                        <artifactId>fractalx-runtime</artifactId>
                        <version>0.2.0-SNAPSHOT</version>
                    </dependency>
                    
                    <!-- WebJars for UI -->
                    <dependency>
                        <groupId>org.webjars</groupId>
                        <artifactId>bootstrap</artifactId>
                        <version>5.3.0</version>
                    </dependency>
                    
                    <dependency>
                        <groupId>org.webjars</groupId>
                        <artifactId>jquery</artifactId>
                        <version>3.7.0</version>
                    </dependency>
                    
                    <dependency>
                        <groupId>org.webjars</groupId>
                        <artifactId>font-awesome</artifactId>
                        <version>6.4.0</version>
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

        Files.writeString(serviceRoot.resolve("pom.xml"), pomContent);
        log.debug("Generated pom.xml");
    }

    private void generateApplicationClass(Path srcMainJava) throws IOException {
        Path packagePath = createPackagePath(srcMainJava, BASE_PACKAGE);

        String content = """
            package com.fractalx.admin;
            
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
        log.debug("Generated Application class");
    }

    private void generateSecurityConfig(Path srcMainJava) throws IOException {
        Path packagePath = createPackagePath(srcMainJava, BASE_PACKAGE + ".config");

        String content = """
            package com.fractalx.admin.config;
            
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
            import org.springframework.security.core.userdetails.User;
            import org.springframework.security.core.userdetails.UserDetails;
            import org.springframework.security.core.userdetails.UserDetailsService;
            import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
            import org.springframework.security.crypto.password.PasswordEncoder;
            import org.springframework.security.provisioning.InMemoryUserDetailsManager;
            import org.springframework.security.web.SecurityFilterChain;
            
            @Configuration
            @EnableWebSecurity
            public class SecurityConfig {
                
                @Bean
                public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                    http
                        .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/css/**", "/js/**", "/webjars/**").permitAll()
                            .requestMatchers("/login").permitAll()
                            .anyRequest().authenticated()
                        )
                        .formLogin(form -> form
                            .loginPage("/login")
                            .defaultSuccessUrl("/dashboard", true)
                            .permitAll()
                        )
                        .logout(logout -> logout
                            .logoutSuccessUrl("/login?logout")
                            .permitAll()
                        );
                    
                    return http.build();
                }
                
                @Bean
                public UserDetailsService userDetailsService() {
                    UserDetails admin = User.builder()
                        .username("admin")
                        .password(passwordEncoder().encode("admin123"))
                        .roles("ADMIN")
                        .build();
                    
                    return new InMemoryUserDetailsManager(admin);
                }
                
                @Bean
                public PasswordEncoder passwordEncoder() {
                    return new BCryptPasswordEncoder();
                }
            }
            """;

        Files.writeString(packagePath.resolve("SecurityConfig.java"), content);
        log.debug("Generated SecurityConfig");
    }

    private void generateWebConfig(Path srcMainJava) throws IOException {
        Path packagePath = createPackagePath(srcMainJava, BASE_PACKAGE + ".config");

        String content = """
            package com.fractalx.admin.config;
            
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

    private void generateControllers(Path srcMainJava, List<FractalModule> modules) throws IOException {
        Path packagePath = createPackagePath(srcMainJava, BASE_PACKAGE + ".controller");

        // Dashboard Controller
        String dashboardController = String.format("""
            package com.fractalx.admin.controller;
            
            import com.fractalx.admin.model.ServiceInfo;
            import org.springframework.stereotype.Controller;
            import org.springframework.ui.Model;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.client.RestTemplate;
            
            import java.util.ArrayList;
            import java.util.List;
            
            @Controller
            public class DashboardController {
                
                private final RestTemplate restTemplate = new RestTemplate();
                
                @GetMapping("/dashboard")
                public String dashboard(Model model) {
                    List<ServiceInfo> services = getServiceStatuses();
                    model.addAttribute("services", services);
                    model.addAttribute("totalServices", services.size());
                    model.addAttribute("runningServices", services.stream().filter(ServiceInfo::isRunning).count());
                    return "dashboard";
                }
                
                private List<ServiceInfo> getServiceStatuses() {
                    List<ServiceInfo> services = new ArrayList<>();
                    %s
                    return services;
                }
                
                private boolean checkServiceHealth(String url) {
                    try {
                        restTemplate.getForObject(url, String.class);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
            """, generateServiceChecks(modules));

        Files.writeString(packagePath.resolve("DashboardController.java"), dashboardController);
        log.debug("Generated DashboardController");
    }

    private String generateServiceChecks(List<FractalModule> modules) {
        StringBuilder checks = new StringBuilder();
        for (FractalModule module : modules) {
            checks.append(String.format(
                    "    services.add(new ServiceInfo(\"%s\", \"http://localhost:%d\", checkServiceHealth(\"http://localhost:%d/actuator/health\")));%n",
                    module.getServiceName(), module.getPort(), module.getPort()
            ));
        }
        return checks.toString();
    }

    private void generateModels(Path srcMainJava) throws IOException {
        Path packagePath = createPackagePath(srcMainJava, BASE_PACKAGE + ".model");

        String serviceInfo = """
            package com.fractalx.admin.model;
            
            public class ServiceInfo {
                private String name;
                private String url;
                private boolean running;
                
                public ServiceInfo(String name, String url, boolean running) {
                    this.name = name;
                    this.url = url;
                    this.running = running;
                }
                
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                
                public String getUrl() { return url; }
                public void setUrl(String url) { this.url = url; }
                
                public boolean isRunning() { return running; }
                public void setRunning(boolean running) { this.running = running; }
                
                public String getStatus() {
                    return running ? "Running" : "Stopped";
                }
                
                public String getStatusClass() {
                    return running ? "success" : "danger";
                }
            }
            """;

        Files.writeString(packagePath.resolve("ServiceInfo.java"), serviceInfo);
        log.debug("Generated ServiceInfo model");
    }

    private void generateApplicationYml(Path srcMainResources, List<FractalModule> modules) throws IOException {
        String ymlContent = """
            spring:
              application:
                name: admin-service
              thymeleaf:
                cache: false
              security:
                user:
                  name: admin
                  password: admin123
            
            server:
              port: 9090
            
            management:
              endpoints:
                web:
                  exposure:
                    include: health,info,metrics
            
            logging:
              level:
                com.fractalx.admin: DEBUG
            """;

        Files.writeString(srcMainResources.resolve("application.yml"), ymlContent);
        log.debug("Generated application.yml");
    }

    private void generateHtmlTemplates(Path templatesPath, List<FractalModule> modules) throws IOException {
        // Generate login.html
        generateLoginTemplate(templatesPath);

        // Generate dashboard.html
        generateDashboardTemplate(templatesPath);
    }

    private void generateLoginTemplate(Path templatesPath) throws IOException {
        String loginHtml = """
            <!DOCTYPE html>
            <html xmlns:th="http://www.thymeleaf.org">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>FractalX Admin - Login</title>
                <link rel="stylesheet" th:href="@{/webjars/bootstrap/5.3.0/css/bootstrap.min.css}">
                <link rel="stylesheet" th:href="@{/webjars/font-awesome/6.4.0/css/all.min.css}">
                <style>
                    body {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                    }
                    .login-card {
                        max-width: 400px;
                        margin: 0 auto;
                    }
                    .card {
                        border: none;
                        border-radius: 1rem;
                        box-shadow: 0 0.5rem 1rem 0 rgba(0, 0, 0, 0.1);
                    }
                    .card-header {
                        background: transparent;
                        border-bottom: none;
                        padding: 2rem 2rem 0;
                    }
                    .btn-primary {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        border: none;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="login-card">
                        <div class="card">
                            <div class="card-header text-center">
                                <h1 class="h3 mb-3">
                                    <i class="fas fa-cube text-primary"></i>
                                    FractalX Admin
                                </h1>
                            </div>
                            <div class="card-body p-4">
                                <div th:if="${param.error}" class="alert alert-danger">
                                    Invalid username or password
                                </div>
                                <div th:if="${param.logout}" class="alert alert-success">
                                    You have been logged out
                                </div>
                                <form th:action="@{/login}" method="post">
                                    <div class="mb-3">
                                        <label for="username" class="form-label">Username</label>
                                        <input type="text" class="form-control" id="username" name="username" 
                                               placeholder="admin" required autofocus>
                                    </div>
                                    <div class="mb-3">
                                        <label for="password" class="form-label">Password</label>
                                        <input type="password" class="form-control" id="password" name="password" 
                                               placeholder="Password" required>
                                    </div>
                                    <div class="d-grid">
                                        <button type="submit" class="btn btn-primary btn-lg">
                                            <i class="fas fa-sign-in-alt me-2"></i>Sign In
                                        </button>
                                    </div>
                                </form>
                                <div class="text-center mt-3 text-muted small">
                                    Default credentials: admin / admin123
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """;

        Files.writeString(templatesPath.resolve("login.html"), loginHtml);
        log.debug("Generated login.html");
    }

    private void generateDashboardTemplate(Path templatesPath) throws IOException {
        String dashboardHtml = """
            <!DOCTYPE html>
            <html xmlns:th="http://www.thymeleaf.org">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>FractalX Admin Dashboard</title>
                <link rel="stylesheet" th:href="@{/webjars/bootstrap/5.3.0/css/bootstrap.min.css}">
                <link rel="stylesheet" th:href="@{/webjars/font-awesome/6.4.0/css/all.min.css}">
                <style>
                    .sidebar {
                        position: fixed;
                        top: 0;
                        bottom: 0;
                        left: 0;
                        z-index: 100;
                        padding: 48px 0 0;
                        box-shadow: inset -1px 0 0 rgba(0, 0, 0, .1);
                        background: linear-gradient(180deg, #667eea 0%, #764ba2 100%);
                    }
                    .sidebar-sticky {
                        position: relative;
                        top: 0;
                        height: calc(100vh - 48px);
                        padding-top: .5rem;
                        overflow-x: hidden;
                        overflow-y: auto;
                    }
                    .sidebar .nav-link {
                        color: rgba(255, 255, 255, .8);
                        padding: 1rem;
                    }
                    .sidebar .nav-link:hover {
                        color: #fff;
                        background: rgba(255, 255, 255, .1);
                    }
                    .sidebar .nav-link.active {
                        color: #fff;
                        background: rgba(255, 255, 255, .2);
                    }
                    .navbar {
                        box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, .075);
                    }
                    main {
                        padding-top: 56px;
                    }
                    .stat-card {
                        border-left: 4px solid;
                        transition: transform 0.2s;
                    }
                    .stat-card:hover {
                        transform: translateY(-5px);
                    }
                </style>
            </head>
            <body>
                <nav class="navbar navbar-dark fixed-top bg-dark flex-md-nowrap p-0 shadow">
                    <a class="navbar-brand col-md-3 col-lg-2 me-0 px-3" href="#">
                        <i class="fas fa-cube me-2"></i>FractalX Admin
                    </a>
                    <div class="navbar-nav">
                        <div class="nav-item text-nowrap">
                            <form th:action="@{/logout}" method="post" class="d-inline">
                                <button type="submit" class="btn btn-link nav-link px-3 text-white">
                                    <i class="fas fa-sign-out-alt me-2"></i>Logout
                                </button>
                            </form>
                        </div>
                    </div>
                </nav>
                
                <div class="container-fluid">
                    <div class="row">
                        <nav id="sidebarMenu" class="col-md-3 col-lg-2 d-md-block sidebar collapse">
                            <div class="sidebar-sticky pt-3">
                                <ul class="nav flex-column">
                                    <li class="nav-item">
                                        <a class="nav-link active" href="/dashboard">
                                            <i class="fas fa-home me-2"></i>Dashboard
                                        </a>
                                    </li>
                                    <li class="nav-item">
                                        <a class="nav-link" href="#services">
                                            <i class="fas fa-server me-2"></i>Services
                                        </a>
                                    </li>
                                    <li class="nav-item">
                                        <a class="nav-link" href="#monitoring">
                                            <i class="fas fa-chart-line me-2"></i>Monitoring
                                        </a>
                                    </li>
                                    <li class="nav-item">
                                        <a class="nav-link" href="#logs">
                                            <i class="fas fa-file-alt me-2"></i>Logs
                                        </a>
                                    </li>
                                </ul>
                            </div>
                        </nav>
                        
                        <main class="col-md-9 ms-sm-auto col-lg-10 px-md-4">
                            <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
                                <h1 class="h2">Dashboard</h1>
                            </div>
                            
                            <!-- Statistics Cards -->
                            <div class="row mb-4">
                                <div class="col-md-4 mb-3">
                                    <div class="card stat-card border-primary">
                                        <div class="card-body">
                                            <div class="d-flex justify-content-between">
                                                <div>
                                                    <h6 class="text-muted mb-1">Total Services</h6>
                                                    <h2 class="mb-0" th:text="${totalServices}">0</h2>
                                                </div>
                                                <div class="text-primary">
                                                    <i class="fas fa-server fa-3x"></i>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                <div class="col-md-4 mb-3">
                                    <div class="card stat-card border-success">
                                        <div class="card-body">
                                            <div class="d-flex justify-content-between">
                                                <div>
                                                    <h6 class="text-muted mb-1">Running</h6>
                                                    <h2 class="mb-0 text-success" th:text="${runningServices}">0</h2>
                                                </div>
                                                <div class="text-success">
                                                    <i class="fas fa-check-circle fa-3x"></i>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                <div class="col-md-4 mb-3">
                                    <div class="card stat-card border-danger">
                                        <div class="card-body">
                                            <div class="d-flex justify-content-between">
                                                <div>
                                                    <h6 class="text-muted mb-1">Stopped</h6>
                                                    <h2 class="mb-0 text-danger" th:text="${totalServices - runningServices}">0</h2>
                                                </div>
                                                <div class="text-danger">
                                                    <i class="fas fa-exclamation-circle fa-3x"></i>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            
                            <!-- Services Table -->
                            <div class="card">
                                <div class="card-header">
                                    <h5 class="mb-0"><i class="fas fa-list me-2"></i>Services Status</h5>
                                </div>
                                <div class="card-body">
                                    <div class="table-responsive">
                                        <table class="table table-hover">
                                            <thead>
                                                <tr>
                                                    <th>Service Name</th>
                                                    <th>URL</th>
                                                    <th>Status</th>
                                                    <th>Actions</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <tr th:each="service : ${services}">
                                                    <td>
                                                        <i class="fas fa-cube me-2"></i>
                                                        <span th:text="${service.name}">Service</span>
                                                    </td>
                                                    <td>
                                                        <a th:href="${service.url}" target="_blank" th:text="${service.url}">URL</a>
                                                    </td>
                                                    <td>
                                                        <span class="badge" th:classappend="${'bg-' + service.statusClass}" 
                                                              th:text="${service.status}">Status</span>
                                                    </td>
                                                    <td>
                                                        <button class="btn btn-sm btn-outline-primary">
                                                            <i class="fas fa-chart-line"></i>
                                                        </button>
                                                        <button class="btn btn-sm btn-outline-info">
                                                            <i class="fas fa-info-circle"></i>
                                                        </button>
                                                    </td>
                                                </tr>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </div>
                        </main>
                    </div>
                </div>
                
                <script th:src="@{/webjars/jquery/3.7.0/jquery.min.js}"></script>
                <script th:src="@{/webjars/bootstrap/5.3.0/js/bootstrap.bundle.min.js}"></script>
                /*<script>
                    // Auto-refresh every 5 seconds
                    setTimeout(function() {
                        location.reload();
                    }, 600000);
                </script>*/
            </body>
            </html>
            """;

        Files.writeString(templatesPath.resolve("dashboard.html"), dashboardHtml);
        log.debug("Generated dashboard.html");
    }

    private void generateStaticAssets(Path staticPath) throws IOException {
        // Generate custom CSS
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

    private Path createPackagePath(Path srcMainJava, String packageName) throws IOException {
        String[] parts = packageName.split("\\.");
        Path packagePath = srcMainJava;
        for (String part : parts) {
            packagePath = packagePath.resolve(part);
        }
        Files.createDirectories(packagePath);
        return packagePath;
    }
}
