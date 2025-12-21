@echo off
echo =========================================
echo Testing FractalX Discovery System
echo =========================================

REM Set correct port mapping
set DISCOVERY_PORT=9998
set DISCOVERY_URL=http://localhost:%DISCOVERY_PORT%
set DISCOVERY_SERVICES=payment-service:localhost:8080,order-service:localhost:8081

echo.
echo =========================================
echo PORT MAPPING:
echo =========================================
echo Discovery Service:   localhost:%DISCOVERY_PORT%
echo API Gateway:         localhost:9999
echo Payment Service:     localhost:8080
echo Order Service:       localhost:8081
echo =========================================

echo.
echo 1. Running unit tests...
cd fractalx-test-app
mvn test -Dtest=DiscoveryControllerTest

echo.
echo 2. Running integration tests...
mvn test -Dtest=DiscoveryIntegrationTest

echo.
echo 3. Testing Discovery Service on port %DISCOVERY_PORT%...
echo.
echo   3.1 Health check:
curl %DISCOVERY_URL%/health
echo.
echo   3.2 Discovery info:
curl %DISCOVERY_URL%/discovery/info
echo.
echo   3.3 Register test services:
curl -X POST %DISCOVERY_URL%/discovery/register ^
  -H "Content-Type: application/json" ^
  -d "{\"instanceId\":\"payment-service-8080\",\"serviceName\":\"payment-service\",\"host\":\"localhost\",\"port\":8080}"
echo.
curl -X POST %DISCOVERY_URL%/discovery/register ^
  -H "Content-Type: application/json" ^
  -d "{\"instanceId\":\"order-service-8081\",\"serviceName\":\"order-service\",\"host\":\"localhost\",\"port\":8081}"
echo.
echo   3.4 View registered services:
curl %DISCOVERY_URL%/discovery/services
echo.

echo.
echo =========================================
echo Manual Testing Instructions:
echo =========================================
echo.
echo 1. Start services on different terminals:
echo    Terminal 1 (Discovery): mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=%DISCOVERY_PORT%
echo    Terminal 2 (API Gateway): mvn spring-boot:run
echo    Terminal 3 (Payment Service): mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080
echo    Terminal 4 (Order Service): mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
echo.
echo 2. Register services with discovery:
echo    curl -X POST %DISCOVERY_URL%/discovery/register -H "Content-Type: application/json" -d "{\"instanceId\":\"payment-1\",\"serviceName\":\"payment-service\",\"host\":\"localhost\",\"port\":8080}"
echo    curl -X POST %DISCOVERY_URL%/discovery/register -H "Content-Type: application/json" -d "{\"instanceId\":\"order-1\",\"serviceName\":\"order-service\",\"host\":\"localhost\",\"port\":8081}"
echo.
echo 3. Test endpoints:
echo    Discovery:   curl %DISCOVERY_URL%/health
echo    Payment:     curl http://localhost:8080/api/payments/health
echo    Order:       curl http://localhost:8081/api/orders/health
echo    Gateway:     curl http://localhost:9999/actuator/health
echo.

pause