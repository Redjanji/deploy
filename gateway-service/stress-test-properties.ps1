<#
.SYNOPSIS
    对 /api/properties 接口进行并发压测，实时输出 Sentinel 限流统计
.DESCRIPTION
    1. 自动获取 JWT Token（通过 /token 接口 HMAC 签名认证）
    2. 并发请求 /api/properties 接口
    3. 实时输出请求统计（成功/限流/熔断/错误）
    4. 压测结束后从 Sentinel Dashboard 拉取资源指标
.PARAMETER GatewayUrl
    网关地址，默认 http://localhost:8080
.PARAMETER AppId
    应用 ID，默认 my-backend-system
.PARAMETER AppSecret
    应用密钥，默认 secret1
.PARAMETER TotalRequests
    总请求数，默认 100
.PARAMETER Concurrency
    并发数，默认 20
.PARAMETER QuerySentinel
    是否从 Sentinel Dashboard 拉取指标，默认 $true
.PARAMETER ForceError
    是否注入 5xx 错误（发送 X-Chaos-Mode: true 头），用于测试熔断规则
.PARAMETER DelayMs
    请求间延迟（毫秒），默认 0。设置后可降低 QPS 避免触发限流，专注测试熔断
.EXAMPLE
    .\stress-test-properties.ps1 -TotalRequests 200 -Concurrency 30
.EXAMPLE
    .\stress-test-properties.ps1 -ForceError -TotalRequests 50 -Concurrency 5 -DelayMs 50
#>
param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$AppId = "my-backend-system",
    [string]$AppSecret = "secret1",
    [int]$TotalRequests = 100,
    [int]$Concurrency = 20,
    [bool]$QuerySentinel = $true,
    [switch]$ForceError,
    [int]$DelayMs = 0
)

Add-Type -AssemblyName System.Net.Http

$ErrorActionPreference = "Continue"

# =====================================================
# 工具函数
# =====================================================

function Get-UnixTimestamp {
    return [int][double]::Parse((Get-Date -UFormat %s -Date (Get-Date).ToUniversalTime()))
}

function Get-HmacSha256 {
    param([string]$Data, [string]$Key)
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($Key)
    $hashBytes = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Data))
    return [Convert]::ToBase64String($hashBytes)
}

function Get-Token {
    param([string]$BaseUrl, [string]$Id, [string]$Secret)

    $timestamp = Get-UnixTimestamp
    $nonce = [Guid]::NewGuid().ToString("N").Substring(0, 16)
    $payload = "$Id`:$timestamp`:$nonce"
    $sign = Get-HmacSha256 -Data $payload -Key $Secret

    $encodedSign = [System.Uri]::EscapeDataString($sign)
    $tokenUrl = "$BaseUrl/token?appId=$Id&timestamp=$timestamp&nonce=$nonce&sign=$encodedSign"

    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [System.TimeSpan]::FromSeconds(10)
    try {
        $content = New-Object System.Net.Http.StringContent("", [System.Text.Encoding]::UTF8, "application/x-www-form-urlencoded")
        $response = $client.PostAsync($tokenUrl, $content).Result
        $body = $response.Content.ReadAsStringAsync().Result
        $json = $body | ConvertFrom-Json
        if ($json.code -eq 200 -and $json.data.token) {
            return $json.data.token
        } else {
            Write-Host "[ERROR] 获取 Token 失败: code=$($json.code), message=$($json.message)" -ForegroundColor Red
            return $null
        }
    } catch {
        Write-Host "[ERROR] 获取 Token 异常: $($_.Exception.Message)" -ForegroundColor Red
        return $null
    } finally {
        $client.Dispose()
    }
}

function Invoke-SentinelApi {
    param([string]$Path, [string]$Method = "GET", [string]$Body = $null)

    $dashboardUrl = "http://127.0.0.1:8718"
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [System.TimeSpan]::FromSeconds(5)

    # 登录获取 Cookie
    $loginContent = New-Object System.Net.Http.StringContent("username=sentinel&password=sentinel", [System.Text.Encoding]::UTF8, "application/x-www-form-urlencoded")
    $loginResp = $client.PostAsync("$dashboardUrl/login", $loginContent).Result
    $cookies = $loginResp.Headers.GetValues("Set-Cookie")
    $cookieStr = ($cookies | ForEach-Object { ($_ -split ';')[0] }) -join '; '
    $client.DefaultRequestHeaders.Add("Cookie", $cookieStr)

    $url = "$dashboardUrl$Path"
    if ($Method -eq "GET") {
        $resp = $client.GetAsync($url).Result
    } else {
        $content = New-Object System.Net.Http.StringContent($Body, [System.Text.Encoding]::UTF8, "application/json")
        $resp = $client.PostAsync($url, $content).Result
    }

    $respBody = $resp.Content.ReadAsStringAsync().Result
    $client.Dispose()
    return $respBody
}

