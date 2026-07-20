#!/bin/bash
# =====================================================
# XSS 微服务集群 - 增量补丁脚本
# =====================================================
# 功能：对已有部署应用增量修复，无需全量重新部署
#
# 已修复问题：
#   P001 - MySQL OOM (384M → 512M)
#   P002 - init-db.sh 无重试逻辑，MySQL 重启后 SQL 执行失败
#   P003 - full-deploy.sh 未自动调用 init-db.sh
#   P004 - Nginx 未处理 CORS OPTIONS 预检请求
#   P005 - Docker 安装使用国外源，国内服务器连接失败
#   P006 - .env.example 缺少正确的 APP_SECRET
#   P007 - docker-compose.yml nacos 服务名不匹配
#
# 使用方式：
#   bash patch-deploy.sh              # 应用全部补丁
#   bash patch-deploy.sh --check      # 仅检查，不修改
#   bash patch-deploy.sh --force      # 强制覆盖所有文件
# =====================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${BLUE}[INFO]${NC}  $1"; }
log_ok()    { echo -e "${GREEN}[ OK ]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[FAIL]${NC}  $1"; }

# 参数
CHECK_ONLY=false
FORCE=false
for arg in "$@"; do
    case "$arg" in
        --check) CHECK_ONLY=true ;;
        --force) FORCE=true ;;
        -h|--help)
            echo "Usage: bash patch-deploy.sh [--check] [--force]"
            echo ""
            echo "Options:"
            echo "  --check  仅检查需要修复的项目，不实际修改"
            echo "  --force  强制覆盖所有文件（即使已是最新）"
            exit 0
            ;;
    esac
done

# 远程文件基础 URL
RAW_BASE="https://gitee.com/redjanji_admin/deploy/raw/master"

# 需要检查/修复的文件列表
PATCH_FILES=(
    "docker-compose.yml"
    "nginx/nginx.conf"
    "init-db.sh"
    "full-deploy.sh"
    "cleanup-deploy.sh"
    ".env.example"
)

# 补丁记录
declare -A PATCHES
PATCHES["docker-compose.yml"]="P001: MySQL 内存 384M→512M, innodb_buffer_pool_size 192M→128M"
PATCHES["nginx/nginx.conf"]="P004: CORS OPTIONS 预检请求处理"
PATCHES["init-db.sh"]="P002: MySQL 就绪检查 + 3次重试逻辑"
PATCHES["full-deploy.sh"]="P003: 自动调用 init-db.sh + P005: 阿里云 Docker 源 + P007: nacos 服务名修复"
PATCHES["cleanup-deploy.sh"]="一键清除并重新部署"
PATCHES[".env.example"]="P006: 正确的 APP_SECRET 配置"

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║      XSS 微服务集群 - 增量补丁脚本              ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""
echo "  部署目录: $SCRIPT_DIR"
echo "  模式: $( [ "$CHECK_ONLY" = true ] && echo '仅检查' || echo '应用补丁' )"
echo ""

# =====================================================
# 1. 下载最新文件
# =====================================================
download_file() {
    local file="$1"
    local url="$RAW_BASE/$file"
    local tmp="/tmp/xss_patch_$(echo "$file" | tr '/' '_')"

    if ! curl -fsSL "$url" -o "$tmp" 2>/dev/null; then
        log_error "下载失败: $file"
        rm -f "$tmp"
        return 1
    fi

    echo "$tmp"
    return 0
}

# =====================================================
# 2. 检查文件是否需要更新
# =====================================================
check_file() {
    local file="$1"
    local local_path="$SCRIPT_DIR/$file"

    if [ ! -f "$local_path" ]; then
        echo "missing"
        return
    fi

    local tmp_file=$(download_file "$file")
    if [ $? -ne 0 ]; then
        echo "error"
        return
    fi

    if diff -q "$local_path" "$tmp_file" &>/dev/null; then
        rm -f "$tmp_file"
        echo "same"
    else
        rm -f "$tmp_file"
        echo "diff"
    fi
}

# =====================================================
# 3. 应用补丁到单个文件
# =====================================================
apply_file() {
    local file="$1"
    local local_path="$SCRIPT_DIR/$file"

    log_info "更新: $file"
    local tmp_file=$(download_file "$file")
    if [ $? -ne 0 ]; then
        return 1
    fi

    # 备份旧文件
    if [ -f "$local_path" ]; then
        cp "$local_path" "${local_path}.bak.$(date +%s)"
    fi

    # 确保目录存在
    mkdir -p "$(dirname "$local_path")"

    # 覆盖
    cp "$tmp_file" "$local_path"
    rm -f "$tmp_file"

    # 设置可执行权限
    case "$file" in
        *.sh) chmod +x "$local_path" ;;
    esac

    log_ok "已更新: $file"
}

# =====================================================
# 主流程
# =====================================================

echo "====================================================="
echo "  阶段 1: 检查文件差异"
echo "====================================================="
echo ""

