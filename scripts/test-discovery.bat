@echo off
echo =========================================
echo Testing FractalX Discovery System
echo =========================================

REM Set test environment variables
set DISCOVERY_SERVICES=order-service:localhost:8081,payment-service:localhost:8080,inventory-service:localhost:8082

echo.
echo 1. Running unit tests...
cd fractalx-test-app
mvn test -Dtest=DiscoveryControllerTest

echo.
echo 2. Running integration tests...
mvn test -Dtest=DiscoveryIntegrationTest

echo.
echo 3. Running all discovery tests...
mvn test -Dtest="*Discovery*Test"

echo.
echo =========================================
echo Test Results Summary:
echo =========================================
echo - Unit Tests: DiscoveryRegistry, DiscoveryClient, StaticDiscoveryConfig
echo - Integration Tests: Full discovery lifecycle
echo - Configuration: YAML config + environment variables
echo.
echo To test manually:
echo 1. Start services on different terminals:
echo    Terminal 1: mvn spring-boot:run -Dserver.port=8081 -Dspring.application.name=order-service
echo    Terminal 2: mvn spring-boot:run -Dserver.port=8080 -Dspring.application.name=payment-service
echo.
echo 2. Check discovery status:
echo    curl http://localhost:8081/actuator/discovery
echo    curl http://localhost:8080/actuator/discovery
echo.
echo 3. Test service calls:
echo    curl http://localhost:8081/api/test
echo    curl http://localhost:8080/api/test

pause