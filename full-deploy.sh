#!/bin/bash
# =====================================================
# XSS 微服务集群 - 一键部署脚本 (增强版 v2.1)
# =====================================================
# 特性：
#   - 自动配置 Docker 镜像加速器（国内源）
#   - 镜像拉取自动重试 + 超时控制 + 备用仓库切换
#   - 启动前强制校验必需镜像存在，避免无限卡死
#   - 网络诊断扩展（检查镜像加速器可达性）
# =====================================================

set -euo pipefail

# 远程仓库配置（Gitee）
REMOTE_REPO="https://gitee.com/redjanji_admin/deploy.git"
REMOTE_DEPLOY_PATH="."

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# ===================== 参数解析 =====================
SKIP_LOAD=false
RESTART_ONLY=false
NO_PULL=false
for arg in "$@"; do
    case "$arg" in
        --skip-load) SKIP_LOAD=true ;;
        --restart)   RESTART_ONLY=true ;;
        --no-pull)   NO_PULL=true ;;
        -h|--help)
            echo "Usage: sudo bash full-deploy.sh [--skip-load] [--restart] [--no-pull]"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $arg${NC}"
            exit 1
            ;;
    esac
done

# 必须使用 root 运行
if [ "$(id -u)" -ne 0 ]; then
    echo -e "${RED}[FAIL] 请使用 sudo 运行：sudo bash $0${NC}"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ===================== 工具函数 =====================
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

check_mem_mb() { free -m | awk '/^Mem:/ {print $2}'; }
check_mem_available_mb() { free -m | awk '/^Mem:/ {print $7}'; }
check_disk_gb() { df -BG "$SCRIPT_DIR" | awk 'NR==2 {gsub("G",""); print $4}'; }
check_cpu_cores() { nproc; }

# ===================== 强制清理残留容器 =====================
force_clean_containers() {
    local containers=$(docker ps -a --filter "name=xss-" -q 2>/dev/null)
    if [ -n "$containers" ]; then
        log_warn "检测到残留容器，正在清理（数据卷保留）..."
        docker rm -f $containers >/dev/null 2>&1 || true
        log_ok "残留容器已清理"
    fi
}

# ===================== 网络连通性诊断（增强版） =====================
network_diag() {
    step "0.5" "网络连通性诊断"
    local test_host="registry-1.docker.io"
    local test_port=443
    if timeout 3 bash -c "echo >/dev/tcp/$test_host/$test_port" 2>/dev/null; then
        log_ok "Docker Hub 可达"
    else
        log_warn "无法连接 Docker Hub (registry-1.docker.io)"
    fi

    # 检查已配置的镜像加速器连通性
    local mirrors=$(docker info 2>/dev/null | awk -F ': ' '/Registry Mirrors/{getline; while($0 ~ /^ /) {print $1; getline}}')
    if [ -n "$mirrors" ]; then
        for mirror in $mirrors; do
            local host=$(echo "$mirror" | awk -F/ '{print $3}')
            if timeout 3 bash -c "echo >/dev/tcp/$host/443" 2>/dev/null; then
                log_ok "镜像加速器可达: $mirror"
            else
                log_warn "镜像加速器不可达: $mirror"
            fi
        done
    else
        log_warn "未检测到镜像加速器配置，后续将自动配置"
    fi
    log_warn "若镜像拉取失败，部署将继续但可能较慢或失败。"
}

