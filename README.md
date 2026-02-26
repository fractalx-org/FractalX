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
15. [Admin Dashboard](#15-admin-dashboard)
16. [Test Endpoints](#16-test-endpoints)
17. [Configuration Reference](#17-configuration-reference)
18. [Troubleshooting](#18-troubleshooting)
19. [Contributing](#19-contributing)
20. [License](#20-license)

---

## 1. Overview

FractalX performs **static decomposition**: it reads your monolith's source code at build time, identifies module boundaries via annotations, and generates fully runnable microservice projects — including inter-service communication, database isolation, distributed sagas, service discovery, an API gateway, and Docker deployment files.

**No runtime agent. No bytecode manipulation. Pure code generation.**

### What Gets Generated

| Artifact | Port | Description |
|----------|------|-------------|
| `<service-name>/` (×N) | Configured | One Spring Boot project per `@DecomposableModule` |
| `fractalx-registry/` | 8761 | Lightweight service registry (self-registration, health polling) |
| `fractalx-gateway/` | 9999 | Spring Cloud Gateway with dynamic routing, auth, rate limiting, CORS |
| `admin-service/` | 9090 | Operations dashboard with live health and service topology |
| `fractalx-saga-orchestrator/` | 8099 | Saga orchestration service (generated when `@DistributedSaga` detected) |
| `docker-compose.yml` | — | Container-ready deployment for the entire mesh |
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
│                                                                            │
│  order-service   :8081  ←──[NetScope gRPC :18081]──► payment-service       │
│  payment-service :8082                                                     │
│                                                                            │
│  admin-service   :9090  ←─ dashboard + topology + live health              │
└────────────────────────────────────────────────────────────────────────────┘
```

### Generation Pipeline (per service)

```
PomGenerator → ApplicationGenerator → ConfigurationGenerator
→ CodeCopier → CodeTransformer (AST rewrites)
→ FileCleanupStep → NetScopeServerAnnotationStep
→ NetScopeClientGenerator → NetScopeClientWiringStep
→ DistributedServiceHelper (DB isolation, Flyway, Outbox, Saga)
→ ServiceRegistrationStep   ← registers with fractalx-registry
→ NetScopeRegistryBridgeStep ← dynamic gRPC host resolution
→ ResilienceConfigStep       ← Resilience4j CB / Retry / TimeLimiter
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
│       ├── java/...            # Copied + transformed source + generated clients
│       └── resources/
│           ├── application.yml         # Base config
│           ├── application-dev.yml     # Localhost defaults
│           └── application-docker.yml  # Full env-var substitution
├── payment-service/
├── fractalx-gateway/           # API Gateway
├── admin-service/              # Operations dashboard
├── fractalx-saga-orchestrator/ # (if @DistributedSaga detected)
├── docker-compose.yml
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

The script starts the registry first, waits for it to be ready, then starts all services, the gateway, and the admin dashboard. Logs are written to `*.log` files.

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

# Terminal 5 — admin dashboard (optional)
cd admin-service && mvn spring-boot:run
```

### Stop everything

```bash
./stop-all.sh
```

---

## 9. Docker Deployment

A `docker-compose.yml` is generated at the root of the output directory.

```bash
cd target/generated-services
docker compose up --build
```

Services start in dependency order: registry → microservices → gateway. Each service container uses the `docker` Spring profile, which resolves all hostnames via Docker DNS (service names as hostnames) instead of localhost.

To run a specific service locally against containerised peers:

```bash
SPRING_PROFILES_ACTIVE=docker \
FRACTALX_REGISTRY_URL=http://localhost:8761 \
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

## 15. Admin Dashboard

The admin service runs at **http://localhost:9090**.

### Endpoints

| Path | Description |
|------|-------------|
| `GET /dashboard` | Thymeleaf HTML dashboard |
| `GET /api/topology` | JSON service dependency graph (nodes + edges) |
| `GET /api/health/summary` | Live health status map for all services |
| `GET /api/services` | Proxies fractalx-registry `/services` |

### Topology response example

```json
{
  "nodes": [
    { "id": "order-service",   "label": "order-service",   "port": 8081, "type": "microservice" },
    { "id": "payment-service", "label": "payment-service", "port": 8082, "type": "microservice" },
    { "id": "fractalx-gateway","label": "API Gateway",     "port": 9999, "type": "gateway" },
    { "id": "fractalx-registry","label": "Service Registry","port": 8761, "type": "registry" }
  ],
  "edges": [
    { "source": "order-service", "target": "payment-service", "protocol": "grpc" }
  ]
}
```

---

## 16. Test Endpoints

### Health checks

```bash
# Registry
curl http://localhost:8761/services/health

# Individual services (via gateway)
curl http://localhost:9999/api/orders/actuator/health
curl http://localhost:9999/api/payments/actuator/health

# Direct
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

### List registered services

```bash
curl http://localhost:8761/services | jq .
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

## 17. Configuration Reference

### `application.yml` (base — common to all profiles)

```yaml
fractalx:
  registry:
    url: ${FRACTALX_REGISTRY_URL:http://localhost:8761}
    enabled: true
    host: ${FRACTALX_REGISTRY_HOST:localhost}

netscope:
  server:
    grpc:
      port: <HTTP_PORT + 10000>
```

### `application-dev.yml` (local development)

Uses H2 in-memory database and localhost for all service addresses. Active by default (`SPRING_PROFILES_ACTIVE=dev`).

### `application-docker.yml` (container deployment)

All values from environment variables. Activated with `SPRING_PROFILES_ACTIVE=docker`.

### Key environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `FRACTALX_REGISTRY_URL` | `http://localhost:8761` | Registry base URL |
| `FRACTALX_REGISTRY_HOST` | `localhost` | This service's advertised host |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` or `docker` |
| `JWT_SECRET` | (dev default) | HMAC secret for Bearer JWT |
| `OAUTH2_JWK_URI` | (Keycloak default) | JWK Set URI for OAuth2 |
| `GATEWAY_SECURITY_ENABLED` | `false` | Enable gateway auth |
| `GATEWAY_BEARER_ENABLED` | `false` | Enable Bearer JWT |
| `GATEWAY_OAUTH2_ENABLED` | `false` | Enable OAuth2 |
| `GATEWAY_BASIC_ENABLED` | `false` | Enable Basic Auth |
| `GATEWAY_APIKEY_ENABLED` | `false` | Enable API Key |
| `CORS_ORIGINS` | `http://localhost:3000,...` | Allowed CORS origins |
| `GATEWAY_DEFAULT_RPS` | `100` | Default rate limit (req/s) |

---

## 18. Troubleshooting

### Services fail to start — "Could not register with fractalx-registry"

The registry is not yet running. Start `fractalx-registry` first, then start other services. The `start-all.sh` script handles this automatically with a 5-second wait.

### NetScope gRPC call fails — "Could not resolve X from registry"

The target service has not yet registered itself. Check that it started without errors and that the registry URL is reachable (`curl http://localhost:8761/services`). Static YAML config is used as fallback so calls should still work.

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

The circuit breaker has opened. Check service health at `/actuator/health`. The circuit resets automatically after 30 seconds (configurable).

### Debug tips

```bash
# Full Maven output
mvn spring-boot:run -e -X

# Spring Boot condition evaluation
mvn spring-boot:run -Dspring-boot.run.arguments="--debug"

# Tail service log (when started via start-all.sh)
tail -f order-service.log
```

---

## 19. Contributing

- Fork the repository
- Create a branch: `git checkout -b feature/my-change`
- Build: `mvn clean install -DskipTests`
- Test against `fractalx-test-db-app`
- Open a pull request with a clear description

---

## 20. License

Licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
