@echo off
set MAVEN_OPTS=-Dcsp.sentinel.dashboard.server=127.0.0.1:8718
cd /d c:\Users\75328\OneDrive\桌面\xss\gateway-service
mvn spring-boot:run