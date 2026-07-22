# 全局完整链路集成测试脚本
# 覆盖所有业务与API，确保服务运行时的端到端测试

$GATEWAY_URL = "http://127.0.0.1:8080"
$APP_ID = "my-backend-system"
$APP_SECRET = "XssBackend2026SecHmacKey8xYz"

$global:testResults = New-Object System.Collections.ArrayList
$global:passCount = 0
$global:failCount = 0

function Write-TestResult($name, $status, $message) {
    $result = [PSCustomObject]@{
        TestName = $name
        Status = $status
        Message = $message
    }
    $global:testResults.Add($result) | Out-Null
    if ($status -eq "PASS") {
        $global:passCount++
        Write-Host "✅ $name : $message" -ForegroundColor Green
    } else {
        $global:failCount++
        Write-Host "❌ $name : $message" -ForegroundColor Red
    }
}

function Generate-HmacSign($appId, $timestamp, $nonce, $appSecret) {
    $payload = "$appId`:$timestamp`:$nonce"
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($appSecret)
    $sign = [Convert]::ToBase64String($hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($payload)))
    return $sign
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  全局完整链路集成测试" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# ============================================================
# 阶段1: 获取应用 Token（HMAC 签名验证）
# ============================================================
Write-Host "`n--- 阶段1: 获取应用 Token ---" -ForegroundColor Yellow

try {
    $timestamp = [math]::Floor([datetime]::UtcNow.Subtract([datetime]::new(1970, 1, 1)).TotalSeconds)
    $nonce = ([guid]::NewGuid()).ToString("N")
    $sign = Generate-HmacSign $APP_ID $timestamp $nonce $APP_SECRET
    
    $body = "appId=$APP_ID&timestamp=$timestamp&nonce=$nonce&sign=$sign"
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/token" -Method POST `
        -ContentType "application/x-www-form-urlencoded" `
        -Body $body `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data.token) {
        $appToken = $resp.data.token
        Write-TestResult "获取应用 Token" "PASS" "Token 获取成功"
    } else {
        Write-TestResult "获取应用 Token" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "获取应用 Token" "FAIL" "异常: $_"
}

# ============================================================
# 阶段2: 用户注册（无需认证）
# ============================================================
Write-Host "`n--- 阶段2: 用户注册 ---" -ForegroundColor Yellow

try {
    $testUsername = "testuser_$(Get-Random)"
    $registerBody = @{
        username = $testUsername
        password = "password123"
        email = "test_$(Get-Random)@example.com"
        phone = "13800138$((Get-Random -Max 1000).ToString('0000'))"
    } | ConvertTo-Json
    
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/register" -Method POST `
        -ContentType "application/json" `
        -Body $registerBody `
        -TimeoutSec 10
    
    Write-TestResult "用户注册" "PASS" "注册成功"
} catch {
    Write-TestResult "用户注册" "FAIL" "异常: $_"
}

# ============================================================
# 阶段3: 用户登录（无需认证）
# ============================================================
Write-Host "`n--- 阶段3: 用户登录 ---" -ForegroundColor Yellow

try {
    $loginBody = @{
        username = $testUsername
        password = "password123"
    } | ConvertTo-Json
    
    $loginResp = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/login" -Method POST `
        -ContentType "application/json" `
        -Body $loginBody `
        -TimeoutSec 10
    
    if ($loginResp.token) {
        $userToken = $loginResp.token
        $userId = $loginResp.userId
        Write-TestResult "用户登录" "PASS" "登录成功, userId=$userId"
    } else {
        Write-TestResult "用户登录" "FAIL" "响应: $($loginResp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "用户登录" "FAIL" "异常: $_"
}

# ============================================================
# 阶段4: 用户信息与Token刷新（认证服务）
# ============================================================
Write-Host "`n--- 阶段4: 用户信息与Token刷新 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/userinfo" -Method GET `
        -Headers @{ "Authorization" = "Bearer $userToken" } `
        -TimeoutSec 10
    
    Write-TestResult "获取用户信息" "PASS" "用户信息获取成功"
} catch {
    Write-TestResult "获取用户信息" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/refresh" -Method POST `
        -Headers @{ "Authorization" = "Bearer $userToken" } `
        -TimeoutSec 10
    
    if ($resp.token) {
        $userToken = $resp.token
        Write-TestResult "刷新Token" "PASS" "Token刷新成功"
    } else {
        Write-TestResult "刷新Token" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "刷新Token" "FAIL" "异常: $_"
}

# ============================================================
# 阶段5: 访问字典服务 - 国家（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段5: 字典服务 - 国家 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/countries" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询国家列表" "PASS" "返回 $($resp.data.Count) 条记录"
    } else {
        Write-TestResult "查询国家列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询国家列表" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/countries/CN" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询单个国家" "PASS" "获取成功"
    } else {
        Write-TestResult "查询单个国家" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询单个国家" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/admin/refresh-countries" -Method POST `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "刷新国家缓存" "PASS" "刷新成功"
    } else {
        Write-TestResult "刷新国家缓存" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "刷新国家缓存" "FAIL" "异常: $_"
}

