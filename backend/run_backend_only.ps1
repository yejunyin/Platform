# 仅启动后端 jar（不编译），用于后台运行
$env:JAVA_HOME = 'C:\Users\Lenovo\.jdks\temurin-24'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$jar = 'E:\Project\Platform\backend\eb-service-task\target\eb-service-task-1.0.0.jar'

$argList = @(
    '-jar', $jar,
    '--spring.main.lazy-initialization=true',
    '--spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV2Reactive',
    '--management.health.redis.enabled=false',
    '--spring.cloud.nacos.discovery.enabled=false',
    '--spring.cloud.nacos.config.enabled=false',
    '--spring.cloud.service-registry.auto-registration.enabled=false',
    '--spring.cloud.nacos.discovery.register-enabled=false',
    '--spring.data.redis.host=localhost',
    '--spring.data.redis.port=6379',
    '--spring.data.redis.timeout=500'
)

Write-Host "[*] Starting backend (PID will be shown)"
$proc = Start-Process -FilePath 'java' -ArgumentList $argList -PassThru `
    -RedirectStandardOutput 'E:\Project\Platform\backend\.tools\backend.log' `
    -RedirectStandardError 'E:\Project\Platform\backend\.tools\backend.err.log'

Write-Host "[*] Backend PID=$($proc.Id), waiting for startup..."
$proc.Id | Out-File 'E:\Project\Platform\backend\.tools\backend.pid' -Encoding ASCII

# 等待端口就绪
$ready = $false
for ($i = 1; $i -le 60; $i++) {
    Start-Sleep -Seconds 2
    if ($proc.HasExited) {
        Write-Host "[X] Process exited (code=$($proc.ExitCode))"
        Get-Content 'E:\Project\Platform\backend\.tools\backend.log' -Tail 20
        Get-Content 'E:\Project\Platform\backend\.tools\backend.err.log' -Tail 20
        exit 1
    }
    try {
        $c = New-Object Net.Sockets.TcpClient
        $c.Connect('localhost', 8081)
        $c.Close()
        $ready = $true
        Write-Host "[OK] Port 8081 ready after $($i*2)s"
        break
    } catch {}
}
if (-not $ready) {
    Write-Host "[X] Timeout waiting for port 8081"
    exit 1
}

# 额外等待 Spring 完全启动
Start-Sleep -Seconds 5

# 测试新接口
Write-Host ""
Write-Host "[*] Test 1: GET /api/v1/material-call/tasks (取一个任务)"
try {
    $resp = Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/material-call/tasks' -Method Get -TimeoutSec 30
    Write-Host "    code=$($resp.code) count=$($resp.data.Count)"
    $resp.data | Select-Object -First 3 | ForEach-Object {
        Write-Host "    id=$($_.id) product=$($_.product_name) product_code=$($_.product_code) workshop=$($_.workshop)"
    }
    $global:testTaskId = ($resp.data | Select-Object -First 1).id
    Write-Host "[*] Will test sync with taskId=$global:testTaskId"
} catch {
    Write-Host "[X] tasks API failed: $($_.Exception.Message)"
    exit 1
}

Write-Host ""
Write-Host "[*] Test 2: GET /api/v1/material-call/materials/sync?taskId=$global:testTaskId"
Write-Host "    (此调用会触发金蝶登录 + ExecuteBillQuery，可能耗时数秒)"
try {
    $syncResp = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/material-call/materials/sync?taskId=$global:testTaskId" -Method Get -TimeoutSec 120
    Write-Host "    code=$($syncResp.code) count=$($syncResp.data.Count)"
    $syncResp.data | Select-Object -First 5 | ForEach-Object {
        Write-Host "    material=$($_.material_code) name=$($_.material_name) req=$($_.required_qty) $($_.unit) avail=$($_.available_qty)"
    }
} catch {
    Write-Host "[X] sync API failed: $($_.Exception.Message)"
    Write-Host "    后端日志（最后 50 行）:"
    Get-Content 'E:\Project\Platform\backend\.tools\backend.log' -Tail 50 | ForEach-Object { Write-Host "    $_" }
}

Write-Host ""
Write-Host "[*] Backend still running on PID=$($proc.Id), port 8081"
Write-Host "[*] Frontend: http://localhost:3000 (Vite proxies /api -> 8081)"
Write-Host "[*] Press Q to stop backend..."
try {
    while ($true) {
        $key = [System.Console]::ReadKey($true).Key
        if ($key -eq 'Q') { break }
    }
} catch {
    Write-Host "[*] Headless mode, keeping backend alive for 30 min..."
    Start-Sleep -Seconds 1800
}
Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
Write-Host "[*] Stopped."
