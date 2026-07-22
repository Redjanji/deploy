$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$DATE = Get-Date -Format "yyyyMMdd"
$OUTPUT_TAR = Join-Path $SCRIPT_DIR "one-click-deployment\deploy\xss-images-${DATE}.tar"
$OUTPUT_GZ = Join-Path $SCRIPT_DIR "one-click-deployment\deploy\xss-images-${DATE}.tar.gz"

$business_images = @(
    "xss/gateway-service:latest",
    "xss/auth-service:latest",
    "xss/dict-service:latest",
    "xss/image-service:latest",
    "xss/property-service:latest",
    "xss/search-service:latest",
    "xss/analytics-service:latest",
    "xss/message-service:latest",
    "xss/favorite-service:latest",
    "xss/review-service:latest",
    "xss/booking-service:latest",
    "nginx:1.27-alpine"
)

$PART_SIZE_MB = 45
$PART_SIZE_BYTES = $PART_SIZE_MB * 1024 * 1024

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  导出 XSS 业务服务镜像" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "输出文件: xss-images-${DATE}.tar.gz"
Write-Host "分片大小: ${PART_SIZE_MB} MB（Gitee 限制 100MB）"
Write-Host "镜像总数: $($business_images.Count)（仅业务服务）"
Write-Host ""

$missing = @()
foreach ($img in $business_images) {
    $exists = docker image inspect $img 2>$null
    if (-not $exists) {
        Write-Host "⚠️  镜像不存在，尝试拉取: $img" -ForegroundColor Yellow
        $pull = docker pull $img
        if ($LASTEXITCODE -ne 0) {
            Write-Host "❌ 拉取失败: $img" -ForegroundColor Red
            $missing += $img
        } else {
            Write-Host "✅ 拉取成功" -ForegroundColor Green
        }
    } else {
        Write-Host "✅ 已存在: $img" -ForegroundColor Green
    }
}

if ($missing.Count -gt 0) {
    Write-Host ""
    Write-Host "以下镜像缺失，无法导出:" -ForegroundColor Red
    foreach ($m in $missing) {
        Write-Host "  - $m" -ForegroundColor Red
    }
    exit 1
}

Write-Host ""
Write-Host "正在导出 $($business_images.Count) 个业务服务镜像..." -ForegroundColor Cyan
$start_time = Get-Date

docker save -o $OUTPUT_TAR $business_images
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 镜像导出失败" -ForegroundColor Red
    exit 1
}

$tar_size = (Get-Item $OUTPUT_TAR).Length
$tar_size_mb = [math]::Round($tar_size / 1MB, 2)
Write-Host "✅ tar 文件生成: ${tar_size_mb} MB" -ForegroundColor Green

Write-Host "正在压缩为 gzip..." -ForegroundColor Cyan
$tar_stream = [System.IO.File]::OpenRead($OUTPUT_TAR)
$gz_stream = [System.IO.File]::Create($OUTPUT_GZ)
$gzip_stream = New-Object System.IO.Compression.GzipStream($gz_stream, [System.IO.Compression.CompressionMode]::Compress)
$tar_stream.CopyTo($gzip_stream)
$gzip_stream.Close()
$gz_stream.Close()
$tar_stream.Close()

Write-Host "✅ 压缩完成" -ForegroundColor Green

Write-Host "正在删除原始 tar 文件..." -ForegroundColor Cyan
Remove-Item $OUTPUT_TAR -Force
Write-Host "✅ 删除完成" -ForegroundColor Green

Write-Host "正在分片打包（每片 ${PART_SIZE_MB} MB）..." -ForegroundColor Cyan
$gz_file = Get-Item $OUTPUT_GZ
$total_bytes = $gz_file.Length
$part_num = 1
$stream = [System.IO.File]::OpenRead($OUTPUT_GZ)
$buffer = New-Object byte[] $PART_SIZE_BYTES

while ($true) {
    $bytes_read = $stream.Read($buffer, 0, $buffer.Length)
    if ($bytes_read -eq 0) { break }
    
    $part_file = "${OUTPUT_GZ}.part" + "{0:D2}" -f $part_num
    $part_stream = [System.IO.File]::Create($part_file)
    $part_stream.Write($buffer, 0, $bytes_read)
    $part_stream.Close()
    
    $part_size_mb = [math]::Round($bytes_read / 1MB, 2)
    Write-Host "  创建分片: $("{0:D2}" -f $part_num)"
    Write-Host "OK: 分片 $("{0:D2}" -f $part_num)/?: ${part_size_mb} MB" -ForegroundColor Green
    
    $part_num++
}
$stream.Close()

$part_count = $part_num - 1
$gz_size_mb = [math]::Round($total_bytes / 1MB, 2)
$end_time = Get-Date
$duration = [math]::Round(($end_time - $start_time).TotalSeconds, 2)

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  导出完成！" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "输出文件: $OUTPUT_GZ"
Write-Host "文件大小: ${gz_size_mb} MB"
Write-Host "镜像数量: $($business_images.Count)（仅业务服务）"
Write-Host "分片数量: $part_count"
Write-Host "耗时: ${duration} 秒"
Write-Host ""
Write-Host "运维交付物:" -ForegroundColor Yellow
Write-Host "  1. 整个 deploy/ 目录"
Write-Host "  2. xss-images-${DATE}.tar.gz.part01~part${part_count}"
