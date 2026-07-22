# XSS 微服务全部接口测试脚本 (76个接口)
# 测试策略:
#   - GET 读接口: 验证返回200或合理的业务错误
#   - POST/PUT/DELETE 写接口: 验证接口可达(非404即认为接口存在)
#   - 管理接口(refresh-*): 验证可达
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(30)

$base = "http://42.193.174.175"
$script:pass = 0
$script:fail = 0
$script:skip = 0
$script:failList = @()
$script:results = @()

function Test-Api($name, $method, $url, $body = $null, $headers = $null, $isWrite = $false) {
    $req = New-Object System.Net.Http.HttpRequestMessage($method, $url)
    if ($headers) {
        foreach ($k in $headers.Keys) { $req.Headers.Add($k, $headers[$k]) }
    }
    if ($body) {
        $content = New-Object System.Net.Http.StringContent($body, [System.Text.Encoding]::UTF8, "application/json")
        $req.Content = $content
    }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $resp = $client.SendAsync($req).Result
        $sw.Stop()
        $respBody = $resp.Content.ReadAsStringAsync().Result
        $bodyPreview = if ($respBody.Length -gt 80) { $respBody.Substring(0, 80) + "..." } else { $respBody }
        $status = [int]$resp.StatusCode
        $color = if ($status -lt 300) { 'Green' } elseif ($status -lt 500) { 'Yellow' } else { 'Red' }

        # 判断是否通过:
        # - GET: 200/400/401/403/500 都算"接口可达"(非404), 但只200算通过
        # - 写接口: 非404就算"接口可达"
        $reachable = ($status -ne 404)
        if ($isWrite) {
            $passed = $reachable
        } else {
            $passed = ($status -lt 300)
        }

        Write-Host ("[{0,3}] {1,-38} {2,5}ms | {3}" -f $status, $name, $sw.ElapsedMilliseconds, $bodyPreview) -ForegroundColor $color
        if ($passed) { $script:pass++ } else { $script:fail++; $script:failList += "$name (HTTP $status)" }
        return @{ name = $name; status = $status; body = $respBody; ok = $passed }
    } catch {
        $sw.Stop()
        Write-Host ("[ERR] {0,-38} {1,5}ms | {2}" -f $name, $sw.ElapsedMilliseconds, $_.Exception.Message) -ForegroundColor Red
        $script:fail++
        $script:failList += "$name (Exception)"
        return @{ name = $name; status = 0; body = $_.Exception.Message; ok = $false }
    }
}

Write-Host "===== XSS 微服务全部接口测试 (76个) =====" -ForegroundColor Cyan
Write-Host "服务器: $base" -ForegroundColor Cyan
Write-Host ""

# ==================== 1. Gateway (2个) ====================
Write-Host "--- Gateway 网关 ---" -ForegroundColor Cyan
Test-Api "GET / (Nginx根)" "GET" "$base/" | Out-Null
Test-Api "GET /actuator/health" "GET" "$base/actuator/health" | Out-Null

# ==================== 获取 App Token ====================
Write-Host "`n--- 获取 App Token ---" -ForegroundColor Cyan
$timeResp = $client.GetAsync("$base/actuator/health").Result
$serverDate = $timeResp.Headers.Date
$timestamp = if ($serverDate) {
    [int]([DateTimeOffset]$serverDate).ToUnixTimeSeconds()
} else {
    [int][double]::Parse((Get-Date -UFormat %s))
}

$appId = "my-backend-system"
$appSecret = "XssBackend2026SecHmacKey8xYz"
$nonce = "$timestamp" + (Get-Random).ToString()
$payload = "$appId`:$timestamp`:$nonce"
$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($appSecret)
$sign = [Convert]::ToBase64String($hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($payload)))
$tokenUrl = "$base/token?appId=$appId&timestamp=$timestamp&nonce=$nonce&sign=$([uri]::EscapeDataString($sign))"
$tokenResult = Test-Api "POST /token (App Token)" "POST" $tokenUrl