# ===================== 关键文件检查 =====================
check_required_files() {
    local required_files=("docker-compose.yml" "full-deploy.sh")
    local missing=()
    for f in "${required_files[@]}"; do
        [ -f "$f" ] || missing+=("$f")
    done
    if [ ! -d "sql" ]; then
        missing+=("sql/")
    elif [ "$(ls sql/*.sql 2>/dev/null | wc -l)" -eq 0 ]; then
        missing+=("sql/ (空)")
    fi

    if [ ${#missing[@]} -gt 0 ]; then
        log_error "缺少必需文件或目录: ${missing[*]}"
        return 1
    fi
    return 0
}

# ===================== Step -1: 拉取/更新部署文件 =====================
pull_deploy_dir() {
    if [ "$NO_PULL" = true ]; then
        log_info "--no-pull 模式，跳过远程拉取"
        check_required_files || exit 1
        return 0
    fi

    if git rev-parse --git-dir > /dev/null 2>&1; then
        local current_remote=$(git remote get-url origin 2>/dev/null || true)
        if [ "$current_remote" = "$REMOTE_REPO" ]; then
            step "-1" "更新部署代码 (git pull)"
            log_info "检测到本地仓库，尝试拉取最新代码..."
            if git pull --ff-only 2>&1 | tee /dev/stderr; then
                log_ok "代码已更新到最新"
                check_required_files || exit 1
                log_info "部署目录: $SCRIPT_DIR"
                return 0
            else
                log_warn "git pull 失败（可能网络问题或存在冲突）"
                if check_required_files 2>/dev/null; then
                    log_warn "将使用现有文件继续部署（可能不是最新版本）"
                    return 0
                else
                    log_error "本地文件不完整且无法拉取，部署终止"
                    exit 1
                fi
            fi
        else
            log_warn "当前目录是 Git 仓库但 remote 不匹配 ($current_remote)"
            log_warn "将忽略该仓库，重新克隆部署文件"
        fi
    fi

    if [ ! -d .git ] && check_required_files 2>/dev/null; then
        log_ok "当前目录已有完整的 deploy 文件，跳过拉取"
        return 0
    fi

    step "-1" "从远程仓库拉取 deploy 目录"
    if ! command -v git &> /dev/null; then
        log_error "git 未安装，请先安装 git：apt install -y git"
        exit 1
    fi

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
            log_warn "无法创建 $deploy_target，尝试: $deploy_target"
            mkdir -p "$deploy_target"
            cd "$deploy_target"
            SCRIPT_DIR="$deploy_target"
            log_ok "部署目录切换成功: $deploy_target"
        fi
    fi

    local tmp_clone="/tmp/xss-deploy-clone-$$"
    log_info "拉取部署文件..."
    log_info "目标目录: $SCRIPT_DIR"
    if [ "$REMOTE_DEPLOY_PATH" = "." ] || [ -z "$REMOTE_DEPLOY_PATH" ]; then
        log_info "直接克隆整个仓库（浅克隆）..."
        git clone --depth 1 "$REMOTE_REPO" "$tmp_clone"
        cd "$tmp_clone"
        local src_dir="$tmp_clone"
    else
        log_info "使用 sparse checkout 只拉取 $REMOTE_DEPLOY_PATH 目录..."
        git clone --depth 1 --sparse "$REMOTE_REPO" "$tmp_clone"
        cd "$tmp_clone"
        git sparse-checkout set "$REMOTE_DEPLOY_PATH"
        local src_dir="$tmp_clone/$REMOTE_DEPLOY_PATH"
    fi

    if [ ! -d "$src_dir" ]; then
        log_error "未找到部署目录: $src_dir"
        rm -rf "$tmp_clone"
        exit 1
    fi

    log_info "复制部署文件到 $SCRIPT_DIR ..."
    cp -r "$src_dir"/. "$SCRIPT_DIR/"
    rm -rf "$tmp_clone"
    cd "$SCRIPT_DIR"

    check_required_files || exit 1

    local file_count=$(find . -type f | wc -l)
    local total_size=$(du -sh . | cut -f1)
    log_ok "拉取完成: $file_count 个文件, 总计 $total_size"
    log_info "  - 镜像分片: $(ls xss-images-*.tar.*.part* 2>/dev/null | wc -l) 个"
    log_info "  - SQL 脚本: $(ls sql/*.sql 2>/dev/null | wc -l) 个"
    log_info "部署目录: $SCRIPT_DIR"
    log_info "后续可在此目录执行: sudo bash full-deploy.sh --no-pull"
}

# ===================== Step 0: 系统资源检查 =====================
check_system_resources() {
    step "0" "系统资源检查"
    local mem_total=$(check_mem_mb)
    local mem_avail=$(check_mem_available_mb)
    local disk_free=$(check_disk_gb)
    local cpu_cores=$(check_cpu_cores)

    log_info "内存总量: ${mem_total}MB | 可用: ${mem_avail}MB"
    log_info "磁盘可用: ${disk_free}GB"
    log_info "CPU 核心数: ${cpu_cores}"

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

    if [ "$mem_avail" -lt 1024 ]; then
        log_warn "可用内存低于 1GB，部署过程可能较慢，建议关闭其他程序"
    fi
    if [ "$mem_total" -lt 3584 ]; then
        log_warn "内存低于 3.5GB，部分服务可能需要调整资源限制"
    fi
    log_ok "系统资源检查通过"
}

# ===================== 自动安装 Docker（含镜像加速） =====================
install_docker() {
    log_info "检测到 Docker 未安装，正在自动安装..."
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        local os_id="$ID"
    else
        log_error "无法检测系统版本，请手动安装 Docker"
        exit 1
    fi

    case "$os_id" in
        ubuntu|debian)
            apt-get update -y
            apt-get install -y ca-certificates curl gnupg lsb-release
            install -m 0755 -d /etc/apt/keyrings
            curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/$os_id/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
            chmod a+r /etc/apt/keyrings/docker.gpg
            echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.aliyun.com/docker-ce/linux/$os_id $(lsb_release -cs) stable" > /etc/apt/sources.list.d/docker.list
            apt-get update -y
            apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
            ;;
        centos|rhel|rocky|almalinux)
            if command -v dnf &> /dev/null; then
                dnf install -y dnf-plugins-core
                dnf config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
                dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
            else
                yum install -y yum-utils
                yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
                yum install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
            fi
            systemctl enable docker
            systemctl start docker
            ;;
        *)
            log_error "不支持的系统: $os_id，请手动安装 Docker"
            exit 1
            ;;
    esac

    if command -v docker &> /dev/null; then
        log_ok "Docker 安装完成: $(docker --version | awk '{print $3}')"
    else
        log_error "Docker 安装失败"
        exit 1
    fi
}

