package com.fractalx.core.generator.admin;

import com.fractalx.core.model.FractalModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates the complete observability and alerting subsystem for the admin service.
 *
 * <p>Produces 9 classes in {@code com.fractalx.admin.observability}:
 * <ol>
 *   <li>{@code AlertSeverity}          — INFO / WARNING / CRITICAL enum</li>
 *   <li>{@code AlertRule}              — rule model (condition, threshold, severity)</li>
 *   <li>{@code AlertEvent}             — event model (id, timestamp, service, resolved)</li>
 *   <li>{@code AlertStore}             — thread-safe in-memory ring-buffer (500 events)</li>
 *   <li>{@code AlertConfigProperties}  — @ConfigurationProperties("fractalx.alerting")</li>
 *   <li>{@code AlertEvaluator}         — @Scheduled evaluator that fires alerts</li>
 *   <li>{@code NotificationDispatcher} — routes AlertEvents to configured channels</li>
 *   <li>{@code AlertChannels}          — AdminUI (SSE) + Webhook + Email + Slack</li>
 *   <li>{@code ObservabilityController}— REST API: /api/observability/*, /api/alerts/*, /api/traces/*, /api/logs</li>
 * </ol>
 */
class AdminObservabilityGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminObservabilityGenerator.class);

    void generate(Path srcMainJava, String basePackage, List<FractalModule> modules) throws IOException {
        Path pkg = AdminPackageUtil.createPackagePath(srcMainJava, basePackage + ".observability");

        generateAlertSeverity(pkg);
        generateAlertRule(pkg);
        generateAlertEvent(pkg);
        generateAlertStore(pkg);
        generateAlertConfigProperties(pkg);
        generateAlertEvaluator(pkg, modules);
        generateNotificationDispatcher(pkg);
        generateAlertChannels(pkg);
        generateObservabilityController(pkg, modules);

        log.debug("Generated admin observability subsystem ({} modules)", modules.size());
    }

    // -------------------------------------------------------------------------

    private void generateAlertSeverity(Path pkg) throws IOException {
        Files.writeString(pkg.resolve("AlertSeverity.java"), """
                package com.fractalx.admin.observability;

                /** Alert severity level, ordered from least to most critical. */
                public enum AlertSeverity {
                    INFO, WARNING, CRITICAL
                }
                """);
    }

    private void generateAlertRule(Path pkg) throws IOException {
        Files.writeString(pkg.resolve("AlertRule.java"), """
                package com.fractalx.admin.observability;

                /**
                 * Defines a single alerting rule.
                 *
                 * <p>Supported conditions: {@code health}, {@code response-time}, {@code error-rate}.
                 */
                public class AlertRule {

                    private String        name;
                    private String        condition;
                    private double        threshold;
                    private AlertSeverity severity          = AlertSeverity.WARNING;
                    private boolean       enabled           = true;
                    private int           consecutiveFailures = 1;

                    public String        getName()                       { return name; }
                    public void          setName(String n)               { this.name = n; }
                    public String        getCondition()                  { return condition; }
                    public void          setCondition(String c)          { this.condition = c; }
                    public double        getThreshold()                  { return threshold; }
                    public void          setThreshold(double t)          { this.threshold = t; }
                    public AlertSeverity getSeverity()                   { return severity; }
                    public void          setSeverity(AlertSeverity s)    { this.severity = s; }
                    public boolean       isEnabled()                     { return enabled; }
                    public void          setEnabled(boolean e)           { this.enabled = e; }
                    public int           getConsecutiveFailures()        { return consecutiveFailures; }
                    public void          setConsecutiveFailures(int c)   { this.consecutiveFailures = c; }
                }
                """);
    }

    private void generateAlertEvent(Path pkg) throws IOException {
        Files.writeString(pkg.resolve("AlertEvent.java"), """
                package com.fractalx.admin.observability;

                import java.time.Instant;
                import java.util.UUID;

                /** An alert that was fired by the {@link AlertEvaluator}. */
                public class AlertEvent {

                    private final String        id        = UUID.randomUUID().toString();
                    private final Instant       timestamp = Instant.now();
                    private String              service;
                    private AlertRule           rule;
                    private AlertSeverity       severity;
                    private String              message;
                    private boolean             resolved  = false;
                    private Instant             resolvedAt;

                    public String        getId()                      { return id; }
                    public Instant       getTimestamp()               { return timestamp; }
                    public String        getService()                 { return service; }
                    public void          setService(String s)         { this.service = s; }
                    public AlertRule     getRule()                    { return rule; }
                    public void          setRule(AlertRule r)         { this.rule = r; }
                    public AlertSeverity getSeverity()                { return severity; }
                    public void          setSeverity(AlertSeverity s) { this.severity = s; }
                    public String        getMessage()                 { return message; }
                    public void          setMessage(String m)         { this.message = m; }
                    public boolean       isResolved()                 { return resolved; }
                    public void          setResolved(boolean r)       { this.resolved = r; }
                    public Instant       getResolvedAt()              { return resolvedAt; }
                    public void          setResolvedAt(Instant t)     { this.resolvedAt = t; }
                }
                """);
    }

    private void generateAlertStore(Path pkg) throws IOException {
        Files.writeString(pkg.resolve("AlertStore.java"), """
                package com.fractalx.admin.observability;

                import org.springframework.stereotype.Component;

                import java.time.Instant;
                import java.util.ArrayList;
                import java.util.List;
                import java.util.concurrent.CopyOnWriteArrayList;
                import java.util.stream.Collectors;

                /**
                 * Thread-safe in-memory alert store with a rolling 500-event buffer.
                 */
                @Component
                public class AlertStore {

                    static final int MAX_SIZE = 500;

                    private final CopyOnWriteArrayList<AlertEvent> events = new CopyOnWriteArrayList<>();

                    public synchronized void save(AlertEvent event) {
                        if (events.size() >= MAX_SIZE) events.remove(0);
                        events.add(event);
                    }

                    public List<AlertEvent> findAll(int page, int size) {
                        List<AlertEvent> all = new ArrayList<>(events);
                        int from = Math.min(page * size, all.size());
                        int to   = Math.min(from + size, all.size());
                        return all.subList(from, to);
                    }

                    public List<AlertEvent> findUnresolved() {
                        return events.stream().filter(e -> !e.isResolved()).collect(Collectors.toList());
                    }

                    public List<AlertEvent> findBySeverity(String severity) {
                        return events.stream()
                                .filter(e -> severity.equalsIgnoreCase(e.getSeverity().name()))
                                .collect(Collectors.toList());
                    }

                    public boolean resolve(String id) {
                        for (AlertEvent e : events) {
                            if (e.getId().equals(id) && !e.isResolved()) {
                                e.setResolved(true);
                                e.setResolvedAt(Instant.now());
                                return true;
                            }
                        }
                        return false;
                    }

                    public long countUnresolved() {
                        return events.stream().filter(e -> !e.isResolved()).count();
                    }
                }
                """);
    }

    private void generateAlertConfigProperties(Path pkg) throws IOException {
        Files.writeString(pkg.resolve("AlertConfigProperties.java"), """
                package com.fractalx.admin.observability;

                import org.springframework.boot.context.properties.ConfigurationProperties;
                import org.springframework.stereotype.Component;

                import java.util.ArrayList;
                import java.util.List;

                /**
                 * Alert configuration properties bound from {@code fractalx.alerting.*}.
                 * Defaults match the generated {@code alerting.yml}.
                 */
                @Component
                @ConfigurationProperties(prefix = "fractalx.alerting")
                public class AlertConfigProperties {

                    private boolean       enabled         = true;
                    private long          evalIntervalMs  = 30_000;
                    private List<AlertRule> rules         = defaultRules();
                    private Channels      channels        = new Channels();

                    public boolean         isEnabled()                        { return enabled; }
                    public void            setEnabled(boolean e)              { this.enabled = e; }
                    public long            getEvalIntervalMs()                { return evalIntervalMs; }
                    public void            setEvalIntervalMs(long m)          { this.evalIntervalMs = m; }
                    public List<AlertRule> getRules()                         { return rules; }
                    public void            setRules(List<AlertRule> r)        { this.rules = r; }
                    public Channels        getChannels()                      { return channels; }
                    public void            setChannels(Channels c)            { this.channels = c; }

                    private static List<AlertRule> defaultRules() {
                        List<AlertRule> list = new ArrayList<>();
                        AlertRule down = new AlertRule();
                        down.setName("service-down"); down.setCondition("health");
                        down.setThreshold(1); down.setSeverity(AlertSeverity.CRITICAL);
                        down.setConsecutiveFailures(2);
                        list.add(down);

                        AlertRule rt = new AlertRule();
                        rt.setName("high-response-time"); rt.setCondition("response-time");
                        rt.setThreshold(2000); rt.setSeverity(AlertSeverity.WARNING);
                        rt.setConsecutiveFailures(3);
                        list.add(rt);

                        AlertRule er = new AlertRule();
                        er.setName("error-rate"); er.setCondition("error-rate");
                        er.setThreshold(10); er.setSeverity(AlertSeverity.WARNING);
                        er.setConsecutiveFailures(3);
                        list.add(er);
                        return list;
                    }

                    public static class Channels {
                        private AdminUI  adminUi = new AdminUI();
                        private Webhook  webhook = new Webhook();
                        private Email    email   = new Email();
                        private Slack    slack   = new Slack();

                        public AdminUI getAdminUi() { return adminUi; }
                        public void    setAdminUi(AdminUI a) { this.adminUi = a; }
                        public Webhook getWebhook() { return webhook; }
                        public void    setWebhook(Webhook w) { this.webhook = w; }
                        public Email   getEmail()   { return email; }
                        public void    setEmail(Email e) { this.email = e; }
                        public Slack   getSlack()   { return slack; }
                        public void    setSlack(Slack s) { this.slack = s; }
                    }

                    public static class AdminUI {
                        private boolean enabled = true;
                        public boolean isEnabled() { return enabled; }
                        public void setEnabled(boolean e) { this.enabled = e; }
                    }

                    public static class Webhook {
                        private boolean enabled = false;
                        private String  url     = "";
                        public boolean isEnabled() { return enabled; }
                        public void setEnabled(boolean e) { this.enabled = e; }
                        public String getUrl() { return url; }
                        public void setUrl(String u) { this.url = u; }
                    }

                    public static class Email {
                        private boolean      enabled  = false;
                        private String       smtpHost = "";
                        private int          smtpPort = 587;
                        private String       from     = "";
                        private List<String> to       = new ArrayList<>();
                        public boolean isEnabled() { return enabled; }
                        public void setEnabled(boolean e) { this.enabled = e; }
                        public String getSmtpHost() { return smtpHost; }
                        public void setSmtpHost(String h) { this.smtpHost = h; }
                        public int getSmtpPort() { return smtpPort; }
                        public void setSmtpPort(int p) { this.smtpPort = p; }
                        public String getFrom() { return from; }
                        public void setFrom(String f) { this.from = f; }
                        public List<String> getTo() { return to; }
                        public void setTo(List<String> t) { this.to = t; }
                    }

                    public static class Slack {
                        private boolean enabled    = false;
                        private String  webhookUrl = "";
                        public boolean isEnabled() { return enabled; }
                        public void setEnabled(boolean e) { this.enabled = e; }
                        public String getWebhookUrl() { return webhookUrl; }
                        public void setWebhookUrl(String u) { this.webhookUrl = u; }
                    }
                }
                """);
    }

    private void generateAlertEvaluator(Path pkg, List<FractalModule> modules) throws IOException {
        StringBuilder checks = new StringBuilder();
        for (FractalModule m : modules) {
            checks.append("""
                        evaluate("%s", "http://localhost:%d/actuator/health",
                                "http://localhost:%d/actuator/metrics/http.server.requests");
                    """.formatted(m.getServiceName(), m.getPort(), m.getPort()));
        }

        Files.writeString(pkg.resolve("AlertEvaluator.java"), """
                package com.fractalx.admin.observability;

                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.scheduling.annotation.Scheduled;
                import org.springframework.stereotype.Component;
                import org.springframework.web.client.RestTemplate;

                import java.util.Map;
                import java.util.concurrent.ConcurrentHashMap;

                /**
                 * Periodically polls every generated service and evaluates alert rules.
                 * Fires {@link AlertEvent}s via {@link NotificationDispatcher} on threshold breach.
                 * Auto-resolves alerts when the service recovers.
                 */
                @Component
                public class AlertEvaluator {

                    private static final Logger log = LoggerFactory.getLogger(AlertEvaluator.class);

                    private final AlertConfigProperties   config;
                    private final AlertStore              store;
                    private final NotificationDispatcher  dispatcher;
                    private final RestTemplate            rest = new RestTemplate();

                    // consecutive failure counter per "service::rule"
                    private final Map<String, Integer> failureCounts = new ConcurrentHashMap<>();

                    public AlertEvaluator(AlertConfigProperties config,
                                          AlertStore store,
                                          NotificationDispatcher dispatcher) {
                        this.config     = config;
                        this.store      = store;
                        this.dispatcher = dispatcher;
                    }

                    @Scheduled(fixedDelayString = "${fractalx.alerting.eval-interval-ms:30000}")
                    public void evaluate() {
                        if (!config.isEnabled()) return;
                        log.debug("Running alert evaluation cycle");
                        %s
                    }

                    private void evaluate(String service, String healthUrl, String metricsUrl) {
                        // --- health check ---
                        try {
                            String body = rest.getForObject(healthUrl, String.class);
                            boolean up = body != null && body.contains("\\"UP\\"");
                            processRule(service, "service-down", !up,
                                    service + " health check failed — status not UP");
                        } catch (Exception e) {
                            processRule(service, "service-down", true,
                                    service + " unreachable: " + e.getMessage());
                        }

                        // --- response-time check (Actuator metrics) ---
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> metrics = rest.getForObject(metricsUrl, Map.class);
                            if (metrics != null) {
                                double ms = extractDuration(metrics);
                                for (AlertRule rule : config.getRules()) {
                                    if ("response-time".equals(rule.getCondition()) && rule.isEnabled()) {
                                        processRule(service, rule.getName(), ms > rule.getThreshold(),
                                                service + " avg response time " + (int) ms + "ms > " + (int) rule.getThreshold() + "ms");
                                    }
                                }
                            }
                        } catch (Exception ignored) { /* metrics endpoint may not exist */ }
                    }

                    private void processRule(String service, String ruleName,
                                             boolean breached, String message) {
                        String key = service + "::" + ruleName;
                        if (breached) {
                            int count = failureCounts.merge(key, 1, Integer::sum);
                            AlertRule rule = config.getRules().stream()
                                    .filter(r -> ruleName.equals(r.getName()))
                                    .findFirst().orElse(null);
                            int threshold = rule != null ? rule.getConsecutiveFailures() : 1;
                            if (count >= threshold && store.findUnresolved().stream()
                                    .noneMatch(e -> service.equals(e.getService())
                                            && ruleName.equals(e.getRule() != null ? e.getRule().getName() : ""))) {
                                AlertEvent event = new AlertEvent();
                                event.setService(service);
                                event.setRule(rule);
                                event.setSeverity(rule != null ? rule.getSeverity() : AlertSeverity.WARNING);
                                event.setMessage(message);
                                store.save(event);
                                dispatcher.dispatch(event);
                                log.warn("Alert fired: {} — {}", ruleName, message);
                            }
                        } else {
                            failureCounts.put(key, 0);
                            // auto-resolve matching open alerts
                            store.findUnresolved().stream()
                                    .filter(e -> service.equals(e.getService())
                                            && ruleName.equals(e.getRule() != null ? e.getRule().getName() : ""))
                                    .forEach(e -> {
                                        store.resolve(e.getId());
                                        log.info("Alert auto-resolved: {} for {}", ruleName, service);
                                    });
                        }
                    }

                    private double extractDuration(@SuppressWarnings("unchecked") Map<String, Object> metrics) {
                        try {
                            @SuppressWarnings("unchecked")
                            java.util.List<Map<String, Object>> measurements =
                                    (java.util.List<Map<String, Object>>) metrics.get("measurements");
                            if (measurements != null) {
                                return measurements.stream()
                                        .filter(m -> "TOTAL_TIME".equals(m.get("statistic")))
                                        .mapToDouble(m -> ((Number) m.get("value")).doubleValue() * 1000)
                                        .findFirst().orElse(0.0);
                            }
                        } catch (Exception ignored) { }
                        return 0.0;
                    }
                }
                """.formatted(checks.toString()));
    }

    private void generateNotificationDispatcher(Path pkg) throws IOException {
        Files.writeString(pkg.resolve("NotificationDispatcher.java"), """
                package com.fractalx.admin.observability;

                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.stereotype.Component;

                /**
                 * Routes a fired {@link AlertEvent} to all configured notification channels.
                 * Each channel self-guards with an {@code enabled} flag.
                 */
                @Component
                public class NotificationDispatcher {

                    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

                    private final AlertConfigProperties config;
                    private final AlertChannels         channels;

                    public NotificationDispatcher(AlertConfigProperties config, AlertChannels channels) {
                        this.config   = config;
                        this.channels = channels;
                    }

                    public void dispatch(AlertEvent event) {
                        if (config.getChannels().getAdminUi().isEnabled()) {
                            channels.publishToAdminUi(event);
                        }
                        if (config.getChannels().getWebhook().isEnabled()) {
                            channels.sendToWebhook(event, config.getChannels().getWebhook().getUrl());
                        }
                        if (config.getChannels().getEmail().isEnabled()) {
                            channels.sendEmail(event, config.getChannels().getEmail());
                        }
                        if (config.getChannels().getSlack().isEnabled()) {
                            channels.sendToSlack(event, config.getChannels().getSlack().getWebhookUrl());
                        }
                        log.debug("Dispatched alert {} to configured channels", event.getId());
                    }
                }
                """);
    }

    private void generateAlertChannels(Path pkg) throws IOException {
        Files.writeString(pkg.resolve("AlertChannels.java"), """
                package com.fractalx.admin.observability;

                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.mail.javamail.JavaMailSenderImpl;
                import org.springframework.stereotype.Component;
                import org.springframework.web.client.RestTemplate;
                import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

                import java.util.List;
                import java.util.Map;
                import java.util.Properties;
                import java.util.concurrent.CopyOnWriteArrayList;

                /**
                 * Delivery implementations for all four alert notification channels.
                 *
                 * <ul>
                 *   <li><b>Admin UI</b>  — Server-Sent Events ({@link SseEmitter})</li>
                 *   <li><b>Webhook</b>   — HTTP POST JSON payload to a configured URL</li>
                 *   <li><b>Email</b>     — HTML email via JavaMailSender (SMTP)</li>
                 *   <li><b>Slack</b>     — Slack Incoming Webhook POST</li>
                 * </ul>
                 */
                @Component
                public class AlertChannels {

                    private static final Logger log = LoggerFactory.getLogger(AlertChannels.class);

                    private final List<SseEmitter> sseEmitters = new CopyOnWriteArrayList<>();
                    private final RestTemplate      rest        = new RestTemplate();

                    // ---- Admin UI (SSE) ----

                    /**
                     * Returns a new long-lived {@link SseEmitter} for the admin alerts stream.
                     * Registered emitters receive every subsequent alert in real time.
                     */
                    public SseEmitter subscribeAdminUi() {
                        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
                        sseEmitters.add(emitter);
                        emitter.onCompletion(() -> sseEmitters.remove(emitter));
                        emitter.onTimeout(()    -> sseEmitters.remove(emitter));
                        emitter.onError(e       -> sseEmitters.remove(emitter));
                        return emitter;
                    }

                    public void publishToAdminUi(AlertEvent event) {
                        List<SseEmitter> dead = new CopyOnWriteArrayList<>();
                        for (SseEmitter emitter : sseEmitters) {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("alert")
                                        .data(toJson(event)));
                            } catch (Exception e) {
                                dead.add(emitter);
                            }
                        }
                        sseEmitters.removeAll(dead);
                    }

                    // ---- Webhook ----

                    public void sendToWebhook(AlertEvent event, String webhookUrl) {
                        if (webhookUrl == null || webhookUrl.isBlank()) return;
                        Map<String, Object> payload = Map.of(
                                "id",        event.getId(),
                                "timestamp", event.getTimestamp().toString(),
                                "service",   event.getService(),
                                "severity",  event.getSeverity().name(),
                                "message",   event.getMessage()
                        );
                        int maxRetries = 3;
                        for (int i = 0; i < maxRetries; i++) {
                            try {
                                rest.postForEntity(webhookUrl, payload, Void.class);
                                return;
                            } catch (Exception e) {
                                log.warn("Webhook delivery attempt {} failed: {}", i + 1, e.getMessage());
                                if (i < maxRetries - 1) {
                                    try { Thread.sleep(1_000L * (i + 1)); } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt(); return;
                                    }
                                }
                            }
                        }
                        log.error("Webhook delivery failed after {} attempts for alert {}", maxRetries, event.getId());
                    }

                    // ---- Email (SMTP) ----

                    public void sendEmail(AlertEvent event, AlertConfigProperties.Email emailCfg) {
                        if (emailCfg.getTo().isEmpty() || emailCfg.getSmtpHost().isBlank()) return;
                        try {
                            JavaMailSenderImpl sender = new JavaMailSenderImpl();
                            sender.setHost(emailCfg.getSmtpHost());
                            sender.setPort(emailCfg.getSmtpPort());
                            Properties props = sender.getJavaMailProperties();
                            props.put("mail.transport.protocol", "smtp");
                            props.put("mail.smtp.starttls.enable", "true");

                            String subject = "[FractalX Alert] " + event.getSeverity().name()
                                    + ": " + event.getService() + " — " + event.getMessage();
                            String body = buildEmailBody(event);

                            for (String to : emailCfg.getTo()) {
                                jakarta.mail.internet.MimeMessage msg = sender.createMimeMessage();
                                org.springframework.mail.javamail.MimeMessageHelper helper =
                                        new org.springframework.mail.javamail.MimeMessageHelper(msg, false, "UTF-8");
                                helper.setFrom(emailCfg.getFrom());
                                helper.setTo(to);
                                helper.setSubject(subject);
                                helper.setText(body, true);
                                sender.send(msg);
                            }
                            log.info("Alert email sent for event {}", event.getId());
                        } catch (Exception e) {
                            log.error("Failed to send alert email: {}", e.getMessage());
                        }
                    }

                    private String buildEmailBody(AlertEvent event) {
                        String color = switch (event.getSeverity()) {
                            case CRITICAL -> "#dc3545";
                            case WARNING  -> "#ffc107";
                            case INFO     -> "#0dcaf0";
                        };
                        return "<html><body style=\\"font-family:sans-serif;padding:24px\\">"
                                + "<h2 style=\\"color:" + color + "\\">[" + event.getSeverity().name()
                                + "] " + event.getService() + "</h2>"
                                + "<table><tr><td><b>Service:</b></td><td>" + event.getService() + "</td></tr>"
                                + "<tr><td><b>Time:</b></td><td>" + event.getTimestamp() + "</td></tr>"
                                + "<tr><td><b>Message:</b></td><td>" + event.getMessage() + "</td></tr></table>"
                                + "<p style=\\"color:#6c757d;font-size:12px\\">Sent by FractalX AlertManager</p>"
                                + "</body></html>";
                    }

                    // ---- Slack ----

                    public void sendToSlack(AlertEvent event, String webhookUrl) {
                        if (webhookUrl == null || webhookUrl.isBlank()) return;
                        String color = switch (event.getSeverity()) {
                            case CRITICAL -> "danger";
                            case WARNING  -> "warning";
                            case INFO     -> "good";
                        };
                        Map<String, Object> payload = Map.of(
                                "text", ":bell: *FractalX Alert* — " + event.getSeverity().name(),
                                "attachments", List.of(Map.of(
                                        "color",  color,
                                        "fields", List.of(
                                                Map.of("title", "Service",   "value", event.getService(),  "short", true),
                                                Map.of("title", "Severity",  "value", event.getSeverity().name(), "short", true),
                                                Map.of("title", "Message",   "value", event.getMessage(), "short", false),
                                                Map.of("title", "Timestamp", "value", event.getTimestamp().toString(), "short", false)
                                        )
                                ))
                        );
                        try {
                            rest.postForEntity(webhookUrl, payload, Void.class);
                        } catch (Exception e) {
                            log.error("Slack alert delivery failed: {}", e.getMessage());
                        }
                    }

                    // ---- Helpers ----

                    private String toJson(AlertEvent e) {
                        return "{\\"id\\":\\"%s\\",\\"timestamp\\":\\"%s\\",\\"service\\":\\"%s\\",\\"severity\\":\\"%s\\",\\"message\\":\\"%s\\",\\"resolved\\":%b}"
                                .formatted(e.getId(), e.getTimestamp(), e.getService(),
                                        e.getSeverity().name(), e.getMessage(), e.isResolved());
                    }
                }
                """);
    }

    private void generateObservabilityController(Path pkg, List<FractalModule> modules) throws IOException {
        StringBuilder healthChecks = new StringBuilder();
        for (FractalModule m : modules) {
            healthChecks.append("""
                        metrics.put("%s", fetchServiceMetrics("%s", "http://localhost:%d/actuator/health",
                                "http://localhost:%d/actuator/metrics/http.server.requests"));
                    """.formatted(m.getServiceName(), m.getServiceName(), m.getPort(), m.getPort()));
        }

        Files.writeString(pkg.resolve("ObservabilityController.java"), """
                package com.fractalx.admin.observability;

                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                import org.springframework.web.client.RestTemplate;
                import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

                import java.util.LinkedHashMap;
                import java.util.List;
                import java.util.Map;

                /**
                 * REST API for the admin observability and alerting subsystem.
                 *
                 * <pre>
                 * GET  /api/observability/metrics       — per-service health + latency snapshot
                 * GET  /api/traces                      — proxy to Jaeger query API
                 * GET  /api/traces/{traceId}            — single trace from Jaeger
                 * GET  /api/alerts                      — paginated alert history
                 * GET  /api/alerts/active               — unresolved alerts only
                 * POST /api/alerts/{id}/resolve         — manually resolve an alert
                 * GET  /api/alerts/stream               — SSE real-time alert feed
                 * GET  /api/alerts/config               — current alert rule configuration
                 * PUT  /api/alerts/config/rules         — update alert rules
                 * GET  /api/logs                        — proxy to logger-service
                 * </pre>
                 */
                @RestController
                @RequestMapping("/api")
                public class ObservabilityController {

                    private final AlertStore              alertStore;
                    private final AlertConfigProperties   alertConfig;
                    private final AlertChannels           alertChannels;
                    private final RestTemplate            rest = new RestTemplate();

                    @Value("${fractalx.observability.jaeger.query-url:http://localhost:16686}")
                    private String jaegerQueryUrl;

                    @Value("${fractalx.observability.logger-url:http://localhost:9099}")
                    private String loggerUrl;

                    public ObservabilityController(AlertStore store,
                                                   AlertConfigProperties config,
                                                   AlertChannels channels) {
                        this.alertStore    = store;
                        this.alertConfig   = config;
                        this.alertChannels = channels;
                    }

                    // ---- Metrics ----

                    @GetMapping("/observability/metrics")
                    public ResponseEntity<Map<String, Object>> getMetrics() {
                        Map<String, Object> metrics = new LinkedHashMap<>();
                        %s
                        return ResponseEntity.ok(metrics);
                    }

                    // ---- Traces (Jaeger proxy) ----

                    @GetMapping("/traces")
                    public ResponseEntity<Object> getTraces(
                            @RequestParam(required = false) String correlationId,
                            @RequestParam(required = false) String service,
                            @RequestParam(defaultValue = "20") int limit) {
                        try {
                            StringBuilder url = new StringBuilder(jaegerQueryUrl + "/api/traces?limit=" + limit);
                            if (service != null) url.append("&service=").append(service);
                            if (correlationId != null) url.append("&tags=").append("correlation.id%%3D").append(correlationId);
                            return ResponseEntity.ok(rest.getForObject(url.toString(), Object.class));
                        } catch (Exception e) {
                            return ResponseEntity.ok(Map.of("error", "Jaeger unavailable: " + e.getMessage()));
                        }
                    }

                    @GetMapping("/traces/{traceId}")
                    public ResponseEntity<Object> getTrace(@PathVariable String traceId) {
                        try {
                            return ResponseEntity.ok(
                                    rest.getForObject(jaegerQueryUrl + "/api/traces/" + traceId, Object.class));
                        } catch (Exception e) {
                            return ResponseEntity.ok(Map.of("error", "Jaeger unavailable: " + e.getMessage()));
                        }
                    }

                    // ---- Alerts ----

                    @GetMapping("/alerts")
                    public ResponseEntity<List<AlertEvent>> getAlerts(
                            @RequestParam(defaultValue = "0")  int page,
                            @RequestParam(defaultValue = "20") int size) {
                        return ResponseEntity.ok(alertStore.findAll(page, size));
                    }

                    @GetMapping("/alerts/active")
                    public ResponseEntity<List<AlertEvent>> getActiveAlerts() {
                        return ResponseEntity.ok(alertStore.findUnresolved());
                    }

                    @PostMapping("/alerts/{id}/resolve")
                    public ResponseEntity<Map<String, Object>> resolveAlert(@PathVariable String id) {
                        boolean resolved = alertStore.resolve(id);
                        return ResponseEntity.ok(Map.of("resolved", resolved, "id", id));
                    }

                    @GetMapping("/alerts/stream")
                    public SseEmitter streamAlerts() {
                        return alertChannels.subscribeAdminUi();
                    }

                    @GetMapping("/alerts/config")
                    public ResponseEntity<AlertConfigProperties> getAlertConfig() {
                        return ResponseEntity.ok(alertConfig);
                    }

                    @PutMapping("/alerts/config/rules")
                    public ResponseEntity<List<AlertRule>> updateRules(@RequestBody List<AlertRule> rules) {
                        alertConfig.setRules(rules);
                        return ResponseEntity.ok(alertConfig.getRules());
                    }

                    // ---- Logs (logger-service proxy) ----

                    @GetMapping("/logs")
                    public ResponseEntity<Object> getLogs(
                            @RequestParam(required = false) String correlationId,
                            @RequestParam(required = false) String service,
                            @RequestParam(required = false) String level,
                            @RequestParam(defaultValue = "0")  int page,
                            @RequestParam(defaultValue = "50") int size) {
                        try {
                            StringBuilder url = new StringBuilder(loggerUrl + "/api/logs?page=" + page + "&size=" + size);
                            if (correlationId != null) url.append("&correlationId=").append(correlationId);
                            if (service != null)       url.append("&service=").append(service);
                            if (level != null)         url.append("&level=").append(level);
                            return ResponseEntity.ok(rest.getForObject(url.toString(), Object.class));
                        } catch (Exception e) {
                            return ResponseEntity.ok(Map.of("error", "Logger service unavailable: " + e.getMessage()));
                        }
                    }

                    // ---- Helper ----

                    private Map<String, Object> fetchServiceMetrics(String service, String healthUrl, String metricsUrl) {
                        Map<String, Object> snap = new LinkedHashMap<>();
                        snap.put("service", service);
                        try {
                            String health = rest.getForObject(healthUrl, String.class);
                            snap.put("health", health != null && health.contains("\\"UP\\"") ? "UP" : "DOWN");
                        } catch (Exception e) {
                            snap.put("health", "DOWN");
                        }
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = rest.getForObject(metricsUrl, Map.class);
                            if (m != null) snap.put("metrics", m);
                        } catch (Exception e) {
                            snap.put("metrics", Map.of());
                        }
                        return snap;
                    }
                }
                """.formatted(healthChecks.toString()));
    }
}