$appToken = ""
try {
    $tj = $tokenResult.body | ConvertFrom-Json
    if ($tj.data.token) { $appToken = $tj.data.token }
} catch {}
if (-not $appToken) { Write-Host "[FAIL] App Token 获取失败" -ForegroundColor Red }
$appAuthH = @{ "Authorization" = "Bearer $appToken" }

# ==================== 注册+登录获取 User Token ====================
Write-Host "`n--- 用户注册+登录 ---" -ForegroundColor Cyan
$testUser = "tester_" + (Get-Date -Format "yyyyMMddHHmmss")
$regBody = "{`"username`":`"$testUser`",`"password`":`"Test12345678`",`"email`":`"$testUser@example.com`",`"phone`":`"13800138000`"}"
Test-Api "POST /auth/register" "POST" "$base/auth/register" $regBody | Out-Null

$loginBody = "{`"username`":`"$testUser`",`"password`":`"Test12345678`"}"
$loginResult = Test-Api "POST /auth/login" "POST" "$base/auth/login" $loginBody
$userToken = ""
try {
    $lj = $loginResult.body | ConvertFrom-Json
    if ($lj.token) { $userToken = $lj.token }
} catch {}
if ($userToken) {
    $userAuthH = @{ "Authorization" = "Bearer $userToken" }
} else {
    Write-Host "[FAIL] 登录失败,后续接口将使用App Token" -ForegroundColor Red
    $userAuthH = $appAuthH
}

# ==================== 2. Auth 认证服务 (5个,已测2个) ====================
Write-Host "`n--- Auth 认证服务 ---" -ForegroundColor Cyan
Test-Api "GET /auth/userinfo" "GET" "$base/auth/userinfo" $null $userAuthH | Out-Null
Test-Api "POST /auth/logout" "POST" "$base/auth/logout" $null $userAuthH $true | Out-Null
Test-Api "POST /auth/refresh" "POST" "$base/auth/refresh" $null $userAuthH $true | Out-Null

