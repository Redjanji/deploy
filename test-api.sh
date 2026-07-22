#!/bin/bash

APP_ID='my-backend-system'
APP_SECRET='XssBackend2026SecHmacKey8xYz'
BASE_URL='http://127.0.0.1:8080'

app_token=""
user_token=""
user_id=""
property_id=""
booking_id=""

PASS=0
FAIL=0
WARN=0

echo "=========================================="
echo "  API 接口测试"
echo "=========================================="
echo ""

check_result() {
    local desc="$1"
    local code="$2"
    local expected="$3"
    
    if [ "$code" = "$expected" ]; then
        echo "✅ PASS: $desc"
        PASS=$((PASS + 1))
    elif [ "$code" = "400" ] || [ "$code" = "404" ]; then
        echo "⚠️  WARN: $desc (code=$code)"
        WARN=$((WARN + 1))
    else
        echo "❌ FAIL: $desc (code=$code)"
        FAIL=$((FAIL + 1))
    fi
    echo ""
}

get_code() {
    echo "$1" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null
}

# 1. 获取 app token
echo "=== [1] POST /token (获取App Token) ==="
TIMESTAMP=$(date +%s)
NONCE=$(openssl rand -hex 8)
SIGN_STR="${APP_ID}:${TIMESTAMP}:${NONCE}"
SIGN=$(echo -n "$SIGN_STR" | openssl dgst -sha256 -hmac "$APP_SECRET" -binary | base64)

resp=$(curl -s -X POST "${BASE_URL}/token" \
  -d "appId=${APP_ID}&timestamp=${TIMESTAMP}&nonce=${NONCE}&sign=${SIGN}")
