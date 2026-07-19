#!/bin/bash
# =====================================================
# XSS 微服务集群 - 一键部署脚本（离线环境 / Ubuntu）
# 串行部署：中间件 → DB初始化 → Nacos → 业务服务 → Nginx
# =====================================================
# 使用方式：
#   1. 将 deploy/ 目录和镜像包传输到服务器
#   2. 修改 .env 文件中的密码配置
#   3. chmod +x deploy-all.sh && ./deploy-all.sh
# =====================================================

DEPLOY_DIR=$(cd "$(dirname "$0")" && pwd)
export COMPOSE_PROJECT_NAME="xss"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[1;30m'
NC='\033[0m'

# =====================================================
# 函数定义
# =====================================================

wait_healthy() {
    local service_name=$1
    local check_command=$2
    local max_retries=${3:-30}
    local interval=${4:-5}

    echo -e "${YELLOW}等待 $service_name 就绪...${NC}"

    for ((i=1; i<=max_retries; i++)); do
        if ${DOCKER_SUDO:-} docker exec "$service_name" sh -c "$check_command" 2>/dev/null; then
            echo -e "${GREEN}✅ $service_name 就绪${NC}"
            return 0
        fi
        echo -e "${GRAY}  等待中 ($i/$max_retries)...${NC}"
        sleep $interval
    done

    echo -e "${RED}❌ $service_name 超时未就绪${NC}"
    return 1
}

check_port_available() {
    local port=$1
    local name=$2
    if ss -tlnp 2>/dev/null | grep -q ":$port "; then
        echo -e "${RED}❌ 端口 $port ($name) 已被占用${NC}"
        return 1
    fi
    return 0
}

print_header() {
    echo ""
    echo -e "${CYAN}------------------------------------------------------"
    echo -e "  $1"
    echo -e "------------------------------------------------------${NC}"
}

load_env() {
    if [ -f "$DEPLOY_DIR/.env" ]; then
        MYSQL_PWD_CHECK=$(grep '^MYSQL_ROOT_PASSWORD=' "$DEPLOY_DIR/.env" | sed 's/^MYSQL_ROOT_PASSWORD=//')
        REDIS_PWD_CHECK=$(grep '^REDIS_PASSWORD=' "$DEPLOY_DIR/.env" | sed 's/^REDIS_PASSWORD=//')
    fi
    MYSQL_PWD_CHECK=${MYSQL_PWD_CHECK:-root}
    REDIS_PWD_CHECK=${REDIS_PWD_CHECK:-redisroot}
    export MYSQL_PWD_CHECK REDIS_PWD_CHECK
}

# =====================================================
# 开始部署
# =====================================================

echo -e "${CYAN}=========================================="
echo -e "  XSS 微服务集群一键部署"
echo -e "==========================================${NC}"
echo -e "部署目录: ${DEPLOY_DIR}"
echo ""

# ---------------------------------------------------
# 步骤 1: 前置检查
# ---------------------------------------------------
print_header "步骤 1/8：前置检查"

# 检查 Docker
if ! command -v docker &>/dev/null; then
    echo -e "${RED}❌ 未找到 Docker，请先安装 Docker${NC}"
    echo -e "${YELLOW}  安装方法: https://docs.docker.com/engine/install/ubuntu/${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Docker 已安装${NC}"

# 检查 Docker 权限
if ! docker info &>/dev/null; then
    echo -e "${YELLOW}⚠️  当前用户无 Docker 权限，尝试使用 sudo...${NC}"
    if ! sudo docker info &>/dev/null; then
        echo -e "${RED}❌ Docker 权限不足，请将用户加入 docker 组：${NC}"
        echo -e "  sudo usermod -aG docker \$USER"
        echo -e "  newgrp docker"
        exit 1
    fi
    DOCKER_SUDO="sudo"
    export DOCKER_SUDO
    echo -e "${YELLOW}  将使用 sudo 执行 Docker 命令${NC}"
else
    DOCKER_SUDO=""
    export DOCKER_SUDO
fi
echo -e "${GREEN}✅ Docker 权限正常${NC}"

# 检查 .env 文件
if [ ! -f "$DEPLOY_DIR/.env" ]; then
    echo -e "${YELLOW}⚠️  .env 文件不存在，复制默认配置${NC}"
    cp "$DEPLOY_DIR/.env.example" "$DEPLOY_DIR/.env"
    echo -e "${YELLOW}  请在部署完成后修改 $DEPLOY_DIR/.env 中的密码${NC}"
fi

# 检查镜像包
img_file=$(ls -1 "$DEPLOY_DIR"/xss-images-*.tar.gz 2>/dev/null | head -1)
if [ -z "$img_file" ]; then
    echo -e "${RED}❌ 未找到镜像包 (xss-images-*.tar.gz)${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 找到镜像包: $(basename "$img_file")${NC}"

# 检查端口占用
echo ""
echo -e "${CYAN}检查端口占用...${NC}"
port_errors=0
check_port_available 80 "Nginx" || port_errors=$((port_errors + 1))
check_port_available 3306 "MySQL" || port_errors=$((port_errors + 1))
check_port_available 6379 "Redis" || port_errors=$((port_errors + 1))
check_port_available 5672 "RabbitMQ" || port_errors=$((port_errors + 1))
check_port_available 8848 "Nacos" || port_errors=$((port_errors + 1))
check_port_available 9000 "MinIO" || port_errors=$((port_errors + 1))

if [ $port_errors -gt 0 ]; then
    echo -e "${RED}有 $port_errors 个端口被占用，请先释放端口后再部署${NC}"
    echo -e "${YELLOW}  查看占用: ss -tlnp | grep 端口号${NC}"
    echo -e "${YELLOW}  停止系统服务: sudo systemctl stop mysql redis-server ...${NC}"
    echo -e "${YELLOW}  或修改 .env 中的端口配置${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 所有端口可用${NC}"

load_env

# ---------------------------------------------------
# 步骤 2: 加载镜像（支持分片文件自动合并）
# ---------------------------------------------------
print_header "步骤 2/8：加载 Docker 镜像"

# 查找镜像包（优先查找分片文件，其次查找完整 tar.gz）
img_file=$(ls -1 "$DEPLOY_DIR"/xss-images-*.tar.gz 2>/dev/null | head -1)
part_files=$(ls -1 "$DEPLOY_DIR"/xss-images-*.part* 2>/dev/null | sort)

if [ -n "$part_files" ]; then
    echo -e "${YELLOW}找到分片文件，开始合并...${NC}"
    first_part=$(echo "$part_files" | head -1)
    img_file="${first_part%.*}.tar.gz"
    
    cat $part_files > "$img_file"
    echo -e "${GREEN}✅ 分片合并完成: $img_file${NC}"
elif [ -z "$img_file" ]; then
    echo -e "${RED}❌ 未找到镜像包 (xss-images-*.tar.gz 或 xss-images-*.part*)${NC}"
    exit 1
fi

echo -e "正在加载镜像: $(basename "$img_file")"
$DOCKER_SUDO docker load -i "$img_file"
echo -e "${GREEN}✅ 镜像加载完成${NC}"

# ---------------------------------------------------
# 步骤 3: 启动 MySQL（先单独启动，用于数据初始化）
# ---------------------------------------------------
print_header "步骤 3/8：启动 MySQL"
cd "$DEPLOY_DIR" && $DOCKER_SUDO docker compose up -d mysql
wait_healthy "xss-mysql" "mysqladmin ping -h localhost -p$MYSQL_PWD_CHECK" 60 5 || exit 1

# ---------------------------------------------------
# 步骤 4: 导入数据库（导入期间临时调高 MySQL 内存）
# ---------------------------------------------------
print_header "步骤 4/8：初始化数据库"

echo -e "${YELLOW}临时调整 MySQL 配置以加速大 SQL 导入...${NC}"
$DOCKER_SUDO docker exec -e MYSQL_PWD="$MYSQL_PWD_CHECK" xss-mysql mysql -uroot -e "
SET GLOBAL innodb_buffer_pool_size = 536870912;
SET GLOBAL max_connections = 500;
SET GLOBAL innodb_flush_log_at_trx_commit = 0;
SET GLOBAL sync_binlog = 0;
" 2>/dev/null
echo -e "${GREEN}✅ MySQL 临时配置已调整${NC}"

cd "$DEPLOY_DIR" && bash init-db.sh
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ 数据库初始化失败${NC}"
    exit 1
fi

echo -e "${YELLOW}恢复 MySQL 常规配置...${NC}"
$DOCKER_SUDO docker exec -e MYSQL_PWD="$MYSQL_PWD_CHECK" xss-mysql mysql -uroot -e "
SET GLOBAL innodb_buffer_pool_size = 268435456;
SET GLOBAL max_connections = 200;
SET GLOBAL innodb_flush_log_at_trx_commit = 1;
SET GLOBAL sync_binlog = 1;
" 2>/dev/null
echo -e "${GREEN}✅ MySQL 配置已恢复${NC}"

# ---------------------------------------------------
# 步骤 5: 启动其他中间件（Redis / RabbitMQ / ES / MinIO / Nacos）
# ---------------------------------------------------
print_header "步骤 5/8：启动其他中间件"
echo -e "启动 Redis、RabbitMQ、Elasticsearch、MinIO、Nacos..."
cd "$DEPLOY_DIR" && $DOCKER_SUDO docker compose up -d redis rabbitmq elasticsearch minio nacos

wait_healthy "xss-redis" "redis-cli -a $REDIS_PWD_CHECK ping" || exit 1
wait_healthy "xss-rabbitmq" "rabbitmq-diagnostics check_running" || exit 1
wait_healthy "xss-elasticsearch" "curl -f http://localhost:9200/_cluster/health || exit 1" || exit 1
wait_healthy "xss-minio" "curl -f http://localhost:9000/minio/health/live" || exit 1
wait_healthy "xss-nacos" "curl -f http://localhost:8848/nacos/v1/console/health/readiness || exit 1" || exit 1

# ---------------------------------------------------
# 步骤 6: 初始化 Nacos 配置
# ---------------------------------------------------
print_header "步骤 6/8：初始化 Nacos 配置"
cd "$DEPLOY_DIR" && bash init-nacos-config.sh

# ---------------------------------------------------
# 步骤 7: 启动业务服务
# ---------------------------------------------------
print_header "步骤 7/8：启动业务服务"
echo -e "启动 11 个业务微服务..."
cd "$DEPLOY_DIR" && $DOCKER_SUDO docker compose up -d
echo -e "${GREEN}✅ 业务服务启动指令已发出${NC}"

# 等待 gateway 就绪（Nginx 依赖它）
echo ""
echo -e "${YELLOW}等待 Gateway 服务就绪（约 1-3 分钟）...${NC}"
wait_healthy "xss-gateway" "curl -f http://localhost:8080/actuator/health || exit 1" 60 5 || {
    echo -e "${YELLOW}⚠️  Gateway 启动较慢，继续等待 Nginx...${NC}"
}

# ---------------------------------------------------
# 步骤 8: 启动 Nginx 反向代理
# ---------------------------------------------------
print_header "步骤 8/8：启动 Nginx 反向代理"
cd "$DEPLOY_DIR" && $DOCKER_SUDO docker compose up -d nginx
sleep 3
if $DOCKER_SUDO docker exec xss-nginx curl -f http://localhost/nginx-health &>/dev/null; then
    echo -e "${GREEN}✅ Nginx 启动成功${NC}"
else
    echo -e "${YELLOW}⚠️  Nginx 健康检查未通过，可能 Gateway 尚未就绪，稍后会自动恢复${NC}"
fi

# ---------------------------------------------------
# 部署完成
# ---------------------------------------------------
echo ""
echo -e "${CYAN}=========================================="
echo -e "${GREEN}  部署完成！${NC}"
echo -e "${CYAN}==========================================${NC}"
echo ""
echo -e "${YELLOW}服务状态:${NC}"
cd "$DEPLOY_DIR" && $DOCKER_SUDO docker compose ps

echo ""
echo -e "${YELLOW}访问地址:${NC}"
echo -e "  API 入口:     ${NC}http://<服务器IP>"
echo -e "  Nacos 控制台: ${NC}http://<服务器IP>:8848/nacos  (nacos/nacos)"
echo -e "  MinIO 控制台: ${NC}http://<服务器IP>:9001       (minioadmin/minioadmin)"
echo -e "  RabbitMQ:     ${NC}http://<服务器IP>:15672      (guest/guest)"
echo ""
echo -e "${YELLOW}注意事项:${NC}"
echo -e "  1. 请修改 .env 中的默认密码"
echo -e "  2. 业务服务完全启动需要 3-5 分钟，可观察日志"
echo -e "  3. 查看日志: docker logs -f xss-gateway"
echo -e "  4. 停止服务: docker compose down"
echo -e "  5. 首次访问慢属正常，JVM 预热后会变快"
