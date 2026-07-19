#!/bin/bash
# =====================================================
# Nacos 配置批量导入脚本（Ubuntu）
# =====================================================
# 功能：通过 Nacos Open API 批量创建各服务的配置文件
# 使用方式：
#   1. 确保 Nacos 已启动
#   2. 修改以下 NACOS_ADDR 和认证信息（如需）
#   3. chmod +x init-nacos-config.sh && ./init-nacos-config.sh
# =====================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[1;30m'
NC='\033[0m'

NACOS_ADDR="127.0.0.1:8848"
NACOS_USER="nacos"
NACOS_PASS="nacos"
GROUP="DEFAULT_GROUP"

access_token=""

auth_body="username=$NACOS_USER&password=$NACOS_PASS"
auth_resp=$(curl -s -X POST "http://$NACOS_ADDR/nacos/v1/auth/login" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "$auth_body")

if echo "$auth_resp" | grep -q "accessToken"; then
    access_token=$(echo "$auth_resp" | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')
    echo -e "${GREEN}✅ Nacos 登录成功${NC}"
else
    echo -e "${YELLOW}⚠️  Nacos 登录失败（可能未启用认证），继续尝试...${NC}"
    access_token=""
fi

gateway_service_yaml=$(cat << 'EOF'
spring:
  cloud:
    sentinel:
      transport:
        dashboard: ${SENTINEL_DASHBOARD:127.0.0.1:8718}
EOF
)

auth_service_yaml=$(cat << 'EOF'
jwt:
  secret-base64: ${JWT_SECRET:eW91ci0yNTYtYml0LXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLWRldi1vbmx5}
  expiration: 7200000
EOF
)

dict_service_yaml=$(cat << 'EOF'
# 字典服务配置（使用本地配置即可）
EOF
)

image_service_yaml=$(cat << 'EOF'
minio:
  endpoint: ${MINIO_ENDPOINT:http://minio:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: images
EOF
)

property_service_yaml=$(cat << 'EOF'
property:
  max-images: 20
  geohash-precision: 12
  search-precision: 5
  default-radius-km: 5.0
EOF
)

search_service_yaml=$(cat << 'EOF'
elasticsearch:
  index-name: properties
EOF
)

analytics_service_yaml=$(cat << 'EOF'
analytics:
  flush:
    cron: "0 */5 * * * ?"
EOF
)

message_service_yaml=$(cat << 'EOF'
message:
  max-retry: 3
  retry-interval-ms: 5000
EOF
)

favorite_service_yaml=$(cat << 'EOF'
# 收藏服务配置（使用本地配置即可）
EOF
)

review_service_yaml=$(cat << 'EOF'
audit:
  machine:
    enabled: true
    provider: mock
EOF
)

booking_service_yaml=$(cat << 'EOF'
server:
  port: 8092
  address: 0.0.0.0

spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:mysql}:${MYSQL_PORT:3306}/booking_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4&allowPublicKeyRetrieval=true
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
  rabbitmq:
    host: ${RABBITMQ_HOST:rabbitmq}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:guest}
    password: ${RABBITMQ_PASS:guest}

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto

message:
  send:
    exchange: message.send.exchange
    routing-key: message.send
  notify:
    admin-email: ${MESSAGE_NOTIFY_ADMIN_EMAIL:redjanji@163.com}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
EOF
)

declare -A configs=(
    ["gateway-service.yaml"]="$gateway_service_yaml"
    ["auth-service.yaml"]="$auth_service_yaml"
    ["dict-service.yaml"]="$dict_service_yaml"
    ["image-service.yaml"]="$image_service_yaml"
    ["property-service.yaml"]="$property_service_yaml"
    ["search-service.yaml"]="$search_service_yaml"
    ["analytics-service.yaml"]="$analytics_service_yaml"
    ["message-service.yaml"]="$message_service_yaml"
    ["favorite-service.yaml"]="$favorite_service_yaml"
    ["review-service.yaml"]="$review_service_yaml"
    ["booking-service.yaml"]="$booking_service_yaml"
)

echo -e "\n${CYAN}开始导入 Nacos 配置...${NC}"

for dataId in "${!configs[@]}"; do
    content="${configs[$dataId]}"
    encoded_content=$(echo "$content" | python3 -c "import sys, urllib.parse; print(urllib.parse.quote(sys.stdin.read()))")
    
    url="http://$NACOS_ADDR/nacos/v1/cs/configs"
    if [ -n "$access_token" ]; then
        url="$url?accessToken=$access_token"
    fi
    
    body="dataId=$dataId&group=$GROUP&type=yaml&content=$encoded_content"
    
    resp=$(curl -s -X POST "$url" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "$body")
    
    if [ "$resp" = "true" ]; then
        echo -e "${GREEN}✅ 创建配置: $dataId${NC}"
    else
        echo -e "${YELLOW}⚠️  配置已存在或创建失败: $dataId (响应: $resp)${NC}"
    fi
done

echo -e "\n${CYAN}Nacos 配置导入完成！${NC}"
echo -e "${GRAY}请登录 Nacos 控制台 http://$NACOS_ADDR/nacos 查看配置列表${NC}"