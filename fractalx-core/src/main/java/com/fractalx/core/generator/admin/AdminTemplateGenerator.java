package com.fractalx.core.generator.admin;

import com.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Generates Thymeleaf HTML templates (login + 9-section dashboard) for the admin service. */
class AdminTemplateGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminTemplateGenerator.class);

    /** @deprecated Use {@link #generate(Path, List)} instead. */
    void generate(Path templatesPath) throws IOException {
        generate(templatesPath, List.of());
    }

    void generate(Path templatesPath, List<FractalModule> modules) throws IOException {
        generateLoginTemplate(templatesPath);
        generateDashboardTemplate(templatesPath, modules);
        log.debug("Generated admin templates (login + 9-section dashboard)");
    }

    // -------------------------------------------------------------------------

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
                        .login-card { max-width:420px; margin:0 auto; width:100%; }
                        .card { border:none; border-radius:1rem; box-shadow:0 1rem 3rem rgba(0,0,0,.2); }
                        .card-header { background:transparent; border-bottom:none; padding:2rem 2rem 0; }
                        .btn-primary { background:linear-gradient(135deg,#667eea 0%,#764ba2 100%); border:none; }
                        .brand-icon { font-size:2.5rem; color:#667eea; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="login-card">
                            <div class="card">
                                <div class="card-header text-center">
                                    <div class="brand-icon mb-2"><i class="fas fa-cubes"></i></div>
                                    <h2 class="fw-bold mb-0">FractalX Admin</h2>
                                    <p class="text-muted small">Microservices Management Dashboard</p>
                                </div>
                                <div class="card-body p-4">
                                    <div th:if="${param.error}" class="alert alert-danger small">
                                        Invalid username or password.
                                    </div>
                                    <div th:if="${param.logout}" class="alert alert-success small">
                                        You have been logged out.
                                    </div>
                                    <form th:action="@{/login}" method="post">
                                        <div class="mb-3">
                                            <label class="form-label fw-semibold">Username</label>
                                            <input type="text" name="username" class="form-control"
                                                   placeholder="admin" autofocus required>
                                        </div>
                                        <div class="mb-4">
                                            <label class="form-label fw-semibold">Password</label>
                                            <input type="password" name="password" class="form-control"
                                                   placeholder="&bull;&bull;&bull;&bull;&bull;&bull;&bull;&bull;" required>
                                        </div>
                                        <button type="submit" class="btn btn-primary w-100 py-2 fw-semibold">
                                            <i class="fas fa-sign-in-alt me-2"></i>Sign In
                                        </button>
                                    </form>
                                </div>
                                <div class="card-footer text-center text-muted small border-0 pb-3">
                                    FractalX v0.3.2 &mdash; Microservices Framework
                                </div>
                            </div>
                        </div>
                    </div>
                    <script th:src="@{/webjars/bootstrap/5.3.0/js/bootstrap.bundle.min.js}"></script>
                </body>
                </html>
                """;
        Files.writeString(templatesPath.resolve("login.html"), content);
    }

    private void generateDashboardTemplate(Path templatesPath, List<FractalModule> modules)
            throws IOException {
        String content = buildDashboard();
        Files.writeString(templatesPath.resolve("dashboard.html"), content);
    }

    // -------------------------------------------------------------------------
    // Dashboard HTML — split across helper methods to stay under JVM 65535-byte limit
    // -------------------------------------------------------------------------

    private String buildDashboard() {
        return buildHtmlHead()
            + "<body>\n"
            + buildSidebar()
            + "\n<div class=\"main-content\">\n"
            + buildTopbar()
            + buildSectionOverview()
            + buildSectionServices()
            + buildSectionCommunication()
            + buildSectionData()
            + buildSectionObservability()
            + buildSectionAlerts()
            + buildSectionTracesLogs()
            + buildSectionSettings()
            + "</div>\n\n"
            + buildModals()
            + "<script th:src=\"@{/webjars/jquery/3.7.0/jquery.min.js}\"></script>\n"
            + "<script th:src=\"@{/webjars/bootstrap/5.3.0/js/bootstrap.bundle.min.js}\"></script>\n"
            + "<script>\n"
            + buildScriptsNavAndOverview()
            + buildScriptsServices()
            + buildScriptsCommunication()
            + buildScriptsData()
            + buildScriptsObservabilityAndAlerts()
            + buildScriptsTracesLogsSettings()
            + "</script>\n</body>\n</html>\n";
    }

    // ---- HTML HEAD ----------------------------------------------------------

    private String buildHtmlHead() {
        return """
                <!DOCTYPE html>
                <html xmlns:th="http://www.thymeleaf.org" lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>FractalX Admin Dashboard</title>
                    <link rel="stylesheet" th:href="@{/webjars/bootstrap/5.3.0/css/bootstrap.min.css}">
                    <link rel="stylesheet" th:href="@{/webjars/font-awesome/6.4.0/css/all.min.css}">
                    <style>
                        :root { --sidebar-width: 220px; --sidebar-bg: #1e293b; --sidebar-text: #94a3b8;
                                --accent: #6366f1; --accent2: #8b5cf6; }
                        body { margin: 0; font-family: 'Segoe UI', sans-serif; background: #f1f5f9; }
                        .sidebar { position: fixed; top: 0; left: 0; height: 100vh;
                                   width: var(--sidebar-width); background: var(--sidebar-bg);
                                   display: flex; flex-direction: column; z-index: 1000; overflow-y: auto; }
                        .sidebar-brand { padding: 1.25rem 1rem; border-bottom: 1px solid #334155;
                                         color: #f1f5f9; font-weight: 700; font-size: 1.1rem;
                                         display: flex; align-items: center; gap: .6rem; }
                        .sidebar-brand i { color: var(--accent); font-size: 1.4rem; }
                        .nav-section { padding: .5rem 1rem .25rem; font-size: .7rem;
                                       color: #475569; text-transform: uppercase; letter-spacing: .08em; }
                        .sidebar-nav a { display: flex; align-items: center; gap: .7rem;
                                         padding: .55rem 1rem; color: var(--sidebar-text);
                                         text-decoration: none; font-size: .9rem; transition: all .2s; }
                        .sidebar-nav a:hover, .sidebar-nav a.active {
                            background: #334155; color: #f1f5f9; border-left: 3px solid var(--accent); }
                        .sidebar-nav a i { width: 18px; text-align: center; }
                        .alert-badge { background: #ef4444; color: #fff; border-radius: 999px;
                                       padding: 1px 6px; font-size: .7rem; }
                        .main-content { margin-left: var(--sidebar-width); padding: 1.5rem; min-height: 100vh; }
                        .topbar { background: #fff; border-radius: .75rem; padding: .75rem 1.25rem;
                                  margin-bottom: 1.5rem; display: flex; justify-content: space-between;
                                  align-items: center; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
                        .section { display: none; }
                        .section.active { display: block; }
                        .stat-card { background: #fff; border-radius: .75rem; padding: 1.25rem;
                                     box-shadow: 0 1px 4px rgba(0,0,0,.06); transition: transform .2s; }
                        .stat-card:hover { transform: translateY(-2px); }
                        .stat-card .stat-icon { width: 48px; height: 48px; border-radius: .6rem;
                                                display: flex; align-items: center; justify-content: center;
                                                font-size: 1.4rem; }
                        .stat-card .stat-value { font-size: 1.8rem; font-weight: 700; color: #1e293b; }
                        .stat-card .stat-label { color: #64748b; font-size: .85rem; }
                        .table-card { background: #fff; border-radius: .75rem;
                                      box-shadow: 0 1px 4px rgba(0,0,0,.06); overflow: hidden; }
                        .table-card .card-header { padding: 1rem 1.25rem; border-bottom: 1px solid #f1f5f9;
                                                   font-weight: 600; background: #fff; }
                        .badge-up { background: #dcfce7; color: #166534; }
                        .badge-down { background: #fee2e2; color: #991b1b; }
                        .badge-unknown { background: #f1f5f9; color: #475569; }
                        .topology-grid { display: flex; flex-wrap: wrap; gap: 1rem; padding: 1rem; }
                        .topo-node { background: #fff; border: 2px solid #e2e8f0; border-radius: .75rem;
                                     padding: .75rem 1rem; text-align: center; min-width: 130px;
                                     position: relative; font-size: .85rem; }
                        .topo-node.microservice { border-color: var(--accent); }
                        .topo-node.infrastructure { border-color: #10b981; }
                        .topo-node .node-type { font-size: .7rem; color: #94a3b8; text-transform: uppercase; }
                        .topo-node .node-port { font-size: .75rem; color: #64748b; }
                        .cmd-box { background: #1e293b; color: #86efac; font-family: monospace;
                                   padding: .5rem .75rem; border-radius: .5rem; font-size: .85rem;
                                   margin: .25rem 0; cursor: pointer; user-select: all; }
                        .settings-tabs { border-bottom: 2px solid #e2e8f0; margin-bottom: 1.25rem; }
                        .settings-tabs .tab-btn { background: none; border: none; padding: .6rem 1.2rem;
                                                   color: #64748b; font-size: .9rem; cursor: pointer;
                                                   border-bottom: 2px solid transparent; margin-bottom: -2px; }
                        .settings-tabs .tab-btn.active {
                            color: var(--accent); border-bottom-color: var(--accent); font-weight: 600; }
                        .settings-pane { display: none; }
                        .settings-pane.active { display: block; }
                    </style>
                </head>
                """;
    }

    // ---- SIDEBAR ------------------------------------------------------------

    private String buildSidebar() {
        return """
                <div class="sidebar">
                    <div class="sidebar-brand">
                        <i class="fas fa-cubes"></i> FractalX Admin
                    </div>
                    <nav class="sidebar-nav">
                        <div class="nav-section">Main</div>
                        <a href="#" onclick="showSection('overview')" id="nav-overview" class="active">
                            <i class="fas fa-th-large"></i> Overview
                        </a>
                        <a href="#" onclick="showSection('services')" id="nav-services">
                            <i class="fas fa-server"></i> Services
                        </a>
                        <div class="nav-section">Architecture</div>
                        <a href="#" onclick="showSection('communication')" id="nav-communication">
                            <i class="fas fa-project-diagram"></i> Communication
                        </a>
                        <a href="#" onclick="showSection('data')" id="nav-data">
                            <i class="fas fa-database"></i> Data Consistency
                        </a>
                        <div class="nav-section">Monitoring</div>
                        <a href="#" onclick="showSection('observability')" id="nav-observability">
                            <i class="fas fa-chart-line"></i> Observability
                        </a>
                        <a href="#" onclick="showSection('alerts')" id="nav-alerts">
                            <i class="fas fa-bell"></i> Alerts
                            <span class="alert-badge" id="alert-badge" style="display:none">0</span>
                        </a>
                        <a href="#" onclick="showSection('traces')" id="nav-traces">
                            <i class="fas fa-route"></i> Traces
                        </a>
                        <a href="#" onclick="showSection('logs')" id="nav-logs">
                            <i class="fas fa-file-alt"></i> Logs
                        </a>
                        <div class="nav-section">Admin</div>
                        <a href="#" onclick="showSection('settings')" id="nav-settings">
                            <i class="fas fa-cog"></i> Settings
                        </a>
                    </nav>
                    <div class="mt-auto p-3" style="border-top:1px solid #334155">
                        <a href="/logout" class="text-danger text-decoration-none small">
                            <i class="fas fa-sign-out-alt me-1"></i> Logout
                        </a>
                    </div>
                </div>
                """;
    }

    // ---- TOPBAR -------------------------------------------------------------

    private String buildTopbar() {
        return """
                    <div class="topbar">
                        <h5 class="mb-0 fw-semibold" id="page-title">Overview</h5>
                        <div class="d-flex align-items-center gap-3">
                            <span class="text-muted small">
                                <i class="fas fa-clock me-1"></i>
                                <span id="last-refresh">Never</span>
                            </span>
                            <button class="btn btn-sm btn-outline-primary" onclick="refreshCurrent()">
                                <i class="fas fa-sync-alt me-1"></i>Refresh
                            </button>
                            <a href="/api/auth/profile" class="text-decoration-none small">
                                <i class="fas fa-user-circle text-muted me-1"></i>
                                <span id="current-user">admin</span>
                            </a>
                        </div>
                    </div>
                """;
    }

    // ---- SECTIONS -----------------------------------------------------------

    private String buildSectionOverview() {
        return """
                    <div id="section-overview" class="section active">
                        <div class="row g-3 mb-4">
                            <div class="col-6 col-lg-3">
                                <div class="stat-card">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="stat-icon" style="background:#ede9fe">
                                            <i class="fas fa-server text-primary"></i>
                                        </div>
                                        <div>
                                            <div class="stat-value" id="ov-total">-</div>
                                            <div class="stat-label">Total Services</div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div class="col-6 col-lg-3">
                                <div class="stat-card">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="stat-icon" style="background:#dcfce7">
                                            <i class="fas fa-check-circle" style="color:#16a34a"></i>
                                        </div>
                                        <div>
                                            <div class="stat-value" id="ov-up">-</div>
                                            <div class="stat-label">Running</div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div class="col-6 col-lg-3">
                                <div class="stat-card">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="stat-icon" style="background:#fee2e2">
                                            <i class="fas fa-exclamation-circle text-danger"></i>
                                        </div>
                                        <div>
                                            <div class="stat-value" id="ov-down">-</div>
                                            <div class="stat-label">Down / Unknown</div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div class="col-6 col-lg-3">
                                <div class="stat-card">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="stat-icon" style="background:#fef3c7">
                                            <i class="fas fa-bell" style="color:#d97706"></i>
                                        </div>
                                        <div>
                                            <div class="stat-value" id="ov-alerts">-</div>
                                            <div class="stat-label">Active Alerts</div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="table-card">
                            <div class="card-header d-flex justify-content-between">
                                <span><i class="fas fa-heartbeat me-2 text-success"></i>Quick Health Status</span>
                                <button class="btn btn-sm btn-light" onclick="loadOverview()">
                                    <i class="fas fa-sync-alt"></i>
                                </button>
                            </div>
                            <div class="p-3">
                                <table class="table table-sm table-hover mb-0">
                                    <thead><tr><th>Service</th><th>Status</th><th>Port</th><th>Type</th><th>Actions</th></tr></thead>
                                    <tbody id="overview-tbody">
                                        <tr><td colspan="5" class="text-muted text-center">Loading...</td></tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                """;
    }

    private String buildSectionServices() {
        return """
                    <div id="section-services" class="section">
                        <div class="table-card mb-3">
                            <div class="card-header d-flex justify-content-between align-items-center">
                                <span><i class="fas fa-server me-2 text-primary"></i>All Services</span>
                                <div class="d-flex gap-2">
                                    <input type="text" class="form-control form-control-sm" id="svc-filter"
                                           placeholder="Filter..." style="width:160px"
                                           oninput="filterServicesTable(this.value)">
                                    <button class="btn btn-sm btn-primary" onclick="loadServicesAll()">
                                        <i class="fas fa-sync-alt me-1"></i>Refresh
                                    </button>
                                </div>
                            </div>
                            <div class="p-3">
                                <table class="table table-sm table-hover mb-0" id="services-table">
                                    <thead>
                                        <tr>
                                            <th>Name</th><th>Type</th><th>Port</th><th>gRPC</th>
                                            <th>Health</th><th>Dependencies</th><th>Deployment</th><th>Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody id="services-tbody">
                                        <tr><td colspan="8" class="text-muted text-center">Loading...</td></tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                """;
    }

    private String buildSectionCommunication() {
        return """
                    <div id="section-communication" class="section">
                        <div class="table-card mb-3">
                            <div class="card-header">
                                <i class="fas fa-project-diagram me-2 text-primary"></i>Service Dependency Topology
                            </div>
                            <div id="topology-grid" class="topology-grid">
                                <div class="text-muted p-3">Loading topology...</div>
                            </div>
                        </div>
                        <div class="row g-3">
                            <div class="col-lg-6">
                                <div class="table-card">
                                    <div class="card-header">
                                        <i class="fas fa-network-wired me-2 text-info"></i>NetScope / gRPC Links
                                    </div>
                                    <div class="p-3">
                                        <table class="table table-sm mb-0">
                                            <thead><tr><th>Source</th><th>Target</th><th>gRPC Port</th><th>Protocol</th></tr></thead>
                                            <tbody id="netscope-tbody">
                                                <tr><td colspan="4" class="text-muted">Loading...</td></tr>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </div>
                            <div class="col-lg-6">
                                <div class="table-card mb-3">
                                    <div class="card-header">
                                        <i class="fas fa-exchange-alt me-2 text-success"></i>API Gateway
                                    </div>
                                    <div class="p-3" id="gateway-info"><span class="text-muted">Loading...</span></div>
                                </div>
                                <div class="table-card">
                                    <div class="card-header">
                                        <i class="fas fa-satellite-dish me-2 text-warning"></i>Service Discovery
                                    </div>
                                    <div class="p-3" id="discovery-info"><span class="text-muted">Loading...</span></div>
                                </div>
                            </div>
                        </div>
                    </div>
                """;
    }

    private String buildSectionData() {
        return """
                    <div id="section-data" class="section">
                        <div class="row g-3 mb-3">
                            <div class="col-md-4">
                                <div class="stat-card">
                                    <div class="stat-value" id="data-saga-count">-</div>
                                    <div class="stat-label">Saga Definitions</div>
                                </div>
                            </div>
                            <div class="col-md-4">
                                <div class="stat-card">
                                    <div class="stat-value" id="data-svc-count">-</div>
                                    <div class="stat-label">Services with DB</div>
                                </div>
                            </div>
                            <div class="col-md-4">
                                <div class="stat-card">
                                    <div class="stat-value" id="data-orch-health">-</div>
                                    <div class="stat-label">Saga Orchestrator</div>
                                </div>
                            </div>
                        </div>
                        <div class="row g-3">
                            <div class="col-lg-6">
                                <div class="table-card">
                                    <div class="card-header d-flex justify-content-between">
                                        <span><i class="fas fa-sitemap me-2 text-primary"></i>Saga Definitions</span>
                                        <button class="btn btn-sm btn-outline-primary" onclick="loadSagaInstances()">
                                            View Instances
                                        </button>
                                    </div>
                                    <div class="p-3">
                                        <table class="table table-sm mb-0">
                                            <thead><tr><th>Saga ID</th><th>Service</th><th>Steps</th><th>Compensation</th></tr></thead>
                                            <tbody id="sagas-tbody">
                                                <tr><td colspan="4" class="text-muted">Loading...</td></tr>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </div>
                            <div class="col-lg-6">
                                <div class="table-card">
                                    <div class="card-header">
                                        <i class="fas fa-database me-2 text-warning"></i>Database Health
                                    </div>
                                    <div class="p-3">
                                        <table class="table table-sm mb-0">
                                            <thead><tr><th>Service</th><th>Schemas</th><th>Health</th></tr></thead>
                                            <tbody id="databases-tbody">
                                                <tr><td colspan="3" class="text-muted">Loading...</td></tr>
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="table-card mt-3">
                            <div class="card-header"><i class="fas fa-inbox me-2 text-success"></i>Outbox Events</div>
                            <div class="p-3" id="outbox-info"><span class="text-muted">Loading...</span></div>
                        </div>
                    </div>
                """;
    }

    private String buildSectionObservability() {
        return """
                    <div id="section-observability" class="section">
                        <div class="row g-3 mb-3" id="metrics-cards">
                            <div class="col-12 text-muted text-center p-4">Loading metrics...</div>
                        </div>
                        <div class="table-card">
                            <div class="card-header d-flex justify-content-between">
                                <span><i class="fas fa-chart-bar me-2 text-primary"></i>Service Health Metrics</span>
                                <button class="btn btn-sm btn-primary" onclick="loadMetrics()">
                                    <i class="fas fa-sync-alt me-1"></i>Refresh
                                </button>
                            </div>
                            <div class="p-3">
                                <table class="table table-sm mb-0">
                                    <thead><tr><th>Service</th><th>Health</th><th>Response P99</th><th>Error Rate</th><th>Uptime</th></tr></thead>
                                    <tbody id="metrics-tbody">
                                        <tr><td colspan="5" class="text-muted">Loading...</td></tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                        <div class="table-card mt-3">
                            <div class="card-header">
                                <i class="fas fa-broadcast-tower me-2 text-info"></i>OpenTelemetry Configuration
                            </div>
                            <div class="p-3">
                                <div class="row g-2">
                                    <div class="col-md-6">
                                        <small class="text-muted">OTLP Endpoint</small>
                                        <div class="cmd-box">${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}</div>
                                    </div>
                                    <div class="col-md-6">
                                        <small class="text-muted">Jaeger UI</small>
                                        <div class="cmd-box">
                                            <a href="http://localhost:16686" target="_blank" class="text-success">
                                                http://localhost:16686 <i class="fas fa-external-link-alt fa-xs"></i>
                                            </a>
                                        </div>
                                    </div>
                                </div>
                                <div class="mt-2 small text-muted">
                                    All services export spans via OTLP/gRPC to Jaeger.
                                    Correlation IDs propagated via W3C <code>traceparent</code> + <code>X-Correlation-Id</code>.
                                </div>
                            </div>
                        </div>
                    </div>
                """;
    }

    private String buildSectionAlerts() {
        return """
                    <div id="section-alerts" class="section">
                        <div class="table-card mb-3">
                            <div class="card-header d-flex justify-content-between">
                                <span>
                                    <i class="fas fa-exclamation-triangle me-2 text-warning"></i>
                                    Active Alerts <span class="badge bg-danger ms-1" id="active-alert-count">0</span>
                                </span>
                                <button class="btn btn-sm btn-primary" onclick="loadAlerts()">
                                    <i class="fas fa-sync-alt me-1"></i>Refresh
                                </button>
                            </div>
                            <div class="p-3">
                                <table class="table table-sm table-hover mb-0">
                                    <thead><tr><th>Time</th><th>Service</th><th>Severity</th><th>Message</th><th>Action</th></tr></thead>
                                    <tbody id="active-alerts-tbody">
                                        <tr><td colspan="5" class="text-muted text-center">No active alerts</td></tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                        <div class="table-card">
                            <div class="card-header"><i class="fas fa-history me-2 text-secondary"></i>Alert History</div>
                            <div class="p-3">
                                <table class="table table-sm table-hover mb-0">
                                    <thead><tr><th>Time</th><th>Service</th><th>Severity</th><th>Message</th><th>Status</th></tr></thead>
                                    <tbody id="alert-history-tbody">
                                        <tr><td colspan="5" class="text-muted text-center">No alerts recorded</td></tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                        <div class="table-card mt-3">
                            <div class="card-header"><i class="fas fa-sliders-h me-2 text-info"></i>Alert Configuration</div>
                            <div class="p-3" id="alert-config-info"><span class="text-muted">Loading...</span></div>
                        </div>
                    </div>
                """;
    }

    private String buildSectionTracesLogs() {
        return """
                    <div id="section-traces" class="section">
                        <div class="table-card mb-3">
                            <div class="card-header"><i class="fas fa-route me-2 text-primary"></i>Distributed Trace Search</div>
                            <div class="p-3">
                                <div class="row g-2 mb-3">
                                    <div class="col-md-5">
                                        <input type="text" class="form-control form-control-sm" id="trace-correlation-id"
                                               placeholder="Correlation ID (X-Correlation-Id)">
                                    </div>
                                    <div class="col-md-4">
                                        <select class="form-select form-select-sm" id="trace-service-select">
                                            <option value="">-- All Services --</option>
                                        </select>
                                    </div>
                                    <div class="col-md-3">
                                        <button class="btn btn-sm btn-primary w-100" onclick="searchTraces()">
                                            <i class="fas fa-search me-1"></i>Search Traces
                                        </button>
                                    </div>
                                </div>
                                <table class="table table-sm mb-0">
                                    <thead><tr><th>Trace ID</th><th>Service</th><th>Duration</th><th>Spans</th><th>Jaeger Link</th></tr></thead>
                                    <tbody id="traces-tbody">
                                        <tr><td colspan="5" class="text-muted text-center">Enter a Correlation ID or service to search</td></tr>
                                    </tbody>
                                </table>
                                <div class="mt-2 small">
                                    <a href="http://localhost:16686" target="_blank" class="text-info">
                                        <i class="fas fa-external-link-alt me-1"></i>Open Jaeger UI
                                    </a>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div id="section-logs" class="section">
                        <div class="table-card">
                            <div class="card-header"><i class="fas fa-file-alt me-2 text-secondary"></i>Log Viewer</div>
                            <div class="p-3">
                                <div class="row g-2 mb-3">
                                    <div class="col-md-3">
                                        <input type="text" class="form-control form-control-sm" id="log-correlation-id"
                                               placeholder="Correlation ID">
                                    </div>
                                    <div class="col-md-3">
                                        <select class="form-select form-select-sm" id="log-service-select">
                                            <option value="">-- All Services --</option>
                                        </select>
                                    </div>
                                    <div class="col-md-2">
                                        <select class="form-select form-select-sm" id="log-level-select">
                                            <option value="">All Levels</option>
                                            <option>INFO</option><option>WARN</option>
                                            <option>ERROR</option><option>DEBUG</option>
                                        </select>
                                    </div>
                                    <div class="col-md-2">
                                        <button class="btn btn-sm btn-primary w-100" onclick="searchLogs(0)">
                                            <i class="fas fa-search me-1"></i>Search
                                        </button>
                                    </div>
                                    <div class="col-md-2">
                                        <button class="btn btn-sm btn-outline-secondary w-100" onclick="loadLogStats()">
                                            <i class="fas fa-chart-pie me-1"></i>Stats
                                        </button>
                                    </div>
                                </div>
                                <table class="table table-sm table-hover mb-2" style="font-size:.82rem">
                                    <thead><tr><th>Time</th><th>Service</th><th>Level</th><th>Correlation ID</th><th>Message</th></tr></thead>
                                    <tbody id="logs-tbody">
                                        <tr><td colspan="5" class="text-muted text-center">Use filters above to search logs</td></tr>
                                    </tbody>
                                </table>
                                <div id="log-pagination" class="d-flex gap-2 flex-wrap"></div>
                            </div>
                        </div>
                        <div class="table-card mt-3" id="log-stats-card" style="display:none">
                            <div class="card-header"><i class="fas fa-chart-pie me-2"></i>Log Statistics</div>
                            <div class="p-3" id="log-stats-content"></div>
                        </div>
                    </div>
                """;
    }

    private String buildSectionSettings() {
        return """
                    <div id="section-settings" class="section">
                        <div class="settings-tabs">
                            <button class="tab-btn active" onclick="showSettingsTab('users')">
                                <i class="fas fa-users me-1"></i>Users
                            </button>
                            <button class="tab-btn" onclick="showSettingsTab('configuration')">
                                <i class="fas fa-sliders-h me-1"></i>Configuration
                            </button>
                            <button class="tab-btn" onclick="showSettingsTab('notifications')">
                                <i class="fas fa-bell me-1"></i>Notifications
                            </button>
                            <button class="tab-btn" onclick="showSettingsTab('general')">
                                <i class="fas fa-wrench me-1"></i>General
                            </button>
                        </div>
                        <div id="settings-pane-users" class="settings-pane active">
                            <div class="table-card">
                                <div class="card-header d-flex justify-content-between align-items-center">
                                    <span><i class="fas fa-users me-2"></i>User Management</span>
                                    <button class="btn btn-sm btn-primary" data-bs-toggle="modal" data-bs-target="#addUserModal">
                                        <i class="fas fa-plus me-1"></i>Add User
                                    </button>
                                </div>
                                <div class="p-3">
                                    <table class="table table-sm table-hover mb-0">
                                        <thead><tr><th>Username</th><th>Roles</th><th>Status</th><th>Last Login</th><th>Created</th><th>Actions</th></tr></thead>
                                        <tbody id="users-tbody"><tr><td colspan="6" class="text-muted">Loading...</td></tr></tbody>
                                    </table>
                                </div>
                            </div>
                        </div>
                        <div id="settings-pane-configuration" class="settings-pane">
                            <div class="table-card mb-3">
                                <div class="card-header"><i class="fas fa-network-wired me-2"></i>Port Mapping</div>
                                <div class="p-3">
                                    <table class="table table-sm mb-0">
                                        <thead><tr><th>Service</th><th>HTTP Port</th><th>gRPC Port</th><th>Has Outbox</th><th>Commands</th></tr></thead>
                                        <tbody id="config-ports-tbody"><tr><td colspan="5" class="text-muted">Loading...</td></tr></tbody>
                                    </table>
                                </div>
                            </div>
                            <div class="table-card">
                                <div class="card-header"><i class="fas fa-code me-2"></i>Environment Variables</div>
                                <div class="p-3">
                                    <div class="accordion" id="env-accordion">
                                        <div class="text-muted">Loading...</div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div id="settings-pane-notifications" class="settings-pane">
                            <div class="table-card">
                                <div class="card-header"><i class="fas fa-bell me-2"></i>Alert Channel Configuration</div>
                                <div class="p-3" id="notification-config-content">
                                    <div class="text-muted">Loading alert config...</div>
                                </div>
                            </div>
                        </div>
                        <div id="settings-pane-general" class="settings-pane">
                            <div class="table-card">
                                <div class="card-header"><i class="fas fa-wrench me-2"></i>General Settings</div>
                                <div class="p-3">
                                    <form onsubmit="updateSettings(event)">
                                        <div class="mb-3">
                                            <label class="form-label">Site Name</label>
                                            <input type="text" class="form-control" id="setting-site-name" value="FractalX Admin">
                                        </div>
                                        <div class="mb-3">
                                            <label class="form-label">Theme</label>
                                            <select class="form-select" id="setting-theme">
                                                <option value="light">Light</option>
                                                <option value="dark">Dark</option>
                                            </select>
                                        </div>
                                        <div class="mb-3">
                                            <label class="form-label">Session Timeout (minutes)</label>
                                            <input type="number" class="form-control" id="setting-session-timeout" value="30" min="5" max="1440">
                                        </div>
                                        <div class="mb-3">
                                            <label class="form-label">Default Alert Email</label>
                                            <input type="email" class="form-control" id="setting-alert-email" placeholder="alerts@example.com">
                                        </div>
                                        <div class="mb-3 form-check">
                                            <input type="checkbox" class="form-check-input" id="setting-maintenance">
                                            <label class="form-check-label">Maintenance Mode</label>
                                        </div>
                                        <button type="submit" class="btn btn-primary">
                                            <i class="fas fa-save me-1"></i>Save Settings
                                        </button>
                                        <span id="settings-save-status" class="ms-2 small text-success" style="display:none">Saved!</span>
                                    </form>
                                </div>
                            </div>
                        </div>
                    </div>
                """;
    }

    // ---- MODALS -------------------------------------------------------------

    private String buildModals() {
        return """
                <div class="modal fade" id="serviceDetailModal" tabindex="-1">
                    <div class="modal-dialog modal-lg">
                        <div class="modal-content">
                            <div class="modal-header">
                                <h5 class="modal-title">
                                    <i class="fas fa-server me-2"></i><span id="svc-detail-title">Service Detail</span>
                                </h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body" id="svc-detail-body">Loading...</div>
                        </div>
                    </div>
                </div>
                <div class="modal fade" id="lifecycleModal" tabindex="-1">
                    <div class="modal-dialog">
                        <div class="modal-content">
                            <div class="modal-header">
                                <h5 class="modal-title">
                                    <i class="fas fa-terminal me-2"></i>Lifecycle &mdash; <span id="lc-service-name"></span>
                                </h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body">
                                <p class="text-muted small mb-3">
                                    Run these commands from your project directory where <code>docker-compose.yml</code> resides.
                                </p>
                                <div id="lifecycle-commands"></div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="modal fade" id="addUserModal" tabindex="-1">
                    <div class="modal-dialog">
                        <div class="modal-content">
                            <div class="modal-header">
                                <h5 class="modal-title"><i class="fas fa-user-plus me-2"></i>Add User</h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body">
                                <div class="mb-3">
                                    <label class="form-label">Username</label>
                                    <input type="text" class="form-control" id="new-username">
                                </div>
                                <div class="mb-3">
                                    <label class="form-label">Password</label>
                                    <input type="password" class="form-control" id="new-password">
                                </div>
                                <div class="mb-3">
                                    <label class="form-label">Role</label>
                                    <select class="form-select" id="new-role">
                                        <option value="ROLE_ADMIN">Admin</option>
                                        <option value="ROLE_OPERATOR">Operator</option>
                                        <option value="ROLE_VIEWER" selected>Viewer</option>
                                    </select>
                                </div>
                                <span id="add-user-error" class="text-danger small"></span>
                            </div>
                            <div class="modal-footer">
                                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                                <button type="button" class="btn btn-primary" onclick="createUser()">Create User</button>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="modal fade" id="changePasswordModal" tabindex="-1">
                    <div class="modal-dialog">
                        <div class="modal-content">
                            <div class="modal-header">
                                <h5 class="modal-title">Change Password &mdash; <span id="cp-username"></span></h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body">
                                <input type="hidden" id="cp-user">
                                <div class="mb-3">
                                    <label class="form-label">New Password</label>
                                    <input type="password" class="form-control" id="cp-new-password">
                                </div>
                                <span id="cp-error" class="text-danger small"></span>
                            </div>
                            <div class="modal-footer">
                                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                                <button type="button" class="btn btn-warning" onclick="submitPasswordChange()">Change Password</button>
                            </div>
                        </div>
                    </div>
                </div>
                """;
    }

    // ---- SCRIPTS: navigation + overview -------------------------------------

    private String buildScriptsNavAndOverview() {
        return """
                let currentSection = 'overview';

                function showSection(name) {
                    document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
                    document.querySelectorAll('.sidebar-nav a').forEach(a => a.classList.remove('active'));
                    document.getElementById('section-' + name).classList.add('active');
                    document.getElementById('nav-' + name).classList.add('active');
                    document.getElementById('page-title').textContent =
                        name.charAt(0).toUpperCase() + name.slice(1);
                    currentSection = name;
                    refreshCurrent();
                }

                function refreshCurrent() {
                    const fn = {
                        overview: loadOverview, services: loadServicesAll,
                        communication: loadCommunicationData, data: loadDataConsistency,
                        observability: loadMetrics, alerts: loadAlerts,
                        traces: loadTraceServices, logs: loadLogServices,
                        settings: loadSettingsSection
                    };
                    if (fn[currentSection]) fn[currentSection]();
                    document.getElementById('last-refresh').textContent = new Date().toLocaleTimeString();
                }

                function loadOverview() {
                    fetch('/api/services/all')
                        .then(r => r.json()).then(services => {
                            let up = 0, down = 0;
                            const tbody = document.getElementById('overview-tbody');
                            tbody.innerHTML = '';
                            services.forEach(s => {
                                const h = s.health || 'UNKNOWN';
                                if (h === 'UP') up++; else down++;
                                tbody.innerHTML += `<tr>
                                    <td><strong>${s.meta.name}</strong></td>
                                    <td><span class="badge ${h==='UP'?'badge-up':h==='DOWN'?'badge-down':'badge-unknown'}">${h}</span></td>
                                    <td>${s.meta.port || '-'}</td>
                                    <td><span class="badge bg-light text-dark">${s.meta.type}</span></td>
                                    <td>
                                        <button class="btn btn-xs btn-outline-primary btn-sm py-0 px-1"
                                            onclick="showServiceDetailModal('${s.meta.name}')">Detail</button>
                                        <button class="btn btn-xs btn-outline-secondary btn-sm py-0 px-1"
                                            onclick="showLifecycleModal('${s.meta.name}')">Commands</button>
                                    </td>
                                </tr>`;
                            });
                            document.getElementById('ov-total').textContent = services.length;
                            document.getElementById('ov-up').textContent = up;
                            document.getElementById('ov-down').textContent = down;
                        }).catch(() => {
                            document.getElementById('overview-tbody').innerHTML =
                                '<tr><td colspan="5" class="text-danger">Failed to load services</td></tr>';
                        });
                    fetch('/api/alerts/active')
                        .then(r => r.json())
                        .then(a => document.getElementById('ov-alerts').textContent = a.length)
                        .catch(() => {});
                }
                """;
    }

    // ---- SCRIPTS: services --------------------------------------------------

    private String buildScriptsServices() {
        return """
                let allServicesData = [];

                function loadServicesAll() {
                    fetch('/api/services/all')
                        .then(r => r.json()).then(services => {
                            allServicesData = services;
                            renderServicesTable(services);
                        }).catch(() => {
                            document.getElementById('services-tbody').innerHTML =
                                '<tr><td colspan="8" class="text-danger">Failed to load</td></tr>';
                        });
                }

                function renderServicesTable(services) {
                    const tbody = document.getElementById('services-tbody');
                    tbody.innerHTML = '';
                    services.forEach(s => {
                        const m = s.meta;
                        const h = s.health || 'UNKNOWN';
                        const dep = m.deployment ? m.deployment.version || '?' : '?';
                        const depStr = (m.dependencies && m.dependencies.length)
                            ? m.dependencies.join(', ') : '<span class="text-muted">none</span>';
                        tbody.innerHTML += `<tr>
                            <td><strong>${m.name}</strong></td>
                            <td><span class="badge bg-light text-dark small">${m.type}</span></td>
                            <td>${m.port || '-'}</td>
                            <td>${m.grpcPort || '-'}</td>
                            <td><span class="badge ${h==='UP'?'badge-up':h==='DOWN'?'badge-down':'badge-unknown'} small">${h}</span></td>
                            <td><small>${depStr}</small></td>
                            <td><small>v${dep}</small></td>
                            <td>
                                <button class="btn btn-xs btn-outline-primary btn-sm py-0 px-1 me-1"
                                    onclick="showServiceDetailModal('${m.name}')">
                                    <i class="fas fa-info-circle"></i>
                                </button>
                                <button class="btn btn-xs btn-outline-secondary btn-sm py-0 px-1"
                                    onclick="showLifecycleModal('${m.name}')">
                                    <i class="fas fa-terminal"></i>
                                </button>
                            </td>
                        </tr>`;
                    });
                }

                function filterServicesTable(q) {
                    const filtered = allServicesData.filter(s =>
                        s.meta.name.toLowerCase().includes(q.toLowerCase()) ||
                        s.meta.type.toLowerCase().includes(q.toLowerCase()));
                    renderServicesTable(filtered);
                }

                function showServiceDetailModal(name) {
                    document.getElementById('svc-detail-title').textContent = name;
                    document.getElementById('svc-detail-body').innerHTML = '<div class="text-muted">Loading...</div>';
                    const modal = new bootstrap.Modal(document.getElementById('serviceDetailModal'));
                    modal.show();
                    fetch('/api/services/' + name + '/detail')
                        .then(r => r.json()).then(d => {
                            const m = d.meta;
                            const h = d.health || {};
                            const dep = d.deployment || {};
                            const stages = dep.stages || [];
                            const stageHtml = stages.map(s =>
                                `<span class="badge bg-success me-1">${s.name} \u2713</span>`).join('');
                            document.getElementById('svc-detail-body').innerHTML = `
                                <div class="row g-3">
                                    <div class="col-md-6">
                                        <h6 class="text-muted mb-1">Service Info</h6>
                                        <table class="table table-sm">
                                            <tr><th>Name</th><td>${m.name}</td></tr>
                                            <tr><th>Type</th><td>${m.type}</td></tr>
                                            <tr><th>HTTP Port</th><td>${m.port}</td></tr>
                                            <tr><th>gRPC Port</th><td>${m.grpcPort || '-'}</td></tr>
                                            <tr><th>Package</th><td><small>${m.packageName || '-'}</small></td></tr>
                                        </table>
                                    </div>
                                    <div class="col-md-6">
                                        <h6 class="text-muted mb-1">Health</h6>
                                        <pre class="bg-light p-2 rounded small" style="max-height:120px;overflow:auto">${JSON.stringify(h, null, 2)}</pre>
                                    </div>
                                </div>
                                <h6 class="text-muted mb-1 mt-2">Deployment Stages</h6>
                                <div>${stageHtml || '<span class="text-muted small">No stage data</span>'}</div>
                            `;
                        }).catch(() => {
                            document.getElementById('svc-detail-body').innerHTML =
                                '<div class="text-danger">Failed to load service detail</div>';
                        });
                }

                function showLifecycleModal(name) {
                    document.getElementById('lc-service-name').textContent = name;
                    document.getElementById('lifecycle-commands').innerHTML = '<div class="text-muted">Loading...</div>';
                    const modal = new bootstrap.Modal(document.getElementById('lifecycleModal'));
                    modal.show();
                    fetch('/api/services/' + name + '/commands')
                        .then(r => r.json()).then(cmds => {
                            let html = '';
                            for (const [action, cmd] of Object.entries(cmds)) {
                                html += `<div class="mb-2">
                                    <small class="text-muted text-uppercase">${action}</small>
                                    <div class="cmd-box" title="Click to copy" onclick="copyText(this)">${cmd}</div>
                                </div>`;
                            }
                            document.getElementById('lifecycle-commands').innerHTML = html;
                        }).catch(() => {
                            document.getElementById('lifecycle-commands').innerHTML =
                                '<div class="text-danger">Failed to load commands</div>';
                        });
                }

                function copyText(el) {
                    navigator.clipboard.writeText(el.textContent.trim()).then(() => {
                        el.style.background = '#166534';
                        setTimeout(() => el.style.background = '', 1000);
                    });
                }
                """;
    }

    // ---- SCRIPTS: communication ---------------------------------------------

    private String buildScriptsCommunication() {
        return """
                function loadCommunicationData() {
                    fetch('/api/communication/topology')
                        .then(r => r.json()).then(data => drawTopologyGrid(data))
                        .catch(() => document.getElementById('topology-grid').innerHTML =
                            '<div class="text-danger p-3">Failed to load topology</div>');

                    fetch('/api/communication/netscope')
                        .then(r => r.json()).then(data => {
                            const tbody = document.getElementById('netscope-tbody');
                            tbody.innerHTML = '';
                            if (!data.length) {
                                tbody.innerHTML = '<tr><td colspan="4" class="text-muted">No NetScope links</td></tr>';
                                return;
                            }
                            data.forEach(svc => {
                                (svc.dependencies || []).forEach(dep => {
                                    tbody.innerHTML += `<tr>
                                        <td>${svc.service}</td><td>${dep.name}</td>
                                        <td><code>${dep.grpcPort}</code></td><td>${dep.protocol}</td>
                                    </tr>`;
                                });
                            });
                        }).catch(() => {});

                    fetch('/api/communication/gateway/health')
                        .then(r => r.json()).then(h => {
                            const status = h.status || 'UNKNOWN';
                            document.getElementById('gateway-info').innerHTML = `
                                <div class="d-flex align-items-center gap-2 mb-2">
                                    <span class="badge ${status==='UP'?'badge-up':'badge-down'} fs-6">${status}</span>
                                    <span class="text-muted small">API Gateway (port 8080)</span>
                                </div>
                                <button class="btn btn-sm btn-outline-primary" onclick="loadGatewayMetrics()">
                                    <i class="fas fa-chart-bar me-1"></i>View Metrics
                                </button>`;
                        }).catch(() => {
                            document.getElementById('gateway-info').innerHTML =
                                '<span class="badge badge-down">DOWN</span> <small class="text-muted">Gateway unavailable</small>';
                        });

                    fetch('/api/communication/discovery/stats')
                        .then(r => r.json()).then(d => {
                            document.getElementById('discovery-info').innerHTML = `
                                <div class="d-flex align-items-center gap-2 mb-2">
                                    <span class="badge ${d.status==='UP'?'badge-up':'badge-down'} fs-6">${d.status}</span>
                                    <span class="text-muted small">fractalx-registry (port 8761)</span>
                                </div>
                                <div class="small">
                                    <strong>${d.count || 0}</strong> services registered
                                    <span class="text-muted ms-2">${d.registryUrl || ''}</span>
                                </div>`;
                        }).catch(() => {
                            document.getElementById('discovery-info').innerHTML =
                                '<span class="badge badge-down">DOWN</span> <small class="text-muted">Registry unavailable</small>';
                        });
                }

                function drawTopologyGrid(data) {
                    const container = document.getElementById('topology-grid');
                    container.innerHTML = '';
                    (data.nodes || []).forEach(node => {
                        const deps = (data.edges || [])
                            .filter(e => e.source === node.id)
                            .map(e => e.target).join(', ');
                        const div = document.createElement('div');
                        div.className = 'topo-node ' + (node.type || '');
                        div.innerHTML = `
                            <div class="fw-semibold">${node.label || node.id}</div>
                            <div class="node-type">${node.type}</div>
                            <div class="node-port">:${node.port}</div>
                            ${deps ? '<div class="mt-1 small text-muted">\u2192 ' + deps + '</div>' : ''}
                        `;
                        container.appendChild(div);
                    });
                }

                function loadGatewayMetrics() {
                    fetch('/api/communication/gateway/metrics')
                        .then(r => r.json()).then(m => alert(JSON.stringify(m, null, 2)))
                        .catch(() => alert('Gateway metrics unavailable'));
                }
                """;
    }

    // ---- SCRIPTS: data consistency ------------------------------------------

    private String buildScriptsData() {
        return """
                function loadDataConsistency() {
                    fetch('/api/data/overview')
                        .then(r => r.json()).then(d => {
                            document.getElementById('data-saga-count').textContent = d.totalSagas || 0;
                            document.getElementById('data-svc-count').textContent = d.totalServices || 0;
                            const orch = d.sagaOrchestrator || {};
                            document.getElementById('data-orch-health').innerHTML =
                                `<span class="badge ${(orch.health||'DOWN')==='UP'?'badge-up':'badge-down'}">${orch.health||'DOWN'}</span>`;
                        }).catch(() => {});

                    fetch('/api/data/sagas')
                        .then(r => r.json()).then(sagas => {
                            const tbody = document.getElementById('sagas-tbody');
                            tbody.innerHTML = '';
                            if (!sagas.length) {
                                tbody.innerHTML = '<tr><td colspan="4" class="text-muted">No saga definitions</td></tr>';
                                return;
                            }
                            sagas.forEach(s => {
                                tbody.innerHTML += `<tr>
                                    <td><code>${s.sagaId}</code></td>
                                    <td>${s.orchestratedBy}</td>
                                    <td>${(s.steps||[]).length} steps</td>
                                    <td>${(s.compensationSteps||[]).length > 0 ?
                                        '<span class="badge bg-success">Yes</span>' :
                                        '<span class="badge bg-secondary">No</span>'}</td>
                                </tr>`;
                            });
                        }).catch(() => {
                            document.getElementById('sagas-tbody').innerHTML =
                                '<tr><td colspan="4" class="text-muted">No sagas configured</td></tr>';
                        });

                    fetch('/api/data/databases')
                        .then(r => r.json()).then(dbs => {
                            const tbody = document.getElementById('databases-tbody');
                            tbody.innerHTML = '';
                            dbs.forEach(db => {
                                const h = db.health || 'UNKNOWN';
                                tbody.innerHTML += `<tr>
                                    <td>${db.service}</td>
                                    <td><small>${db.schemas || '-'}</small></td>
                                    <td><span class="badge ${h==='UP'?'badge-up':h==='DOWN'?'badge-down':'badge-unknown'} small">${h}</span></td>
                                </tr>`;
                            });
                        }).catch(() => {
                            document.getElementById('databases-tbody').innerHTML =
                                '<tr><td colspan="3" class="text-muted">DB health unavailable</td></tr>';
                        });

                    fetch('/api/data/outbox')
                        .then(r => r.json()).then(data => {
                            let html = '<div class="row g-2">';
                            data.forEach(ob => {
                                html += `<div class="col-md-3">
                                    <div class="stat-card p-2 text-center">
                                        <div class="fw-semibold small">${ob.service}</div>
                                        <div class="text-muted small">${ob.metrics ? 'Available' : 'N/A'}</div>
                                    </div>
                                </div>`;
                            });
                            document.getElementById('outbox-info').innerHTML = html + '</div>';
                        }).catch(() => {
                            document.getElementById('outbox-info').innerHTML =
                                '<span class="text-muted">Outbox data unavailable</span>';
                        });
                }

                function loadSagaInstances() {
                    fetch('/api/data/sagas/instances')
                        .then(r => r.json()).then(d => alert(JSON.stringify(d, null, 2)))
                        .catch(() => alert('Saga orchestrator unavailable'));
                }
                """;
    }

    // ---- SCRIPTS: observability + alerts ------------------------------------

    private String buildScriptsObservabilityAndAlerts() {
        return """
                function loadMetrics() {
                    fetch('/api/observability/metrics')
                        .then(r => r.json()).then(data => {
                            const tbody = document.getElementById('metrics-tbody');
                            const cards = document.getElementById('metrics-cards');
                            tbody.innerHTML = '';
                            cards.innerHTML = '';
                            Object.entries(data).forEach(([svc, m]) => {
                                const health  = m.health || 'UNKNOWN';
                                const p99     = m.response_time_p99 != null ? m.response_time_p99 + 'ms' : '-';
                                const errRate = m.error_rate        != null ? m.error_rate + '%'      : '-';
                                const uptime  = m.uptime_percent    != null ? m.uptime_percent + '%'  : '-';
                                tbody.innerHTML += `<tr>
                                    <td>${svc}</td>
                                    <td><span class="badge ${health==='UP'?'badge-up':health==='DOWN'?'badge-down':'badge-unknown'}">${health}</span></td>
                                    <td>${p99}</td><td>${errRate}</td><td>${uptime}</td>
                                </tr>`;
                                cards.innerHTML += `<div class="col-sm-6 col-lg-3">
                                    <div class="stat-card text-center">
                                        <div class="fw-semibold mb-1">${svc}</div>
                                        <span class="badge ${health==='UP'?'badge-up':health==='DOWN'?'badge-down':'badge-unknown'} fs-6">${health}</span>
                                    </div>
                                </div>`;
                            });
                            if (!Object.keys(data).length) {
                                tbody.innerHTML = '<tr><td colspan="5" class="text-muted">No metrics available</td></tr>';
                            }
                        }).catch(() => {
                            document.getElementById('metrics-tbody').innerHTML =
                                '<tr><td colspan="5" class="text-danger">Metrics unavailable</td></tr>';
                        });
                }

                let alertStream = null;

                function loadAlerts() {
                    fetch('/api/alerts/active')
                        .then(r => r.json()).then(alerts => {
                            const tbody = document.getElementById('active-alerts-tbody');
                            tbody.innerHTML = '';
                            document.getElementById('active-alert-count').textContent = alerts.length;
                            updateAlertBadge(alerts.length);
                            if (!alerts.length) {
                                tbody.innerHTML = '<tr><td colspan="5" class="text-muted text-center">No active alerts</td></tr>';
                                return;
                            }
                            alerts.forEach(a => {
                                const sev = a.severity || 'INFO';
                                tbody.innerHTML += `<tr>
                                    <td><small>${a.timestamp || ''}</small></td>
                                    <td>${a.service}</td>
                                    <td><span class="badge ${sev==='CRITICAL'?'bg-danger':sev==='WARNING'?'bg-warning':'bg-info'}">${sev}</span></td>
                                    <td><small>${a.message || ''}</small></td>
                                    <td><button class="btn btn-xs btn-sm btn-outline-success py-0"
                                            onclick="resolveAlert('${a.id}')">Resolve</button></td>
                                </tr>`;
                            });
                        }).catch(() => {});

                    fetch('/api/alerts?size=20')
                        .then(r => r.json()).then(alerts => {
                            const tbody = document.getElementById('alert-history-tbody');
                            tbody.innerHTML = '';
                            if (!alerts.length) {
                                tbody.innerHTML = '<tr><td colspan="5" class="text-muted text-center">No alerts recorded</td></tr>';
                                return;
                            }
                            alerts.forEach(a => {
                                const sev = a.severity || 'INFO';
                                tbody.innerHTML += `<tr>
                                    <td><small>${a.timestamp || ''}</small></td>
                                    <td>${a.service}</td>
                                    <td><span class="badge ${sev==='CRITICAL'?'bg-danger':sev==='WARNING'?'bg-warning':'bg-info'}">${sev}</span></td>
                                    <td><small>${a.message || ''}</small></td>
                                    <td>${a.resolved ?
                                        '<span class="badge bg-success">Resolved</span>' :
                                        '<span class="badge bg-warning">Active</span>'}</td>
                                </tr>`;
                            });
                        }).catch(() => {});

                    fetch('/api/alerts/config')
                        .then(r => r.json()).then(cfg => {
                            const rules = cfg.rules || [];
                            let html = '<div class="mb-2"><strong>Rules:</strong> ' + rules.length + ' configured</div>';
                            html += '<div class="row g-2">';
                            rules.forEach(r => {
                                html += `<div class="col-md-4">
                                    <div class="stat-card p-2">
                                        <div class="fw-semibold small">${r.name}</div>
                                        <div class="text-muted" style="font-size:.78rem">
                                            Condition: ${r.condition} | Severity: ${r.severity}
                                        </div>
                                    </div>
                                </div>`;
                            });
                            html += '</div>';
                            document.getElementById('alert-config-info').innerHTML = html;
                        }).catch(() => {});

                    if (!alertStream) connectAlertStream();
                }

                function resolveAlert(id) {
                    fetch('/api/alerts/' + id + '/resolve', {method:'POST'})
                        .then(() => loadAlerts()).catch(() => {});
                }

                function connectAlertStream() {
                    if (!window.EventSource) return;
                    alertStream = new EventSource('/api/alerts/stream');
                    alertStream.addEventListener('alert', e => {
                        loadAlerts();
                        const a = JSON.parse(e.data);
                        console.log('[ALERT]', a.severity, a.service, a.message);
                    });
                    alertStream.onerror = () => {
                        alertStream.close();
                        alertStream = null;
                        setTimeout(connectAlertStream, 10000);
                    };
                }

                function updateAlertBadge(count) {
                    const badge = document.getElementById('alert-badge');
                    badge.textContent = count;
                    badge.style.display = count > 0 ? '' : 'none';
                }
                """;
    }

    // ---- SCRIPTS: traces + logs + settings + init --------------------------

    private String buildScriptsTracesLogsSettings() {
        return buildScriptsTracesLogs2()
            + buildScriptsSettings2()
            + buildScriptsInit2();
    }

    private String buildScriptsTracesLogs2() {
        return """
                function loadTraceServices() {
                    fetch('/api/services/all')
                        .then(r => r.json()).then(services => {
                            const sel = document.getElementById('trace-service-select');
                            services.forEach(s => {
                                const o = document.createElement('option');
                                o.value = o.textContent = s.meta.name;
                                sel.appendChild(o);
                            });
                        }).catch(() => {});
                }

                function searchTraces() {
                    const cid    = document.getElementById('trace-correlation-id').value.trim();
                    const svc    = document.getElementById('trace-service-select').value;
                    const params = new URLSearchParams();
                    if (cid) params.set('correlationId', cid);
                    if (svc) params.set('service', svc);
                    params.set('limit', '20');
                    fetch('/api/traces?' + params)
                        .then(r => r.json()).then(data => {
                            const tbody = document.getElementById('traces-tbody');
                            tbody.innerHTML = '';
                            const traces = data.data || data || [];
                            if (!traces.length) {
                                tbody.innerHTML = '<tr><td colspan="5" class="text-muted text-center">No traces found</td></tr>';
                                return;
                            }
                            traces.forEach(t => {
                                const traceId = t.traceID || t.traceId || '';
                                const svcName = t.processes ? Object.values(t.processes)[0]?.serviceName || '-' : '-';
                                const dur     = t.duration ? (t.duration / 1000).toFixed(2) + 'ms' : '-';
                                const spans   = t.spans ? t.spans.length : '-';
                                tbody.innerHTML += `<tr>
                                    <td><code style="font-size:.75rem">${traceId.substring(0,16)}...</code></td>
                                    <td>${svcName}</td><td>${dur}</td><td>${spans}</td>
                                    <td><a href="http://localhost:16686/trace/${traceId}" target="_blank"
                                           class="btn btn-xs btn-sm btn-outline-info py-0">
                                        <i class="fas fa-external-link-alt"></i></a></td>
                                </tr>`;
                            });
                        }).catch(() => {
                            document.getElementById('traces-tbody').innerHTML =
                                '<tr><td colspan="5" class="text-warning">Jaeger unavailable or no traces found</td></tr>';
                        });
                }

                let currentLogPage = 0;

                function loadLogServices() {
                    fetch('/api/logs/services')
                        .then(r => r.json()).then(services => {
                            const sel = document.getElementById('log-service-select');
                            sel.innerHTML = '<option value="">-- All Services --</option>';
                            services.forEach(s => {
                                const o = document.createElement('option');
                                o.value = o.textContent = s;
                                sel.appendChild(o);
                            });
                        }).catch(() => {});
                }

                function searchLogs(page) {
                    currentLogPage = page;
                    const cid   = document.getElementById('log-correlation-id').value.trim();
                    const svc   = document.getElementById('log-service-select').value;
                    const level = document.getElementById('log-level-select').value;
                    const params = new URLSearchParams({page, size: 50});
                    if (cid)   params.set('correlationId', cid);
                    if (svc)   params.set('service', svc);
                    if (level) params.set('level', level);
                    fetch('/api/logs?' + params)
                        .then(r => r.json()).then(logs => {
                            const tbody = document.getElementById('logs-tbody');
                            tbody.innerHTML = '';
                            if (!logs.length) {
                                tbody.innerHTML = '<tr><td colspan="5" class="text-muted text-center">No logs found</td></tr>';
                                renderLogPagination(page, 0);
                                return;
                            }
                            logs.forEach(l => {
                                const lvl = l.level || 'INFO';
                                tbody.innerHTML += `<tr>
                                    <td><small class="text-muted">${l.receivedAt || ''}</small></td>
                                    <td><small>${l.service || ''}</small></td>
                                    <td><span class="badge ${lvl==='ERROR'?'bg-danger':lvl==='WARN'?'bg-warning':'bg-secondary'} small">${lvl}</span></td>
                                    <td><code style="font-size:.72rem">${(l.correlationId||'').substring(0,12)}</code></td>
                                    <td><small>${l.message || ''}</small></td>
                                </tr>`;
                            });
                            renderLogPagination(page, logs.length);
                        }).catch(() => {
                            document.getElementById('logs-tbody').innerHTML =
                                '<tr><td colspan="5" class="text-danger">Log service unavailable</td></tr>';
                        });
                }

                function renderLogPagination(page, count) {
                    const div = document.getElementById('log-pagination');
                    div.innerHTML = '';
                    if (page > 0) {
                        const btn = document.createElement('button');
                        btn.className = 'btn btn-sm btn-outline-secondary';
                        btn.textContent = '\u2190 Previous';
                        btn.onclick = () => searchLogs(page - 1);
                        div.appendChild(btn);
                    }
                    if (count >= 50) {
                        const btn = document.createElement('button');
                        btn.className = 'btn btn-sm btn-outline-primary';
                        btn.textContent = 'Next \u2192';
                        btn.onclick = () => searchLogs(page + 1);
                        div.appendChild(btn);
                    }
                }

                function loadLogStats() {
                    fetch('/api/logs/stats')
                        .then(r => r.json()).then(stats => {
                            const card    = document.getElementById('log-stats-card');
                            const content = document.getElementById('log-stats-content');
                            card.style.display = '';
                            let html = '<div class="row g-2">';
                            Object.entries(stats).forEach(([svc, s]) => {
                                html += `<div class="col-md-3"><div class="stat-card p-2">
                                    <div class="fw-semibold small">${svc}</div>
                                    <div class="small">Total: ${s.total || 0} | Errors: <span class="text-danger">${s.errors || 0}</span></div>
                                </div></div>`;
                            });
                            content.innerHTML = html + '</div>';
                        }).catch(() => {});
                }
                """;
    }

    private String buildScriptsSettings2() {
        return """
                function loadSettingsSection() {
                    loadUsers(); loadConfig(); loadNotificationConfig(); loadGeneralSettings(); loadCurrentUser();
                }

                function showSettingsTab(tab) {
                    document.querySelectorAll('.settings-pane').forEach(p => p.classList.remove('active'));
                    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
                    document.getElementById('settings-pane-' + tab).classList.add('active');
                    event.target.classList.add('active');
                }

                function loadUsers() {
                    fetch('/api/users')
                        .then(r => r.json()).then(users => {
                            const tbody = document.getElementById('users-tbody');
                            tbody.innerHTML = '';
                            users.forEach(u => {
                                tbody.innerHTML += `<tr>
                                    <td><strong>${u.username}</strong></td>
                                    <td><small>${(u.roles || []).join(', ')}</small></td>
                                    <td>${u.active ? '<span class="badge bg-success">Active</span>' : '<span class="badge bg-secondary">Inactive</span>'}</td>
                                    <td><small>${u.lastLoginAt || 'Never'}</small></td>
                                    <td><small>${u.createdAt || ''}</small></td>
                                    <td>
                                        <button class="btn btn-xs btn-sm btn-outline-warning py-0 me-1"
                                            onclick="openChangePassword('${u.username}')"><i class="fas fa-key"></i></button>
                                        <button class="btn btn-xs btn-sm btn-outline-danger py-0"
                                            onclick="deleteUser('${u.username}')"><i class="fas fa-trash"></i></button>
                                    </td>
                                </tr>`;
                            });
                        }).catch(() => {
                            document.getElementById('users-tbody').innerHTML =
                                '<tr><td colspan="6" class="text-danger">Failed to load users</td></tr>';
                        });
                }

                function createUser() {
                    const username = document.getElementById('new-username').value.trim();
                    const password = document.getElementById('new-password').value;
                    const role     = document.getElementById('new-role').value;
                    const errEl    = document.getElementById('add-user-error');
                    errEl.textContent = '';
                    if (!username || !password) { errEl.textContent = 'Username and password required'; return; }
                    fetch('/api/users', {
                        method: 'POST', headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify({username, password, roles: [role]})
                    }).then(r => r.json()).then(res => {
                        if (res.error) { errEl.textContent = res.error; return; }
                        bootstrap.Modal.getInstance(document.getElementById('addUserModal')).hide();
                        loadUsers();
                    }).catch(() => errEl.textContent = 'Request failed');
                }

                function deleteUser(username) {
                    if (!confirm('Delete user ' + username + '?')) return;
                    fetch('/api/users/' + username, {method:'DELETE'}).then(() => loadUsers()).catch(() => {});
                }

                function openChangePassword(username) {
                    document.getElementById('cp-username').textContent = username;
                    document.getElementById('cp-user').value = username;
                    document.getElementById('cp-new-password').value = '';
                    document.getElementById('cp-error').textContent = '';
                    new bootstrap.Modal(document.getElementById('changePasswordModal')).show();
                }

                function submitPasswordChange() {
                    const username    = document.getElementById('cp-user').value;
                    const newPassword = document.getElementById('cp-new-password').value;
                    if (!newPassword) { document.getElementById('cp-error').textContent = 'Password required'; return; }
                    fetch('/api/users/' + username + '/password', {
                        method: 'PUT', headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify({newPassword})
                    }).then(r => {
                        if (r.ok) bootstrap.Modal.getInstance(document.getElementById('changePasswordModal')).hide();
                        else document.getElementById('cp-error').textContent = 'Failed to change password';
                    }).catch(() => document.getElementById('cp-error').textContent = 'Request failed');
                }

                function loadConfig() {
                    fetch('/api/config/ports')
                        .then(r => r.json()).then(ports => {
                            const tbody = document.getElementById('config-ports-tbody');
                            tbody.innerHTML = '';
                            ports.forEach(p => {
                                tbody.innerHTML += `<tr>
                                    <td><strong>${p.name}</strong></td>
                                    <td><code>${p.httpPort}</code></td>
                                    <td><code>${p.grpcPort || '-'}</code></td>
                                    <td>${p.hasOutbox ? '<i class="fas fa-check text-success"></i>' : '<span class="text-muted">-</span>'}</td>
                                    <td><button class="btn btn-xs btn-sm btn-outline-secondary py-0"
                                            onclick="showLifecycleModal('${p.name}')">
                                        <i class="fas fa-terminal me-1"></i>Commands</button></td>
                                </tr>`;
                            });
                        }).catch(() => {});

                    fetch('/api/config/environment')
                        .then(r => r.json()).then(env => {
                            const acc = document.getElementById('env-accordion');
                            acc.innerHTML = '';
                            Object.entries(env).forEach(([svc, vars], idx) => {
                                const rows = Object.entries(vars || {}).map(([k,v]) =>
                                    `<tr><td class="text-muted small">${k}</td><td><code class="small">${v}</code></td></tr>`
                                ).join('');
                                acc.innerHTML += `<div class="accordion-item">
                                    <h2 class="accordion-header">
                                        <button class="accordion-button collapsed py-2 small" type="button"
                                            data-bs-toggle="collapse" data-bs-target="#env-${idx}">${svc}</button>
                                    </h2>
                                    <div id="env-${idx}" class="accordion-collapse collapse">
                                        <div class="accordion-body p-2">
                                            <table class="table table-sm mb-0">${rows}</table>
                                        </div>
                                    </div>
                                </div>`;
                            });
                        }).catch(() => {});
                }

                function loadNotificationConfig() {
                    fetch('/api/alerts/config')
                        .then(r => r.json()).then(cfg => {
                            const channels = cfg.channels || {};
                            let html = '<div class="row g-2">';
                            Object.entries(channels).forEach(([name, ch]) => {
                                const enabled = ch.enabled || false;
                                html += `<div class="col-md-3"><div class="stat-card p-2">
                                    <div class="fw-semibold small text-capitalize">${name}</div>
                                    <span class="badge ${enabled?'bg-success':'bg-secondary'}">${enabled?'Enabled':'Disabled'}</span>
                                </div></div>`;
                            });
                            html += '</div><p class="mt-2 small text-muted">Edit <code>alerting.yml</code> to enable channels.</p>';
                            document.getElementById('notification-config-content').innerHTML = html;
                        }).catch(() => {
                            document.getElementById('notification-config-content').innerHTML =
                                '<span class="text-muted">Alert config unavailable</span>';
                        });
                }

                function loadGeneralSettings() {
                    fetch('/api/settings')
                        .then(r => r.json()).then(s => {
                            document.getElementById('setting-site-name').value       = s.siteName || '';
                            document.getElementById('setting-theme').value           = s.theme || 'light';
                            document.getElementById('setting-session-timeout').value = s.sessionTimeoutMin || 30;
                            document.getElementById('setting-alert-email').value     = s.defaultAlertEmail || '';
                            document.getElementById('setting-maintenance').checked   = s.maintenanceMode || false;
                        }).catch(() => {});
                }

                function updateSettings(e) {
                    e.preventDefault();
                    const settings = {
                        siteName:          document.getElementById('setting-site-name').value,
                        theme:             document.getElementById('setting-theme').value,
                        sessionTimeoutMin: parseInt(document.getElementById('setting-session-timeout').value),
                        defaultAlertEmail: document.getElementById('setting-alert-email').value,
                        maintenanceMode:   document.getElementById('setting-maintenance').checked
                    };
                    fetch('/api/settings', {
                        method: 'PUT', headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify(settings)
                    }).then(() => {
                        const status = document.getElementById('settings-save-status');
                        status.style.display = '';
                        setTimeout(() => status.style.display = 'none', 2000);
                    }).catch(() => {});
                }

                function loadCurrentUser() {
                    fetch('/api/auth/profile')
                        .then(r => r.json())
                        .then(p => { if (p.username) document.getElementById('current-user').textContent = p.username; })
                        .catch(() => {});
                }
                """;
    }

    private String buildScriptsInit2() {
        return """
                document.addEventListener('DOMContentLoaded', () => {
                    loadOverview();
                    connectAlertStream();
                    setInterval(() => updateAlertBadge(
                        parseInt(document.getElementById('active-alert-count').textContent) || 0), 30000);
                });
                """;
    }
}