# ==================== 3. Dict 字典服务 (25个) ====================
Write-Host "`n--- Dict 字典服务 ---" -ForegroundColor Cyan
# 通用字典 (4个)
Test-Api "GET /api/dict/types" "GET" "$base/api/dict/types" $null $appAuthH | Out-Null
Test-Api "GET /api/dict/property_type/list" "GET" "$base/api/dict/property_type/list" $null $appAuthH | Out-Null
Test-Api "GET /api/dict/property_type/item/residential" "GET" "$base/api/dict/property_type/item/residential" $null $appAuthH | Out-Null
Test-Api "GET /api/dict/region/tree" "GET" "$base/api/dict/region/tree" $null $appAuthH | Out-Null
# 房产字典 (3个)
Test-Api "GET /api/dict/items?type=property_type" "GET" "$base/api/dict/items?type=property_type" $null $appAuthH | Out-Null
Test-Api "GET /api/dict/items/property_type/residential" "GET" "$base/api/dict/items/property_type/residential" $null $appAuthH | Out-Null
Test-Api "GET /api/dict/property-types" "GET" "$base/api/dict/property-types" $null $appAuthH | Out-Null
# 行政区划 (6个)
Test-Api "GET /api/provinces" "GET" "$base/api/provinces" $null $appAuthH | Out-Null
Test-Api "GET /api/cities?provinceCode=440000" "GET" "$base/api/cities?provinceCode=440000" $null $appAuthH | Out-Null
Test-Api "GET /api/districts?cityCode=440300" "GET" "$base/api/districts?cityCode=440300" $null $appAuthH | Out-Null
Test-Api "GET /api/towns?districtCode=440305" "GET" "$base/api/towns?districtCode=440305" $null $appAuthH | Out-Null
Test-Api "GET /api/villages?townCode=440305001" "GET" "$base/api/villages?townCode=440305001" $null $appAuthH | Out-Null
Test-Api "GET /api/regions/path?code=440305" "GET" "$base/api/regions/path?code=440305" $null $appAuthH | Out-Null
# 国际化 (8个)
Test-Api "GET /api/countries" "GET" "$base/api/countries" $null $appAuthH | Out-Null
Test-Api "GET /api/countries/CN" "GET" "$base/api/countries/CN" $null $appAuthH | Out-Null
Test-Api "GET /api/currencies" "GET" "$base/api/currencies" $null $appAuthH | Out-Null
Test-Api "GET /api/currencies/CNY" "GET" "$base/api/currencies/CNY" $null $appAuthH | Out-Null
Test-Api "GET /api/languages" "GET" "$base/api/languages" $null $appAuthH | Out-Null
Test-Api "GET /api/languages/zh" "GET" "$base/api/languages/zh" $null $appAuthH | Out-Null
Test-Api "GET /api/timezones" "GET" "$base/api/timezones" $null $appAuthH | Out-Null
Test-Api "GET /api/timezones/detail?timezoneId=Asia/Shanghai" "GET" "$base/api/timezones/detail?timezoneId=Asia/Shanghai" $null $appAuthH | Out-Null
# 管理接口 (6个,写操作)
Test-Api "POST /api/dict/admin/refresh/property_type" "POST" "$base/api/dict/admin/refresh/property_type" $null $appAuthH $true | Out-Null
Test-Api "POST /api/dict/admin/refresh-all" "POST" "$base/api/dict/admin/refresh-all" $null $appAuthH $true | Out-Null
Test-Api "POST /api/admin/refresh-countries" "POST" "$base/api/admin/refresh-countries" $null $appAuthH $true | Out-Null
Test-Api "POST /api/admin/refresh-currencies" "POST" "$base/api/admin/refresh-currencies" $null $appAuthH $true | Out-Null
Test-Api "POST /api/admin/refresh-languages" "POST" "$base/api/admin/refresh-languages" $null $appAuthH $true | Out-Null
Test-Api "POST /api/admin/refresh-regions" "POST" "$base/api/admin/refresh-regions" $null $appAuthH $true | Out-Null

# ==================== 4. Property 房源服务 (10个) ====================
Write-Host "`n--- Property 房源服务 ---" -ForegroundColor Cyan
# 先创建一个房源用于后续测试
$createBody = '{"title":"测试房源API","description":"测试","price":5000,"area":80,"cityCode":"440300","districtCode":"440305","address":"测试地址","latitude":22.54,"longitude":113.94,"bedrooms":2,"bathrooms":1,"floor":10,"totalFloors":20,"propertyType":"residential"}'
$createResult = Test-Api "POST /api/properties (创建)" "POST" "$base/api/properties" $createBody $userAuthH $true
$propertyId = "1"
try {
    $cj = $createResult.body | ConvertFrom-Json
    if ($cj.data -and $cj.data.id) { $propertyId = $cj.data.id.ToString() }
} catch {}

Test-Api "GET /api/properties (列表)" "GET" "$base/api/properties?page=1&size=10" $null $userAuthH | Out-Null
Test-Api "GET /api/properties/$propertyId (详情)" "GET" "$base/api/properties/$propertyId" $null $userAuthH | Out-Null
Test-Api "GET /api/properties/$propertyId/exists" "GET" "$base/api/properties/$propertyId/exists" $null $userAuthH | Out-Null
Test-Api "GET /api/properties/brief" "GET" "$base/api/properties/brief?page=1&size=10" $null $userAuthH | Out-Null
Test-Api "PUT /api/properties/$propertyId (更新)" "PUT" "$base/api/properties/$propertyId" '{"title":"更新标题"}' $userAuthH $true | Out-Null
Test-Api "PUT /api/properties/$propertyId/publish-status" "PUT" "$base/api/properties/$propertyId/publish-status?status=1" $null $userAuthH $true | Out-Null
Test-Api "PUT /api/properties/$propertyId/audit-status" "PUT" "$base/api/properties/$propertyId/audit-status?status=1" $null $userAuthH $true | Out-Null
Test-Api "DELETE /api/properties/99999 (删除不存在)" "DELETE" "$base/api/properties/99999" $null $userAuthH $true | Out-Null

