#!/bin/bash
# =====================================================
# XSS 微服务集群 - 一键清除/重新部署脚本
# =====================================================
# 功能：
#   1. 停止并删除所有 xss- 容器
#   2. 删除 Docker 网络和数据卷（彻底清除数据）
#   3. 删除部署目录
#   4. 保留 Docker 镜像（避免重新下载）
#   5. 可选自动重新部署
#
# 使用方式：
#   bash cleanup-deploy.sh              # 清除并重新部署（保留镜像）
#   bash cleanup-deploy.sh --clean-only # 仅清除，不重新部署
#   bash cleanup-deploy.sh --full       # 彻底清除（含镜像，慎用）
# =====================================================

set -uo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

CLEAN_ONLY=false
DELETE_IMAGES=false

for arg in "$@"; do
    case "$arg" in
        --clean-only) CLEAN_ONLY=true ;;
        --full)       DELETE_IMAGES=true ;;
        -h|--help)
            echo "Usage: bash cleanup-deploy.sh [--clean-only] [--full]"
            echo ""
            echo "  (无参数)       清除数据 + 保留镜像 + 自动重新部署"
            echo "  --clean-only   仅清除，不重新部署"
            echo "  --full         彻底清除（含镜像），需配合 --clean-only 使用"
            exit 0
            ;;
    esac
done

log_info()  { echo -e "${BLUE}[INFO]${NC}  $1"; }
log_ok()    { echo -e "${GREEN}[ OK ]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[FAIL]${NC}  $1"; }

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║      XSS 微服务集群 - 清除/重新部署              ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""
log_info "保留镜像: $([ "$DELETE_IMAGES" = false ] && echo '是' || echo '否')"
log_info "重新部署: $([ "$CLEAN_ONLY" = false ] && echo '是' || echo '否')"
echo ""

# =====================================================
# 1. 停止并删除所有容器
# =====================================================
log_info "停止并删除所有 xss- 容器..."

# 先停止所有运行中的容器
if docker ps -a --filter "name=xss-" -q 2>/dev/null | grep -q .; then
    docker stop $(docker ps -a --filter "name=xss-" -q 2>/dev/null) 2>/dev/null || true
    docker rm -f $(docker ps -a --filter "name=xss-" -q 2>/dev/null) 2>/dev/null || true
    log_ok "容器已删除"
else
    log_ok "无容器需要清理"
fi

# =====================================================
# 2. 删除 Docker 网络
# =====================================================
log_info "删除 Docker 网络..."
docker network rm xss-net 2>/dev/null && log_ok "网络已删除" || log_ok "网络不存在"

# 也用 docker compose down 兜底（如果在部署目录中）
if [ -f docker-compose.yml ]; then
    docker compose --profile infra --profile core --profile business --profile nginx down --remove-orphans 2>/dev/null || true
fi

# =====================================================
# 3. 删除数据卷（彻底清除数据库等数据）
# =====================================================
log_info "删除数据卷..."

# 列出所有相关数据卷并删除
VOLUME_NAMES=("mysql-data" "redis-data" "rabbitmq-data" "es-data" "minio-data" "nacos-data" "nginx-log")

for vol in "${VOLUME_NAMES[@]}"; do
    # 尝试多种命名格式：xss-deploy_mysql-data, deploy_mysql-data, mysql-data
    for full_vol in $(docker volume ls --filter "name=$vol" -q 2>/dev/null); do
        docker volume rm -f "$full_vol" 2>/dev/null && log_ok "  删除卷: $full_vol"
    done
done

# 兜底：删除所有含 xss 的卷
for vol in $(docker volume ls --filter "name=xss" -q 2>/dev/null); do
    docker volume rm -f "$vol" 2>/dev/null && log_ok "  删除卷: $vol"
done

log_ok "数据卷清理完成"

# =====================================================
# 4. 删除业务镜像（仅 --full 模式）
# =====================================================
if [ "$DELETE_IMAGES" = true ]; then
    log_warn "删除业务镜像..."
    if docker images --filter "reference=xss/*" -q 2>/dev/null | grep -q .; then
        docker rmi -f $(docker images --filter "reference=xss/*" -q 2>/dev/null) 2>/dev/null || true
        log_ok "镜像已删除"
    else
        log_ok "无镜像需要清理"
    fi
else
    log_info "保留业务镜像（$(docker images --filter 'reference=xss/*' -q 2>/dev/null | wc -l) 个）"
fi

# =====================================================
# 5. 删除部署目录
# =====================================================
DEPLOY_DIRS=("/opt/xss-deploy" "$HOME/xss-deploy")

for dir in "${DEPLOY_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        log_info "删除部署目录: $dir"
        rm -rf "$dir"
        log_ok "  已删除"
    fi
done

# 清理临时克隆目录
rm -rf /tmp/xss-deploy-clone-* 2>/dev/null || true

# =====================================================
# 6. 清理 Docker 悬空资源
# =====================================================
log_info "清理 Docker 悬空资源..."
docker system prune -f --volumes 2>/dev/null | tail -1 || true

echo ""
echo "====================================================="
echo "  清除完成"
echo "====================================================="
echo ""

# =====================================================
# 7. 自动重新部署
# =====================================================
if [ "$CLEAN_ONLY" = false ]; then
    log_info "开始重新部署..."
    echo ""
    curl -fsSL https://gitee.com/redjanji_admin/one-click-deployment/raw/master/one-click-deployment/deploy/full-deploy.sh | sudo bash
else
    log_info "如需重新部署，执行:"
    log_info "  curl -fsSL https://gitee.com/redjanji_admin/one-click-deployment/raw/master/one-click-deployment/deploy/full-deploy.sh | sudo bash"
fi
