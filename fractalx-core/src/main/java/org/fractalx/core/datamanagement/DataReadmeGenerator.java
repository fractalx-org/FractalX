package org.fractalx.core.datamanagement;

import org.fractalx.core.model.FractalModule;
import org.fractalx.core.model.MethodParam;
import org.fractalx.core.model.SagaDefinition;
import org.fractalx.core.model.SagaStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates a service-specific DATA_README.md file.
 * Documents the database strategy, decoupling, and dependencies for a SINGLE service.
 */
public class DataReadmeGenerator {

    private static final Logger log = LoggerFactory.getLogger(DataReadmeGenerator.class);

    public void generateServiceDataReadme(FractalModule module, Path serviceRoot, String driverClassName) {
        log.info("   📝 Generating Data README for '{}'...", module.getServiceName());

        StringBuilder md = new StringBuilder();

        String dbType = detectDbType(driverClassName);
        String dbUrlDisplay = detectDbUrlPattern(module, dbType);

        // 1. Header
        md.append("# 🗄️ Data Architecture: ").append(module.getServiceName()).append("\n\n");
        md.append("This service has been upgraded by FractalX to support **Distributed Data Isolation**.\n\n");

        // 2. Database Configuration
        md.append("## 1. Database Configuration\n");
        md.append("This service follows the **Database-per-Service** pattern.\n\n");
        md.append("| Property | Value |\n");
        md.append("| :--- | :--- |\n");
        md.append("| **Database Type** | ").append(dbType).append(" |\n");
        md.append("| **Connection URL** | `").append(dbUrlDisplay).append("` |\n");
        md.append("| **Schema Strategy** | `ddl-auto: update` (Hibernate managed) |\n\n");

        if (dbType.contains("H2")) {
            md.append("> **⚠️ Note:** This service uses an **In-Memory H2 Database** (Default).\n");
            md.append("> - Data is lost when the service stops.\n");
            md.append("> - To use MySQL/Postgres, see the **Configuration Guide** below.\n\n");
        } else {
            md.append("> **✅ Production Mode:** This service is configured to use an external database (").append(dbType).append(").\n\n");
        }

        // 3. Decoupling Strategy
        md.append("## 2. Decoupling Strategy\n");
        md.append("Cross-service relationships have been transformed at the Java level to remove hard Foreign Key constraints.\n\n");
        md.append("- **Local Entities**: Relationships inside this service (e.g., `@OneToMany`) are preserved. Foreign Keys exist.\n");
        md.append("- **Remote Entities**: Relationships to other services were converted to IDs (e.g., `Customer customer` → `String customerId`).\n\n");

        // 4. Dependencies
        md.append("## 3. Injected Dependencies\n");
        md.append("FractalX automatically injected the following driver into `pom.xml`:\n");
        md.append("- **Driver Class**: `").append(driverClassName != null ? driverClassName : "org.h2.Driver").append("`\n\n");

        // 5. How to Verify
        md.append("## 4. How to Verify\n");
        md.append("1. Start the service: `mvn spring-boot:run`\n");
        md.append("2. Check logs for `Hibernate: create table ...`\n");
        if (dbType.contains("H2")) {
            md.append("3. Access H2 Console: `http://localhost:").append(module.getPort()).append("/h2-console`\n");
            md.append("   - JDBC URL: `").append(dbUrlDisplay).append("`\n");
        }
        md.append("\n");

        // 6. Configuration Guide
        md.append("## 5. Configuration Guide\n");
        md.append("To switch this service to a physical database (MySQL/PostgreSQL), add the following block to your **Monolith's** `application.yml` before decomposition:\n\n");

        md.append("```yaml\n");
        md.append("fractalx:\n");
        md.append("  modules:\n");
        md.append("    # Must match the service name exactly\n");
        md.append("    ").append(module.getServiceName()).append(":\n");
        md.append("      datasource:\n");
        md.append("        url: jdbc:mysql://localhost:3306/").append(module.getServiceName().replace("-", "_")).append("_db\n");
        md.append("        username: root\n");
        md.append("        password: <your_password>\n");
        md.append("        driver-class-name: com.mysql.cj.jdbc.Driver\n");
        md.append("      jpa:\n");
        md.append("        hibernate:\n");
        md.append("          ddl-auto: update\n");
        md.append("```\n");

        // Write file inside the SERVICE ROOT
        try {
            Path readmePath = serviceRoot.resolve("DATA_README.md");
            Files.writeString(readmePath, md.toString());
        } catch (IOException e) {
            log.error("Failed to generate Data README for " + module.getServiceName(), e);
        }
    }

