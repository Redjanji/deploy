# Gateway Sentinel 限流压测脚本
param(
    [string]$Url = "http://localhost:8080/token?appId=test&timestamp=0&nonce=test&sign=test",
    [string]$Method = "POST",
    [int]$TotalRequests = 100,
    [int]$Concurrency = 20
)

Add-Type -AssemblyName System.Net.Http

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Gateway Sentinel 限流压测" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "URL: $Url" -ForegroundColor White
Write-Host "Method: $Method" -ForegroundColor White
Write-Host "总请求数: $TotalRequests" -ForegroundColor White
Write-Host "并发数: $Concurrency" -ForegroundColor White
Write-Host "开始时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

$results = @()
$current = 0
$lock = New-Object System.Object

$scriptBlock = {
    param($Url)
    Add-Type -AssemblyName System.Net.Http
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [System.TimeSpan]::FromSeconds(10)
    try {
        $content = New-Object System.Net.Http.StringContent("", [System.Text.Encoding]::UTF8, "application/x-www-form-urlencoded")
        $response = $client.PostAsync($Url, $content).Result
        $status = [int]$response.StatusCode
        $client.Dispose()
        return $status
    } catch {
        $client.Dispose()
        return -1
    }
}

$runspacePool = [System.Management.Automation.Runspaces.RunspaceFactory]::CreateRunspacePool(1, $Concurrency)
$runspacePool.Open()

$jobs = @()

for ($i = 0; $i -lt $TotalRequests; $i++) {
    $job = [System.Management.Automation.PowerShell]::Create()
    $job.RunspacePool = $runspacePool
    $job.AddScript($scriptBlock) | Out-Null
    $job.AddParameter("Url", $Url) | Out-Null
    $asyncResult = $job.BeginInvoke()
    $jobs += @{ Job = $job; AsyncResult = $asyncResult; Index = $i }
}

$completed = 0
$statusCounts = @{}

foreach ($jobInfo in $jobs) {
    $statuses = $jobInfo.Job.EndInvoke($jobInfo.AsyncResult)
    $jobInfo.Job.Dispose()
    
    foreach ($status in $statuses) {
        if ($statusCounts.ContainsKey($status)) {
            $statusCounts[$status]++
        } else {
            $statusCounts[$status] = 1
        }
    }
    
    $completed++
    if ($completed % 20 -eq 0) {
        Write-Host "进度: $completed / $TotalRequests" -ForegroundColor Gray
    }
}

$stopwatch.Stop()
$runspacePool.Close()
$runspacePool.Dispose()

$successCount = 0
$blockedCount = 0
$errorCount = 0

foreach ($code in $statusCounts.Keys) {
    if ($code -eq 429) {
        $blockedCount += $statusCounts[$code]
    } elseif ($code -ge 200 -and $code -lt 500) {
        $successCount += $statusCounts[$code]
    } else {
        $errorCount += $statusCounts[$code]
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  压测结果" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "总耗时: $($stopwatch.Elapsed.TotalSeconds.ToString('F2')) 秒" -ForegroundColor White
Write-Host "总请求数: $TotalRequests" -ForegroundColor White
Write-Host "实际 QPS: $([Math]::Round($TotalRequests / $stopwatch.Elapsed.TotalSeconds, 2))" -ForegroundColor White
Write-Host ""
Write-Host "成功请求 (2xx/4xx): $successCount" -ForegroundColor Green
Write-Host "被限流 (429): $blockedCount" -ForegroundColor Yellow
Write-Host "错误请求: $errorCount" -ForegroundColor Red
Write-Host ""
Write-Host "状态码分布:" -ForegroundColor White
foreach ($code in $statusCounts.Keys | Sort-Object) {
    $count = $statusCounts[$code]
    $pct = [Math]::Round($count / $TotalRequests * 100, 1)
    $color = if ($code -eq 429) { "Yellow" } elseif ($code -ge 200 -and $code -lt 300) { "Green" } elseif ($code -ge 400 -and $code -lt 500) { "Cyan" } else { "Red" }
    Write-Host "  HTTP $code : $count 次 ($pct%)" -ForegroundColor $color
}
Write-Host ""
Write-Host "限流验证: $(if ($blockedCount -gt 0) { '通过 - Sentinel 限流规则生效!' } else { '未通过 - 没有请求被限流' })" -ForegroundColor $(if ($blockedCount -gt 0) { "Green" } else { "Red" })
Write-Host "========================================" -ForegroundColor Cyan