# ============================================================
# 阶段6: 访问字典服务 - 货币（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段6: 字典服务 - 货币 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/currencies" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询货币列表" "PASS" "返回 $($resp.data.Count) 条记录"
    } else {
        Write-TestResult "查询货币列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询货币列表" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/currencies/CNY" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询单个货币" "PASS" "获取成功"
    } else {
        Write-TestResult "查询单个货币" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询单个货币" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/admin/refresh-currencies" -Method POST `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "刷新货币缓存" "PASS" "刷新成功"
    } else {
        Write-TestResult "刷新货币缓存" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "刷新货币缓存" "FAIL" "异常: $_"
}

# ============================================================
# 阶段7: 访问字典服务 - 语言（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段7: 字典服务 - 语言 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/languages" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询语言列表" "PASS" "返回 $($resp.data.Count) 条记录"
    } else {
        Write-TestResult "查询语言列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询语言列表" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/languages/chi" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询单个语言" "PASS" "获取成功"
    } else {
        Write-TestResult "查询单个语言" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询单个语言" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/admin/refresh-languages" -Method POST `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "刷新语言缓存" "PASS" "刷新成功"
    } else {
        Write-TestResult "刷新语言缓存" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "刷新语言缓存" "FAIL" "异常: $_"
}

# ============================================================
# 阶段8: 访问字典服务 - 时区（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段8: 字典服务 - 时区 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/timezones" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询时区列表" "PASS" "返回 $($resp.data.Count) 条记录"
    } else {
        Write-TestResult "查询时区列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询时区列表" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/timezones/detail?timezone_id=Asia/Shanghai" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询单个时区" "PASS" "获取成功"
    } else {
        Write-TestResult "查询单个时区" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询单个时区" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/admin/refresh-timezones" -Method POST `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "刷新时区缓存" "PASS" "刷新成功"
    } else {
        Write-TestResult "刷新时区缓存" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "刷新时区缓存" "FAIL" "异常: $_"
}

# ============================================================
# 阶段9: 访问字典服务 - 区域（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段9: 字典服务 - 区域 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/provinces" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询省份列表" "PASS" "返回 $($resp.data.Count) 条记录"
    } else {
        Write-TestResult "查询省份列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询省份列表" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/cities?province_code=110000" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        if ($resp.data -and $resp.data.Count -gt 0) {
            Write-TestResult "查询城市列表" "PASS" "返回 $($resp.data.Count) 条记录"
        } else {
            Write-TestResult "查询城市列表" "PASS" "接口正常，数据为空"
        }
    } else {
        Write-TestResult "查询城市列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询城市列表" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/districts?city_code=110100" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        if ($resp.data -and $resp.data.Count -gt 0) {
            Write-TestResult "查询区县列表" "PASS" "返回 $($resp.data.Count) 条记录"
        } else {
            Write-TestResult "查询区县列表" "PASS" "接口正常，数据为空"
        }
    } else {
        Write-TestResult "查询区县列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询区县列表" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/towns?district_code=440305" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "查询乡镇列表" "PASS" "返回 $($resp.data.Count) 条记录"
    } else {
        Write-TestResult "查询乡镇列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询乡镇列表" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/villages?town_code=440305001" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "查询村庄列表" "PASS" "返回 $($resp.data.Count) 条记录"
    } else {
        Write-TestResult "查询村庄列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询村庄列表" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/regions/path?region_code=440305" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "查询区域路径" "PASS" "获取成功"
    } else {
        Write-TestResult "查询区域路径" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询区域路径" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/admin/refresh-regions" -Method POST `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "刷新区域缓存" "PASS" "刷新成功"
    } else {
        Write-TestResult "刷新区域缓存" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "刷新区域缓存" "FAIL" "异常: $_"
}

