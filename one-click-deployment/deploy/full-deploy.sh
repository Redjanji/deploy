#!/bin/bash
# =====================================================
# XSS 微服务集群 - 一键部署脚本
# =====================================================
# 功能：
#   1. 自动拉取 deploy 目录（仅部署所需文件，不含源码）
#   2. 系统资源检查（内存/磁盘/CPU）
#   3. Docker 环境检查
#   4. 自动合并分片镜像并加载
#   5. 分批启动服务（infra → core → business → nginx）
#   6. 健康检查与部署结果汇报
#
# 使用方式：
#   bash full-deploy.sh              # 完整部署（自动拉取 deploy 目录）
#   bash full-deploy.sh --skip-load  # 跳过镜像加载（已加载过时）
#   bash full-deploy.sh --restart    # 重启已部署的服务
#   bash full-deploy.sh --no-pull    # 跳过远程拉取（已有 deploy 文件时）
#
# 服务器首次部署（一行命令）：
#   curl -fsSL https://gitee.com/redjanji_admin/deploy/raw/master/full-deploy.sh | bash
# =====================================================

set -euo pipefail

# 远程仓库配置
REMOTE_REPO="https://gitee.com/redjanji_admin/deploy.git"
REMOTE_DEPLOY_PATH="."

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 参数解析
SKIP_LOAD=false
RESTART_ONLY=false
NO_PULL=false
for arg in "$@"; do
    case "$arg" in
        --skip-load) SKIP_LOAD=true ;;
        --restart)   RESTART_ONLY=true ;;
        --no-pull)   NO_PULL=true ;;
        -h|--help)
            echo "Usage: bash full-deploy.sh [--skip-load] [--restart] [--no-pull]"
            echo ""
            echo "Options:"
            echo "  --skip-load  跳过镜像合并加载（已加载过时使用）"
            echo "  --restart    重启已部署的服务（不重新拉取和加载镜像）"
            echo "  --no-pull    跳过远程拉取（当前目录已有 deploy 文件时使用）"
            echo ""
            echo "首次部署（一行命令）："
            echo "  curl -fsSL https://gitee.com/redjanji_admin/deploy/raw/master/full-deploy.sh | bash"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $arg${NC}"
            exit 1
            ;;
    esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# =====================================================
# 工具函数
# =====================================================

log_info()  { echo -e "${BLUE}[INFO]${NC}  $1"; }
log_ok()    { echo -e "${GREEN}[ OK ]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[FAIL]${NC}  $1"; }

step() {
    local step_num="$1"
    local title="$2"
    echo ""
    echo "====================================================="
    echo "  Step $step_num: $title"
    echo "====================================================="
}

check_mem_mb() {
    free -m | awk '/^Mem:/ {print $2}'
}

check_mem_available_mb() {
    free -m | awk '/^Mem:/ {print $7}'
}

check_disk_gb() {
    df -BG "$SCRIPT_DIR" | awk 'NR==2 {gsub("G",""); print $4}'
}

check_cpu_cores() {
    nproc
}

