$services = @(
    @{name="dict"; port=8083; dir="dict-service"},
    @{name="auth"; port=8081; dir="auth-service"},
    @{name="image"; port=8082; dir="image-service"},
    @{name="property"; port=8085; dir="property-service"},
    @{name="search"; port=8086; dir="search-service"},
    @{name="analytics"; port=8087; dir="analytics-service"},
    @{name="message"; port=8088; dir="message-service"},
    @{name="favorite"; port=8089; dir="favorite-service"},
    @{name="review"; port=8091; dir="review-service"},
    @{name="booking"; port=8092; dir="booking-service"},
    @{name="gateway"; port=8080; dir="gateway-service"}
)

$baseDir = "c:\Users\75328\OneDrive\桌面\xss"
$logDir = "$baseDir\logs"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

foreach ($service in $services) {
    $serviceDir = "$baseDir\$($service.dir)"
    $jarFile = "$serviceDir\target\$($service.dir)-0.0.1-SNAPSHOT.jar"
    
    if (-not (Test-Path $jarFile)) {
        Write-Host "[$($service.name)] JAR文件不存在: $jarFile" -ForegroundColor Red
        continue
    }
    
    Write-Host "[$($service.name)] 启动中..." -ForegroundColor Cyan
    
    $env:NACOS_SERVER_ADDR = "127.0.0.1:8848"
    $env:MYSQL_HOST = "127.0.0.1"
    $env:MYSQL_USER = "root"
    $env:MYSQL_PASS = "root"
    $env:REDIS_HOST = "127.0.0.1"
    $env:REDIS_PASSWORD = "redisroot"
    $env:RABBITMQ_HOST = "127.0.0.1"
    $env:MINIO_HOST = "127.0.0.1"
    $env:MINIO_PORT = "9000"
    $env:MINIO_ACCESS_KEY = "minioadmin"
    $env:MINIO_SECRET_KEY = "minioadmin"
    $env:ELASTICSEARCH_HOST = "127.0.0.1"
    $env:JWT_SECRET = "eW91ci0yNTYtYml0LXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLWRldi1vbmx5"
    $env:APP_SECRET_BACKEND = "XssBackend2026SecHmacKey8xYz"
    
    $stdoutLog = "$logDir\$($service.name)-stdout.log"
    $stderrLog = "$logDir\$($service.name)-stderr.log"
    
    $process = Start-Process -FilePath "java" -ArgumentList "-Dfile.encoding=UTF-8", "-Dclient.encoding.override=UTF-8", "-jar", $jarFile `
        -WorkingDirectory $serviceDir `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -NoNewWindow `
        -PassThru
    
    Write-Host "[$($service.name)] 已启动, PID: $($process.Id)" -ForegroundColor Green
    Start-Sleep -Seconds 3
}

Write-Host ""
Write-Host "所有服务启动完成！" -ForegroundColor Green
Write-Host "等待服务初始化..." -ForegroundColor Yellow
Start-Sleep -Seconds 40

Write-Host ""
Write-Host "服务状态检查:" -ForegroundColor Cyan
foreach ($service in $services) {
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:$($service.port)/actuator/health" -Method Get -TimeoutSec 5
        Write-Host "[$($service.name)] 端口 $($service.port): $($resp.status)" -ForegroundColor Green
    } catch {
        Write-Host "[$($service.name)] 端口 $($service.port): DOWN" -ForegroundColor Red
    }
}
