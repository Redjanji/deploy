#!/bin/bash
# =====================================================
# 数据库初始化脚本（适用于离线环境 / Ubuntu）
# MySQL 8.0 兼容版
# =====================================================
# 使用方式：
#   1. 确保 MySQL 容器已启动且健康
#   2. chmod +x init-db.sh && ./init-db.sh
# =====================================================

set -uo pipefail

DEPLOY_DIR=$(cd "$(dirname "$0")" && pwd)
SQL_DIR="$DEPLOY_DIR/sql"
MYSQL_USER="root"

# 从 .env 读取 MySQL 密码
if [ -f "$DEPLOY_DIR/.env" ]; then
    MYSQL_PASSWORD=$(grep '^MYSQL_ROOT_PASSWORD=' "$DEPLOY_DIR/.env" | sed 's/^MYSQL_ROOT_PASSWORD=//')
fi
if [ -z "$MYSQL_PASSWORD" ]; then
    MYSQL_PASSWORD="root"
fi

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[1;30m'
NC='\033[0m'

# =====================================================
# 等待 MySQL 完全就绪（稳定运行 10 秒以上）
# =====================================================
wait_mysql_ready() {
    echo -e "${CYAN}等待 MySQL 完全就绪...${NC}"

    local max_wait=120
    local waited=0
    local stable_count=0

    while [ "$waited" -lt "$max_wait" ]; do
        # 检查容器是否在运行（非 restarting）
        local state=$(docker inspect --format='{{.State.Status}}' xss-mysql 2>/dev/null || echo "not_found")
        if [ "$state" != "running" ]; then
            echo -e "${YELLOW}  MySQL 容器状态: $state，等待 5 秒...${NC}"
            sleep 5
            waited=$((waited + 5))
            stable_count=0
            continue
        fi

        # 尝试执行简单查询
        if docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" xss-mysql mysql -u"$MYSQL_USER" -e "SELECT 1" &>/dev/null; then
            stable_count=$((stable_count + 1))
            if [ "$stable_count" -ge 2 ]; then
                echo -e "${GREEN}✅ MySQL 已就绪且稳定${NC}"
                return 0
            fi
            echo -e "${GRAY}  MySQL 可连接，确认稳定性 ($stable_count/2)...${NC}"
        else
            stable_count=0
            echo -e "${YELLOW}  MySQL 尚未接受连接，等待 5 秒...${NC}"
        fi
        sleep 5
        waited=$((waited + 5))
    done

    echo -e "${RED}❌ MySQL 在 ${max_wait} 秒内未就绪${NC}"
    echo -e "${YELLOW}  查看日志: docker logs xss-mysql --tail 50${NC}"
    return 1
}

# =====================================================
# 执行单个 SQL 文件（带重试）
# =====================================================
exec_sql_with_retry() {
    local script_path="$1"
    local script_name="$2"
    local max_retries=3
    local attempt=1

    while [ "$attempt" -le "$max_retries" ]; do
        # 检查 MySQL 容器是否还在运行
        local state=$(docker inspect --format='{{.State.Status}}' xss-mysql 2>/dev/null || echo "not_found")
        if [ "$state" != "running" ]; then
            echo -e "${YELLOW}  MySQL 容器状态异常 ($state)，等待恢复...${NC}"
            wait_mysql_ready || return 1
        fi

        # 复制 SQL 文件到容器
        local tmp_file="/tmp/$(basename "$script_path")"
        docker cp "$script_path" xss-mysql:"$tmp_file" 2>/dev/null

        if [ $? -ne 0 ]; then
            echo -e "${RED}  复制文件到容器失败（尝试 $attempt/$max_retries）${NC}"
            attempt=$((attempt + 1))
            sleep 3
            continue
        fi

        # 执行 SQL
        docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" xss-mysql sh -c "mysql -u $MYSQL_USER < $tmp_file" 2>&1
        local ret=$?
        docker exec xss-mysql rm -f "$tmp_file" 2>/dev/null

        if [ $ret -eq 0 ]; then
            return 0
        fi

        echo -e "${YELLOW}  执行失败，退出码 $ret（尝试 $attempt/$max_retries）${NC}"
        if [ "$attempt" -lt "$max_retries" ]; then
            echo -e "${GRAY}  等待 MySQL 恢复后重试...${NC}"
            sleep 5
            wait_mysql_ready || true
        fi
        attempt=$((attempt + 1))
    done

    return 1
}

scripts=(
    "01-create-databases.sql"
    "02-auth_db.sql"
    "02-dict_db.sql"
    "03-property_db.sql"
    "04-image_db.sql"
    "05-analytics_db.sql"
    "06-message_db.sql"
    "07-message_templates.sql"
    "08-favorite_db.sql"
    "09-review_db.sql"
    "10-booking_db.sql"
    "11-booking_templates.sql"
)

echo -e "${CYAN}=========================================="
echo -e "  XSS 数据库初始化"
echo -e "==========================================${NC}"
echo -e "SQL目录: $SQL_DIR"
echo -e "MySQL:   $MYSQL_USER@xss-mysql:3306"
echo -e "脚本数:  ${#scripts[@]}"
echo ""

if [ ! -d "$SQL_DIR" ]; then
    echo -e "${RED}SQL目录不存在: $SQL_DIR${NC}"
    exit 1
fi

# 等待 MySQL 就绪
if ! wait_mysql_ready; then
    exit 1
fi

success_count=0
fail_count=0
failed=()

total=${#scripts[@]}
for ((i=0; i<total; i++)); do
    script="${scripts[$i]}"
    script_path="$SQL_DIR/$script"
    current=$((i + 1))

    if [ ! -f "$script_path" ]; then
        echo -e "${RED}❌ 脚本不存在: $script${NC}"
        fail_count=$((fail_count + 1))
        failed+=("$script")
        continue
    fi

    echo -e "${CYAN}[$current/$total] 执行: $script${NC}"

    start_time=$(date +%s)

    if exec_sql_with_retry "$script_path" "$script"; then
        end_time=$(date +%s)
        duration=$((end_time - start_time))
        minutes=$((duration / 60))
        seconds=$((duration % 60))
        echo -e "${GREEN}✅ 执行成功 (用时 ${minutes}分${seconds}秒)${NC}"
        success_count=$((success_count + 1))
    else
        echo -e "${RED}❌ 执行失败（已重试 3 次）${NC}"
        fail_count=$((fail_count + 1))
        failed+=("$script")
    fi

    echo ""
done

echo -e "${CYAN}=========================================="
echo -e "  初始化完成"
echo -e "==========================================${NC}"
echo -e "${GREEN}成功: $success_count 个${NC}"
if [ $fail_count -gt 0 ]; then
    echo -e "${RED}失败: $fail_count 个${NC}"
else
    echo -e "${GRAY}失败: $fail_count 个${NC}"
fi

if [ $fail_count -gt 0 ]; then
    echo -e "${RED}失败脚本: ${failed[*]}${NC}"
    echo ""
    echo -e "${YELLOW}排查建议:${NC}"
    echo -e "  1. 确认 MySQL 容器状态: docker ps | grep mysql"
    echo -e "  2. 查看 MySQL 日志: docker logs xss-mysql --tail 50"
    echo -e "  3. 手动测试连接: docker exec -e MYSQL_PWD=$MYSQL_PASSWORD xss-mysql mysql -uroot -e 'SELECT 1'"
    echo -e "  4. 如需重新初始化: docker volume rm xss-mysql-data && 重新启动 MySQL"
    exit 1
fi

echo ""
echo -e "${YELLOW}下一步：执行 init-nacos-config.sh 导入 Nacos 配置${NC}"
