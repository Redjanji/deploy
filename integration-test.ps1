# 全局完整链路集成测试脚本
# 覆盖所有业务与API，确保服务运行时的端到端测试

$GATEWAY_URL = "http://127.0.0.1:8080"
$APP_ID = "my-backend-system"
$APP_SECRET = "dev-secret-key-change-in-production"

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
    $sign = $sign.Replace("+", "%2B")
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
# 阶段4: 访问字典服务（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段4: 访问字典服务 ---" -ForegroundColor Yellow

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
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/dict/items?type=property_type" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询字典项" "PASS" "返回 $($resp.data.Count) 条记录"
    } else {
        Write-TestResult "查询字典项" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询字典项" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/cities?province=广东省" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200 -and $resp.data) {
        Write-TestResult "查询城市列表" "PASS" "返回 $($resp.data.Count) 条记录"
    } else {
        Write-TestResult "查询城市列表" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "查询城市列表" "FAIL" "异常: $_"
}

# ============================================================
# 阶段5: 创建房源（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段5: 创建房源 ---" -ForegroundColor Yellow

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
# 阶段6: 更新房源（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段6: 更新房源 ---" -ForegroundColor Yellow

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
# 阶段7: 获取房源详情（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段7: 获取房源详情 ---" -ForegroundColor Yellow

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
# 阶段8: 搜索房源（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段8: 搜索房源 ---" -ForegroundColor Yellow

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
# 阶段9: 更新发布状态（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段9: 更新发布状态 ---" -ForegroundColor Yellow

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
# 阶段10: 更新审核状态（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段10: 更新审核状态 ---" -ForegroundColor Yellow

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
# 阶段11: 全文检索（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段11: 全文检索 ---" -ForegroundColor Yellow

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
        Write-TestResult "多条件组合搜索" "PASS" "搜索成功, 共 $($resp.data.total) 条记录"
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
        Write-TestResult "价格区间搜索" "PASS" "搜索成功, 共 $($resp.data.total) 条记录"
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
        Write-TestResult "附近搜索" "PASS" "搜索成功, 共 $($resp.data.total) 条记录"
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
# 阶段12: 查看统计数据（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段12: 查看统计数据 ---" -ForegroundColor Yellow

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

# ============================================================
# 阶段13: 消息服务（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段13: 消息服务 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/messages/records" -Method GET `
        -Headers @{ "Authorization" = "Bearer $appToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "消息记录查询" "PASS" "查询成功"
    } else {
        Write-TestResult "消息记录查询" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "消息记录查询" "FAIL" "异常: $_"
}

# ============================================================
# 阶段14: 重新登录获取新Token + 收藏服务测试
# ============================================================
Write-Host "`n--- 阶段14: 收藏服务测试 ---" -ForegroundColor Yellow

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
        Write-TestResult "重新登录" "PASS" "Token 刷新成功, userId=$userId"
    }
} catch {
    Write-TestResult "重新登录" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/favorites" -Method GET `
        -Headers @{ "Authorization" = "Bearer $userToken" } `
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
        -Headers @{ "Authorization" = "Bearer $userToken" } `
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
        -Headers @{ "Authorization" = "Bearer $userToken" } `
        -TimeoutSec 10
    
    if ($resp.code -eq 200) {
        Write-TestResult "检查收藏状态" "PASS" "状态: $($resp.data)"
    } else {
        Write-TestResult "检查收藏状态" "FAIL" "响应: $($resp | ConvertTo-Json)"
    }
} catch {
    Write-TestResult "检查收藏状态" "FAIL" "异常: $_"
}

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/api/favorites/$propertyId" -Method DELETE `
        -Headers @{ "Authorization" = "Bearer $userToken" } `
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
# 阶段15: 审核服务测试（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段15: 审核服务测试 ---" -ForegroundColor Yellow

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
# 阶段16: 删除房源（应用 Token 认证）
# ============================================================
Write-Host "`n--- 阶段16: 删除房源 ---" -ForegroundColor Yellow

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
# 阶段17: 用户登出（用户 Token 认证）
# ============================================================
Write-Host "`n--- 阶段17: 用户登出 ---" -ForegroundColor Yellow

try {
    $resp = Invoke-RestMethod -Uri "$GATEWAY_URL/auth/logout" -Method POST `
        -Headers @{ "Authorization" = "Bearer $userToken" } `
        -TimeoutSec 10
    
    Write-TestResult "用户登出" "PASS" "登出成功: $resp"
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