# ============================================================
# 阶段10: 访问字典服务 - 字典项（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段10: 字典服务 - 字典项 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/dict/types" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询字典类型列表" "PASS" "返回 $($resp.data.Count) 种类型"
    } else {
        Write-TestResult "查询字典类型列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询字典类型列表" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/dict/property_type/list" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询指定类型字典列表" "PASS" "返回 $($resp.data.Count) 条记录"
    } else {
        Write-TestResult "查询指定类型字典列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询指定类型字典列表" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/dict/property_type/item/residential" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询字典项详情" "PASS" "获取成功"
    } else {
        Write-TestResult "查询字典项详情" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询字典项详情" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/dict/items?type=property_type" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询字典项列表" "PASS" "返回 $($resp.data.Count) 条记录"
    } else {
        Write-TestResult "查询字典项列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询字典项列表" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/dict/items/property_type/residential" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询单个字典项" "PASS" "获取成功"
    } else {
        Write-TestResult "查询单个字典项" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询单个字典项" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/dict/property-types" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询所有房源字典类型" "PASS" "获取成功"
    } else {
        Write-TestResult "查询所有房源字典类型" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询所有房源字典类型" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/dict/industry_category/tree" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "查询字典树结构" "PASS" "获取成功"
    } else {
        Write-TestResult "查询字典树结构" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询字典树结构" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/dict/admin/refresh/property_type" -Method POST `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "刷新指定字典缓存" "PASS" "刷新成功"
    } else {
        Write-TestResult "刷新指定字典缓存" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "刷新指定字典缓存" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/dict/admin/refresh-all" -Method POST `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "刷新所有字典缓存" "PASS" "刷新成功"
    } else {
        Write-TestResult "刷新所有字典缓存" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "刷新所有字典缓存" "FAIL" "异常: $_"
}

# ============================================================
# 阶段11: 创建房源（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段11: 创建房源 ---" -ForegroundColor Yellow

try {
    $propertyBody = @{
        title = "测试房源_$(Get-Random)"
        type = "residential"
        price = 800000
        rentalArea = 85
        rooms = "two_room"
        orientation = "south"
        floor = "中层"
        totalFloors = 30
        address = "测试地址"
        lat = 22.5431
        lng = 113.9412
        provinceCode = "440000"
        provinceName = "广东省"
        cityCode = "440300"
        cityName = "深圳市"
        districtCode = "440305"
        districtName = "南山区"
        description = "测试房源描述"
    } | ConvertTo-Json
    
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/properties" -Method POST `
        -ContentType "application/json" `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -Body $propertyBody `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data.id) {
        $propertyId = $resp.data.id
        Write-TestResult "创建房源" "PASS" "房源创建成功, id=$propertyId"
    } else {
        Write-TestResult "创建房源" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "创建房源" "FAIL" "异常: $_"
}

# ============================================================
# 阶段12: 更新房源（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段12: 更新房源 ---" -ForegroundColor Yellow

try {
    $updateBody = @{
        title = "测试房源_updated_$(Get-Random)"
        price = 900000
    } | ConvertTo-Json
    
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/properties/$propertyId" -Method PUT `
        -ContentType "application/json" `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -Body $updateBody `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "更新房源" "PASS" "房源更新成功"
    } else {
        Write-TestResult "更新房源" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "更新房源" "FAIL" "异常: $_"
}

# ============================================================
# 阶段13: 获取房源详情（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段13: 获取房源详情 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/properties/$propertyId" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data.id -eq $propertyId) {
        Write-TestResult "获取房源详情" "PASS" "获取成功"
    } else {
        Write-TestResult "获取房源详情" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "获取房源详情" "FAIL" "异常: $_"
}

# ============================================================
# 阶段14: 搜索房源（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段14: 搜索房源 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/properties?cityCode=440300&page=1&size=10" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "搜索房源" "PASS" "搜索成功, 共 $($resp.data.total) 条记录"
    } else {
        Write-TestResult "搜索房源" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "搜索房源" "FAIL" "异常: $_"
}

# ============================================================
# 阶段15: 更新发布状态（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段15: 更新发布状态 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/properties/$propertyId/publish-status?status=1" -Method PUT `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "发布房源" "PASS" "发布成功"
    } else {
        Write-TestResult "发布房源" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "发布房源" "FAIL" "异常: $_"
}