# ==================== 5. Image 图片服务 (7个) ====================
Write-Host "`n--- Image 图片服务 ---" -ForegroundColor Cyan
Test-Api "GET /api/images (列表)" "GET" "$base/api/images?page=1&size=20" $null $userAuthH | Out-Null
Test-Api "DELETE /api/images/99999 (删除不存在)" "DELETE" "$base/api/images/99999" $null $userAuthH $true | Out-Null
# 图片组
Test-Api "POST /api/groups (创建组)" "POST" "$base/api/groups" '{"name":"测试组","description":"测试"}' $userAuthH $true | Out-Null
Test-Api "GET /api/groups (组列表)" "GET" "$base/api/groups?page=1&size=20" $null $userAuthH | Out-Null
Test-Api "GET /api/groups/1/images" "GET" "$base/api/groups/1/images" $null $userAuthH | Out-Null
Test-Api "POST /api/groups/1/images (添加图片)" "POST" "$base/api/groups/1/images" '{"imageIds":[1]}' $userAuthH $true | Out-Null
# 文件上传测试(只验证接口可达,不实际传文件)
Test-Api "POST /api/images/upload (无文件)" "POST" "$base/api/images/upload" $null $userAuthH $true | Out-Null

# ==================== 6. Search 搜索服务 (2个) ====================
Write-Host "`n--- Search 搜索服务 ---" -ForegroundColor Cyan
Test-Api "GET /api/search/properties" "GET" "$base/api/search/properties?page=1&size=10" $null $userAuthH | Out-Null
Test-Api "POST /api/search/admin/reindex" "POST" "$base/api/search/admin/reindex" $null $userAuthH $true | Out-Null

# ==================== 7. Analytics 统计服务 (4个) ====================
Write-Host "`n--- Analytics 统计服务 ---" -ForegroundColor Cyan
Test-Api "GET /api/stats/dashboard" "GET" "$base/api/stats/dashboard" $null $userAuthH | Out-Null
Test-Api "GET /api/stats/property/views" "GET" "$base/api/stats/property/views?appId=default&startDate=2026-01-01" $null $userAuthH | Out-Null
Test-Api "GET /api/stats/image/upload-summary" "GET" "$base/api/stats/image/upload-summary?appId=default" $null $userAuthH | Out-Null
Test-Api "GET /api/stats/user/actions" "GET" "$base/api/stats/user/actions?appId=default" $null $userAuthH | Out-Null

# ==================== 8. Message 消息服务 (5个) ====================
Write-Host "`n--- Message 消息服务 ---" -ForegroundColor Cyan
Test-Api "GET /api/messages/records" "GET" "$base/api/messages/records?page=1&size=20" $null $userAuthH | Out-Null
Test-Api "GET /api/messages/records/1" "GET" "$base/api/messages/records/1" $null $userAuthH | Out-Null
Test-Api "POST /api/messages/retry/1" "POST" "$base/api/messages/retry/1" $null $userAuthH $true | Out-Null
Test-Api "POST /api/messages/send" "POST" "$base/api/messages/send" '{"appId":"my-backend-system","templateCode":"TEST","receiver":"test@example.com","channel":"EMAIL","subject":"测试","params":{}}' $userAuthH $true | Out-Null
Test-Api "POST /api/messages/send-test?email=test@example.com" "POST" "$base/api/messages/send-test?email=test@example.com" $null $userAuthH $true | Out-Null

