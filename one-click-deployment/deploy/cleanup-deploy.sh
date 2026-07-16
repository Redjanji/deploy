#!/bin/bash
# =====================================================
# XSS 微服务集群 - 一键清除脚本
# =====================================================
# 功能：
#   1. 停止并删除所有 xss- 前缀的容器
#   2. 删除 Docker 网络
#   3. 删除相关数据卷（可选）
#   4. 删除业务镜像（可选）
#   5. 删除部署目录（可选）
#
# 使用方式：
#   bash cleanup-deploy.sh              # 仅删除容器和网络（保留数据）
#   bash cleanup-deploy.sh --volumes    # 删除容器、网络和数据卷（不保留数据）
#   bash cleanup-deploy.sh --images     # 删除容器、网络、数据卷和业务镜像
#   bash cleanup-deploy.sh --all        # 完全清除（容器+网络+数据卷+镜像+部署目录）
#   bash cleanup-deploy.sh --help       # 显示帮助
# =====================================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

DELETE_VOLUMES=false
DELETE_IMAGES=false
DELETE_DIR=false

for arg in "$@"; do
    case "$arg" in
        --volumes) DELETE_VOLUMES=true ;;
        --images)  DELETE_IMAGES=true; DELETE_VOLUMES=true ;;
        --all)     DELETE_ALL=true; DELETE_VOLUMES=true; DELETE_IMAGES=true; DELETE_DIR=true ;;
        -h|--help)
            echo "Usage: bash cleanup-deploy.sh [--volumes] [--images] [--all]"
            echo ""
            echo "Options:"
            echo "  --volumes  删除容器、网络和数据卷（不保留数据）"
            echo "  --images   删除容器、网络、数据卷和业务镜像"
            echo "  --all      完全清除（容器+网络+数据卷+镜像+部署目录）"
            echo ""
            echo "示例："
            echo "  bash cleanup-deploy.sh          # 仅删除容器和网络"
            echo "  bash cleanup-deploy.sh --images # 删除容器、网络、数据卷和镜像"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $arg${NC}"
            exit 1
            ;;
    esac
done

log_info()  { echo -e "${BLUE}[INFO]${NC}  $1"; }
log_ok()    { echo -e "${GREEN}[ OK ]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[FAIL]${NC}  $1"; }

confirm() {
    read -p "$1 (y/N): " -n 1 -r
    echo ""
    [[ $REPLY =~ ^[Yy]$ ]]
}

# =====================================================
# 步骤 1: 停止并删除容器
# =====================================================
step1_stop_containers() {
    echo ""
    echo "====================================================="
    echo "  步骤 1/5: 停止并删除容器"
    echo "====================================================="

    local containers=$(docker ps -a --filter "name=xss-" -q 2>/dev/null | wc -l)
    if [ "$containers" -eq 0 ]; then
        log_info "没有找到 xss- 前缀的容器"
        return 0
    fi

    log_info "找到 $containers 个 xss- 容器，停止中..."
    docker stop $(docker ps -a --filter "name=xss-" -q 2>/dev/null) 2>/dev/null || true

    log_info "删除容器中..."
    docker rm $(docker ps -a --filter "name=xss-" -q 2>/dev/null) 2>/dev/null || true

    log_ok "容器已删除"
}

# =====================================================
# 步骤 2: 删除 Docker 网络
# =====================================================
step2_remove_network() {
    echo ""
    echo "====================================================="
    echo "  步骤 2/5: 删除 Docker 网络"
    echo "====================================================="

    if docker network inspect xss-net &> /dev/null; then
        log_info "删除 xss-net 网络..."
        docker network rm xss-net
        log_ok "网络已删除"
    else
        log_info "xss-net 网络不存在"
    fi
}

# =====================================================
# 步骤 3: 删除数据卷
# =====================================================
step3_remove_volumes() {
    if [ "$DELETE_VOLUMES" = false ]; then
        return 0
    fi

    echo ""
    echo "====================================================="
    echo "  步骤 3/5: 删除数据卷（⚠️ 数据将丢失）"
    echo "====================================================="

    local volumes=$(docker volume ls --filter "name=xss-" -q 2>/dev/null | wc -l)
    if [ "$volumes" -eq 0 ]; then
        log_info "没有找到 xss- 前缀的数据卷"
        return 0
    fi

    if ! confirm "确定要删除 $volumes 个数据卷吗？所有数据将永久丢失"; then
        log_warn "跳过数据卷删除"
        return 0
    fi

    log_info "删除数据卷中..."
    docker volume rm $(docker volume ls --filter "name=xss-" -q 2>/dev/null) 2>/dev/null || true

    log_ok "数据卷已删除"
}

# =====================================================
# 步骤 4: 删除业务镜像
# =====================================================
step4_remove_images() {
    if [ "$DELETE_IMAGES" = false ]; then
        return 0
    fi

    echo ""
    echo "====================================================="
    echo "  步骤 4/5: 删除业务镜像"
    echo "====================================================="

    local images=$(docker images --filter "reference=xss/*" -q 2>/dev/null | wc -l)
    if [ "$images" -eq 0 ]; then
        log_info "没有找到 xss/ 前缀的镜像"
        return 0
    fi

    if ! confirm "确定要删除 $images 个业务镜像吗？下次部署需要重新加载"; then
        log_warn "跳过镜像删除"
        return 0
    fi

    log_info "删除业务镜像中..."
    docker rmi $(docker images --filter "reference=xss/*" -q 2>/dev/null) 2>/dev/null || true

    log_ok "业务镜像已删除"
}

# =====================================================
# 步骤 5: 删除部署目录
# =====================================================
step5_remove_directory() {
    if [ "$DELETE_DIR" = false ]; then
        return 0
    fi

    echo ""
    echo "====================================================="
    echo "  步骤 5/5: 删除部署目录"
    echo "====================================================="

    local deploy_dir="/opt/xss-deploy"
    if [ ! -d "$deploy_dir" ]; then
        log_info "部署目录 $deploy_dir 不存在"
        return 0
    fi

    if ! confirm "确定要删除部署目录 $deploy_dir 吗？"; then
        log_warn "跳过部署目录删除"
        return 0
    fi

    log_info "删除部署目录中..."
    rm -rf "$deploy_dir"

    log_ok "部署目录已删除"
}

# =====================================================
# 主流程
# =====================================================
main() {
    echo ""
    echo "╔══════════════════════════════════════════════════╗"
    echo "║      XSS 微服务集群 - 一键清除脚本              ║"
    echo "╚══════════════════════════════════════════════════╝"
    echo ""
    echo "  删除容器:     true"
    echo "  删除网络:     true"
    echo "  删除数据卷:   $DELETE_VOLUMES"
    echo "  删除镜像:     $DELETE_IMAGES"
    echo "  删除部署目录: $DELETE_DIR"
    echo ""

    if [ "$DELETE_VOLUMES" = true ] || [ "$DELETE_IMAGES" = true ] || [ "$DELETE_DIR" = true ]; then
        if ! confirm "此操作将删除部署的内容，确认继续"; then
            log_warn "已取消操作"
            exit 0
        fi
    fi

    step1_stop_containers
    step2_remove_network
    step3_remove_volumes
    step4_remove_images
    step5_remove_directory

    echo ""
    echo "====================================================="
    echo "  清除完成"
    echo "====================================================="
    echo ""
    log_info "如需重新部署，执行:"
    log_info "  curl -fsSL https://gitee.com/redjanji_admin/one-click-deployment/raw/master/one-click-deployment/deploy/full-deploy.sh | sudo bash"
}

main "$@"
