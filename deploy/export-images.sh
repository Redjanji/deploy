#!/bin/bash
# =====================================================
# XSS 微服务集群 - 导出镜像脚本（开发机执行 / Ubuntu）
# 导出所有业务服务 + 中间件 + Nginx 镜像为 tar.gz
# =====================================================
# 用法: bash export-images.sh
# =====================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATE=$(date +%Y%m%d)
OUTPUT_TAR="${SCRIPT_DIR}/xss-images-${DATE}.tar"
OUTPUT_GZ="${SCRIPT_DIR}/xss-images-${DATE}.tar.gz"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

business_images=(
    "xss/gateway-service:latest"
    "xss/auth-service:latest"
    "xss/dict-service:latest"
    "xss/image-service:latest"
    "xss/property-service:latest"
    "xss/search-service:latest"
    "xss/analytics-service:latest"
    "xss/message-service:latest"
    "xss/favorite-service:latest"
    "xss/review-service:latest"
    "xss/booking-service:latest"
)

middleware_images=(
    "mysql:8.0"
    "redis:7-alpine"
    "rabbitmq:3.12-management"
    "docker.elastic.co/elasticsearch/elasticsearch:8.11.0"
    "minio/minio:latest"
    "nacos/nacos-server:v2.3.0"
    "nginx:1.27-alpine"
)

all_images=("${business_images[@]}" "${middleware_images[@]}")

echo -e "${CYAN}=========================================="
echo -e "  导出 XSS 镜像包"
echo -e "==========================================${NC}"
echo -e "输出文件: xss-images-${DATE}.tar.gz"
echo -e "镜像总数: ${#all_images[@]}"
echo ""

missing=()
for img in "${all_images[@]}"; do
    if ! docker image inspect "$img" &>/dev/null; then
        echo -e "${YELLOW}⚠️  镜像不存在，尝试拉取: $img${NC}"
        if docker pull "$img"; then
            echo -e "${GREEN}✅ 拉取成功${NC}"
        else
            echo -e "${RED}❌ 拉取失败: $img${NC}"
            missing+=("$img")
        fi
    else
        echo -e "${GREEN}✅ 已存在: $img${NC}"
    fi
done

if [ ${#missing[@]} -gt 0 ]; then
    echo ""
    echo -e "${RED}以下镜像缺失，无法导出:${NC}"
    for m in "${missing[@]}"; do
        echo -e "  - $m"
    done
    exit 1
fi

echo ""
echo -e "${CYAN}正在导出 ${#all_images[@]} 个镜像...${NC}"
start_time=$(date +%s)

docker save -o "$OUTPUT_TAR" "${all_images[@]}"
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ 镜像导出失败${NC}"
    exit 1
fi

tar_size=$(du -h "$OUTPUT_TAR" | cut -f1)
echo -e "${GREEN}✅ tar 文件生成: $tar_size${NC}"

echo -e "${CYAN}正在压缩为 gzip...${NC}"
gzip -f "$OUTPUT_TAR"
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ 压缩失败${NC}"
    exit 1
fi

end_time=$(date +%s)
duration=$((end_time - start_time))
gz_size=$(du -h "$OUTPUT_GZ" | cut -f1)

echo ""
echo -e "${CYAN}=========================================="
echo -e "${GREEN}  导出完成！${NC}"
echo -e "${CYAN}==========================================${NC}"
echo -e "输出文件: $OUTPUT_GZ"
echo -e "文件大小: $gz_size"
echo -e "镜像数量: ${#all_images[@]}"
echo -e "耗时: ${duration} 秒"
echo ""
echo -e "${YELLOW}运维交付物:${NC}"
echo -e "  1. 整个 deploy/ 目录（压缩成 zip/tar.gz）"
echo -e "  2. xss-images-${DATE}.tar.gz 镜像包"
