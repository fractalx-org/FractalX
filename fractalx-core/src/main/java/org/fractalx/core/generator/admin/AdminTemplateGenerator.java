package org.fractalx.core.generator.admin;

import org.fractalx.core.FractalxVersion;
import org.fractalx.core.model.FractalModule;
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
                <html xmlns:th="http://www.thymeleaf.org" lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>FractalX Admin</title>
                    <link rel="preconnect" href="https://fonts.googleapis.com">
                    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&display=swap" rel="stylesheet">
                    <link rel="stylesheet" th:href="@{/webjars/font-awesome/6.4.0/css/all.min.css}">
                    <style>
                        *,*::before,*::after{box-sizing:border-box}
                        body{margin:0;font-family:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
                             background:#f9fafb;min-height:100vh;display:flex;align-items:center;
                             justify-content:center;color:#111827;-webkit-font-smoothing:antialiased}
                        .wrap{width:100%;max-width:400px;padding:20px}
                        .card{background:#fff;border:1px solid #e5e7eb;border-radius:12px;padding:40px 36px}
                        .logo{display:flex;align-items:center;gap:10px;margin-bottom:28px}
                        .logo-icon{width:32px;height:32px;background:#111827;border-radius:7px;
                                   display:flex;align-items:center;justify-content:center;
                                   color:#fff;font-size:13px;flex-shrink:0}
                        .logo-name{font-size:15px;font-weight:600;color:#111827}
                        h2{margin:0 0 6px;font-size:22px;font-weight:600;color:#111827}
                        .sub{margin:0 0 28px;font-size:13px;color:#6b7280}
                        label{display:block;font-size:13px;font-weight:500;color:#374151;margin-bottom:6px}
                        input{width:100%;padding:9px 12px;font-size:14px;font-family:inherit;
                              border:1px solid #d1d5db;border-radius:7px;color:#111827;background:#fff;
                              outline:none;transition:border-color .15s,box-shadow .15s}
                        input:focus{border-color:#111827;box-shadow:0 0 0 3px rgba(17,24,39,.08)}
                        .mb4{margin-bottom:18px}.mb5{margin-bottom:24px}
                        .btn-submit{width:100%;padding:10px;background:#111827;color:#fff;border:none;
                                    border-radius:7px;font-size:14px;font-weight:500;font-family:inherit;
                                    cursor:pointer;transition:background .15s}
                        .btn-submit:hover{background:#1f2937}
                        .alert-err{background:#fef2f2;border:1px solid #fecaca;color:#dc2626;
                                   font-size:13px;padding:10px 12px;border-radius:7px;margin-bottom:16px}
                        .alert-ok{background:#f0fdf4;border:1px solid #bbf7d0;color:#15803d;
                                  font-size:13px;padding:10px 12px;border-radius:7px;margin-bottom:16px}
                        .footer{text-align:center;margin-top:20px;font-size:12px;color:#9ca3af}
                    </style>
                </head>
                <body>
                    <div class="wrap">
                        <div class="card">
                            <div class="logo">
                                <div class="logo-icon"><svg width="16" height="17" viewBox="0 0 18 19" fill="none" xmlns="http://www.w3.org/2000/svg"><rect x="2.1054" y="4.60606" width="7.69016" height="4.57289" transform="rotate(27.4135 2.1054 4.60606)" fill="white"/><rect x="2.1054" width="14.9697" height="4.60606" fill="white"/><rect x="6.82661" y="7.6" width="10.2485" height="4.60606" fill="white"/><rect x="6.82661" y="12.2061" width="6.79394" height="4.72121" transform="rotate(90 6.82661 12.2061)" fill="white"/></svg></div>
                                <span class="logo-name">FractalX</span>
                            </div>
                            <h2>Sign in</h2>
                            <p class="sub">Access your microservices dashboard</p>
                            <div th:if="${param.error}" class="alert-err">
                                <i class="fas fa-exclamation-circle"></i>&nbsp;Invalid username or password.
                            </div>
                            <div th:if="${param.logout}" class="alert-ok">
                                <i class="fas fa-check-circle"></i>&nbsp;You have been signed out.
                            </div>
                            <form th:action="@{/login}" method="post">
                                <div class="mb4">
                                    <label for="username">Username</label>
                                    <input type="text" id="username" name="username" placeholder="admin" autofocus required>
                                </div>
                                <div class="mb5">
                                    <label for="password">Password</label>
                                    <input type="password" id="password" name="password" placeholder="••••••••" required>
                                </div>
                                <button type="submit" class="btn-submit">
                                    Sign in &nbsp;<i class="fas fa-arrow-right" style="font-size:11px"></i>
                                </button>
                            </form>
                        </div>
                        <div class="footer">FractalX v" + FractalxVersion.release() + " &mdash; Microservices Framework</div>
                    </div>
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
    // Dashboard — assembled from focused helpers to stay under JVM 65535-byte limit
    // -------------------------------------------------------------------------

    private String buildDashboard() {
        return buildHtmlHead()
            + "<body>\n"
            + "<div class=\"sb-overlay\" id=\"sb-overlay\" onclick=\"closeSidebar()\"></div>\n"
            + buildSidebar()
            + "<div class=\"main-wrap\" id=\"main-wrap\">\n"
            + buildTopbar()
            + "<div class=\"content-area\">\n"
            + buildSectionOverview()
            + buildSectionServices()
            + buildSectionCommunication()
            + buildSectionData()
            + buildSectionObservability()
            + buildSectionAlerts()
            + buildSectionTracesLogs()
            + buildSectionSettings()
            + buildSectionAnalytics()
            + buildSectionApiExplorer()
            + buildSectionNetworkMap()
            + buildSectionGrpcBrowser()
            + buildSectionIncidents()
            + buildSectionConfigEditor()
            + "</div>\n"
            + "</div>\n\n"
            + buildModals()
            + "<script src=\"https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js\"></script>\n"
            + "<script th:src=\"@{/webjars/jquery/3.7.0/jquery.min.js}\"></script>\n"
            + "<script th:src=\"@{/webjars/bootstrap/5.3.0/js/bootstrap.bundle.min.js}\"></script>\n"
            + "<script>\n"
            + buildScriptsNavAndOverview()
            + buildScriptsServices()
            + buildScriptsCommunication()
            + buildScriptsData()
            + buildScriptsObservabilityAndAlerts()
            + buildScriptsTracesLogsSettings()
            + buildScriptsAnalytics()
            + buildScriptsAnalyticsB()
            + buildScriptsApiExplorer()
            + buildScriptsNetworkMap()
            + buildScriptsNetworkMapB()
            + buildScriptsGrpcBrowser()
            + buildScriptsIncidents()
            + buildScriptsConfigEditor()
            + buildScriptsCircuitBreaker()
            + buildScriptsDecompositionStats()
            + buildScriptsOverviewEnhanced()
            + buildScriptsMobileNav()
            + "</script>\n</body>\n</html>\n";
    }

    // ---- HTML HEAD ----------------------------------------------------------

    private String buildHtmlHead() {
        return buildHtmlHeadA() + buildHtmlHeadB() + buildHtmlHeadC() + buildHtmlHeadD();
    }

    private String buildHtmlHeadA() {
        return """
                <!DOCTYPE html>
                <html xmlns:th="http://www.thymeleaf.org" lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>FractalX Admin</title>
                    <link rel="preconnect" href="https://fonts.googleapis.com">
                    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
                    <link rel="stylesheet" th:href="@{/webjars/bootstrap/5.3.0/css/bootstrap.min.css}">
                    <link rel="stylesheet" th:href="@{/webjars/font-awesome/6.4.0/css/all.min.css}">
                    <style>
                        :root{--sb:240px;--topbar:52px;--bg:#f5f5f5;--surf:#ffffff;
                              --bdr:#e5e7eb;--t1:#111827;--t2:#6b7280;--t3:#9ca3af;--r:8px}
                        *,*::before,*::after{box-sizing:border-box}
                        body{margin:0;font-family:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
                             background:var(--bg);color:var(--t1);font-size:14px;line-height:1.5;
                             -webkit-font-smoothing:antialiased}

                        /* ── Sidebar ── */
                        .sidebar{position:fixed;top:0;left:0;width:var(--sb);height:100vh;
                                 background:var(--surf);border-right:1px solid var(--bdr);
                                 display:flex;flex-direction:column;z-index:1000;
                                 overflow-y:auto;transition:transform .25s ease}
                        .sb-brand{display:flex;align-items:center;gap:10px;padding:16px;
                                  border-bottom:1px solid var(--bdr);text-decoration:none;color:var(--t1);
                                  flex-shrink:0}
                        .sb-icon{width:30px;height:30px;background:#111827;border-radius:7px;
                                 display:flex;align-items:center;justify-content:center;
                                 color:#fff;font-size:13px;flex-shrink:0}
                        .sb-name{font-size:14px;font-weight:600;color:#111827}
                        .nav-grp{padding:14px 8px 2px}
                        .nav-lbl{font-size:10.5px;font-weight:500;color:var(--t3);
                                 text-transform:uppercase;letter-spacing:.08em;padding:0 8px 4px}
                        .sidebar-nav a{display:flex;align-items:center;gap:9px;padding:7px 8px;
                                       margin-bottom:1px;color:var(--t2);text-decoration:none;
                                       font-size:13.5px;border-radius:6px;
                                       transition:background .12s,color .12s}
                        .sidebar-nav a .ni{width:15px;text-align:center;font-size:12px;
                                           flex-shrink:0;color:var(--t3);transition:color .12s}
                        .sidebar-nav a:hover,.sidebar-nav a.active{background:#f3f4f6;color:#111827}
                        .sidebar-nav a:hover .ni,.sidebar-nav a.active .ni{color:#111827}
                        .sidebar-nav a.active{font-weight:500}
                        .alert-badge{background:#ef4444;color:#fff;border-radius:10px;
                                     padding:1px 6px;font-size:10px;font-weight:600;
                                     margin-left:auto;line-height:1.5}
                        .sb-footer{margin-top:auto;padding:12px 8px;border-top:1px solid var(--bdr)}
                        .sb-footer a{display:flex;align-items:center;gap:8px;padding:7px 8px;
                                     color:var(--t2);text-decoration:none;font-size:13px;
                                     border-radius:6px;transition:background .12s}
                        .sb-footer a:hover{background:#fef2f2;color:#dc2626}
                        .sb-overlay{display:none;position:fixed;inset:0;background:rgba(0,0,0,.25);z-index:999}
                        .sb-overlay.open{display:block}
                """;
    }

    private String buildHtmlHeadB() {
        return """
                        /* ── Layout ── */
                        .main-wrap{margin-left:var(--sb);min-height:100vh;display:flex;flex-direction:column}
                        .topbar{background:var(--surf);border-bottom:1px solid var(--bdr);
                                padding:0 24px;height:var(--topbar);display:flex;align-items:center;
                                justify-content:space-between;position:sticky;top:0;z-index:100;flex-shrink:0}
                        .topbar-l{display:flex;align-items:center;gap:6px}
                        .topbar-brand{font-size:13px;color:var(--t2);text-decoration:none}
                        .topbar-sep{color:var(--t3);font-size:14px;padding:0 2px}
                        #page-title{font-size:13px;font-weight:500;color:var(--t1)}
                        .topbar-r{display:flex;align-items:center;gap:10px}
                        .refresh-ts{font-size:11.5px;color:var(--t3)}
                        .content-area{padding:24px;flex:1}
                        .hamburger{display:none;background:none;border:1px solid var(--bdr);
                                   border-radius:6px;width:34px;height:34px;align-items:center;
                                   justify-content:center;cursor:pointer;color:var(--t2);font-size:13px}

                        /* ── Cards ── */
                        .card2{background:var(--surf);border:1px solid var(--bdr);
                               border-radius:var(--r);overflow:hidden;margin-bottom:12px}
                        .card2:last-child{margin-bottom:0}
                        .card-hd{padding:12px 16px;border-bottom:1px solid var(--bdr);display:flex;
                                 align-items:center;justify-content:space-between;font-size:13px;
                                 font-weight:500;color:var(--t1);background:#fafafa}
                        .card-hd-l{display:flex;align-items:center;gap:7px}
                        .card-hd i{color:var(--t2);font-size:12px}
                        .card-bd{padding:0}

                        /* ── Stat cards ── */
                        .stats-row{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:16px}
                        .stat-card{background:var(--surf);border:1px solid var(--bdr);
                                   border-radius:var(--r);padding:18px 20px}
                        .stat-ic{width:34px;height:34px;border-radius:7px;display:flex;
                                 align-items:center;justify-content:center;font-size:14px;margin-bottom:12px}
                        .stat-val{font-size:26px;font-weight:600;line-height:1;
                                  color:var(--t1);margin-bottom:4px}
                        .stat-lbl{font-size:12px;color:var(--t2)}

                        /* ── Tables ── */
                        .table-wrap{overflow-x:auto}
                        .table{font-size:13px}
                        .table thead th{background:#fafafa;border-bottom:1px solid var(--bdr);
                                        font-size:10.5px;font-weight:500;color:var(--t2);
                                        text-transform:uppercase;letter-spacing:.05em;
                                        padding:9px 14px;white-space:nowrap}
                        .table tbody td{padding:11px 14px;border-bottom:1px solid #f3f4f6;
                                        vertical-align:middle;color:var(--t1)}
                        .table tbody tr:last-child td{border-bottom:none}
                        .table-sm thead th,.table-sm tbody td{padding:8px 12px}
                        .table-hover tbody tr:hover td{background:#fafafa}

                        /* ── Badges ── */
                        .badge-up{background:#dcfce7!important;color:#15803d!important}
                        .badge-down{background:#fee2e2!important;color:#b91c1c!important}
                        .badge-degraded{background:#fef3c7!important;color:#92400e!important}
                        .badge-unknown{background:#f3f4f6!important;color:#6b7280!important}
                        .badge-info{background:#dbeafe!important;color:#1d4ed8!important}
                        /* Process status pills */
                        .proc-pill{display:inline-flex;align-items:center;gap:4px;font-size:11px;font-weight:600;
                                   padding:2px 8px;border-radius:20px;letter-spacing:.02em}
                        .proc-pill.running{background:#dcfce7;color:#15803d;border:1px solid #bbf7d0}
                        .proc-pill.unreachable{background:#fee2e2;color:#b91c1c;border:1px solid #fecaca}
                        .proc-pill.unknown{background:#f3f4f6;color:#6b7280;border:1px solid #e5e7eb}
                        /* Component pills */
                        .comp-pill{display:inline-flex;align-items:center;gap:3px;font-size:10px;padding:2px 7px;
                                   border-radius:10px;border:1px solid #e5e7eb;background:#f9fafb;margin:1px;
                                   cursor:default;transition:opacity .15s}
                        .comp-pill:hover{opacity:.8}
                        .comp-pill.up{background:#f0fdf4;border-color:#bbf7d0;color:#15803d}
                        .comp-pill.down{background:#fef2f2;border-color:#fecaca;color:#b91c1c}
                        .comp-pill.unknown{background:#f3f4f6;border-color:#e5e7eb;color:#9ca3af}
                        .comp-pill.degraded{background:#fffbeb;border-color:#fde68a;color:#92400e}
                        /* Health card in detail modal */
                        .health-card{border-radius:10px;border:1px solid #e5e7eb;overflow:hidden;background:#fff}
                        .health-card-header{padding:10px 14px;background:#f9fafb;border-bottom:1px solid #e5e7eb;
                                            display:flex;align-items:center;gap:8px;font-weight:600;font-size:13px}
                        .comp-row{display:grid;grid-template-columns:auto 1fr auto;align-items:center;
                                  gap:8px;padding:7px 14px;border-bottom:1px solid #f1f5f9;font-size:12px}
                        .comp-row:last-child{border-bottom:none}
                        .comp-row:hover{background:#fafafa}
                        .comp-cat-tag{font-size:9px;padding:1px 5px;border-radius:8px;font-weight:600;
                                      text-transform:uppercase;letter-spacing:.04em}
                        .comp-cat-tag.process{background:#e0e7ff;color:#3730a3}
                        .comp-cat-tag.resource{background:#e0f2fe;color:#0369a1}
                        .comp-cat-tag.dependency{background:#fef3c7;color:#92400e}
                        .badge.bg-success{background:#dcfce7!important;color:#15803d!important}
                        .badge.bg-danger{background:#fee2e2!important;color:#b91c1c!important}
                        .badge.bg-warning{background:#fef9c3!important;color:#854d0e!important;color:#854d0e}
                        .badge.bg-secondary{background:#f3f4f6!important;color:#6b7280!important}
                        .badge.bg-info{background:#dbeafe!important;color:#1d4ed8!important}
                        .badge.bg-light{background:#f9fafb!important;color:#374151!important;
                                        border:1px solid var(--bdr)!important}
                        .badge.bg-primary{background:#ede9fe!important;color:#5b21b6!important}
                        .badge{font-size:11px;font-weight:500;border-radius:20px;padding:2px 8px}

                        /* ── Buttons override ── */
                        .btn{font-family:inherit;font-size:13px;font-weight:500;border-radius:6px;
                             transition:all .15s;display:inline-flex;align-items:center;gap:5px}
                        .btn-primary{background:#111827!important;border-color:#111827!important;color:#fff!important}
                        .btn-primary:hover{background:#1f2937!important;border-color:#1f2937!important}
                        .btn-secondary{background:var(--surf)!important;border-color:var(--bdr)!important;color:var(--t1)!important}
                        .btn-secondary:hover{background:#f9fafb!important}
                        .btn-outline-primary{color:var(--t1)!important;border-color:var(--bdr)!important;background:var(--surf)!important}
                        .btn-outline-primary:hover{background:#f3f4f6!important;border-color:#d1d5db!important;color:var(--t1)!important}
                        .btn-outline-secondary{color:var(--t2)!important;border-color:var(--bdr)!important;background:transparent!important}
                        .btn-outline-secondary:hover{background:#f3f4f6!important;color:var(--t1)!important}
                        .btn-outline-success{color:#15803d!important;border-color:#bbf7d0!important;background:transparent!important}
                        .btn-outline-success:hover{background:#f0fdf4!important}
                        .btn-outline-warning{color:#92400e!important;border-color:#fde68a!important;background:transparent!important}
                        .btn-outline-warning:hover{background:#fffbeb!important}
                        .btn-outline-danger{color:#b91c1c!important;border-color:#fca5a5!important;background:transparent!important}
                        .btn-outline-danger:hover{background:#fef2f2!important}
                        .btn-outline-info{color:#1d4ed8!important;border-color:#bfdbfe!important;background:transparent!important}
                        .btn-outline-info:hover{background:#eff6ff!important}
                        .btn-light{background:#f3f4f6!important;border-color:var(--bdr)!important;color:var(--t2)!important}
                        .btn-light:hover{background:#e5e7eb!important}
                        .btn-xs{padding:2px 7px!important;font-size:11px!important}

                        /* ── Forms ── */
                        .form-control,.form-select{font-family:inherit;font-size:13px;
                             border:1px solid var(--bdr);border-radius:6px;color:var(--t1);
                             background:var(--surf);transition:border-color .15s,box-shadow .15s}
                        .form-control:focus,.form-select:focus{border-color:#111827;
                             box-shadow:0 0 0 3px rgba(17,24,39,.07);outline:none}
                        .form-control-sm,.form-select-sm{padding:5px 9px;font-size:12.5px}
                        .form-label{font-size:13px;font-weight:500;color:var(--t1)}
                        .form-check-input:checked{background-color:#111827;border-color:#111827}

                        /* ── Sections ── */
                        .section{display:none}.section.active{display:block}

                        /* ── Command box ── */
                        .cmd-box{background:#0d0d0d;color:#4ade80;font-family:'Menlo','Monaco',monospace;
                                 padding:9px 14px;border-radius:6px;font-size:12px;cursor:pointer;
                                 user-select:all;overflow-x:auto;transition:background .2s;margin:3px 0}
                        .cmd-box:hover{background:#1a1a1a}

                        /* ── Topology ── */
                        .topology-grid{display:flex;flex-wrap:wrap;gap:10px;padding:16px}
                        .topo-node{background:var(--surf);border:1px solid var(--bdr);
                                   border-top:3px solid var(--bdr);border-radius:8px;
                                   padding:12px 14px;min-width:130px;text-align:center;font-size:13px}
                        .topo-node.microservice{border-top-color:#6366f1}
                        .topo-node.infrastructure{border-top-color:#10b981}
                        .node-type{font-size:10px;color:var(--t3);text-transform:uppercase;letter-spacing:.05em}
                        .node-port{font-size:11px;color:var(--t2)}

                        /* ── Settings tabs ── */
                        .settings-tabs{display:flex;border-bottom:1px solid var(--bdr);
                                       margin-bottom:16px;overflow-x:auto}
                        .tab-btn{background:none;border:none;border-bottom:2px solid transparent;
                                 padding:9px 14px;font-size:13px;color:var(--t2);cursor:pointer;
                                 font-family:inherit;margin-bottom:-1px;white-space:nowrap;
                                 transition:color .12s}
                        .tab-btn:hover{color:var(--t1)}
                        .tab-btn.active{color:var(--t1);font-weight:500;border-bottom-color:#111827}
                        .settings-pane{display:none}.settings-pane.active{display:block}

                        /* ── Modal ── */
                        .modal-content{border:1px solid var(--bdr);border-radius:12px;
                                       box-shadow:0 8px 32px rgba(0,0,0,.1);font-family:inherit}
                        .modal-header{border-bottom:1px solid var(--bdr);padding:16px 20px}
                        .modal-title{font-size:15px;font-weight:600}
                        .modal-body{padding:20px}
                        .modal-footer{border-top:1px solid var(--bdr);padding:14px 20px}

                        /* ── Accordion override ── */
                        .accordion-button{font-family:inherit;font-size:13px;background:#fafafa;
                                          color:var(--t1);padding:10px 14px}
                        .accordion-button:not(.collapsed){background:#f3f4f6;color:var(--t1);box-shadow:none}
                        .accordion-item{border-color:var(--bdr)}

                        /* ── Code ── */
                        code{font-family:'Menlo','Monaco','Courier New',monospace;font-size:11.5px;
                             background:#f3f4f6;padding:1px 5px;border-radius:4px;color:#374151}
                        pre{font-family:'Menlo','Monaco',monospace;font-size:12px;background:#f9fafb;
                            border:1px solid var(--bdr);border-radius:6px;padding:10px 12px;
                            overflow-x:auto;color:var(--t1);margin:0}

                        /* ── Responsive ── */
                        @media(max-width:900px){
                            .sidebar{transform:translateX(-100%);box-shadow:2px 0 16px rgba(0,0,0,.1)}
                            .sidebar.open{transform:translateX(0)}
                            .main-wrap{margin-left:0}
                            .hamburger{display:flex}
                            .stats-row{grid-template-columns:repeat(2,1fr)}
                            .content-area{padding:16px}
                        }
                        @media(max-width:480px){
                            .stats-row{grid-template-columns:1fr 1fr}
                            .topbar{padding:0 14px}
                        }

                        /* ── Utilities ── */
                        .text-muted{color:var(--t2)!important}
                        .ms-auto{margin-left:auto!important}
                        .two-col{display:grid;grid-template-columns:1fr 1fr;gap:12px}
                        @media(max-width:768px){.two-col{grid-template-columns:1fr}}
                """;
    }

    private String buildHtmlHeadC() {
        return """
                        /* ── Analytics ── */
                        .metric-card{background:var(--surf);border:1px solid var(--bdr);
                                     border-radius:var(--r);padding:16px;text-align:center}
                        .metric-card .val{font-size:28px;font-weight:700;color:var(--t1);line-height:1}
                        .metric-card .lbl{font-size:12px;color:var(--t2);margin-top:4px}
                        .chart-box{background:var(--surf);border:1px solid var(--bdr);
                                   border-radius:var(--r);padding:16px;position:relative}
                        .chart-box canvas{max-height:220px}
                        .metrics-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(140px,1fr));gap:10px;margin-bottom:16px}
                        .svc-metric-row{display:flex;align-items:center;gap:10px;padding:10px 14px;
                                        background:var(--surf);border:1px solid var(--bdr);border-radius:var(--r);margin-bottom:8px}
                        .svc-metric-row .svc-name{font-weight:500;min-width:130px}
                        .svc-chip{font-size:11px;padding:2px 8px;border-radius:10px;background:#f3f4f6;color:var(--t2)}
                        /* ── API Explorer ── */
                        .explorer-layout{display:grid;grid-template-columns:320px 1fr;gap:12px;min-height:500px}
                        @media(max-width:900px){.explorer-layout{grid-template-columns:1fr}}
                        .ep-list{max-height:520px;overflow-y:auto}
                        .ep-item{padding:8px 10px;border-radius:6px;cursor:pointer;
                                 display:flex;align-items:center;gap:8px;border:1px solid transparent}
                        .ep-item:hover{background:#f3f4f6;border-color:var(--bdr)}
                        .ep-item.active{background:#eff6ff;border-color:#bfdbfe}
                        .method-badge{font-size:10px;font-weight:700;padding:1px 6px;border-radius:4px;min-width:44px;text-align:center}
                        .method-GET{background:#d1fae5;color:#065f46}
                        .method-POST{background:#dbeafe;color:#1e40af}
                        .method-PUT{background:#fef3c7;color:#92400e}
                        .method-DELETE{background:#fee2e2;color:#991b1b}
                        .method-PATCH{background:#ede9fe;color:#5b21b6}
                        .response-area{font-family:monospace;font-size:12px;max-height:300px;
                                       overflow:auto;white-space:pre-wrap;word-break:break-all;
                                       background:#1e1e2e;color:#cdd6f4;border-radius:6px;padding:12px}
                        .status-ok{color:#22c55e;font-weight:600}
                        .status-err{color:#ef4444;font-weight:600}
                """;
    }

    private String buildHtmlHeadD() {
        return """
                        /* ── Network Map ── */
                        .netmap-wrap{position:relative;background:var(--surf);border:1px solid var(--bdr);border-radius:var(--r);overflow:hidden}
                        #netmap-canvas{display:block;width:100%;cursor:grab}
                        #netmap-canvas:active{cursor:grabbing}
                        .netmap-legend{display:flex;gap:14px;padding:10px 14px;border-top:1px solid var(--bdr);flex-wrap:wrap}
                        .netmap-legend-item{display:flex;align-items:center;gap:6px;font-size:12px;color:var(--t2)}
                        .netmap-legend-dot{width:12px;height:12px;border-radius:50%}
                        .netmap-tooltip{position:absolute;background:#1e1e2e;color:#cdd6f4;font-size:12px;
                                        padding:8px 12px;border-radius:6px;pointer-events:none;display:none;
                                        box-shadow:0 4px 12px rgba(0,0,0,.3);z-index:10;max-width:220px;line-height:1.6}
                        .netmap-controls{display:flex;gap:8px;padding:10px 14px;border-bottom:1px solid var(--bdr)}
                        /* ── gRPC Browser ── */
                        .grpc-svc-card{background:var(--surf);border:1px solid var(--bdr);border-radius:var(--r);padding:14px;margin-bottom:10px;cursor:pointer;transition:border-color .15s}
                        .grpc-svc-card:hover,.grpc-svc-card.active{border-color:#6366f1;background:#fafafe}
                        .grpc-port-chip{background:#ede9fe;color:#5b21b6;font-size:11px;font-weight:600;padding:2px 8px;border-radius:8px}
                        .grpc-dep-row{display:flex;align-items:center;gap:10px;padding:8px 12px;border-bottom:1px solid #f3f4f6}
                        .ping-btn{background:none;border:1px solid var(--bdr);border-radius:5px;font-size:11px;padding:2px 8px;cursor:pointer;transition:all .15s}
                        .ping-ok{color:#15803d;background:#dcfce7;border-color:#bbf7d0}
                        .ping-fail{color:#b91c1c;background:#fee2e2;border-color:#fecaca}
                        /* ── Incident Manager ── */
                        .sev-p1{background:#fee2e2;color:#b91c1c;font-weight:700}
                        .sev-p2{background:#fef3c7;color:#92400e;font-weight:700}
                        .sev-p3{background:#dbeafe;color:#1e40af;font-weight:700}
                        .sev-p4{background:#f3f4f6;color:#6b7280}
                        .inc-status-open{background:#fee2e2;color:#b91c1c}
                        .inc-status-investigating{background:#fef3c7;color:#92400e}
                        .inc-status-resolved{background:#dcfce7;color:#15803d}
                        .inc-row-p1 td:first-child{border-left:3px solid #ef4444}
                        .inc-row-p2 td:first-child{border-left:3px solid #f59e0b}
                        .inc-row-p3 td:first-child{border-left:3px solid #3b82f6}
                        .inc-row-p4 td:first-child{border-left:3px solid #d1d5db}
                        /* ── Config Editor ── */
                        .cfg-kv-row{display:flex;align-items:center;gap:8px;padding:6px 12px;border-bottom:1px solid #f3f4f6;font-size:13px}
                        .cfg-key{font-weight:500;min-width:200px;font-family:monospace;font-size:12px;color:var(--t2);flex-shrink:0}
                        .cfg-val{flex:1;font-family:monospace;font-size:12px;color:var(--t1);word-break:break-all}
                        .cfg-override-badge{font-size:10px;background:#fef3c7;color:#92400e;padding:1px 6px;border-radius:6px;flex-shrink:0}
                        /* ── Enhanced Overview ── */
                        .ov-kpi-grid{display:grid;grid-template-columns:repeat(6,1fr);gap:10px;margin-bottom:16px}
                        @media(max-width:1200px){.ov-kpi-grid{grid-template-columns:repeat(3,1fr)}}
                        @media(max-width:600px){.ov-kpi-grid{grid-template-columns:repeat(2,1fr)}}
                        .ov-kpi{background:var(--surf);border:1px solid var(--bdr);border-radius:var(--r);padding:14px 16px;text-align:center}
                        .ov-kpi .val{font-size:26px;font-weight:700;line-height:1}
                        .ov-kpi .lbl{font-size:11px;color:var(--t2);margin-top:3px}
                        .ov-alert-item{display:flex;gap:8px;align-items:flex-start;padding:8px 12px;border-bottom:1px solid #f3f4f6;font-size:12px}
                        .ov-inc-item{display:flex;gap:8px;align-items:center;padding:7px 12px;border-bottom:1px solid #f3f4f6;font-size:12px}
                        /* ── Enhanced API Explorer ── */
                        .explorer3-layout{display:grid;grid-template-columns:190px 1fr 1fr;min-height:600px;border:1px solid var(--bdr);border-radius:var(--r);overflow:hidden;background:var(--surf)}
                        @media(max-width:1100px){.explorer3-layout{grid-template-columns:1fr}}
                        .explorer3-col{border-right:1px solid var(--bdr);display:flex;flex-direction:column;overflow:hidden}
                        .explorer3-col:last-child{border-right:none}
                        .explorer3-col-hdr{padding:10px 12px;border-bottom:1px solid var(--bdr);background:#fafafa;font-size:12px;font-weight:600;color:var(--t1);flex-shrink:0;display:flex;align-items:center}
                        .explorer3-ep-list{overflow-y:auto;flex:1}
                        .explorer3-ep-group-hdr{padding:5px 12px;font-size:10px;font-weight:600;color:var(--t3);text-transform:uppercase;letter-spacing:.05em;background:#f9fafb;border-bottom:1px solid #f3f4f6}
                        .explorer3-ep-item{padding:7px 12px;display:flex;align-items:center;gap:7px;cursor:pointer;border-bottom:1px solid #f3f4f6;font-size:12px}
                        .explorer3-ep-item:hover{background:#f9fafb}
                        .explorer3-ep-item.active{background:#eff6ff}
                        .req-panel{padding:12px;overflow-y:auto;display:flex;flex-direction:column;gap:10px;flex:1}
                        .resp-panel{padding:12px;overflow-y:auto;display:flex;flex-direction:column;gap:0;flex:1}
                        .resp-tabs{display:flex;border-bottom:1px solid var(--bdr);margin:-12px -12px 10px;padding:0 12px;background:#fafafa;flex-shrink:0}
                        .resp-tab{padding:8px 12px;font-size:12px;font-weight:500;cursor:pointer;border-bottom:2px solid transparent;color:var(--t2)}
                        .resp-tab.active{color:var(--t1);border-bottom-color:#111827}
                        .resp-tab-pane{display:none}.resp-tab-pane.active{display:block}
                        .hist-item{padding:6px 10px;font-size:11px;cursor:pointer;border-bottom:1px solid #f3f4f6;display:flex;align-items:center;gap:7px}
                        .hist-item:hover{background:#f9fafb}
                        .json-s{color:#a3e635}.json-n{color:#fb923c}.json-b{color:#60a5fa}.json-k{color:#e879f9}.json-null{color:#94a3b8}
                    </style>
                </head>
                """;
    }

    // ---- SIDEBAR ------------------------------------------------------------

    private String buildSidebar() {
        return """
                <aside class="sidebar" id="sidebar">
                    <a class="sb-brand" href="#">
                        <div class="sb-icon"><svg width="16" height="17" viewBox="0 0 18 19" fill="none" xmlns="http://www.w3.org/2000/svg"><rect x="2.1054" y="4.60606" width="7.69016" height="4.57289" transform="rotate(27.4135 2.1054 4.60606)" fill="white"/><rect x="2.1054" width="14.9697" height="4.60606" fill="white"/><rect x="6.82661" y="7.6" width="10.2485" height="4.60606" fill="white"/><rect x="6.82661" y="12.2061" width="6.79394" height="4.72121" transform="rotate(90 6.82661 12.2061)" fill="white"/></svg></div>
                        <span class="sb-name">FractalX</span>
                    </a>
                    <nav class="sidebar-nav">
                        <div class="nav-grp">
                            <div class="nav-lbl">Overview</div>
                            <a href="#" onclick="showSection('overview');closeSidebar()" id="nav-overview" class="active">
                                <i class="fas fa-th-large ni"></i> Overview
                            </a>
                            <a href="#" onclick="showSection('services');closeSidebar()" id="nav-services">
                                <i class="fas fa-server ni"></i> Services
                            </a>
                        </div>
                        <div class="nav-grp">
                            <div class="nav-lbl">Architecture</div>
                            <a href="#" onclick="showSection('decomposition');closeSidebar()" id="nav-decomposition">
                                <i class="fas fa-cubes ni"></i> Decomposition
                            </a>
                            <a href="#" onclick="showSection('communication');closeSidebar()" id="nav-communication">
                                <i class="fas fa-project-diagram ni"></i> Communication
                            </a>
                            <a href="#" onclick="showSection('data');closeSidebar()" id="nav-data">
                                <i class="fas fa-database ni"></i> Data Consistency
                            </a>
                            <a href="#" onclick="showSection('networkmap');closeSidebar()" id="nav-networkmap">
                                <i class="fas fa-circle-dot ni"></i> Network Map
                            </a>
                        </div>
                        <div class="nav-grp">
                            <div class="nav-lbl">Monitoring</div>
                            <a href="#" onclick="showSection('observability');closeSidebar()" id="nav-observability">
                                <i class="fas fa-chart-line ni"></i> Observability
                            </a>
                            <a href="#" onclick="showSection('alerts');closeSidebar()" id="nav-alerts">
                                <i class="fas fa-bell ni"></i> Alerts
                                <span class="alert-badge" id="alert-badge" style="display:none">0</span>
                            </a>
                            <a href="#" onclick="showSection('traces');closeSidebar()" id="nav-traces">
                                <i class="fas fa-route ni"></i> Traces
                            </a>
                            <a href="#" onclick="showSection('logs');closeSidebar()" id="nav-logs">
                                <i class="fas fa-file-alt ni"></i> Logs
                            </a>
                            <a href="#" onclick="showSection('analytics');closeSidebar()" id="nav-analytics">
                                <i class="fas fa-chart-bar ni"></i> Analytics
                            </a>
                            <a href="#" onclick="showSection('circuitbreaker');closeSidebar()" id="nav-circuitbreaker" style="display:none">
                                <i class="fas fa-circle-notch ni"></i> Circuit Breakers
                            </a>
                        </div>
                        <div class="nav-grp">
                            <div class="nav-lbl">Developer</div>
                            <a href="#" onclick="showSection('explorer');closeSidebar()" id="nav-explorer">
                                <i class="fas fa-terminal ni"></i> API Explorer
                            </a>
                            <a href="#" onclick="showSection('grpc');closeSidebar()" id="nav-grpc">
                                <i class="fas fa-network-wired ni"></i> gRPC Browser
                            </a>
                            <a href="#" onclick="showSection('configeditor');closeSidebar()" id="nav-configeditor">
                                <i class="fas fa-sliders-h ni"></i> Config Editor
                            </a>
                        </div>
                        <div class="nav-grp">
                            <div class="nav-lbl">Operations</div>
                            <a href="#" onclick="showSection('incidents');closeSidebar()" id="nav-incidents">
                                <i class="fas fa-fire ni"></i> Incidents
                                <span class="alert-badge" id="incident-badge" style="display:none">0</span>
                            </a>
                            <a href="#" onclick="showSection('settings');closeSidebar()" id="nav-settings">
                                <i class="fas fa-cog ni"></i> Settings
                            </a>
                        </div>
                    </nav>
                    <div class="sb-footer">
                        <a href="/logout">
                            <i class="fas fa-sign-out-alt" style="width:15px;text-align:center"></i>
                            Sign out
                        </a>
                    </div>
                </aside>
                """;
    }

    // ---- TOPBAR -------------------------------------------------------------

    private String buildTopbar() {
        return """
                    <header class="topbar">
                        <div class="topbar-l">
                            <button class="hamburger" onclick="openSidebar()" aria-label="Open menu">
                                <i class="fas fa-bars"></i>
                            </button>
                            <a class="topbar-brand" href="#">FractalX</a>
                            <span class="topbar-sep">/</span>
                            <span id="page-title">Overview</span>
                        </div>
                        <div class="topbar-r">
                            <span class="refresh-ts">
                                <i class="fas fa-clock" style="margin-right:4px;color:var(--t3)"></i>
                                <span id="last-refresh">—</span>
                            </span>
                            <button class="btn btn-light btn-sm" onclick="refreshCurrent()">
                                <i class="fas fa-sync-alt"></i> Refresh
                            </button>
                            <span class="d-flex align-items-center gap-2"
                               style="font-size:13px;color:var(--t2);cursor:default">
                                <span style="width:26px;height:26px;background:#f3f4f6;border:1px solid var(--bdr);
                                             border-radius:50%;display:flex;align-items:center;justify-content:center;
                                             font-size:11px;color:var(--t2)">
                                    <i class="fas fa-user"></i>
                                </span>
                                <span id="current-user" style="font-weight:500;color:var(--t1)">admin</span>
                            </span>
                        </div>
                    </header>
                """;
    }

    // ---- SECTIONS -----------------------------------------------------------

    private String buildSectionOverview() {
        return buildSectionOverviewA() + buildSectionOverviewB();
    }

    private String buildSectionOverviewA() {
        return """
                    <div id="section-overview" class="section active">
                        <div class="stats-row">
                            <div class="stat-card">
                                <div class="stat-ic" style="background:#ede9fe">
                                    <i class="fas fa-server" style="color:#7c3aed"></i>
                                </div>
                                <div class="stat-val" id="ov-total">—</div>
                                <div class="stat-lbl">Total Services</div>
                            </div>
                            <div class="stat-card">
                                <div class="stat-ic" style="background:#dcfce7">
                                    <i class="fas fa-check-circle" style="color:#15803d"></i>
                                </div>
                                <div class="stat-val" id="ov-up">—</div>
                                <div class="stat-lbl">Running</div>
                            </div>
                            <div class="stat-card">
                                <div class="stat-ic" style="background:#fee2e2">
                                    <i class="fas fa-times-circle" style="color:#b91c1c"></i>
                                </div>
                                <div class="stat-val" id="ov-down">—</div>
                                <div class="stat-lbl">Down / Unknown</div>
                            </div>
                            <div class="stat-card">
                                <div class="stat-ic" style="background:#fef3c7">
                                    <i class="fas fa-bell" style="color:#d97706"></i>
                                </div>
                                <div class="stat-val" id="ov-alerts">—</div>
                                <div class="stat-lbl">Active Alerts</div>
                            </div>
                            <div class="stat-card">
                                <div class="stat-ic" style="background:#e0f2fe">
                                    <i class="fas fa-chart-line" style="color:#0284c7"></i>
                                </div>
                                <div class="stat-val" id="ov-rps">—</div>
                                <div class="stat-lbl">Total RPS</div>
                            </div>
                            <div class="stat-card">
                                <div class="stat-ic" style="background:#f0fdf4">
                                    <i class="fas fa-microchip" style="color:#16a34a"></i>
                                </div>
                                <div class="stat-val" id="ov-cpu">—</div>
                                <div class="stat-lbl">Avg CPU</div>
                            </div>
                        </div>
                        <div class="card2">
                            <div class="card-hd">
                                <div class="card-hd-l">
                                    <i class="fas fa-heartbeat" style="color:#10b981"></i>
                                    <span>Quick Health Status</span>
                                </div>
                                <button class="btn btn-light btn-sm" onclick="loadOverview()">
                                    <i class="fas fa-sync-alt"></i>
                                </button>
                            </div>
                            <div class="card-bd table-wrap">
                                <table class="table table-sm table-hover mb-0">
                                    <thead><tr>
                                        <th>Service</th><th>Status</th><th>Port</th><th>gRPC</th><th>Type</th><th>Actions</th>
                                    </tr></thead>
                                    <tbody id="overview-tbody">
                                        <tr><td colspan="6" class="text-center text-muted p-4">Loading…</td></tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                """;
    }

    private String buildSectionOverviewB() {
        return """
                        <div class="two-col" style="gap:16px;margin-top:16px">
                            <!-- Active Alerts card -->
                            <div style="background:#fff;border:1px solid #e5e7eb;border-radius:14px;box-shadow:0 1px 4px rgba(0,0,0,.05);overflow:hidden">
                                <div style="display:flex;align-items:center;justify-content:space-between;padding:14px 16px;border-bottom:1px solid #f1f5f9">
                                    <div style="display:flex;align-items:center;gap:8px">
                                        <div style="width:30px;height:30px;border-radius:8px;background:#fff7ed;display:flex;align-items:center;justify-content:center">
                                            <i class="fas fa-bell" style="color:#f59e0b;font-size:13px"></i>
                                        </div>
                                        <div>
                                            <div style="font-size:13px;font-weight:700;color:#111827">Active Alerts</div>
                                            <div style="font-size:10px;color:#9ca3af;margin-top:1px">Firing right now</div>
                                        </div>
                                    </div>
                                    <div style="display:flex;align-items:center;gap:10px">
                                        <span id="ov-alert-badge" style="display:none;background:#fef2f2;color:#ef4444;font-size:11px;font-weight:700;padding:2px 9px;border-radius:20px;border:1px solid #fecaca"></span>
                                        <a href="#" onclick="showSection('alerts')" style="font-size:11px;color:#6366f1;text-decoration:none;font-weight:500">View all →</a>
                                    </div>
                                </div>
                                <div id="ov-alerts-list" style="max-height:230px;overflow-y:auto;padding:10px 12px">
                                    <div style="text-align:center;padding:20px;color:#d1d5db;font-size:12px">
                                        <i class="fas fa-spinner fa-spin" style="font-size:16px;margin-bottom:6px;display:block"></i>Loading…
                                    </div>
                                </div>
                                <div style="padding:10px 16px;border-top:1px solid #f8fafc;background:#fafafa">
                                    <span style="font-size:10px;color:#9ca3af">Last checked: <span id="ov-alerts-ts">—</span></span>
                                </div>
                            </div>
                            <!-- Open Incidents card -->
                            <div style="background:#fff;border:1px solid #e5e7eb;border-radius:14px;box-shadow:0 1px 4px rgba(0,0,0,.05);overflow:hidden">
                                <div style="display:flex;align-items:center;justify-content:space-between;padding:14px 16px;border-bottom:1px solid #f1f5f9">
                                    <div style="display:flex;align-items:center;gap:8px">
                                        <div style="width:30px;height:30px;border-radius:8px;background:#fef2f2;display:flex;align-items:center;justify-content:center">
                                            <i class="fas fa-fire-alt" style="color:#ef4444;font-size:13px"></i>
                                        </div>
                                        <div>
                                            <div style="font-size:13px;font-weight:700;color:#111827">Open Incidents</div>
                                            <div style="font-size:10px;color:#9ca3af;margin-top:1px">Requires attention</div>
                                        </div>
                                    </div>
                                    <div style="display:flex;align-items:center;gap:10px">
                                        <span id="ov-inc-badge" style="display:none;background:#fef2f2;color:#ef4444;font-size:11px;font-weight:700;padding:2px 9px;border-radius:20px;border:1px solid #fecaca"></span>
                                        <a href="#" onclick="showSection('incidents')" style="font-size:11px;color:#6366f1;text-decoration:none;font-weight:500">View all →</a>
                                    </div>
                                </div>
                                <div id="ov-incidents-list" style="max-height:230px;overflow-y:auto;padding:10px 12px">
                                    <div style="text-align:center;padding:20px;color:#d1d5db;font-size:12px">
                                        <i class="fas fa-spinner fa-spin" style="font-size:16px;margin-bottom:6px;display:block"></i>Loading…
                                    </div>
                                </div>
                                <div style="padding:10px 16px;border-top:1px solid #f8fafc;background:#fafafa">
                                    <span style="font-size:10px;color:#9ca3af">Last checked: <span id="ov-incidents-ts">—</span></span>
                                </div>
                            </div>
                        </div>
                    </div>
                """;
    }

    private String buildSectionServices() {
        return """
                    <div id="section-services" class="section">
                        <div class="card2">
                            <div class="card-hd">
                                <div class="card-hd-l">
                                    <i class="fas fa-server" style="color:#6366f1"></i>
                                    <span>All Services</span>
                                </div>
                                <div class="d-flex gap-2">
                                    <input type="text" class="form-control form-control-sm"
                                           id="svc-filter" placeholder="Filter services…"
                                           style="width:160px" oninput="filterServicesTable(this.value)">
                                    <button class="btn btn-primary btn-sm" onclick="loadServicesAll()">
                                        <i class="fas fa-sync-alt"></i> Refresh
                                    </button>
                                </div>
                            </div>
                            <div class="card-bd table-wrap">
                                <table class="table table-sm table-hover mb-0" id="services-table">
                                    <thead><tr>
                                        <th>Name</th><th>Type</th><th>HTTP</th><th>gRPC</th>
                                        <th>Health</th><th>Dependencies</th><th>Version</th><th>Actions</th>
                                    </tr></thead>
                                    <tbody id="services-tbody">
                                        <tr><td colspan="8" class="text-center text-muted p-4">Loading…</td></tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                """;
    }

    private String buildSectionCommunication() {
        return """
                    <div id="section-decomposition" class="section">
                        <div class="page-header">
                            <div>
                                <h1 class="page-title-h">Decomposition</h1>
                                <p class="page-sub">Stats and breakdown from the last FractalX decomposition run</p>
                            </div>
                            <button class="btn btn-sm btn-primary" onclick="loadDecompositionStats()">
                                <i class="fas fa-sync-alt"></i> Refresh
                            </button>
                        </div>
                        <!-- Summary stat cards -->
                        <div id="decomp-cards" class="row g-3 mb-3">
                            <div class="col-12 text-center text-muted p-4">Loading…</div>
                        </div>
                        <!-- Services breakdown -->
                        <div class="card2 mb-3">
                            <div class="card-hd">
                                <div class="card-hd-l">
                                    <i class="fas fa-cubes" style="color:#6366f1"></i>
                                    <span>Services Breakdown</span>
                                </div>
                            </div>
                            <div class="card-bd table-wrap">
                                <table class="table table-sm mb-0">
                                    <thead><tr>
                                        <th>Service</th><th>Class</th><th>Port</th>
                                        <th>gRPC Port</th><th>Dependencies</th>
                                        <th>Schemas</th><th>Indep. Deploy</th>
                                    </tr></thead>
                                    <tbody id="decomp-services-tbody">
                                        <tr><td colspan="7" class="text-muted p-4 text-center">Loading…</td></tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                        <!-- Sagas breakdown -->
                        <div class="card2" id="decomp-sagas-card" style="display:none">
                            <div class="card-hd">
                                <div class="card-hd-l">
                                    <i class="fas fa-random" style="color:#8b5cf6"></i>
                                    <span>Distributed Sagas</span>
                                </div>
                            </div>
                            <div class="card-bd table-wrap">
                                <table class="table table-sm mb-0">
                                    <thead><tr>
                                        <th>Saga ID</th><th>Service</th><th>Method</th>
                                        <th>Steps</th><th>Timeout</th>
                                    </tr></thead>
                                    <tbody id="decomp-sagas-tbody"></tbody>
                                </table>
                            </div>
                        </div>
                    </div>

                    <div id="section-communication" class="section">
                        <div class="card2">
                            <div class="card-hd">
                                <div class="card-hd-l">
                                    <i class="fas fa-project-diagram" style="color:#6366f1"></i>
                                    <span>Service Dependency Topology</span>
                                </div>
                            </div>
                            <div id="topology-grid" class="topology-grid">
                                <span class="text-muted p-3">Loading topology…</span>
                            </div>
                        </div>
                        <div class="two-col" style="margin-top:12px">
                            <div class="card2">
                                <div class="card-hd">
                                    <div class="card-hd-l">
                                        <i class="fas fa-network-wired" style="color:#3b82f6"></i>
                                        <span>NetScope / gRPC Links</span>
                                    </div>
                                </div>
                                <div class="card-bd table-wrap">
                                    <table class="table table-sm mb-0">
                                        <thead><tr>
                                            <th>Source</th><th>Target</th><th>gRPC Port</th><th>Protocol</th>
                                        </tr></thead>
                                        <tbody id="netscope-tbody">
                                            <tr><td colspan="4" class="text-muted p-3">Loading…</td></tr>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                            <div>
                                <div class="card2">
                                    <div class="card-hd">
                                        <div class="card-hd-l">
                                            <i class="fas fa-exchange-alt" style="color:#10b981"></i>
                                            <span>API Gateway</span>
                                        </div>
                                    </div>
                                    <div class="card-bd p-3" id="gateway-info">
                                        <span class="text-muted">Loading…</span>
                                    </div>
                                </div>
                                <div class="card2">
                                    <div class="card-hd">
                                        <div class="card-hd-l">
                                            <i class="fas fa-satellite-dish" style="color:#f59e0b"></i>
                                            <span>Service Discovery</span>
                                        </div>
                                    </div>
                                    <div class="card-bd p-3" id="discovery-info">
                                        <span class="text-muted">Loading…</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                """;
    }

    private String buildSectionData() {
        return """
                    <div id="section-data" class="section">
                        <div class="stats-row" style="grid-template-columns:repeat(3,1fr)">
                            <div class="stat-card">
                                <div class="stat-ic" style="background:#ede9fe">
                                    <i class="fas fa-sitemap" style="color:#7c3aed"></i>
                                </div>
                                <div class="stat-val" id="data-saga-count">—</div>
                                <div class="stat-lbl">Saga Definitions</div>
                            </div>
                            <div class="stat-card">
                                <div class="stat-ic" style="background:#dbeafe">
                                    <i class="fas fa-database" style="color:#1d4ed8"></i>
                                </div>
                                <div class="stat-val" id="data-svc-count">—</div>
                                <div class="stat-lbl">Services with DB</div>
                            </div>
                            <div class="stat-card">
                                <div class="stat-ic" style="background:#dcfce7">
                                    <i class="fas fa-cogs" style="color:#15803d"></i>
                                </div>
                                <div class="stat-val" id="data-orch-health">—</div>
                                <div class="stat-lbl">Saga Orchestrator</div>
                            </div>
                        </div>
                        <div class="two-col">
                            <div class="card2">
                                <div class="card-hd">
                                    <div class="card-hd-l">
                                        <i class="fas fa-sitemap" style="color:#6366f1"></i>
                                        <span>Saga Definitions</span>
                                    </div>
                                    <button class="btn btn-outline-secondary btn-sm"
                                            onclick="loadSagaInstances(null)">
                                        <i class="fas fa-list-ul"></i> All Instances
                                    </button>
                                </div>
                                <div class="card-bd table-wrap">
                                    <table class="table table-sm mb-0">
                                        <thead><tr>
                                            <th>Saga ID</th><th>Owner</th><th>Steps</th><th>Compensation</th><th></th>
                                        </tr></thead>
                                        <tbody id="sagas-tbody">
                                            <tr><td colspan="5" class="text-muted p-3">Loading…</td></tr>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                            <div class="card2">
                                <div class="card-hd">
                                    <div class="card-hd-l">
                                        <i class="fas fa-database" style="color:#f59e0b"></i>
                                        <span>Database Health</span>
                                    </div>
                                </div>
                                <div class="card-bd table-wrap">
                                    <table class="table table-sm mb-0">
                                        <thead><tr>
                                            <th>Service</th><th>Schemas</th><th>Health</th><th>Actions</th>
                                        </tr></thead>
                                        <tbody id="databases-tbody">
                                            <tr><td colspan="4" class="text-muted p-3">Loading…</td></tr>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </div>
                        <!-- Saga Instances Panel (shown on demand) -->
                        <div id="instances-panel" class="card2" style="margin-top:12px;display:none">
                            <div class="card-hd">
                                <div class="card-hd-l">
                                    <i class="fas fa-stream" style="color:#6366f1"></i>
                                    <span id="instances-panel-title">Saga Instances</span>
                                </div>
                                <button class="btn btn-sm btn-outline-secondary" onclick="closeInstancesPanel()">
                                    <i class="fas fa-times"></i> Close
                                </button>
                            </div>
                            <div class="card-bd table-wrap">
                                <table class="table table-sm mb-0">
                                    <thead><tr>
                                        <th>#</th><th>Correlation ID</th><th>Status</th>
                                        <th>Step Progress</th><th>Started</th><th>Updated</th><th></th>
                                    </tr></thead>
                                    <tbody id="instances-tbody">
                                        <tr><td colspan="7" class="text-muted p-3 text-center">Loading…</td></tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                        <div class="card2" style="margin-top:12px">
                            <div class="card-hd">
                                <div class="card-hd-l">
                                    <i class="fas fa-inbox" style="color:#10b981"></i>
                                    <span>Outbox Events</span>
                                </div>
                            </div>
                            <div class="card-bd p-3" id="outbox-info">
                                <span class="text-muted">Loading…</span>
                            </div>
                        </div>
                    </div>
                    <!-- Saga Instance Detail Modal -->
                    <div class="modal fade" id="instanceDetailModal" tabindex="-1">
                        <div class="modal-dialog modal-lg">
                            <div class="modal-content">
                                <div class="modal-header" style="border-bottom:1px solid #e5e7eb">
                                    <h5 class="modal-title">
                                        <i class="fas fa-info-circle me-2" style="color:#6366f1"></i>Saga Instance Detail
                                    </h5>
                                    <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                                </div>
                                <div class="modal-body" id="instance-detail-body" style="padding:20px">
                                    Loading…
                                </div>
                            </div>
                        </div>
                    </div>
                """;
    }

    private String buildSectionObservability() {
        return """
                    <div id="section-observability" class="section">
                        <div class="row g-3 mb-3" id="metrics-cards">
                            <div class="col-12 text-center text-muted p-4">Loading metrics…</div>
                        </div>
                        <div class="card2">
                            <div class="card-hd">
                                <div class="card-hd-l">
                                    <i class="fas fa-chart-bar" style="color:#6366f1"></i>
                                    <span>Service Health Metrics</span>
                                </div>
                                <button class="btn btn-primary btn-sm" onclick="loadMetrics()">
                                    <i class="fas fa-sync-alt"></i> Refresh
                                </button>
                            </div>
                            <div class="card-bd table-wrap">
                                <table class="table table-sm mb-0">
                                    <thead><tr>
                                        <th>Service</th><th>Health</th><th>Response P99</th>
                                        <th>Error Rate</th><th>Uptime</th>
                                    </tr></thead>
                                    <tbody id="metrics-tbody">
                                        <tr><td colspan="5" class="text-muted p-4 text-center">Loading…</td></tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                        <div class="card2" style="margin-top:12px">
                            <div class="card-hd">
                                <div class="card-hd-l">
                                    <i class="fas fa-broadcast-tower" style="color:#3b82f6"></i>
                                    <span>OpenTelemetry Configuration</span>
                                </div>
                            </div>
                            <div class="card-bd p-3">
                                <div class="row g-3">
                                    <div class="col-md-6">
                                        <div class="text-muted mb-1" style="font-size:11.5px">OTLP Endpoint</div>
                                        <div class="cmd-box">${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}</div>
                                    </div>
                                    <div class="col-md-6">
                                        <div class="text-muted mb-1" style="font-size:11.5px">Jaeger UI</div>
                                        <div class="cmd-box">
                                            <a href="http://localhost:16686" target="_blank"
                                               style="color:#4ade80;text-decoration:none">
                                                http://localhost:16686
                                                <i class="fas fa-external-link-alt" style="font-size:10px;margin-left:4px"></i>
                                            </a>
                                        </div>
                                    </div>
                                </div>
                                <p class="text-muted mt-2 mb-0" style="font-size:12px">
                                    All services export spans via OTLP/gRPC to Jaeger.
                                    Correlation IDs propagated via W3C <code>traceparent</code> + <code>X-Correlation-Id</code>.
                                </p>
                            </div>
                        </div>
                    </div>
                """;
    }

    private String buildSectionAlerts() {
        return """
                    <div id="section-alerts" class="section">
                        <div class="card2">
                            <div class="card-hd">
                                <div class="card-hd-l">
                                    <i class="fas fa-exclamation-triangle" style="color:#f59e0b"></i>
                                    <span>Active Alerts</span>
                                    <span class="badge bg-danger ms-1" id="active-alert-count">0</span>
                                </div>
                                <button class="btn btn-primary btn-sm" onclick="loadAlerts()">
                                    <i class="fas fa-sync-alt"></i> Refresh
                                </button>
                            </div>
                            <div class="card-bd table-wrap">
                                <table class="table table-sm table-hover mb-0">
                                    <thead><tr>
                                        <th>Time</th><th>Service</th><th>Severity</th><th>Message</th><th>Action</th>
                                    </tr></thead>
                                    <tbody id="active-alerts-tbody">
                                        <tr><td colspan="5" class="text-center text-muted p-4">No active alerts</td></tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                        <div class="card2" style="margin-top:12px">
                            <div class="card-hd">
                                <div class="card-hd-l">
                                    <i class="fas fa-history" style="color:var(--t2)"></i>
                                    <span>Alert History</span>
                                </div>
                            </div>
                            <div class="card-bd table-wrap">
                                <table class="table table-sm table-hover mb-0">
                                    <thead><tr>
                                        <th>Time</th><th>Service</th><th>Severity</th><th>Message</th><th>Status</th>
                                    </tr></thead>
                                    <tbody id="alert-history-tbody">
                                        <tr><td colspan="5" class="text-center text-muted p-4">No alerts recorded</td></tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                        <div class="card2" style="margin-top:12px">
                            <div class="card-hd">
                                <div class="card-hd-l">
                                    <i class="fas fa-sliders-h" style="color:#3b82f6"></i>
                                    <span>Alert Rules</span>
                                </div>
                            </div>
                            <div class="card-bd p-3" id="alert-config-info">
                                <span class="text-muted">Loading…</span>
                            </div>
                        </div>
                    </div>
                """;
    }

    private String buildSectionTracesLogs() {
        return """
                    <div id="section-traces" class="section">
                        <div class="card2">
                            <div class="card-hd">
                                <div class="card-hd-l">
                                    <i class="fas fa-route" style="color:#6366f1"></i>
                                    <span>Distributed Trace Search</span>
                                </div>
                            </div>
                            <div class="card-bd p-3">
                                <div class="row g-2 mb-3">
                                    <div class="col-md-5">
                                        <input type="text" class="form-control form-control-sm"
                                               id="trace-correlation-id"
                                               placeholder="Correlation ID (X-Correlation-Id)">
                                    </div>
                                    <div class="col-md-4">
                                        <select class="form-select form-select-sm" id="trace-service-select">
                                            <option value="">— All Services —</option>
                                        </select>
                                    </div>
                                    <div class="col-md-3">
                                        <button class="btn btn-primary btn-sm w-100" onclick="searchTraces()">
                                            <i class="fas fa-search"></i> Search
                                        </button>
                                    </div>
                                </div>
                                <div class="table-wrap">
                                    <table class="table table-sm mb-0">
                                        <thead><tr>
                                            <th>Trace ID</th><th>Correlation ID</th><th>Service</th>
                                            <th>Duration</th><th>Spans</th><th>Jaeger</th><th>Logs</th>
                                        </tr></thead>
                                        <tbody id="traces-tbody">
                                            <tr><td colspan="7" class="text-center text-muted p-4">
                                                Enter a Correlation ID or service to search
                                            </td></tr>
                                        </tbody>
                                    </table>
                                </div>
                                <div id="trace-pagination" class="d-flex gap-2 flex-wrap mt-2"></div>
                                <div class="mt-2">
                                    <a href="http://localhost:16686" target="_blank"
                                       style="font-size:12px;color:#3b82f6;text-decoration:none">
                                        <i class="fas fa-external-link-alt me-1"></i>Open Jaeger UI
                                    </a>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div id="section-logs" class="section">
                        <div class="card2">
                            <div class="card-hd">
                                <div class="card-hd-l">
                                    <i class="fas fa-file-alt" style="color:var(--t2)"></i>
                                    <span>Log Viewer</span>
                                </div>
                            </div>
                            <div class="card-bd p-3">
                                <div class="row g-2 mb-3">
                                    <div class="col-md-3">
                                        <input type="text" class="form-control form-control-sm"
                                               id="log-correlation-id" placeholder="Correlation ID">
                                    </div>
                                    <div class="col-md-3">
                                        <select class="form-select form-select-sm" id="log-service-select">
                                            <option value="">— All Services —</option>
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
                                        <button class="btn btn-primary btn-sm w-100" onclick="searchLogs(0)">
                                            <i class="fas fa-search"></i> Search
                                        </button>
                                    </div>
                                    <div class="col-md-2">
                                        <button class="btn btn-outline-secondary btn-sm w-100" onclick="loadLogStats()">
                                            <i class="fas fa-chart-pie"></i> Stats
                                        </button>
                                    </div>
                                </div>
                                <div class="table-wrap">
                                    <table class="table table-sm table-hover mb-2" style="font-size:.82rem">
                                        <thead><tr>
                                            <th>Time</th><th>Service</th><th>Level</th>
                                            <th>Correlation ID</th><th>Message</th>
                                        </tr></thead>
                                        <tbody id="logs-tbody">
                                            <tr><td colspan="5" class="text-center text-muted p-4">
                                                Use filters above to search logs
                                            </td></tr>
                                        </tbody>
                                    </table>
                                </div>
                                <div id="log-pagination" class="d-flex gap-2 flex-wrap"></div>
                            </div>
                        </div>
                        <div class="card2" id="log-stats-card" style="display:none;margin-top:12px">
                            <div class="card-hd">
                                <div class="card-hd-l">
                                    <i class="fas fa-chart-pie"></i>
                                    <span>Log Statistics</span>
                                </div>
                            </div>
                            <div class="card-bd p-3" id="log-stats-content"></div>
                        </div>
                    </div>
                """;
    }

    private String buildSectionSettings() {
        return """
                    <div id="section-settings" class="section">
                        <div class="settings-tabs">
                            <button class="tab-btn" onclick="showSettingsTab('users')" style="display:none">
                                <i class="fas fa-users me-1"></i>Users
                            </button>
                            <button class="tab-btn active" onclick="showSettingsTab('configuration')">
                                <i class="fas fa-sliders-h me-1"></i>Configuration
                            </button>
                            <button class="tab-btn" onclick="showSettingsTab('notifications')">
                                <i class="fas fa-bell me-1"></i>Notifications
                            </button>
                            <button class="tab-btn" onclick="showSettingsTab('general')">
                                <i class="fas fa-wrench me-1"></i>General
                            </button>
                        </div>

                        <div id="settings-pane-users" class="settings-pane" style="display:none">
                            <div class="card2">
                                <div class="card-hd">
                                    <div class="card-hd-l">
                                        <i class="fas fa-users" style="color:#6366f1"></i>
                                        <span>User Management</span>
                                    </div>
                                    <button class="btn btn-primary btn-sm"
                                            data-bs-toggle="modal" data-bs-target="#addUserModal">
                                        <i class="fas fa-plus"></i> Add User
                                    </button>
                                </div>
                                <div class="card-bd table-wrap">
                                    <table class="table table-sm table-hover mb-0">
                                        <thead><tr>
                                            <th>Username</th><th>Roles</th><th>Status</th>
                                            <th>Last Login</th><th>Created</th><th>Actions</th>
                                        </tr></thead>
                                        <tbody id="users-tbody">
                                            <tr><td colspan="6" class="text-muted p-4 text-center">Loading…</td></tr>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </div>

                        <div id="settings-pane-configuration" class="settings-pane active">
                            <div class="card2">
                                <div class="card-hd">
                                    <div class="card-hd-l">
                                        <i class="fas fa-network-wired" style="color:#3b82f6"></i>
                                        <span>Port Mapping</span>
                                    </div>
                                </div>
                                <div class="card-bd table-wrap">
                                    <table class="table table-sm mb-0">
                                        <thead><tr>
                                            <th>Service</th><th>HTTP Port</th><th>gRPC Port</th>
                                            <th>Outbox</th><th>Commands</th>
                                        </tr></thead>
                                        <tbody id="config-ports-tbody">
                                            <tr><td colspan="5" class="text-muted p-3">Loading…</td></tr>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                            <div class="card2" style="margin-top:12px">
                                <div class="card-hd">
                                    <div class="card-hd-l">
                                        <i class="fas fa-code" style="color:var(--t2)"></i>
                                        <span>Environment Variables</span>
                                    </div>
                                </div>
                                <div class="card-bd p-2">
                                    <div class="accordion" id="env-accordion">
                                        <div class="text-muted p-3">Loading…</div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div id="settings-pane-notifications" class="settings-pane">
                            <div class="card2">
                                <div class="card-hd">
                                    <div class="card-hd-l">
                                        <i class="fas fa-bell" style="color:#f59e0b"></i>
                                        <span>Alert Channels</span>
                                    </div>
                                </div>
                                <div class="card-bd p-3" id="notification-config-content">
                                    <span class="text-muted">Loading…</span>
                                </div>
                            </div>
                        </div>

                        <div id="settings-pane-general" class="settings-pane">
                            <div class="card2" style="max-width:560px">
                                <div class="card-hd">
                                    <div class="card-hd-l">
                                        <i class="fas fa-wrench" style="color:var(--t2)"></i>
                                        <span>General Settings</span>
                                    </div>
                                </div>
                                <div class="card-bd p-4">
                                    <form onsubmit="updateSettings(event)">
                                        <div class="mb-3" style="display:none">
                                            <label class="form-label">Site Name</label>
                                            <input type="text" class="form-control" id="setting-site-name"
                                                   value="FractalX Admin">
                                        </div>
                                        <div class="mb-3">
                                            <label class="form-label">Session Timeout (minutes)</label>
                                            <input type="number" class="form-control"
                                                   id="setting-session-timeout" value="30" min="5" max="1440">
                                        </div>
                                        <div class="mb-3" style="display:none">
                                            <label class="form-label">Default Alert Email</label>
                                            <input type="email" class="form-control" id="setting-alert-email"
                                                   placeholder="alerts@example.com">
                                        </div>
                                        <div class="mb-4 form-check" style="display:none">
                                            <input type="checkbox" class="form-check-input" id="setting-maintenance">
                                            <label class="form-check-label" for="setting-maintenance"
                                                   style="font-weight:400">Maintenance Mode</label>
                                        </div>
                                        <div class="d-flex align-items-center gap-3">
                                            <button type="submit" class="btn btn-primary">
                                                <i class="fas fa-save"></i> Save Settings
                                            </button>
                                            <span id="settings-save-status" class="text-muted"
                                                  style="font-size:12px;display:none">
                                                <i class="fas fa-check" style="color:#10b981"></i> Saved
                                            </span>
                                        </div>
                                    </form>
                                </div>
                            </div>

                            <div class="card2" style="max-width:560px;margin-top:16px">
                                <div class="card-hd">
                                    <div class="card-hd-l">
                                        <i class="fas fa-key" style="color:#f59e0b"></i>
                                        <span>Change Password</span>
                                    </div>
                                </div>
                                <div class="card-bd p-4">
                                    <form onsubmit="changeAdminPassword(event)">
                                        <div class="mb-3">
                                            <label class="form-label">New Password</label>
                                            <input type="password" class="form-control"
                                                   id="gen-new-password" placeholder="Enter new password"
                                                   minlength="6" required>
                                        </div>
                                        <div class="mb-4">
                                            <label class="form-label">Confirm New Password</label>
                                            <input type="password" class="form-control"
                                                   id="gen-confirm-password" placeholder="Confirm new password"
                                                   minlength="6" required>
                                        </div>
                                        <div id="change-pw-error" class="text-danger mb-2"
                                             style="font-size:13px;display:none"></div>
                                        <div class="d-flex align-items-center gap-3">
                                            <button type="submit" class="btn btn-warning">
                                                <i class="fas fa-key"></i> Update Password
                                            </button>
                                            <span id="change-pw-status" class="text-success"
                                                  style="font-size:13px;display:none">
                                                <i class="fas fa-check"></i> Password updated
                                            </span>
                                        </div>
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
                                    <i class="fas fa-server me-2" style="color:#6366f1"></i>
                                    <span id="svc-detail-title">Service Detail</span>
                                </h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body" id="svc-detail-body">Loading…</div>
                        </div>
                    </div>
                </div>
                <div class="modal fade" id="lifecycleModal" tabindex="-1">
                    <div class="modal-dialog">
                        <div class="modal-content">
                            <div class="modal-header">
                                <h5 class="modal-title">
                                    <i class="fas fa-terminal me-2"></i>
                                    Lifecycle &mdash; <span id="lc-service-name"></span>
                                </h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body">
                                <p class="text-muted mb-3" style="font-size:12.5px">
                                    Run these commands from your project directory where
                                    <code>docker-compose.yml</code> resides.
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
                                <h5 class="modal-title">
                                    <i class="fas fa-user-plus me-2" style="color:#6366f1"></i>Add User
                                </h5>
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
                                <span id="add-user-error" class="text-danger" style="font-size:12.5px"></span>
                            </div>
                            <div class="modal-footer">
                                <button type="button" class="btn btn-secondary"
                                        data-bs-dismiss="modal">Cancel</button>
                                <button type="button" class="btn btn-primary"
                                        onclick="createUser()">Create User</button>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="modal fade" id="changePasswordModal" tabindex="-1">
                    <div class="modal-dialog">
                        <div class="modal-content">
                            <div class="modal-header">
                                <h5 class="modal-title">
                                    Change Password &mdash; <span id="cp-username"></span>
                                </h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body">
                                <input type="hidden" id="cp-user">
                                <div class="mb-3">
                                    <label class="form-label">New Password</label>
                                    <input type="password" class="form-control" id="cp-new-password">
                                </div>
                                <span id="cp-error" class="text-danger" style="font-size:12.5px"></span>
                            </div>
                            <div class="modal-footer">
                                <button type="button" class="btn btn-secondary"
                                        data-bs-dismiss="modal">Cancel</button>
                                <button type="button" class="btn btn-outline-warning"
                                        onclick="submitPasswordChange()">Change Password</button>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="modal fade" id="dbDetailsModal" tabindex="-1">
                    <div class="modal-dialog modal-lg">
                        <div class="modal-content">
                            <div class="modal-header">
                                <h5 class="modal-title">
                                    <i class="fas fa-database me-2" style="color:#f59e0b"></i>
                                    <span id="db-details-title">Database Details</span>
                                </h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body" id="db-details-body">Loading…</div>
                        </div>
                    </div>
                </div>
                """;
    }

    // ---- SCRIPTS: navigation + overview -------------------------------------

    private String buildScriptsNavAndOverview() {
        return """
                // ── Health helpers ─────────────────────────────────────────────────────────
                function healthStatus(h) {
                    if (!h) return 'UNKNOWN';
                    return typeof h === 'object' ? (h.status || 'UNKNOWN') : String(h);
                }

                function processBadge(h) {
                    const ps = (h && typeof h === 'object') ? (h.processStatus || 'UNKNOWN') : 'UNKNOWN';
                    const cls  = ps === 'RUNNING'     ? 'running'
                               : ps === 'UNREACHABLE' ? 'unreachable' : 'unknown';
                    const icon = ps === 'RUNNING'     ? '●'
                               : ps === 'UNREACHABLE' ? '○' : '?';
                    return `<span class="proc-pill ${cls}" title="Process status">${icon} ${ps}</span>`;
                }

                function healthBadge(h, large) {
                    const s   = healthStatus(h);
                    const sz  = large ? ' fs-6' : ' small';
                    const cls = s==='UP'       ? 'badge-up'
                              : s==='DEGRADED' ? 'badge-degraded'
                              : s==='DOWN'     ? 'badge-down'
                              :                  'badge-unknown';
                    const tip = (typeof h === 'object' && h.degraded)
                        ? ' title="Service process is RUNNING — a dependency is unreachable"' : '';
                    const icon = s==='UP' ? '✓' : s==='DEGRADED' ? '⚠' : s==='DOWN' ? '✗' : '?';
                    return `<span class="badge ${cls}${sz}"${tip}>${icon} ${s}</span>`;
                }

                /** Compact row: [● RUNNING] [⚠ DEGRADED] */
                function healthCell(h) {
                    return `<div style="display:flex;flex-direction:column;gap:3px">
                        <div style="display:flex;gap:4px;flex-wrap:wrap;align-items:center">
                            ${processBadge(h)} ${healthBadge(h)}
                        </div>
                        ${componentPills(h)}
                    </div>`;
                }

                function componentPills(h) {
                    if (!h || typeof h !== 'object' || !h.components) return '';
                    const comps = h.components;
                    if (!comps || Object.keys(comps).length === 0) return '';
                    return '<div style="display:flex;flex-wrap:wrap;gap:2px;margin-top:2px">' +
                        Object.entries(comps).map(([name, detail]) => {
                            const st  = typeof detail === 'object' ? (detail.status || '?') : String(detail);
                            const err = typeof detail === 'object' ? (detail.error || detail.description || '') : '';
                            const cls = st==='UP' ? 'up' : st==='DOWN' ? 'down' : 'unknown';
                            const dot = st==='UP' ? '●' : st==='DOWN' ? '○' : '◌';
                            const tip = err ? `${name}: ${st} — ${err}` : `${name}: ${st}`;
                            return `<span class="comp-pill ${cls}" title="${escHtml(tip)}">${dot} ${name}</span>`;
                        }).join('') + '</div>';
                }

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
                        settings: loadSettingsSection,
                        analytics: loadAnalyticsSection, explorer: loadExplorerServices,
                        networkmap: loadNetworkMap, grpc: loadGrpcBrowser,
                        incidents: loadIncidents, configeditor: loadConfigEditor,
                        circuitbreaker: loadCircuitBreakers,
                        decomposition: loadDecompositionStats
                    };
                    if (fn[currentSection]) fn[currentSection]();
                    document.getElementById('last-refresh').textContent = new Date().toLocaleTimeString();
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
                        const dep = s.deployment ? s.deployment.version || '?' : '?';
                        const depStr = (m.dependencies && m.dependencies.length)
                            ? m.dependencies.join(', ') : '<span class="text-muted">none</span>';
                        const pills = componentPills(s.health);
                        tbody.innerHTML += `<tr>
                            <td><strong>${m.name}</strong></td>
                            <td><span class="badge bg-light text-dark small">${m.type}</span></td>
                            <td>${m.port || '-'}</td>
                            <td>${m.grpcPort || '-'}</td>
                            <td style="min-width:180px">${healthCell(s.health)}</td>
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
                    document.getElementById('svc-detail-body').innerHTML = '<div class="text-muted p-3">Loading…</div>';
                    const modal = new bootstrap.Modal(document.getElementById('serviceDetailModal'));
                    modal.show();
                    fetch('/api/services/' + name + '/detail')
                        .then(r => r.json()).then(d => {
                            const m    = d.meta;
                            const rawH = d.health || {};
                            const dep  = d.deployment || {};
                            const stages = dep.stages || [];
                            const stageHtml = stages.map(sg =>
                                `<span class="badge bg-success me-1">${sg.name} ✓</span>`).join('');

                            // Health breakdown
                            const ps         = rawH.processStatus || 'UNKNOWN';
                            const overallSt  = rawH.status        || 'UNKNOWN';
                            const isDegraded = rawH.degraded === true;
                            const connErr    = rawH.error || null;
                            const comps      = rawH.components || {};

                            // Build component rows grouped by category
                            const catOrder = ['process','resource','dependency'];
                            const catLabel = {process:'Process',resource:'Resource',dependency:'Dependency'};
                            const compEntries = Object.entries(comps);
                            const compRows = catOrder.flatMap(cat => {
                                const rows = compEntries.filter(([,v]) =>
                                    (typeof v === 'object' ? v.category : 'dependency') === cat);
                                if (!rows.length) return [];
                                return [
                                    `<div style="padding:4px 14px 2px;font-size:10px;font-weight:700;
                                        text-transform:uppercase;letter-spacing:.06em;color:#9ca3af;
                                        background:#f9fafb;border-bottom:1px solid #f1f5f9">${catLabel[cat]}</div>`,
                                    ...rows.map(([name, detail]) => {
                                        const st  = typeof detail === 'object' ? (detail.status||'?') : String(detail);
                                        const err = typeof detail === 'object' ? (detail.error||detail.description||'') : '';
                                        const stCls = st==='UP' ? 'badge-up' : st==='DOWN' ? 'badge-down' : 'badge-unknown';
                                        const icon  = st==='UP' ? '✓' : st==='DOWN' ? '✗' : '?';
                                        return `<div class="comp-row">
                                            <span class="comp-cat-tag ${cat}">${cat[0].toUpperCase()}</span>
                                            <span style="font-family:monospace;font-size:12px">${name}
                                                ${err ? `<br><span style="font-size:10px;color:#9ca3af">${escHtml(err)}</span>` : ''}
                                            </span>
                                            <span class="badge ${stCls} small">${icon} ${st}</span>
                                        </div>`;
                                    })
                                ];
                            }).join('');

                            const psIcon  = ps==='RUNNING' ? '●' : '○';
                            const psCls   = ps==='RUNNING' ? '#15803d' : '#b91c1c';
                            const psBg    = ps==='RUNNING' ? '#f0fdf4' : '#fef2f2';
                            const stIcon  = overallSt==='UP' ? '✓' : overallSt==='DEGRADED' ? '⚠' : '✗';
                            const stCl    = overallSt==='UP' ? '#15803d' : overallSt==='DEGRADED' ? '#92400e' : '#b91c1c';
                            const stBg    = overallSt==='UP' ? '#f0fdf4' : overallSt==='DEGRADED' ? '#fffbeb' : '#fef2f2';

                            document.getElementById('svc-detail-body').innerHTML = `
                                <div class="row g-3">
                                    <div class="col-md-5">
                                        <h6 class="text-muted mb-2" style="font-size:11px;text-transform:uppercase;letter-spacing:.06em">Service Info</h6>
                                        <table class="table table-sm mb-0" style="font-size:12px">
                                            <tr><th>Name</th><td><strong>${m.name}</strong></td></tr>
                                            <tr><th>Type</th><td><span class="badge bg-light text-dark">${m.type}</span></td></tr>
                                            <tr><th>HTTP</th><td>:${m.port}</td></tr>
                                            <tr><th>gRPC</th><td>${m.grpcPort ? ':'+m.grpcPort : '—'}</td></tr>
                                            <tr><th>Package</th><td><code style="font-size:10px">${m.packageName||'—'}</code></td></tr>
                                        </table>

                                        <h6 class="text-muted mt-3 mb-2" style="font-size:11px;text-transform:uppercase;letter-spacing:.06em">Status</h6>
                                        <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px">
                                            <div style="border-radius:8px;padding:10px;background:${psBg};border:1px solid ${psCls}22;text-align:center">
                                                <div style="font-size:20px;color:${psCls}">${psIcon}</div>
                                                <div style="font-size:10px;color:#6b7280;margin:2px 0">PROCESS</div>
                                                <div style="font-size:12px;font-weight:700;color:${psCls}">${ps}</div>
                                            </div>
                                            <div style="border-radius:8px;padding:10px;background:${stBg};border:1px solid ${stCl}22;text-align:center">
                                                <div style="font-size:20px;color:${stCl}">${stIcon}</div>
                                                <div style="font-size:10px;color:#6b7280;margin:2px 0">HEALTH</div>
                                                <div style="font-size:12px;font-weight:700;color:${stCl}">${overallSt}</div>
                                            </div>
                                        </div>
                                        ${isDegraded ? `
                                        <div style="margin-top:8px;padding:8px 10px;border-radius:8px;
                                            background:#fffbeb;border:1px solid #fde68a;font-size:11px;color:#92400e">
                                            <strong>⚠ Running with degraded dependencies</strong><br>
                                            The service process is healthy. One or more external services it depends on are currently unreachable.
                                        </div>` : ''}
                                        ${connErr ? `
                                        <div style="margin-top:8px;padding:8px 10px;border-radius:8px;
                                            background:#fef2f2;border:1px solid #fecaca;font-size:11px;color:#b91c1c;
                                            overflow-wrap:break-word;word-break:break-word;max-width:100%">
                                            <strong>✗ Unreachable</strong><br>${escHtml(connErr)}
                                        </div>` : ''}
                                    </div>
                                    <div class="col-md-7">
                                        <h6 class="text-muted mb-2" style="font-size:11px;text-transform:uppercase;letter-spacing:.06em">Health Components</h6>
                                        <div class="health-card">
                                            <div class="health-card-header">
                                                <i class="fas fa-heartbeat" style="color:#10b981"></i>
                                                Actuator Components
                                                <span class="ms-auto" style="font-size:10px;font-weight:400;color:#9ca3af">
                                                    ${Object.keys(comps).length} checked
                                                </span>
                                            </div>
                                            ${compRows || '<div style="padding:16px;text-align:center;color:#9ca3af;font-size:12px">No component data available</div>'}
                                        </div>
                                        <h6 class="text-muted mt-3 mb-1" style="font-size:11px;text-transform:uppercase;letter-spacing:.06em">Deployment</h6>
                                        <div>${stageHtml || '<span class="text-muted small">No stage data</span>'}</div>
                                    </div>
                                </div>
                            `;
                        }).catch(() => {
                            document.getElementById('svc-detail-body').innerHTML =
                                '<div class="text-danger p-3">Failed to load service detail</div>';
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
                                    <span class="text-muted small">API Gateway (port 9999)</span>
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
                                tbody.innerHTML = '<tr><td colspan="5" class="text-muted">No saga definitions</td></tr>';
                                return;
                            }
                            sagas.forEach(s => {
                                const stepsList = (s.steps||[]).map((st, i) => {
                                    const parts = st.split(':');
                                    const svc = parts[0], method = parts[1] || st;
                                    const compEntry = (s.compensationSteps||[])[i] || '';
                                    const compMethod = compEntry ? (compEntry.split(':')[1] || compEntry) : '';
                                    const detailId = `sd-${s.sagaId.replace(/[^a-z0-9]/gi,'')}-${i}`;
                                    return `<div style="font-size:11px;padding:2px 0;cursor:pointer;user-select:none" onclick="toggleStepDetail('${detailId}')">
                                        <span style="color:#9ca3af">${i+1}.</span>
                                        <span style="color:#6366f1">${svc}</span> → <strong>${method}</strong>
                                        ${compMethod ? `<span style="color:#f59e0b;font-size:10px;margin-left:4px" title="Compensation: ${compMethod}">↩</span>` : ''}
                                    </div>
                                    <div id="${detailId}" style="display:none;background:#f8faff;border-left:3px solid #6366f1;padding:5px 10px;margin:1px 0 3px 14px;border-radius:0 4px 4px 0;font-size:10.5px">
                                        <div><span class="text-muted">Service:</span> <strong>${svc}</strong></div>
                                        <div><span class="text-muted">Method:</span> <code style="font-size:10px">${method}</code></div>
                                        ${compMethod
                                            ? `<div><span class="text-muted">Compensation:</span> <code style="font-size:10px;color:#f59e0b">↩ ${compMethod}</code></div>`
                                            : `<div class="text-muted" style="font-style:italic">No compensation defined</div>`}
                                    </div>`;
                                }).join('');
                                tbody.innerHTML += `<tr>
                                    <td><code style="font-size:12px">${s.sagaId}</code></td>
                                    <td><small>${s.orchestratedBy}</small></td>
                                    <td><div style="min-width:200px">${stepsList}</div></td>
                                    <td style="text-align:center">${(s.compensationSteps||[]).length > 0 ?
                                        '<span class="badge bg-success small">Yes</span>' :
                                        '<span class="badge bg-secondary small">No</span>'}</td>
                                    <td><button class="btn btn-sm" style="font-size:11px;padding:2px 8px;background:#6366f115;color:#6366f1;border:1px solid #6366f130;border-radius:4px" onclick="loadSagaInstances('${s.sagaId}')"><i class="fas fa-list-ul me-1"></i>Instances</button></td>
                                </tr>`;
                            });
                        }).catch(() => {
                            document.getElementById('sagas-tbody').innerHTML =
                                '<tr><td colspan="5" class="text-muted">No sagas configured</td></tr>';
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
                                    <td><button class="btn btn-xs btn-outline-info" onclick="showDbDetails('${db.service}')">Details</button></td>
                                </tr>`;
                            });
                        }).catch(() => {
                            document.getElementById('databases-tbody').innerHTML =
                                '<tr><td colspan="4" class="text-muted">DB health unavailable</td></tr>';
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

                function loadSagaInstances(sagaIdFilter) {
                    const panel = document.getElementById('instances-panel');
                    const title = document.getElementById('instances-panel-title');
                    panel.style.display = 'block';
                    title.textContent = sagaIdFilter ? `Instances — ${sagaIdFilter}` : 'All Saga Instances';
                    const tbody = document.getElementById('instances-tbody');
                    tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted p-3"><i class="fas fa-spinner fa-spin me-2"></i>Loading…</td></tr>';
                    panel.scrollIntoView({behavior:'smooth', block:'start'});

                    fetch('/api/data/sagas/instances/enriched')
                        .then(r => r.json())
                        .then(data => {
                            if (data && data.error) {
                                tbody.innerHTML = `<tr><td colspan="7" class="text-danger p-3 text-center">${data.error}</td></tr>`;
                                return;
                            }
                            const instances = sagaIdFilter ? (data||[]).filter(i => i.sagaId === sagaIdFilter) : (data||[]);
                            if (!instances.length) {
                                tbody.innerHTML = '<tr><td colspan="7" class="text-muted p-3 text-center">No instances found</td></tr>';
                                return;
                            }
                            const statusClass = {DONE:'badge-up',IN_PROGRESS:'bg-primary',STARTED:'bg-info text-dark',COMPENSATING:'bg-warning text-dark',FAILED:'badge-down'};
                            tbody.innerHTML = '';
                            instances.forEach(inst => {
                                const sc = statusClass[inst.status] || 'bg-secondary';
                                const shortId = (inst.correlationId||'').substring(0,8)+'…';
                                const progress = buildStepProgressBubbles(inst.stepProgress||[]);
                                const started = fmtTs(inst.startedAt);
                                const updated = fmtTs(inst.updatedAt);
                                const dataAttr = encodeURIComponent(JSON.stringify(inst));
                                tbody.innerHTML += `<tr>
                                    <td class="text-muted small">${inst.id||'—'}</td>
                                    <td><code title="${inst.correlationId}" style="font-size:11px">${shortId}</code></td>
                                    <td><span class="badge ${sc}" style="font-size:11px">${inst.status}</span></td>
                                    <td>${progress}</td>
                                    <td class="text-muted small">${started}</td>
                                    <td class="text-muted small">${updated}</td>
                                    <td><button class="btn btn-sm" style="font-size:11px;padding:2px 8px;background:#f3f4f6;border:1px solid #e5e7eb;border-radius:4px" onclick="showInstanceDetail(decodeURIComponent('${dataAttr}'))">Detail</button></td>
                                </tr>`;
                            });
                        })
                        .catch(() => {
                            tbody.innerHTML = '<tr><td colspan="7" class="text-danger p-3 text-center">Saga orchestrator unavailable</td></tr>';
                        });
                }

                function buildStepProgressBubbles(steps) {
                    if (!steps.length) return '<span class="text-muted small">—</span>';
                    const icons = {COMPLETED:'<span style="color:#22c55e" title="COMPLETED">✓</span>',IN_PROGRESS:'<span style="color:#3b82f6;font-weight:bold" title="IN_PROGRESS">▶</span>',FAILED:'<span style="color:#ef4444" title="FAILED">✗</span>',PENDING:'<span style="color:#d1d5db" title="PENDING">○</span>'};
                    return '<span style="font-size:14px;letter-spacing:2px">' + steps.map(sp => `<span title="${sp.step}">${(icons[sp.status]||'○')}</span>`).join('<span style="color:#d1d5db;font-size:10px"> › </span>') + '</span>';
                }

                function fmtTs(ts) {
                    if (!ts) return '—';
                    if (Array.isArray(ts)) {
                        const [y,mo,d,h,mi,s] = ts;
                        return `${y}-${String(mo).padStart(2,'0')}-${String(d).padStart(2,'0')} ${String(h).padStart(2,'0')}:${String(mi).padStart(2,'0')}:${String(s).padStart(2,'0')}`;
                    }
                    return new Date(ts).toLocaleString();
                }

                function showInstanceDetail(raw) {
                    const inst = JSON.parse(raw);
                    const statusColor = {DONE:'#22c55e',IN_PROGRESS:'#3b82f6',STARTED:'#06b6d4',COMPENSATING:'#f59e0b',FAILED:'#ef4444'};
                    const sc = statusColor[inst.status] || '#6b7280';
                    const stepIcons = {COMPLETED:'✓',IN_PROGRESS:'▶',FAILED:'✗',PENDING:'○'};
                    const stepColors = {COMPLETED:'#22c55e',IN_PROGRESS:'#3b82f6',FAILED:'#ef4444',PENDING:'#9ca3af'};

                    const stepsHtml = (inst.stepProgress||[]).map((sp, i) => {
                        const parts = sp.step.split(':');
                        const svc = parts[0], method = parts[1]||sp.step;
                        const ic = stepIcons[sp.status]||'○';
                        const col = stepColors[sp.status]||'#9ca3af';
                        const isCurrent = sp.status === 'IN_PROGRESS' || sp.status === 'FAILED';
                        return `<div style="display:flex;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid #f3f4f6${isCurrent?';background:#f0f9ff;margin:0 -8px;padding:8px':''}" >
                            <span style="width:24px;height:24px;border-radius:50%;background:${col}20;display:flex;align-items:center;justify-content:center;color:${col};font-size:13px;font-weight:bold;flex-shrink:0">${ic}</span>
                            <div style="flex:1">
                                <div style="font-weight:500;font-size:13px">${i+1}. ${method}</div>
                                <div style="color:#6b7280;font-size:11px">${svc}</div>
                            </div>
                            <span style="font-size:10px;padding:2px 8px;border-radius:10px;background:${col}20;color:${col};font-weight:600">${sp.status}</span>
                        </div>`;
                    }).join('');

                    let payload = '—';
                    try { payload = JSON.stringify(JSON.parse(inst.payload||'{}'), null, 2); } catch(e) { payload = inst.payload||'—'; }

                    // Derive compensation actions taken (only for FAILED/COMPENSATING sagas)
                    let compensationHtml = '';
                    if (inst.status === 'FAILED' || inst.status === 'COMPENSATING') {
                        const compSteps = inst.compensationSteps || [];
                        const completedIdxs = (inst.stepProgress || [])
                            .map((sp, i) => ({...sp, idx: i}))
                            .filter(sp => sp.status === 'COMPLETED')
                            .reverse(); // compensations run in reverse order
                        if (completedIdxs.length > 0) {
                            const rows = completedIdxs.map(sp => {
                                const compEntry = compSteps[sp.idx] || '';
                                const compParts = compEntry.split(':');
                                const compSvc    = compParts[0] || sp.step.split(':')[0];
                                const compMethod = compParts[1] || compEntry;
                                const fwdMethod  = sp.step.split(':')[1] || sp.step;
                                return `<div style="display:flex;align-items:center;gap:10px;padding:7px 0;border-bottom:1px solid #fef3c7">
                                    <span style="width:22px;height:22px;border-radius:50%;background:#f59e0b20;display:flex;align-items:center;justify-content:center;color:#f59e0b;font-size:13px;flex-shrink:0">↩</span>
                                    <div style="flex:1">
                                        <div style="font-size:12px;font-weight:500">${compMethod || '—'}</div>
                                        <div style="font-size:10px;color:#6b7280">${compSvc} — compensates <code style="font-size:10px">${fwdMethod}</code></div>
                                    </div>
                                </div>`;
                            }).join('');
                            compensationHtml = `<div style="margin-bottom:16px">
                                <div style="font-weight:600;font-size:13px;margin-bottom:8px;color:#d97706">
                                    <i class="fas fa-undo-alt me-1"></i>Compensation Actions Taken (${completedIdxs.length})
                                </div>
                                <div style="border:1px solid #fde68a;border-radius:8px;padding:0 12px;background:#fffbeb">${rows}</div>
                            </div>`;
                        } else {
                            compensationHtml = `<div style="margin-bottom:16px;padding:10px 12px;background:#fef3c7;border:1px solid #fde68a;border-radius:6px;font-size:12px;color:#92400e">
                                <i class="fas fa-info-circle me-1"></i>No compensation actions taken — saga failed at first step.
                            </div>`;
                        }
                    }

                    document.getElementById('instance-detail-body').innerHTML = `
                        <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:16px;padding-bottom:16px;border-bottom:1px solid #f3f4f6">
                            <div><div class="text-muted" style="font-size:11px;margin-bottom:2px">SAGA ID</div><code style="font-size:13px">${inst.sagaId||'—'}</code></div>
                            <div><div class="text-muted" style="font-size:11px;margin-bottom:2px">STATUS</div><span style="font-weight:700;color:${sc};font-size:14px">${inst.status}</span></div>
                            <div style="grid-column:1/-1"><div class="text-muted" style="font-size:11px;margin-bottom:2px">CORRELATION ID</div><code style="font-size:11px;word-break:break-all">${inst.correlationId||'—'}</code></div>
                            <div><div class="text-muted" style="font-size:11px;margin-bottom:2px">OWNER SERVICE</div><span style="font-size:13px">${inst.ownerService||'—'}</span></div>
                            <div><div class="text-muted" style="font-size:11px;margin-bottom:2px">STARTED</div><span style="font-size:12px">${fmtTs(inst.startedAt)}</span></div>
                            <div><div class="text-muted" style="font-size:11px;margin-bottom:2px">LAST UPDATED</div><span style="font-size:12px">${fmtTs(inst.updatedAt)}</span></div>
                        </div>
                        ${inst.errorMessage ? `<div style="background:#fef2f2;border:1px solid #fecaca;border-radius:6px;padding:10px 12px;margin-bottom:16px;font-size:12px;color:#dc2626"><strong>Error:</strong> ${inst.errorMessage}</div>` : ''}
                        ${compensationHtml}
                        <div style="margin-bottom:16px">
                            <div style="font-weight:600;font-size:13px;margin-bottom:8px;color:#374151">Step Execution (${(inst.stepProgress||[]).length} steps)</div>
                            <div style="border:1px solid #e5e7eb;border-radius:8px;padding:0 12px">${stepsHtml||'<div class="text-muted small p-3 text-center">No step data</div>'}</div>
                        </div>
                        <div>
                            <div style="font-weight:600;font-size:13px;margin-bottom:6px;color:#374151">Payload</div>
                            <pre style="background:#f8f9fa;border:1px solid #e5e7eb;border-radius:6px;padding:12px;font-size:11px;max-height:160px;overflow:auto;margin:0;color:#374151">${payload}</pre>
                        </div>`;
                    new bootstrap.Modal(document.getElementById('instanceDetailModal')).show();
                }

                function closeInstancesPanel() {
                    document.getElementById('instances-panel').style.display = 'none';
                }

                function toggleStepDetail(id) {
                    const el = document.getElementById(id);
                    if (el) el.style.display = el.style.display === 'none' ? 'block' : 'none';
                }

                function showDbDetails(service) {
                    fetch('/api/data/databases/config/' + service)
                        .then(r => r.json()).then(cfg => {
                            const isH2 = cfg.isH2;
                            let html = `
                                <table class="table table-sm">
                                    <tr><th>Service</th><td>${cfg.service}</td></tr>
                                    <tr><th>URL</th><td><code>${cfg.url || '-'}</code></td></tr>
                                    <tr><th>Username</th><td><code>${cfg.username || '-'}</code></td></tr>
                                    <tr><th>Password</th><td><code>${cfg.password || (isH2 ? '(empty)' : '***')}</code></td></tr>
                                    <tr><th>Driver</th><td><code>${cfg.driverClassName || '-'}</code></td></tr>
                                    <tr><th>Type</th><td>${isH2 ? '<span class="badge badge-info">H2 (in-memory)</span>' : '<span class="badge badge-unknown">External</span>'}</td></tr>
                                </table>`;
                            if (isH2 && cfg.h2ConsoleUrl) {
                                html += `<div class="mt-2">
                                    <a href="${cfg.h2ConsoleUrl}" target="_blank" class="btn btn-sm btn-warning">
                                        <i class="fas fa-external-link-alt"></i> Open H2 Console
                                    </a>
                                    <small class="text-muted ms-2">JDBC URL: <code>${cfg.url}</code> &nbsp; User: <code>${cfg.username}</code> &nbsp; Password: <em>leave empty</em></small>
                                </div>`;
                            }
                            document.getElementById('db-details-body').innerHTML = html;
                            document.getElementById('db-details-title').textContent = cfg.service + ' — Database Details';
                            const modal = new bootstrap.Modal(document.getElementById('dbDetailsModal'));
                            modal.show();
                        }).catch(e => alert('Could not load DB config: ' + e));
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
                    // Load from Jaeger's own service list (services that have actually reported spans)
                    fetch('/api/traces/services')
                        .then(r => r.json()).then(resp => {
                            const sel = document.getElementById('trace-service-select');
                            sel.innerHTML = '<option value="">— All Services —</option>';
                            const services = resp.data || resp || [];
                            services.forEach(s => {
                                const o = document.createElement('option');
                                o.value = o.textContent = typeof s === 'string' ? s : (s.meta?.name || s.name || s);
                                sel.appendChild(o);
                            });
                        }).catch(() => {
                            // Fallback: load from FractalX internal service registry
                            fetch('/api/services/all')
                                .then(r => r.json()).then(services => {
                                    const sel = document.getElementById('trace-service-select');
                                    sel.innerHTML = '<option value="">— All Services —</option>';
                                    services.forEach(s => {
                                        const o = document.createElement('option');
                                        o.value = o.textContent = s.meta?.name || s;
                                        sel.appendChild(o);
                                    });
                                }).catch(() => {});
                        });
                }

                let allTraces = [];
                let currentTracePage = 0;

                function searchTraces(page) {
                    if (page === undefined || page === 0) {
                        currentTracePage = 0;
                        const cid    = document.getElementById('trace-correlation-id').value.trim();
                        const svc    = document.getElementById('trace-service-select').value;
                        const params = new URLSearchParams();
                        if (cid) params.set('correlationId', cid);
                        if (svc) params.set('service', svc);
                        params.set('limit', '100');
                        fetch('/api/traces?' + params)
                            .then(r => r.json()).then(data => {
                                allTraces = (data.data || data || []).slice().reverse();
                                renderTracesPage(0);
                            }).catch(() => {
                                document.getElementById('traces-tbody').innerHTML =
                                    '<tr><td colspan="7" class="text-warning">Jaeger unavailable or no traces found</td></tr>';
                                document.getElementById('trace-pagination').innerHTML = '';
                            });
                    } else {
                        currentTracePage = page;
                        renderTracesPage(page);
                    }
                }

                function renderTracesPage(page) {
                    const pageSize = 20;
                    const start = page * pageSize;
                    const pageTraces = allTraces.slice(start, start + pageSize);
                    const tbody = document.getElementById('traces-tbody');
                    tbody.innerHTML = '';
                    if (!pageTraces.length) {
                        tbody.innerHTML = '<tr><td colspan="7" class="text-muted text-center">No traces found</td></tr>';
                        renderTracePagination(page, 0);
                        return;
                    }
                    const searchCid = document.getElementById('trace-correlation-id').value.trim();
                    pageTraces.forEach(t => {
                        const traceId = t.traceID || t.traceId || '';
                        const svcName = t.processes ? Object.values(t.processes)[0]?.serviceName || '-' : '-';
                        const dur     = t.duration ? (t.duration / 1000).toFixed(2) + 'ms' : '-';
                        const spans   = t.spans ? t.spans.length : '-';
                        let cid = '';
                        if (t.spans && t.spans.length > 0) {
                            for (const sp of t.spans) {
                                const tag = (sp.tags || []).find(tg =>
                                    tg.key === 'correlation.id' || tg.key === 'correlationId' || tg.key === 'x-correlation-id');
                                if (tag) { cid = tag.value; break; }
                            }
                        }
                        if (!cid) cid = searchCid;
                        const cidShort = cid ? cid.substring(0, 12) + (cid.length > 12 ? '…' : '') : '-';
                        const cidEsc = cid.replace(/\\\\/g, '\\\\\\\\').replace(/'/g, "\\\\'");
                        tbody.innerHTML += `<tr>
                            <td><code style="font-size:.75rem" title="${traceId}">${traceId.substring(0,16)}…</code></td>
                            <td><code style="font-size:.72rem" title="${cid}">${cidShort}</code></td>
                            <td>${svcName}</td><td>${dur}</td><td>${spans}</td>
                            <td><a href="http://localhost:16686/trace/${traceId}" target="_blank"
                                   class="btn btn-xs btn-sm btn-outline-info py-0">
                                <i class="fas fa-external-link-alt"></i></a></td>
                            <td>${cid ? `<button class="btn btn-xs btn-sm btn-outline-success py-0" title="View logs for this correlation ID" onclick="goToLogsWithCorrelation('${cidEsc}')"><i class="fas fa-file-alt"></i></button>` : ''}</td>
                        </tr>`;
                    });
                    renderTracePagination(page, pageTraces.length);
                }

                function renderTracePagination(page, count) {
                    const div = document.getElementById('trace-pagination');
                    div.innerHTML = '';
                    if (page > 0) {
                        const btn = document.createElement('button');
                        btn.className = 'btn btn-sm btn-outline-secondary';
                        btn.textContent = '\\u2190 Previous';
                        btn.onclick = () => searchTraces(page - 1);
                        div.appendChild(btn);
                    }
                    if (count >= 20) {
                        const btn = document.createElement('button');
                        btn.className = 'btn btn-sm btn-outline-primary';
                        btn.textContent = 'Next \\u2192';
                        btn.onclick = () => searchTraces(page + 1);
                        div.appendChild(btn);
                    }
                    if (allTraces.length > 0) {
                        const info = document.createElement('span');
                        info.className = 'text-muted small align-self-center';
                        info.textContent = 'Page ' + (page + 1) + ' \\u00b7 ' + allTraces.length + ' total';
                        div.appendChild(info);
                    }
                }

                function goToLogsWithCorrelation(cid) {
                    document.getElementById('log-correlation-id').value = cid;
                    showSection('logs');
                    searchLogs(0);
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
                            if (Array.isArray(logs)) logs.reverse();
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
                    const tbody = document.getElementById('users-tbody');
                    tbody.innerHTML = '<tr><td colspan="6" class="text-center p-4"><span class="text-muted" style="font-size:12px">Loading users…</span></td></tr>';
                    fetch('/api/users')
                        .then(r => r.json()).then(users => {
                            if (!users || !users.length) {
                                tbody.innerHTML = '<tr><td colspan="6" class="text-center p-4"><span class="text-muted" style="font-size:12px">No users found</span></td></tr>';
                                return;
                            }
                            tbody.innerHTML = users.map(u => `<tr>
                                <td>
                                    <div style="display:flex;align-items:center;gap:8px">
                                        <span style="width:30px;height:30px;border-radius:50%;background:#6366f1;color:#fff;
                                            display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:700;flex-shrink:0">
                                            ${(u.username||'?')[0].toUpperCase()}
                                        </span>
                                        <strong style="font-size:13px">${u.username}</strong>
                                    </div>
                                </td>
                                <td>${(u.roles||[]).map(r=>`<span class="badge bg-light text-dark me-1" style="font-size:10px">${r}</span>`).join('')||'<span class="text-muted small">—</span>'}</td>
                                <td>${u.active ? '<span class="badge bg-success">Active</span>' : '<span class="badge bg-secondary">Inactive</span>'}</td>
                                <td><small class="text-muted">${u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : 'Never'}</small></td>
                                <td><small class="text-muted">${u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '—'}</small></td>
                                <td>
                                    <button class="btn btn-xs btn-sm btn-outline-secondary py-0 px-2 me-1"
                                        onclick="openChangePassword('${u.username}')" title="Change password">
                                        <i class="fas fa-key"></i>
                                    </button>
                                    <button class="btn btn-xs btn-sm btn-outline-danger py-0 px-2"
                                        onclick="deleteUser('${u.username}')" title="Delete user">
                                        <i class="fas fa-trash"></i>
                                    </button>
                                </td>
                            </tr>`).join('');
                        }).catch(() => {
                            tbody.innerHTML = '<tr><td colspan="6" class="text-center p-4"><span class="text-danger" style="font-size:12px"><i class="fas fa-exclamation-circle me-1"></i>Failed to load users — is the admin service running?</span></td></tr>';
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
                            document.getElementById('setting-session-timeout').value = s.sessionTimeoutMin || 30;
                            document.getElementById('setting-alert-email').value     = s.defaultAlertEmail || '';
                            document.getElementById('setting-maintenance').checked   = s.maintenanceMode || false;
                        }).catch(() => {});
                }

                function updateSettings(e) {
                    e.preventDefault();
                    const settings = {
                        siteName:          document.getElementById('setting-site-name').value,
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

                function changeAdminPassword(e) {
                    e.preventDefault();
                    const errEl    = document.getElementById('change-pw-error');
                    const statusEl = document.getElementById('change-pw-status');
                    errEl.style.display    = 'none';
                    statusEl.style.display = 'none';
                    const newPw  = document.getElementById('gen-new-password').value;
                    const confPw = document.getElementById('gen-confirm-password').value;
                    if (newPw !== confPw) {
                        errEl.textContent    = 'Passwords do not match';
                        errEl.style.display  = '';
                        return;
                    }
                    fetch('/api/auth/profile')
                        .then(r => r.json())
                        .then(profile => {
                            const username = profile.username;
                            return fetch('/api/users/' + username + '/password', {
                                method: 'PUT',
                                headers: {'Content-Type': 'application/json'},
                                body: JSON.stringify({newPassword: newPw})
                            });
                        })
                        .then(r => {
                            if (r.ok) {
                                document.getElementById('gen-new-password').value     = '';
                                document.getElementById('gen-confirm-password').value = '';
                                statusEl.style.display = '';
                                setTimeout(() => statusEl.style.display = 'none', 3000);
                            } else {
                                errEl.textContent   = 'Failed to update password';
                                errEl.style.display = '';
                            }
                        })
                        .catch(() => {
                            errEl.textContent   = 'Error contacting server';
                            errEl.style.display = '';
                        });
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
                    setInterval(loadOverview, 30000);
                    setInterval(() => updateAlertBadge(
                        parseInt(document.getElementById('active-alert-count').textContent) || 0), 30000);
                });
                """;
    }

    // ---- SECTION: analytics -------------------------------------------------

    private String buildSectionAnalytics() {
        return """
                <div class="section" id="section-analytics">
                    <div class="page-header">
                        <h1 class="page-title-h">Analytics</h1>
                        <p class="page-sub">Real-time metrics collected from Actuator endpoints every 15 s</p>
                    </div>

                    <!-- Overview KPIs -->
                    <div class="metrics-grid" id="analytics-kpis">
                        <div class="metric-card"><div class="val" id="kpi-rps">-</div><div class="lbl">Total RPS</div></div>
                        <div class="metric-card"><div class="val" id="kpi-cpu">-</div><div class="lbl">Avg CPU %</div></div>
                        <div class="metric-card"><div class="val" id="kpi-err">-</div><div class="lbl">Avg Error %</div></div>
                        <div class="metric-card"><div class="val" id="kpi-p99">-</div><div class="lbl">Avg P99 ms</div></div>
                        <div class="metric-card"><div class="val" id="kpi-svcs">-</div><div class="lbl">Tracked</div></div>
                    </div>

                    <!-- Live per-service cards -->
                    <div class="card-box mb-3">
                        <div class="section-hdr">
                            <span class="section-title">Live Metrics</span>
                            <span class="text-muted" style="font-size:11px" id="analytics-live-ts"></span>
                        </div>
                        <div id="analytics-live-rows"><p class="text-muted p-3">Waiting for first data point…</p></div>
                    </div>

                    <!-- Charts -->
                    <div class="two-col mb-3">
                        <div class="chart-box">
                            <div class="section-hdr"><span class="section-title">CPU % — select service</span>
                                <select id="chart-svc-select" class="form-select form-select-sm w-auto" onchange="loadHistoryChart()"></select>
                            </div>
                            <canvas id="chart-cpu"></canvas>
                        </div>
                        <div class="chart-box">
                            <div class="section-hdr"><span class="section-title">Heap % — same service</span></div>
                            <canvas id="chart-heap"></canvas>
                        </div>
                    </div>
                    <div class="two-col mb-3">
                        <div class="chart-box">
                            <div class="section-hdr"><span class="section-title">Request Rate (RPS)</span></div>
                            <canvas id="chart-rps"></canvas>
                        </div>
                        <div class="chart-box">
                            <div class="section-hdr"><span class="section-title">P99 Response Time (ms)</span></div>
                            <canvas id="chart-p99"></canvas>
                        </div>
                    </div>

                    <!-- Trends -->
                    <div class="card-box">
                        <div class="section-hdr"><span class="section-title">All-service RPS Trends</span></div>
                        <canvas id="chart-trends" style="max-height:240px"></canvas>
                    </div>
                </div>
                """;
    }

    // ---- SECTION: API Explorer ----------------------------------------------

    private String buildSectionApiExplorer() {
        return buildSectionApiExplorerA() + buildSectionApiExplorerB();
    }

    private String buildSectionApiExplorerA() {
        return """
                <div class="section" id="section-explorer">
                    <div class="page-header">
                        <h1 class="page-title-h">API Explorer</h1>
                        <p class="page-sub">Browse REST endpoints and make live API calls directly from the browser</p>
                    </div>
                    <div style="display:grid;grid-template-columns:260px 1fr;gap:16px;height:calc(100vh - 180px);min-height:600px">
                        <!-- Left panel: service + endpoint picker -->
                        <div style="display:flex;flex-direction:column;gap:10px;overflow:hidden">
                            <div style="background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:12px">
                                <label style="font-size:11px;font-weight:600;color:#6b7280;text-transform:uppercase;letter-spacing:.06em;display:block;margin-bottom:6px">Service</label>
                                <select id="explorer-svc-select" class="form-select form-select-sm"
                                        onchange="loadServiceMappings()" style="font-size:13px">
                                    <option value="">— select service —</option>
                                </select>
                                <div style="margin-top:8px;position:relative">
                                    <i class="fas fa-search" style="position:absolute;left:9px;top:50%;transform:translateY(-50%);color:#9ca3af;font-size:11px"></i>
                                    <input type="text" id="explorer-filter" class="form-control form-control-sm"
                                           style="padding-left:26px;font-size:12px" placeholder="Filter endpoints…"
                                           oninput="filterEndpoints()">
                                </div>
                            </div>
                            <div style="flex:1;overflow-y:auto;background:#fff;border:1px solid #e5e7eb;border-radius:10px" id="ep-list">
                                <div style="padding:32px 16px;text-align:center;color:#9ca3af">
                                    <i class="fas fa-layer-group" style="font-size:28px;margin-bottom:10px;display:block;color:#d1d5db"></i>
                                    <span style="font-size:12px">Select a service to browse endpoints</span>
                                </div>
                            </div>
                        </div>
                        <!-- Right panel: request + response -->
                        <div style="display:flex;flex-direction:column;gap:10px;overflow:hidden">
                            <!-- URL bar -->
                            <div style="background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:12px">
                                <div style="display:flex;gap:8px;align-items:center">
                                    <select id="req-method" style="width:96px;font-size:12px;font-weight:700;
                                        border:1px solid #e5e7eb;border-radius:6px;padding:6px 8px;background:#f9fafb;cursor:pointer"
                                        onchange="updateMethodColor()">
                                        <option style="color:#16a34a">GET</option>
                                        <option style="color:#2563eb">POST</option>
                                        <option style="color:#d97706">PUT</option>
                                        <option style="color:#dc2626">DELETE</option>
                                        <option style="color:#7c3aed">PATCH</option>
                                    </select>
                                    <input type="text" id="req-url" class="form-control form-control-sm" style="flex:1;font-family:monospace;font-size:12px"
                                           placeholder="http://localhost:8081/api/…">
                                    <button onclick="executeRequest()" id="btn-send"
                                        style="padding:6px 18px;background:#6366f1;color:#fff;border:none;border-radius:6px;
                                               font-size:13px;font-weight:600;cursor:pointer;white-space:nowrap;display:flex;align-items:center;gap:6px">
                                        <i class="fas fa-paper-plane" style="font-size:11px"></i> Send
                                    </button>
                                </div>
                            </div>
                            <!-- Request tabs -->
                            <div style="background:#fff;border:1px solid #e5e7eb;border-radius:10px;display:flex;flex-direction:column">
                                <div style="display:flex;border-bottom:1px solid #f1f5f9;padding:0 12px">
                                    <button onclick="showReqTab('headers')" id="rtab-headers"
                                        style="padding:10px 14px;border:none;background:none;font-size:12px;font-weight:600;color:#6366f1;border-bottom:2px solid #6366f1;cursor:pointer">Headers</button>
                                    <button onclick="showReqTab('body')" id="rtab-body"
                                        style="padding:10px 14px;border:none;background:none;font-size:12px;color:#9ca3af;border-bottom:2px solid transparent;cursor:pointer">Body</button>
                                </div>
                                <div id="rtab-panel-headers" style="padding:12px">
                                    <textarea id="req-headers" style="width:100%;height:80px;font-family:monospace;font-size:12px;
                                        border:1px solid #e5e7eb;border-radius:6px;padding:8px;resize:vertical;color:#374151"
                                        placeholder='{"Authorization": "Bearer token", "Content-Type": "application/json"}'></textarea>
                                </div>
                """;
    }

    private String buildSectionApiExplorerB() {
        return """
                                <div id="rtab-panel-body" style="padding:12px;display:none">
                                    <textarea id="req-body" style="width:100%;height:80px;font-family:monospace;font-size:12px;
                                        border:1px solid #e5e7eb;border-radius:6px;padding:8px;resize:vertical;color:#374151"
                                        placeholder='{"key": "value"}'></textarea>
                                </div>
                            </div>
                            <!-- Response -->
                            <div style="flex:1;background:#fff;border:1px solid #e5e7eb;border-radius:10px;display:flex;flex-direction:column;overflow:hidden">
                                <div style="padding:10px 14px;border-bottom:1px solid #f1f5f9;display:flex;align-items:center;gap:12px;flex-shrink:0">
                                    <span style="font-size:13px;font-weight:600;color:#111827">Response</span>
                                    <span id="resp-status-badge"></span>
                                    <span id="resp-duration" style="font-size:11px;color:#9ca3af"></span>
                                    <div style="margin-left:auto;display:flex;gap:6px">
                                        <button onclick="showRespTab('body')" id="restab-body"
                                            style="padding:3px 10px;border-radius:5px;border:1px solid #6366f1;background:#6366f1;color:#fff;font-size:11px;cursor:pointer">Body</button>
                                        <button onclick="showRespTab('headers')" id="restab-headers"
                                            style="padding:3px 10px;border-radius:5px;border:1px solid #e5e7eb;background:#fff;color:#6b7280;font-size:11px;cursor:pointer">Headers</button>
                                    </div>
                                </div>
                                <div id="restab-panel-body" style="flex:1;overflow:auto;padding:12px">
                                    <pre id="resp-body" style="font-family:monospace;font-size:12px;margin:0;white-space:pre-wrap;word-break:break-word;color:#374151">No response yet — configure a request and click Send.</pre>
                                </div>
                                <div id="restab-panel-headers" style="flex:1;overflow:auto;padding:12px;display:none">
                                    <pre id="resp-headers" style="font-family:monospace;font-size:11px;margin:0;white-space:pre-wrap;color:#374151"></pre>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                """;
    }

    // ---- SCRIPTS: analytics -------------------------------------------------

    private String buildScriptsAnalytics() {
        return """
                // ── Analytics ──────────────────────────────────────────────────────────────
                let analyticsCharts = {};
                let analyticsSSE = null;
                const COLORS = ['#6366f1','#22c55e','#f59e0b','#ef4444','#06b6d4','#8b5cf6','#ec4899'];

                function loadAnalyticsSection() {
                    fetch('/api/analytics/overview')
                        .then(r => r.json()).then(d => {
                            document.getElementById('kpi-rps').textContent  = d.totalRps;
                            document.getElementById('kpi-cpu').textContent  = d.avgCpuPct + '%';
                            document.getElementById('kpi-err').textContent  = d.avgErrorRatePct + '%';
                            document.getElementById('kpi-p99').textContent  = d.avgP99Ms;
                            document.getElementById('kpi-svcs').textContent = d.trackedServices;
                        }).catch(() => {});
                    fetch('/api/analytics/realtime')
                        .then(r => r.json()).then(renderLiveRows).catch(() => {});
                    loadTrendsChart();
                    populateSvcSelect();
                    if (!analyticsSSE) startAnalyticsSSE();
                }

                function populateSvcSelect() {
                    fetch('/api/analytics/realtime')
                        .then(r => r.json()).then(data => {
                            const sel = document.getElementById('chart-svc-select');
                            const cur = sel.value;
                            sel.innerHTML = Object.keys(data).sort().map(s =>
                                `<option value="${s}" ${s===cur?'selected':''}>${s}</option>`).join('');
                            if (!cur && sel.options.length) loadHistoryChart();
                        }).catch(() => {});
                }

                function renderLiveRows(data) {
                    const wrap = document.getElementById('analytics-live-rows');
                    if (!Object.keys(data).length) { wrap.innerHTML='<p class="text-muted p-3">No data yet.</p>'; return; }
                    wrap.innerHTML = Object.entries(data).map(([svc, m]) => `
                        <div class="svc-metric-row">
                            <span class="svc-name">${svc}</span>
                            <span class="svc-chip" title="CPU">${m.cpu}% CPU</span>
                            <span class="svc-chip" title="Heap">${m.heapUsed}/${m.heapMax} MB heap (${m.heapPct}%)</span>
                            <span class="svc-chip" title="RPS">${m.rps} rps</span>
                            <span class="svc-chip" title="P99">${m.p99Ms} ms p99</span>
                            <span class="svc-chip" title="Threads">${m.threads} threads</span>
                            <span class="svc-chip" title="Error rate" style="${m.errorRate>0?'color:#ef4444':''}">${m.errorRate}% err</span>
                            <span class="ms-auto text-muted" style="font-size:11px">${m.ts}</span>
                        </div>`).join('');
                    document.getElementById('analytics-live-ts').textContent = 'Updated ' + new Date().toLocaleTimeString();
                }

                function loadHistoryChart() {
                    const svc = document.getElementById('chart-svc-select').value;
                    if (!svc) return;
                    fetch('/api/analytics/history/' + encodeURIComponent(svc))
                        .then(r => r.json()).then(d => {
                            drawOrUpdate('chart-cpu',  d.labels, d.cpu,       'CPU %',      '#6366f1');
                            drawOrUpdate('chart-heap', d.labels, d.heapPct,   'Heap %',     '#22c55e');
                            drawOrUpdate('chart-rps',  d.labels, d.rps,       'RPS',        '#f59e0b');
                            drawOrUpdate('chart-p99',  d.labels, d.p99Ms,     'P99 ms',     '#ef4444');
                        }).catch(() => {});
                }

                function loadTrendsChart() {
                    fetch('/api/analytics/trends')
                        .then(r => r.json()).then(d => {
                            if (!d.datasets || !d.datasets.length) return;
                            const ctx = document.getElementById('chart-trends');
                            if (analyticsCharts['trends']) analyticsCharts['trends'].destroy();
                            analyticsCharts['trends'] = new Chart(ctx, {
                                type: 'line',
                                data: {
                                    labels: d.labels,
                                    datasets: d.datasets.map((ds, i) => ({
                                        label: ds.service + ' RPS',
                                        data: ds.rps,
                                        borderColor: COLORS[i % COLORS.length],
                                        backgroundColor: COLORS[i % COLORS.length] + '22',
                                        tension: 0.3, fill: false, pointRadius: 2
                                    }))
                                },
                                options: chartOpts('RPS')
                            });
                        }).catch(() => {});
                }
                """;
    }

    private String buildScriptsAnalyticsB() {
        return """
                function drawOrUpdate(canvasId, labels, data, label, color) {
                    const ctx = document.getElementById(canvasId);
                    if (analyticsCharts[canvasId]) analyticsCharts[canvasId].destroy();
                    analyticsCharts[canvasId] = new Chart(ctx, {
                        type: 'line',
                        data: {
                            labels: labels,
                            datasets: [{ label, data, borderColor: color,
                                backgroundColor: color + '22', tension: 0.3,
                                fill: true, pointRadius: 2 }]
                        },
                        options: chartOpts(label)
                    });
                }

                function chartOpts(yLabel) {
                    return {
                        responsive: true, maintainAspectRatio: true,
                        plugins: { legend: { display: false } },
                        scales: {
                            x: { ticks: { maxTicksLimit: 8, font: { size: 10 } }, grid: { color: '#f3f4f6' } },
                            y: { beginAtZero: true, title: { display: true, text: yLabel, font: { size: 10 } },
                                 ticks: { font: { size: 10 } }, grid: { color: '#f3f4f6' } }
                        }
                    };
                }

                function startAnalyticsSSE() {
                    analyticsSSE = new EventSource('/api/analytics/stream');
                    analyticsSSE.addEventListener('metrics', e => {
                        try { renderLiveRows(JSON.parse(e.data)); } catch (_) {}
                    });
                    analyticsSSE.onerror = () => {
                        analyticsSSE.close(); analyticsSSE = null;
                        setTimeout(startAnalyticsSSE, 5000);
                    };
                }
                """;
    }

    // ---- SCRIPTS: API Explorer ----------------------------------------------

    private String buildScriptsApiExplorer() {
        return """
                // ── API Explorer ───────────────────────────────────────────────────────────
                let explorerEndpoints = [];

                function loadExplorerServices() {
                    fetch('/api/explorer/services')
                        .then(r => r.json()).then(svcs => {
                            const sel = document.getElementById('explorer-svc-select');
                            sel.innerHTML = '<option value="">— select service —</option>' +
                                svcs.map(s => `<option value="${s.name}" data-url="${s.baseUrl}">${s.name} :${s.port}</option>`).join('');
                        }).catch(() => {});
                }

                function loadServiceMappings() {
                    const sel = document.getElementById('explorer-svc-select');
                    const svc = sel.value;
                    if (!svc) return;
                    const opt = sel.options[sel.selectedIndex];
                    document.getElementById('req-url').value = opt.dataset.url || '';
                    document.getElementById('ep-list').innerHTML = '<p class="text-muted p-2" style="font-size:12px">Loading…</p>';
                    fetch('/api/explorer/' + encodeURIComponent(svc) + '/mappings')
                        .then(r => r.json()).then(d => {
                            explorerEndpoints = d.endpoints || [];
                            renderEndpointList(explorerEndpoints, opt.dataset.url || '');
                        }).catch(e => {
                            document.getElementById('ep-list').innerHTML =
                                `<p class="text-danger p-2" style="font-size:12px">Error: ${e}</p>`;
                        });
                }

                function showReqTab(tab) {
                    ['headers','body'].forEach(t => {
                        document.getElementById('rtab-panel-'+t).style.display = t===tab ? '' : 'none';
                        const btn = document.getElementById('rtab-'+t);
                        btn.style.color      = t===tab ? '#6366f1' : '#9ca3af';
                        btn.style.borderBottomColor = t===tab ? '#6366f1' : 'transparent';
                    });
                }
                function showRespTab(tab) {
                    ['body','headers'].forEach(t => {
                        document.getElementById('restab-panel-'+t).style.display = t===tab ? '' : 'none';
                        const btn = document.getElementById('restab-'+t);
                        btn.style.background = t===tab ? '#6366f1' : '#fff';
                        btn.style.color      = t===tab ? '#fff' : '#6b7280';
                        btn.style.borderColor = t===tab ? '#6366f1' : '#e5e7eb';
                    });
                }
                function updateMethodColor() {
                    const m = document.getElementById('req-method').value;
                    const colors = {GET:'#16a34a',POST:'#2563eb',PUT:'#d97706',DELETE:'#dc2626',PATCH:'#7c3aed'};
                    document.getElementById('req-method').style.color = colors[m]||'#374151';
                }

                function renderEndpointList(eps, baseUrl) {
                    const el = document.getElementById('ep-list');
                    if (!eps.length) {
                        el.innerHTML = `<div style="padding:32px 16px;text-align:center;color:#9ca3af">
                            <i class="fas fa-inbox" style="font-size:24px;margin-bottom:8px;display:block;color:#d1d5db"></i>
                            <span style="font-size:12px">No endpoints found<br><span style="font-size:11px">Check if Spring Actuator mappings are enabled</span></span>
                        </div>`;
                        return;
                    }
                    const methodColor = {GET:'#16a34a',POST:'#2563eb',PUT:'#d97706',DELETE:'#dc2626',PATCH:'#7c3aed'};
                    el.innerHTML = eps.map((ep, i) => {
                        const methods = Array.isArray(ep.methods) ? ep.methods : ['GET'];
                        const method = methods[0] || 'GET';
                        const color = methodColor[method] || '#6b7280';
                        const pathShort = ep.path.length > 32 ? '…'+ep.path.slice(-30) : ep.path;
                        return `<div id="ep-${i}" onclick="selectEndpoint(${i}, '${baseUrl}')"
                            style="display:flex;align-items:center;gap:8px;padding:9px 12px;border-bottom:1px solid #f1f5f9;cursor:pointer;transition:background .12s"
                            onmouseover="this.style.background='#f8fafc'" onmouseout="this.style.background=this.classList.contains('ep-active')?'#eef2ff':''">
                            <span style="font-size:9px;font-weight:700;color:${color};background:${color}14;padding:2px 5px;border-radius:4px;min-width:38px;text-align:center">${method}</span>
                            <span style="font-size:11px;font-family:monospace;color:#374151;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="${ep.path}">${pathShort}</span>
                        </div>`;
                    }).join('');
                }

                function selectEndpoint(idx, baseUrl) {
                    document.querySelectorAll('#ep-list > div').forEach(e => {
                        e.classList.remove('ep-active'); e.style.background = '';
                    });
                    const selected = document.getElementById('ep-' + idx);
                    if (selected) { selected.classList.add('ep-active'); selected.style.background = '#eef2ff'; }
                    const ep = explorerEndpoints[idx];
                    const methods = Array.isArray(ep.methods) ? ep.methods : ['GET'];
                    document.getElementById('req-method').value = methods[0] || 'GET';
                    document.getElementById('req-url').value = baseUrl + ep.path;
                    updateMethodColor();
                }

                function filterEndpoints() {
                    const q = document.getElementById('explorer-filter').value.toLowerCase();
                    const filtered = explorerEndpoints.filter(ep =>
                        ep.path.toLowerCase().includes(q) ||
                        (ep.methods || []).some(m => m.toLowerCase().includes(q)));
                    const baseUrl = (() => {
                        const sel = document.getElementById('explorer-svc-select');
                        return (sel.options[sel.selectedIndex] || {}).dataset?.url || '';
                    })();
                    renderEndpointList(filtered, baseUrl);
                }

                function executeRequest() {
                    const method  = document.getElementById('req-method').value;
                    const url     = document.getElementById('req-url').value.trim();
                    const bodyRaw = document.getElementById('req-body').value.trim();
                    const hdrsRaw = document.getElementById('req-headers').value.trim();
                    if (!url) { alert('Enter a URL'); return; }
                    let headers = {};
                    if (hdrsRaw) { try { headers = JSON.parse(hdrsRaw); } catch { alert('Invalid headers JSON'); return; } }
                    const btn = document.getElementById('btn-send');
                    btn.disabled = true;
                    btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Sending…';
                    fetch('/api/explorer/request', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ method, url, headers, body: bodyRaw || null })
                    }).then(r => r.json()).then(d => {
                        btn.disabled = false; btn.innerHTML = '<i class="fas fa-paper-plane" style="font-size:11px"></i> Send';
                        const ok = !d.error && d.status < 400;
                        const stColor = ok ? '#16a34a' : '#dc2626';
                        const stBg    = ok ? '#f0fdf4' : '#fef2f2';
                        document.getElementById('resp-status-badge').innerHTML =
                            `<span style="font-size:12px;font-weight:700;color:${stColor};background:${stBg};padding:2px 8px;border-radius:5px">${d.status} ${d.statusText||''}</span>`;
                        document.getElementById('resp-duration').textContent = d.durationMs != null ? d.durationMs + ' ms' : '';
                        const hdrs = d.headers || {};
                        document.getElementById('resp-headers').textContent =
                            Object.entries(hdrs).map(([k,v]) => `${k}: ${v}`).join('\\n') || '(none)';
                        let body = d.body || '';
                        try { body = JSON.stringify(JSON.parse(body), null, 2); } catch {}
                        document.getElementById('resp-body').textContent = body || '(empty)';
                        showRespTab('body');
                    }).catch(e => {
                        btn.disabled = false; btn.innerHTML = '<i class="fas fa-paper-plane" style="font-size:11px"></i> Send';
                        document.getElementById('resp-body').textContent = 'Request failed: ' + e;
                    });
                }
                """;
    }

    // ---- SECTION: Network Map -----------------------------------------------

    private String buildSectionNetworkMap() {
        return """
                <div class="section" id="section-networkmap">
                    <div class="page-header">
                        <h1 class="page-title-h">Network Map</h1>
                        <p class="page-sub">Force-directed service topology graph — health-coloured nodes, dependency edges</p>
                    </div>
                    <div style="position:relative;background:#f8fafc;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;margin-bottom:14px">
                        <canvas id="netmap-canvas" width="1200" height="540"
                            style="width:100%;height:540px;cursor:grab;display:block"></canvas>
                        <div id="netmap-tooltip"
                            style="display:none;position:absolute;background:rgba(15,23,42,.9);color:#f1f5f9;
                                   padding:7px 12px;border-radius:7px;font-size:12px;pointer-events:none;z-index:20;
                                   white-space:pre-line;line-height:1.55"></div>
                        <div id="netmap-loading"
                            style="position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);
                                   color:#94a3b8;font-size:13px">
                            <i class="fas fa-spinner fa-spin"></i> Loading…
                        </div>
                        <div style="position:absolute;top:10px;right:10px;display:flex;gap:6px">
                            <button onclick="zoomNetmap(1.25)"
                                style="background:#fff;border:1px solid #d1d5db;border-radius:5px;
                                       padding:4px 9px;font-size:13px;cursor:pointer">+</button>
                            <button onclick="zoomNetmap(0.8)"
                                style="background:#fff;border:1px solid #d1d5db;border-radius:5px;
                                       padding:4px 9px;font-size:13px;cursor:pointer">−</button>
                            <button onclick="resetNetmapView()"
                                style="background:#fff;border:1px solid #d1d5db;border-radius:5px;
                                       padding:4px 9px;font-size:11px;cursor:pointer">Reset</button>
                            <button onclick="loadNetworkMap()"
                                style="background:#fff;border:1px solid #d1d5db;border-radius:5px;
                                       padding:4px 9px;font-size:11px;cursor:pointer">
                                <i class="fas fa-sync" style="font-size:10px"></i>
                            </button>
                        </div>
                    </div>
                    <div style="display:flex;gap:20px;align-items:center;flex-wrap:wrap;margin-bottom:12px;font-size:12px;color:#374151">
                        <span style="display:flex;align-items:center;gap:5px">
                            <span style="width:11px;height:11px;border-radius:50%;background:#22c55e;border:2px solid #22c55e;display:inline-block"></span>
                            <b style="color:#15803d">Running</b>
                        </span>
                        <span style="display:flex;align-items:center;gap:5px">
                            <span style="width:11px;height:11px;border-radius:50%;background:#ef4444;border:2px solid #ef4444;display:inline-block"></span>
                            <b style="color:#dc2626">Stopped</b>
                        </span>
                        <span style="margin-left:auto;font-size:11px;color:#9ca3af">Drag nodes · Scroll to zoom · Click for details</span>
                    </div>
                    <div id="netmap-info"
                        style="display:none;padding:14px;background:#fff;border:1px solid #e5e7eb;border-radius:10px">
                        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
                            <strong id="netmap-info-name" style="font-size:14px"></strong>
                            <button onclick="document.getElementById('netmap-info').style.display='none'"
                                style="background:none;border:none;font-size:18px;color:#9ca3af;cursor:pointer">&times;</button>
                        </div>
                        <div id="netmap-info-body"
                            style="font-size:13px;color:#374151;display:grid;
                                   grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:10px">
                        </div>
                    </div>
                </div>
                """;
    }

    // ---- SECTION: gRPC Browser ----------------------------------------------

    private String buildSectionGrpcBrowser() {
        return """
                <div class="section" id="section-grpc">
                    <div class="page-header" style="display:flex;justify-content:space-between;align-items:flex-start">
                        <div>
                            <h1 class="page-title-h">gRPC / NetScope Browser</h1>
                            <p class="page-sub">Inspect inter-service NetScope/gRPC connections, dependency graph, and connectivity</p>
                        </div>
                        <button onclick="loadGrpcBrowser()" style="padding:6px 14px;border:1px solid #e5e7eb;border-radius:7px;background:#fff;font-size:12px;cursor:pointer;display:flex;align-items:center;gap:6px">
                            <i class="fas fa-sync" style="font-size:11px"></i> Refresh
                        </button>
                    </div>

                    <!-- Top: service cards -->
                    <div style="background:#fff;border:1px solid #e5e7eb;border-radius:12px;margin-bottom:16px">
                        <div style="padding:12px 16px;border-bottom:1px solid #f1f5f9;display:flex;align-items:center;justify-content:space-between">
                            <span style="font-size:13px;font-weight:600;color:#111827"><i class="fas fa-project-diagram" style="color:#6366f1;margin-right:8px"></i>gRPC Services</span>
                            <span id="grpc-svc-count" style="font-size:11px;color:#9ca3af"></span>
                        </div>
                        <div id="grpc-svc-list" style="display:flex;flex-wrap:wrap;gap:12px;padding:16px;min-height:80px">
                            <div style="color:#9ca3af;font-size:12px;padding:16px">Loading services…</div>
                        </div>
                    </div>

                    <!-- Middle: dependency detail + TCP Ping -->
                    <div style="display:grid;grid-template-columns:1fr 320px;gap:16px;margin-bottom:16px">
                        <div style="background:#fff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden">
                            <div style="padding:12px 16px;border-bottom:1px solid #f1f5f9;display:flex;align-items:center;gap:8px">
                                <i class="fas fa-sitemap" style="color:#6366f1;font-size:12px"></i>
                                <span id="grpc-detail-title" style="font-size:13px;font-weight:600;color:#111827">Select a service card above</span>
                            </div>
                            <div id="grpc-detail-body" style="padding:16px;min-height:100px">
                                <div style="text-align:center;padding:24px;color:#9ca3af">
                                    <i class="fas fa-mouse-pointer" style="font-size:24px;margin-bottom:8px;display:block;color:#d1d5db"></i>
                                    <span style="font-size:12px">Click a service card to view its upstream and downstream gRPC dependencies</span>
                                </div>
                            </div>
                        </div>
                        <!-- TCP Ping tool -->
                        <div style="background:#fff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden">
                            <div style="padding:12px 16px;border-bottom:1px solid #f1f5f9">
                                <span style="font-size:13px;font-weight:600;color:#111827"><i class="fas fa-satellite-dish" style="color:#10b981;margin-right:8px"></i>TCP Connectivity</span>
                            </div>
                            <div style="padding:16px;display:flex;flex-direction:column;gap:10px">
                                <div>
                                    <label style="font-size:11px;font-weight:600;color:#6b7280;display:block;margin-bottom:4px">HOST</label>
                                    <input id="ping-host" value="localhost" class="form-control form-control-sm" style="font-family:monospace">
                                </div>
                                <div>
                                    <label style="font-size:11px;font-weight:600;color:#6b7280;display:block;margin-bottom:4px">gRPC PORT</label>
                                    <input id="ping-port" type="number" placeholder="e.g. 18081" class="form-control form-control-sm" style="font-family:monospace">
                                </div>
                                <button onclick="doGrpcPing()"
                                    style="padding:8px;border:none;border-radius:7px;background:#6366f1;color:#fff;font-size:13px;font-weight:600;cursor:pointer">
                                    <i class="fas fa-paper-plane" style="margin-right:4px"></i> Ping Port
                                </button>
                                <div id="ping-result" style="min-height:32px;font-size:13px;text-align:center;padding:6px;border-radius:6px"></div>
                            </div>
                        </div>
                    </div>

                    <!-- Bottom: full connections table -->
                    <div style="background:#fff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden">
                        <div style="padding:12px 16px;border-bottom:1px solid #f1f5f9">
                            <span style="font-size:13px;font-weight:600;color:#111827"><i class="fas fa-network-wired" style="color:#6366f1;margin-right:8px"></i>All NetScope Connections</span>
                        </div>
                        <div style="overflow-x:auto">
                            <table style="width:100%;border-collapse:collapse;font-size:12px">
                                <thead>
                                    <tr style="background:#f8fafc;border-bottom:1px solid #e5e7eb">
                                        <th style="padding:10px 14px;text-align:left;font-weight:600;color:#6b7280;font-size:11px;text-transform:uppercase;letter-spacing:.06em">Caller Service</th>
                                        <th style="padding:10px 14px;text-align:left;font-weight:600;color:#6b7280;font-size:11px;text-transform:uppercase;letter-spacing:.06em">HTTP</th>
                                        <th style="padding:10px 14px;text-align:left;font-weight:600;color:#6b7280;font-size:11px;text-transform:uppercase;letter-spacing:.06em">Depends On</th>
                                        <th style="padding:10px 14px;text-align:left;font-weight:600;color:#6b7280;font-size:11px;text-transform:uppercase;letter-spacing:.06em">gRPC Port</th>
                                        <th style="padding:10px 14px;text-align:left;font-weight:600;color:#6b7280;font-size:11px;text-transform:uppercase;letter-spacing:.06em">Protocol</th>
                                        <th style="padding:10px 14px;text-align:left;font-weight:600;color:#6b7280;font-size:11px;text-transform:uppercase;letter-spacing:.06em">Test</th>
                                    </tr>
                                </thead>
                                <tbody id="grpc-connections-tbody">
                                    <tr><td colspan="6" style="padding:24px;text-align:center;color:#9ca3af;font-size:12px">Loading…</td></tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
                """;
    }

    // ---- SECTION: Incidents -------------------------------------------------

    private String buildSectionIncidents() {
        return buildSectionIncidentsA() + buildSectionIncidentsB();
    }

    private String buildSectionIncidentsA() {
        return """
                <div class="section" id="section-circuitbreaker">
                    <div class="page-header">
                        <div>
                            <h1 class="page-title-h">Circuit Breakers</h1>
                            <p class="page-sub">Real-time Resilience4j circuit breaker state across all microservices</p>
                        </div>
                        <button class="btn btn-sm btn-primary" onclick="loadCircuitBreakers()">
                            <i class="fas fa-sync-alt"></i> Refresh
                        </button>
                    </div>
                    <div id="cb-summary-cards" class="row g-3 mb-3">
                        <div class="col-12 text-center text-muted p-4">Loading circuit breakers…</div>
                    </div>
                    <div class="card2">
                        <div class="card-hd">
                            <div class="card-hd-l">
                                <i class="fas fa-circle-notch" style="color:#6366f1"></i>
                                <span>Circuit Breaker States</span>
                            </div>
                        </div>
                        <div class="card-bd table-wrap">
                            <table class="table table-sm mb-0">
                                <thead><tr>
                                    <th>Service</th>
                                    <th>Circuit Breaker</th>
                                    <th>State</th>
                                    <th>Failure Rate</th>
                                    <th>Slow Call Rate</th>
                                    <th>Buffered</th>
                                    <th>Failed</th>
                                    <th>Not Permitted</th>
                                </tr></thead>
                                <tbody id="cb-tbody">
                                    <tr><td colspan="8" class="text-muted p-4 text-center">Loading…</td></tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>

                <div class="section" id="section-incidents">
                    <div class="page-header" style="display:flex;justify-content:space-between;align-items:flex-start">
                        <div>
                            <h1 class="page-title-h">Incidents</h1>
                            <p class="page-sub">Track and manage production incidents with severity and status workflow</p>
                        </div>
                        <button class="btn btn-sm btn-primary" data-bs-toggle="modal" data-bs-target="#inc-create-modal">
                            <i class="fas fa-plus"></i> New Incident
                        </button>
                    </div>
                    <!-- KPI row -->
                    <div class="metrics-grid" style="margin-bottom:18px">
                        <div class="metric-card"><div class="val" id="inc-total">-</div><div class="lbl">Total</div></div>
                        <div class="metric-card"><div class="val" id="inc-open" style="color:#ef4444">-</div><div class="lbl">Open</div></div>
                        <div class="metric-card"><div class="val" id="inc-inv" style="color:#f59e0b">-</div><div class="lbl">Investigating</div></div>
                        <div class="metric-card"><div class="val" id="inc-res" style="color:#22c55e">-</div><div class="lbl">Resolved</div></div>
                        <div class="metric-card"><div class="val" id="inc-p1" style="color:#dc2626">-</div><div class="lbl">P1 Critical</div></div>
                    </div>
                """;
    }

    private String buildSectionIncidentsB() {
        return """
                    <!-- Filter bar -->
                    <div style="background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:12px 16px;margin-bottom:14px;display:flex;gap:10px;flex-wrap:wrap;align-items:center">
                        <select id="inc-filter-status" onchange="filterIncidents()"
                            style="font-size:12px;border:1px solid #e5e7eb;border-radius:6px;padding:5px 10px;color:#374151;background:#f9fafb">
                            <option value="">All Statuses</option>
                            <option value="OPEN">Open</option>
                            <option value="INVESTIGATING">Investigating</option>
                            <option value="RESOLVED">Resolved</option>
                        </select>
                        <select id="inc-filter-sev" onchange="filterIncidents()"
                            style="font-size:12px;border:1px solid #e5e7eb;border-radius:6px;padding:5px 10px;color:#374151;background:#f9fafb">
                            <option value="">All Severities</option>
                            <option value="P1">P1 — Critical</option>
                            <option value="P2">P2 — High</option>
                            <option value="P3">P3 — Medium</option>
                            <option value="P4">P4 — Low</option>
                        </select>
                        <button onclick="loadIncidents()"
                            style="padding:5px 12px;border:1px solid #e5e7eb;border-radius:6px;background:#fff;font-size:12px;cursor:pointer;margin-left:auto">
                            <i class="fas fa-sync" style="font-size:11px;margin-right:4px"></i> Refresh
                        </button>
                    </div>
                    <!-- Incident cards -->
                    <div id="inc-cards" style="display:flex;flex-direction:column;gap:10px">
                        <div style="text-align:center;padding:32px;color:#9ca3af;font-size:12px">Loading incidents…</div>
                    </div>
                </div>

                <!-- Create Incident Modal -->
                <div class="modal fade" id="inc-create-modal" tabindex="-1">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content" style="border-radius:14px;border:none;box-shadow:0 20px 60px rgba(0,0,0,.15)">
                            <div class="modal-header" style="border-bottom:1px solid #f1f5f9;padding:18px 24px">
                                <h5 class="modal-title" style="font-size:16px;font-weight:700">
                                    <i class="fas fa-fire" style="color:#ef4444;margin-right:8px"></i>New Incident
                                </h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body" style="padding:20px 24px">
                                <div class="mb-3">
                                    <label style="font-size:12px;font-weight:600;color:#374151;display:block;margin-bottom:5px">TITLE <span style="color:#ef4444">*</span></label>
                                    <input id="inc-new-title" class="form-control" placeholder="Brief description of the incident" style="font-size:13px">
                                </div>
                                <div class="mb-3">
                                    <label style="font-size:12px;font-weight:600;color:#374151;display:block;margin-bottom:5px">AFFECTED SERVICE</label>
                                    <input id="inc-new-svc" class="form-control" placeholder="e.g. payment-service" style="font-size:13px;font-family:monospace">
                                </div>
                                <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:12px">
                                    <div>
                                        <label style="font-size:12px;font-weight:600;color:#374151;display:block;margin-bottom:5px">SEVERITY</label>
                                        <select id="inc-new-sev" class="form-select" style="font-size:13px">
                                            <option value="P1">P1 — Critical</option>
                                            <option value="P2">P2 — High</option>
                                            <option value="P3" selected>P3 — Medium</option>
                                            <option value="P4">P4 — Low</option>
                                        </select>
                                    </div>
                                    <div>
                                        <label style="font-size:12px;font-weight:600;color:#374151;display:block;margin-bottom:5px">ASSIGNEE</label>
                                        <input id="inc-new-assignee" class="form-control" placeholder="username" style="font-size:13px">
                                    </div>
                                </div>
                                <div>
                                    <label style="font-size:12px;font-weight:600;color:#374151;display:block;margin-bottom:5px">DESCRIPTION</label>
                                    <textarea id="inc-new-desc" class="form-control" rows="3" placeholder="What happened? What is the impact?" style="font-size:13px;resize:none"></textarea>
                                </div>
                            </div>
                            <div class="modal-footer" style="border-top:1px solid #f1f5f9;padding:14px 24px;gap:8px">
                                <button class="btn btn-sm btn-light" data-bs-dismiss="modal" style="font-size:13px;padding:6px 16px">Cancel</button>
                                <button onclick="createIncident()"
                                    style="padding:7px 20px;border:none;border-radius:7px;background:#ef4444;color:#fff;font-size:13px;font-weight:600;cursor:pointer">
                                    <i class="fas fa-fire" style="margin-right:6px"></i>Create Incident
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
                """;
    }

    // ---- SECTION: Config Editor ---------------------------------------------

    private String buildSectionConfigEditor() {
        return buildSectionConfigEditorA() + buildSectionConfigEditorB();
    }

    private String buildSectionConfigEditorA() {
        return """
                <div class="section" id="section-configeditor">
                    <div class="page-header" style="display:flex;justify-content:space-between;align-items:flex-start">
                        <div>
                            <h1 class="page-title-h">Config Editor</h1>
                            <p class="page-sub">View runtime configuration and apply in-memory environment overrides per service</p>
                        </div>
                        <button onclick="loadConfigEditor()" style="padding:6px 14px;border:1px solid #e5e7eb;border-radius:7px;background:#fff;font-size:12px;cursor:pointer">
                            <i class="fas fa-sync" style="font-size:11px;margin-right:4px"></i> Refresh
                        </button>
                    </div>
                    <div style="display:grid;grid-template-columns:240px 1fr;gap:16px;align-items:start">
                        <!-- Left: service picker -->
                        <div style="background:#fff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden">
                            <div style="padding:10px 14px;border-bottom:1px solid #f1f5f9">
                                <span style="font-size:12px;font-weight:600;color:#111827">Services</span>
                            </div>
                            <div id="cfg-svc-list" style="max-height:480px;overflow-y:auto"></div>
                        </div>
                        <!-- Right: detail tabs -->
                        <div style="background:#fff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden">
                            <!-- Service header -->
                            <div style="padding:12px 16px;border-bottom:1px solid #f1f5f9;display:flex;align-items:center;justify-content:space-between">
                                <div style="display:flex;align-items:center;gap:8px">
                                    <i class="fas fa-cog" style="color:#6366f1;font-size:13px"></i>
                                    <span id="cfg-svc-title" style="font-size:14px;font-weight:600;color:#111827">Select a service</span>
                                </div>
                                <div id="cfg-reload-wrap" style="display:none">
                                    <button onclick="hotReloadService()"
                                        style="padding:5px 12px;border:1px solid #f59e0b;border-radius:6px;background:#fffbeb;color:#92400e;font-size:12px;cursor:pointer">
                                        <i class="fas fa-bolt" style="margin-right:4px"></i> Hot Reload
                                    </button>
                                </div>
                            </div>
                            <!-- Tabs -->
                            <div style="display:flex;border-bottom:1px solid #f1f5f9;padding:0 16px">
                                <button onclick="showCfgTab('config')" id="cfgtab-config"
                                    style="padding:10px 14px;border:none;background:none;font-size:12px;font-weight:600;color:#6366f1;border-bottom:2px solid #6366f1;cursor:pointer">Configuration</button>
                                <button onclick="showCfgTab('override')" id="cfgtab-override"
                                    style="padding:10px 14px;border:none;background:none;font-size:12px;color:#9ca3af;border-bottom:2px solid transparent;cursor:pointer">Set Override</button>
                                <button onclick="showCfgTab('diff')" id="cfgtab-diff"
                                    style="padding:10px 14px;border:none;background:none;font-size:12px;color:#9ca3af;border-bottom:2px solid transparent;cursor:pointer">Active Overrides &amp; Diff</button>
                            </div>
                            <!-- Config tab -->
                            <div id="cfgtab-panel-config" style="padding:16px;min-height:200px">
                                <div style="color:#9ca3af;font-size:12px;text-align:center;padding:32px">
                                    <i class="fas fa-mouse-pointer" style="font-size:24px;margin-bottom:8px;display:block;color:#d1d5db"></i>
                                    Select a service on the left to view its runtime configuration
                                </div>
                            </div>
                """;
    }

    private String buildSectionConfigEditorB() {
        return """
                            <!-- Override tab -->
                            <div id="cfgtab-panel-override" style="padding:20px;display:none">
                                <p style="font-size:12px;color:#6b7280;margin-bottom:16px">
                                    Overrides are applied in-memory immediately without restarting the service. They reset on service restart.
                                </p>
                                <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:12px">
                                    <div>
                                        <label style="font-size:11px;font-weight:600;color:#6b7280;display:block;margin-bottom:4px;text-transform:uppercase;letter-spacing:.05em">Service</label>
                                        <input id="ov-svc" class="form-control form-control-sm" placeholder="payment-service" style="font-family:monospace">
                                    </div>
                                    <div>
                                        <label style="font-size:11px;font-weight:600;color:#6b7280;display:block;margin-bottom:4px;text-transform:uppercase;letter-spacing:.05em">Environment Key</label>
                                        <input id="ov-key" class="form-control form-control-sm" placeholder="SPRING_DATASOURCE_URL" style="font-family:monospace">
                                    </div>
                                </div>
                                <div style="margin-bottom:12px">
                                    <label style="font-size:11px;font-weight:600;color:#6b7280;display:block;margin-bottom:4px;text-transform:uppercase;letter-spacing:.05em">Value</label>
                                    <input id="ov-val" class="form-control form-control-sm" placeholder="jdbc:mysql://host:3306/db" style="font-family:monospace">
                                </div>
                                <button onclick="saveOverride()"
                                    style="padding:8px 20px;border:none;border-radius:7px;background:#6366f1;color:#fff;font-size:13px;font-weight:600;cursor:pointer">
                                    <i class="fas fa-save" style="margin-right:6px"></i> Apply Override
                                </button>
                                <div id="ov-save-msg" style="margin-top:10px;font-size:12px"></div>
                            </div>
                            <!-- Diff tab -->
                            <div id="cfgtab-panel-diff" style="padding:16px;display:none">
                                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
                                    <span style="font-size:13px;font-weight:600;color:#111827">Active Overrides</span>
                                    <div style="display:flex;gap:8px">
                                        <button onclick="loadOverrides()"
                                            style="font-size:11px;padding:4px 10px;border:1px solid #e5e7eb;border-radius:5px;background:#fff;cursor:pointer">Refresh</button>
                                        <button onclick="loadConfigDiff()"
                                            style="font-size:11px;padding:4px 10px;border:1px solid #6366f1;border-radius:5px;background:#eef2ff;color:#4338ca;cursor:pointer">
                                            <i class="fas fa-code-branch" style="margin-right:3px"></i> Show Diff
                                        </button>
                                    </div>
                                </div>
                                <div id="cfg-overrides-body" style="background:#f8fafc;border:1px solid #e5e7eb;border-radius:8px;padding:12px;font-size:12px;max-height:180px;overflow-y:auto">
                                    <span style="color:#9ca3af">No active overrides</span>
                                </div>
                                <div style="font-size:12px;font-weight:600;color:#111827;margin:14px 0 6px">Config Diff</div>
                                <pre id="cfg-diff-body" style="font-size:11px;font-family:monospace;background:#0f172a;color:#e2e8f0;
                                    border-radius:8px;padding:14px;max-height:200px;overflow:auto;white-space:pre-wrap;margin:0">
Click "Show Diff" above to compare active overrides against the base configuration.</pre>
                            </div>
                        </div>
                    </div>
                </div>
                """;
    }

    // ---- SCRIPTS: Network Map -----------------------------------------------

    private String buildScriptsNetworkMap() {
        return """
                // ── Network Map — module-level state ──────────────────────────────────────
                let nmNodes = [], nmEdges = [], nmHealth = {};
                let nmScale = 1, nmOffX = 0, nmOffY = 0;
                let nmDrag = null, nmMouse = {x:0,y:0};
                let nmAnimId = null;

                // ── Shared helpers — module scope so nmDraw, tooltip AND showNetmapInfo can use them ──

                // nmHealth stores one of: 'UP' | 'DEGRADED' | 'DOWN'
                // 'UP'      = process RUNNING + actuator UP          → green  (Running)
                // 'DEGRADED'= process RUNNING + actuator DEGRADED   → amber  (Warning)
                // 'DOWN'    = process UNREACHABLE (TCP refused)      → red    (Stopped)
                // Uses the exact same processStatus+status fields as the Services section.
                function nmResolveStatus(h) {
                    if (!h) return 'UP';
                    const proc = h.processStatus;
                    const st   = typeof h === 'object' ? (h.status || '') : String(h);
                    // Stopped ONLY when the process itself is unreachable (TCP refused)
                    if (proc === 'UNREACHABLE') return 'DOWN';
                    // Process is RUNNING — any bad actuator health = Warning (orange)
                    if (st === 'DOWN' || st === 'DEGRADED' || h.degraded) return 'DEGRADED';
                    // Process RUNNING + health UP or UNKNOWN = Running (green)
                    return 'UP';
                }

                const nmPalette = {
                    UP:       { ring:'#22c55e', fill:'rgba(34,197,94,0.15)',   dot:'#22c55e', label:'Running'  },
                    DEGRADED: { ring:'#f59e0b', fill:'rgba(245,158,11,0.15)', dot:'#f59e0b', label:'Warning'  },
                    DOWN:     { ring:'#ef4444', fill:'rgba(239,68,68,0.15)',   dot:'#ef4444', label:'Stopped'  }
                };

                // Small badge shown bottom-left of infra nodes so type is visible even on colour nodes
                const nmTypeLabel = { gateway:'GW', registry:'REG', admin:'ADM' };

                // ── Load ────────────────────────────────────────────────────────────────────

                function loadNetworkMap() {
                    const loading = document.getElementById('netmap-loading');
                    loading.style.display = '';
                    loading.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Loading…';

                    Promise.all([
                        fetch('/api/communication/topology').then(r => r.json()),
                        fetch('/api/services/all').then(r => r.json()).catch(() => []),
                        fetch('/api/health/summary').then(r => r.json()).catch(() => ({}))
                    ]).then(([topo, services, infraHealth]) => {
                        const canvas = document.getElementById('netmap-canvas');
                        requestAnimationFrame(() => {
                            const W = canvas.parentElement.clientWidth || 900;
                            const H = 520;
                            canvas.width  = W;
                            canvas.height = H;

                            // Build health map from /api/services/all (microservices)
                            nmHealth = {};
                            (services || []).forEach(s => {
                                const name = s.meta && s.meta.name;
                                if (name && s.health) nmHealth[name] = s.health;
                            });

                            // Supplement infra nodes from /api/health/summary
                            Object.entries(infraHealth || {}).forEach(([id, raw]) => {
                                if (!nmHealth[id]) {
                                    const proc = (raw === 'DOWN') ? 'UNREACHABLE' : 'RUNNING';
                                    const st   = (raw === 'RUNNING') ? 'UNKNOWN' : (raw || 'UNKNOWN');
                                    nmHealth[id] = { processStatus: proc, status: st };
                                }
                            });

                            // Build nodes from topology
                            const nodeMap = {};
                            const count = (topo.nodes || []).length;
                            nmNodes = (topo.nodes || []).map((n, i) => {
                                const angle = (2 * Math.PI * i) / Math.max(count, 1) - Math.PI / 2;
                                const r = Math.min(W, H) * 0.33;
                                const node = {
                                    id:       n.id,
                                    label:    n.label || n.id,
                                    port:     n.port,
                                    grpcPort: n.port > 0 ? n.port + 10000 : 0,
                                    type:     n.type || 'service',
                                    x: W / 2 + r * Math.cos(angle),
                                    y: H / 2 + r * Math.sin(angle),
                                    vx: 0, vy: 0, radius: 30
                                };
                                nodeMap[node.id] = node;
                                return node;
                            });

                            nmEdges = (topo.edges || []).map(e => ({
                                source:   nodeMap[e.source],
                                target:   nodeMap[e.target],
                                protocol: e.protocol || 'grpc'
                            })).filter(e => e.source && e.target);

                            loading.style.display = 'none';

                            if (!nmNodes.length) {
                                loading.style.display = '';
                                loading.innerHTML = '<i class="fas fa-info-circle" style="color:#9ca3af"></i> No topology data — run decompose first';
                                return;
                            }

                            if (nmAnimId) cancelAnimationFrame(nmAnimId);
                            nmSetupCanvas();
                            nmTickLoop();
                        });
                    }).catch(() => {
                        loading.style.display = '';
                        loading.innerHTML = '<i class="fas fa-exclamation-triangle" style="color:#ef4444"></i> Failed to load topology';
                    });
                }

                function nmTickLoop() {
                    const REPEL = 4000, SPRING_LEN = 140, SPRING_K = 0.035, DAMP = 0.80, GRAV = 0.02;
                    const canvas = document.getElementById('netmap-canvas');
                    const W = canvas.width || 900, H = canvas.height || 520;
                    for (let iter = 0; iter < 80; iter++) {
                        for (let i = 0; i < nmNodes.length; i++) {
                            const a = nmNodes[i];
                            for (let j = i + 1; j < nmNodes.length; j++) {
                                const b = nmNodes[j];
                                const dx = b.x - a.x, dy = b.y - a.y;
                                const dist = Math.sqrt(dx*dx + dy*dy) || 1;
                                const f = REPEL / (dist * dist);
                                a.vx -= f*dx/dist; a.vy -= f*dy/dist;
                                b.vx += f*dx/dist; b.vy += f*dy/dist;
                            }
                        }
                        nmEdges.forEach(e => {
                            if (!e.source || !e.target) return;
                            const dx = e.target.x - e.source.x, dy = e.target.y - e.source.y;
                            const dist = Math.sqrt(dx*dx + dy*dy) || 1;
                            const f = (dist - SPRING_LEN) * SPRING_K;
                            e.source.vx += f*dx/dist; e.source.vy += f*dy/dist;
                            e.target.vx -= f*dx/dist; e.target.vy -= f*dy/dist;
                        });
                        nmNodes.forEach(n => {
                            n.vx += (W / 2 - n.x) * GRAV;
                            n.vy += (H / 2 - n.y) * GRAV;
                            n.vx *= DAMP; n.vy *= DAMP;
                            n.x  += n.vx;  n.y  += n.vy;
                        });
                    }
                    nmDraw();
                    const still = nmNodes.every(n => Math.abs(n.vx) < 0.15 && Math.abs(n.vy) < 0.15);
                    if (!still) nmAnimId = requestAnimationFrame(nmTickLoop);
                }
                """;
    }

    private String buildScriptsNetworkMapB() {
        return """
                function nmDraw() {
                    const canvas = document.getElementById('netmap-canvas');
                    if (!canvas) return;
                    const ctx = canvas.getContext('2d');
                    const W = canvas.width, H = canvas.height;
                    ctx.clearRect(0, 0, W, H);
                    ctx.save();
                    ctx.translate(nmOffX, nmOffY);
                    ctx.scale(nmScale, nmScale);

                    // edges — drawn below nodes
                    nmEdges.forEach(e => {
                        if (!e.source || !e.target) return;
                        ctx.beginPath();
                        ctx.moveTo(e.source.x, e.source.y);
                        ctx.lineTo(e.target.x, e.target.y);
                        ctx.strokeStyle = '#cbd5e1'; ctx.lineWidth = 1.5;
                        ctx.setLineDash([5, 4]); ctx.stroke(); ctx.setLineDash([]);
                        // arrowhead at target perimeter
                        const dx = e.target.x - e.source.x, dy = e.target.y - e.source.y;
                        const len = Math.sqrt(dx*dx + dy*dy) || 1;
                        const ux = dx/len, uy = dy/len;
                        const tx = e.target.x - ux * (e.target.radius + 2);
                        const ty = e.target.y - uy * (e.target.radius + 2);
                        ctx.beginPath();
                        ctx.moveTo(tx, ty);
                        ctx.lineTo(tx - ux*9 + uy*5, ty - uy*9 - ux*5);
                        ctx.lineTo(tx - ux*9 - uy*5, ty - uy*9 + ux*5);
                        ctx.fillStyle = '#94a3b8'; ctx.fill();
                    });

                    // nodes — use module-level nmResolveStatus() + nmPalette
                    nmNodes.forEach(n => {
                        const rawHealth = nmHealth[n.id];   // health object or null/undefined
                        const status    = nmResolveStatus(rawHealth);
                        const palette   = nmPalette[status];

                        // circle body
                        ctx.globalAlpha = status === 'DOWN' ? 0.55 : 1.0;
                        ctx.beginPath();
                        ctx.arc(n.x, n.y, n.radius, 0, Math.PI * 2);
                        ctx.fillStyle   = palette.fill;
                        ctx.fill();
                        ctx.strokeStyle = palette.ring;   // status colour — no type override
                        ctx.lineWidth   = 3;
                        ctx.stroke();
                        ctx.globalAlpha = 1.0;

                        // pulsing status dot (top-right corner of circle)
                        const dotX = n.x + n.radius * 0.68;
                        const dotY = n.y - n.radius * 0.68;
                        ctx.beginPath();
                        ctx.arc(dotX, dotY, 5.5, 0, Math.PI * 2);
                        ctx.fillStyle   = palette.dot;
                        ctx.fill();
                        ctx.strokeStyle = '#fff';
                        ctx.lineWidth   = 1.5;
                        ctx.stroke();

                        // service name label
                        const short = n.id.replace('-service','').replace('fractalx-','fx-');
                        ctx.fillStyle = status === 'DOWN' ? '#94a3b8' : '#1e293b';
                        ctx.font      = 'bold 10px Inter,system-ui,sans-serif';
                        ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
                        ctx.fillText(short.length > 11 ? short.slice(0,11)+'…' : short, n.x, n.y - 3);

                        // port number below name
                        if (n.port) {
                            ctx.fillStyle = '#94a3b8';
                            ctx.font      = '9px Inter,system-ui,sans-serif';
                            ctx.fillText(':' + n.port, n.x, n.y + 11);
                        }

                        // type badge (bottom-left corner) for infra nodes
                        const badge = nmTypeLabel[n.type];
                        if (badge) {
                            const bw = 26, bh = 12, bx = n.x - n.radius + 1, by = n.y + n.radius - 13;
                            ctx.fillStyle   = 'rgba(15,23,42,0.65)';
                            ctx.beginPath();
                            ctx.roundRect(bx, by, bw, bh, 3);
                            ctx.fill();
                            ctx.fillStyle = '#e2e8f0';
                            ctx.font      = 'bold 8px Inter,system-ui,sans-serif';
                            ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
                            ctx.fillText(badge, bx + bw / 2, by + bh / 2);
                        }
                    });
                    ctx.restore();
                }

                function nmSetupCanvas() {
                    const canvas = document.getElementById('netmap-canvas');
                    const tip = document.getElementById('netmap-tooltip');
                    canvas.onmousedown = e => {
                        const {nx, ny} = nmCanvasCoords(e);
                        nmDrag = nmNodes.find(n => Math.hypot(n.x-nx, n.y-ny) < n.radius) || null;
                        if (!nmDrag) { canvas.style.cursor = 'grabbing'; nmMouse = nmCanvasCoords(e, true); }
                    };
                    canvas.onmousemove = e => {
                        if (nmDrag) {
                            const {nx, ny} = nmCanvasCoords(e);
                            nmDrag.x = nx; nmDrag.y = ny; nmDrag.vx = 0; nmDrag.vy = 0; nmDraw();
                        } else if (e.buttons === 1) {
                            const {nx, ny} = nmCanvasCoords(e, true);
                            nmOffX += nx - nmMouse.x; nmOffY += ny - nmMouse.y;
                            nmMouse = {nx, ny}; nmDraw();
                        } else {
                            const {nx, ny} = nmCanvasCoords(e);
                            const hit = nmNodes.find(n => Math.hypot(n.x-nx, n.y-ny) < n.radius);
                            if (hit) {
                                const rawH = nmHealth[hit.id];
                                const statusLabel = nmPalette[nmResolveStatus(rawH)].label;
                                tip.innerHTML = `<b>${hit.label}</b>\\nHTTP :${hit.port||'—'}  gRPC :${hit.grpcPort||'—'}\\nStatus: ${statusLabel}`;
                                tip.style.display = '';
                                tip.style.left = (e.offsetX + 14) + 'px';
                                tip.style.top  = (e.offsetY - 10) + 'px';
                            } else { tip.style.display = 'none'; }
                        }
                    };
                    canvas.onmouseup = e => {
                        if (!nmDrag) canvas.style.cursor = 'grab';
                        const {nx, ny} = nmCanvasCoords(e);
                        const hit = nmNodes.find(n => Math.hypot(n.x-nx, n.y-ny) < n.radius);
                        if (hit && nmDrag === hit) showNetmapInfo(hit);
                        nmDrag = null;
                    };
                    canvas.onmouseleave = () => { tip.style.display = 'none'; nmDrag = null; };
                    canvas.onwheel = e => {
                        e.preventDefault();
                        zoomNetmap(e.deltaY < 0 ? 1.1 : 0.9);
                    };
                }

                function nmCanvasCoords(e, raw) {
                    const canvas = document.getElementById('netmap-canvas');
                    const rect = canvas.getBoundingClientRect();
                    const cx = (e.clientX - rect.left) * (canvas.width / rect.width);
                    const cy = (e.clientY - rect.top) * (canvas.height / rect.height);
                    if (raw) return {nx: cx, ny: cy};
                    return { nx: (cx - nmOffX) / nmScale, ny: (cy - nmOffY) / nmScale };
                }

                function zoomNetmap(factor) {
                    nmScale = Math.max(0.25, Math.min(4, nmScale * factor)); nmDraw();
                }
                function resetNetmapView() { nmScale = 1; nmOffX = 0; nmOffY = 0; nmDraw(); }

                function showNetmapInfo(node) {
                    const info = document.getElementById('netmap-info');
                    document.getElementById('netmap-info-name').textContent = node.label || node.id;
                    const rawH   = nmHealth[node.id];
                    const normH  = nmResolveStatus(rawH);
                    const pal    = nmPalette[normH];
                    const bgMap  = { UP:'#f0fdf4', DEGRADED:'#fffbeb', DOWN:'#fef2f2' };
                    const st     = { label: pal.label, color: pal.ring, bg: bgMap[normH] || '#f8fafc' };
                    const connections = nmEdges.filter(e => e.source === node || e.target === node).length;
                    const upstream   = nmEdges.filter(e => e.source === node).length;
                    const downstream = nmEdges.filter(e => e.target === node).length;
                    document.getElementById('netmap-info-body').innerHTML =
                        `<div><small style="color:#9ca3af;display:block;margin-bottom:3px">Status</small>
                            <span style="display:inline-flex;align-items:center;gap:5px;padding:3px 9px;border-radius:20px;
                                background:${st.bg};color:${st.color};font-weight:700;font-size:12px">
                                <span style="width:7px;height:7px;border-radius:50%;background:${st.color};display:inline-block"></span>
                                ${st.label}</span></div>
                        <div><small style="color:#9ca3af">HTTP Port</small><br><b>:${node.port || '—'}</b></div>
                        <div><small style="color:#9ca3af">gRPC Port</small><br><b>:${node.grpcPort || '—'}</b></div>
                        <div><small style="color:#9ca3af">Type</small><br><b>${node.type || '—'}</b></div>
                        <div><small style="color:#9ca3af">Connections</small><br>
                            <b>${connections}</b>
                            <span style="font-size:10px;color:#9ca3af;margin-left:4px">(↑${upstream} out · ↓${downstream} in)</span></div>`;
                    info.style.display = '';
                }
                """;
    }

    // ---- SCRIPTS: gRPC Browser ----------------------------------------------

    private String buildScriptsGrpcBrowser() {
        return """
                // ── gRPC Browser ───────────────────────────────────────────────────────────
                function loadGrpcBrowser() {
                    fetch('/api/grpc/services')
                        .then(r => r.json()).then(svcs => {
                            const el = document.getElementById('grpc-svc-list');
                            document.getElementById('grpc-svc-count').textContent = svcs.length + ' service(s)';
                            if (!svcs.length) {
                                el.innerHTML = '<div style="padding:24px;text-align:center;color:#9ca3af;width:100%"><i class="fas fa-project-diagram" style="font-size:24px;margin-bottom:8px;display:block;color:#d1d5db"></i><span style="font-size:12px">No gRPC services found</span></div>';
                                return;
                            }
                            el.innerHTML = svcs.map(s => `
                                <div onclick="loadGrpcDeps('${s.name}')"
                                    style="background:#f8fafc;border:1px solid #e5e7eb;border-radius:10px;padding:14px 16px;cursor:pointer;
                                           min-width:180px;transition:all .15s;position:relative"
                                    onmouseover="this.style.borderColor='#6366f1';this.style.background='#eef2ff'"
                                    onmouseout="this.style.borderColor='#e5e7eb';this.style.background='#f8fafc'">
                                    <div style="font-weight:600;font-size:13px;color:#111827;margin-bottom:6px">${s.name}</div>
                                    <div style="display:flex;gap:10px;flex-wrap:wrap">
                                        <span style="font-size:11px;color:#6b7280"><i class="fas fa-globe" style="margin-right:3px;color:#9ca3af"></i>HTTP :${s.httpPort||'-'}</span>
                                        <span style="font-size:11px;color:#6366f1;font-weight:600"><i class="fas fa-plug" style="margin-right:3px"></i>gRPC :${s.grpcPort||'-'}</span>
                                    </div>
                                    <span style="position:absolute;top:8px;right:8px;font-size:9px;font-weight:700;background:#e0e7ff;color:#4338ca;padding:2px 6px;border-radius:4px;text-transform:uppercase">${s.type||'service'}</span>
                                </div>`).join('');
                        }).catch(() => {
                            document.getElementById('grpc-svc-list').innerHTML =
                                '<div style="padding:16px;color:#ef4444;font-size:12px">Failed to load gRPC services</div>';
                        });
                    loadGrpcConnections();
                }

                function loadGrpcDeps(name) {
                    document.getElementById('grpc-detail-title').textContent = name;
                    document.getElementById('grpc-detail-body').innerHTML =
                        '<div style="padding:16px;color:#9ca3af;font-size:12px">Loading dependencies…</div>';
                    fetch('/api/grpc/' + encodeURIComponent(name) + '/deps')
                        .then(r => r.json()).then(d => {
                            if (d.error) {
                                document.getElementById('grpc-detail-body').innerHTML =
                                    `<div style="padding:16px;color:#ef4444;font-size:12px">${d.error}</div>`;
                                return;
                            }
                            const depCard = (arr, label, icon, color) => {
                                if (!arr.length) return `<div style="padding:8px 0;color:#9ca3af;font-size:12px">${label}: none</div>`;
                                return `<div style="margin-bottom:12px">
                                    <div style="font-size:11px;font-weight:700;color:${color};text-transform:uppercase;letter-spacing:.06em;margin-bottom:6px">
                                        <i class="${icon}" style="margin-right:4px"></i>${label}</div>
                                    ${arr.map(x => `<div style="display:flex;align-items:center;gap:8px;padding:6px 10px;background:#f8fafc;border:1px solid #e5e7eb;border-radius:7px;margin-bottom:4px">
                                        <span style="font-size:12px;font-weight:600;color:#111827">${x.name}</span>
                                        <span style="font-size:11px;color:#6366f1;font-weight:600">gRPC :${x.grpcPort||'-'}</span>
                                        <button onclick="document.getElementById('ping-host').value='localhost';document.getElementById('ping-port').value=${x.grpcPort||0};doGrpcPing()"
                                            style="margin-left:auto;font-size:10px;padding:2px 8px;border:1px solid #6366f1;border-radius:5px;background:#fff;color:#6366f1;cursor:pointer">Ping</button>
                                    </div>`).join('')}
                                </div>`;
                            };
                            document.getElementById('grpc-detail-body').innerHTML =
                                `<div style="padding:4px">
                                    ${depCard(d.upstream||[], 'Upstream Callers', 'fas fa-arrow-down', '#059669')}
                                    ${depCard(d.downstream||[], 'Downstream Dependencies', 'fas fa-arrow-up', '#2563eb')}
                                </div>`;
                            if (d.grpcPort) document.getElementById('ping-port').value = d.grpcPort;
                        }).catch(() => {
                            document.getElementById('grpc-detail-body').innerHTML =
                                '<div style="padding:16px;color:#ef4444;font-size:12px">Failed to load dependency info</div>';
                        });
                }

                function loadGrpcConnections() {
                    fetch('/api/grpc/connections')
                        .then(r => r.json()).then(conns => {
                            const tbody = document.getElementById('grpc-connections-tbody');
                            if (!conns.length) {
                                tbody.innerHTML = '<tr><td colspan="6" style="padding:24px;text-align:center;color:#9ca3af;font-size:12px">No NetScope connections found</td></tr>';
                                return;
                            }
                            const rows = [];
                            conns.forEach(c => {
                                (c.dependencies || []).forEach(d => {
                                    rows.push(`<tr style="border-bottom:1px solid #f1f5f9">
                                        <td style="padding:10px 14px"><strong style="color:#111827">${c.service}</strong></td>
                                        <td style="padding:10px 14px;color:#6b7280;font-family:monospace">:${c.httpPort}</td>
                                        <td style="padding:10px 14px"><span style="font-weight:600">${d.name}</span></td>
                                        <td style="padding:10px 14px"><span style="color:#6366f1;font-weight:700;font-family:monospace">:${d.grpcPort}</span></td>
                                        <td style="padding:10px 14px"><span style="font-size:10px;font-weight:700;background:#e0e7ff;color:#4338ca;padding:2px 7px;border-radius:4px">${d.protocol||'NetScope/gRPC'}</span></td>
                                        <td style="padding:10px 14px">
                                            <button onclick="document.getElementById('ping-host').value='localhost';document.getElementById('ping-port').value=${d.grpcPort};doGrpcPing()"
                                                style="font-size:11px;padding:3px 10px;border:1px solid #6366f1;border-radius:5px;background:#fff;color:#6366f1;cursor:pointer">
                                                <i class="fas fa-satellite-dish" style="margin-right:3px"></i>Ping</button>
                                        </td>
                                    </tr>`);
                                });
                            });
                            tbody.innerHTML = rows.join('');
                        }).catch(() => {
                            document.getElementById('grpc-connections-tbody').innerHTML =
                                '<tr><td colspan="6" style="padding:16px;text-align:center;color:#ef4444;font-size:12px">Failed to load connections</td></tr>';
                        });
                }

                function doGrpcPing() {
                    const host = document.getElementById('ping-host').value || 'localhost';
                    const port = parseInt(document.getElementById('ping-port').value);
                    const el   = document.getElementById('ping-result');
                    if (!port) { el.style.background='#fef3c7'; el.style.color='#92400e'; el.textContent = 'Enter a port number'; return; }
                    el.style.background='#f8fafc'; el.style.color='#6b7280'; el.textContent = '⏳ Pinging…';
                    fetch('/api/grpc/ping', { method: 'POST',
                        headers: {'Content-Type':'application/json'},
                        body: JSON.stringify({host, port})
                    }).then(r => r.json()).then(d => {
                        if (d.reachable) {
                            el.style.background='#f0fdf4'; el.style.color='#15803d';
                            el.innerHTML = `<i class="fas fa-check-circle"></i> Reachable in ${d.latencyMs}ms`;
                        } else {
                            el.style.background='#fef2f2'; el.style.color='#b91c1c';
                            el.innerHTML = `<i class="fas fa-times-circle"></i> Unreachable (${d.latencyMs}ms)`;
                        }
                    }).catch(() => {
                        el.style.background='#fef2f2'; el.style.color='#b91c1c';
                        el.textContent = 'Ping request failed';
                    });
                }
                """;
    }

    // ---- SCRIPTS: Incidents -------------------------------------------------

    private String buildScriptsIncidents() {
        return """
                // ── Incidents ──────────────────────────────────────────────────────────────
                let allIncidents = [];
                function loadIncidents() {
                    fetch('/api/incidents/stats').then(r => r.json()).then(s => {
                        document.getElementById('inc-total').textContent = s.total ?? '-';
                        document.getElementById('inc-open').textContent = s.open ?? '-';
                        document.getElementById('inc-inv').textContent = s.investigating ?? '-';
                        document.getElementById('inc-res').textContent = s.resolved ?? '-';
                        const sev = s.bySeverity || {};
                        document.getElementById('inc-p1').textContent = sev.P1 ?? 0;
                        const badge = document.getElementById('incident-badge');
                        const openCount = (s.open || 0) + (s.investigating || 0);
                        if (openCount > 0) { badge.textContent = openCount; badge.style.display = ''; }
                        else badge.style.display = 'none';
                    }).catch(() => {});
                    fetch('/api/incidents').then(r => r.json()).then(incs => {
                        allIncidents = incs;
                        renderIncidentCards(incs);
                    }).catch(() => {
                        document.getElementById('inc-cards').innerHTML =
                            '<div style="text-align:center;padding:24px;color:#ef4444;font-size:12px">Failed to load incidents</div>';
                    });
                }

                function filterIncidents() {
                    const st  = document.getElementById('inc-filter-status').value;
                    const sev = document.getElementById('inc-filter-sev').value;
                    renderIncidentCards(allIncidents.filter(i =>
                        (!st  || i.status   === st) &&
                        (!sev || i.severity === sev)));
                }

                function renderIncidentCards(incs) {
                    const el = document.getElementById('inc-cards');
                    if (!incs.length) {
                        el.innerHTML = `<div style="background:#fff;border:1px solid #e5e7eb;border-radius:12px;padding:48px;text-align:center;color:#9ca3af">
                            <i class="fas fa-shield-alt" style="font-size:36px;margin-bottom:12px;display:block;color:#d1d5db"></i>
                            <div style="font-size:14px;font-weight:600;margin-bottom:4px">No incidents</div>
                            <div style="font-size:12px">Your services are running smoothly</div>
                        </div>`;
                        return;
                    }
                    const sevCfg = {
                        P1:{bg:'#fef2f2',bd:'#ef4444',tx:'#b91c1c',label:'Critical'},
                        P2:{bg:'#fff7ed',bd:'#f97316',tx:'#c2410c',label:'High'},
                        P3:{bg:'#fffbeb',bd:'#f59e0b',tx:'#92400e',label:'Medium'},
                        P4:{bg:'#f0fdf4',bd:'#22c55e',tx:'#166534',label:'Low'}};
                    const stCfg = {
                        OPEN:         {bg:'#fef2f2',tx:'#b91c1c',icon:'fas fa-circle'},
                        INVESTIGATING:{bg:'#fffbeb',tx:'#92400e',icon:'fas fa-search'},
                        RESOLVED:     {bg:'#f0fdf4',tx:'#166534',icon:'fas fa-check-circle'}};
                    el.innerHTML = incs.map(i => {
                        const sc = sevCfg[i.severity]||sevCfg.P3;
                        const ss = stCfg[i.status]||{bg:'#f8fafc',tx:'#6b7280',icon:'fas fa-question-circle'};
                        const age = incAge(i.createdAt);
                        return `<div style="background:#fff;border:1px solid #e5e7eb;border-left:4px solid ${sc.bd};border-radius:0 12px 12px 0;padding:16px 20px;
                                   display:flex;align-items:flex-start;gap:16px;position:relative">
                            <!-- Severity badge -->
                            <div style="flex-shrink:0;min-width:52px;text-align:center;padding:8px 6px;border-radius:8px;background:${sc.bg}">
                                <div style="font-size:11px;font-weight:800;color:${sc.tx}">${i.severity}</div>
                                <div style="font-size:9px;color:${sc.tx};opacity:.7">${sc.label}</div>
                            </div>
                            <!-- Main content -->
                            <div style="flex:1;min-width:0">
                                <div style="font-size:14px;font-weight:600;color:#111827;margin-bottom:4px">${escHtml(i.title)}</div>
                                <div style="display:flex;gap:12px;flex-wrap:wrap;font-size:11px;color:#6b7280">
                                    ${i.affectedService?`<span><i class="fas fa-server" style="margin-right:3px"></i>${i.affectedService}</span>`:''}
                                    ${i.assignee?`<span><i class="fas fa-user" style="margin-right:3px"></i>${i.assignee}</span>`:''}
                                    <span><i class="fas fa-clock" style="margin-right:3px"></i>${age}</span>
                                </div>
                                ${i.description?`<div style="font-size:12px;color:#6b7280;margin-top:6px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:600px">${escHtml(i.description)}</div>`:''}
                            </div>
                            <!-- Status + actions -->
                            <div style="flex-shrink:0;display:flex;flex-direction:column;align-items:flex-end;gap:8px">
                                <span style="font-size:10px;font-weight:700;background:${ss.bg};color:${ss.tx};padding:3px 10px;border-radius:20px">
                                    <i class="${ss.icon}" style="margin-right:3px"></i>${i.status}
                                </span>
                                <div style="display:flex;gap:6px">
                                    ${i.status==='OPEN'?`<button onclick="updateIncidentStatus('${i.id}','INVESTIGATING')"
                                        style="font-size:11px;padding:4px 10px;border:1px solid #f59e0b;border-radius:5px;background:#fffbeb;color:#92400e;cursor:pointer">
                                        <i class="fas fa-search" style="margin-right:3px"></i>Investigate</button>`:''}
                                    ${i.status!=='RESOLVED'?`<button onclick="resolveIncident('${i.id}')"
                                        style="font-size:11px;padding:4px 10px;border:1px solid #22c55e;border-radius:5px;background:#f0fdf4;color:#166534;cursor:pointer">
                                        <i class="fas fa-check" style="margin-right:3px"></i>Resolve</button>`:''}
                                    <button onclick="deleteIncident('${i.id}')"
                                        style="font-size:11px;padding:4px 10px;border:1px solid #e5e7eb;border-radius:5px;background:#fff;color:#9ca3af;cursor:pointer">
                                        <i class="fas fa-trash"></i>
                                    </button>
                                </div>
                            </div>
                        </div>`;
                    }).join('');
                }

                function updateIncidentStatus(id, status) {
                    fetch('/api/incidents/' + id + '/status', { method:'PUT',
                        headers:{'Content-Type':'application/json'},
                        body: JSON.stringify({status, notes: status + ' via admin dashboard'})
                    }).then(() => loadIncidents()).catch(() => alert('Failed to update incident'));
                }

                function createIncident() {
                    const body = {
                        title: document.getElementById('inc-new-title').value,
                        affectedService: document.getElementById('inc-new-svc').value,
                        severity: document.getElementById('inc-new-sev').value,
                        assignee: document.getElementById('inc-new-assignee').value,
                        description: document.getElementById('inc-new-desc').value
                    };
                    if (!body.title) { alert('Title is required'); return; }
                    fetch('/api/incidents', { method:'POST',
                        headers:{'Content-Type':'application/json'},
                        body: JSON.stringify(body)
                    }).then(r => r.json()).then(() => {
                        bootstrap.Modal.getInstance(document.getElementById('inc-create-modal')).hide();
                        ['inc-new-title','inc-new-svc','inc-new-assignee','inc-new-desc'].forEach(id => {
                            document.getElementById(id).value = '';
                        });
                        loadIncidents();
                    }).catch(() => alert('Failed to create incident'));
                }

                function resolveIncident(id) {
                    fetch('/api/incidents/' + id + '/status', { method:'PUT',
                        headers:{'Content-Type':'application/json'},
                        body: JSON.stringify({status:'RESOLVED', notes:'Resolved via admin dashboard'})
                    }).then(() => loadIncidents()).catch(() => alert('Failed to update incident'));
                }

                function deleteIncident(id) {
                    if (!confirm('Delete this incident?')) return;
                    fetch('/api/incidents/' + id, { method:'DELETE' })
                        .then(() => loadIncidents()).catch(() => alert('Failed to delete incident'));
                }

                function incAge(iso) {
                    if (!iso) return '-';
                    const diff = Date.now() - new Date(iso).getTime();
                    const h = Math.floor(diff / 3600000);
                    if (h < 1) return Math.floor(diff/60000) + 'm ago';
                    if (h < 24) return h + 'h ago';
                    return Math.floor(h/24) + 'd ago';
                }

                function escHtml(s) {
                    return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
                }
                """;
    }

    // ---- SCRIPTS: Config Editor ---------------------------------------------

    private String buildScriptsConfigEditor() {
        return """
                // ── Config Editor ──────────────────────────────────────────────────────────
                let cfgCurrentService = null;

                function showCfgTab(tab) {
                    ['config','override','diff'].forEach(t => {
                        const btn = document.getElementById('cfgtab-' + t);
                        const panel = document.getElementById('cfgtab-panel-' + t);
                        if (!btn || !panel) return;
                        const active = t === tab;
                        btn.style.color = active ? '#6366f1' : '#9ca3af';
                        btn.style.borderBottomColor = active ? '#6366f1' : 'transparent';
                        btn.style.fontWeight = active ? '600' : '400';
                        panel.style.display = active ? '' : 'none';
                    });
                }

                function loadConfigEditor() {
                    fetch('/api/config/editor/all')
                        .then(r => r.json()).then(cfgs => {
                            const el = document.getElementById('cfg-svc-list');
                            if (!cfgs.length) {
                                el.innerHTML = '<div style="padding:20px;text-align:center;color:#9ca3af;font-size:12px">' +
                                    '<i class="fas fa-inbox" style="font-size:22px;margin-bottom:6px;display:block"></i>' +
                                    'No service configs found.</div>';
                                return;
                            }
                            el.innerHTML = cfgs.map(c => {
                                const ovCount = Object.keys(c.overrides||{}).length;
                                const ovBadge = ovCount
                                    ? `<span style="display:inline-block;margin-top:4px;padding:1px 7px;background:#fef3c7;color:#92400e;border-radius:10px;font-size:10px;font-weight:600">
                                        <i class="fas fa-exclamation-triangle" style="font-size:9px;margin-right:2px"></i>${ovCount} override${ovCount>1?'s':''}</span>`
                                    : '';
                                return `<div onclick="showCfgDetail('${c.name}')" id="cfgsvc-${c.name}"
                                    style="cursor:pointer;padding:10px 14px;border-bottom:1px solid #f1f5f9;transition:background .15s"
                                    onmouseenter="this.style.background='#f8fafc'" onmouseleave="if(cfgCurrentService!=='${c.name}')this.style.background=''">
                                    <div style="font-weight:600;font-size:13px;color:#111827">${c.name}</div>
                                    <div style="font-size:11px;color:#9ca3af;margin-top:2px">
                                        HTTP :${c.httpPort||'—'} &nbsp;&nbsp; gRPC :${c.grpcPort||'—'}
                                    </div>
                                    ${ovBadge}
                                </div>`;
                            }).join('');
                            // auto-select first service
                            if (cfgs.length) showCfgDetail(cfgs[0].name);
                        }).catch(() => {
                            document.getElementById('cfg-svc-list').innerHTML =
                                '<div style="padding:16px;color:#ef4444;font-size:12px">Failed to load service configs.</div>';
                        });
                    loadOverrides();
                }

                function showCfgDetail(name) {
                    cfgCurrentService = name;
                    document.getElementById('cfg-svc-title').textContent = name;
                    document.getElementById('cfg-reload-wrap').style.display = '';
                    document.getElementById('ov-svc').value = name;
                    // Highlight selected item in left list
                    document.querySelectorAll('[id^="cfgsvc-"]').forEach(el => {
                        el.style.background = el.id === 'cfgsvc-' + name ? '#eef2ff' : '';
                    });
                    const panel = document.getElementById('cfgtab-panel-config');
                    panel.innerHTML = '<div style="text-align:center;padding:24px;color:#9ca3af;font-size:12px">' +
                        '<i class="fas fa-spinner fa-spin" style="font-size:18px;margin-bottom:6px;display:block"></i>Loading…</div>';
                    showCfgTab('config');
                    fetch('/api/config/editor/' + encodeURIComponent(name))
                        .then(r => r.json()).then(c => {
                            const entries = Object.entries(c.effective || {});
                            if (!entries.length) {
                                panel.innerHTML = '<div style="padding:20px;text-align:center;color:#9ca3af;font-size:12px">' +
                                    '<i class="fas fa-folder-open" style="font-size:22px;margin-bottom:6px;display:block"></i>' +
                                    'No environment variables configured.</div>';
                                return;
                            }
                            const rows = entries.map(([k,v]) => {
                                const isOv = k in (c.overrides || {});
                                return `<div style="display:grid;grid-template-columns:1fr 2fr auto;gap:8px;align-items:center;
                                    padding:6px 0;border-bottom:1px solid #f1f5f9;font-size:12px">
                                    <span style="font-family:monospace;font-size:11px;color:${isOv?'#d97706':'#374151'};
                                        overflow-wrap:anywhere;font-weight:${isOv?'600':'400'}">${escHtml(k)}
                                        ${isOv?'<span style="font-size:9px;padding:1px 5px;background:#fef3c7;color:#92400e;border-radius:4px;margin-left:4px">overridden</span>':''}</span>
                                    <span style="font-family:monospace;font-size:11px;color:#64748b;overflow-wrap:anywhere">${escHtml(v)}</span>
                                    ${isOv?`<button onclick="removeOverride('${name}','${escHtml(k)}')"
                                        style="background:none;border:none;color:#ef4444;cursor:pointer;font-size:12px;padding:0;line-height:1"
                                        title="Remove override">✕</button>`:'<span></span>'}
                                </div>`;
                            }).join('');
                            panel.innerHTML = `<div style="padding:4px 0">${rows}</div>`;
                        }).catch(() => {
                            panel.innerHTML = '<div style="padding:16px;color:#ef4444;font-size:12px">' +
                                '<i class="fas fa-exclamation-circle" style="margin-right:4px"></i>Failed to load config for ' + name + '</div>';
                        });
                }

                function saveOverride() {
                    const service = document.getElementById('ov-svc').value.trim();
                    const key = document.getElementById('ov-key').value.trim();
                    const value = document.getElementById('ov-val').value.trim();
                    const msg = document.getElementById('ov-save-msg');
                    if (!service||!key||!value) { msg.innerHTML='<span style="color:#ef4444">All fields required</span>'; return; }
                    fetch('/api/config/editor/override', { method:'POST',
                        headers:{'Content-Type':'application/json'},
                        body: JSON.stringify({service, key, value})
                    }).then(r => r.json()).then(() => {
                        msg.innerHTML = '<span style="color:#22c55e"><i class="fas fa-check"></i> Saved</span>';
                        document.getElementById('ov-key').value = '';
                        document.getElementById('ov-val').value = '';
                        if (cfgCurrentService === service) showCfgDetail(service);
                        loadOverrides();
                        setTimeout(() => { msg.textContent = ''; }, 3000);
                    }).catch(() => { msg.innerHTML = '<span style="color:#ef4444">Failed to save</span>'; });
                }

                function removeOverride(service, key) {
                    fetch('/api/config/editor/override/' + encodeURIComponent(service) + '/' + encodeURIComponent(key),
                        { method:'DELETE' })
                        .then(() => { if (cfgCurrentService) showCfgDetail(cfgCurrentService); loadOverrides(); })
                        .catch(() => {});
                }

                function loadOverrides() {
                    fetch('/api/config/editor/overrides').then(r => r.json()).then(ovs => {
                        const el = document.getElementById('cfg-overrides-body');
                        const entries = Object.entries(ovs);
                        if (!entries.length) {
                            el.innerHTML = '<p class="text-muted" style="font-size:12px">No active overrides.</p>';
                            return;
                        }
                        el.innerHTML = entries.map(([svc, kvs]) =>
                            `<div style="margin-bottom:8px"><b style="font-size:12px">${svc}</b>` +
                            Object.entries(kvs).map(([k,v]) =>
                                `<div style="font-size:11px;font-family:monospace;padding:2px 0;color:#6b7280">
                                    <span style="color:#f59e0b">${escHtml(k)}</span> = ${escHtml(v)}</div>`
                            ).join('') + '</div>'
                        ).join('');
                    }).catch(() => {});
                }

                function loadConfigDiff() {
                    fetch('/api/config/editor/diff').then(r => r.json()).then(diff => {
                        const el = document.getElementById('cfg-diff-body');
                        const entries = Object.entries(diff);
                        if (!entries.length) { el.textContent = 'No differences — no overrides applied.'; return; }
                        el.innerHTML = entries.map(([svc, changes]) =>
                            `<div style="color:#6366f1;margin-bottom:4px">## ${svc}</div>` +
                            (changes||[]).map(c =>
                                `<div><span style="color:#22c55e">+ ${escHtml(c.key)} = ${escHtml(c.newValue)}</span>` +
                                (c.isNew ? '' : `\\n<span style="color:#ef4444">- ${escHtml(c.key)} = ${escHtml(c.baseValue)}</span>`) +
                                '</div>'
                            ).join('')
                        ).join('\\n');
                    }).catch(() => { document.getElementById('cfg-diff-body').textContent = 'Failed to load diff.'; });
                }

                function hotReloadService() {
                    if (!cfgCurrentService) return;
                    fetch('/api/config/editor/reload/' + encodeURIComponent(cfgCurrentService), {method:'POST'})
                        .then(r => r.json()).then(d => {
                            alert(d.success ? d.message : (d.message || 'Reload failed') + (d.hint ? '\\n' + d.hint : ''));
                        }).catch(() => alert('Hot reload request failed'));
                }
                """;
    }

    // ---- SCRIPTS: Enhanced Overview -----------------------------------------

    private String buildScriptsOverviewEnhanced() {
        return """
                // ── Enhanced Overview ──────────────────────────────────────────────────────
                function loadOverview() {
                    fetch('/api/services/all')
                        .then(r => r.json()).then(services => {
                            let up = 0, degraded = 0, down = 0;
                            const tbody = document.getElementById('overview-tbody');
                            tbody.innerHTML = '';
                            services.forEach(s => {
                                const st = healthStatus(s.health);
                                if (st === 'UP') up++;
                                else if (st === 'DEGRADED') { degraded++; up++; }
                                else down++;
                                tbody.innerHTML += `<tr>
                                    <td><strong>${s.meta.name}</strong></td>
                                    <td style="min-width:160px">${healthCell(s.health)}</td>
                                    <td>${s.meta.port || '-'}</td>
                                    <td><span style="color:#6366f1;font-size:12px">${s.meta.grpcPort || '-'}</span></td>
                                    <td><span class="badge bg-light text-dark">${s.meta.type}</span></td>
                                    <td>
                                        <button class="btn btn-xs btn-outline-primary btn-sm py-0 px-1"
                                            onclick="showServiceDetailModal('${s.meta.name}')">Detail</button>
                                    </td></tr>`;
                            });
                            document.getElementById('ov-total').textContent = services.length;
                            document.getElementById('ov-up').textContent = up;
                            document.getElementById('ov-down').textContent = down;
                        }).catch(() => {
                            document.getElementById('overview-tbody').innerHTML =
                                '<tr><td colspan="6" class="text-danger text-center p-3">Failed to load</td></tr>';
                        });
                    fetch('/api/alerts/active')
                        .then(r => r.json())
                        .then(a => {
                            document.getElementById('ov-alerts').textContent = a.length;
                            const el = document.getElementById('ov-alerts-list');
                            const badge = document.getElementById('ov-alert-badge');
                            const ts = document.getElementById('ov-alerts-ts');
                            if (ts) ts.textContent = new Date().toLocaleTimeString();
                            if (!el) return;
                            if (badge) {
                                if (a.length) { badge.textContent = a.length + ' firing'; badge.style.display = ''; }
                                else badge.style.display = 'none';
                            }
                            if (!a.length) {
                                el.innerHTML = `<div style="padding:24px 12px;text-align:center">
                                    <div style="width:40px;height:40px;border-radius:50%;background:#f0fdf4;display:flex;align-items:center;justify-content:center;margin:0 auto 8px">
                                        <i class="fas fa-check" style="color:#10b981;font-size:16px"></i>
                                    </div>
                                    <div style="font-size:13px;font-weight:600;color:#111827;margin-bottom:3px">All clear</div>
                                    <div style="font-size:11px;color:#9ca3af">No active alerts at this time</div>
                                </div>`;
                                return;
                            }
                            const sevCfg = {
                                CRITICAL:{dot:'#ef4444',bg:'#fef2f2',pill:'background:#fef2f2;color:#b91c1c;border:1px solid #fecaca'},
                                HIGH:    {dot:'#f97316',bg:'#fff7ed',pill:'background:#fff7ed;color:#c2410c;border:1px solid #fed7aa'},
                                WARN:    {dot:'#f59e0b',bg:'#fffbeb',pill:'background:#fffbeb;color:#92400e;border:1px solid #fde68a'},
                                INFO:    {dot:'#3b82f6',bg:'#eff6ff',pill:'background:#eff6ff;color:#1d4ed8;border:1px solid #bfdbfe'}
                            };
                            el.innerHTML = a.slice(0,5).map(al => {
                                const sev = (al.severity||'WARN').toUpperCase();
                                const c = sevCfg[sev] || sevCfg.WARN;
                                return `<div style="display:flex;align-items:flex-start;gap:10px;padding:9px 10px;
                                    background:${c.bg};border-radius:9px;margin-bottom:7px;border:1px solid rgba(0,0,0,.05)">
                                    <div style="width:8px;height:8px;border-radius:50%;background:${c.dot};margin-top:4px;flex-shrink:0"></div>
                                    <div style="flex:1;min-width:0">
                                        <div style="display:flex;align-items:center;gap:6px;margin-bottom:3px">
                                            <span style="font-size:10px;font-weight:700;padding:1px 7px;border-radius:10px;${c.pill}">${sev}</span>
                                            ${al.service ? `<span style="font-size:10px;color:#6b7280;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">
                                                <i class="fas fa-server" style="font-size:9px;margin-right:2px;color:#9ca3af"></i>${escHtml(al.service)}</span>` : ''}
                                        </div>
                                        <div style="font-size:12px;font-weight:500;color:#1f2937;line-height:1.4;overflow-wrap:break-word">
                                            ${escHtml(al.message||al.name||'Alert triggered')}</div>
                                    </div>
                                </div>`;
                            }).join('') + (a.length > 5 ? `<div style="text-align:center;padding:6px 0">
                                <a href="#" onclick="showSection('alerts')" style="font-size:11px;color:#6366f1;text-decoration:none">
                                    +${a.length-5} more alerts →</a></div>` : '');
                        }).catch(() => {
                            const el = document.getElementById('ov-alerts-list');
                            if (el) el.innerHTML = '<div style="padding:14px;text-align:center;color:#9ca3af;font-size:12px">Could not load alerts</div>';
                        });
                    fetch('/api/incidents/open')
                        .then(r => r.json())
                        .then(incs => {
                            const el = document.getElementById('ov-incidents-list');
                            const badge = document.getElementById('ov-inc-badge');
                            const ts = document.getElementById('ov-incidents-ts');
                            if (ts) ts.textContent = new Date().toLocaleTimeString();
                            if (!el) return;
                            if (badge) {
                                if (incs.length) { badge.textContent = incs.length + ' open'; badge.style.display = ''; }
                                else badge.style.display = 'none';
                            }
                            if (!incs.length) {
                                el.innerHTML = `<div style="padding:24px 12px;text-align:center">
                                    <div style="width:40px;height:40px;border-radius:50%;background:#f0fdf4;display:flex;align-items:center;justify-content:center;margin:0 auto 8px">
                                        <i class="fas fa-shield-alt" style="color:#10b981;font-size:16px"></i>
                                    </div>
                                    <div style="font-size:13px;font-weight:600;color:#111827;margin-bottom:3px">No open incidents</div>
                                    <div style="font-size:11px;color:#9ca3af">System is operating normally</div>
                                </div>`;
                                return;
                            }
                            const sev = {
                                P1:{label:'P1',bg:'#fef2f2',tx:'#b91c1c',border:'#fecaca',dot:'#ef4444'},
                                P2:{label:'P2',bg:'#fff7ed',tx:'#c2410c',border:'#fed7aa',dot:'#f97316'},
                                P3:{label:'P3',bg:'#fffbeb',tx:'#92400e',border:'#fde68a',dot:'#f59e0b'},
                                P4:{label:'P4',bg:'#f0fdf4',tx:'#166534',border:'#bbf7d0',dot:'#22c55e'}
                            };
                            const stColors = {OPEN:'#ef4444',INVESTIGATING:'#f59e0b',RESOLVED:'#10b981'};
                            el.innerHTML = incs.slice(0,5).map(i => {
                                const sc = sev[i.severity] || sev.P3;
                                const stColor = stColors[i.status] || '#9ca3af';
                                return `<div style="background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:10px 12px;margin-bottom:7px;
                                    border-left:3px solid ${sc.dot};box-shadow:0 1px 3px rgba(0,0,0,.04)">
                                    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:5px">
                                        <div style="display:flex;align-items:center;gap:6px">
                                            <span style="font-size:10px;font-weight:800;padding:1px 8px;border-radius:10px;
                                                background:${sc.bg};color:${sc.tx};border:1px solid ${sc.border}">${sc.label}</span>
                                            <span style="display:inline-flex;align-items:center;gap:3px;font-size:10px;font-weight:600;color:${stColor}">
                                                <span style="width:5px;height:5px;border-radius:50%;background:${stColor};display:inline-block"></span>
                                                ${i.status||'OPEN'}
                                            </span>
                                        </div>
                                        <span style="font-size:10px;color:#9ca3af">${incAge(i.createdAt)}</span>
                                    </div>
                                    <div style="font-size:12px;font-weight:600;color:#111827;margin-bottom:3px;overflow-wrap:break-word">
                                        ${escHtml(i.title)}</div>
                                    ${i.affectedService ? `<div style="font-size:10px;color:#6b7280;display:flex;align-items:center;gap:3px">
                                        <i class="fas fa-server" style="font-size:9px;color:#9ca3af"></i>${escHtml(i.affectedService)}</div>` : ''}
                                </div>`;
                            }).join('') + (incs.length > 5 ? `<div style="text-align:center;padding:6px 0">
                                <a href="#" onclick="showSection('incidents')" style="font-size:11px;color:#6366f1;text-decoration:none">
                                    +${incs.length-5} more incidents →</a></div>` : '');
                        }).catch(() => {
                            const el = document.getElementById('ov-incidents-list');
                            if (el) el.innerHTML = '<div style="padding:14px;text-align:center;color:#9ca3af;font-size:12px">Could not load incidents</div>';
                        });
                    fetch('/api/analytics/overview')
                        .then(r => r.json())
                        .then(d => {
                            const rps = document.getElementById('ov-rps');
                            const cpu = document.getElementById('ov-cpu');
                            if (rps) rps.textContent = (d.totalRps || 0).toFixed(1);
                            if (cpu) cpu.textContent = (d.avgCpu || 0).toFixed(1) + '%';
                        }).catch(() => {});
                }
                """;
    }

    // ---- SCRIPTS: decomposition stats ----------------------------------------

    private String buildScriptsDecompositionStats() {
        return """
                // ── Decomposition Stats ─────────────────────────────────────────────────────
                function loadDecompositionStats() {
                    Promise.all([
                        fetch('/api/decomposition/stats').then(r => r.json()),
                        fetch('/api/decomposition/services').then(r => r.json()),
                        fetch('/api/decomposition/sagas').then(r => r.json())
                    ]).then(([stats, services, sagas]) => {
                        renderDecompCards(stats);
                        renderDecompServices(services);
                        renderDecompSagas(sagas);
                    }).catch(() => {
                        document.getElementById('decomp-cards').innerHTML =
                            '<div class="col-12 text-danger text-center p-4">Failed to load decomposition stats</div>';
                    });
                }

                function renderDecompCards(s) {
                    const fmt = v => v != null ? v : '-';
                    document.getElementById('decomp-cards').innerHTML = `
                        <div class="col-6 col-lg-3">
                            <div class="stat-card text-center">
                                <div class="text-muted" style="font-size:11px;margin-bottom:4px">MICROSERVICES</div>
                                <div style="font-size:26px;font-weight:700;color:#6366f1">${fmt(s.totalServices)}</div>
                                <div class="text-muted" style="font-size:11px">Generated</div>
                            </div>
                        </div>
                        <div class="col-6 col-lg-3">
                            <div class="stat-card text-center">
                                <div class="text-muted" style="font-size:11px;margin-bottom:4px">CROSS-SERVICE DEPS</div>
                                <div style="font-size:26px;font-weight:700;color:#3b82f6">${fmt(s.totalDependencies)}</div>
                                <div class="text-muted" style="font-size:11px">${fmt(s.servicesWithDeps)} services affected</div>
                            </div>
                        </div>
                        <div class="col-6 col-lg-3">
                            <div class="stat-card text-center">
                                <div class="text-muted" style="font-size:11px;margin-bottom:4px">DB SCHEMAS</div>
                                <div style="font-size:26px;font-weight:700;color:#10b981">${fmt(s.totalSchemas)}</div>
                                <div class="text-muted" style="font-size:11px">${fmt(s.servicesWithDb)} services with DB</div>
                            </div>
                        </div>
                        <div class="col-6 col-lg-3">
                            <div class="stat-card text-center">
                                <div class="text-muted" style="font-size:11px;margin-bottom:4px">SAGAS</div>
                                <div style="font-size:26px;font-weight:700;color:#f59e0b">${fmt(s.totalSagas)}</div>
                                <div class="text-muted" style="font-size:11px">${fmt(s.totalSagaSteps)} total steps</div>
                            </div>
                        </div>
                        <div class="col-12 mt-1" style="font-size:11px;color:#9ca3af;text-align:right">
                            FractalX ${s.fractalxVersion || ''} &nbsp;·&nbsp; Generated at ${s.generatedAt ? new Date(s.generatedAt).toLocaleString() : ''}
                        </div>`;
                }

                function renderDecompServices(services) {
                    const tbody = document.getElementById('decomp-services-tbody');
                    tbody.innerHTML = '';
                    if (!services.length) {
                        tbody.innerHTML = '<tr><td colspan="7" class="text-muted text-center p-4">No services</td></tr>';
                        return;
                    }
                    services.forEach(s => {
                        const deps = (s.dependencies || []).join(', ') || '<span class="text-muted">none</span>';
                        const schemas = (s.ownedSchemas || []).join(', ') || '<span class="text-muted">none</span>';
                        tbody.innerHTML += `<tr>
                            <td><strong>${s.name}</strong></td>
                            <td><code style="font-size:11px">${s.className}</code></td>
                            <td><code>${s.port}</code></td>
                            <td><code>${s.grpcPort}</code></td>
                            <td style="font-size:12px">${deps}</td>
                            <td style="font-size:12px">${schemas}</td>
                            <td>${s.independentDeployment
                                ? '<span class="badge badge-up">Yes</span>'
                                : '<span class="badge badge-unknown">No</span>'}</td>
                        </tr>`;
                    });
                }

                function renderDecompSagas(sagas) {
                    const card = document.getElementById('decomp-sagas-card');
                    if (!sagas.length) { card.style.display = 'none'; return; }
                    card.style.display = '';
                    const tbody = document.getElementById('decomp-sagas-tbody');
                    tbody.innerHTML = '';
                    sagas.forEach(s => {
                        const timeout = s.timeoutMs ? (s.timeoutMs / 1000) + 's' : '-';
                        tbody.innerHTML += `<tr>
                            <td><code style="font-size:11px">${s.sagaId}</code></td>
                            <td>${s.service}</td>
                            <td><code style="font-size:11px">${s.method}</code></td>
                            <td><span class="badge bg-secondary">${s.steps}</span></td>
                            <td class="text-muted">${timeout}</td>
                        </tr>`;
                    });
                }
                """;
    }

    // ---- SCRIPTS: circuit breakers ------------------------------------------

    private String buildScriptsCircuitBreaker() {
        return """
                // ── Circuit Breakers ────────────────────────────────────────────────────────
                function loadCircuitBreakers() {
                    const tbody = document.getElementById('cb-tbody');
                    const cards = document.getElementById('cb-summary-cards');
                    if (!tbody) return;
                    tbody.innerHTML = '<tr><td colspan="8" class="text-muted p-4 text-center">Loading…</td></tr>';
                    fetch('/api/circuit-breakers')
                        .then(r => r.json()).then(data => {
                            tbody.innerHTML = '';
                            cards.innerHTML = '';
                            let totalOpen = 0, totalHalf = 0, totalClosed = 0;
                            data.forEach(svc => {
                                if (!svc.reachable) {
                                    tbody.innerHTML += `<tr>
                                        <td><strong>${svc.service}</strong></td>
                                        <td colspan="7" class="text-muted small">
                                            <i class="fas fa-exclamation-circle text-warning me-1"></i>
                                            Service unreachable — actuator may be offline
                                        </td></tr>`;
                                    return;
                                }
                                if (!svc.circuitBreakers || svc.circuitBreakers.length === 0) {
                                    tbody.innerHTML += `<tr>
                                        <td><strong>${svc.service}</strong></td>
                                        <td colspan="7" class="text-muted small">No circuit breakers registered</td>
                                    </tr>`;
                                    return;
                                }
                                svc.circuitBreakers.forEach((cb, idx) => {
                                    const state = cb.state || 'UNKNOWN';
                                    if (state === 'OPEN')      totalOpen++;
                                    else if (state === 'HALF_OPEN') totalHalf++;
                                    else if (state === 'CLOSED')    totalClosed++;
                                    const stateClass = state === 'CLOSED'    ? 'badge-up'
                                                     : state === 'OPEN'      ? 'badge-down'
                                                     : state === 'HALF_OPEN' ? 'bg-warning text-dark'
                                                     : 'badge-unknown';
                                    const svcCell = idx === 0
                                        ? `<td rowspan="${svc.circuitBreakers.length}"><strong>${svc.service}</strong></td>`
                                        : '';
                                    tbody.innerHTML += `<tr>
                                        ${svcCell}
                                        <td><code style="font-size:11px">${cb.name}</code></td>
                                        <td><span class="badge ${stateClass}">${state}</span></td>
                                        <td>${cb.failureRate ?? '-'}</td>
                                        <td>${cb.slowCallRate ?? '-'}</td>
                                        <td>${cb.bufferedCalls ?? '-'}</td>
                                        <td>${cb.failedCalls ?? '-'}</td>
                                        <td>${cb.notPermittedCalls ?? '-'}</td>
                                    </tr>`;
                                });
                            });
                            if (!tbody.innerHTML) {
                                tbody.innerHTML = '<tr><td colspan="8" class="text-muted text-center p-4">No circuit breakers found</td></tr>';
                            }
                            // Summary cards
                            cards.innerHTML = `
                                <div class="col-sm-4">
                                    <div class="stat-card text-center">
                                        <div class="text-muted" style="font-size:11px;margin-bottom:4px">CLOSED</div>
                                        <div style="font-size:22px;font-weight:700;color:#22c55e">${totalClosed}</div>
                                        <div class="text-muted" style="font-size:11px">Healthy</div>
                                    </div>
                                </div>
                                <div class="col-sm-4">
                                    <div class="stat-card text-center">
                                        <div class="text-muted" style="font-size:11px;margin-bottom:4px">HALF OPEN</div>
                                        <div style="font-size:22px;font-weight:700;color:#f59e0b">${totalHalf}</div>
                                        <div class="text-muted" style="font-size:11px">Recovering</div>
                                    </div>
                                </div>
                                <div class="col-sm-4">
                                    <div class="stat-card text-center">
                                        <div class="text-muted" style="font-size:11px;margin-bottom:4px">OPEN</div>
                                        <div style="font-size:22px;font-weight:700;color:#ef4444">${totalOpen}</div>
                                        <div class="text-muted" style="font-size:11px">Tripped</div>
                                    </div>
                                </div>`;
                        }).catch(() => {
                            tbody.innerHTML = '<tr><td colspan="8" class="text-danger p-4 text-center">Failed to load circuit breaker data</td></tr>';
                            cards.innerHTML = '';
                        });
                }
                """;
    }

    // ---- SCRIPTS: mobile sidebar --------------------------------------------

    private String buildScriptsMobileNav() {
        return """
                function openSidebar() {
                    document.getElementById('sidebar').classList.add('open');
                    document.getElementById('sb-overlay').classList.add('open');
                }
                function closeSidebar() {
                    document.getElementById('sidebar').classList.remove('open');
                    document.getElementById('sb-overlay').classList.remove('open');
                }
                """;
    }
}
