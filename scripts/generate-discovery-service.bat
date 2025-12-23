@echo off
setlocal enabledelayedexpansion

echo =========================================
echo Generating FractalX Discovery Service
echo =========================================

set DISCOVERY_DIR=D:\Research\FractalX_IMPL_main\FractalX\fractalx-test-app\target\generated-services\discovery-service

echo Creating discovery service directory...
if exist "%DISCOVERY_DIR%" rmdir /s /q "%DISCOVERY_DIR%"
mkdir "%DISCOVERY_DIR%"
mkdir "%DISCOVERY_DIR%\src\main\java\com\fractalx\discovery"
mkdir "%DISCOVERY_DIR%\src\main\resources"
mkdir "%DISCOVERY_DIR%\src\test\java"

echo Generating POM.xml...
(
echo ^<?xml version="1.0" encoding="UTF-8"?^>
echo ^<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"^>
echo     ^<modelVersion^>4.0.0^</modelVersion^>
echo.
echo     ^<groupId^>com.fractalx.generated^</groupId^>
echo     ^<artifactId^>discovery-service^</artifactId^>
echo     ^<version^>1.0.0-SNAPSHOT^</version^>
echo     ^<packaging^>jar^</packaging^>
echo.
echo     ^<name^>FractalX Discovery Service^</name^>
echo     ^<description^>Service discovery for FractalX microservices^</description^>
echo.
echo     ^<properties^>
echo         ^<java.version^>17^</java.version^>
echo         ^<spring-boot.version^>3.2.0^</spring-boot.version^>
echo         ^<maven.compiler.source^>17^</maven.compiler.source^>
echo         ^<maven.compiler.target^>17^</maven.compiler.target^>
echo         ^<project.build.sourceEncoding^>UTF-8^</project.build.sourceEncoding^>
echo     ^</properties^>
echo.
echo     ^<dependencyManagement^>
echo         ^<dependencies^>
echo             ^<dependency^>
echo                 ^<groupId^>org.springframework.boot^</groupId^>
echo                 ^<artifactId^>spring-boot-dependencies^</artifactId^>
echo                 ^<version^>${spring-boot.version}^</version^>
echo                 ^<type^>pom^</type^>
echo                 ^<scope^>import^</scope^>
echo             ^</dependency^>
echo         ^</dependencies^>
echo     ^</dependencyManagement^>
echo.
echo     ^<dependencies^>
echo         ^<dependency^>
echo             ^<groupId^>org.springframework.boot^</groupId^>
echo             ^<artifactId^>spring-boot-starter-web^</artifactId^>
echo         ^</dependency^>
echo.
echo         ^<dependency^>
echo             ^<groupId^>com.fractalx^</groupId^>
echo             ^<artifactId^>fractalx-core^</artifactId^>
echo             ^<version^>0.1.0-SNAPSHOT^</version^>
echo         ^</dependency^>
echo.
echo         ^<dependency^>
echo             ^<groupId^>org.springframework.boot^</groupId^>
echo             ^<artifactId^>spring-boot-starter-actuator^</artifactId^>
echo         ^</dependency^>
echo.
echo         ^<dependency^>
echo             ^<groupId^>org.yaml^</groupId^>
echo             ^<artifactId^>snakeyaml^</artifactId^>
echo             ^<version^>2.2^</version^>
echo         ^</dependency^>
echo     ^</dependencies^>
echo.
echo     ^<build^>
echo         ^<plugins^>
echo             ^<plugin^>
echo                 ^<groupId^>org.springframework.boot^</groupId^>
echo                 ^<artifactId^>spring-boot-maven-plugin^</artifactId^>
echo                 ^<version^>${spring-boot.version}^</version^>
echo             ^</plugin^>
echo         ^</plugins^>
echo     ^</build^>
echo ^</project^>
) > "%DISCOVERY_DIR%\pom.xml"

echo Generating Application class...
(
echo package com.fractalx.discovery;
echo.
echo import com.fractalx.core.discovery.DiscoveryServiceApplication;
echo import org.springframework.boot.SpringApplication;
echo import org.springframework.boot.autoconfigure.SpringBootApplication;
echo.
echo @SpringBootApplication
echo public class DiscoveryApplication {
echo.
echo     public static void main(String[] args^) {
echo         SpringApplication.run(DiscoveryServiceApplication.class, args^);
echo     }
echo }
) > "%DISCOVERY_DIR%\src\main\java\com\fractalx\discovery\DiscoveryApplication.java"

echo Generating application.yml...
(
echo spring:
echo   application:
echo     name: discovery-service
echo.
echo server:
echo   port: 8761
echo.
echo fractalx:
echo   discovery:
echo     enabled: true
echo     mode: DYNAMIC
echo     host: localhost
echo     port: 8761
echo     heartbeat-interval: 30000
echo     instance-ttl: 90000
echo     auto-cleanup: true
echo     auto-register: true
echo.
echo ^# Static configuration for initial bootstrap
echo discovery:
echo   static-config:
echo     enabled: true
echo     file: classpath:static-services.yml
echo.
echo management:
echo   endpoints:
echo     web:
echo       exposure:
echo         include: health,info,metrics,discovery
echo   endpoint:
echo     health:
echo       show-details: always
echo.
echo logging:
echo   level:
echo     com.fractalx.core.discovery: DEBUG
echo     com.fractalx.discovery: INFO
) > "%DISCOVERY_DIR%\src\main\resources\application.yml"

echo Generating static-services.yml...
(
echo ^# Static Services Configuration
echo ^# Used for initial discovery service bootstrap
echo.
echo instances:
echo   discovery-service:
echo     - host: localhost
echo       port: 8761
echo       metadata:
echo         role: registry
echo         version: "1.0"
echo.
echo   fractalx-gateway:
echo     - host: localhost
echo       port: 9999
echo       metadata:
echo         role: gateway
echo         version: "1.0"
echo.
echo ^# Service dependencies
echo dependencies:
echo   fractalx-gateway:
echo     - discovery-service
) > "%DISCOVERY_DIR%\src\main\resources\static-services.yml"

echo Generating start script...
(
echo @echo off
echo echo Starting FractalX Discovery Service...
echo echo Port: 8761
echo echo.
echo cd /d "%%~dp0"
echo mvn spring-boot:run ^
echo   -Dspring-boot.run.jvmArguments="-Dserver.port=8761" ^
echo   -Dspring-boot.run.arguments="--server.port=8761"
) > "%DISCOVERY_DIR%\start.bat"

echo.
echo =========================================
echo Discovery Service Generated Successfully!
echo =========================================
echo Location: %DISCOVERY_DIR%
echo Port: 8761
echo Health Check: http://localhost:8761/api/discovery/health
echo Services Endpoint: http://localhost:8761/api/discovery/services
echo.
pause

endlocal