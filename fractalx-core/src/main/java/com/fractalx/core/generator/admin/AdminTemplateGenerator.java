package com.fractalx.core.generator.admin;

import com.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Generates Thymeleaf HTML templates (login + dashboard) for the admin service. */
class AdminTemplateGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminTemplateGenerator.class);

    /** @deprecated Use {@link #generate(Path, List)} instead. */
    void generate(Path templatesPath) throws IOException {
        generate(templatesPath, List.of());
    }

    void generate(Path templatesPath, List<FractalModule> modules) throws IOException {
        generateLoginTemplate(templatesPath);
        generateDashboardTemplate(templatesPath, modules);
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

    private void generateDashboardTemplate(Path templatesPath,
                                            List<FractalModule> modules) throws IOException {
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
                        .sidebar .nav-link:hover, .sidebar .nav-link.active { color:#fff; background:rgba(255,255,255,.1); border-radius:.25rem; }
                        main { padding-top:56px; }
                        .section { display:none; }
                        .section.active { display:block; }
                        .badge-alert { position:relative; top:-6px; right:-2px; font-size:.65em; }
                        .severity-CRITICAL { color:#dc3545; font-weight:600; }
                        .severity-WARNING  { color:#fd7e14; font-weight:600; }
                        .severity-INFO     { color:#0dcaf0; }
                        .metric-card { border-left:4px solid; }
                        .metric-card.up   { border-color:#198754; }
                        .metric-card.down { border-color:#dc3545; }
                        #alertBell .badge { font-size:.6em; }
                    </style>
                </head>
                <body>
                    <nav class="navbar navbar-dark fixed-top bg-dark flex-md-nowrap p-0 shadow">
                        <a class="navbar-brand col-md-3 col-lg-2 me-0 px-3" href="#">
                            <i class="fas fa-cube me-2"></i>FractalX Admin
                        </a>
                        <div class="navbar-nav ms-auto me-3">
                            <span id="alertBell" class="nav-link text-white position-relative" style="cursor:pointer"
                                  onclick="showSection('alerts')">
                                <i class="fas fa-bell"></i>
                                <span id="alertCount" class="badge bg-danger badge-alert d-none">0</span>
                            </span>
                            <form th:action="@{/logout}" method="post" class="d-inline">
                                <button type="submit" class="btn btn-link nav-link px-3 text-white">
                                    <i class="fas fa-sign-out-alt me-2"></i>Logout
                                </button>
                            </form>
                        </div>
                    </nav>
                    <div class="container-fluid">
                        <div class="row">
                            <nav id="sidebarMenu" class="col-md-3 col-lg-2 d-md-block sidebar collapse">
                                <div class="sidebar-sticky pt-3">
                                    <ul class="nav flex-column">
                                        <li class="nav-item">
                                            <a class="nav-link active" href="#" onclick="showSection('dashboard')">
                                                <i class="fas fa-home me-2"></i>Dashboard
                                            </a>
                                        </li>
                                        <li class="nav-item">
                                            <a class="nav-link" href="#" onclick="showSection('services')">
                                                <i class="fas fa-server me-2"></i>Services
                                            </a>
                                        </li>
                                        <li class="nav-item">
                                            <a class="nav-link" href="#" onclick="showSection('observability')">
                                                <i class="fas fa-chart-line me-2"></i>Observability
                                            </a>
                                        </li>
                                        <li class="nav-item">
                                            <a class="nav-link" href="#" onclick="showSection('alerts')">
                                                <i class="fas fa-bell me-2"></i>Alerts
                                                <span id="alertBadge" class="badge bg-danger ms-1 d-none">0</span>
                                            </a>
                                        </li>
                                        <li class="nav-item">
                                            <a class="nav-link" href="#" onclick="showSection('traces')">
                                                <i class="fas fa-project-diagram me-2"></i>Traces
                                            </a>
                                        </li>
                                        <li class="nav-item">
                                            <a class="nav-link" href="#" onclick="showSection('logs')">
                                                <i class="fas fa-file-alt me-2"></i>Logs
                                            </a>
                                        </li>
                                    </ul>
                                </div>
                            </nav>
                            <main class="col-md-9 ms-sm-auto col-lg-10 px-md-4">

                                <!-- ============ DASHBOARD SECTION ============ -->
                                <div id="section-dashboard" class="section active">
                                    <div class="d-flex justify-content-between align-items-center pt-3 pb-2 mb-3 border-bottom">
                                        <h1 class="h2">Dashboard</h1>
                                        <button class="btn btn-sm btn-outline-secondary" onclick="refreshAll()">
                                            <i class="fas fa-sync-alt me-1"></i>Refresh
                                        </button>
                                    </div>
                                    <div class="row mb-4">
                                        <div class="col-md-3 mb-3">
                                            <div class="card border-primary">
                                                <div class="card-body">
                                                    <h6 class="text-muted">Total Services</h6>
                                                    <h2 th:text="${totalServices}">0</h2>
                                                </div>
                                            </div>
                                        </div>
                                        <div class="col-md-3 mb-3">
                                            <div class="card border-success">
                                                <div class="card-body">
                                                    <h6 class="text-muted">Running</h6>
                                                    <h2 class="text-success" th:text="${runningServices}">0</h2>
                                                </div>
                                            </div>
                                        </div>
                                        <div class="col-md-3 mb-3">
                                            <div class="card border-danger">
                                                <div class="card-body">
                                                    <h6 class="text-muted">Stopped</h6>
                                                    <h2 class="text-danger" th:text="${totalServices - runningServices}">0</h2>
                                                </div>
                                            </div>
                                        </div>
                                        <div class="col-md-3 mb-3">
                                            <div class="card border-warning">
                                                <div class="card-body">
                                                    <h6 class="text-muted">Active Alerts</h6>
                                                    <h2 class="text-warning" id="dashAlertCount">—</h2>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="card">
                                        <div class="card-header"><h5>Services Status</h5></div>
                                        <div class="card-body">
                                            <table class="table table-hover">
                                                <thead><tr><th>Service</th><th>URL</th><th>Status</th></tr></thead>
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
                                </div>

                                <!-- ============ SERVICES SECTION ============ -->
                                <div id="section-services" class="section">
                                    <div class="pt-3 pb-2 mb-3 border-bottom">
                                        <h1 class="h2">Services</h1>
                                    </div>
                                    <div id="servicesContent">Loading...</div>
                                </div>

                                <!-- ============ OBSERVABILITY SECTION ============ -->
                                <div id="section-observability" class="section">
                                    <div class="d-flex justify-content-between align-items-center pt-3 pb-2 mb-3 border-bottom">
                                        <h1 class="h2">Observability</h1>
                                        <button class="btn btn-sm btn-outline-secondary" onclick="loadMetrics()">
                                            <i class="fas fa-sync-alt me-1"></i>Refresh
                                        </button>
                                    </div>
                                    <div id="metricsContent">
                                        <div class="row" id="metricsCards"></div>
                                    </div>
                                </div>

                                <!-- ============ ALERTS SECTION ============ -->
                                <div id="section-alerts" class="section">
                                    <div class="d-flex justify-content-between align-items-center pt-3 pb-2 mb-3 border-bottom">
                                        <h1 class="h2">Alerts</h1>
                                        <div>
                                            <button class="btn btn-sm btn-outline-danger me-2" onclick="loadAlerts()">
                                                <i class="fas fa-sync-alt me-1"></i>Refresh
                                            </button>
                                        </div>
                                    </div>
                                    <div class="row mb-3">
                                        <div class="col-md-6">
                                            <div class="card border-danger">
                                                <div class="card-body text-center">
                                                    <h6 class="text-muted">Active Alerts</h6>
                                                    <h2 class="text-danger" id="activeAlertCount">—</h2>
                                                </div>
                                            </div>
                                        </div>
                                        <div class="col-md-6">
                                            <div class="card border-secondary">
                                                <div class="card-body text-center">
                                                    <h6 class="text-muted">Total (History)</h6>
                                                    <h2 id="totalAlertCount">—</h2>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="card">
                                        <div class="card-header d-flex justify-content-between">
                                            <h5>Active Alerts</h5>
                                        </div>
                                        <div class="card-body">
                                            <table class="table table-hover" id="alertsTable">
                                                <thead><tr><th>Time</th><th>Service</th><th>Severity</th><th>Message</th><th>Action</th></tr></thead>
                                                <tbody id="alertsBody"></tbody>
                                            </table>
                                        </div>
                                    </div>
                                    <div class="card mt-3">
                                        <div class="card-header"><h5>Alert History</h5></div>
                                        <div class="card-body">
                                            <table class="table table-sm" id="alertHistoryTable">
                                                <thead><tr><th>Time</th><th>Service</th><th>Severity</th><th>Message</th><th>Resolved</th></tr></thead>
                                                <tbody id="alertHistoryBody"></tbody>
                                            </table>
                                        </div>
                                    </div>
                                </div>

                                <!-- ============ TRACES SECTION ============ -->
                                <div id="section-traces" class="section">
                                    <div class="pt-3 pb-2 mb-3 border-bottom">
                                        <h1 class="h2">Distributed Traces</h1>
                                    </div>
                                    <div class="card mb-3">
                                        <div class="card-body">
                                            <div class="input-group">
                                                <input type="text" id="correlationIdInput" class="form-control"
                                                       placeholder="Search by Correlation ID...">
                                                <select id="traceServiceFilter" class="form-select" style="max-width:200px">
                                                    <option value="">All services</option>
                                                </select>
                                                <button class="btn btn-primary" onclick="searchTraces()">
                                                    <i class="fas fa-search me-1"></i>Search
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="card mb-3">
                                        <div class="card-header d-flex justify-content-between align-items-center">
                                            <h5 class="mb-0">Results</h5>
                                            <a href="http://localhost:16686" target="_blank" class="btn btn-sm btn-outline-primary">
                                                <i class="fas fa-external-link-alt me-1"></i>Open Jaeger UI
                                            </a>
                                        </div>
                                        <div class="card-body">
                                            <table class="table table-sm" id="tracesTable">
                                                <thead><tr><th>Correlation ID</th><th>Service</th><th>Duration</th><th>Spans</th><th>Timestamp</th></tr></thead>
                                                <tbody id="tracesBody"></tbody>
                                            </table>
                                        </div>
                                    </div>
                                </div>

                                <!-- ============ LOGS SECTION ============ -->
                                <div id="section-logs" class="section">
                                    <div class="pt-3 pb-2 mb-3 border-bottom">
                                        <h1 class="h2">Logs</h1>
                                    </div>
                                    <div class="card mb-3">
                                        <div class="card-body">
                                            <div class="row g-2">
                                                <div class="col-md-4">
                                                    <input type="text" id="logCorrelationId" class="form-control"
                                                           placeholder="Correlation ID">
                                                </div>
                                                <div class="col-md-3">
                                                    <select id="logServiceFilter" class="form-select">
                                                        <option value="">All services</option>
                                                    </select>
                                                </div>
                                                <div class="col-md-2">
                                                    <select id="logLevelFilter" class="form-select">
                                                        <option value="">All levels</option>
                                                        <option value="ERROR">ERROR</option>
                                                        <option value="WARN">WARN</option>
                                                        <option value="INFO">INFO</option>
                                                        <option value="DEBUG">DEBUG</option>
                                                    </select>
                                                </div>
                                                <div class="col-md-1">
                                                    <button class="btn btn-primary w-100" onclick="searchLogs()">
                                                        <i class="fas fa-search"></i>
                                                    </button>
                                                </div>
                                                <div class="col-md-2">
                                                    <button class="btn btn-outline-secondary w-100" onclick="clearLogFilters()">
                                                        Clear
                                                    </button>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="card">
                                        <div class="card-header d-flex justify-content-between align-items-center">
                                            <h5 class="mb-0">Log Entries</h5>
                                            <small id="logStats" class="text-muted"></small>
                                        </div>
                                        <div class="card-body p-0">
                                            <div class="table-responsive" style="max-height:500px; overflow-y:auto">
                                                <table class="table table-sm table-striped mb-0 font-monospace" style="font-size:.8em">
                                                    <thead class="table-dark sticky-top">
                                                        <tr><th>Time</th><th>Service</th><th>Level</th><th>Correlation ID</th><th>Message</th></tr>
                                                    </thead>
                                                    <tbody id="logsBody"></tbody>
                                                </table>
                                            </div>
                                        </div>
                                        <div class="card-footer">
                                            <nav><ul class="pagination pagination-sm mb-0 justify-content-end" id="logsPagination"></ul></nav>
                                        </div>
                                    </div>
                                </div>

                            </main>
                        </div>
                    </div>

                    <script th:src="@{/webjars/jquery/3.7.0/jquery.min.js}"></script>
                    <script th:src="@{/webjars/bootstrap/5.3.0/js/bootstrap.bundle.min.js}"></script>
                    <script>
                    // ──────────────────────────────────────────────────
                    // Section navigation
                    // ──────────────────────────────────────────────────
                    function showSection(name) {
                        document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
                        document.querySelectorAll('.sidebar .nav-link').forEach(l => l.classList.remove('active'));
                        document.getElementById('section-' + name).classList.add('active');
                        // load data lazily on first visit
                        if (name === 'observability') loadMetrics();
                        if (name === 'alerts')        loadAlerts();
                        if (name === 'traces')        loadTraceServices();
                        if (name === 'logs')          { loadLogServices(); searchLogs(); }
                    }

                    function refreshAll() {
                        fetch('/api/alerts/active').then(r => r.json()).then(data => {
                            const n = Array.isArray(data) ? data.length : 0;
                            updateAlertBadge(n);
                            document.getElementById('dashAlertCount').textContent = n;
                        }).catch(() => {});
                    }

                    function updateAlertBadge(n) {
                        const badge  = document.getElementById('alertBadge');
                        const nBadge = document.getElementById('alertCount');
                        if (n > 0) {
                            badge.textContent  = n; badge.classList.remove('d-none');
                            nBadge.textContent = n; nBadge.classList.remove('d-none');
                        } else {
                            badge.classList.add('d-none');
                            nBadge.classList.add('d-none');
                        }
                    }

                    // ──────────────────────────────────────────────────
                    // Observability metrics
                    // ──────────────────────────────────────────────────
                    function loadMetrics() {
                        fetch('/api/observability/metrics')
                            .then(r => r.json())
                            .then(data => {
                                const container = document.getElementById('metricsCards');
                                container.innerHTML = '';
                                (Array.isArray(data) ? data : []).forEach(m => {
                                    const status = m.health === 'UP' ? 'up' : 'down';
                                    container.innerHTML += `
                                        <div class="col-md-4 mb-3">
                                            <div class="card metric-card ${status}">
                                                <div class="card-body">
                                                    <div class="d-flex justify-content-between align-items-center mb-2">
                                                        <h6 class="mb-0">${m.service}</h6>
                                                        <span class="badge ${m.health === 'UP' ? 'bg-success' : 'bg-danger'}">${m.health}</span>
                                                    </div>
                                                    <small class="text-muted">Response p99</small>
                                                    <div class="fw-bold">${m.responseTimeP99 != null ? m.responseTimeP99 + ' ms' : '—'}</div>
                                                    <small class="text-muted">Error rate</small>
                                                    <div class="fw-bold">${m.errorRate != null ? m.errorRate.toFixed(1) + ' %' : '—'}</div>
                                                    <small class="text-muted">Uptime</small>
                                                    <div class="fw-bold">${m.uptimePercent != null ? m.uptimePercent.toFixed(1) + ' %' : '—'}</div>
                                                </div>
                                            </div>
                                        </div>`;
                                });
                                if (!container.innerHTML) container.innerHTML = '<p class="text-muted">No metrics available.</p>';
                            })
                            .catch(() => { document.getElementById('metricsCards').innerHTML = '<p class="text-danger">Failed to load metrics.</p>'; });
                    }

                    // ──────────────────────────────────────────────────
                    // Alerts
                    // ──────────────────────────────────────────────────
                    function loadAlerts() {
                        // Active alerts
                        fetch('/api/alerts/active').then(r => r.json()).then(data => {
                            const n = Array.isArray(data) ? data.length : 0;
                            document.getElementById('activeAlertCount').textContent = n;
                            updateAlertBadge(n);
                            const tbody = document.getElementById('alertsBody');
                            tbody.innerHTML = '';
                            data.forEach(a => {
                                tbody.innerHTML += `<tr>
                                    <td>${new Date(a.timestamp).toLocaleTimeString()}</td>
                                    <td>${a.service}</td>
                                    <td class="severity-${a.severity}">${a.severity}</td>
                                    <td>${a.message}</td>
                                    <td><button class="btn btn-xs btn-outline-success btn-sm"
                                            onclick="resolveAlert('${a.id}')">Resolve</button></td>
                                </tr>`;
                            });
                            if (!data.length) tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No active alerts</td></tr>';
                        }).catch(() => {});

                        // Alert history
                        fetch('/api/alerts?size=50').then(r => r.json()).then(data => {
                            const list  = Array.isArray(data) ? data : (data.content || []);
                            document.getElementById('totalAlertCount').textContent = list.length;
                            const tbody = document.getElementById('alertHistoryBody');
                            tbody.innerHTML = '';
                            list.forEach(a => {
                                tbody.innerHTML += `<tr class="${a.resolved ? '' : 'table-warning'}">
                                    <td>${new Date(a.timestamp).toLocaleString()}</td>
                                    <td>${a.service}</td>
                                    <td class="severity-${a.severity}">${a.severity}</td>
                                    <td>${a.message}</td>
                                    <td>${a.resolved ? '<span class="badge bg-success">Resolved</span>' : '<span class="badge bg-danger">Open</span>'}</td>
                                </tr>`;
                            });
                        }).catch(() => {});
                    }

                    function resolveAlert(id) {
                        fetch('/api/alerts/' + id + '/resolve', { method: 'POST' })
                            .then(() => loadAlerts())
                            .catch(() => alert('Failed to resolve alert'));
                    }

                    // ──────────────────────────────────────────────────
                    // Real-time SSE alert feed
                    // ──────────────────────────────────────────────────
                    (function connectAlertStream() {
                        if (typeof EventSource === 'undefined') return;
                        const es = new EventSource('/api/alerts/stream');
                        es.onmessage = function(e) {
                            try {
                                const alert = JSON.parse(e.data);
                                // Re-count active alerts
                                loadAlerts();
                                // Flash badge
                                updateAlertBadge(parseInt(document.getElementById('activeAlertCount').textContent || '0') + 1);
                            } catch (_) {}
                        };
                        es.onerror = function() { setTimeout(connectAlertStream, 5000); };
                    })();

                    // ──────────────────────────────────────────────────
                    // Traces
                    // ──────────────────────────────────────────────────
                    function loadTraceServices() {
                        fetch('/api/logs/services').then(r => r.json()).then(svcs => {
                            const sel = document.getElementById('traceServiceFilter');
                            sel.innerHTML = '<option value="">All services</option>';
                            svcs.forEach(s => sel.innerHTML += `<option value="${s}">${s}</option>`);
                        }).catch(() => {});
                    }

                    function searchTraces() {
                        const correlationId = document.getElementById('correlationIdInput').value.trim();
                        const service       = document.getElementById('traceServiceFilter').value;
                        let url = '/api/traces?limit=20';
                        if (correlationId) url += '&correlationId=' + encodeURIComponent(correlationId);
                        if (service)       url += '&service=' + encodeURIComponent(service);

                        fetch(url).then(r => r.json()).then(data => {
                            const tbody = document.getElementById('tracesBody');
                            tbody.innerHTML = '';
                            const list = Array.isArray(data) ? data : (data.data || []);
                            list.forEach(t => {
                                const spans = t.spans ? t.spans.length : '—';
                                const dur   = t.spans && t.spans.length ?
                                    ((t.spans[0].duration || 0) / 1000).toFixed(2) + ' ms' : '—';
                                tbody.innerHTML += `<tr>
                                    <td><code>${t.traceID || correlationId}</code></td>
                                    <td>${t.spans && t.spans[0] ? t.spans[0].process.serviceName : service}</td>
                                    <td>${dur}</td>
                                    <td>${spans}</td>
                                    <td>${t.spans && t.spans[0] ? new Date(t.spans[0].startTime / 1000).toLocaleString() : ''}</td>
                                </tr>`;
                            });
                            if (!tbody.innerHTML) tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No traces found</td></tr>';
                        }).catch(() => {
                            document.getElementById('tracesBody').innerHTML = '<tr><td colspan="5" class="text-danger">Failed to load traces</td></tr>';
                        });
                    }

                    // ──────────────────────────────────────────────────
                    // Logs
                    // ──────────────────────────────────────────────────
                    let currentLogPage = 0;

                    function loadLogServices() {
                        fetch('/api/logs/services').then(r => r.json()).then(svcs => {
                            const sel = document.getElementById('logServiceFilter');
                            sel.innerHTML = '<option value="">All services</option>';
                            svcs.forEach(s => sel.innerHTML += `<option value="${s}">${s}</option>`);
                        }).catch(() => {});
                    }

                    function searchLogs(page) {
                        currentLogPage = page || 0;
                        const correlationId = document.getElementById('logCorrelationId').value.trim();
                        const service       = document.getElementById('logServiceFilter').value;
                        const level         = document.getElementById('logLevelFilter').value;
                        let url = `/api/logs?page=${currentLogPage}&size=50`;
                        if (correlationId) url += '&correlationId=' + encodeURIComponent(correlationId);
                        if (service)       url += '&service=' + encodeURIComponent(service);
                        if (level)         url += '&level=' + encodeURIComponent(level);

                        fetch(url).then(r => r.json()).then(data => {
                            const entries = Array.isArray(data) ? data : (data.content || []);
                            const total   = data.totalElements || entries.length;
                            document.getElementById('logStats').textContent = `${total} entries`;

                            const tbody = document.getElementById('logsBody');
                            tbody.innerHTML = '';
                            entries.forEach(e => {
                                const levelClass = e.level === 'ERROR' ? 'text-danger fw-bold'
                                                 : e.level === 'WARN'  ? 'text-warning'
                                                 : e.level === 'DEBUG' ? 'text-muted' : '';
                                const ts = e.timestamp ? new Date(e.timestamp).toLocaleTimeString() : '';
                                tbody.innerHTML += `<tr>
                                    <td>${ts}</td>
                                    <td>${e.service || ''}</td>
                                    <td class="${levelClass}">${e.level || ''}</td>
                                    <td><code style="font-size:.75em">${(e.correlationId || '').substring(0,8)}</code></td>
                                    <td>${e.message || ''}</td>
                                </tr>`;
                            });
                            if (!entries.length) tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No log entries found</td></tr>';

                            // Pagination
                            renderLogPagination(data.totalPages || 1, currentLogPage);
                        }).catch(() => {});
                    }

                    function renderLogPagination(totalPages, current) {
                        const ul = document.getElementById('logsPagination');
                        ul.innerHTML = '';
                        for (let i = 0; i < Math.min(totalPages, 10); i++) {
                            ul.innerHTML += `<li class="page-item ${i === current ? 'active' : ''}">
                                <a class="page-link" href="#" onclick="searchLogs(${i})">${i + 1}</a></li>`;
                        }
                    }

                    function clearLogFilters() {
                        document.getElementById('logCorrelationId').value = '';
                        document.getElementById('logServiceFilter').value = '';
                        document.getElementById('logLevelFilter').value   = '';
                        searchLogs(0);
                    }

                    // ──────────────────────────────────────────────────
                    // Boot
                    // ──────────────────────────────────────────────────
                    document.addEventListener('DOMContentLoaded', function() {
                        refreshAll();
                        setInterval(refreshAll, 30000);
                    });
                    </script>
                </body>
                </html>
                """;
        Files.writeString(templatesPath.resolve("dashboard.html"), content);
        log.debug("Generated dashboard.html with Observability/Alerts/Traces/Logs sections");
    }
}
