#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATE=$(date +%Y%m%d)
PART_SIZE_MB=95
PART_SIZE_BYTES=$((PART_SIZE_MB * 1024 * 1024))

echo "=========================================="
echo "  XSS 微服务镜像更新脚本"
echo "=========================================="

# 1. 拉取最新代码
echo ""
echo "[1/4] 拉取最新代码..."
cd "$SCRIPT_DIR/.."
git pull
echo "SUCCESS: 代码拉取完成"

# 2. 编译项目
echo ""
echo "[2/4] 编译 Maven 项目..."
cd "$SCRIPT_DIR/../.."
if [ ! -f pom.xml ]; then
    echo "ERROR: pom.xml 不存在，请确认目录结构"
    exit 1
fi
mvn clean package -DskipTests -q
echo "SUCCESS: Maven 编译完成"

# 3. 构建 Docker 镜像
echo ""
echo "[3/4] 构建 Docker 镜像..."
services=(
    "gateway-service"
    "auth-service"
    "dict-service"
    "image-service"
    "property-service"
    "search-service"
    "analytics-service"
    "message-service"
    "favorite-service"
    "review-service"
    "booking-service"
)

for service in "${services[@]}"; do
    echo "Building xss/${service}:latest..."
    docker build -t "xss/${service}:latest" --build-arg SERVICE_NAME="$service" .
    echo "SUCCESS: xss/${service}:latest"
done

# 4. 导出并分片
echo ""
echo "[4/4] 导出镜像并分片..."
OUTPUT_GZ="${SCRIPT_DIR}/xss-images-${DATE}.tar.gz"
OUTPUT_TAR="${OUTPUT_GZ%.gz}"

# 删除旧分片
OLD_PARTS=$(ls "${SCRIPT_DIR}/xss-images-"*.tar.gz.part* 2>/dev/null | grep -v "${DATE}")
if [ -n "$OLD_PARTS" ]; then
    echo "删除旧分片文件..."
    rm -f $OLD_PARTS
fi

rm -f "$OUTPUT_TAR" "$OUTPUT_GZ" "${OUTPUT_GZ}.part"*

echo "正在导出 11 个业务服务镜像..."
docker save -o "$OUTPUT_TAR" \
    xss/gateway-service:latest \
    xss/auth-service:latest \
    xss/dict-service:latest \
    xss/image-service:latest \
    xss/property-service:latest \
    xss/search-service:latest \
    xss/analytics-service:latest \
    xss/message-service:latest \
    xss/favorite-service:latest \
    xss/review-service:latest \
    xss/booking-service:latest

echo "正在压缩为 gzip..."
gzip -9 "$OUTPUT_TAR"

echo "正在分片打包（每片 ${PART_SIZE_MB} MB）..."
split -b "${PART_SIZE_BYTES}" "$OUTPUT_GZ" "${OUTPUT_GZ}.part"
PART_COUNT=$(ls "${OUTPUT_GZ}.part"* 2>/dev/null | wc -l)
GZ_SIZE=$(du -h "$OUTPUT_GZ" | awk '{print $1}')

echo "删除原始 gz 文件..."
rm -f "$OUTPUT_GZ"

echo ""
echo "导出完成！"
echo "文件大小: $GZ_SIZE"
echo "分片数量: $PART_COUNT"

echo ""
echo "=========================================="
echo "  镜像更新完成！"
echo "=========================================="
echo "下一步：推送至远程仓库"
echo "cd $SCRIPT_DIR"
echo "git add xss-images-${DATE}.tar.gz.part*"
echo "git commit -m 'chore: 更新镜像分片 ${DATE}'"
echo "git push"