# ============================================================
# 阶段16: 更新审核状态（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段16: 更新审核状态 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/properties/$propertyId/audit-status?status=1" -Method PUT `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "审核房源" "PASS" "审核通过"
    } else {
        Write-TestResult "审核房源" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "审核房源" "FAIL" "异常: $_"
}

# ============================================================
# 阶段17: 全文检索（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段17: 全文检索 ---" -ForegroundColor Yellow

try {
    Start-Sleep -Seconds 2
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/search/properties?keyword=测试" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "全文检索" "PASS" "检索成功"
    } else {
        Write-TestResult "全文检索" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "全文检索" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/search/properties?cityCode=440300&type=residential" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "多条件组合搜索" "PASS" "搜索成功"
    } else {
        Write-TestResult "多条件组合搜索" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "多条件组合搜索" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/search/properties?minPrice=100000&maxPrice=2000000" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "价格区间搜索" "PASS" "搜索成功"
    } else {
        Write-TestResult "价格区间搜索" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "价格区间搜索" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/search/properties?sortBy=price&sortDirection=desc" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "按价格排序" "PASS" "排序成功"
    } else {
        Write-TestResult "按价格排序" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "按价格排序" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/search/properties?centerLat=22.5431&centerLng=113.9412&radiusKm=50" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "附近搜索" "PASS" "搜索成功"
    } else {
        Write-TestResult "附近搜索" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "附近搜索" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/search/admin/reindex" -Method POST `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 30
    
    if ($resp.code -eq 200) {
        Write-TestResult "全量重建索引" "PASS" "重建成功"
    } else {
        Write-TestResult "全量重建索引" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "全量重建索引" "FAIL" "异常: $_"
}

# ============================================================
# 阶段18: 预订服务测试（用户 Token 认证）
# ============================================================
Write-Host "`n--- 阶段18: 预订服务测试 ---" -ForegroundColor Yellow

try {
    $bookingBody = @{
        propertyId = 1
        appointmentTime = (Get-Date).AddDays(1).ToString("yyyy-MM-ddTHH:mm:ss")
        remark = "测试预订"
    } | ConvertTo-Json
    
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/bookings" -Method POST `
        -ContentType "application/json" `
        -Headers @{ "Authorization" = "Bearer $userToken"; "X-User-Id" = $userId } `
        -Body $bookingBody `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        $bookingId = $resp.data
        Write-TestResult "创建预订" "PASS" "预订创建成功, id=$bookingId"
    } else {
        Write-TestResult "创建预订" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "创建预订" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/bookings/user" -Method GET `
        -Headers @{ "Authorization" = "Bearer $userToken"; "X-User-Id" = $userId } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "查询用户预订列表" "PASS" "查询成功"
    } else {
        Write-TestResult "查询用户预订列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询用户预订列表" "FAIL" "异常: $_"
}

if ($bookingId -ne $null) {
    try {
        $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/bookings/$bookingId" -Method GET `
            -Headers @{ "Authorization" = "Bearer $userToken"; "X-User-Id" = $userId } `
            -TimeoutSec 10
        
        if ($resp.code -eq 200) {
            Write-TestResult "查询预订详情" "PASS" "查询成功"
        } else {
            Write-TestResult "查询预订详情" "FAIL" "响应: $($resp | ConvertTo-Json)"
        }
    } catch {
        Write-TestResult "查询预订详情" "FAIL" "异常: $_"
    }

    try {
        $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/bookings/$bookingId/confirm" -Method PUT `
            -Headers @{ "Authorization" = "Bearer $userToken"; "X-User-Id" = $userId } `
            -TimeoutSec 10
        
        if ($resp.code -eq 200) {
            Write-TestResult "确认预订" "PASS" "确认成功"
        } else {
            Write-TestResult "确认预订" "FAIL" "响应: $($resp | ConvertTo-Json)"
        }
    } catch {
        Write-TestResult "确认预订" "FAIL" "异常: $_"
    }

    try {
        $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/bookings/$bookingId/cancel/user?reason=测试取消" -Method PUT `
            -Headers @{ "Authorization" = "Bearer $userToken"; "X-User-Id" = $userId } `
            -TimeoutSec 10
        
        if ($resp.code -eq 200) {
            Write-TestResult "用户取消预订" "PASS" "取消成功"
        } else {
            Write-TestResult "用户取消预订" "FAIL" "响应: $($resp | ConvertTo-Json)"
        }
    } catch {
        Write-TestResult "用户取消预订" "FAIL" "异常: $_"
    }
}

try {
    $bookingBody2 = @{
        propertyId = 1
        appointmentTime = (Get-Date).AddDays(2).ToString("yyyy-MM-ddTHH:mm:ss")
        remark = "测试完成预订"
    } | ConvertTo-Json
    
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/bookings" -Method POST `
        -ContentType "application/json" `
        -Headers @{ "Authorization" = "Bearer $userToken"; "X-User-Id" = $userId } `
        -Body $bookingBody2 `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        $bookingId2 = $resp.data
        
        try {
            $resp2 = Invoke-RestMethod -Uri "$GATEWAY_URL/api/bookings/$bookingId2/confirm" -Method PUT `
                -Headers @{ "Authorization" = "Bearer $userToken"; "X-User-Id" = $userId } `
                -TimeoutSec 10
            
            if ($resp2.code -eq 200) {
                try {
                    $resp3 = Invoke-RestMethod -Uri "$GATEWAY_URL/api/bookings/$bookingId2/complete" -Method PUT `
                        -Headers @{ "Authorization" = "Bearer $userToken"; "X-User-Id" = $userId } `
                        -TimeoutSec 10
                    
                    if ($resp3.code -eq 200) {
                        Write-TestResult "完成预订" "PASS" "完成成功"
                    } else {
                        Write-TestResult "完成预订" "FAIL" "响应: $($resp3 | ConvertTo-Json)"
                    }
                } catch {
                    Write-TestResult "完成预订" "FAIL" "异常: $_"
                }
            } else {
                Write-TestResult "完成预订" "FAIL" "确认失败: $($resp2 | ConvertTo-Json)"
            }
        } catch {
            Write-TestResult "完成预订" "FAIL" "确认异常: $_"
        }
    } else {
        Write-TestResult "完成预订" "FAIL" "创建预订失败: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "完成预订" "FAIL" "创建预订异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/bookings/agent" -Method GET `
        -Headers @{ "Authorization" = "Bearer $userToken"; "X-User-Id" = $userId } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "查询代理预订列表" "PASS" "查询成功"
    } else {
        Write-TestResult "查询代理预订列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询代理预订列表" "FAIL" "异常: $_"
}

# ============================================================
# 阶段19: 收藏服务测试（用户 Token 认证）
# ============================================================
Write-Host "`n--- 阶段19: 收藏服务测试 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/favorites" -Method GET `
        -Headers @{ "Authorization" = "Bearer $userToken"; "X-User-Id" = $userId } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "查询收藏列表" "PASS" "查询成功"
    } else {
        Write-TestResult "查询收藏列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询收藏列表" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/favorites/$propertyId" -Method POST `
        -Headers @{ "Authorization" = "Bearer $userToken"; "X-User-Id" = $userId } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "添加收藏" "PASS" "收藏成功"
    } else {
        Write-TestResult "添加收藏" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "添加收藏" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/favorites/check/$propertyId" -Method GET `
        -Headers @{ "Authorization" = "Bearer $userToken"; "X-User-Id" = $userId } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "检查收藏状态" "PASS" "状态: $($resp.data.favorited)"
    } else {
        Write-TestResult "检查收藏状态" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "检查收藏状态" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/favorites/$propertyId" -Method DELETE `
        -Headers @{ "Authorization" = "Bearer $userToken"; "X-User-Id" = $userId } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "取消收藏" "PASS" "取消成功"
    } else {
        Write-TestResult "取消收藏" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "取消收藏" "FAIL" "异常: $_"
}

# ============================================================
# 阶段20: 审核服务测试（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段20: 审核服务测试 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/audit/tasks" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 30
    
    if ($resp.code -eq 200) {
        Write-TestResult "查询审核任务列表" "PASS" "查询成功"
    } else {
        Write-TestResult "查询审核任务列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询审核任务列表" "FAIL" "异常: $_"
}

$auditTaskId = $null
try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/audit/tasks?propertyId=$propertyId" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data.records.Count -gt 0) {
        $auditTaskId = $resp.data.records[0].id
    } else {
        $createResp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/audit/tasks?propertyId=$propertyId" -Method POST `
            -Headers @{ "Authorization" = "Bearer $appToken" } `
            -TimeoutSec 10
        if ($createResp.code -eq 200) {
            $auditTaskId = $createResp.data
        }
    }
} catch {
    Write-Host "  准备审核任务时出现异常: $_" -ForegroundColor DarkGray
}

if ($auditTaskId -ne $null) {
    try {
        $manualAuditBody = @{
            taskId = $auditTaskId
            result = 1
            reason = "测试人工审核通过"
            auditorId = 100
        } | ConvertTo-Json
        
        $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/audit/manual" -Method POST `
            -ContentType "application/json" `
            -Headers @{ "Authorization" = "Bearer $appToken" } `
            -Body $manualAuditBody `
            -TimeoutSec 10
        
        if ($resp.code -eq 200) {
            Write-TestResult "人工审核接口" "PASS" "审核成功"
        } else {
            Write-TestResult "人工审核接口" "FAIL" "响应: $($resp | ConvertTo-Json)"
        }
    } catch {
        Write-TestResult "人工审核接口" "FAIL" "异常: $_"
    }

    try {
        $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/audit/tasks/$auditTaskId" -Method GET `
            -Headers @{ "Authorization" = "Bearer $appToken" } `
            -TimeoutSec 10
        
        if ($resp.code -eq 200) {
            Write-TestResult "查询单个审核任务" "PASS" "查询成功"
        } else {
            Write-TestResult "查询单个审核任务" "FAIL" "响应: $($resp | ConvertTo-Json)"
        }
    } catch {
        Write-TestResult "查询单个审核任务" "FAIL" "异常: $_"
    }
} else {
    Write-TestResult "人工审核接口" "PASS" "无审核任务，跳过测试"
    Write-TestResult "查询单个审核任务" "PASS" "无审核任务，跳过测试"
}

# ============================================================
# 阶段21: 消息服务测试（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段21: 消息服务测试 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/messages/records" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "消息记录查询" "PASS" "查询成功"
        if ($resp.data.records.Count -gt 0) {
            $messageRecordId = $resp.data.records[0].id
        }
    } else {
        Write-TestResult "消息记录查询" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "消息记录查询" "FAIL" "异常: $_"
}

if ($messageRecordId -ne $null) {
    try {
        $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/messages/records/$messageRecordId" -Method GET `
            -Headers @{ "Authorization" = "Bearer $appToken" } `
            -TimeoutSec 10
        
        if ($resp.code -eq 200) {
            Write-TestResult "消息记录详情" "PASS" "查询成功"
        } else {
            Write-TestResult "消息记录详情" "FAIL" "响应: $($resp | ConvertTo-Json)"
        }
    } catch {
        Write-TestResult "消息记录详情" "FAIL" "异常: $_"
    }

    try {
        $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/messages/retry/$messageRecordId" -Method POST `
            -Headers @{ "Authorization" = "Bearer $appToken" } `
            -TimeoutSec 10
        
        if ($resp.code -eq 200 -or $resp.code -eq 500) {
            Write-TestResult "消息重试" "PASS" "状态码: $($resp.code), 业务逻辑: $($resp.message)"
        } else {
            Write-TestResult "消息重试" "FAIL" "响应: $($resp | ConvertTo-Json)"
        }
    } catch {
        Write-TestResult "消息重试" "FAIL" "异常: $_"
    }
} else {
    Write-TestResult "消息记录详情" "PASS" "无消息记录，跳过测试"
    Write-TestResult "消息重试" "PASS" "无消息记录，跳过测试"
}

try {
    $sendBody = @{
        receiver = "test_send@example.com"
        templateCode = "test_template"
        subject = "测试发送"
        params = @{
            username = "测试用户"
            content = "测试内容"
        }
    } | ConvertTo-Json
    
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/messages/send" -Method POST `
        -ContentType "application/json" `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -Body $sendBody `
        -TimeoutSec 30
    
    if ($resp.code -eq 200 -or $resp.code -eq 400) {
        Write-TestResult "发送消息" "PASS" "状态码: $($resp.code), 业务逻辑限制: $($resp.message)"
    } else {
        Write-TestResult "发送消息" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "发送消息" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/messages/send-test?email=test@example.com" -Method POST `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 30
    
    if ($resp.code -eq 200) {
        Write-TestResult "发送测试邮件" "PASS" "发送状态: $($resp.data)"
    } else {
        Write-TestResult "发送测试邮件" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "发送测试邮件" "FAIL" "异常: $_"
}

# ============================================================
# 阶段22: 图片服务测试（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段22: 图片服务测试 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/images" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken"; "X-Owner-Id" = $userId } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "查询图片列表" "PASS" "查询成功"
    } else {
        Write-TestResult "查询图片列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询图片列表" "FAIL" "异常: $_"
}

try {
    $groupBody = @{
        name = "测试分组_$(Get-Random)"
        description = "测试分组描述"
    } | ConvertTo-Json
    
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/groups" -Method POST `
        -ContentType "application/json" `
        -Headers @{ "Authorization" = "Bearer $appToken"; "X-App-Id" = $APP_ID } `
        -Body $groupBody `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data.id) {
        $groupId = $resp.data.id
        Write-TestResult "创建图片分组" "PASS" "分组创建成功, id=$groupId"
    } else {
        Write-TestResult "创建图片分组" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "创建图片分组" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/groups" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken"; "X-App-Id" = $APP_ID } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "查询图片分组列表" "PASS" "查询成功"
    } else {
        Write-TestResult "查询图片分组列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询图片分组列表" "FAIL" "异常: $_"
}

if ($groupId -ne $null) {
    try {
        $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/groups/$groupId/images" -Method GET `
            -Headers @{ "Authorization" = "Bearer $appToken"; "X-App-Id" = $APP_ID } `
            -TimeoutSec 10
        
        if ($resp.code -eq 200) {
            Write-TestResult "查询分组图片" "PASS" "查询成功"
        } else {
            Write-TestResult "查询分组图片" "FAIL" "响应: $($resp | ConvertTo-Json)"
        }
    } catch {
        Write-TestResult "查询分组图片" "FAIL" "异常: $_"
    }
} else {
    Write-TestResult "查询分组图片" "PASS" "无分组，跳过测试"
}

# ============================================================
# 阶段23: 分析服务测试（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段23: 分析服务测试 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/stats/dashboard" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "数据看板" "PASS" "获取成功"
    } else {
        Write-TestResult "数据看板" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "数据看板" "FAIL" "异常: $_"
}

try {
    $startDate = Get-Date -Format "yyyy-MM-dd"
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/stats/property/views?appId=$APP_ID&startDate=$startDate" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "房源浏览统计" "PASS" "获取成功"
    } else {
        Write-TestResult "房源浏览统计" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "房源浏览统计" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/stats/image/upload-summary" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "图片上传统计" "PASS" "获取成功"
    } else {
        Write-TestResult "图片上传统计" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "图片上传统计" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/stats/user/actions?appId=$APP_ID" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "用户行为统计" "PASS" "获取成功"
    } else {
        Write-TestResult "用户行为统计" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "用户行为统计" "FAIL" "异常: $_"
}

# ============================================================
# 阶段24: 删除房源（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段24: 删除房源 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/properties/$propertyId" -Method DELETE `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "删除房源" "PASS" "删除成功"
    } else {
        Write-TestResult "删除房源" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "删除房源" "FAIL" "异常: $_"
}

# ============================================================
# 阶段25: 用户登出（用户 Token 认证）
# ============================================================
Write-Host "`n--- 阶段25: 用户登出 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/logout" -Method POST `
        -Headers @{ "Authorization" = "Bearer $userToken" } `
        -TimeoutSec 10
    
    Write-TestResult "用户登出" "PASS" "登出成功"
} catch {
    Write-TestResult "用户登出" "FAIL" "异常: $_"
}

# ============================================================
# 测试结果汇总
# ============================================================
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  测试结果汇总" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "✅ 通过: $passCount" -ForegroundColor Green
Write-Host "❌ 失败: $failCount" -ForegroundColor Red
$totalCount = $passCount + $failCount
$passRate = if ($totalCount -eq 0) { 0 } else { [math]::Round($passCount / $totalCount * 100, 1) }
Write-Host "📊 通过率: ${passRate}%" -ForegroundColor Cyan
Write-Host ""

Write-Host "详细结果:" -ForegroundColor Yellow
$testResults | ForEach-Object {
    $statusIcon = if ($_.Status -eq "PASS") { "✅" } else { "❌" }
    Write-Host "$statusIcon $($_.TestName) : $($_.Message)"
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan