param(
    [string]$BaseUrl = "http://42.193.174.175",
    [string]$AppSecret = "XssBackend2026SecHmacKey8xYz"
)

Write-Host "========================================"
Write-Host "  XSS 微服务 API 全量测试"
Write-Host "  Base URL: $BaseUrl"
Write-Host "========================================"
Write-Host ""

$headers = @{}
$token = $null

# ============================================================
# 阶段1: 获取应用 Token
# ============================================================
Write-Host "--- 阶段1: 获取应用 Token ---"
try {
    $timestamp = [math]::Floor([datetime]::UtcNow.Subtract([datetime]::new(1970, 1, 1)).TotalSeconds)
    $nonce = ([guid]::NewGuid()).ToString("N")
    $payload = "my-backend-system:$timestamp`:$nonce"
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($AppSecret)
    $signature = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($payload))
    $sign = [Convert]::ToBase64String($signature)
    $body = "appId=my-backend-system&timestamp=$timestamp&nonce=$nonce&sign=$sign"
    
    $resp = Invoke-RestMethod -Uri "$BaseUrl/token" -Method POST -ContentType "application/x-www-form-urlencoded" -Body $body -TimeoutSec 30
    Write-Host "✅ 成功: $($resp | ConvertTo-Json -Compress)"
    $token = $resp.data.token
} catch {
    Write-Host "❌ 失败: $($_.Exception.Message)"
}
Write-Host ""

if (-not $token) {
    Write-Host "Token 获取失败，退出测试"
    exit 1
}

$headers["Authorization"] = "Bearer $token"

# ============================================================
# 阶段2: 用户注册
# ============================================================
Write-Host "--- 阶段2: 用户注册 ---"
try {
    $testUsername = "testuser_$(Get-Date -Format 'yyyyMMddHHmmss')"
    $body = @{
        username = $testUsername
        password = "Test12345678"
        email = "$testUsername@example.com"
        phone = "13800138$([random]::new().Next(1000,9999))"
    } | ConvertTo-Json
    
    $resp = Invoke-RestMethod -Uri "$BaseUrl/auth/register" -Method POST -ContentType "application/json" -Body $body -TimeoutSec 30
    Write-Host "✅ 成功: $($resp | ConvertTo-Json -Compress)"
} catch {
    Write-Host "❌ 失败: $($_.Exception.Message)"
}
Write-Host ""

# ============================================================
# 阶段3: 用户登录
# ============================================================
Write-Host "--- 阶段3: 用户登录 ---"
try {
    $body = @{
        username = "testuser"
        password = "Test12345678"
    } | ConvertTo-Json
    
    $resp = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method POST -ContentType "application/json" -Body $body -TimeoutSec 30
    Write-Host "✅ 成功: $($resp | ConvertTo-Json -Compress)"
    $userToken = $resp.data.token
    if ($userToken) {
        $headers["Authorization"] = "Bearer $userToken"
        Write-Host "已切换为 User Token"
    }
} catch {
    Write-Host "❌ 失败: $($_.Exception.Message)"
}
Write-Host ""

# ============================================================
# 阶段4: Dict 字典服务
# ============================================================
Write-Host "--- 阶段4: Dict 字典服务 ---"

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/dict/types" -Headers $headers -TimeoutSec 30
    Write-Host "✅ GET /api/dict/types: 成功"
} catch {
    Write-Host "❌ GET /api/dict/types: $($_.Exception.Message)"
}

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/dict/property_type/list" -Headers $headers -TimeoutSec 30
    Write-Host "✅ GET /api/dict/property_type/list: 成功"
} catch {
    Write-Host "❌ GET /api/dict/property_type/list: $($_.Exception.Message)"
}

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/dict/property-types" -Headers $headers -TimeoutSec 30
    Write-Host "✅ GET /api/dict/property-types: 成功"
} catch {
    Write-Host "❌ GET /api/dict/property-types: $($_.Exception.Message)"
}

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/dict/items?type=property_type" -Headers $headers -TimeoutSec 30
    Write-Host "✅ GET /api/dict/items: 成功"
} catch {
    Write-Host "❌ GET /api/dict/items: $($_.Exception.Message)"
}
Write-Host ""

# ============================================================
# 阶段5: Property 房源服务
# ============================================================
Write-Host "--- 阶段5: Property 房源服务 ---"

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/properties?page=1&size=5" -Headers $headers -TimeoutSec 30
    Write-Host "✅ GET /api/properties: 成功"
} catch {
    Write-Host "❌ GET /api/properties: $($_.Exception.Message)"
}

try {
    $body = @{
        title = "测试房源"
        description = "测试描述"
        price = 3000
        area = 50
        cityCode = "440300"
        address = "测试地址"
        bedrooms = 1
        bathrooms = 1
        propertyType = "residential"
        latitude = 22.5431
        longitude = 113.9445
    } | ConvertTo-Json
    
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/properties" -Method POST -ContentType "application/json" -Body $body -Headers $headers -TimeoutSec 30
    Write-Host "✅ POST /api/properties: 成功"
    $propertyId = $resp.data.id
} catch {
    Write-Host "❌ POST /api/properties: $($_.Exception.Message)"
    $propertyId = $null
}

