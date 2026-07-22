#!/bin/bash
# =====================================================
# XSS 微服务集群 - 一键清除/重新部署脚本
# =====================================================
# 功能：
#   1. 停止并删除所有 xss- 容器
#   2. 删除 Docker 网络和数据卷（彻底清除数据）
#   3. 保留 Docker 镜像（避免重新下载/加载）
#   4. 可选保留部署目录（快速重试，无需重新拉取）
#   5. 可选自动重新部署
#
# 使用方式：
#   bash cleanup-deploy.sh                      # 清除 + 保留镜像 + 保留部署目录 + 重新部署（最快重试）
#   bash cleanup-deploy.sh --clean-only         # 仅清除，不重新部署
#   bash cleanup-deploy.sh --purge-deploy       # 同时删除部署目录（彻底重来，仍保留镜像）
#   bash cleanup-deploy.sh --full               # 彻底清除（含镜像，慎用）
#   bash cleanup-deploy.sh --restart-docker     # 清除前先重启 Docker 守护进程（解决顽固状态）
# =====================================================

set -uo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

CLEAN_ONLY=false
DELETE_IMAGES=false
PURGE_DEPLOY=false
RESTART_DOCKER=false
SKIP_LOAD=false

for arg in "$@"; do
    case "$arg" in
        --clean-only)     CLEAN_ONLY=true ;;
        --full)           DELETE_IMAGES=true ;;
        --purge-deploy)   PURGE_DEPLOY=true ;;
        --restart-docker) RESTART_DOCKER=true ;;
        --skip-load)      SKIP_LOAD=true ;;
        -h|--help)
            echo "Usage: bash cleanup-deploy.sh [OPTIONS]"
            echo ""
            echo "选项:"
            echo "  (无参数)          清除数据 + 保留镜像 + 保留部署目录 + 重新部署（最快重试）"
            echo "  --clean-only      仅清除，不重新部署"
            echo "  --purge-deploy    同时删除部署目录（彻底重来，仍保留镜像）"
            echo "  --full            彻底清除（含镜像），需配合 --clean-only 使用"
            echo "  --restart-docker  清除前先重启 Docker 守护进程（解决顽固状态）"
            echo "  --skip-load       重新部署时跳过镜像加载（镜像已在本地）"
            exit 0
            ;;
    esac
done

log_info()  { echo -e "${BLUE}[INFO]${NC}  $1"; }
log_ok()    { echo -e "${GREEN}[ OK ]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[FAIL]${NC}  $1"; }

# 定位部署目录
SCRIPT_PATH="${BASH_SOURCE[0]}"
while [ -L "$SCRIPT_PATH" ]; do
    SCRIPT_PATH=$(readlink -f "$SCRIPT_PATH")
done
SCRIPT_DIR=$(cd "$(dirname "$SCRIPT_PATH")" && pwd)

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║      XSS 微服务集群 - 清除/重新部署              ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""
log_info "保留镜像:     $([ "$DELETE_IMAGES" = false ] && echo '是' || echo '否')"
log_info "保留部署目录: $([ "$PURGE_DEPLOY" = false ] && echo '是（快速重试）' || echo '否（彻底重来）')"
log_info "重新部署:     $([ "$CLEAN_ONLY" = false ] && echo '是' || echo '否')"
log_info "重启 Docker:  $([ "$RESTART_DOCKER" = true ] && echo '是' || echo '否')"
echo ""

# =====================================================
# 0. 可选：重启 Docker 守护进程（解决顽固状态）
# =====================================================
if [ "$RESTART_DOCKER" = true ]; then
    log_warn "重启 Docker 守护进程..."
    if command -v systemctl &> /dev/null; then
        sudo systemctl restart docker 2>/dev/null && log_ok "Docker 已重启" || {
            log_error "Docker 重启失败，请手动执行: sudo systemctl restart docker"
            exit 1
        }
    elif command -v service &> /dev/null; then
        sudo service docker restart 2>/dev/null && log_ok "Docker 已重启" || {
            log_error "Docker 重启失败，请手动执行: sudo service docker restart"
            exit 1
        }
    fi
    sleep 3
    docker info &>/dev/null || {
        log_error "Docker 不可用，请手动启动"
        exit 1
    }
fi

# =====================================================
# 1. 停止并删除所有容器（含非 xss- 的冲突容器）
# =====================================================
log_info "停止并删除所有 xss- 容器..."

# 先用 docker compose down（如果在部署目录中）
if [ -f "$SCRIPT_DIR/docker-compose.yml" ]; then
    cd "$SCRIPT_DIR"
    log_info "执行 docker compose down..."
    docker compose --profile infra --profile core --profile business --profile nginx down --remove-orphans 2>/dev/null || true
fi

# 停止并删除所有 xss- 容器
if docker ps -a --filter "name=xss-" -q 2>/dev/null | grep -q .; then
    log_info "强制删除 $(docker ps -a --filter 'name=xss-' -q 2>/dev/null | wc -l) 个容器..."
    docker stop $(docker ps -a --filter "name=xss-" -q 2>/dev/null) 2>/dev/null || true
    docker rm -f $(docker ps -a --filter "name=xss-" -q 2>/dev/null) 2>/dev/null || true
    log_ok "容器已删除"
else
    log_ok "无 xss- 容器需要清理"
fi

# 兜底：删除所有已停止的容器（防止端口占用）
log_info "清理所有已停止的容器..."
STOPPED=$(docker ps -a --filter "status=exited" --filter "status=dead" -q 2>/dev/null)
if [ -n "$STOPPED" ]; then
    echo "$STOPPED" | xargs -r docker rm -f 2>/dev/null || true
    log_ok "已停止的容器已清理"
else
    log_ok "无已停止的容器"
fi

