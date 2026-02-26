<img src="https://raw.githubusercontent.com/Project-FractalX/fractalx-static-content/937e314f9af4fca1b23d76262f40c1d339d179d4/images/logos/FractalX-1.jpg" alt="FractalX logo" width="30%" />

# FractalX

A static decomposition framework that converts modular monolithic Spring Boot applications into production-ready microservice deployments via AST analysis and code generation.

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [Prerequisites](#3-prerequisites)
4. [Build](#4-build)
5. [Annotate Your Monolith](#5-annotate-your-monolith)
6. [Run Decomposition](#6-run-decomposition)
7. [Generated Output](#7-generated-output)
8. [Start Generated Services](#8-start-generated-services)
9. [Docker Deployment](#9-docker-deployment)
10. [API Gateway](#10-api-gateway)
11. [Service Discovery](#11-service-discovery)
12. [Gateway Security](#12-gateway-security)
13. [Resilience](#13-resilience)
14. [Data Consistency](#14-data-consistency)
15. [Observability & Monitoring](#15-observability--monitoring)
16. [Admin Dashboard](#16-admin-dashboard)
17. [Test Endpoints](#17-test-endpoints)
18. [Configuration Reference](#18-configuration-reference)
19. [Troubleshooting](#19-troubleshooting)
20. [Contributing](#20-contributing)
21. [License](#21-license)

---

## 1. Overview

FractalX performs **static decomposition**: it reads your monolith's source code at build time, identifies module boundaries via annotations, and generates fully runnable microservice projects — including inter-service communication, database isolation, distributed sagas, service discovery, an API gateway, full observability stack, and Docker deployment files.

**No runtime agent. No bytecode manipulation. Pure code generation.**

### What Gets Generated

| Artifact | Port | Description |
|----------|------|-------------|
| `<service-name>/` (×N) | Configured | One Spring Boot project per `@DecomposableModule` |
| `fractalx-registry/` | 8761 | Lightweight service registry (self-registration, health polling) |
| `fractalx-gateway/` | 9999 | Spring Cloud Gateway with dynamic routing, auth, rate limiting, CORS, metrics |
| `logger-service/` | 9099 | Centralized structured log ingestion and query service |
| `admin-service/` | 9090 | Operations dashboard — health, topology, alerts, traces, logs |
| `fractalx-saga-orchestrator/` | 8099 | Saga orchestration service (generated when `@DistributedSaga` detected) |
| `docker-compose.yml` | — | Container-ready deployment for the entire mesh including Jaeger |
| `start-all.sh` / `stop-all.sh` | — | One-command local startup |

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Your Monolith                                │
│  @DecomposableModule(serviceName="order-service", port=8081)        │
│  @DecomposableModule(serviceName="payment-service", port=8082)      │
└──────────────────────────┬──────────────────────────────────────────┘
                           │  mvn fractalx:decompose
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    FractalX Code Generator                          │
│  ModuleAnalyzer → ServiceGenerator pipeline → GatewayGenerator     │
└──────────────────────────┬──────────────────────────────────────────┘
                           │  Generated output
                           ▼
┌────────────────────────────────────────────────────────────────────────────┐
│  fractalx-registry :8761  ←─ all services self-register on startup         │
│                                                                            │
│  fractalx-gateway  :9999  ←─ pulls live routes from registry              │
│    ├── /api/orders/**    → order-service   :8081                           │
│    └── /api/payments/**  → payment-service :8082                           │
│    └── TracingFilter (X-Correlation-Id, W3C traceparent)                   │
│    └── GatewayMetricsFilter (gateway.requests.total / duration)            │
│                                                                            │
│  order-service   :8081  ←──[NetScope gRPC :18081]──► payment-service       │
│  payment-service :8082   ←─ OtelConfig (OTLP → Jaeger)                    │
│                           ←─ ServiceHealthConfig (TCP HealthIndicator)     │
│                                                                            │
│  logger-service  :9099  ←─ structured log ingestion (correlationId)        │
│  jaeger          :16686 / :4317  ←─ distributed trace store (OTLP)        │
│                                                                            │
│  admin-service   :9090  ←─ Observability · Alerts · Traces · Logs         │
└────────────────────────────────────────────────────────────────────────────┘
```

### Generation Pipeline (per service)

```
PomGenerator → ApplicationGenerator → ConfigurationGenerator
→ ObservabilityInjector (patch YAML)
→ CodeCopier → CodeTransformer (AST rewrites)
→ FileCleanupStep → NetScopeServerAnnotationStep
→ NetScopeClientGenerator → NetScopeClientWiringStep
→ DistributedServiceHelper (DB isolation, Flyway, Outbox, Saga)
→ OtelConfigStep           ← generates OtelConfig.java (OTLP → Jaeger)
→ HealthMetricsStep        ← generates ServiceHealthConfig.java (TCP HealthIndicator)
→ ServiceRegistrationStep  ← registers with fractalx-registry
→ NetScopeRegistryBridgeStep ← dynamic gRPC host resolution
→ ResilienceConfigStep     ← Resilience4j CB / Retry / TimeLimiter
```

---

## 3. Prerequisites

| Requirement | Version |
|-------------|---------|
| Java | 17+ |
| Maven | 3.8+ |
| Git | any |
| Docker + Compose | optional (for container deployment) |

---

## 4. Build

Build and install all framework modules:

```bash
cd fractalx-parent
mvn clean install -DskipTests
```

This installs into your local Maven repository:
- `fractalx-annotations` — `@DecomposableModule`, `@ServiceBoundary`, `@AdminEnabled`, `@DistributedSaga`
- `fractalx-core` — AST analyzer + all code generators
- `fractalx-maven-plugin` — `mvn fractalx:decompose` goal
- `fractalx-runtime` — runtime beans used by generated services

---

## 5. Annotate Your Monolith

Add the FractalX annotations dependency:

```xml
<dependency>
    <groupId>com.fractalx</groupId>
    <artifactId>fractalx-annotations</artifactId>
    <version>0.3.2-SNAPSHOT</version>
</dependency>
```

Mark each logical module:

```java
@DecomposableModule(
    serviceName = "order-service",
    port = 8081,
    independentDeployment = true
)
@ServiceBoundary
public class OrderModule {
    private final PaymentService paymentService; // cross-module dependency — auto-detected
    ...
}
```

```java
@DecomposableModule(
    serviceName = "payment-service",
    port = 8082,
    independentDeployment = true
)
public class PaymentModule { ... }
```

For distributed sagas:

```java
@DistributedSaga(sagaId = "place-order")
public OrderResult placeOrder(OrderRequest req) { ... }
```

---

## 6. Run Decomposition

```bash
cd your-app
mvn fractalx:decompose
```

Or with explicit version:

```bash
mvn com.fractalx:fractalx-maven-plugin:0.3.2-SNAPSHOT:decompose
```

Generated services appear under:

```bash
target/generated-services/
```

---

## 7. Generated Output

```
target/generated-services/
├── fractalx-registry/          # Service discovery registry (start first)
├── order-service/              # Generated microservice
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/
│       │   ├── com/example/order/     # Copied + transformed source
│       │   └── com/fractalx/generated/orderservice/
│       │       ├── OtelConfig.java         # OpenTelemetry SDK (OTLP → Jaeger)
│       │       └── ServiceHealthConfig.java # TCP HealthIndicators per dependency
│       └── resources/
│           ├── application.yml         # Base config
│           ├── application-dev.yml     # Localhost defaults
│           └── application-docker.yml  # Full env-var substitution
├── payment-service/
├── fractalx-gateway/           # API Gateway + GatewayMetricsFilter
├── logger-service/             # Centralized log ingestion (port 9099)
├── admin-service/              # Operations dashboard (port 9090)
│   └── src/main/java/com/fractalx/admin/
│       ├── observability/
│       │   ├── AlertSeverity.java
│       │   ├── AlertRule.java
│       │   ├── AlertEvent.java
│       │   ├── AlertStore.java
│       │   ├── AlertConfigProperties.java
│       │   ├── AlertEvaluator.java
│       │   ├── NotificationDispatcher.java
│       │   ├── AlertChannels.java
│       │   └── ObservabilityController.java
│       └── ...
├── fractalx-saga-orchestrator/ # (if @DistributedSaga detected)
├── docker-compose.yml          # Includes Jaeger, logger-service, all OTEL env vars
├── start-all.sh
└── stop-all.sh
```

---

## 8. Start Generated Services

### One command (recommended)

```bash
cd target/generated-services
./start-all.sh
```

The script starts the registry first, waits for it to be ready, then starts all services, the gateway, admin, and logger. Logs are written to `*.log` files.

### Manual startup order

Services must start **after** the registry:

```bash
# Terminal 1 — registry (must be first)
cd fractalx-registry && mvn spring-boot:run

# Terminal 2
cd order-service && mvn spring-boot:run

# Terminal 3
cd payment-service && mvn spring-boot:run

# Terminal 4 — gateway (can start any time after registry)
cd fractalx-gateway && mvn spring-boot:run

# Terminal 5 — logger service
cd logger-service && mvn spring-boot:run

# Terminal 6 — admin dashboard
cd admin-service && mvn spring-boot:run
```

### Stop everything

```bash
./stop-all.sh
```

---

## 9. Docker Deployment

A `docker-compose.yml` is generated at the root of the output directory. It includes all services plus the full observability stack.

```bash
cd target/generated-services
docker compose up --build
```

Services start in dependency order: Jaeger → logger-service → registry → microservices → gateway → admin.

Each service container uses the `docker` Spring profile (all hostnames via Docker DNS) and automatically receives:

| Container env var | Value |
|---|---|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://jaeger:4317` |
| `OTEL_SERVICE_NAME` | Service name |
| `FRACTALX_LOGGER_URL` | `http://logger-service:9099/api/logs` |
| `FRACTALX_REGISTRY_URL` | `http://fractalx-registry:8761` |

To run a specific service locally against containerised peers:

```bash
SPRING_PROFILES_ACTIVE=docker \
FRACTALX_REGISTRY_URL=http://localhost:8761 \
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 \
mvn spring-boot:run
```

---

## 10. API Gateway

The gateway runs at **http://localhost:9999** and provides a single entry point for all services.

### Route mapping

| Gateway path | Forwards to |
|---|---|
| `/api/orders/**` | `order-service` |
| `/api/payments/**` | `payment-service` |
| `...` | `...` |

Routes are resolved **dynamically** from `fractalx-registry` at startup and refreshed periodically. If the registry is unavailable, the gateway falls back to the static routes baked into `application.yml`.

### Per-request filters (in order)

| Order | Filter | Purpose |
|-------|--------|---------|
| -100 | `RequestLoggingFilter` | Logs method, path, status, duration, correlationId on response |
| -99 | `TracingFilter` | Propagates `X-Correlation-Id` and W3C `traceparent`; echoes both in response |
| -98 | `GatewayMetricsFilter` | Records `gateway.requests.total{service,method,status}` counter + `gateway.requests.duration{service}` timer |
| -95 | `GatewayApiKeyFilter` | API key validation (disabled by default) |
| -90 | `GatewayBearerJwtFilter` | HMAC-SHA256 JWT validation (disabled by default) |
| -85 | `GatewayBasicAuthFilter` | Basic auth (disabled by default) |
| -80 | `GatewayRateLimiterFilter` | Sliding-window in-memory rate limiter |

### Per-route filters applied

- **Circuit Breaker** — opens after sustained failures; returns 503 JSON from `GatewayFallbackController`
- **Retry** — 2 retries on `502`, `503`, `504`
- **Rate Limiter** — sliding-window in-memory (default 100 req/s per IP per service)

---

## 11. Service Discovery

`fractalx-registry` is a lightweight REST-based service registry generated alongside your services.

### Registry API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/services` | Register a service instance |
| `GET` | `/services` | List all registered services |
| `GET` | `/services/{name}` | Get a specific service |
| `POST` | `/services/{name}/heartbeat` | Keep-alive ping |
| `DELETE` | `/services/{name}/deregister` | Deregister |
| `GET` | `/services/health` | Registry health summary |

### How registration works

Each generated service includes `ServiceRegistrationAutoConfig`:

1. **On startup** (`@PostConstruct`) — POSTs `{name, host, port, grpcPort, healthUrl}` to the registry
2. **Every 30 seconds** — sends a heartbeat to refresh `lastSeen`
3. **On shutdown** (`@PreDestroy`) — sends a deregister request

The registry polls each service's `/actuator/health` every 15 seconds and evicts services that have been unresponsive for 90 seconds.

### gRPC host resolution

`NetScopeRegistryBridge` runs at startup in any service that has gRPC dependencies. It queries the registry for each peer's current `host` and dynamically overrides the static YAML `netscope.client.servers.*` configuration via Spring's `ConfigurableEnvironment`. This makes NetScope host resolution container-ready without changing any YAML manually.

---

## 12. Gateway Security

All security mechanisms are **disabled by default**. Enable them selectively in `fractalx-gateway/src/main/resources/application.yml` or via environment variables.

### Supported mechanisms

| Mechanism | Activate | Description |
|-----------|----------|-------------|
| **Bearer JWT** | `fractalx.gateway.security.bearer.enabled: true` | HMAC-SHA256 signed tokens; validates `Authorization: Bearer <token>` |
| **OAuth2** | `fractalx.gateway.security.oauth2.enabled: true` | Validates tokens from an external IdP (Keycloak, Auth0, Okta) via JWK Set URI |
| **Basic Auth** | `fractalx.gateway.security.basic.enabled: true` | `Authorization: Basic <base64>` with configurable username/password |
| **API Key** | `fractalx.gateway.security.api-key.enabled: true` | `X-Api-Key` header or `?api_key=` query param; supports a list of valid keys |

Multiple mechanisms can be active simultaneously. Each filter self-skips if its mechanism is not enabled, so the order is: API Key → Bearer JWT → Basic Auth → OAuth2 resource server.

### Configuration

```yaml
fractalx:
  gateway:
    security:
      enabled: true                              # master switch (default: false)
      public-paths: /api/*/public/**, /api/*/auth/**

      bearer:
        enabled: true
        jwt-secret: ${JWT_SECRET:my-secret-min-32-chars}

      oauth2:
        enabled: false
        jwk-set-uri: ${OAUTH2_JWK_URI:http://keycloak:8080/realms/myrealm/protocol/openid-connect/certs}

      basic:
        enabled: false
        username: ${GATEWAY_BASIC_USER:admin}
        password: ${GATEWAY_BASIC_PASS:changeme}

      api-key:
        enabled: false
        valid-keys:
          - ${GATEWAY_API_KEY_1:replace-me}
```

### On successful authentication

The authenticating filter injects downstream headers:

| Header | Value |
|--------|-------|
| `X-User-Id` | Subject / username |
| `X-User-Roles` | Roles claim (Bearer JWT) |
| `X-Auth-Method` | `bearer-jwt` / `oauth2` / `basic` / `api-key` |

### CORS

```yaml
fractalx:
  gateway:
    cors:
      allowed-origins: ${CORS_ORIGINS:http://localhost:3000,http://localhost:4200}
      allowed-methods: GET,POST,PUT,DELETE,PATCH,OPTIONS
      allow-credentials: true
```

Exposed response headers include `X-Request-Id`, `X-Correlation-Id`, `X-Auth-Method`, `X-RateLimit-Limit`, and `X-RateLimit-Remaining`.

---

## 13. Resilience

### Per-service Resilience4j (generated in each service)

Every generated service gets a `NetScopeResilienceConfig` and Resilience4j YAML configuration for each of its cross-service dependencies:

| Pattern | Default config |
|---------|---------------|
| **CircuitBreaker** | 50% failure rate threshold, 30s wait in OPEN, 5 calls in HALF_OPEN |
| **Retry** | 3 attempts, 100ms base delay, exponential backoff (×2) |
| **TimeLimiter** | 2s timeout per gRPC call |

Override per dependency in `application.yml`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      payment-service:
        failure-rate-threshold: 60
        wait-duration-in-open-state: 60s
  retry:
    instances:
      payment-service:
        max-attempts: 5
  timelimiter:
    instances:
      payment-service:
        timeout-duration: 3s
```

### Gateway-level circuit breaker

Each route has a named `CircuitBreaker` filter. When it opens, the gateway returns a `503 Service Unavailable` JSON response rather than failing with a timeout. Override gateway rate limits per service:

```yaml
fractalx:
  gateway:
    rate-limit:
      default-rps: 100
      per-service:
        payment-service: 50
        order-service: 200
```

---

## 14. Data Consistency

FractalX generates a full data isolation and consistency stack per service:

| Concern | Generated component |
|---------|-------------------|
| DB isolation | Dual-package `@EntityScan` + `@EnableJpaRepositories` |
| DB config | Reads `fractalx-config.yml` → `fractalx.services.<name>` |
| Schema migration | Flyway `V1__init.sql` scaffold + outbox table |
| Transactional outbox | `OutboxEvent`, `OutboxRepository`, `OutboxPublisher`, `OutboxPoller` |
| Cross-service FK validation | `ReferenceValidator` using NetScope `exists()` calls |
| Relationship decoupling | `@ManyToOne ForeignEntity` → `String foreignEntityId` (AST rewrite) |

### Configure production databases in `fractalx-config.yml`

```yaml
fractalx:
  services:
    order-service:
      datasource:
        url: jdbc:mysql://localhost:3306/order_db
        username: root
        password: secret
    payment-service:
      datasource:
        url: jdbc:postgresql://localhost:5432/payment_db
        username: postgres
        password: secret
```

---

## 15. Observability & Monitoring

FractalX generates a complete observability stack automatically — no instrumentation code to write.

### Correlation IDs

Every request receives a `X-Correlation-Id` header at the gateway. This ID propagates through all downstream service calls via MDC (SLF4J), gRPC metadata, and the W3C `traceparent` header, making it possible to trace a request end-to-end across logs, metrics, and distributed traces.

```
Client → Gateway (assigns X-Correlation-Id) → order-service → payment-service
                                                     ↓                ↓
                                              logger-service     logger-service
                                              (correlationId)    (correlationId)
```

### Distributed Tracing — OpenTelemetry + Jaeger

Each generated service includes an `OtelConfig.java` class that configures the OpenTelemetry SDK:

- Exports spans via **OTLP/gRPC** to Jaeger (port 4317)
- Propagates **W3C `traceparent`** and **Baggage** headers across service calls
- Tags every span with `service.name` from Spring's `spring.application.name`
- Uses `BatchSpanProcessor` for efficient, non-blocking export

```yaml
# In application.yml (generated automatically)
fractalx:
  observability:
    otel:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
```

**Jaeger UI** is available at `http://localhost:16686` when running via Docker Compose.

### Service Health Metrics

For each service that has cross-service dependencies, a `ServiceHealthConfig.java` is generated with:

- One Spring Boot `HealthIndicator` per NetScope peer — performs a 2-second TCP connect to the peer's gRPC port
- A Micrometer gauge `fractalx.service.dependency.up{service=<name>}` (1.0 = UP, 0.0 = DOWN)
- Results surfaced in `/actuator/health` (show-details: always) and `/actuator/metrics`

### Centralized Logger Service

`logger-service` (port 9099) receives structured log payloads from all generated services via `FractalLogAppender` in `fractalx-runtime`:

| Endpoint | Description |
|----------|-------------|
| `POST /api/logs` | Ingest a log entry |
| `GET /api/logs?correlationId=&service=&level=&page=&size=` | Paginated log search |
| `GET /api/logs/services` | List distinct service names that have sent logs |
| `GET /api/logs/stats` | Per-service log count and error rate |

Each log entry includes: `service`, `correlationId`, `spanId`, `parentSpanId`, `level`, `message`, `timestamp`.

### Alert System

`admin-service` includes a built-in alert manager (`AlertEvaluator`) that polls service health and metrics every 30 seconds:

**Default alert rules:**

| Rule | Condition | Threshold | Severity |
|------|-----------|-----------|----------|
| `service-down` | health ≠ UP | 2 consecutive failures | CRITICAL |
| `high-response-time` | p99 response time | > 2000 ms (3 consecutive) | WARNING |
| `error-rate` | HTTP error rate | > 10% (3 consecutive) | WARNING |

**Notification channels** (all configurable in `alerting.yml`):

| Channel | Default | Config |
|---------|---------|--------|
| Admin UI (SSE) | **enabled** | Real-time push to dashboard |
| Webhook | disabled | `ALERT_WEBHOOK_URL` |
| Email (SMTP) | disabled | `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM`, `ALERT_EMAIL_TO` |
| Slack | disabled | `SLACK_WEBHOOK_URL` |

#### Alert configuration (`admin-service/src/main/resources/alerting.yml`)

```yaml
fractalx:
  alerting:
    enabled: true
    eval-interval-ms: 30000
    rules:
      - name: service-down
        condition: health
        threshold: 1
        severity: CRITICAL
        enabled: true
        consecutive-failures: 2
      - name: high-response-time
        condition: response-time
        threshold: 2000
        severity: WARNING
        enabled: true
        consecutive-failures: 3
    channels:
      admin-ui:
        enabled: true
      webhook:
        enabled: false
        url: ${ALERT_WEBHOOK_URL:}
      email:
        enabled: false
        smtp-host: ${SMTP_HOST:}
        smtp-port: ${SMTP_PORT:587}
        from: ${SMTP_FROM:}
        to: ${ALERT_EMAIL_TO:}
      slack:
        enabled: false
        webhook-url: ${SLACK_WEBHOOK_URL:}
```

### Prometheus Metrics

All generated services expose `/actuator/prometheus` for Prometheus scraping:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  tracing:
    sampling:
      probability: 1.0
```

---

## 16. Admin Dashboard

The admin service runs at **http://localhost:9090**. Access it with the default credentials `admin / admin`.

### Dashboard sections

| Section | Description |
|---------|-------------|
| **Dashboard** | Service count summary card + live alert badge + services status table |
| **Services** | All registered services from the registry |
| **Observability** | Per-service metrics cards: health status, response p99, error rate, uptime % |
| **Alerts** | Active alert table (with one-click resolve) + full history + real-time SSE feed |
| **Traces** | Search traces by Correlation ID or service; links through to Jaeger UI |
| **Logs** | Paginated log viewer with filters: correlationId, service, level |

### REST API endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/dashboard` | Thymeleaf HTML dashboard |
| `GET` | `/api/topology` | JSON service dependency graph (nodes + edges) |
| `GET` | `/api/health/summary` | Live health status map for all services |
| `GET` | `/api/services` | Proxies fractalx-registry `/services` |
| `GET` | `/api/observability/metrics` | Per-service snapshot: health, p99 latency, error rate, uptime % |
| `GET` | `/api/traces?correlationId=&service=&limit=` | Jaeger trace proxy — search by correlation ID |
| `GET` | `/api/traces/{traceId}` | Jaeger trace proxy — single trace detail |
| `GET` | `/api/alerts?page=&size=&severity=&service=` | Paginated alert history |
| `GET` | `/api/alerts/active` | Unresolved alerts only |
| `POST` | `/api/alerts/{id}/resolve` | Manually resolve an alert |
| `GET` | `/api/alerts/stream` | SSE feed — real-time alert push |
| `GET` | `/api/alerts/config` | Current alert rule configuration |
| `PUT` | `/api/alerts/config/rules` | Update alert rules list |
| `GET` | `/api/logs?correlationId=&service=&level=&page=&size=` | Logger-service proxy — paginated log query |

### Topology response example

```json
{
  "nodes": [
    { "id": "order-service",    "label": "order-service",    "port": 8081, "type": "microservice" },
    { "id": "payment-service",  "label": "payment-service",  "port": 8082, "type": "microservice" },
    { "id": "fractalx-gateway", "label": "API Gateway",      "port": 9999, "type": "gateway" },
    { "id": "fractalx-registry","label": "Service Registry", "port": 8761, "type": "registry" }
  ],
  "edges": [
    { "source": "order-service", "target": "payment-service", "protocol": "grpc" }
  ]
}
```

---

## 17. Test Endpoints

### Health checks

```bash
# Registry
curl http://localhost:8761/services/health

# Individual services (via gateway)
curl http://localhost:9999/api/orders/actuator/health
curl http://localhost:9999/api/payments/actuator/health

# Direct — shows dependency health indicators
curl http://localhost:8081/actuator/health

# Prometheus metrics
curl http://localhost:8081/actuator/prometheus
```

### List registered services

```bash
curl http://localhost:8761/services | jq .
```

### Correlation ID tracing

Every gateway request automatically receives an `X-Correlation-Id`. Pass your own to trace a specific flow:

```bash
curl http://localhost:9999/api/orders \
  -H "X-Correlation-Id: my-trace-id-123"

# Then query logs for that correlation ID
curl "http://localhost:9099/api/logs?correlationId=my-trace-id-123" | jq .

# Or search traces in the admin
curl "http://localhost:9090/api/traces?correlationId=my-trace-id-123" | jq .
```

### Query alerts

```bash
# Active alerts
curl http://localhost:9090/api/alerts/active | jq .

# Alert history (paginated)
curl "http://localhost:9090/api/alerts?page=0&size=20" | jq .

# Resolve an alert
curl -X POST http://localhost:9090/api/alerts/<id>/resolve

# Real-time alert stream (SSE)
curl -N http://localhost:9090/api/alerts/stream
```

### Service metrics

```bash
# Per-service observability snapshot
curl http://localhost:9090/api/observability/metrics | jq .
```

### Example business requests

```bash
# Process a payment
curl -X POST http://localhost:9999/api/payments/process \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST001","amount":100.50}'

# Create an order (calls payment service via NetScope gRPC)
curl -X POST http://localhost:9999/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST001","amount":100.50}'
```

### With API Key authentication (if enabled)

```bash
curl -X POST http://localhost:9999/api/orders \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: my-api-key" \
  -d '{"customerId":"CUST001","amount":100.50}'
```

### With Bearer JWT (if enabled)

```bash
curl -X POST http://localhost:9999/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt-token>" \
  -d '{"customerId":"CUST001","amount":100.50}'
```

---

## 18. Configuration Reference

### `application.yml` (base — common to all profiles)

```yaml
fractalx:
  registry:
    url: ${FRACTALX_REGISTRY_URL:http://localhost:8761}
    enabled: true
    host: ${FRACTALX_REGISTRY_HOST:localhost}
  observability:
    tracing: true
    metrics: true
    logger-url: ${FRACTALX_LOGGER_URL:http://localhost:9099/api/logs}
    otel:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}

netscope:
  server:
    grpc:
      port: <HTTP_PORT + 10000>

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
      show-components: always
  tracing:
    sampling:
      probability: 1.0
```

### `application-dev.yml` (local development)

Uses H2 in-memory database and localhost for all service addresses. Active by default (`SPRING_PROFILES_ACTIVE=dev`).

### `application-docker.yml` (container deployment)

All values from environment variables. Activated with `SPRING_PROFILES_ACTIVE=docker`. All service hostnames use Docker DNS (service names).

### Key environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `FRACTALX_REGISTRY_URL` | `http://localhost:8761` | Registry base URL |
| `FRACTALX_REGISTRY_HOST` | `localhost` | This service's advertised host |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` or `docker` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | OpenTelemetry collector (Jaeger) endpoint |
| `OTEL_SERVICE_NAME` | (service name) | Service name tag for traces |
| `FRACTALX_LOGGER_URL` | `http://localhost:9099/api/logs` | Centralized logger ingest URL |
| `JAEGER_QUERY_URL` | `http://localhost:16686` | Jaeger query API base (admin service) |
| `JWT_SECRET` | (dev default) | HMAC secret for Bearer JWT |
| `OAUTH2_JWK_URI` | (Keycloak default) | JWK Set URI for OAuth2 |
| `GATEWAY_SECURITY_ENABLED` | `false` | Enable gateway auth |
| `CORS_ORIGINS` | `http://localhost:3000,...` | Allowed CORS origins |
| `GATEWAY_DEFAULT_RPS` | `100` | Default rate limit (req/s) |
| `ALERT_WEBHOOK_URL` | — | Webhook URL for alert notifications |
| `SLACK_WEBHOOK_URL` | — | Slack incoming webhook URL |
| `SMTP_HOST` | — | SMTP server for email alerts |
| `ALERT_EMAIL_TO` | — | Alert recipient email address |

---

## 19. Troubleshooting

### Services fail to start — "Could not register with fractalx-registry"

The registry is not yet running. Start `fractalx-registry` first, then start other services. The `start-all.sh` script handles this automatically with a 5-second wait.

### NetScope gRPC call fails — "Could not resolve X from registry"

The target service has not yet registered itself. Check that it started without errors and that the registry URL is reachable (`curl http://localhost:8761/services`). Static YAML config is used as fallback so calls should still work.

### Traces not appearing in Jaeger

Check that Jaeger is running and reachable on port 4317:

```bash
# Docker Compose
docker compose ps jaeger

# Or start manually
docker run -d --name jaeger \
  -e COLLECTOR_OTLP_ENABLED=true \
  -p 16686:16686 -p 4317:4317 \
  jaegertracing/all-in-one:1.53
```

Then verify `OTEL_EXPORTER_OTLP_ENDPOINT` is set correctly in your service environment.

### Can't find logs for a request

Make sure `logger-service` is running (`curl http://localhost:9099/api/logs/services`). Each service sends logs asynchronously via `FractalLogAppender` in `fractalx-runtime`. Check that `FRACTALX_LOGGER_URL` points to the correct host. Use the `X-Correlation-Id` returned in the response header to search:

```bash
curl "http://localhost:9099/api/logs?correlationId=<id-from-response-header>"
```

### Alert evaluator is not firing

Verify `fractalx.alerting.enabled: true` in `alerting.yml`. The evaluator polls every 30 seconds by default. Check admin logs for `AlertEvaluator` output. You can reduce the interval for testing:

```yaml
fractalx:
  alerting:
    eval-interval-ms: 5000
```

### H2 startup error — `SET FOREIGN_KEY_CHECKS`

Generated `schema.sql` contains MySQL-specific syntax. Switch to MySQL via `application-dev.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/order_db
    username: root
    password: secret
    driver-class-name: com.mysql.cj.jdbc.Driver
```

Or disable SQL init temporarily:

```yaml
spring.sql.init.enabled: false
```

### Gateway returns 503 for a service

The circuit breaker has opened. Check service health at `/actuator/health` and review dependency health indicators (added by `ServiceHealthConfig`). The circuit resets automatically after 30 seconds (configurable).

### Debug tips

```bash
# Full Maven output
mvn spring-boot:run -e -X

# Spring Boot condition evaluation
mvn spring-boot:run -Dspring-boot.run.arguments="--debug"

# Tail service log (when started via start-all.sh)
tail -f order-service.log

# Check correlation ID propagation
curl -v http://localhost:9999/api/orders 2>&1 | grep -i "correlation\|trace\|request-id"
```

---

## 20. Contributing

- Fork the repository
- Create a branch: `git checkout -b feature/my-change`
- Build: `mvn clean install -DskipTests`
- Test against `fractalx-test-db-app`
- Open a pull request with a clear description

---

## 21. License

Licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
