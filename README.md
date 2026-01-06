<img src="https://raw.githubusercontent.com/Project-FractalX/fractalx-static-content/937e314f9af4fca1b23d76262f40c1d339d179d4/images/logos/FractalX-1.jpg" alt="FractalX logo" width="30%" />

# FractalX

A framework for static decomposition of modular monolithic applications into microservice deployments.

## Table of Contents
1. Overview
2. Prerequisites
3. Quick Install and Build
4. Generate Services
5. Start Generated Services
6. Test Endpoints
7. Common Problems and Fixes
8. Git Tag Commands
9. Frontend Integration
10. Contributing
11. License

## 1. Overview
FractalX analyzes a monolith and generates modular microservice projects. This repository contains the plugin and an example app `fractalx-test-app` that demonstrates generation and local service execution.

## 2. Prerequisites
- Java 17+ on `PATH`
- Maven 3.8+
- Git
- Optional: MySQL if you want to run MySQL-specific SQL scripts instead of H2

## 3. Quick Install and Build
Build framework modules:
```bash
cd fractalx-parent
mvn clean install -DskipTests
````

Generate services from example app:

```bash
cd fractalx-test-app
mvn com.fractalx:fractalx-maven-plugin:0.2.0-SNAPSHOT:decompose
```

Or, after configuring `settings.xml` and a plugin alias:

```bash
mvn fractalx:decompose
```

## 4. Generate Services (Detailed)

After running the plugin, generated services appear under:

```bash
cd fractalx-test-app/target/generated-services
ls -la
```

Expected output:

```text
order-service/
payment-service/
```

## 5. Start Generated Services

Start each service in its own terminal.

Payment service:

```bash
cd fractalx-test-app/target/generated-services/payment-service
mvn clean spring-boot:run
```

Order service:

```bash
cd fractalx-test-app/target/generated-services/order-service
mvn clean spring-boot:run
```

For Spring Boot debug output:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--debug"
```

## 6. Test Endpoints

Example `curl` requests:

Payment health:

```bash
curl http://localhost:8082/api/payments/health
```

Order health:

```bash
curl http://localhost:8081/api/orders/health
```

Process payment:

```bash
curl -X POST http://localhost:8082/api/payments/process \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST001","amount":100.50}'
```

Create order (calls payment service):

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST001","amount":100.50}'
```

## 7. Common Problems and Fixes

### Problem

Application fails at startup with an error referencing `SET FOREIGN_KEY_CHECKS = 0` and an H2 syntax error.

### Cause

Generated `schema.sql` contains MySQL-specific statements that H2 does not support.

### Fixes

Choose one of the following.

#### Use MySQL at runtime

Edit `src/main/resources/application.properties` or `application.yml` in the generated service:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/yourdb
spring.datasource.username=youruser
spring.datasource.password=yourpass
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

Ensure the MySQL server and database exist before starting the service.

#### Make SQL platform-specific

* Rename MySQL scripts to `schema-mysql.sql`
* Provide `schema-h2.sql` if needed
* Set the platform:

```properties
spring.sql.init.platform=mysql
```

#### Modify SQL scripts

Remove or guard MySQL-only statements such as `SET FOREIGN_KEY_CHECKS` from `schema.sql`.

### Temporary Workarounds

Not recommended for production.

Ignore unsupported statements:

```properties
spring.sql.init.continue-on-error=true
```

Disable automatic SQL initialization:

```properties
spring.sql.init.enabled=false
```

### Debugging Tips

* Re-run Maven with `-e` or `-X` for full stack traces
* Start with `--debug` to see Spring Boot condition evaluation output

## 8. Git Tag Commands

Create an annotated tag:

```bash
git tag -a v1.0.0 -m "release v1.0.0"
```

Push a single tag:

```bash
git push origin v1.0.0
```

Push all tags:

```bash
git push --tags
```

Delete a remote tag:

```bash
git push --delete origin v1.0.0
```

Delete a local tag:

```bash
git tag -d v1.0.0
```

## 9. Frontend Integration

A React or Next.js frontend should be a separate project that calls the generated services' REST APIs.

Scaffold frontend:

```bash
npx create-react-app frontend
```

Or:

```bash
npx create-next-app frontend
```

Configure API base URLs using environment variables, for example:

```text
REACT_APP_API_BASE=http://localhost:8081
```

Implement UI pages that call:

* `POST /api/orders`
* `POST /api/payments/process`

### CORS Configuration

Enable cross-origin access in generated services.

Example Java configuration:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
      .allowedOrigins("http://localhost:3000")
      .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
  }
}
```

## 10. Contributing

* Fork the repository
* Create a branch for your change
* Run `mvn clean install -DskipTests`
* Open a pull request with a clear description

## 11. License

This project is licensed under the APACHE LICENSE 2.0. See the [LICENSE](LICENSE) file for details.