if ($propertyId) {
    try {
        $resp = Invoke-RestMethod -Uri "$BaseUrl/api/properties/$propertyId" -Headers $headers -TimeoutSec 30
        Write-Host "✅ GET /api/properties/${propertyId}: 成功"
    } catch {
        Write-Host "❌ GET /api/properties/${propertyId}: $($_.Exception.Message)"
    }
}
Write-Host ""

# ============================================================
# 阶段6: Image 图片服务
# ============================================================
Write-Host "--- 阶段6: Image 图片服务 ---"

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/images?page=1&size=5" -Headers $headers -TimeoutSec 30
    Write-Host "✅ GET /api/images: 成功"
} catch {
    Write-Host "❌ GET /api/images: $($_.Exception.Message)"
}
Write-Host ""

# ============================================================
# 阶段7: Search 搜索服务
# ============================================================
Write-Host "--- 阶段7: Search 搜索服务 ---"

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/search/properties?page=1&size=5" -Headers $headers -TimeoutSec 30
    Write-Host "✅ GET /api/search/properties: 成功"
} catch {
    Write-Host "❌ GET /api/search/properties: $($_.Exception.Message)"
}
Write-Host ""

# ============================================================
# 阶段8: Analytics 统计服务
# ============================================================
Write-Host "--- 阶段8: Analytics 统计服务 ---"

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/stats/dashboard" -Headers $headers -TimeoutSec 30
    Write-Host "✅ GET /api/stats/dashboard: 成功"
} catch {
    Write-Host "❌ GET /api/stats/dashboard: $($_.Exception.Message)"
}
Write-Host ""

# ============================================================
# 阶段9: Message 消息服务
# ============================================================
Write-Host "--- 阶段9: Message 消息服务 ---"

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/messages/records?page=1&size=5" -Headers $headers -TimeoutSec 30
    Write-Host "✅ GET /api/messages/records: 成功"
} catch {
    Write-Host "❌ GET /api/messages/records: $($_.Exception.Message)"
}
Write-Host ""

# ============================================================
# 阶段10: Favorite 收藏服务
# ============================================================
Write-Host "--- 阶段10: Favorite 收藏服务 ---"

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/favorites" -Headers $headers -TimeoutSec 30
    Write-Host "✅ GET /api/favorites: 成功"
} catch {
    Write-Host "❌ GET /api/favorites: $($_.Exception.Message)"
}

if ($propertyId) {
    try {
        $resp = Invoke-RestMethod -Uri "$BaseUrl/api/favorites/$propertyId" -Method POST -Headers $headers -TimeoutSec 30
        Write-Host "✅ POST /api/favorites/${propertyId}: 成功"
    } catch {
        Write-Host "❌ POST /api/favorites/${propertyId}: $($_.Exception.Message)"
    }

    try {
        $resp = Invoke-RestMethod -Uri "$BaseUrl/api/favorites/check/$propertyId" -Headers $headers -TimeoutSec 30
        Write-Host "✅ GET /api/favorites/check/${propertyId}: 成功"
    } catch {
        Write-Host "❌ GET /api/favorites/check/${propertyId}: $($_.Exception.Message)"
    }
}
Write-Host ""

# ============================================================
# 阶段11: Review 审核服务
# ============================================================
Write-Host "--- 阶段11: Review 审核服务 ---"

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/audit/tasks?page=1&size=5" -Headers $headers -TimeoutSec 30
    Write-Host "✅ GET /api/audit/tasks: 成功"
} catch {
    Write-Host "❌ GET /api/audit/tasks: $($_.Exception.Message)"
}
Write-Host ""

# ============================================================
# 阶段12: Booking 预约服务
# ============================================================
Write-Host "--- 阶段12: Booking 预约服务 ---"

try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/bookings/user?page=1&size=5" -Headers $headers -TimeoutSec 30
    Write-Host "✅ GET /api/bookings/user: 成功"
} catch {
    Write-Host "❌ GET /api/bookings/user: $($_.Exception.Message)"
}

if ($propertyId) {
    try {
        $body = @{
            propertyId = $propertyId
            appointmentTime = "2026-08-01T14:00:00"
            remark = "测试预约"
        } | ConvertTo-Json
        
        $resp = Invoke-RestMethod -Uri "$BaseUrl/api/bookings" -Method POST -ContentType "application/json" -Body $body -Headers $headers -TimeoutSec 30
        Write-Host "✅ POST /api/bookings: 成功"
    } catch {
        Write-Host "❌ POST /api/bookings: $($_.Exception.Message)"
    }
}
Write-Host ""

Write-Host "========================================"
Write-Host "  测试完成"
Write-Host "========================================"