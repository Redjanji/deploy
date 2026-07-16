Write-Host "================================================"
Write-Host "XSS 微服务集群 - Docker 镜像构建脚本"
Write-Host "================================================"

$BASE_DIR = $PSScriptRoot
$IMAGE_TAG = "latest"

$services = @(
    "gateway-service",
    "auth-service",
    "dict-service",
    "image-service",
    "property-service",
    "search-service",
    "analytics-service",
    "message-service",
    "favorite-service",
    "review-service",
    "booking-service"
)

Write-Host ""
Write-Host "[1/2] 编译 Maven 项目..."
cd $BASE_DIR
mvn clean package -DskipTests -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Maven 编译失败！"
    exit 1
}
Write-Host "SUCCESS: Maven 编译完成"

Write-Host ""
Write-Host "[2/2] 构建 Docker 镜像..."

foreach ($service in $services) {
    Write-Host ""
    Write-Host "Building xss/$service`:$IMAGE_TAG..."
    docker build -t "xss/$service`:$IMAGE_TAG" --build-arg SERVICE_NAME=$service .
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: 构建 xss/$service 失败！"
        exit 1
    }
    Write-Host "SUCCESS: xss/$service`:$IMAGE_TAG"
}

Write-Host ""
Write-Host "================================================"
Write-Host "所有镜像构建完成！"
Write-Host "================================================"
docker images | Select-String "xss/"