# =====================================================
# 主流程
# =====================================================

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  /api/properties 并发压测 + Sentinel 统计" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "网关地址:       $GatewayUrl" -ForegroundColor White
Write-Host "目标接口:       GET /api/properties" -ForegroundColor White
Write-Host "总请求数:       $TotalRequests" -ForegroundColor White
Write-Host "并发数:         $Concurrency" -ForegroundColor White
Write-Host "Sentinel 阈值:  30 QPS (限流) / 50% 错误率 (熔断)" -ForegroundColor White
if ($ForceError) {
    Write-Host "故障注入:       开启 (X-Chaos-Mode: true → 500)" -ForegroundColor Red
} else {
    Write-Host "故障注入:       关闭" -ForegroundColor Gray
}
if ($DelayMs -gt 0) {
    Write-Host "请求间隔:       ${DelayMs}ms" -ForegroundColor White
}
Write-Host "开始时间:       $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan

# --- 第 1 步：获取 Token ---
Write-Host ""
Write-Host "[1/4] 获取 JWT Token..." -ForegroundColor Yellow
$token = Get-Token -BaseUrl $GatewayUrl -Id $AppId -Secret $AppSecret
if ($token) {
    Write-Host "  Token 获取成功: $($token.Substring(0, 20))..." -ForegroundColor Green
} else {
    Write-Host "  Token 获取失败，将不带 Token 压测（请求会返回 401，但限流仍生效）" -ForegroundColor Yellow
    $token = $null
}

# --- 第 2 步：压测前 Sentinel 快照 ---
Write-Host ""
Write-Host "[2/4] 获取压测前 Sentinel 指标快照..." -ForegroundColor Yellow
$sentinelAvailable = $false
if ($QuerySentinel) {
    try {
        $beforeMetrics = Invoke-SentinelApi -Path "/metric/top.json?app=gateway-service&resource=GET:/api/properties&searchKey=GET:/api/properties&page=1&limit=1"
        $sentinelAvailable = $true
        Write-Host "  Sentinel Dashboard 连接正常" -ForegroundColor Green
    } catch {
        Write-Host "  Sentinel Dashboard 不可用: $($_.Exception.Message)" -ForegroundColor Yellow
    }
} else {
    Write-Host "  跳过 Sentinel Dashboard 查询" -ForegroundColor Gray
}

# --- 第 3 步：并发压测 ---
Write-Host ""
Write-Host "[3/4] 开始并发压测..." -ForegroundColor Yellow
Write-Host ""

$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

$statusCounts = @{}
$latencies = @()
$lock = New-Object System.Object

$runspacePool = [System.Management.Automation.Runspaces.RunspaceFactory]::CreateRunspacePool(1, $Concurrency)
$runspacePool.Open()

$scriptBlock = {
    param($Url, $Token, $ForceError, $DelayMs)
    Add-Type -AssemblyName System.Net.Http

    if ($DelayMs -gt 0) {
        Start-Sleep -Milliseconds $DelayMs
    }

    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [System.TimeSpan]::FromSeconds(10)

    if ($Token) {
        $client.DefaultRequestHeaders.Add("Authorization", "Bearer $Token")
    }
    if ($ForceError) {
        $client.DefaultRequestHeaders.Add("X-Chaos-Mode", "true")
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = $client.GetAsync($Url).Result
        $status = [int]$response.StatusCode
        $sw.Stop()
        $client.Dispose()
        return @{ Status = $status; Latency = $sw.ElapsedMilliseconds; Success = $true }
    } catch {
        $sw.Stop()
        $client.Dispose()
        return @{ Status = -1; Latency = $sw.ElapsedMilliseconds; Success = $false }
    }
}

$jobs = @()
$apiUrl = "$GatewayUrl/api/properties?page=1&size=10"

for ($i = 0; $i -lt $TotalRequests; $i++) {
    $ps = [System.Management.Automation.PowerShell]::Create()
    $ps.RunspacePool = $runspacePool
    $ps.AddScript($scriptBlock) | Out-Null
    $ps.AddParameter("Url", $apiUrl) | Out-Null
    $ps.AddParameter("Token", $token) | Out-Null
    $ps.AddParameter("ForceError", [bool]$ForceError) | Out-Null
    $ps.AddParameter("DelayMs", $DelayMs) | Out-Null

    $asyncResult = $ps.BeginInvoke()
    $jobs += @{ PS = $ps; AsyncResult = $asyncResult; Index = $i }
}

