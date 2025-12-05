# FractalX
A Framework for Static Decomposition of Modular Monolithic Applications into Microservice Deployments

Install
```
cd fractalx-parent
```
```
mvn clean install
```

Run
```
cd /Users/sathnindu/Develop/fractalx/FractalX/fractalx-test-app
```

```
mvn com.fractalx:fractalx-maven-plugin:0.1.0-SNAPSHOT:decompose
```

Proposed run command (after settings.xml configuration)
```
mvn fractalx:decompose
```

--
### Generation

```
cd /Users/sathnindu/Develop/fractalx/FractalX/fractalx-parent
mvn clean install -DskipTests
```

```
cd /Users/sathnindu/Develop/fractalx/FractalX/fractalx-test-app
mvn com.fractalx:fractalx-maven-plugin:0.1.0-SNAPSHOT:decompose
```

--
### Start

```
cd /Users/sathnindu/Develop/fractalx/FractalX/fractalx-test-app/target/generated-services
ls -la
```

You should see:
```
order-service/
payment-service/
```

Step 2: Start each service in separate terminals
Terminal 1 - Payment Service:

```
cd /Users/sathnindu/Develop/fractalx/FractalX/fractalx-test-app/target/generated-services/payment-service
mvn clean spring-boot:run
```

Wait until you see:
```
Started Application in X.XXX seconds
```

Terminal 2 - Order Service:

```
cd /Users/sathnindu/Develop/fractalx/FractalX/fractalx-test-app/target/generated-services/order-service
mvn clean spring-boot:run
```

Step 3: Verify services are running
Terminal 3 - Test the services:

```
# Test payment service health
curl http://localhost:8082/api/payments/health

# Test order service health
curl http://localhost:8081/api/orders/health

# Test payment processing
curl -X POST http://localhost:8082/api/payments/process \
-H "Content-Type: application/json" \
-d '{"customerId": "CUST001", "amount": 100.50}'

# Test order creation (which should call payment service)
curl -X POST http://localhost:8081/api/orders \
-H "Content-Type: application/json" \
-d '{"customerId": "CUST001", "amount": 100.50}'
```