# =====================================================
# 2. 删除 Docker 网络
# =====================================================
log_info "删除 Docker 网络..."

# 删除 xss-net 网络
docker network rm xss-net 2>/dev/null && log_ok "  网络 xss-net 已删除" || log_ok "  网络 xss-net 不存在"

# 删除所有未使用的网络
docker network prune -f 2>/dev/null | tail -1 || true

# =====================================================
# 3. 删除数据卷（彻底清除数据库等数据）
# =====================================================
log_info "删除数据卷（含数据库、Redis、ES 等所有持久化数据）..."

# 列出所有相关数据卷并删除
VOLUME_NAMES=("mysql-data" "redis-data" "rabbitmq-data" "es-data" "minio-data" "nacos-data" "nginx-log" "init-db")

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

# 删除所有未使用的卷（彻底清理）
docker volume prune -f 2>/dev/null | tail -1 || true

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
    # 清理所有未使用的镜像
    docker image prune -a -f 2>/dev/null | tail -1 || true
else
    IMAGE_COUNT=$(docker images --filter "reference=xss/*" -q 2>/dev/null | wc -l)
    log_ok "保留业务镜像（${IMAGE_COUNT} 个）"
    if [ "$IMAGE_COUNT" -gt 0 ]; then
        docker images --format "  {{.Repository}}:{{.Tag}} ({{.Size}})" --filter "reference=xss/*" 2>/dev/null
    fi
fi

# =====================================================
# 5. 处理部署目录
# =====================================================
if [ "$PURGE_DEPLOY" = true ]; then
    log_warn "删除部署目录..."
    DEPLOY_DIRS=("/opt/xss-deploy" "$HOME/xss-deploy")
    for dir in "${DEPLOY_DIRS[@]}"; do
        if [ -d "$dir" ]; then
            log_info "删除: $dir"
            rm -rf "$dir"
            log_ok "  已删除"
        fi
    done
else
    # 保留部署目录，但清理可能残留的临时文件
    log_info "保留部署目录: $SCRIPT_DIR"
    # 清理可能残留的合并后的 tar 文件（释放空间）
    if [ -f "$SCRIPT_DIR/xss-images-*.tar" ] || [ -f "$SCRIPT_DIR/xss-images-*.tar.gz" ]; then
        rm -f "$SCRIPT_DIR"/xss-images-*.tar "$SCRIPT_DIR"/xss-images-*.tar.gz 2>/dev/null || true
        log_ok "已清理合并后的 tar 文件"
    fi
    log_ok "部署目录已保留（含 SQL 脚本、配置文件等）"
fi

# 清理临时克隆目录
rm -rf /tmp/xss-deploy-clone-* 2>/dev/null || true

# =====================================================
# 6. 清理 Docker 构建缓存和悬空资源
# =====================================================
log_info "清理 Docker 悬空资源..."
docker builder prune -f 2>/dev/null | tail -1 || true
if [ "$DELETE_IMAGES" = true ]; then
    docker system prune -f --volumes 2>/dev/null | tail -1 || true
else
    # 不使用 -a，保留所有 tagged 镜像
    docker container prune -f 2>/dev/null | tail -1 || true
fi

# =====================================================
# 7. 验证清理结果
# =====================================================
echo ""
log_info "清理结果验证:"
REMAINING_CONTAINERS=$(docker ps -a --filter "name=xss-" -q 2>/dev/null | wc -l)
REMAINING_VOLUMES=$(docker volume ls --filter "name=xss" -q 2>/dev/null | wc -l)
REMAINING_IMAGES=$(docker images --filter "reference=xss/*" -q 2>/dev/null | wc -l)

echo "  容器:   $REMAINING_CONTAINERS 个（应为 0）"
echo "  数据卷: $REMAINING_VOLUMES 个（应为 0）"
echo "  镜像:   $REMAINING_IMAGES 个（$([ "$DELETE_IMAGES" = false ] && echo '已保留' || echo '已删除')）"

if [ "$REMAINING_CONTAINERS" -gt 0 ] || [ "$REMAINING_VOLUMES" -gt 0 ]; then
    log_warn "部分资源未清理干净，建议加 --restart-docker 参数重试"
fi

echo ""
echo "====================================================="
echo "  清除完成"
echo "====================================================="
echo ""

# =====================================================
# 8. 自动重新部署
# =====================================================
if [ "$CLEAN_ONLY" = false ]; then
    log_info "开始重新部署..."

    # 确定部署目录
    if [ "$PURGE_DEPLOY" = false ] && [ -f "$SCRIPT_DIR/full-deploy.sh" ]; then
        # 保留部署目录模式：直接运行，跳过拉取和镜像加载
        log_info "使用保留的部署目录: $SCRIPT_DIR"
        log_info "跳过镜像加载（镜像已在本地）"
        echo ""
        cd "$SCRIPT_DIR"
        if [ "$SKIP_LOAD" = true ] || [ "$DELETE_IMAGES" = false ]; then
            bash full-deploy.sh --no-pull --skip-load
        else
            bash full-deploy.sh --no-pull
        fi
    else
        # 彻底重来模式：从远程拉取
        log_info "从远程拉取部署文件..."
        echo ""
        curl -fsSL https://raw.githubusercontent.com/Redjanji/deploy/master/full-deploy.sh | bash
    fi
else
    log_info "如需重新部署，执行:"
    if [ "$PURGE_DEPLOY" = false ] && [ -f "$SCRIPT_DIR/full-deploy.sh" ]; then
        log_info "  cd $SCRIPT_DIR && bash full-deploy.sh --no-pull --skip-load"
    else
        log_info "  curl -fsSL https://raw.githubusercontent.com/Redjanji/deploy/master/full-deploy.sh | bash"
    fi
fi