declare -a need_patch=()
for file in "${PATCH_FILES[@]}"; do
    local_path="$SCRIPT_DIR/$file"
    status=$(check_file "$file")
    case "$status" in
        same)
            if [ "$FORCE" = true ]; then
                log_warn "  $file (强制更新)"
                need_patch+=("$file")
            else
                log_ok "  $file (已是最新)"
            fi
            ;;
        diff)
            log_warn "  $file (需要更新)"
            echo -e "    ${YELLOW}修复内容: ${PATCHES[$file]:-未知}${NC}"
            need_patch+=("$file")
            ;;
        missing)
            log_warn "  $file (缺失)"
            need_patch+=("$file")
            ;;
        error)
            log_error "  $file (检查失败，跳过)"
            ;;
    esac
done

echo ""
if [ "${#need_patch[@]}" -eq 0 ]; then
    log_ok "所有文件已是最新，无需补丁"
    echo ""
    exit 0
fi

if [ "$CHECK_ONLY" = true ]; then
    log_info "共 ${#need_patch[@]} 个文件需要更新（--check 模式，未实际修改）"
    echo ""
    exit 0
fi

echo "====================================================="
echo "  阶段 2: 应用补丁"
echo "====================================================="
echo ""

for file in "${need_patch[@]}"; do
    apply_file "$file"
done

echo ""
echo "====================================================="
echo "  阶段 3: 重启受影响的服务"
echo "====================================================="
echo ""

# 判断 Docker Compose 命令
COMPOSE_CMD=""
if docker compose version &>/dev/null; then
    COMPOSE_CMD="docker compose"
elif command -v docker-compose &>/dev/null; then
    COMPOSE_CMD="docker-compose"
fi

if [ -z "$COMPOSE_CMD" ]; then
    log_warn "Docker Compose 未安装，请手动重启服务"
else
    # 检查是否需要重启 Nginx（CORS 修复）
    for file in "${need_patch[@]}"; do
        if [ "$file" = "nginx/nginx.conf" ]; then
            log_info "重启 Nginx（应用 CORS 修复）..."
            $COMPOSE_CMD --profile nginx restart 2>/dev/null || true
            log_ok "Nginx 已重启"
            break
        fi
    done

    # 检查是否需要重启 MySQL（内存修复）
    for file in "${need_patch[@]}"; do
        if [ "$file" = "docker-compose.yml" ]; then
            log_warn "docker-compose.yml 已更新，需要重建 MySQL 容器以应用内存限制"
            log_info "  执行: $COMPOSE_CMD --profile infra up -d mysql"
            $COMPOSE_CMD --profile infra up -d mysql 2>/dev/null || true
            log_ok "MySQL 已重建"

            # 等待 MySQL 稳定
            log_info "等待 MySQL 稳定..."
            sleep 15
            local stable=0
            local waited=0
            local mysql_pwd="root"
            if [ -f .env ]; then
                mysql_pwd=$(grep '^MYSQL_ROOT_PASSWORD=' .env | sed 's/^MYSQL_ROOT_PASSWORD=//' || echo "root")
            fi
            while [ "$waited" -lt 60 ]; do
                if docker exec -e MYSQL_PWD="$mysql_pwd" xss-mysql mysql -uroot -e "SELECT 1" &>/dev/null; then
                    stable=$((stable + 1))
                    if [ "$stable" -ge 2 ]; then
                        break
                    fi
                else
                    stable=0
                fi
                sleep 5
                waited=$((waited + 5))
            done
            if [ "$stable" -ge 2 ]; then
                log_ok "MySQL 已稳定"

                # 检查数据库完整性
                local db_count=0
                for db in auth_db dict_db property_db image_db analytics_db message_db favorite_db review_db booking_db; do
                    if docker exec -e MYSQL_PWD="$mysql_pwd" xss-mysql mysql -uroot -e "USE $db;" 2>/dev/null; then
                        db_count=$((db_count + 1))
                    fi
                done
                if [ "$db_count" -lt 9 ]; then
                    log_warn "数据库不完整 ($db_count/9)，执行 init-db.sh..."
                    chmod +x ./init-db.sh
                    bash ./init-db.sh
                else
                    log_ok "数据库完整 ($db_count/9)"
                fi
            else
                log_error "MySQL 未稳定，请手动检查"
            fi
            break
        fi
    done
fi

echo ""
echo "====================================================="
echo "  补丁完成"
echo "====================================================="
echo ""
echo "  已修复的问题："
echo "    P001 - MySQL OOM (内存增至 512M)"
echo "    P002 - init-db.sh 添加重试逻辑"
echo "    P003 - full-deploy.sh 自动调用 init-db.sh"
echo "    P004 - Nginx CORS OPTIONS 预检处理"
echo "    P005 - Docker 安装使用阿里云源"
echo "    P006 - .env.example 正确的 APP_SECRET"
echo "    P007 - docker-compose nacos 服务名修复"
echo ""
echo "  备份文件: $SCRIPT_DIR/*.bak.*"
echo ""
if [ -z "$COMPOSE_CMD" ]; then
    log_warn "请手动重启相关服务以应用补丁"
else
    log_ok "受影响的服务已自动重启"
fi
echo ""