# ==================== 9. Favorite 收藏服务 (4个) ====================
Write-Host "`n--- Favorite 收藏服务 ---" -ForegroundColor Cyan
Test-Api "GET /api/favorites" "GET" "$base/api/favorites" $null $userAuthH | Out-Null
Test-Api "POST /api/favorites/$propertyId (添加收藏)" "POST" "$base/api/favorites/$propertyId" $null $userAuthH $true | Out-Null
Test-Api "GET /api/favorites/check/$propertyId" "GET" "$base/api/favorites/check/$propertyId" $null $userAuthH | Out-Null
Test-Api "DELETE /api/favorites/$propertyId (取消收藏)" "DELETE" "$base/api/favorites/$propertyId" $null $userAuthH $true | Out-Null

# ==================== 10. Review 审核服务 (4个) ====================
Write-Host "`n--- Review 审核服务 ---" -ForegroundColor Cyan
Test-Api "GET /api/audit/tasks" "GET" "$base/api/audit/tasks?page=1&size=10" $null $userAuthH | Out-Null
Test-Api "GET /api/audit/tasks/1" "GET" "$base/api/audit/tasks/1" $null $userAuthH | Out-Null
Test-Api "POST /api/audit/tasks (创建审核)" "POST" "$base/api/audit/tasks?propertyId=$propertyId&taskType=MANUAL" $null $userAuthH $true | Out-Null
Test-Api "POST /api/audit/manual" "POST" "$base/api/audit/manual" '{"taskId":1,"result":1,"reason":"合规","auditorId":1}' $userAuthH $true | Out-Null

# ==================== 11. Booking 预约服务 (8个) ====================
Write-Host "`n--- Booking 预约服务 ---" -ForegroundColor Cyan
$bookingBody = "{`"propertyId`":$propertyId,`"appointmentTime`":`"2026-08-01T14:00:00`",`"remark`":`"测试预约`"}"
$bookingResult = Test-Api "POST /api/bookings (创建预约)" "POST" "$base/api/bookings" $bookingBody $userAuthH $true
$bookingId = "1"
try {
    $bj = $bookingResult.body | ConvertFrom-Json
    if ($bj.data -and $bj.data.id) { $bookingId = $bj.data.id.ToString() }
} catch {}

Test-Api "GET /api/bookings/$bookingId (详情)" "GET" "$base/api/bookings/$bookingId" $null $userAuthH | Out-Null
Test-Api "GET /api/bookings/user" "GET" "$base/api/bookings/user?page=1&size=10" $null $userAuthH | Out-Null
Test-Api "GET /api/bookings/agent" "GET" "$base/api/bookings/agent?page=1&size=10" $null $userAuthH | Out-Null
Test-Api "PUT /api/bookings/$bookingId/confirm" "PUT" "$base/api/bookings/$bookingId/confirm" $null $userAuthH $true | Out-Null
Test-Api "PUT /api/bookings/$bookingId/complete" "PUT" "$base/api/bookings/$bookingId/complete" $null $userAuthH $true | Out-Null
Test-Api "PUT /api/bookings/$bookingId/cancel/user" "PUT" "$base/api/bookings/$bookingId/cancel/user?reason=测试" $null $userAuthH $true | Out-Null
Test-Api "PUT /api/bookings/$bookingId/cancel/agent" "PUT" "$base/api/bookings/$bookingId/cancel/agent?reason=测试" $null $userAuthH $true | Out-Null

# ==================== 汇总 ====================
Write-Host "`n===== 测试汇总 =====" -ForegroundColor Cyan
$total = $script:pass + $script:fail
Write-Host ("  通过: $script:pass / $total") -ForegroundColor Green
Write-Host ("  失败: $script:fail / $total") -ForegroundColor $(if ($script:fail -gt 0) { 'Red' } else { 'Green' })
if ($script:failList.Count -gt 0) {
    Write-Host "`n失败项:" -ForegroundColor Red
    foreach ($f in $script:failList) { Write-Host "  - $f" -ForegroundColor Red }
}