$completed = 0
$successCount = 0
$blockedCount = 0
$degradedCount = 0
$errorCount = 0
$serverErrorCount = 0

foreach ($job in $jobs) {
    $results = $job.PS.EndInvoke($job.AsyncResult)
    $job.PS.Dispose()

    foreach ($result in $results) {
        $status = $result.Status
        $latency = $result.Latency

        if ($statusCounts.ContainsKey($status)) {
            $statusCounts[$status]++
        } else {
            $statusCounts[$status] = 1
        }
        $latencies += $latency

        if ($status -eq 429) {
            $blockedCount++
        } elseif ($status -eq 503) {
            $degradedCount++
        } elseif ($status -ge 500) {
            $serverErrorCount++
            $errorCount++
        } elseif ($status -ge 200) {
            $successCount++
        } else {
            $errorCount++
        }
    }

    $completed++
    if ($completed % 10 -eq 0 -or $completed -eq $TotalRequests) {
        $elapsed = $stopwatch.Elapsed.TotalSeconds
        $currentQps = if ($elapsed -gt 0) { [Math]::Round($completed / $elapsed, 1) } else { 0 }
        Write-Host ("`r  进度: {0,3}/{1} | 已用: {2:F2}s | QPS: {3} | 成功: {4} | 限流: {5} | 熔断: {6} | 5xx: {7}" -f `
            $completed, $TotalRequests, $elapsed, $currentQps, $successCount, $blockedCount, $degradedCount, $serverErrorCount) -NoNewline -ForegroundColor Gray
    }
}

$stopwatch.Stop()
$runspacePool.Close()
$runspacePool.Dispose()

Write-Host ""
Write-Host ""

# --- 压测结果 ---
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  压测结果" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$totalTime = $stopwatch.Elapsed.TotalSeconds
$actualQps = if ($totalTime -gt 0) { [Math]::Round($TotalRequests / $totalTime, 2) } else { 0 }

Write-Host "总耗时:         $($totalTime.ToString('F2')) 秒" -ForegroundColor White
Write-Host "总请求数:       $TotalRequests" -ForegroundColor White
Write-Host "实际 QPS:       $actualQps" -ForegroundColor White
Write-Host ""

$successColor = if ($successCount -gt 0) { "Green" } else { "Gray" }
$blockedColor = if ($blockedCount -gt 0) { "Yellow" } else { "Gray" }
$degradedColor = if ($degradedCount -gt 0) { "Magenta" } else { "Gray" }
$errorColor = if ($serverErrorCount -gt 0) { "Red" } else { "Gray" }

Write-Host "成功请求 (2xx):     $successCount" -ForegroundColor $successColor
Write-Host "被限流 (429):       $blockedCount" -ForegroundColor $blockedColor
Write-Host "被熔断 (503):       $degradedCount" -ForegroundColor $degradedColor
Write-Host "服务器错误 (5xx):   $serverErrorCount" -ForegroundColor $errorColor
Write-Host ""

Write-Host "状态码分布:" -ForegroundColor White
foreach ($code in $statusCounts.Keys | Sort-Object) {
    $count = $statusCounts[$code]
    $pct = [Math]::Round($count / $TotalRequests * 100, 1)
    $color = if ($code -eq 429) { "Yellow" } elseif ($code -eq 503) { "Magenta" } elseif ($code -ge 200 -and $code -lt 300) { "Green" } elseif ($code -ge 400 -and $code -lt 500) { "Cyan" } else { "Red" }
    $bar = "#" * [Math]::Min(50, [Math]::Floor($pct / 2))
    Write-Host ("  HTTP {0,-3} : {1,4} 次 ({2,5}%) {3}" -f $code, $count, $pct, $bar) -ForegroundColor $color
}

if ($latencies.Count -gt 0) {
    $avgLatency = [Math]::Round(($latencies | Measure-Object -Average).Average, 1)
    $maxLatency = ($latencies | Measure-Object -Maximum).Maximum
    $minLatency = ($latencies | Measure-Object -Minimum).Minimum
    Write-Host ""
    Write-Host "延迟统计:" -ForegroundColor White
    Write-Host "  最小: ${minLatency}ms | 平均: ${avgLatency}ms | 最大: ${maxLatency}ms" -ForegroundColor Gray
}

# --- 第 4 步：Sentinel Dashboard 指标 ---
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Sentinel Dashboard 指标" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if ($sentinelAvailable) {
    Write-Host "[4/4] 从 Sentinel Dashboard 拉取指标..." -ForegroundColor Yellow
    Start-Sleep -Seconds 2

    try {
        $topMetrics = Invoke-SentinelApi -Path "/metric/top.json?app=gateway-service&resource=GET:/api/properties&searchKey=GET:/api/properties&page=1&limit=5"
        $metricsJson = $topMetrics | ConvertFrom-Json

        if ($metricsJson.data -and $metricsJson.data.Count -gt 0) {
            Write-Host ""
            Write-Host "资源 GET:/api/properties 实时指标:" -ForegroundColor White
            Write-Host "  资源名:    $($metricsJson.data[0].resource)" -ForegroundColor Gray
            Write-Host "  通过 QPS:  $($metricsJson.data[0].passQps)" -ForegroundColor Green
            Write-Host "  拒绝 QPS:  $($metricsJson.data[0].blockQps)" -ForegroundColor Yellow
            Write-Host "  成功 QPS:  $($metricsJson.data[0].successQps)" -ForegroundColor Green
            Write-Host "  异常 QPS:  $($metricsJson.data[0].exceptionQps)" -ForegroundColor Red
            Write-Host "  响应时间:  $([Math]::Round($metricsJson.data[0].rt, 2)) ms" -ForegroundColor Gray
            Write-Host "  并发线程:  $($metricsJson.data[0].threadNum)" -ForegroundColor Gray
        } else {
            Write-Host "  Dashboard 暂无该资源的指标数据" -ForegroundColor Yellow
        }

        # 拉取所有资源指标
        Write-Host ""
        Write-Host "所有资源 Top 指标:" -ForegroundColor White
        $allMetrics = Invoke-SentinelApi -Path "/metric/top.json?app=gateway-service&searchKey=&page=1&limit=10"
        $allJson = $allMetrics | ConvertFrom-Json
        if ($allJson.data) {
            Write-Host "  {'资源名':<30} {'通过QPS':<10} {'拒绝QPS':<10} {'成功QPS':<10} {'异常QPS':<10} {'RT(ms)':<10}" -ForegroundColor DarkGray
            foreach ($item in $allJson.data) {
                $resName = if ($item.resource.Length -gt 28) { $item.resource.Substring(0, 28) + ".." } else { $item.resource }
                Write-Host ("  {0,-30} {1,-10} {2,-10} {3,-10} {4,-10} {5,-10}" -f `
                    $resName, $item.passQps, $item.blockQps, $item.successQps, $item.exceptionQps, [Math]::Round($item.rt, 2)) -ForegroundColor White
            }
        }
    } catch {
        Write-Host "  拉取 Sentinel 指标失败: $($_.Exception.Message)" -ForegroundColor Red
    }
} else {
    Write-Host "  Sentinel Dashboard 不可用，跳过指标拉取" -ForegroundColor Yellow
}

