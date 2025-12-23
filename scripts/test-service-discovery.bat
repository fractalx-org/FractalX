@echo off
echo =========================================
echo Testing FractalX Service Discovery
echo =========================================

set DISCOVERY_URL=http://localhost:8761
set GATEWAY_URL=http://localhost:9999

echo.
echo 1. Checking Discovery Service Health...
curl %DISCOVERY_URL%/api/discovery/health
echo.
echo.

echo 2. Registering test services...
curl -X POST %DISCOVERY_URL%/api/discovery/register ^
  -H "Content-Type: application/json" ^
  -d "{\"serviceName\":\"payment-service\",\"host\":\"localhost\",\"port\":8080,\"status\":\"UP\"}"
echo.

curl -X POST %DISCOVERY_URL%/api/discovery/register ^
  -H "Content-Type: application/json" ^
  -d "{\"serviceName\":\"order-service\",\"host\":\"localhost\",\"port\":8081,\"status\":\"UP\"}"
echo.

echo 3. Listing all registered services...
curl %DISCOVERY_URL%/api/discovery/services
echo.
echo.

echo 4. Getting discovery statistics...
curl %DISCOVERY_URL%/api/discovery/stats
echo.
echo.

echo 5. Testing Gateway with Discovery...
echo   Payment Service via Gateway:
curl %GATEWAY_URL%/api/payments/health
echo.
echo   Order Service via Gateway:
curl %GATEWAY_URL%/api/orders/health
echo.
echo.

echo 6. Testing direct service access...
echo   Payment Service Direct:
curl http://localhost:8080/api/payments/health
echo.
echo   Order Service Direct:
curl http://localhost:8081/api/orders/health
echo.
echo.

echo 7. Testing service discovery client...
echo   Getting healthy instance of payment-service...
REM This would be called from within a service using DiscoveryClient
echo   (Internal API call - check service logs for details)
echo.
echo.

echo =========================================
echo Service Discovery Test Complete!
echo =========================================
echo.
echo Next steps:
echo   1. Check service logs for discovery registration
echo   2. Verify heartbeat messages in logs
echo   3. Test service failover by stopping a service
echo   4. Check if gateway routes to available instances
echo.
pause