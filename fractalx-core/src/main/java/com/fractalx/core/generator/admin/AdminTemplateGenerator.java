package com.fractalx.core.generator.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates Thymeleaf HTML templates (login + dashboard) for the admin service. */
class AdminTemplateGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminTemplateGenerator.class);

    void generate(Path templatesPath) throws IOException {
        generateLoginTemplate(templatesPath);
        generateDashboardTemplate(templatesPath);
    }

    private void generateLoginTemplate(Path templatesPath) throws IOException {
        String content = """
                <!DOCTYPE html>
                <html xmlns:th="http://www.thymeleaf.org">
                <head>
                    <meta charset="UTF-8">
                    <title>FractalX Admin - Login</title>
                    <link rel="stylesheet" th:href="@{/webjars/bootstrap/5.3.0/css/bootstrap.min.css}">
                    <link rel="stylesheet" th:href="@{/webjars/font-awesome/6.4.0/css/all.min.css}">
                    <style>
                        body { background: linear-gradient(135deg,#667eea 0%,#764ba2 100%);
                               min-height:100vh; display:flex; align-items:center; }
                        .login-card { max-width:400px; margin:0 auto; }
                        .card { border:none; border-radius:1rem;
                                box-shadow:0 0.5rem 1rem rgba(0,0,0,.1); }
                        .btn-primary { background:linear-gradient(135deg,#667eea 0%,#764ba2 100%); border:none; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="login-card">
                            <div class="card">
                                <div class="card-header text-center">
                                    <h1 class="h3 mb-3">
                                        <i class="fas fa-cube text-primary"></i> FractalX Admin
                                    </h1>
                                </div>
                                <div class="card-body p-4">
                                    <div th:if="${param.error}" class="alert alert-danger">Invalid credentials</div>
                                    <div th:if="${param.logout}" class="alert alert-success">You have been logged out</div>
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
                                </div>
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """;
        Files.writeString(templatesPath.resolve("login.html"), content);
        log.debug("Generated login.html");
    }

    private void generateDashboardTemplate(Path templatesPath) throws IOException {
        String content = """
                <!DOCTYPE html>
                <html xmlns:th="http://www.thymeleaf.org">
                <head>
                    <meta charset="UTF-8">
                    <title>FractalX Admin Dashboard</title>
                    <link rel="stylesheet" th:href="@{/webjars/bootstrap/5.3.0/css/bootstrap.min.css}">
                    <link rel="stylesheet" th:href="@{/webjars/font-awesome/6.4.0/css/all.min.css}">
                    <style>
                        .sidebar { position:fixed; top:0; bottom:0; left:0; z-index:100; padding:48px 0 0;
                                   background:linear-gradient(180deg,#667eea 0%,#764ba2 100%); }
                        .sidebar .nav-link { color:rgba(255,255,255,.8); padding:1rem; }
                        .sidebar .nav-link:hover { color:#fff; background:rgba(255,255,255,.1); }
                        main { padding-top:56px; }
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
                                    </ul>
                                </div>
                            </nav>
                            <main class="col-md-9 ms-sm-auto col-lg-10 px-md-4">
                                <div class="d-flex justify-content-between align-items-center pt-3 pb-2 mb-3 border-bottom">
                                    <h1 class="h2">Dashboard</h1>
                                </div>
                                <div class="row mb-4">
                                    <div class="col-md-4 mb-3">
                                        <div class="card border-primary">
                                            <div class="card-body">
                                                <h6 class="text-muted">Total Services</h6>
                                                <h2 th:text="${totalServices}">0</h2>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="col-md-4 mb-3">
                                        <div class="card border-success">
                                            <div class="card-body">
                                                <h6 class="text-muted">Running</h6>
                                                <h2 class="text-success" th:text="${runningServices}">0</h2>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="col-md-4 mb-3">
                                        <div class="card border-danger">
                                            <div class="card-body">
                                                <h6 class="text-muted">Stopped</h6>
                                                <h2 class="text-danger" th:text="${totalServices - runningServices}">0</h2>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                <div class="card">
                                    <div class="card-header"><h5>Services Status</h5></div>
                                    <div class="card-body">
                                        <table class="table table-hover">
                                            <thead>
                                                <tr>
                                                    <th>Service</th><th>URL</th><th>Status</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <tr th:each="service : ${services}">
                                                    <td th:text="${service.name}">Service</td>
                                                    <td><a th:href="${service.url}" target="_blank"
                                                           th:text="${service.url}">URL</a></td>
                                                    <td>
                                                        <span class="badge"
                                                              th:classappend="${'bg-' + service.statusClass}"
                                                              th:text="${service.status}">Status</span>
                                                    </td>
                                                </tr>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </main>
                        </div>
                    </div>
                    <script th:src="@{/webjars/jquery/3.7.0/jquery.min.js}"></script>
                    <script th:src="@{/webjars/bootstrap/5.3.0/js/bootstrap.bundle.min.js}"></script>
                </body>
                </html>
                """;
        Files.writeString(templatesPath.resolve("dashboard.html"), content);
        log.debug("Generated dashboard.html");
    }
}