# ===================== 确保镜像加速器配置 =====================
ensure_registry_mirrors() {
    local daemon_file="/etc/docker/daemon.json"
    local mirrors=(
        "https://registry.cn-hangzhou.aliyuncs.com"
        "https://docker.m.daocloud.io"
        "https://mirror.baidubce.com"
    )

    local mirrors_json=""
    if command -v jq &> /dev/null; then
        mirrors_json=$(printf '%s\n' "${mirrors[@]}" | jq -R . | jq -s -c .)
    else
        mirrors_json="["
        for m in "${mirrors[@]}"; do
            mirrors_json+="\"$m\", "
        done
        mirrors_json="${mirrors_json%, }]"
    fi

    if [ ! -f "$daemon_file" ]; then
        log_info "未找到 /etc/docker/daemon.json，自动创建并配置镜像加速..."
        mkdir -p /etc/docker
        cat > "$daemon_file" <<-EOF
{
    "registry-mirrors": $mirrors_json
}
EOF
        systemctl daemon-reload
        systemctl restart docker
        sleep 3
        log_ok "镜像加速配置已写入"
        return 0
    fi

    if ! grep -q '"registry-mirrors"' "$daemon_file"; then
        log_warn "daemon.json 未配置 registry-mirrors，正在自动添加..."
        cp "$daemon_file" "$daemon_file.bak.$(date +%s)"
        if command -v jq &> /dev/null; then
            local tmp_file=$(mktemp)
            jq --argjson mirrors "$mirrors_json" \
               '. + {"registry-mirrors": $mirrors}' "$daemon_file" > "$tmp_file"
            mv "$tmp_file" "$daemon_file"
        else
            log_warn "未安装 jq，将直接覆盖 daemon.json 并保留原有内容（请手动验证）"
            local tmp_content=$(sed '$d' "$daemon_file")
            tmp_content+=", \"registry-mirrors\": $mirrors_json }"
            echo "$tmp_content" > "$daemon_file"
        fi
        systemctl daemon-reload
        systemctl restart docker
        sleep 3
        log_ok "镜像加速已添加并生效"
    else
        log_ok "镜像加速器已配置"
    fi
}

# ===================== Step 1: Docker 环境检查 =====================
check_docker() {
    cd "$SCRIPT_DIR"
    step "1" "Docker 环境检查"

    if ! command -v docker &> /dev/null; then
        install_docker
    else
        log_ok "Docker 已安装: $(docker --version | awk '{print $3}')"
    fi

    if ! docker info &> /dev/null; then
        log_warn "Docker 服务未运行，尝试启动..."
        systemctl start docker &>/dev/null || service docker start &>/dev/null || true
        sleep 3
    fi

    if ! docker info &> /dev/null; then
        log_error "Docker 服务启动失败，请手动启动后重试"
        exit 1
    fi
    log_ok "Docker 服务运行中"

    ensure_registry_mirrors

    if docker compose version &> /dev/null; then
        COMPOSE_CMD="docker compose"
        log_ok "Docker Compose V2: $(docker compose version | awk '{print $4}')"
    elif command -v docker-compose &> /dev/null; then
        COMPOSE_CMD="docker-compose"
        log_ok "Docker Compose V1: $(docker-compose --version | awk '{print $3}')"
    else
        log_error "Docker Compose 未安装"
        exit 1
    fi

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

    force_clean_containers
}