# --- 总结 ---
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  验证结论" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 限流验证
if ($blockedCount -gt 0) {
    $blockRate = [Math]::Round($blockedCount / $TotalRequests * 100, 1)
    Write-Host "  限流验证: 通过 - $blockedCount 个请求被限流 (限流率: $blockRate%)" -ForegroundColor Green
} else {
    Write-Host "  限流验证: 未触发 - 没有请求被限流" -ForegroundColor Yellow
}

# 熔断验证
if ($degradedCount -gt 0) {
    $degradeRate = [Math]::Round($degradedCount / $TotalRequests * 100, 1)
    Write-Host "  熔断验证: 通过 - $degradedCount 个请求被熔断返回 503 (熔断率: $degradeRate%)" -ForegroundColor Magenta
} elseif ($serverErrorCount -gt 0) {
    $errorRate = [Math]::Round($serverErrorCount / $TotalRequests * 100, 1)
    Write-Host "  熔断验证: 5xx 错误 $serverErrorCount 个 (错误率: $errorRate%)，熔断可能即将触发或已恢复" -ForegroundColor Yellow
} else {
    Write-Host "  熔断验证: 未触发 - 无 5xx 错误和 503 熔断响应" -ForegroundColor Gray
}

# QPS 分析
if ($actualQps -gt 30) {
    Write-Host "  QPS 分析: 实际 QPS ($actualQps) > 限流阈值 (30)" -ForegroundColor Green
} else {
    Write-Host "  QPS 分析: 实际 QPS ($actualQps) <= 限流阈值 (30)" -ForegroundColor Gray
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
