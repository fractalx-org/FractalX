# FractalX Monolith Generator Prompt

> Copy everything below the line and paste into Claude / ChatGPT / etc.
> Replace the `[DOMAIN]` section with a new business domain each iteration.

---

## Role

You are a senior Spring Boot engineer building a production-grade modular monolith. Your output must be a **complete, compilable, runnable** Maven project — no placeholders, no TODOs, no "implement here" stubs. Every class must be fully implemented with real business logic.

## Domain

[DOMAIN — replace this block each iteration. Examples below.]

<!--
  Iteration ideas (pick one per run, or invent your own):

  1. **Hospital Management** — patients, doctors, appointments, prescriptions,
     lab results, billing, insurance claims, pharmacy inventory, ward management
  2. **E-Commerce Marketplace** — sellers, products, inventory, shopping cart,
     orders, payments, shipping, returns/refunds, reviews, coupons, wishlists
  3. **University Portal** — students, professors, courses, enrollment, grades,
     library, fees, scholarships, timetable, exam scheduling, transcript generation
  4. **Fleet Management** — vehicles, drivers, trips, fuel tracking, maintenance
     schedules, GPS tracking, route optimization, toll management, compliance/licensing
  5. **Real Estate Platform** — properties, agents, listings, viewings, offers,
     contracts, mortgage calculator, tenant management, maintenance requests
  6. **Food Delivery** — restaurants, menus, customers, orders, delivery drivers,
     real-time tracking, ratings, promotions, subscription plans, kitchen display
  7. **Banking System** — accounts, customers, transactions, transfers, loans,
     credit scoring, KYC, statements, interest calculation, fraud detection
  8. **Event Ticketing** — events, venues, tickets, seating maps, pricing tiers,
     reservations, payments, QR codes, check-in, waitlists, refund policies
  9. **HR & Payroll** — employees, departments, attendance, leave management,
     payroll calculation, tax deductions, benefits, performance reviews, recruitment
  10. **IoT Device Platform** — devices, telemetry ingestion, alerts, firmware
      updates, device groups, dashboards, rule engine, notification channels
-->

Build a **[chosen domain]** system with at least **6 clearly separable business modules** that each own their own aggregate root, repository, service, and controller. The modules must have **realistic cross-module dependencies** (e.g., order-service calls payment-service and inventory-service).

## Technical Stack — Mandatory