# ===================== 端口占用检查 =====================
check_port_conflicts() {
    step "1.5" "端口占用检查"
    local ports=(3306 6379 5672 9200 9000 9001 8848 8080 80)
    local occupied=()
    local cmd=""
    if command -v ss &> /dev/null; then
        cmd="ss -tlnp"
    elif command -v netstat &> /dev/null; then
        cmd="netstat -tlnp"
    else
        log_warn "无法检查端口占用（缺少 ss 或 netstat）"
        return
    fi

    for p in "${ports[@]}"; do
        if $cmd 2>/dev/null | grep -q ":$p "; then
            occupied+=("$p")
        fi
    done

    if [ ${#occupied[@]} -gt 0 ]; then
        log_warn "以下端口已被占用: ${occupied[*]}"
        log_warn "可能导致对应服务启动失败，请关闭占用进程或修改 docker-compose.yml 端口映射"
    else
        log_ok "所有关键端口空闲"
    fi
}

# ===================== 智能拉取镜像（超时控制 + 备用仓库） =====================
smart_pull() {
    local image="$1"
    local timeout_sec=60   # 每次拉取超时秒数
    local fallback_images=()

    case "$image" in
        mysql:*|redis:*|rabbitmq:*|nginx:*)
            fallback_images+=("registry.cn-hangzhou.aliyuncs.com/library/${image}")
            ;;
        minio/minio:*)
            fallback_images+=("quay.io/minio/minio:latest")
            fallback_images+=("registry.cn-hangzhou.aliyuncs.com/minio/minio:latest")
            fallback_images+=("docker.m.daocloud.io/minio/minio:latest")
            ;;
        nacos/nacos-server:*)
            fallback_images+=("registry.cn-hangzhou.aliyuncs.com/nacos/nacos-server:v2.3.2")
            fallback_images+=("docker.m.daocloud.io/nacos/nacos-server:v2.3.2")
            ;;
        docker.elastic.co/*)
            fallback_images+=("registry.cn-hangzhou.aliyuncs.com/elastic/elasticsearch:8.11.0")
            fallback_images+=("docker.m.daocloud.io/elasticsearch/elasticsearch:8.11.0")
            ;;
    esac

    # 尝试主镜像，每次超时自动终止
    for i in {1..3}; do
        log_info "拉取 $image (尝试 $i/3, 超时 ${timeout_sec}s)..."
        if timeout ${timeout_sec} docker pull "$image" 2>/dev/null; then
            return 0
        fi
        log_warn "拉取失败或超时，重试..."
        sleep 2
    done

    # 备用仓库
    for fallback in "${fallback_images[@]}"; do
        log_info "尝试备用仓库: $fallback (超时 ${timeout_sec}s)..."
        if timeout ${timeout_sec} docker pull "$fallback" 2>/dev/null; then
            docker tag "$fallback" "$image"
            log_ok "已通过备用仓库获取 $image"
            return 0
        fi
        log_warn "备用仓库拉取失败或超时: $fallback"
    done

    log_error "所有源拉取失败: $image"
    return 1
}

# ===================== Step 2: 加载镜像 =====================
load_images() {
    cd "$SCRIPT_DIR"
    if [ "$SKIP_LOAD" = true ]; then
        step "2" "跳过镜像加载（--skip-load）"
        return 0
    fi

    step "2" "加载业务镜像并拉取中间件"

    local middleware_images=(
        "mysql:8.0"
        "redis:7-alpine"
        "rabbitmq:3.12-management-alpine"
        "docker.elastic.co/elasticsearch/elasticsearch:8.11.0"
        "minio/minio:latest"
        "nacos/nacos-server:v2.3.2"
        "nginx:1.27-alpine"
    )

    log_info "从官方仓库拉取中间件镜像（7个）..."
    for img in "${middleware_images[@]}"; do
        if docker image inspect "$img" &>/dev/null; then
            log_ok "  已存在: $img"
        else
            log_info "  拉取: $img"
            if smart_pull "$img"; then
                log_ok "  拉取成功"
            else
                log_warn "  拉取失败: $img（将在启动时重试）"
            fi
        fi
    done

    # 业务镜像处理
    local part_files=($(ls -1 xss-images-*.tar.*.part* 2>/dev/null | sort))
    local part_count=${#part_files[@]}
    local tar_file=""

    if [ "$part_count" -eq 0 ]; then
        if ls xss-images-*.tar &> /dev/null; then
            tar_file=$(ls -1 xss-images-*.tar | head -1)
            log_info "找到完整业务镜像文件: $tar_file"
        elif ls xss-images-*.tar.gz &> /dev/null; then
            tar_file=$(ls -1 xss-images-*.tar.gz | head -1)
            log_info "找到完整业务镜像文件（压缩）: $tar_file"
        else
            log_warn "未找到业务镜像文件，业务镜像将通过构建或远程仓库获取"
            return 0
        fi
    else
        log_info "找到 $part_count 个业务镜像分片文件"
        local first_part="${part_files[0]}"
        tar_file="${first_part%.part*}"
        if [ -f "$tar_file" ]; then
            log_info "业务镜像文件已存在: $tar_file，跳过合并"
        else
            log_info "正在合并分片到: $tar_file ..."
            cat "${part_files[@]}" > "$tar_file"
            log_ok "合并完成: $(du -h "$tar_file" | cut -f1)"
        fi
    fi

    if [ -n "$tar_file" ] && [ -f "$tar_file" ]; then
        log_info "正在加载业务 Docker 镜像..."
        local load_start=$(date +%s)
        if [[ "$tar_file" == *.tar.gz ]]; then
            gzip -dc "$tar_file" | docker load
        else
            docker load -i "$tar_file"
        fi
        local load_end=$(date +%s)
        log_ok "业务镜像加载完成，耗时 $((load_end - load_start)) 秒"

        log_info "已加载的业务镜像:"
        docker images --format "{{.Repository}}:{{.Tag}} {{.Size}}" | grep "^xss/" | sort | while read line; do echo "       $line"; done

        # 清理合并后的 tar 文件
        if [ -f "$tar_file" ]; then
            log_info "清理合并后的镜像文件以释放磁盘..."
            rm -f "$tar_file"
            log_ok "已删除 $tar_file"
        fi

        # 清理分片文件
        if [ "$part_count" -gt 0 ]; then
            log_info "清理业务镜像分片文件..."
            rm -f xss-images-*.tar.*.part*
            log_ok "分片文件已删除"
        fi
    fi
}

# ===================== 启动前校验中间件镜像 =====================
verify_middleware_images() {
    local required_images=(
        "mysql:8.0"
        "redis:7-alpine"
        "rabbitmq:3.12-management-alpine"
        "docker.elastic.co/elasticsearch/elasticsearch:8.11.0"
        "minio/minio:latest"
        "nacos/nacos-server:v2.3.2"
        "nginx:1.27-alpine"
    )
    local missing=()
    for img in "${required_images[@]}"; do
        if ! docker image inspect "$img" &>/dev/null; then
            missing+=("$img")
        fi
    done

    if [ ${#missing[@]} -gt 0 ]; then
        log_error "以下中间件镜像缺失，无法继续: ${missing[*]}"
        log_error "请检查网络或手动导入镜像后重试。"
        exit 1
    fi
    log_ok "所有中间件镜像已就绪"
}

# ===================== Step 3-7: 启动各个服务层 =====================
start_infra() {
    cd "$SCRIPT_DIR"
    step "3" "启动中间件层（infra）"
    verify_middleware_images

    $COMPOSE_CMD --profile infra down --remove-orphans 2>/dev/null || true
    log_info "启动服务: mysql, redis, rabbitmq, elasticsearch, minio, nacos"
    $COMPOSE_CMD --profile infra up -d

    log_info "等待中间件健康检查通过（最长约 3 分钟）..."
    local infra_services=("xss-mysql" "xss-redis" "xss-rabbitmq" "xss-elasticsearch" "xss-minio" "xss-nacos")
    local all_healthy=true
    local max_wait=180

    for svc in "${infra_services[@]}"; do
        log_info "等待 $svc ..."
        local waited=0
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

init_config() {
    cd "$SCRIPT_DIR"
    step "4" "初始化数据库与配置"
    local mysql_pwd="${MYSQL_ROOT_PASSWORD:-root}"
    if [ -f .env ]; then
        mysql_pwd=$(grep '^MYSQL_ROOT_PASSWORD=' .env | sed 's/^MYSQL_ROOT_PASSWORD=//' || echo "root")
    fi

    log_info "等待 MySQL 稳定运行..."
    local waited=0
    while [ "$waited" -lt 60 ]; do
        if docker exec -e MYSQL_PWD="$mysql_pwd" xss-mysql mysql -uroot -e "SELECT 1" &>/dev/null; then
            break
        fi
        sleep 5
        waited=$((waited + 5))
    done

    local expected_dbs=("auth_db" "dict_db" "property_db" "image_db" "analytics_db" "message_db" "favorite_db" "review_db" "booking_db")
    local db_count=0
    for db in "${expected_dbs[@]}"; do
        if docker exec -e MYSQL_PWD="$mysql_pwd" xss-mysql mysql -uroot -e "USE $db;" 2>/dev/null; then
            db_count=$((db_count + 1))
        fi
    done
    log_info "已初始化数据库: $db_count / ${#expected_dbs[@]}"

    if [ "$db_count" -lt "${#expected_dbs[@]}" ]; then
        log_warn "数据库未完全初始化，执行手动初始化..."
        if [ -f "./init-db.sh" ]; then
            chmod +x ./init-db.sh
            bash ./init-db.sh
        else
            log_error "init-db.sh 不存在，无法初始化数据库"
            exit 1
        fi
    else
        log_ok "数据库已完全初始化"
    fi

    if [ -d "./nacos-config" ]; then
        log_info "Nacos 配置目录已就绪"
    fi
    log_ok "配置初始化完成"
}

start_core() {
    cd "$SCRIPT_DIR"
    step "5" "启动核心业务服务（core）"
    $COMPOSE_CMD --profile infra --profile core down --remove-orphans 2>/dev/null || true
    log_info "启动服务: gateway, auth, property, dict"
    $COMPOSE_CMD --profile infra --profile core up -d

    log_info "等待核心服务就绪（最长约 2 分钟）..."
    local core_services=("xss-gateway" "xss-auth" "xss-property" "xss-dict")
    local max_wait=120
    for svc in "${core_services[@]}"; do
        log_info "等待 $svc ..."
        local waited=0
        while [ "$waited" -lt "$max_wait" ]; do
            if docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null | grep -q "healthy"; then
                log_ok "  $svc is healthy"
                break
            fi
            if docker inspect --format='{{.State.Running}}' "$svc" 2>/dev/null | grep -q "true"; then
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

start_business() {
    cd "$SCRIPT_DIR"
    step "6" "启动其他业务服务（business）"
    $COMPOSE_CMD --profile infra --profile core --profile business down --remove-orphans 2>/dev/null || true
    log_info "启动服务: image, search, analytics, message, favorite, review, booking"
    $COMPOSE_CMD --profile infra --profile core --profile business up -d

    log_info "等待业务服务启动中（约 1-2 分钟）..."
    sleep 30
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

start_nginx() {
    cd "$SCRIPT_DIR"
    step "7" "启动 Nginx 反向代理"
    $COMPOSE_CMD --profile infra --profile core --profile business --profile nginx down --remove-orphans 2>/dev/null || true
    $COMPOSE_CMD --profile infra --profile core --profile business --profile nginx up -d

    local waited=0
    while [ "$waited" -lt 30 ]; do
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

# ===================== Step 8: 部署结果汇总 =====================
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

# ===================== 重启模式 =====================
restart_all() {
    step "重启" "重启所有服务"
    check_docker
    $COMPOSE_CMD --profile infra --profile core --profile business --profile nginx down --remove-orphans 2>/dev/null || true
    sleep 5
    start_infra
    init_config
    start_core
    start_business
    start_nginx
    report_status
}

# ===================== 主流程 =====================
main() {
    echo ""
    echo "╔══════════════════════════════════════════════════╗"
    echo "║      XSS 微服务集群 - 一键部署脚本 (增强版)     ║"
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
    network_diag
    check_docker
    check_port_conflicts
    load_images
    start_infra
    init_config
    start_core
    start_business
    start_nginx
    report_status
}

main "$@"