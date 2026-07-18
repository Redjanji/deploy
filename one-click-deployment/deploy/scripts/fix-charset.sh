#!/bin/bash
# =====================================================
# MySQL 字符集修复脚本
# 解决数据库中文乱码问题
# =====================================================

set -uo pipefail

SCRIPT_PATH="${BASH_SOURCE[0]}"
while [ -L "$SCRIPT_PATH" ]; do
    SCRIPT_PATH=$(readlink -f "$SCRIPT_PATH")
done
DEPLOY_DIR=$(cd "$(dirname "$SCRIPT_PATH")/.." && pwd)

MYSQL_USER="root"
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
NC='\033[0m'

MYSQL_CMD="docker exec -e MYSQL_PWD=\"$MYSQL_PASSWORD\" xss-mysql mysql -u$MYSQL_USER --default-character-set=utf8mb4"

databases=(
    "auth_db"
    "dict_db"
    "property_db"
    "image_db"
    "analytics_db"
    "message_db"
    "favorite_db"
    "review_db"
    "booking_db"
)

echo -e "${CYAN}=========================================="
echo -e "  MySQL 字符集修复脚本"
echo -e "==========================================${NC}"

echo -e "${CYAN}1. 设置 MySQL 全局字符集...${NC}"
$MYSQL_CMD -e "
    SET GLOBAL character_set_server = 'utf8mb4';
    SET GLOBAL collation_server = 'utf8mb4_unicode_ci';
    SET GLOBAL character_set_client = 'utf8mb4';
    SET GLOBAL character_set_connection = 'utf8mb4';
    SET GLOBAL character_set_results = 'utf8mb4';
" 2>/dev/null
echo -e "${GREEN}   ✅ 全局字符集已设置${NC}"

echo -e "${CYAN}2. 修改数据库字符集...${NC}"
for db in "${databases[@]}"; do
    echo -e "${GRAY}   处理: $db${NC}"
    $MYSQL_CMD -e "ALTER DATABASE \`$db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null
    $MYSQL_CMD -D "$db" -e "
        SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES 
        WHERE TABLE_SCHEMA = '$db' AND TABLE_TYPE = 'BASE TABLE';
    " 2>/dev/null | tail -n +2 | while read -r table; do
        [ -z "$table" ] && continue
        $MYSQL_CMD -D "$db" -e "ALTER TABLE \`$table\` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null
        $MYSQL_CMD -D "$db" -e "ALTER TABLE \`$table\` DEFAULT CHARACTER SET utf8mb4;" 2>/dev/null
    done
    echo -e "${GREEN}   ✅ $db 修复完成${NC}"
done

echo -e "${CYAN}3. 验证字符集设置...${NC}"
$MYSQL_CMD -e "
    SELECT 
        @@character_set_server AS server_charset,
        @@collation_server AS server_collation,
        @@character_set_client AS client_charset,
        @@character_set_connection AS connection_charset,
        @@character_set_results AS results_charset;
" 2>/dev/null | sed 's/\t/    /g'

echo -e "${CYAN}4. 验证数据库字符集...${NC}"
$MYSQL_CMD -e "
    SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME 
    FROM INFORMATION_SCHEMA.SCHEMATA 
    WHERE SCHEMA_NAME IN ('auth_db','dict_db','property_db','favorite_db','review_db','booking_db');
" 2>/dev/null | sed 's/\t/    /g'

echo -e "${GREEN}=========================================="
echo -e "  字符集修复完成！"
echo -e "==========================================${NC}"
echo -e "${YELLOW}注意：已修复的数据需要重新插入才能生效${NC}"