| Requirement | Value |
|---|---|
| Java | 17 |
| Spring Boot | 3.2.x |
| Build | Maven (single-module monolith, one `pom.xml`) |
| Database | H2 in-memory (default profile), PostgreSQL (prod profile) |
| ORM | Spring Data JPA with Hibernate |
| Security | Spring Security 6 with JWT (stateless, `SecurityFilterChain`) |
| Validation | Bean Validation (`jakarta.validation`) on all DTOs and entities |
| API docs | SpringDoc OpenAPI 3 (`/swagger-ui.html`) |
| Observability | Spring Boot Actuator, Micrometer metrics |
| Migration | Liquibase (at least one changeset per module's tables) |
| Testing | JUnit 5 + Mockito unit tests for every service class |

## Architecture Rules — Must Follow Exactly

### Package Structure
```
com.example.[appname]/
├── config/                      ← SecurityConfig, JwtFilter, CorsConfig, OpenApiConfig
├── common/                      ← shared DTOs, exceptions, base entity, audit
├── [module1]/
│   ├── [Module1].java           ← @Entity aggregate root
│   ├── [Module1]Repository.java ← Spring Data JPA
│   ├── [Module1]Service.java    ← business logic, calls other module services
│   ├── [Module1]Controller.java ← @RestController
│   └── dto/                     ← request/response DTOs
├── [module2]/
│   └── ...
└── [moduleN]/
    └── ...
```

### Every Controller Must Have
- Full CRUD: `POST`, `GET` (by ID), `GET` (list with pagination), `PUT`, `PATCH`, `DELETE`
- At least one endpoint with `@RequestBody` + `@Valid`
- At least one endpoint with `@PathVariable`
- At least one endpoint with `@RequestParam` (filtering/search)
- Proper HTTP status codes: 201 for creation, 204 for delete, 404 for not found, 409 for conflict
- Role-based access: some endpoints `@PreAuthorize("hasRole('ADMIN')")`, others `hasRole('USER')`

### Every Entity Must Have
- `@Id` with `@GeneratedValue`
- At least one `@NotBlank` or `@NotNull` field
- An `@Enumerated` status field with a dedicated enum class (e.g., `OrderStatus`, `PaymentStatus`)
- Audit fields: `createdAt`, `updatedAt` (use `@PrePersist` / `@PreUpdate` or a `@MappedSuperclass`)
- At least one relationship annotation (`@ManyToOne`, `@OneToMany`, `@ManyToMany`, or `@OneToOne`)
- `@Version` for optimistic locking on at least 2 entities

### Cross-Module Dependencies — Critical
- At least **3 modules** must directly call methods on other modules' service classes (injected via constructor). For example: `OrderService` calls `PaymentService.processPayment()` and `InventoryService.reserveStock()`
- At least **2 cross-module workflows** must span 3+ modules in a single method call chain (e.g., place order → reserve inventory → process payment → send notification)
- These cross-module methods must be annotated with `@Transactional`

### Multi-Step Business Transactions (Saga Candidates)
- Include at least **2 service methods** that:
  1. Span 3+ module calls in sequence
  2. Have compensating logic (e.g., if payment fails, unreserve inventory)
  3. Modify state in multiple aggregates within the same method
  4. Are annotated with `@Transactional`
- These are the methods FractalX will transform into distributed sagas

### Authentication & Authorization
- `POST /auth/register` — create user with BCrypt-hashed password
- `POST /auth/login` — return JWT access token + refresh token
- `POST /auth/refresh` — issue new access token from refresh token
- JWT filter that extracts roles from claims and sets `SecurityContext`
- At least 3 roles: `ADMIN`, `USER`, `MANAGER` (or domain-appropriate equivalents)
- Method-level security with `@PreAuthorize` on controller methods
- At least one endpoint restricted to `ADMIN` only
- At least one public endpoint (no auth required) — e.g., health check or product catalog browse

### Patterns to Include (All Required)

**Data patterns:**
- Soft delete (boolean `deleted` field + `@Where` or `@SQLRestriction`) on at least one entity
- Composite unique constraint (`@Table(uniqueConstraints = ...)`) on at least one entity
- `@ElementCollection` on at least one entity
- Custom JPQL query with `@Query` in at least 2 repositories
- Specification/Criteria API or querydsl for dynamic filtering in at least 1 service
- Pagination with `Pageable` and `Page<T>` response in every list endpoint

**Service patterns:**
- `@Async` method in at least one service (e.g., sending email notification)
- `@Scheduled` task in at least one service (e.g., expire pending orders, cleanup)
- `@EventListener` / `ApplicationEventPublisher` for at least one domain event
- `@Cacheable` / `@CacheEvict` on at least one service method
- Custom exception classes: `ResourceNotFoundException`, `BusinessRuleException`, `ConflictException`
- Global exception handler with `@RestControllerAdvice` returning structured error response

**API patterns:**
- File upload endpoint (`MultipartFile`) in at least one controller
- Endpoint returning `byte[]` or `Resource` (file download / report export)
- SSE (Server-Sent Events) or WebSocket endpoint for real-time updates
- Rate limiting annotation or filter on at least one endpoint
- API versioning via path prefix (`/api/v1/...`)
- HATEOAS links (`EntityModel` / `CollectionModel`) on at least one resource — optional but preferred

**Configuration:**
- `application.yml` with profiles: `default` (H2), `prod` (PostgreSQL), `test`
- Custom `@ConfigurationProperties` class for app-specific settings
- CORS configuration allowing configurable origins
- Graceful shutdown enabled

### Data Seeding
- Include a `DataInitializer` component (`@Component` implementing `CommandLineRunner`) that:
  - Creates default admin user
  - Seeds at least 10 sample records per module
  - Only runs when a profile/property is active (e.g., `app.seed-data=true`)

### What NOT to Include
- No Spring Cloud dependencies (no Eureka, no Config Server, no Zuul/Gateway)
- No message broker dependencies (no Kafka, no RabbitMQ)
- No Docker files
- No multi-module Maven (single `pom.xml` at root)
- No Lombok — write all getters/setters/constructors explicitly
- No `record` types for entities (records are immutable, JPA needs mutability)
- No `var` keyword — use explicit types everywhere

## Output Format

Produce the **complete project** as a series of files. For each file, output:

```
// FILE: path/from/project/root/FileName.java
<complete file content>
```

Start with `pom.xml`, then `application.yml`, then the security/config layer, then each module alphabetically (entity → enum → repository → DTO → service → controller), then tests, then `DataInitializer`.

Every file must be complete and compilable. The project must start successfully with `mvn spring-boot:run` using the default H2 profile with zero errors.

## Final Checklist (Verify Before Output)

- [ ] At least 6 modules with their own entity/repo/service/controller
- [ ] At least 3 cross-module service-to-service calls via constructor injection
- [ ] At least 2 multi-step methods spanning 3+ modules (saga candidates)
- [ ] JWT auth with register/login/refresh + 3 roles + @PreAuthorize
- [ ] Full CRUD with POST/GET/GET-list/PUT/PATCH/DELETE per controller
- [ ] @Valid on request DTOs, @NotBlank/@NotNull on entities
- [ ] Enum status field per entity
- [ ] @ManyToOne/@OneToMany/@ManyToMany relationships
- [ ] @Version optimistic locking on 2+ entities
- [ ] Soft delete on 1+ entity
- [ ] @Async, @Scheduled, @EventListener, @Cacheable each used at least once
- [ ] File upload and file download endpoints
- [ ] Pagination on all list endpoints
- [ ] Global exception handler with structured error response
- [ ] Liquibase changesets
- [ ] OpenAPI/Swagger configured
- [ ] Custom @ConfigurationProperties
- [ ] DataInitializer with seed data
- [ ] No Lombok, no var, no Spring Cloud, no message broker
- [ ] Unit tests for every service class
- [ ] Compiles and runs with `mvn spring-boot:run`