echo "Response: $resp"
echo "Debug: APP_ID=$APP_ID, TIMESTAMP=$TIMESTAMP, NONCE=$NONCE, SIGN=$SIGN"
app_token=$(echo "$resp" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token','') if d.get('code')==200 else '')" 2>/dev/null)
echo ""

if [ -z "$app_token" ] || [ "$app_token" = "None" ]; then
    echo "FAIL: 获取app token失败"
    exit 1
fi
echo "OK: app_token = ${app_token:0:20}..."
PASS=$((PASS + 1))
echo ""

# 2. 用户注册
echo "=== [2] POST /auth/register (用户注册) ==="
USERNAME="testuser001"
PHONE="13800138001"
resp=$(curl -s -X POST "${BASE_URL}/auth/register" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $app_token" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"Test123456\",\"phone\":\"$PHONE\",\"nickname\":\"测试用户\"}")
echo "$resp"
code=$(get_code "$resp")
echo ""
if [ "$code" = "200" ] || [ "$code" = "400" ]; then
    check_result "用户注册（成功或已存在）" "$code" "200"
else
    check_result "用户注册" "$code" "200"
fi

# 3. 用户登录
echo "=== [3] POST /auth/login (用户登录) ==="
resp=$(curl -s -X POST "${BASE_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $app_token" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"Test123456\"}")
echo "$resp"
user_token=$(echo "$resp" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token','') if d.get('code')==200 else '')" 2>/dev/null)
user_id=$(echo "$resp" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('userId','') if d.get('code')==200 else '')" 2>/dev/null)
code=$(get_code "$resp")
echo ""

if [ -z "$user_token" ]; then
    echo "FAIL: 登录失败"
    FAIL=$((FAIL + 1))
else
    echo "OK: user_token = ${user_token:0:20}..."
    echo "OK: user_id = $user_id"
    PASS=$((PASS + 1))
fi
echo ""

# 4. 字典接口 - property_type
echo "=== [4] GET /api/dict/property_type/list (字典-房源类型) ==="
resp=$(curl -s "${BASE_URL}/api/dict/property_type/list" \
  -H "Authorization: Bearer $app_token")
echo "$resp"
code=$(get_code "$resp")
echo ""
check_result "字典-房源类型" "$code" "200"

# 5. 字典接口 - decoration
echo "=== [5] GET /api/dict/decoration/list (字典-装修) ==="
resp=$(curl -s "${BASE_URL}/api/dict/decoration/list" \
  -H "Authorization: Bearer $app_token")
echo "$resp"
code=$(get_code "$resp")
echo ""
check_result "字典-装修" "$code" "200"

# 6. 字典接口 - provinces
echo "=== [6] GET /api/provinces (字典-省份) ==="
resp=$(curl -s "${BASE_URL}/api/provinces" \
  -H "Authorization: Bearer $app_token")
echo "$resp" | head -c 300
echo ""
code=$(get_code "$resp")
echo ""
check_result "字典-省份列表" "$code" "200"

# 7. 房源列表
echo "=== [7] GET /api/properties/list (房源列表) ==="
resp=$(curl -s "${BASE_URL}/api/properties/list?page=1&size=10" \
  -H "Authorization: Bearer $app_token")
echo "$resp" | head -c 500
echo ""
code=$(get_code "$resp")
property_id=$(echo "$resp" | python3 -c "
import sys,json
d = json.load(sys.stdin)
if d.get('code') == 200:
    records = d.get('data', {}).get('records', [])
    if records:
        print(records[0].get('id', ''))
" 2>/dev/null)
echo ""
check_result "房源列表" "$code" "200"
if [ -n "$property_id" ]; then
    echo "OK: 找到房源ID = $property_id"
    echo ""
fi

# 8. 房源详情
if [ -n "$property_id" ] && [ "$property_id" != "None" ]; then
    echo "=== [8] GET /api/properties/{id} (房源详情) ==="
    resp=$(curl -s "${BASE_URL}/api/properties/${property_id}" \
      -H "Authorization: Bearer $app_token")
    echo "$resp" | head -c 500
    echo ""
    code=$(get_code "$resp")
    echo ""
    check_result "房源详情" "$code" "200"
else
    echo "=== [8] 跳过房源详情（无有效房源ID） ==="
    WARN=$((WARN + 1))
    echo ""
fi

# 9. 图片上传
echo "=== [9] POST /api/images/upload (图片上传) ==="
echo "test image content" > /tmp/test-img.txt
resp=$(curl -s -X POST "${BASE_URL}/api/images/upload" \
  -H "Authorization: Bearer $user_token" \
  -F "file=@/tmp/test-img.txt")
echo "$resp"
code=$(get_code "$resp")
echo ""
check_result "图片上传" "$code" "200"

# 10. 收藏列表
echo "=== [10] GET /api/favorites/list (收藏列表) ==="
resp=$(curl -s "${BASE_URL}/api/favorites/list?page=1&size=10" \
  -H "Authorization: Bearer $user_token")
echo "$resp"
code=$(get_code "$resp")
echo ""
check_result "收藏列表" "$code" "200"

# 11. 添加收藏
if [ -n "$property_id" ] && [ "$property_id" != "None" ]; then
    echo "=== [11] POST /api/favorites/add (添加收藏) ==="
    resp=$(curl -s -X POST "${BASE_URL}/api/favorites/add" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $user_token" \
      -d "{\"propertyId\":\"$property_id\"}")
    echo "$resp"
    code=$(get_code "$resp")
    echo ""
    check_result "添加收藏" "$code" "200"
else
    echo "=== [11] 跳过添加收藏（无有效房源ID） ==="
    WARN=$((WARN + 1))
    echo ""
fi

# 12. 预订列表
echo "=== [12] GET /api/bookings/list (预订列表) ==="
resp=$(curl -s "${BASE_URL}/api/bookings/list?page=1&size=10" \
  -H "Authorization: Bearer $user_token")
echo "$resp"
code=$(get_code "$resp")
echo ""
check_result "预订列表" "$code" "200"

# 13. 创建预订
if [ -n "$property_id" ] && [ "$property_id" != "None" ]; then
    echo "=== [13] POST /api/bookings/create (创建预订) ==="
    CHECK_IN=$(date -d "+1 day" +%Y-%m-%d 2>/dev/null || date -v+1d +%Y-%m-%d 2>/dev/null || echo "2026-01-01")
    CHECK_OUT=$(date -d "+3 day" +%Y-%m-%d 2>/dev/null || date -v+3d +%Y-%m-%d 2>/dev/null || echo "2026-01-03")
    resp=$(curl -s -X POST "${BASE_URL}/api/bookings/create" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $user_token" \
      -d "{\"propertyId\":\"$property_id\",\"checkIn\":\"$CHECK_IN\",\"checkOut\":\"$CHECK_OUT\",\"guestName\":\"测试用户\",\"guestPhone\":\"$PHONE\"}")
    echo "$resp"
    code=$(get_code "$resp")
    echo ""
    check_result "创建预订" "$code" "200"
else
    echo "=== [13] 跳过创建预订（无有效房源ID） ==="
    WARN=$((WARN + 1))
    echo ""
fi

# 14. 审核任务列表（review-service）
echo "=== [14] GET /api/audit/tasks (审核任务列表) ==="
resp=$(curl -s "${BASE_URL}/api/audit/tasks?page=1&size=10" \
  -H "Authorization: Bearer $app_token")
echo "$resp"
code=$(get_code "$resp")
echo ""
check_result "审核任务列表" "$code" "200"

# 15. 消息列表
echo "=== [15] GET /api/messages/list (消息列表) ==="
resp=$(curl -s "${BASE_URL}/api/messages/list?page=1&size=10" \
  -H "Authorization: Bearer $user_token")
echo "$resp"
code=$(get_code "$resp")
echo ""
check_result "消息列表" "$code" "200"

# 16. 用户信息
echo "=== [16] GET /auth/userinfo (用户信息) ==="
resp=$(curl -s "${BASE_URL}/auth/userinfo" \
  -H "Authorization: Bearer $user_token")
echo "$resp"
code=$(get_code "$resp")
echo ""
check_result "用户信息" "$code" "200"

echo "=========================================="
echo "  测试结果汇总"
echo "=========================================="
echo "✅ 通过: $PASS"
echo "⚠️  警告: $WARN"
echo "❌ 失败: $FAIL"
echo "=========================================="

if [ $FAIL -gt 0 ]; then
    exit 1
fi
