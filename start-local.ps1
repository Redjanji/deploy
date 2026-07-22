# =====================================================
# 本地启动脚本 - 连接Windows Docker中的基础设施
# 使用方法: powershell -ExecutionPolicy Bypass -File start-local.ps1
# =====================================================

$ErrorActionPreference = "Stop"

$DOCKER_HOST = "127.0.0.1"

$services = @(
    @{name="dict-service"; port=8081; path="dict-service"},
    @{name="auth-service"; port=8083; path="auth-service"},
    @{name="image-service"; port=8082; path="image-service"},
    @{name="property-service"; port=8084; path="property-service"},
    @{name="search-service"; port=8085; path="search-service"},
    @{name="analytics-service"; port=8086; path="analytics-service"},
    @{name="message-service"; port=8087; path="message-service"},
    @{name="favorite-service"; port=8088; path="favorite-service"},
    @{name="review-service"; port=8089; path="review-service"},
    @{name="booking-service"; port=8090; path="booking-service"},
    @{name="gateway-service"; port=8080; path="gateway-service"}
)

foreach ($service in $services) {
    Write-Host "`n=== 启动 $($service.name) ===" -ForegroundColor Cyan
    $jarPath = "$($service.path)/target/$($service.name)-0.0.1-SNAPSHOT.jar"
    
    if (-not (Test-Path $jarPath)) {
        Write-Host "[$($service.name)] JAR文件不存在: $jarPath" -ForegroundColor Yellow
        continue
    }
    
    $script = @"
cd c:\Users\75328\OneDrive\桌面\xss\$($service.path)
`$env:NACOS_SERVER_ADDR="$DOCKER_HOST:8848"
`$env:MYSQL_HOST="$DOCKER_HOST"
`$env:MYSQL_USER="root"
`$env:MYSQL_PASS="root"
`$env:REDIS_HOST="$DOCKER_HOST"
`$env:REDIS_PASSWORD="redisroot"
`$env:RABBITMQ_HOST="$DOCKER_HOST"
`$env:JWT_SECRET="eW91ci0yNTYtYml0LXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLWRldi1vbmx5"
`$env:APP_SECRET_BACKEND="XssBackend2026SecHmacKey8xYz"
java -jar target\$($service.name)-0.0.1-SNAPSHOT.jar
"@
    
    $scriptPath = "$($service.path)/start-service.ps1"
    Set-Content -Path $scriptPath -Value $script
    
    Start-Process powershell -ArgumentList "-ExecutionPolicy Bypass -File $scriptPath" -WindowStyle Normal
    Write-Host "[$($service.name)] 已启动，端口: $($service.port)" -ForegroundColor Green
    
    Start-Sleep -Seconds 6
}

Write-Host "`n=== 所有服务启动完成 ===" -ForegroundColor Green
Write-Host "网关地址: http://localhost:8080" -ForegroundColor Yellow
Write-Host "Nacos控制台: http://$DOCKER_HOST:8848/nacos" -ForegroundColor Yellow
