#!/bin/bash
# =====================================================
# XSS 微服务集群 - 服务器端集成测试脚本（Ubuntu）
# 通过 Nginx 入口验证所有 API 接口
# =====================================================
# 使用方式：
#   1. 部署完成后执行: chmod +x run-integration-test.sh && ./run-integration-test.sh
#   2. 或指定服务器地址: API_BASE_URL=http://192.168.1.100 ./run-integration-test.sh
# =====================================================

set -e

# 配置
API_BASE_URL="${API_BASE_URL:-http://localhost}"
APP_ID="my-backend-system"
APP_SECRET="XssBackend2026SecHmacKey8xYz"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[1;30m'
NC='\033[0m'

# 统计
pass_count=0
fail_count=0

# 测试结果数组
declare -a test_results

# =====================================================
# 工具函数
# =====================================================

print_header() {
    echo ""
    echo -e "${CYAN}------------------------------------------------------"
    echo -e "  $1"
    echo -e "------------------------------------------------------${NC}"
}

write_result() {
    local name="$1"
    local status="$2"
    local message="$3"
    
    if [ "$status" = "PASS" ]; then
        pass_count=$((pass_count + 1))
        echo -e "${GREEN}✅ $name : $message${NC}"
    else
        fail_count=$((fail_count + 1))
        echo -e "${RED}❌ $name : $message${NC}"
    fi
    test_results+=("$status|$name|$message")
}

generate_hmac_sign() {
    local app_id="$1"
    local timestamp="$2"
    local nonce="$3"
    local app_secret="$4"
    local payload="$app_id:$timestamp:$nonce"
    echo -n "$payload" | openssl dgst -sha256 -hmac "$app_secret" -binary | base64
}

# =====================================================
# 测试主体
# =====================================================

echo -e "${CYAN}=========================================="
echo -e "  XSS 微服务集群 - 服务器端集成测试"
echo -e "==========================================${NC}"
echo -e "API 地址: $API_BASE_URL"
echo ""

# ---------------------------------------------------
# 阶段1: 获取应用 Token
# ---------------------------------------------------
print_header "阶段1: 获取应用 Token"

timestamp=$(date +%s)
nonce=$(openssl rand -hex 16)
sign=$(generate_hmac_sign "$APP_ID" "$timestamp" "$nonce" "$APP_SECRET")

resp=$(curl -s -X POST "$API_BASE_URL/api/token/app" \
    -H "Content-Type: application/json" \
    -d "{\"appId\":\"$APP_ID\",\"timestamp\":$timestamp,\"nonce\":\"$nonce\",\"sign\":\"$sign\"}" \
    -w "%{http_code}" -o /tmp/resp.json)

if [ "$resp" = "200" ]; then
    app_token=$(cat /tmp/resp.json | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
    write_result "获取应用 Token" "PASS" "Token 获取成功"
else
    write_result "获取应用 Token" "FAIL" "状态码: $resp, 响应: $(cat /tmp/resp.json 2>/dev/null)"
    exit 1
fi

# ---------------------------------------------------
# 阶段2: 用户注册与登录
# ---------------------------------------------------
print_header "阶段2: 用户注册与登录"

# 注册
user_name="test_$(date +%s)"
resp=$(curl -s -X POST "$API_BASE_URL/auth/register" \
    -H "Content-Type: application/json" \
    -H "X-App-Id: $APP_ID" \
    -d "{\"username\":\"$user_name\",\"password\":\"Test@1234\",\"email\":\"${user_name}@test.com\",\"phone\":\"13800138000\"}" \
    -w "%{http_code}" -o /tmp/resp.json)

if [ "$resp" = "200" ]; then
    write_result "用户注册" "PASS" "注册成功"
else
    write_result "用户注册" "FAIL" "状态码: $resp"
fi

# 登录
resp=$(curl -s -X POST "$API_BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -H "X-App-Id: $APP_ID" \
    -d "{\"username\":\"$user_name\",\"password\":\"Test@1234\"}" \
    -w "%{http_code}" -o /tmp/resp.json)

if [ "$resp" = "200" ]; then
    user_token=$(cat /tmp/resp.json | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
    write_result "用户登录" "PASS" "登录成功"
else
    write_result "用户登录" "FAIL" "状态码: $resp"
    exit 1
fi

# ---------------------------------------------------
# 阶段3: 字典服务测试
# ---------------------------------------------------
print_header "阶段3: 字典服务测试"

# 查询国家列表
resp=$(curl -s -X GET "$API_BASE_URL/api/countries" \
    -H "Authorization: Bearer $user_token" \
    -w "%{http_code}" -o /tmp/resp.json)
[ "$resp" = "200" ] && write_result "查询国家列表" "PASS" "接口正常" || write_result "查询国家列表" "FAIL" "状态码: $resp"

# 查询字典类型
resp=$(curl -s -X GET "$API_BASE_URL/api/dict/types" \
    -H "Authorization: Bearer $user_token" \
    -w "%{http_code}" -o /tmp/resp.json)
[ "$resp" = "200" ] && write_result "查询字典类型" "PASS" "接口正常" || write_result "查询字典类型" "FAIL" "状态码: $resp"

# 查询字典项详情
resp=$(curl -s -X GET "$API_BASE_URL/api/dict/items/property_type" \
    -H "Authorization: Bearer $user_token" \
    -w "%{http_code}" -o /tmp/resp.json)
[ "$resp" = "200" ] && write_result "查询字典项详情" "PASS" "接口正常" || write_result "查询字典项详情" "FAIL" "状态码: $resp"

# ---------------------------------------------------
# 阶段4: 房源服务测试
# ---------------------------------------------------
print_header "阶段4: 房源服务测试"

# 创建房源
resp=$(curl -s -X POST "$API_BASE_URL/api/properties" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $user_token" \
    -d "{\"title\":\"测试房源\",\"description\":\"测试描述\",\"propertyTypeId\":1,\"provinceCode\":\"110000\",\"cityCode\":\"110100\",\"districtCode\":\"110101\",\"address\":\"测试地址\",\"price\":1000000,\"area\":100,\"bedrooms\":3,\"bathrooms\":2,\"latitude\":39.9042,\"longitude\":116.4074}" \
    -w "%{http_code}" -o /tmp/resp.json)

if [ "$resp" = "200" ]; then
    property_id=$(cat /tmp/resp.json | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['id'] if 'data' in d and 'id' in d['data'] else '0')")
    write_result "创建房源" "PASS" "房源创建成功, id=$property_id"
else
    write_result "创建房源" "FAIL" "状态码: $resp"
    property_id=0
fi

# 查询房源列表
resp=$(curl -s -X GET "$API_BASE_URL/api/properties?page=1&size=10" \
    -H "Authorization: Bearer $user_token" \
    -w "%{http_code}" -o /tmp/resp.json)
[ "$resp" = "200" ] && write_result "查询房源列表" "PASS" "接口正常" || write_result "查询房源列表" "FAIL" "状态码: $resp"

# 获取房源详情（如果创建成功）
if [ "$property_id" != "0" ]; then
    resp=$(curl -s -X GET "$API_BASE_URL/api/properties/$property_id" \
        -H "Authorization: Bearer $user_token" \
        -w "%{http_code}" -o /tmp/resp.json)
    [ "$resp" = "200" ] && write_result "获取房源详情" "PASS" "接口正常" || write_result "获取房源详情" "FAIL" "状态码: $resp"
fi

# ---------------------------------------------------
# 阶段5: 预订服务测试
# ---------------------------------------------------
print_header "阶段5: 预订服务测试"

if [ "$property_id" != "0" ]; then
    # 创建预订
    resp=$(curl -s -X POST "$API_BASE_URL/api/bookings" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $user_token" \
        -d "{\"propertyId\":$property_id,\"appointmentTime\":\"$(date -d "+2 days" +%Y-%m-%dT10:00:00)\",\"remark\":\"测试预订\"}" \
        -w "%{http_code}" -o /tmp/resp.json)
    [ "$resp" = "200" ] && write_result "创建预订" "PASS" "接口正常" || write_result "创建预订" "FAIL" "状态码: $resp"
fi

# ---------------------------------------------------
# 阶段6: 搜索服务测试
# ---------------------------------------------------
print_header "阶段6: 搜索服务测试"

resp=$(curl -s -X GET "$API_BASE_URL/api/search/properties?keyword=测试" \
    -H "Authorization: Bearer $user_token" \
    -w "%{http_code}" -o /tmp/resp.json)
[ "$resp" = "200" ] && write_result "全文检索" "PASS" "接口正常" || write_result "全文检索" "FAIL" "状态码: $resp"

# ---------------------------------------------------
# 阶段7: 收藏服务测试
# ---------------------------------------------------
print_header "阶段7: 收藏服务测试"

if [ "$property_id" != "0" ]; then
    resp=$(curl -s -X POST "$API_BASE_URL/api/favorites/$property_id" \
        -H "Authorization: Bearer $user_token" \
        -w "%{http_code}" -o /tmp/resp.json)
    [ "$resp" = "200" ] && write_result "添加收藏" "PASS" "接口正常" || write_result "添加收藏" "FAIL" "状态码: $resp"
fi

# ---------------------------------------------------
# 阶段8: 分析服务测试
# ---------------------------------------------------
print_header "阶段8: 分析服务测试"

resp=$(curl -s -X GET "$API_BASE_URL/api/stats/dashboard" \
    -H "Authorization: Bearer $user_token" \
    -w "%{http_code}" -o /tmp/resp.json)
[ "$resp" = "200" ] && write_result "数据看板" "PASS" "接口正常" || write_result "数据看板" "FAIL" "状态码: $resp"

# ---------------------------------------------------
# 阶段9: 用户登出
# ---------------------------------------------------
print_header "阶段9: 用户登出"

resp=$(curl -s -X POST "$API_BASE_URL/auth/logout" \
    -H "Authorization: Bearer $user_token" \
    -w "%{http_code}" -o /tmp/resp.json)
[ "$resp" = "200" ] && write_result "用户登出" "PASS" "登出成功" || write_result "用户登出" "FAIL" "状态码: $resp"

# ---------------------------------------------------
# 汇总报告
# ---------------------------------------------------
echo ""
echo -e "${CYAN}=========================================="
echo -e "  测试结果汇总"
echo -e "==========================================${NC}"
echo ""

if [ $fail_count -eq 0 ]; then
    echo -e "${GREEN}✅ 通过: $pass_count"
    echo -e "${GREEN}❌ 失败: $fail_count"
    echo -e "${GREEN}📊 通过率: 100%${NC}"
else
    echo -e "${GREEN}✅ 通过: $pass_count"
    echo -e "${RED}❌ 失败: $fail_count"
    echo -e "${YELLOW}📊 通过率: $(echo "scale=2; $pass_count * 100 / ($pass_count + $fail_count)" | bc)%${NC}"
    echo ""
    echo -e "${RED}失败详情:${NC}"
    for result in "${test_results[@]}"; do
        IFS='|' read -r status name message <<< "$result"
        if [ "$status" = "FAIL" ]; then
            echo -e "  - $name: $message"
        fi
    done
fi

echo ""
echo -e "${YELLOW}注意事项:${NC}"
echo -e "  1. 如果部分接口失败，可能是服务尚未完全启动"
echo -e "  2. 服务完全启动需要 3-5 分钟"
echo -e "  3. 查看服务日志: docker logs -f xss-gateway"
echo ""

# 清理临时文件
rm -f /tmp/resp.json

# 返回退出码
if [ $fail_count -gt 0 ]; then
    exit 1
else
    exit 0
fi