    private String detectDbType(String driver) {
        if (driver == null) return "H2 (Default)";
        if (driver.contains("mysql")) return "MySQL";
        if (driver.contains("postgresql")) return "PostgreSQL";
        if (driver.contains("h2")) return "H2 (In-Memory)";
        return "Custom SQL (" + driver + ")";
    }

    private String detectDbUrlPattern(FractalModule module, String dbType) {
        if (dbType.contains("H2")) {
            return "jdbc:h2:mem:" + module.getServiceName().replace("-", "_");
        }
        return "See src/main/resources/application.yml";
    }

    // =========================================================================
    // Saga Orchestrator README
    // =========================================================================

    /**
     * Generates {@code DATA_README.md} for the {@code fractalx-saga-orchestrator} service.
     * Documents the saga state machine, each saga's REST endpoint, payload format,
     * compensation flow, and database schema.
     */
    public void generateSagaOrchestratorReadme(List<SagaDefinition> sagas,
                                               List<FractalModule> modules,
                                               Path serviceRoot) {
        log.info("   📝 Generating Saga Orchestrator README...");

        StringBuilder md = new StringBuilder();

        // ── Header ────────────────────────────────────────────────────────────
        md.append("# 🎭 Saga Orchestrator: fractalx-saga-orchestrator\n\n");
        md.append("Auto-generated by **FractalX**. ");
        md.append("This service manages distributed sagas using the **Orchestrator Saga Pattern** ");
        md.append("to coordinate multi-step transactions across microservices.\n\n");

        // ── 1. What This Service Does ─────────────────────────────────────────
        md.append("## 1. What This Service Does\n\n");
        md.append("The saga orchestrator ensures data consistency across services that cannot share ");
        md.append("a single database transaction. It:\n\n");
        md.append("- Coordinates ordered remote calls across services via **NetScope gRPC**.\n");
        md.append("- Persists each execution's state in the `saga_instance` table — so progress survives restarts.\n");
        md.append("- Automatically triggers **compensating transactions** in **reverse order** if any step fails.\n");
        md.append("- Exposes REST endpoints to **start**, **query**, and **list** saga executions.\n\n");

        // ── 2. Managed Sagas table ────────────────────────────────────────────
        md.append("## 2. Managed Sagas\n\n");
        md.append("| Saga ID | Owner Service | Steps | Description |\n");
        md.append("| :--- | :--- | :---: | :--- |\n");
        for (SagaDefinition saga : sagas) {
            md.append("| `").append(saga.getSagaId()).append("` | ")
              .append(saga.getOwnerServiceName()).append(" | ")
              .append(saga.getSteps().size()).append(" | ");
            String desc = saga.getDescription();
            md.append(desc.isBlank() ? "—" : desc).append(" |\n");
        }
        md.append("\n");

        // ── 3. Saga State Machine ─────────────────────────────────────────────
        md.append("## 3. Saga State Machine\n\n");
        md.append("Each saga execution transitions through the following states:\n\n");
        md.append("```\n");
        md.append("STARTED → IN_PROGRESS → DONE\n");
        md.append("                  ↓\n");
        md.append("            COMPENSATING → FAILED\n");
        md.append("```\n\n");
        md.append("| Status | Meaning |\n");
        md.append("| :--- | :--- |\n");
        md.append("| `STARTED` | A new saga instance was created and saved. |\n");
        md.append("| `IN_PROGRESS` | Forward steps are executing. |\n");
        md.append("| `DONE` | All steps completed successfully. |\n");
        md.append("| `COMPENSATING` | A step failed; compensating transactions are running in reverse. |\n");
        md.append("| `FAILED` | All compensations have run; the saga is marked permanently failed. |\n\n");

        // ── 4. Saga Endpoints & Payloads ─────────────────────────────────────
        md.append("## 4. How to Start Each Saga\n\n");
        md.append("All endpoints are served on **port 8099**.\n\n");
        for (SagaDefinition saga : sagas) {
            String endpoint = "/saga/" + saga.getSagaId() + "/start";
            String payloadClass = saga.toClassName() + "Payload";
            List<MethodParam> params = saga.getSagaMethodParams();

            md.append("### `").append(saga.getSagaId()).append("`\n\n");
            if (!saga.getDescription().isBlank()) {
                md.append("> ").append(saga.getDescription()).append("\n\n");
            }
            md.append("**Endpoint:** `POST ").append(endpoint).append("`\n\n");

            if (params.isEmpty()) {
                md.append("No payload required — send an empty JSON object `{}`.\n\n");
            } else {
                md.append("**Payload (`").append(payloadClass).append("`):**\n\n");
                md.append("```json\n{\n");
                for (MethodParam p : params) {
                    md.append("  \"").append(p.getName()).append("\": ")
                      .append(jsonPlaceholder(p.getType())).append(",\n");
                }
                // remove last comma
                int lastComma = md.lastIndexOf(",\n");
                if (lastComma != -1) md.replace(lastComma, lastComma + 2, "\n");
                md.append("}\n```\n\n");

                md.append("| Field | Type | Notes |\n");
                md.append("| :--- | :--- | :--- |\n");
                for (MethodParam p : params) {
                    md.append("| `").append(p.getName()).append("` | `")
                      .append(p.getType()).append("` | |\n");
                }
                md.append("\n");

                md.append("**curl example:**\n\n");
                md.append("```bash\n");
                md.append("curl -X POST http://localhost:8099").append(endpoint).append(" \\\n");
                md.append("  -H \"Content-Type: application/json\" \\\n");
                md.append("  -d '").append(buildCurlSample(params)).append("'\n");
                md.append("```\n\n");
            }

            md.append("**Response:** `202 Accepted` — body: `{ \"correlationId\": \"<uuid>\" }`\n\n");
            md.append("Use the correlationId to track progress:\n\n");
            md.append("```bash\n");
            md.append("curl http://localhost:8099/saga/status/<correlationId>\n");
            md.append("```\n\n");
        }

        // ── 5. Steps and Compensation ─────────────────────────────────────────
        md.append("## 5. Steps and Compensation\n\n");
        md.append("Steps execute in the listed order. ");
        md.append("On failure, compensations run in **reverse order** to undo side-effects.\n\n");
        for (SagaDefinition saga : sagas) {
            md.append("### `").append(saga.getSagaId()).append("`\n\n");
            md.append("| # | Service | Forward Method | Compensation Method |\n");
            md.append("| :--- | :--- | :--- | :--- |\n");
            int i = 1;
            for (SagaStep step : saga.getSteps()) {
                md.append("| ").append(i++).append(" | `")
                  .append(step.getTargetServiceName()).append("` | `")
                  .append(step.getMethodName()).append("()`  | ");
                if (step.hasCompensation()) {
                    md.append("`").append(step.getCompensationMethodName()).append("()`");
                } else {
                    md.append("—");
                }
                md.append(" |\n");
            }
            md.append("\n");
        }

        // ── 6. Database ───────────────────────────────────────────────────────
        md.append("## 6. Database: `saga_instance` Table\n\n");
        md.append("This service uses an **H2 in-memory database** (default). ");
        md.append("Saga execution state is stored in a single table:\n\n");
        md.append("| Column | Type | Description |\n");
        md.append("| :--- | :--- | :--- |\n");
        md.append("| `id` | `BIGINT IDENTITY` | Auto-generated primary key |\n");
        md.append("| `saga_id` | `VARCHAR(100)` | Saga type identifier (e.g. `place-order-saga`) |\n");
        md.append("| `correlation_id` | `VARCHAR(36)` | Unique execution UUID |\n");
        md.append("| `owner_service` | `VARCHAR(100)` | Service that triggered this saga |\n");
        md.append("| `status` | `VARCHAR(30)` | Current `SagaStatus` value |\n");
        md.append("| `current_step` | `VARCHAR(200)` | Last attempted step (`service:method`) |\n");
        md.append("| `payload` | `TEXT` | Original JSON payload |\n");
        md.append("| `error_message` | `TEXT` | Failure message if any |\n\n");
        md.append("Migration script: `src/main/resources/db/migration/V1__init_saga.sql`\n\n");
        md.append("> **⚠️ Note:** Data is lost when this service stops (H2 in-memory).\n");
        md.append("> For production, configure a persistent datasource — see **Configuration Guide** below.\n");
        md.append("> Access H2 Console: `http://localhost:8099/h2-console` (JDBC URL: `jdbc:h2:mem:saga_db`)\n\n");

        // ── 7. Service Discovery (gRPC ports) ─────────────────────────────────
        md.append("## 7. Service Connectivity\n\n");
        md.append("The orchestrator reaches participating services via **NetScope gRPC**. ");
        md.append("Default ports (service port + 10 000):\n\n");
        md.append("| Service | HTTP Port | gRPC Port |\n");
        md.append("| :--- | :---: | :---: |\n");
        for (FractalModule m : modules) {
            md.append("| `").append(m.getServiceName()).append("` | ")
              .append(m.getPort()).append(" | ").append(m.getPort() + 10000).append(" |\n");
        }
        md.append("\n");
        md.append("Configured in `application.yml` under `netscope.client.servers`.\n\n");

        // ── 8. Configuration Guide ────────────────────────────────────────────
        md.append("## 8. Configuration Guide\n\n");
        md.append("To persist saga state across restarts, replace the H2 datasource in `application.yml`:\n\n");
        md.append("```yaml\n");
        md.append("spring:\n");
        md.append("  datasource:\n");
        md.append("    url: jdbc:postgresql://localhost:5432/saga_db\n");
        md.append("    username: postgres\n");
        md.append("    password: <your_password>\n");
        md.append("    driver-class-name: org.postgresql.Driver\n");
        md.append("  jpa:\n");
        md.append("    hibernate:\n");
        md.append("      ddl-auto: validate\n");
        md.append("```\n\n");
        md.append("Add the PostgreSQL driver to `pom.xml`:\n\n");
        md.append("```xml\n");
        md.append("<dependency>\n");
        md.append("    <groupId>org.postgresql</groupId>\n");
        md.append("    <artifactId>postgresql</artifactId>\n");
        md.append("</dependency>\n");
        md.append("```\n");

        try {
            Files.writeString(serviceRoot.resolve("DATA_README.md"), md.toString());
            log.info("   ✅ Saga Orchestrator README written to {}/DATA_README.md", serviceRoot.getFileName());
        } catch (IOException e) {
            log.error("Failed to write Saga Orchestrator README", e);
        }
    }

    /** Returns a JSON sample value for a given Java type. */
    private String jsonPlaceholder(String type) {
        return switch (type) {
            case "String"                       -> "\"example\"";
            case "Long", "long",
                 "Integer", "int"               -> "1";
            case "Double", "double",
                 "Float", "float",
                 "BigDecimal"                   -> "99.99";
            case "Boolean", "boolean"           -> "true";
            case "LocalDate"                    -> "\"2026-03-02\"";
            case "LocalDateTime",
                 "ZonedDateTime"                -> "\"2026-03-02T10:00:00\"";
            case "UUID"                         -> "\"550e8400-e29b-41d4-a716-446655440000\"";
            default                             -> "null";
        };
    }

    /** Builds a compact one-line JSON sample for a curl -d argument. */
    private String buildCurlSample(List<MethodParam> params) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < params.size(); i++) {
            MethodParam p = params.get(i);
            if (i > 0) sb.append(",");
            sb.append("\\\"").append(p.getName()).append("\\\":")
              .append(jsonPlaceholder(p.getType()).replace("\"", "\\\""));
        }
        sb.append("}");
        return sb.toString();
    }
}