# =====================================================
# Step -1: 从远程仓库拉取 deploy 目录
# =====================================================
pull_deploy_dir() {
    if [ "$NO_PULL" = true ]; then
        return 0
    fi

    # 检查当前目录是否已有 deploy 必需文件（含 sql 目录）
    if [ -f "docker-compose.yml" ] && [ -f "full-deploy.sh" ] && [ -d "sql" ] && [ "$(ls sql/*.sql 2>/dev/null | wc -l)" -gt 0 ]; then
        log_ok "当前目录已有完整的 deploy 文件，跳过拉取"
        return 0
    fi
    if [ -f "docker-compose.yml" ] && [ -f "full-deploy.sh" ]; then
        log_warn "当前目录有 deploy 文件但 sql 目录缺失或不完整，将重新拉取"
    fi

    step "-1" "从远程仓库拉取 deploy 目录"

    # 检查 git 是否可用
    if ! command -v git &> /dev/null; then
        log_error "git 未安装，请先安装 git：apt install -y git"
        exit 1
    fi

    # 如果当前目录是 home/root/tmp，自动切换到 /opt/xss-deploy
    local home_dir="$HOME"
    local deploy_target=""
    if [ "$SCRIPT_DIR" = "$home_dir" ] || [ "$SCRIPT_DIR" = "/root" ] || [[ "$SCRIPT_DIR" == /tmp/* ]]; then
        deploy_target="/opt/xss-deploy"
        log_info "当前目录为 $SCRIPT_DIR，尝试切换部署目录到: $deploy_target"
        
        if mkdir -p "$deploy_target" 2>/dev/null; then
            cd "$deploy_target"
            SCRIPT_DIR="$deploy_target"
            log_ok "部署目录切换成功: $deploy_target"
        else
            deploy_target="$home_dir/xss-deploy"
            log_warn "无法创建 $deploy_target（权限不足），尝试: $deploy_target"
            mkdir -p "$deploy_target"
            cd "$deploy_target"
            SCRIPT_DIR="$deploy_target"
            log_ok "部署目录切换成功: $deploy_target"
        fi
    fi

    local tmp_clone="/tmp/xss-deploy-clone-$$"
    log_info "拉取部署文件..."
    log_info "目标目录: $SCRIPT_DIR"

    # 判断是否需要 sparse-checkout
    if [ "$REMOTE_DEPLOY_PATH" = "." ] || [ -z "$REMOTE_DEPLOY_PATH" ]; then
        # 整个仓库都是部署文件，直接浅克隆
        log_info "直接克隆整个仓库（浅克隆）..."
        git clone --depth 1 "$REMOTE_REPO" "$tmp_clone"
        cd "$tmp_clone"
        local src_dir="$tmp_clone"
    else
        log_info "使用 sparse checkout 只拉取 $REMOTE_DEPLOY_PATH 目录..."
        # 浅克隆 + sparse checkout
        git clone --depth 1 --sparse "$REMOTE_REPO" "$tmp_clone"
        cd "$tmp_clone"
        git sparse-checkout set "$REMOTE_DEPLOY_PATH"
        local src_dir="$tmp_clone/$REMOTE_DEPLOY_PATH"
    fi

    # 将 deploy 目录内容复制到脚本所在目录
    if [ ! -d "$src_dir" ]; then
        log_error "未找到部署目录: $src_dir"
        rm -rf "$tmp_clone"
        exit 1
    fi

    log_info "复制部署文件到 $SCRIPT_DIR ..."
    cp -r "$src_dir"/. "$SCRIPT_DIR/"

    # 清理临时目录
    rm -rf "$tmp_clone"
    cd "$SCRIPT_DIR"

    # 验证关键文件
    local required_files=("docker-compose.yml" "full-deploy.sh" ".env.example")
    for f in "${required_files[@]}"; do
        if [ ! -f "$f" ]; then
            log_error "拉取后缺少必需文件: $f"
            exit 1
        fi
    done

    # 验证 SQL 目录
    if [ ! -d "sql" ]; then
        log_error "拉取后缺少 sql 目录"
        exit 1
    fi
    local sql_count=$(ls sql/*.sql 2>/dev/null | wc -l)
    if [ "$sql_count" -eq 0 ]; then
        log_error "sql 目录为空，请检查远程仓库"
        exit 1
    fi

    # 统计拉取的文件
    local file_count=$(find . -type f | wc -l)
    local total_size=$(du -sh . | cut -f1)
    log_ok "拉取完成: $file_count 个文件, 总计 $total_size"
    log_info "  - 镜像分片: $(ls xss-images-*.tar.*.part* 2>/dev/null | wc -l) 个"
    log_info "  - SQL 脚本: $sql_count 个"
    log_info "  - 部署脚本: full-deploy.sh, deploy-all.sh, init-db.sh"
    log_info ""
    log_info "部署目录: $SCRIPT_DIR"
    log_info "后续可在此目录执行: bash full-deploy.sh --no-pull"
}

# =====================================================
# Step 0: 系统资源检查
# =====================================================
check_system_resources() {
    step "0" "系统资源检查"

    local mem_total=$(check_mem_mb)
    local mem_avail=$(check_mem_available_mb)
    local disk_free=$(check_disk_gb)
    local cpu_cores=$(check_cpu_cores)

    log_info "内存总量: ${mem_total}MB | 可用: ${mem_avail}MB"
    log_info "磁盘可用: ${disk_free}GB"
    log_info "CPU 核心数: ${cpu_cores}"

    # 最低要求检查
    local mem_min=2048
    local disk_min=5
    local cpu_min=2

    if [ "$mem_total" -lt "$mem_min" ]; then
        log_error "内存不足！最低要求 ${mem_min}MB，当前 ${mem_total}MB"
        exit 1
    fi

    if [ "$disk_free" -lt "$disk_min" ]; then
        log_error "磁盘空间不足！最低要求 ${disk_min}GB，当前 ${disk_free}GB"
        exit 1
    fi

    if [ "$cpu_cores" -lt "$cpu_min" ]; then
        log_error "CPU 核心数不足！最低要求 ${cpu_min}核，当前 ${cpu_cores}核"
        exit 1
    fi

    # 警告（非阻塞）
    if [ "$mem_avail" -lt 1024 ]; then
        log_warn "可用内存低于 1GB，部署过程可能较慢，建议关闭其他程序"
    fi

    if [ "$mem_total" -lt 3584 ]; then
        log_warn "内存低于 3.5GB，部分服务可能需要调整资源限制"
    fi

    log_ok "系统资源检查通过"
}

# =====================================================
# 自动安装 Docker（国内镜像源）
# =====================================================
install_docker() {
    log_info "检测到 Docker 未安装，正在自动安装..."

    # 检测系统发行版
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        local os_id="$ID"
        local os_version="$VERSION_ID"
    else
        log_error "无法检测系统版本，请手动安装 Docker"
        exit 1
    fi

    log_info "系统: $os_id $os_version"

    case "$os_id" in
        ubuntu|debian)
            log_info "使用 apt 安装 Docker（使用国内阿里云镜像源）..."
            # 更新包索引
            apt-get update -y
            # 安装依赖
            apt-get install -y ca-certificates curl gnupg lsb-release
            # 添加 Docker GPG key（使用阿里云镜像，解决 TLS 连接问题）
            install -m 0755 -d /etc/apt/keyrings
            log_info "下载 Docker GPG key（阿里云镜像）..."
            if curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/$os_id/gpg -o /tmp/docker.gpg; then
                gpg --dearmor -o /etc/apt/keyrings/docker.gpg /tmp/docker.gpg
                rm -f /tmp/docker.gpg
            else
                log_warn "阿里云 GPG key 下载失败，尝试官方源..."
                curl -fsSL https://download.docker.com/linux/$os_id/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
            fi
            chmod a+r /etc/apt/keyrings/docker.gpg
            # 添加 Docker 仓库（使用阿里云镜像）
            log_info "配置 Docker 软件源（阿里云镜像）..."
            echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.aliyun.com/docker-ce/linux/$os_id $(lsb_release -cs) stable" > /etc/apt/sources.list.d/docker.list
            # 安装 Docker
            apt-get update -y
            apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
            ;;
        centos|rhel|rocky|almalinux)
            log_info "使用 yum/dnf 安装 Docker（使用国内阿里云镜像源）..."
            # 安装 yum-utils
            if command -v dnf &> /dev/null; then
                dnf install -y dnf-plugins-core
                dnf config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
                dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
            else
                yum install -y yum-utils
                yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
                yum install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
            fi
            # 启动 Docker
            systemctl enable docker
            systemctl start docker
            ;;
        *)
            log_error "不支持的系统: $os_id，请手动安装 Docker"
            log_error "参考: https://docs.docker.com/engine/install/"
            exit 1
            ;;
    esac

    # 配置 Docker 国内镜像加速
    log_info "配置 Docker 国内镜像加速..."
    mkdir -p /etc/docker
    cat > /etc/docker/daemon.json << 'EOF'
{
    "registry-mirrors": [
        "https://registry.cn-hangzhou.aliyuncs.com",
        "https://docker.m.daocloud.io",
        "https://mirror.baidubce.com"
    ]
}
EOF
    # 重启 Docker 服务使配置生效
    if command -v systemctl &> /dev/null; then
        systemctl daemon-reload
        systemctl restart docker
    elif command -v service &> /dev/null; then
        service docker restart
    fi
    sleep 3

    # 验证安装
    if command -v docker &> /dev/null; then
        log_ok "Docker 安装完成: $(docker --version | awk '{print $3}')"
        log_ok "Docker 镜像加速已配置"
    else
        log_error "Docker 安装失败，请手动安装"
        exit 1
    fi
}

# =====================================================
# Step 1: Docker 环境检查
# =====================================================
check_docker() {
    cd "$SCRIPT_DIR"
    step "1" "Docker 环境检查"

    if ! command -v docker &> /dev/null; then
        # 检查是否为 root
        if [ "$(id -u)" -ne 0 ]; then
            log_error "Docker 未安装，且当前非 root 用户"
            log_error "请使用 sudo 运行: sudo bash full-deploy.sh"
            exit 1
        fi
        install_docker
    else
        log_ok "Docker 已安装: $(docker --version | awk '{print $3}')"
    fi

    # 检查 Docker 服务是否运行
    if ! docker info &> /dev/null; then
        log_warn "Docker 服务未运行，尝试启动..."
        local docker_started=false
        
        if command -v systemctl &> /dev/null; then
            if systemctl start docker &> /dev/null; then
                docker_started=true
            elif sudo -n systemctl start docker &> /dev/null; then
                docker_started=true
            fi
        elif command -v service &> /dev/null; then
            if service docker start &> /dev/null; then
                docker_started=true
            elif sudo -n service docker start &> /dev/null; then
                docker_started=true
            fi
        fi
        
        if [ "$docker_started" = true ]; then
            sleep 3
            if docker info &> /dev/null; then
                log_ok "Docker 服务已启动"
            else
                docker_started=false
            fi
        fi
        
        if [ "$docker_started" = false ]; then
            log_warn "Docker 服务启动失败（权限不足）"
            log_warn "请在另一个终端中执行：sudo systemctl start docker"
            log_warn "或使用 sudo 运行本脚本：sudo bash full-deploy.sh"
            log_warn ""
            log_warn "等待 Docker 服务启动..."
            
            local wait_count=0
            while [ "$wait_count" -lt 120 ]; do
                if docker info &> /dev/null; then
                    log_ok "Docker 服务已启动"
                    docker_started=true
                    break
                fi
                if [ $((wait_count % 10)) -eq 0 ]; then
                    log_info "等待中... ($((wait_count / 10))/12)"
                fi
                sleep 1
                wait_count=$((wait_count + 1))
            done
            
            if [ "$docker_started" = false ]; then
                log_error "等待超时！请手动启动 Docker 后重新运行"
                exit 1
            fi
        fi
    fi
    log_ok "Docker 服务运行中"

    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        log_error "Docker Compose 未安装，请先安装 Docker Compose"
        exit 1
    fi

    if docker compose version &> /dev/null; then
        COMPOSE_CMD="docker compose"
        log_ok "Docker Compose V2: $(docker compose version | awk '{print $4}')"
    else
        COMPOSE_CMD="docker-compose"
        log_ok "Docker Compose V1: $(docker-compose --version | awk '{print $3}')"
    fi

    # 检查 .env 文件
    if [ ! -f .env ]; then
        if [ -f .env.example ]; then
            log_warn ".env 文件不存在，从 .env.example 复制默认配置"
            cp .env.example .env
        else
            log_warn ".env 文件不存在，将使用 docker-compose.yml 中的默认值"
        fi
    else
        log_ok ".env 配置文件已就绪"
    fi
}

# =====================================================
# Step 2: 合并并加载 Docker 镜像
# =====================================================
load_images() {
    cd "$SCRIPT_DIR"
    if [ "$SKIP_LOAD" = true ]; then
        step "2" "跳过镜像加载（--skip-load）"
        return 0
    fi

    step "2" "合并并加载 Docker 镜像"

    # 查找分片文件（支持 .tar.part* 和 .tar.gz.part*）
    local part_files=($(ls -1 xss-images-*.tar.*.part* 2>/dev/null | sort))
    local part_count=${#part_files[@]}

    if [ "$part_count" -eq 0 ]; then
        if ls xss-images-*.tar &> /dev/null; then
            local tar_file=$(ls -1 xss-images-*.tar | head -1)
            log_info "找到完整镜像文件: $tar_file"
        elif ls xss-images-*.tar.gz &> /dev/null; then
            local tar_file=$(ls -1 xss-images-*.tar.gz | head -1)
            log_info "找到完整镜像文件（压缩）: $tar_file"
        else
            log_warn "未找到镜像分片文件（xss-images-*.tar.part* 或 xss-images-*.tar.gz.part*），跳过镜像加载"
            log_warn "如果是首次部署，请确保镜像分片文件在 deploy/ 目录下"
            return 0
        fi
    else
        log_info "找到 $part_count 个镜像分片文件"

        # 推导 tar 文件名（去掉 .partXX 后缀）
        local first_part="${part_files[0]}"
        local tar_file="${first_part%.part*}"

        # 检查是否已合并
        if [ -f "$tar_file" ]; then
            log_info "镜像文件已存在: $tar_file，跳过合并"
        else
            log_info "正在合并分片到: $tar_file ..."
            cat "${part_files[@]}" > "$tar_file"
            log_ok "合并完成: $(du -h "$tar_file" | cut -f1)"
        fi
    fi

    # 加载镜像
    log_info "正在加载 Docker 镜像（可能需要 1-3 分钟）..."
    local load_start=$(date +%s)
    
    if [[ "$tar_file" == *.tar.gz ]]; then
        gzip -dc "$tar_file" | docker load
    else
        docker load -i "$tar_file"
    fi
    
    local load_end=$(date +%s)
    local load_duration=$((load_end - load_start))

    log_ok "镜像加载完成，耗时 ${load_duration} 秒"

    # 验证镜像
    log_info "已加载的业务镜像:"
    docker images --format "{{.Repository}}:{{.Tag}} {{.Size}}" | grep "^xss/" | sort | while read line; do
        echo "       $line"
    done

    # 清理 tar 文件（释放磁盘空间）
    if [ "$part_count" -gt 0 ] && [ -f "$tar_file" ]; then
        log_info "清理合并后的文件以释放磁盘空间..."
        rm -f "$tar_file"
        log_ok "已清理"
    fi
}

# =====================================================
# Step 3: 启动中间件层
# =====================================================
start_infra() {
    cd "$SCRIPT_DIR"
    step "3" "启动中间件层（infra）"

    log_info "启动服务: mysql, redis, rabbitmq, elasticsearch, minio, nacos"
    $COMPOSE_CMD --profile infra up -d

    log_info "等待中间件健康检查通过（最长约 3 分钟）..."
    local infra_services=("xss-mysql" "xss-redis" "xss-rabbitmq" "xss-elasticsearch" "xss-minio" "xss-nacos")
    local all_healthy=true
    local max_wait=180
    local waited=0

    for svc in "${infra_services[@]}"; do
        log_info "等待 $svc ..."
        waited=0
        while [ "$waited" -lt "$max_wait" ]; do
            if docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null | grep -q "healthy"; then
                log_ok "  $svc is healthy"
                break
            fi
            sleep 5
            waited=$((waited + 5))
        done
        if [ "$waited" -ge "$max_wait" ]; then
            log_error "  $svc 超时未就绪"
            all_healthy=false
        fi
    done

    if [ "$all_healthy" = false ]; then
        log_error "部分中间件服务未就绪，请检查日志: docker logs <container>"
        exit 1
    fi

    log_ok "所有中间件服务已就绪"
}

# =====================================================
# Step 4: 初始化数据库和 Nacos 配置
# =====================================================
init_config() {
    cd "$SCRIPT_DIR"
    step "4" "初始化数据库与配置"

    # 检查数据库是否已被 docker-entrypoint-initdb.d 自动初始化
    log_info "检查数据库初始化状态..."

    # 读取 MySQL 密码
    local mysql_pwd="${MYSQL_ROOT_PASSWORD:-root}"
    if [ -f .env ]; then
        mysql_pwd=$(grep '^MYSQL_ROOT_PASSWORD=' .env | sed 's/^MYSQL_ROOT_PASSWORD=//' || echo "root")
    fi

    # 等待 MySQL 完全稳定
    log_info "等待 MySQL 稳定运行..."
    local stable_count=0
    local waited=0
    while [ "$waited" -lt 60 ]; do
        if docker exec -e MYSQL_PWD="$mysql_pwd" xss-mysql mysql -uroot -e "SELECT 1" &>/dev/null; then
            stable_count=$((stable_count + 1))
            if [ "$stable_count" -ge 2 ]; then
                break
            fi
        else
            stable_count=0
        fi
        sleep 5
        waited=$((waited + 5))
    done

    # 检查已有数据库数量
    local expected_dbs=("auth_db" "dict_db" "property_db" "image_db" "analytics_db" "message_db" "favorite_db" "review_db" "booking_db")
    local db_count=0
    for db in "${expected_dbs[@]}"; do
        if docker exec -e MYSQL_PWD="$mysql_pwd" xss-mysql mysql -uroot -e "USE $db;" 2>/dev/null; then
            db_count=$((db_count + 1))
        fi
    done

    log_info "已初始化数据库: $db_count / ${#expected_dbs[@]}"

    # 如果数据库不完整，手动执行 init-db.sh
    if [ "$db_count" -lt "${#expected_dbs[@]}" ]; then
        log_warn "数据库未完全初始化（$db_count/${#expected_dbs[@]}），执行手动初始化..."
        if [ -f "./init-db.sh" ]; then
            chmod +x ./init-db.sh
            bash ./init-db.sh
            local init_ret=$?
            if [ "$init_ret" -ne 0 ]; then
                log_error "数据库初始化失败，请手动检查"
                exit 1
            fi
        else
            log_error "init-db.sh 不存在，无法初始化数据库"
            exit 1
        fi
    else
        log_ok "数据库已完全初始化"
    fi

    # Nacos 配置初始化
    if [ -d "./nacos-config" ]; then
        log_info "Nacos 配置目录已就绪"
    fi

    log_ok "配置初始化完成"
}

# =====================================================
# Step 5: 启动核心业务服务
# =====================================================
start_core() {
    cd "$SCRIPT_DIR"
    step "5" "启动核心业务服务（core）"

    log_info "启动服务: gateway, auth, property, dict"
    $COMPOSE_CMD --profile infra --profile core up -d

    log_info "等待核心服务就绪（最长约 2 分钟）..."
    local core_services=("xss-gateway" "xss-auth" "xss-property" "xss-dict")
    local all_healthy=true
    local max_wait=120

    for svc in "${core_services[@]}"; do
        log_info "等待 $svc ..."
        local waited=0
        while [ "$waited" -lt "$max_wait" ]; do
            if docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null | grep -q "healthy"; then
                log_ok "  $svc is healthy"
                break
            fi
            # 检查容器是否在运行（可能没有 healthcheck）
            if docker inspect --format='{{.State.Running}}' "$svc" 2>/dev/null | grep -q "true"; then
                # 没有健康检查的服务，运行中即视为就绪
                if ! docker inspect --format='{{.State.Health}}' "$svc" 2>/dev/null | grep -q "Health:"; then
                    log_ok "  $svc is running"
                    break
                fi
            fi
            sleep 5
            waited=$((waited + 5))
        done
        if [ "$waited" -ge "$max_wait" ]; then
            log_warn "  $svc 可能未完全就绪（继续后续步骤）"
        fi
    done

    log_ok "核心业务服务启动完成"
}

# =====================================================
# Step 6: 启动其他业务服务
# =====================================================
start_business() {
    cd "$SCRIPT_DIR"
    step "6" "启动其他业务服务（business）"

    log_info "启动服务: image, search, analytics, message, favorite, review, booking"
    $COMPOSE_CMD --profile infra --profile core --profile business up -d

    log_info "等待业务服务启动中（约 1-2 分钟）..."
    # 不等待所有服务就绪（数量多，节省时间），直接给个固定等待
    sleep 30

    # 检查关键服务状态
    local business_services=("xss-image" "xss-search" "xss-analytics" "xss-message" "xss-favorite" "xss-review" "xss-booking")
    local running=0
    for svc in "${business_services[@]}"; do
        if docker inspect --format='{{.State.Running}}' "$svc" 2>/dev/null | grep -q "true"; then
            running=$((running + 1))
        else
            log_warn "  $svc 未在运行"
        fi
    done

    log_ok "业务服务启动完成: $running / ${#business_services[@]} 运行中"
}

# =====================================================
# Step 7: 启动 Nginx
# =====================================================
start_nginx() {
    cd "$SCRIPT_DIR"
    step "7" "启动 Nginx 反向代理"

    $COMPOSE_CMD --profile infra --profile core --profile business --profile nginx up -d

    local waited=0
    local max_wait=30
    while [ "$waited" -lt "$max_wait" ]; do
        if docker inspect --format='{{.State.Health.Status}}' xss-nginx 2>/dev/null | grep -q "healthy"; then
            break
        fi
        sleep 3
        waited=$((waited + 3))
    done

    if docker inspect --format='{{.State.Running}}' xss-nginx 2>/dev/null | grep -q "true"; then
        log_ok "Nginx 已启动"
    else
        log_warn "Nginx 启动失败"
    fi
}

# =====================================================
# Step 8: 部署结果汇总
# =====================================================
report_status() {
    step "8" "部署结果汇总"

    echo ""
    echo "  容器状态："
    echo "  ────────────────────────────────────────────────"
    docker ps --format "table  {{.Names}}\t{{.Status}}\t{{.Size}}" | sort

    local total=$(docker ps -a --filter "name=xss-" -q | wc -l)
    local running=$(docker ps --filter "name=xss-" --filter "status=running" -q | wc -l)
    local healthy=0
    for c in $(docker ps --filter "name=xss-" -q); do
        if docker inspect --format='{{.State.Health.Status}}' "$c" 2>/dev/null | grep -q "healthy"; then
            healthy=$((healthy + 1))
        fi
    done

    echo ""
    echo "  汇总："
    echo "    总容器数: $total"
    echo "    运行中:   $running"
    echo "    健康:     $healthy"
    echo ""
    echo "  访问入口："
    echo "    API 网关:  http://<服务器IP>:8080"
    echo "    Nginx:    http://<服务器IP>:80"
    echo "    Nacos:    http://<服务器IP>:8848/nacos"
    echo "    MinIO:    http://<服务器IP>:9001"
    echo "    RabbitMQ: http://<服务器IP>:15672"
    echo ""

    if [ "$running" -eq "$total" ]; then
        log_ok "所有容器运行中！部署成功 🎉"
    else
        log_warn "部分容器未运行，请检查日志排查问题"
    fi
}

# =====================================================
# 重启模式
# =====================================================
restart_all() {
    step "重启" "重启所有服务"

    check_docker

    log_info "停止所有服务..."
    $COMPOSE_CMD --profile infra --profile core --profile business --profile nginx down

    log_info "等待 5 秒..."
    sleep 5

    log_info "重新启动..."
    start_infra
    init_config
    start_core
    start_business
    start_nginx
    report_status
}

# =====================================================
# 主流程
# =====================================================

main() {
    echo ""
    echo "╔══════════════════════════════════════════════════╗"
    echo "║      XSS 微服务集群 - 一键部署脚本              ║"
    echo "╚══════════════════════════════════════════════════╝"
    echo ""
    echo "  部署目录: $SCRIPT_DIR"
    echo "  模式: $( [ "$RESTART_ONLY" = true ] && echo '重启' || echo '完整部署' )"
    echo "  跳过镜像加载: $SKIP_LOAD"
    echo "  跳过远程拉取: $NO_PULL"
    echo ""

    if [ "$RESTART_ONLY" = true ]; then
        restart_all
        exit 0
    fi

    pull_deploy_dir
    check_system_resources
    check_docker
    load_images
    start_infra
    init_config
    start_core
    start_business
    start_nginx
    report_status
}

main "$@"
