# FractalX

[![Build](https://github.com/Project-FractalX/FractalX/actions/workflows/ci.yml/badge.svg)](https://github.com/Project-FractalX/FractalX/actions)
[![Maven Central](https://img.shields.io/badge/maven--central-1.0.0-SNAPSHOT-blue)](https://central.sonatype.com/artifact/org.fractalx/fractalx-annotations)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](LICENSE)
[![Java](https://img.shields.io/badge/java-17%2B-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/spring--boot-3.2.0-brightgreen)](https://spring.io/projects/spring-boot)

**Static decomposition framework — converts modular monolithic Spring Boot applications into production-ready microservice deployments via AST analysis and code generation.**

> No runtime agent. No bytecode manipulation. No manual wiring. Add three annotations, run one command, get a fully operational microservice platform.

---

## Why FractalX?

Manual microservices migrations are slow, error-prone, and expensive — requiring weeks of boilerplate wiring, container setup, and cross-cutting concerns like observability, resilience, and service discovery. FractalX eliminates that entirely:

| Capability | Without FractalX | With FractalX |
|---|---|---|
| Service scaffolding | Days per service | Seconds — fully generated |
| Inter-service communication | Write gRPC stubs manually | Auto-generated `@NetScopeClient` interfaces |
| Distributed tracing | Instrument every service | Auto-injected `OtelConfig.java` per service |
| Service discovery | Configure Eureka/Consul | Lightweight `fractalx-registry` auto-generated |
| Circuit breaking & retry | Configure Resilience4j per service | Auto-generated per dependency |
| Database isolation | Manually split schemas | `DataIsolationGenerator` + Flyway scaffolds |
| Distributed sagas | Write orchestrator from scratch | Full saga service from `@DistributedSaga` |
| Docker deployment | Write Dockerfiles + Compose | Generated `Dockerfile` + `docker-compose.yml` |
| Admin dashboard | Build your own | 14-section ops dashboard auto-generated |

---

## Table of Contents

1. [Quick Start -- 5 Minutes](#1-quick-start----5-minutes)
2. [How It Works](#2-how-it-works)
3. [Prerequisites](#3-prerequisites)
4. [Build the Framework](#4-build-the-framework)
5. [Annotate Your Monolith](#5-annotate-your-monolith)
6. [Pre-Decomposition Configuration](#6-pre-decomposition-configuration)
7. [Maven Plugin Reference](#7-maven-plugin-reference)
8. [Generated Output](#8-generated-output)
9. [Start Generated Services](#9-start-generated-services)
10. [Docker Deployment](#10-docker-deployment)
11. [API Gateway](#11-api-gateway)
12. [Service Discovery](#12-service-discovery)
13. [Gateway Security](#13-gateway-security)
14. [Resilience](#14-resilience)
15. [Data Consistency](#15-data-consistency)
16. [Observability & Monitoring](#16-observability--monitoring)
17. [Admin Dashboard](#17-admin-dashboard)
18. [Static Verification](#18-static-verification)
19. [Test Endpoints](#19-test-endpoints)
20. [Configuration Reference](#20-configuration-reference)
21. [Troubleshooting](#21-troubleshooting)
22. [Contributing](#22-contributing)
23. [License](#23-license)

---

## 1. Quick Start -- 5 Minutes

### Step 1 -- Add the dependency

```xml
<dependency>
    <groupId>org.fractalx</groupId>
    <artifactId>fractalx-annotations</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Step 2 -- Mark each module boundary

```java
// src/main/java/com/myapp/order/OrderModule.java
@DecomposableModule(serviceName = "order-service", port = 8081)
public class OrderModule {
    private PaymentService paymentService; // cross-module dep -- auto-detected
}

// src/main/java/com/myapp/payment/PaymentModule.java
@DecomposableModule(serviceName = "payment-service", port = 8082)
public class PaymentModule { }
```

That is the only source change required. No other annotations are needed in your service classes.

### Step 3 -- Add the plugin to your `pom.xml`

```xml
<plugin>
    <groupId>org.fractalx</groupId>
    <artifactId>fractalx-maven-plugin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</plugin>
```

### Step 4 -- Decompose

```bash
mvn fractalx:decompose
```

```
[INFO] FractalX 1.0.0-SNAPSHOT -- starting decomposition
[INFO] Phase 1: Parsing 22 source files ...
[INFO] Phase 2: Detected 2 modules, 1 cross-module dependency
[INFO] Generating order-service   (port 8081) ...
[INFO] Generating payment-service (port 8082) ...
[INFO] Generating fractalx-gateway, fractalx-registry, admin-service, logger-service
[INFO] Generating docker-compose.yml, start-all.sh
[INFO] Done in 0.61s
```

### Step 5 -- Start everything

```bash
cd fractalx-output
./start-all.sh
```

In under a minute you have:

| Service | URL |
|---|---|
| order-service | http://localhost:8081 |
| payment-service | http://localhost:8082 |
| API Gateway | http://localhost:9999 |
| Service Registry | http://localhost:8761 |
| Admin Dashboard | http://localhost:9090 |
| Jaeger Traces | http://localhost:16686 |

---

## 2. How It Works

FractalX is a **build-time static analyzer + code generator**. It reads your monolith's source tree with JavaParser, identifies bounded contexts from `@DecomposableModule` annotations, and generates everything needed to run those contexts as independent Spring Boot services.

### Two-phase AST analysis

```
Phase 1: Parse all .java files into a Map<Path, CompilationUnit>
Phase 2: For each @DecomposableModule, identify its package prefix,
         scan all files in that prefix, collect imports that reference
         another module's package -- those become cross-module calls
```

Cross-module dependencies are detected **automatically** from Spring field injection and import statements -- no explicit declaration required.

### Generation pipeline (per service)

```
PomGenerator                 -> pom.xml (netscope-server, netscope-client, resilience4j)
ApplicationGenerator         -> Main class (@EnableNetScopeServer, @EnableNetScopeClient)
ConfigurationGenerator       -> application.yml + application-dev.yml + application-docker.yml
CodeCopier                   -> copy source files into the new project
CodeTransformer              -> AST rewrites (package, imports, cross-boundary FK decoupling)
FileCleanupStep              -> remove cross-boundary types from each service
NetScopeServerAnnotationStep -> @NetworkPublic on methods called by other modules
NetScopeClientGenerator      -> @NetScopeClient interfaces for each dependency
DistributedServiceHelper     -> DB isolation, Flyway scaffold, outbox, saga support
OtelConfigStep               -> OtelConfig.java (OTLP/gRPC -> Jaeger)
HealthMetricsStep            -> ServiceHealthConfig.java (TCP HealthIndicator per dep)
ServiceRegistrationStep      -> self-registration with fractalx-registry
NetScopeRegistryBridgeStep   -> dynamic gRPC host resolution from registry
ResilienceConfigStep         -> Resilience4j CircuitBreaker + Retry + TimeLimiter per dep
```

Then, after all services are generated:

```
GatewayGenerator           -> fractalx-gateway (Spring Cloud Gateway + security + CORS + metrics)
RegistryServiceGenerator   -> fractalx-registry (lightweight REST-based service registry)
AdminServiceGenerator      -> admin-service (14-section ops dashboard)
LoggerServiceGenerator     -> logger-service (structured log ingestion + query)
SagaOrchestratorGenerator  -> fractalx-saga-orchestrator (when @DistributedSaga is found)
DockerComposeGenerator     -> docker-compose.yml + multi-stage Dockerfiles
```

### System architecture (generated)

```
+---------------------------------------------------------------------+
|                        Your Monolith                                |
|  @DecomposableModule(serviceName="order-service",   port=8081)      |
|  @DecomposableModule(serviceName="payment-service", port=8082)      |
+---------------------------+-----------------------------------------+
                            |  mvn fractalx:decompose
                            v
+---------------------------------------------------------------------+
|                    FractalX Code Generator                          |
|  ModuleAnalyzer -> ServiceGenerator pipeline -> GatewayGenerator   |
+---------------------------+-----------------------------------------+
                            |  Generated output
                            v
+----------------------------------------------------------------------------+
|  fractalx-registry :8761  <- all services self-register on startup         |
|                                                                            |
|  fractalx-gateway  :9999  <- pulls live routes from registry              |
|    +-- /api/orders/**    -> order-service   :8081                          |
|    +-- /api/payments/**  -> payment-service :8082                          |
|    +-- TracingFilter  (X-Correlation-Id, W3C traceparent)                  |
|    +-- RateLimiterFilter (sliding-window, in-memory)                       |
|    +-- GatewayMetricsFilter (gateway.requests.total / duration)            |
|                                                                            |
|  order-service   :8081  --[NetScope gRPC :18081]--> payment-service :8082  |
|    +-- OtelConfig.java          (OTLP/gRPC -> Jaeger :4317)                |
|    +-- ServiceHealthConfig.java (TCP HealthIndicator per gRPC dep)         |
|    +-- Resilience4j   (CircuitBreaker + Retry + TimeLimiter per dep)       |
|                                                                            |
|  logger-service  :9099  <- structured log ingest (correlationId, spanId)  |
|  jaeger          :16686 / :4317  <- distributed trace store (OTLP)        |
|                                                                            |
|  admin-service   :9090  <- Observability, Alerts, Traces, Logs, Config    |
+----------------------------------------------------------------------------+
```

### Inter-service communication -- NetScope

FractalX uses **NetScope** (gRPC-based) for inter-service communication. The gRPC port is always `HTTP port + 10000`:

| Service | HTTP | gRPC |
|---|---|---|
| order-service | :8081 | :18081 |
| payment-service | :8082 | :18082 |

**Server side** -- FractalX detects which bean methods are called by other modules and adds `@NetworkPublic` to them:

```java
// Generated in payment-service -- auto-detected from order-service dependency
@NetworkPublic
public PaymentResult charge(Long customerId, BigDecimal amount) { ... }
```

**Client side** -- FractalX generates a `@NetScopeClient` interface in order-service:

```java
@NetScopeClient(server = "payment-service", beanName = "paymentService")
public interface PaymentServiceClient {
    PaymentResult charge(Long customerId, BigDecimal amount);
}
```

The host is resolved dynamically from `fractalx-registry` at startup -- no hardcoded hostnames.

---

## 3. Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| Java | 17+ | Required |
| Maven | 3.8+ | Required |
| Git | any | Required |
| Docker + Docker Compose | 24+ | Optional -- needed only for container deployment |

---

## 4. Build the Framework

Clone and install all framework modules:

```bash
git clone https://github.com/Project-FractalX/FractalX.git
cd FractalX/fractalx-parent
mvn clean install -DskipTests
```

This installs into your local Maven repository:

| Module | Artifact | Purpose |
|---|---|---|
| `fractalx-annotations` | Annotations jar | `@DecomposableModule`, `@ServiceBoundary`, `@AdminEnabled`, `@DistributedSaga` |
| `fractalx-core` | Core library | AST analyzer + all 30+ code generators |
| `fractalx-maven-plugin` | Maven plugin | `mvn fractalx:decompose` and all plugin goals |
| `fractalx-runtime` | Runtime jar | `FractalServiceRegistry`, `FractalLogAppender`, `TraceContextPropagator` |

---

## 5. Annotate Your Monolith

### `@DecomposableModule` -- mark a bounded context

Place one marker class per module. FractalX uses it as the root of that service's package tree.

```java
@DecomposableModule(
    serviceName           = "order-service",
    port                  = 8081,
    independentDeployment = true,           // generates its own Dockerfile
    ownedSchemas          = {"orders", "order_items"}
)
public class OrderModule {
    // Declare cross-module dependencies as fields.
    // FractalX reads these field types to build the dependency graph.
    private PaymentService paymentService;
}
```

| Attribute | Type | Required | Description |
|---|---|---|---|
| `serviceName` | `String` | Yes | Generated service name and Docker container name |
| `port` | `int` | Yes | HTTP port (1-65535); gRPC port = this + 10000 |
| `independentDeployment` | `boolean` | No | `true` to generate a Dockerfile for this service |
| `ownedSchemas` | `String[]` | No | Database schemas this service owns (Flyway + `@EntityScan`) |

### `@ServiceBoundary` -- expose a method over gRPC

FractalX adds `@NetworkPublic` automatically to methods it detects as cross-module. Use `@ServiceBoundary` to mark methods explicitly when you want to be certain they are exposed.

```java
@ServiceBoundary
public boolean isConfirmed(Long orderId) {
    return repository.existsByIdAndStatus(orderId, CONFIRMED);
}

// This method stays internal -- not exposed to other services.
public void recalculateTotal(Order order) { ... }
```

### `@DistributedSaga` -- generate saga orchestration

FractalX detects methods annotated with `@DistributedSaga`, maps their cross-service call sequence, and generates a complete `fractalx-saga-orchestrator` service with forward steps and automatic compensation.

```java
@DistributedSaga(
    sagaId       = "checkout",
    compensation = "cancelCheckout"
)
public void processCheckout(Long orderId, Long userId) {
    orderService.confirm(orderId);
    paymentService.charge(userId, orderService.getTotal(orderId));
    inventoryService.reserve(orderId);
}

public void cancelCheckout(Long orderId, Long userId) {
    paymentService.refund(userId);
    orderService.cancel(orderId);
}
```

### `@AdminEnabled` -- opt a service into extended admin controls

Applied to a module class. The admin dashboard shows extended metrics and controls for this service.

### Cross-module dependency detection

FractalX detects dependencies two ways -- use either, or both:

**Explicit** (module marker fields):
```java
@DecomposableModule(serviceName = "leave-service", port = 8083)
public class LeaveModule {
    private EmployeeService employeeService;
    private DepartmentService departmentService;
}
```

**Implicit** (import analysis -- FractalX scans all `.java` files in the module's package):
```java
// LeaveService.java -- FractalX finds the cross-module imports automatically
import com.myapp.employee.EmployeeService;
import com.myapp.department.DepartmentService;
```

---

## 6. Pre-Decomposition Configuration

Create `fractalx-config.yml` in `src/main/resources/` alongside your `application.yml` **before** running `mvn fractalx:decompose`. FractalX reads this file at generation time and bakes the values directly into all generated services -- no manual editing of generated files is required.

```yaml
fractalx:
  registry:
    url: http://my-registry-host:8761        # baked into FRACTALX_REGISTRY_URL fallback
  logger:
    url: http://my-logger-host:9099          # baked into FRACTALX_LOGGER_URL fallback
  otel:
    endpoint: http://my-jaeger-host:4317     # baked into OTEL_EXPORTER_OTLP_ENDPOINT fallback
  gateway:
    port: 9999
    cors:
      allowed-origins: "http://myapp.com,http://localhost:3000"
    security:
      oauth2:
        jwk-set-uri: http://auth-host:8080/realms/fractalx/protocol/openid-connect/certs
  admin:
    port: 9090
    datasource:
      url: jdbc:mysql://localhost:3306/admin_db
      username: admin_user
      password: secret
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

All keys are optional. When absent, FractalX falls back to sensible defaults: registry `http://localhost:8761`, logger `http://localhost:9099`, Jaeger `http://localhost:4317`. The source monolith's `application.yml` and `application.properties` are also consulted as a secondary fallback.

After generation, the admin portal exposes the baked-in platform config at `GET /api/config/runtime` and allows in-memory overrides (`PUT /api/config/runtime/{key}`) without restarting.

---

## 7. Maven Plugin Reference

Every goal renders the **FRACTALX** ASCII banner. The interactive menu is the recommended entry point.

### Common flags

| Flag | Default | Description |
|---|---|---|
| `-Dfractalx.outputDirectory` | `./fractalx-output` | Output directory for generated services |
| `-Dfractalx.color=false` | `true` | Disable ANSI colors (auto-disabled on non-TTY) |

---

### `fractalx:menu` -- Interactive TUI

Full-screen interactive menu. Use arrow keys to navigate, Enter to run.

```bash
mvn fractalx:menu
```

| Key | Action |
|---|---|
| `Up` / `Down` or `k` / `j` | Navigate |
| `Enter` | Run selected goal |
| `q`, `Ctrl-C`, `ESC` | Quit |

> On dumb terminals (no ANSI) a numbered list is shown instead.

---

### `fractalx:decompose` -- Decompose the monolith

```bash
# Full decomposition (default)
mvn fractalx:decompose

# Analysis only, skip file generation
mvn fractalx:decompose -Dfractalx.generate=false

# Custom output directory
mvn fractalx:decompose -Dfractalx.outputDirectory=/path/to/output
```

---

### `fractalx:verify` -- Verify generated output

Multi-level static analysis on generated services -- no need to start them.

```bash
mvn fractalx:verify
mvn fractalx:verify -Dfractalx.verify.compile=true          # include mvn compile per service
mvn fractalx:verify -Dfractalx.verify.failBuild=true        # fail build on any finding
mvn fractalx:verify -Dfractalx.verify.smokeTests=true       # generate Spring context tests
```

| Level | Checks |
|---|---|
| 1 | Structural -- required files, package structure, Dockerfiles, docker-compose |
| 2 | NetScope compatibility -- `@NetScopeClient` interfaces match `@NetworkPublic` server methods |
| 3 | Port conflicts, cross-boundary imports, REST API conventions |
| 4 | Dependency graph acyclicity, SQL schema validity, `@Transactional` on GET, secret detection, config coverage |
| 5 (opt-in) | Compilation -- `mvn compile` on every generated service |
| 6 (opt-in) | Smoke test generation -- Spring context load test per service |

---

### Service lifecycle goals

| Goal | Description |
|---|---|
| `fractalx:services` | List generated services with ports and Docker status |
| `fractalx:ps` | Port-based check of which services are currently running |
| `fractalx:start [-Dfractalx.service=<name>]` | Start all or a named service |
| `fractalx:stop [-Dfractalx.service=<name>]` | Stop all or a named service |
| `fractalx:restart [-Dfractalx.service=<name>]` | Stop then start |

---

### Typical workflow

```bash
mvn fractalx:decompose                                   # 1. Generate
mvn fractalx:verify                                      # 2. Verify
mvn fractalx:services                                    # 3. Inspect
mvn fractalx:start                                       # 4. Start all
mvn fractalx:ps                                          # 5. Check running
mvn fractalx:restart -Dfractalx.service=order-service   # 6. Hot-reload one service
mvn fractalx:stop                                        # 7. Stop all
```

---

## 8. Generated Output

```
fractalx-output/
+-- fractalx-registry/              # Service discovery registry -- start first
+-- order-service/
|   +-- pom.xml                     # netscope-server, netscope-client, resilience4j deps
|   +-- Dockerfile                  # Multi-stage build
|   +-- src/main/
|       +-- java/
|       |   +-- com/example/order/  # Copied + AST-transformed source
|       |   +-- org/fractalx/generated/orderservice/
|       |       +-- OtelConfig.java              # OTLP/gRPC -> Jaeger
|       |       +-- ServiceHealthConfig.java     # TCP HealthIndicator per gRPC dep
|       +-- resources/
|           +-- application.yml         # Base config
|           +-- application-dev.yml     # Localhost defaults, H2 DB
|           +-- application-docker.yml  # Full env-var substitution
+-- payment-service/
|   +-- src/main/java/com/example/payment/
|       +-- PaymentServiceClient.java    # @NetScopeClient interface (auto-generated)
+-- fractalx-gateway/               # Spring Cloud Gateway :9999
+-- logger-service/                 # Log ingestion + query :9099
+-- admin-service/                  # Operations dashboard :9090
|   +-- src/main/java/org/fractalx/admin/
|       +-- observability/          # AlertEvaluator, AlertChannels (SSE+Webhook+Email+Slack)
|       +-- topology/               # TopologyController, NetworkMapController
|       +-- config/                 # RuntimeConfigController (live config diff + override)
+-- fractalx-saga-orchestrator/     # (generated when @DistributedSaga is detected) :8099
+-- docker-compose.yml              # All services + Jaeger, logger, OTEL env vars
+-- start-all.sh                    # Ordered startup: registry -> services -> gateway -> admin
+-- stop-all.sh
```

### What every generated microservice includes

| Component | Description |
|---|---|
| `pom.xml` | Spring Boot 3.2, netscope-server, netscope-client, Resilience4j, Flyway, Actuator, Micrometer |
| Main class | `@EnableNetScopeServer` + `@EnableNetScopeClient` |
| `application.yml` | Registry URL, gRPC port, OTEL endpoint, logger URL (with env-var overrides) |
| `application-dev.yml` | Localhost defaults, H2 in-memory DB |
| `application-docker.yml` | Full Docker DNS hostnames via env vars |
| `OtelConfig.java` | OpenTelemetry SDK -- OTLP/gRPC to Jaeger, W3C traceparent propagation |
| `ServiceHealthConfig.java` | One Spring `HealthIndicator` + Micrometer gauge per gRPC dependency |
| `ServiceRegistrationAutoConfig` | Self-register on startup; heartbeat every 30s; deregister on shutdown |
| `NetScopeRegistryBridgeStep` | Dynamic gRPC host resolution from registry at startup |
| Resilience4j YAML | CircuitBreaker + Retry + TimeLimiter per dependency |
| `Dockerfile` | Multi-stage (build + runtime), non-root user |
| `@NetScopeClient` interfaces | One per outgoing gRPC dependency |

---

## 9. Start Generated Services

### One command (recommended)

```bash
cd fractalx-output
./start-all.sh
```

The script starts the registry first, waits for it to be healthy, then starts all services in dependency order. Logs go to `<service-name>.log`.

### Manual startup order

Services must start **after** the registry:

```bash
# Terminal 1 -- start first
cd fractalx-registry && mvn spring-boot:run

# Terminals 2, 3, ... -- domain services
cd order-service   && mvn spring-boot:run
cd payment-service && mvn spring-boot:run

# Gateway and support services -- any time after registry
cd fractalx-gateway && mvn spring-boot:run
cd logger-service   && mvn spring-boot:run
cd admin-service    && mvn spring-boot:run
```

### Default port map

| Service | HTTP | gRPC | Role |
|---|---|---|---|
| `fractalx-registry` | 8761 | -- | Service discovery |
| Domain service 1 | 8081 | 18081 | First generated microservice |
| Domain service 2 | 8082 | 18082 | Second generated microservice |
| Domain service N | 808N | 1808N | N-th generated microservice |
| `fractalx-gateway` | 9999 | -- | API gateway (single entry point) |
| `logger-service` | 9099 | -- | Centralized log ingestion |
| `admin-service` | 9090 | -- | Operations dashboard |
| `fractalx-saga-orchestrator` | 8099 | -- | Saga orchestration (if applicable) |
| Jaeger UI | 16686 | 4317 | Distributed trace store |

```bash
./stop-all.sh
```

---

## 10. Docker Deployment

```bash
cd fractalx-output
docker compose up --build
```

Services start in dependency order: Jaeger -> logger-service -> registry -> microservices -> gateway -> admin.

Each container receives these environment variables automatically:

| Variable | Value in Docker |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `docker` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://jaeger:4317` |
| `OTEL_SERVICE_NAME` | Service name |
| `FRACTALX_LOGGER_URL` | `http://logger-service:9099/api/logs` |
| `FRACTALX_REGISTRY_URL` | `http://fractalx-registry:8761` |

Run a single service locally against containerised peers:

```bash
SPRING_PROFILES_ACTIVE=docker \
FRACTALX_REGISTRY_URL=http://localhost:8761 \
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 \
mvn spring-boot:run
```

---

## 11. API Gateway

The gateway runs at **http://localhost:9999** and is the single entry point for all services.

Routes are resolved **dynamically** from `fractalx-registry` at startup and refreshed periodically. A static YAML fallback is baked in for resilience.

### Per-request filter chain

| Order | Filter | Purpose |
|---|---|---|
| -100 | `RequestLoggingFilter` | Logs method, path, status, duration, correlationId |
| -99 | `TracingFilter` | Propagates `X-Correlation-Id` and W3C `traceparent` |
| -98 | `GatewayMetricsFilter` | `gateway.requests.total{service,method,status}` counter + duration timer |
| -95 | `GatewayApiKeyFilter` | API key validation (disabled by default) |
| -90 | `GatewayBearerJwtFilter` | HMAC-SHA256 JWT validation (disabled by default) |
| -85 | `GatewayBasicAuthFilter` | Basic auth (disabled by default) |
| -80 | `GatewayRateLimiterFilter` | Sliding-window in-memory rate limiter |

### Per-route filters

- **Circuit Breaker** -- opens after sustained failures; returns `503` JSON from `GatewayFallbackController`
- **Retry** -- 2 retries on `502`, `503`, `504`
- **Rate Limiter** -- 100 req/s per IP per service (configurable)

---

## 12. Service Discovery

`fractalx-registry` is a lightweight REST-based service registry generated alongside your services -- no Eureka or Consul dependency required.

### Registry API

| Method | Path | Description |
|---|---|---|
| `POST` | `/services` | Register a service instance |
| `GET` | `/services` | List all registered services |
| `GET` | `/services/{name}` | Get a specific service |
| `POST` | `/services/{name}/heartbeat` | Keep-alive ping |
| `DELETE` | `/services/{name}/deregister` | Deregister |
| `GET` | `/services/health` | Registry health summary |

### Registration lifecycle

Each generated service includes `ServiceRegistrationAutoConfig`:

1. **On startup** -- POST `{name, host, port, grpcPort, healthUrl}` to the registry
2. **Every 30 seconds** -- heartbeat to refresh `lastSeen`
3. **On shutdown** -- deregister gracefully

The registry polls each service's `/actuator/health` every 15 seconds and evicts services unresponsive for 90 seconds.

---

## 13. Gateway Security

All mechanisms are **disabled by default**. Enable selectively in `fractalx-gateway/src/main/resources/application.yml`.

| Mechanism | Enable flag | Description |
|---|---|---|
| Bearer JWT | `fractalx.gateway.security.bearer.enabled: true` | HMAC-SHA256 signed tokens |
| OAuth2 | `fractalx.gateway.security.oauth2.enabled: true` | External IdP via JWK Set URI |
| Basic Auth | `fractalx.gateway.security.basic.enabled: true` | `Authorization: Basic` header |
| API Key | `fractalx.gateway.security.api-key.enabled: true` | `X-Api-Key` header or `?api_key=` param |

<details>
<summary>Full security configuration</summary>

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

    cors:
      allowed-origins: ${CORS_ORIGINS:http://localhost:3000,http://localhost:4200}
      allowed-methods: GET,POST,PUT,DELETE,PATCH,OPTIONS
      allow-credentials: true

    rate-limit:
      default-rps: 100
      per-service:
        payment-service: 50
        order-service: 200
```

</details>

On successful authentication, the filter injects downstream headers: `X-User-Id`, `X-User-Roles`, `X-Auth-Method`.

---

## 14. Resilience

### Per-service (generated in each microservice)

Every service gets Resilience4j configuration for each of its gRPC dependencies:

| Pattern | Default |
|---|---|
| CircuitBreaker | 50% failure threshold, 30s wait in OPEN, 5 calls in HALF_OPEN |
| Retry | 3 attempts, 100ms base delay, exponential backoff (x2) |
| TimeLimiter | 2s timeout per gRPC call |

```yaml
# Override per dependency in application.yml
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

### Gateway-level

Each route has a named circuit breaker. When open, the gateway returns `503 Service Unavailable` JSON rather than hanging.

---

## 15. Data Consistency

FractalX generates a complete data isolation and consistency stack for each service:

| Concern | Generated component |
|---|---|
| DB per service | `DataIsolationGenerator` -- dual-package `@EntityScan` + `@EnableJpaRepositories` |
| DB configuration | `DbConfigurationGenerator` -- reads `fractalx-config.yml -> fractalx.services.<name>` |
| Schema migration | `FlywayMigrationGenerator` -- `V1__init.sql` scaffold + outbox table |
| Transactional outbox | `OutboxEvent`, `OutboxRepository`, `OutboxPublisher`, `OutboxPoller` |
| Cross-service FK validation | `ReferenceValidator` -- uses NetScope `exists()` calls instead of DB joins |
| Relationship decoupling | `RelationshipDecoupler` -- AST rewrites `@ManyToOne ForeignEntity` to `String foreignEntityId` |

### Configuring production databases

```yaml
# fractalx-config.yml
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

### Distributed sagas

When `@DistributedSaga` is detected, FractalX generates `fractalx-saga-orchestrator` (HTTP :8099):

- One `<SagaId>SagaService` per `@DistributedSaga` method with forward steps and compensation logic
- `SagaInstance` JPA entity tracking per-execution state: `STARTED -> IN_PROGRESS -> DONE` (or `COMPENSATING -> FAILED`)
- `@NetScopeClient` interfaces for every service called in the saga
- REST: `POST /saga/{sagaId}/start`, `GET /saga/status/{correlationId}`, `GET /saga`

---

## 16. Observability & Monitoring

FractalX generates a complete observability stack automatically -- no instrumentation code required.

### Distributed tracing -- OpenTelemetry + Jaeger

Each service gets an `OtelConfig.java` that configures the OpenTelemetry SDK:

- Exports spans via **OTLP/gRPC** to Jaeger (port 4317)
- Propagates **W3C `traceparent`** and **Baggage** across all service calls
- Tags every span with `service.name`
- Uses `BatchSpanProcessor` for non-blocking export

Jaeger UI: **http://localhost:16686**

### Correlation ID propagation

```
Client -> Gateway (assigns X-Correlation-Id) -> order-service -> payment-service
                                                     |                |
                                               logger-service   logger-service
```

The same ID flows through MDC (SLF4J), gRPC metadata, W3C `traceparent`, and all log entries.

### Service health metrics

For every service with gRPC dependencies, `ServiceHealthConfig.java` provides:

- One Spring `HealthIndicator` per NetScope peer (2s TCP connect to gRPC port)
- Micrometer gauge `fractalx.service.dependency.up{service=<name>}` (1.0 = UP, 0.0 = DOWN)
- Exposed via `/actuator/health` and `/actuator/metrics`

### Centralized logger service

`logger-service` (port 9099) receives structured logs from all services via `FractalLogAppender`:

| Endpoint | Description |
|---|---|
| `POST /api/logs` | Ingest a log entry |
| `GET /api/logs?correlationId=&service=&level=&page=&size=` | Paginated log search |
| `GET /api/logs/services` | Distinct service names that have sent logs |
| `GET /api/logs/stats` | Per-service log count and error rate |

### Alert system

`admin-service` evaluates alert rules every 30 seconds:

| Rule | Condition | Threshold | Severity |
|---|---|---|---|
| `service-down` | health != UP | 2 consecutive failures | CRITICAL |
| `high-response-time` | p99 response time | > 2000 ms (3 consecutive) | WARNING |
| `error-rate` | HTTP error rate | > 10% (3 consecutive) | WARNING |

**Notification channels:**

| Channel | Default | Config key |
|---|---|---|
| Admin UI (SSE) | Enabled | -- |
| Webhook | Disabled | `ALERT_WEBHOOK_URL` |
| Email (SMTP) | Disabled | `SMTP_HOST`, `SMTP_FROM`, `ALERT_EMAIL_TO` |
| Slack | Disabled | `SLACK_WEBHOOK_URL` |

<details>
<summary>Full alerting.yml reference</summary>

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

</details>

All generated services expose `/actuator/prometheus` for Prometheus scraping.

---

## 17. Admin Dashboard

Access at **http://localhost:9090** (default credentials: `admin / admin`).

### Dashboard sections

| Group | Section | What you get |
|---|---|---|
| Overview | **Overview** | 6 KPI cards + service health table + recent incidents + activity timeline |
| Overview | **Services** | All registered services from registry with health indicators |
| Architecture | **Communication** | NetScope gRPC connection graph; upstream/downstream per service |
| Architecture | **Data Consistency** | Saga state, outbox backlog, cross-service FK validation |
| Architecture | **Network Map** | Canvas force-directed live service mesh with health overlay |
| Monitoring | **Observability** | Per-service metrics: health, p99, error rate, uptime % |
| Monitoring | **Alerts** | Active alert table (one-click resolve) + full history + SSE feed |
| Monitoring | **Traces** | Search by Correlation ID or service; links to Jaeger UI |
| Monitoring | **Logs** | Paginated log viewer: correlationId, service, level filters |
| Monitoring | **Analytics** | Historical charts: RPS trends, CPU/memory, error rates |
| Developer | **API Explorer** | 3-panel: service selector, endpoint list, request/response with auth presets |
| Developer | **gRPC Browser** | Services with gRPC ports, TCP ping tool (latency), dep map |
| Developer | **Config Editor** | Per-service env var table, in-memory override, hot-reload |
| Operations | **Incidents** | P1-P4 severity lifecycle: OPEN -> INVESTIGATING -> RESOLVED |
| Operations | **Settings** | Admin user management, site settings, theme |

### Database persistence (admin service)

By default the admin service uses H2 in-memory. To persist users and settings:

```yaml
# fractalx-config.yml -- bake credentials before decomposition
fractalx:
  admin:
    datasource:
      url: jdbc:mysql://localhost:3306/admin_db?useSSL=false&serverTimezone=UTC
      username: admin_user
      password: secret
```

Then activate the `db` profile:

```bash
cd fractalx-output/admin-service
mvn spring-boot:run -Dspring-boot.run.profiles=db
```

| Database | JDBC URL pattern |
|---|---|
| H2 (embedded, zero config) | `jdbc:h2:./admin-service;DB_CLOSE_DELAY=-1;MODE=MySQL` |
| MySQL 8+ | `jdbc:mysql://localhost:3306/admin_db?useSSL=false&serverTimezone=UTC` |
| PostgreSQL 15+ | `jdbc:postgresql://localhost:5432/admin_db` |

<details>
<summary>Full Admin REST API reference</summary>

#### Topology & health

| Method | Path | Description |
|---|---|---|
| `GET` | `/dashboard` | HTML dashboard |
| `GET` | `/api/topology` | Service dependency graph (nodes + edges) |
| `GET` | `/api/health/summary` | Live health status map |
| `GET` | `/api/services/all` | All registered services (registry proxy) |

#### Observability

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/observability/metrics` | Per-service snapshot: health, p99, error rate, uptime |
| `GET` | `/api/traces?correlationId=&service=&limit=` | Jaeger proxy -- search traces |
| `GET` | `/api/traces/{traceId}` | Jaeger proxy -- single trace |
| `GET` | `/api/logs?correlationId=&service=&level=&page=&size=` | Logger-service proxy |
| `GET` | `/api/analytics/overview` | Platform-wide analytics |
| `GET` | `/api/analytics/service/{name}` | Historical metrics for one service |

#### Alerts

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/alerts?page=&size=&severity=&service=` | Paginated alert history |
| `GET` | `/api/alerts/active` | Unresolved alerts |
| `POST` | `/api/alerts/{id}/resolve` | Manually resolve |
| `GET` | `/api/alerts/stream` | SSE real-time feed |
| `GET` | `/api/alerts/config` | Current alert rules |
| `PUT` | `/api/alerts/config/rules` | Update alert rules |

#### Configuration

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/config/services` | All service configurations |
| `GET` | `/api/config/runtime` | Platform config + current overrides + effective values |
| `PUT` | `/api/config/runtime/{key}` | In-memory override `{"value":"..."}` |
| `DELETE` | `/api/config/runtime/{key}` | Revert to generation-time default |
| `GET` | `/api/config/runtime/diff` | Keys whose effective value differs from default |
| `POST` | `/api/config/editor/reload/{service}` | Trigger `/actuator/refresh` on a service |

#### gRPC Browser

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/grpc/services` | Services with active gRPC ports |
| `POST` | `/api/grpc/ping` | TCP probe `{host, port}` -> `{reachable, latencyMs}` |
| `GET` | `/api/grpc/{service}/deps` | Upstream/downstream gRPC deps |

#### Incidents

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/incidents` | All incidents, sorted by created desc |
| `POST` | `/api/incidents` | Create `{title, description, severity, affectedService}` |
| `PUT` | `/api/incidents/{id}/status` | Update `{status, notes}` |
| `DELETE` | `/api/incidents/{id}` | Delete |

#### Users

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/users` | List all admin users |
| `POST` | `/api/users` | Create a user |
| `PUT` | `/api/users/{id}` | Update a user |
| `DELETE` | `/api/users/{id}` | Delete a user |

#### Topology response example

```json
{
  "nodes": [
    { "id": "order-service",     "port": 8081, "type": "microservice" },
    { "id": "payment-service",   "port": 8082, "type": "microservice" },
    { "id": "fractalx-gateway",  "port": 9999, "type": "gateway" },
    { "id": "fractalx-registry", "port": 8761, "type": "registry" }
  ],
  "edges": [
    { "source": "order-service", "target": "payment-service", "protocol": "grpc" }
  ]
}
```

</details>

---

## 18. Static Verification

```bash
cd your-monolith
mvn fractalx:verify

# One-shot: decompose + full verification
mvn fractalx:decompose fractalx:verify \
    -Dfractalx.verify.compile=true \
    -Dfractalx.verify.failBuild=true
```

Results are printed with `[PASS]` / `[FAIL]` / `[WARN]` markers. See [Maven Plugin Reference](#7-maven-plugin-reference) for level details.

---

## 19. Test Endpoints

### Health checks

```bash
# Registry
curl http://localhost:8761/services/health

# Via gateway
curl http://localhost:9999/api/orders/actuator/health

# Direct (shows dependency health indicators from ServiceHealthConfig)
curl http://localhost:8081/actuator/health

# Prometheus metrics
curl http://localhost:8081/actuator/prometheus
```

### Correlation ID tracing

```bash
# All gateway responses include X-Correlation-Id -- or pass your own
curl http://localhost:9999/api/orders \
    -H "X-Correlation-Id: my-trace-id"

# Query logs for that ID
curl "http://localhost:9099/api/logs?correlationId=my-trace-id" | jq .

# Search traces in admin
curl "http://localhost:9090/api/traces?correlationId=my-trace-id" | jq .
```

### Alerts

```bash
curl http://localhost:9090/api/alerts/active | jq .
curl -X POST http://localhost:9090/api/alerts/<id>/resolve

# Real-time SSE alert stream
curl -N http://localhost:9090/api/alerts/stream
```

### Example business requests

```bash
# Process a payment
curl -X POST http://localhost:9999/api/payments/process \
    -H "Content-Type: application/json" \
    -d '{"customerId":"CUST001","amount":100.50}'

# Create an order (calls payment-service via NetScope gRPC internally)
curl -X POST http://localhost:9999/api/orders \
    -H "Content-Type: application/json" \
    -d '{"customerId":"CUST001","amount":100.50}'

# With API key (if enabled)
curl -X POST http://localhost:9999/api/orders \
    -H "Content-Type: application/json" \
    -H "X-Api-Key: my-api-key" \
    -d '{"customerId":"CUST001","amount":100.50}'

# With Bearer JWT (if enabled)
curl -X POST http://localhost:9999/api/orders \
    -H "Authorization: Bearer <token>" \
    -H "Content-Type: application/json" \
    -d '{"customerId":"CUST001","amount":100.50}'
```

---

## 20. Configuration Reference

### Environment variables -- all generated services

| Variable | Default | Description |
|---|---|---|
| `FRACTALX_REGISTRY_URL` | `http://localhost:8761` | Registry base URL |
| `FRACTALX_REGISTRY_HOST` | `localhost` | This service's advertised host |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` or `docker` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | OpenTelemetry collector endpoint |
| `OTEL_SERVICE_NAME` | service name | Span service name tag |
| `FRACTALX_LOGGER_URL` | `http://localhost:9099/api/logs` | Centralized logger ingest URL |
| `JAEGER_QUERY_URL` | `http://localhost:16686` | Jaeger query API (admin service) |

### Gateway variables

| Variable | Default | Description |
|---|---|---|
| `GATEWAY_SECURITY_ENABLED` | `false` | Enable gateway authentication |
| `JWT_SECRET` | dev default | HMAC secret for Bearer JWT (min 32 chars) |
| `OAUTH2_JWK_URI` | Keycloak default | JWK Set URI for OAuth2 |
| `CORS_ORIGINS` | `http://localhost:3000,...` | Allowed CORS origins |
| `GATEWAY_DEFAULT_RPS` | `100` | Default rate limit (req/s per IP per service) |

### Alert notification variables

| Variable | Description |
|---|---|
| `ALERT_WEBHOOK_URL` | Webhook URL for alert notifications |
| `SLACK_WEBHOOK_URL` | Slack incoming webhook URL |
| `SMTP_HOST` | SMTP server for email alerts |
| `SMTP_PORT` | SMTP port (default: `587`) |
| `SMTP_FROM` | Sender email address |
| `ALERT_EMAIL_TO` | Recipient email address |

### Admin service database variables

| Variable | Default | Description |
|---|---|---|
| `ADMIN_DB_URL` | H2 embedded | JDBC URL (only with `db` profile) |
| `ADMIN_DB_USERNAME` | `sa` | Database username |
| `ADMIN_DB_PASSWORD` | *(empty)* | Database password |
| `ADMIN_DB_DRIVER` | `org.h2.Driver` | JDBC driver class name |

<details>
<summary>Generated application.yml structure</summary>

```yaml
# application.yml (base -- common to all profiles)
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

`application-dev.yml` -- H2 in-memory DB, localhost for all peers. Active by default.

`application-docker.yml` -- all values from environment variables, Docker DNS hostnames. Activated with `SPRING_PROFILES_ACTIVE=docker`.

</details>

---

## 21. Troubleshooting

### "Could not register with fractalx-registry"

The registry is not yet running. Start `fractalx-registry` first. The `start-all.sh` script handles ordering automatically.

### "Could not resolve X from registry" (NetScope gRPC)

The target service has not registered yet. Verify it started cleanly:

```bash
curl http://localhost:8761/services | jq .
```

Static YAML fallback is used automatically, so calls should continue to work.

### Traces not appearing in Jaeger

Verify Jaeger is running on port 4317:

```bash
docker compose ps jaeger

# Or start standalone
docker run -d --name jaeger \
  -e COLLECTOR_OTLP_ENABLED=true \
  -p 16686:16686 -p 4317:4317 \
  jaegertracing/all-in-one:1.53
```

Verify `OTEL_EXPORTER_OTLP_ENDPOINT` is set correctly in your service.

### Logs not visible in logger service

```bash
curl http://localhost:9099/api/logs/services

# Search by correlation ID from response header
curl "http://localhost:9099/api/logs?correlationId=<id>"
```

Check `FRACTALX_LOGGER_URL` points to the correct host.

### Alert evaluator not firing

Verify `fractalx.alerting.enabled: true` in `alerting.yml`. Reduce interval for testing:

```yaml
fractalx:
  alerting:
    eval-interval-ms: 5000
```

### Gateway returns 503

The circuit breaker has opened. Check `/actuator/health` on the target service. The circuit resets after 30 seconds by default (configurable via `resilience4j.circuitbreaker.instances.<name>.wait-duration-in-open-state`).

### H2 startup error -- `SET FOREIGN_KEY_CHECKS`

Generated schema contains MySQL-specific syntax. Either switch to MySQL in `application-dev.yml`, or disable SQL init:

```yaml
spring.sql.init.enabled: false
```

### Debug tips

```bash
mvn spring-boot:run -e -X                                    # full Maven debug
mvn spring-boot:run -Dspring-boot.run.arguments="--debug"    # Spring condition report
tail -f fractalx-output/order-service.log                    # service log
curl -v http://localhost:9999/api/orders 2>&1 | grep -i "correlation\|trace"
```

---

## 22. Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-change`
3. Build all modules: `cd fractalx-parent && mvn clean install -DskipTests`
4. Test against `fractalx-test-db-app`: `mvn test`
5. Open a pull request with a clear description of the change and why

Please keep changes focused. Each PR should address a single concern.

---

## 23. License

Licensed under the [Apache License 2.0](LICENSE).
