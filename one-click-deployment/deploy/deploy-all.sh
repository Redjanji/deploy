#!/bin/bash
# =====================================================
# XSS 微服务集群 - 一键部署脚本（离线环境 / Ubuntu）
# 串行部署：清理旧环境 → 加载镜像 → 中间件 → DB初始化 → Nacos → 业务服务 → Nginx
# =====================================================
# 使用方式：
#   1. 将 deploy/ 目录和镜像包传输到服务器
#   2. 修改 .env 文件中的密码配置
#   3. chmod +x deploy-all.sh && ./deploy-all.sh
#
# 重新部署（更新服务）：
#   直接再次运行 ./deploy-all.sh
#   脚本会自动清除旧容器和数据卷，保留中间件镜像，重新加载业务镜像
# =====================================================

DEPLOY_DIR=$(cd "$(dirname "$0")" && pwd)
export COMPOSE_PROJECT_NAME="xss"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[1;30m'
NC='\033[0m'

# 中间件镜像列表（清理时保留，不删除）
MIDDLEWARE_IMAGES=(
    "mysql:8.0"
    "redis:7-alpine"
    "rabbitmq:3.12-management-alpine"
    "docker.elastic.co/elasticsearch/elasticsearch:8.11.0"
    "minio/minio:latest"
    "nacos/nacos-server:v2.3.2"
    "nginx:1.27-alpine"
)

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

# 清除旧环境：停止容器 → 删除容器 → 删除数据卷 → 删除业务镜像（保留中间件镜像）
cleanup_old_env() {
    print_header "步骤 1：清除旧环境"

    # 检查是否有旧容器在运行
    local old_containers=$($DOCKER_SUDO docker ps -a --filter "name=xss-" --format "{{.Names}}" 2>/dev/null)
    if [ -z "$old_containers" ]; then
        echo -e "${GREEN}✅ 未发现旧环境，跳过清理${NC}"
        return 0
    fi

    echo -e "${YELLOW}发现旧环境，开始清理...${NC}"
    echo ""

    # 1. 停止并删除所有 xss- 容器
    echo -e "${CYAN}停止并删除旧容器...${NC}"
    cd "$DEPLOY_DIR" && $DOCKER_SUDO docker compose down --remove-orphans 2>/dev/null
    # 兜底：手动删除残留容器
    for c in $old_containers; do
        $DOCKER_SUDO docker rm -f "$c" 2>/dev/null
    done
    echo -e "${GREEN}✅ 旧容器已清除${NC}"

    # 2. 删除数据卷（数据库数据、Redis 数据等全部清除）
    echo ""
    echo -e "${CYAN}删除旧数据卷...${NC}"
    local volumes="mysql-data redis-data rabbitmq-data es-data minio-data nacos-data nginx-log"
    for vol in $volumes; do
        local full_name="${COMPOSE_PROJECT_NAME}_${vol}"
        if $DOCKER_SUDO docker volume inspect "$full_name" &>/dev/null; then
            $DOCKER_SUDO docker volume rm "$full_name" 2>/dev/null && \
                echo -e "  ${GREEN}已删除: $full_name${NC}" || \
                echo -e "  ${YELLOW}跳过: $full_name (可能正在使用)${NC}"
        fi
    done
    echo -e "${GREEN}✅ 数据卷已清除${NC}"

    # 3. 删除业务镜像（xss/*），保留中间件镜像
    echo ""
    echo -e "${CYAN}删除旧业务镜像（保留中间件镜像）...${NC}"
    local business_images=$($DOCKER_SUDO docker images --filter "reference=xss/*" --format "{{.Repository}}:{{.Tag}}" 2>/dev/null)
    if [ -n "$business_images" ]; then
        for img in $business_images; do
            $DOCKER_SUDO docker rmi -f "$img" 2>/dev/null && \
                echo -e "  ${GREEN}已删除: $img${NC}" || \
                echo -e "  ${YELLOW}跳过: $img${NC}"
        done
    else
        echo -e "  ${GRAY}无业务镜像需要删除${NC}"
    fi
    echo -e "${GREEN}✅ 业务镜像已清除${NC}"

    # 4. 显示保留的中间件镜像
    echo ""
    echo -e "${CYAN}保留的中间件镜像:${NC}"
    for img in "${MIDDLEWARE_IMAGES[@]}"; do
        if $DOCKER_SUDO docker image inspect "$img" &>/dev/null; then
            echo -e "  ${GREEN}✅ $img${NC}"
        else
            echo -e "  ${GRAY}（未安装）$img${NC}"
        fi
    done

    # 5. 清理无用镜像层
    echo ""
    echo -e "${CYAN}清理悬空镜像层...${NC}"
    $DOCKER_SUDO docker image prune -f 2>/dev/null
    echo -e "${GREEN}✅ 清理完成${NC}"
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
# 步骤 1: 前置检查 + 清除旧环境
# ---------------------------------------------------
print_header "步骤 1/9：前置检查"

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
part_files=$(ls -1 "$DEPLOY_DIR"/xss-images-*.part* 2>/dev/null | sort)
if [ -z "$img_file" ] && [ -z "$part_files" ]; then
    echo -e "${RED}❌ 未找到镜像包 (xss-images-*.tar.gz 或 xss-images-*.part*)${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 找到镜像包${NC}"

load_env

# 清除旧环境
cleanup_old_env

# ---------------------------------------------------
# 步骤 2: 加载镜像（支持分片文件自动合并）
# ---------------------------------------------------
print_header "步骤 2/9：加载 Docker 镜像"

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
print_header "步骤 3/9：启动 MySQL"
cd "$DEPLOY_DIR" && $DOCKER_SUDO docker compose up -d mysql
wait_healthy "xss-mysql" "mysqladmin ping -h localhost -p$MYSQL_PWD_CHECK" 60 5 || exit 1

# ---------------------------------------------------
# 步骤 4: 导入数据库（导入期间临时调高 MySQL 内存）
# ---------------------------------------------------
print_header "步骤 4/9：初始化数据库"

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
print_header "步骤 5/9：启动其他中间件"
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
print_header "步骤 6/9：初始化 Nacos 配置"
cd "$DEPLOY_DIR" && bash init-nacos-config.sh

# ---------------------------------------------------
# 步骤 7: 启动核心业务服务（gateway/auth/property/dict）
# ---------------------------------------------------
print_header "步骤 7/9：启动核心业务服务"
echo -e "启动 gateway、auth、property、dict..."
cd "$DEPLOY_DIR" && $DOCKER_SUDO docker compose up -d gateway-service auth-service property-service dict-service

# 等待 Gateway 就绪（启动约需 40 秒）
echo ""
echo -e "${YELLOW}等待 Gateway 服务就绪（约 40-60 秒）...${NC}"
wait_healthy "xss-gateway" "curl -f http://localhost:8080/actuator/health || exit 1" 30 5 || {
    echo -e "${YELLOW}⚠️  Gateway 启动较慢，继续后续步骤...${NC}"
}

# ---------------------------------------------------
# 步骤 8: 启动其他业务服务 + Nginx
# ---------------------------------------------------
print_header "步骤 8/9：启动其他业务服务 + Nginx"
echo -e "启动 search、analytics、message、favorite、review、booking、image..."
cd "$DEPLOY_DIR" && $DOCKER_SUDO docker compose up -d search-service analytics-service message-service favorite-service review-service booking-service image-service

echo -e "${YELLOW}等待 10 秒后启动 Nginx...${NC}"
sleep 10
cd "$DEPLOY_DIR" && $DOCKER_SUDO docker compose up -d nginx
sleep 3
if $DOCKER_SUDO docker exec xss-nginx curl -f http://localhost/nginx-health &>/dev/null; then
    echo -e "${GREEN}✅ Nginx 启动成功${NC}"
else
    echo -e "${YELLOW}⚠️  Nginx 健康检查未通过，可能 Gateway 尚未就绪，稍后会自动恢复${NC}"
    echo -e "${YELLOW}  可手动重试: docker compose up -d nginx${NC}"
fi

# ---------------------------------------------------
# 步骤 9: 部署完成
# ---------------------------------------------------
print_header "步骤 9/9：部署完成"
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
echo -e "  5. 重新部署: 直接再次运行 ./deploy-all.sh（自动清理旧环境）"
echo -e "  6. 首次访问慢属正常，JVM 预热后会变快"
