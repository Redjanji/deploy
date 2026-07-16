@echo off
setlocal enabledelayedexpansion

echo ================================================
echo XSS 微服务集群 - Docker 镜像构建脚本
echo ================================================

set BASE_DIR=%~dp0
set IMAGE_TAG=latest

echo.
echo [1/2] 编译 Maven 项目...
cd "%BASE_DIR%"
call mvn clean package -DskipTests -q
if %errorlevel% neq 0 (
    echo ERROR: Maven 编译失败！
    exit /b 1
)
echo SUCCESS: Maven 编译完成

echo.
echo [2/2] 构建 Docker 镜像...

call :build_image gateway-service
call :build_image auth-service
call :build_image dict-service
call :build_image image-service
call :build_image property-service
call :build_image search-service
call :build_image analytics-service
call :build_image message-service
call :build_image favorite-service
call :build_image review-service
call :build_image booking-service

echo.
echo ================================================
echo 所有镜像构建完成！
echo ================================================
docker images | findstr "xss/"

endlocal
exit /b 0

:build_image
    echo.
    echo Building xss/%1:%IMAGE_TAG%...
    docker build -t xss/%1:%IMAGE_TAG% --build-arg SERVICE_NAME=%1 .
    if !errorlevel! neq 0 (
        echo ERROR: 构建 xss/%1 失败！
        exit /b 1
    )
    echo SUCCESS: xss/%1:%IMAGE_TAG%
goto :eof
