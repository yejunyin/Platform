# ===========================================================================
# Local start script for Enterprise Brain backend (eb-service-task)
# Auto setup Maven -> compile -> start -> verify MES product task API
# ===========================================================================

$ErrorActionPreference = 'Continue'
$BackendRoot   = 'E:\Project\Platform\backend'
$ToolsDir      = Join-Path $BackendRoot '.tools'
$MavenVersion  = '3.2.5'
$MavenDir      = Join-Path $ToolsDir "apache-maven-$MavenVersion"
$MavenZip      = Join-Path $ToolsDir "apache-maven-$MavenVersion-bin.zip"
$MavenUrl      = "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip"
$LombokVersion = '1.18.46'   # override parent pom 1.18.32 for JDK24 compat
$Port          = 8081
$ApiUrl        = "http://localhost:$Port/api/v1/material-call/tasks"

function Step($msg) { Write-Host "`n[*] $msg" -ForegroundColor Cyan }
function Ok($msg)   { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Warn2($msg){ Write-Host "    [!]  $msg" -ForegroundColor Yellow }
function Err($msg)  { Write-Host "    [X] $msg" -ForegroundColor Red }

function Test-Port($computer, $port) {
    try { $c = New-Object Net.Sockets.TcpClient; $c.Connect($computer, $port); $c.Close(); return $true } catch { return $false }
}

# Run a native command, capture first line of output (stderr+stdout merged), suppress error records
function Get-CmdFirstLine($exe, $args) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    $out = & $exe @args 2>&1
    $ErrorActionPreference = $prev
    return ($out | Select-Object -First 1)
}

# ---- 1. Check Java ----
Step 'Check Java'
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $candidates = @(
        'D:\Java\jdk-17.0.20.1'
    ) | Where-Object { Test-Path "$_\bin\java.exe" }
    if ($candidates) { $env:JAVA_HOME = $candidates[0] } else {
        Err 'JDK not found. Install JDK17+ and set JAVA_HOME'
        exit 1
    }
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$javaVer = Get-CmdFirstLine 'java' @('-version')
Ok "JAVA_HOME = $env:JAVA_HOME"
Ok "Java: $javaVer"
if ("$javaVer" -match 'version "(\d+)') {
    $major = [int]$Matches[1]
    if ($major -ge 23) { Warn2 "JDK$major is new; Spring Boot 3.2.5 supports 17-22. Using Lombok $LombokVersion. If compile fails, use JDK17-21" }
}

# ---- 2. Download and extract Maven ----
Step "Check Maven $MavenVersion"
if (-not (Test-Path "$MavenDir\bin\mvn.cmd")) {
    if (-not (Test-Path $ToolsDir)) { New-Item -ItemType Directory -Path $ToolsDir -Force | Out-Null }
    if (-not (Test-Path $MavenZip)) {
        Warn2 "Downloading Maven $MavenVersion from archive.apache.org (may be slow)..."
        try {
            Invoke-WebRequest -Uri $MavenUrl -OutFile $MavenZip -UseBasicParsing -TimeoutSec 300
            Ok "Downloaded: $MavenZip"
        } catch {
            Err "Download failed: $_"
            exit 1
        }
    }
    Warn2 'Extracting...'
    Expand-Archive -Path $MavenZip -DestinationPath $ToolsDir -Force
    Ok "Extracted: $MavenDir"
} else {
    Ok "Maven already exists, skip download"
}
$env:MAVEN_HOME = $MavenDir
$env:PATH = "$MavenDir\bin;$env:PATH"

# ---- 3. Configure Maven Aliyun mirror (only this maven instance) ----
Step 'Configure Maven Aliyun mirror'
$settingsFile = Join-Path $MavenDir 'conf\settings.xml'
$settingsXml = @'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <localRepository>E:\Project\Platform\backend\.tools\m2-repo</localRepository>
  <mirrors>
    <mirror>
      <id>aliyun-public</id>
      <mirrorOf>*</mirrorOf>
      <name>Aliyun Public</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
'@
$settingsXml | Out-File -FilePath $settingsFile -Encoding UTF8 -Force
Ok "settings.xml written: $settingsFile"
Ok "Local repo: E:\Project\Platform\backend\.tools\m2-repo"
$mvnVer = Get-CmdFirstLine 'mvn' @('-v')
Ok "Maven: $mvnVer"

# ---- 4. Compile backend (eb-common + eb-service-task) ----
Step 'Compile backend (first run downloads deps, ~2-5 min)...'
Push-Location $BackendRoot
try {
    & mvn -s $settingsFile clean install "-Dmaven.test.skip=true" -pl eb-service-task -am `
        "-Dlombok.version=$LombokVersion" `
        "-Dmaven.javadoc.skip=true" `
        "-Djacoco.skip=true" 2>&1 | Tee-Object -Variable mvnLog
    $mvnExit = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($mvnExit -ne 0) {
    Err "Maven compile failed (exit=$mvnExit)"
    Warn2 'If Lombok/JDK issue, edit $LombokVersion at top of script or use JDK17-21'
    Warn2 'If dependency download issue, check network'
    exit $mvnExit
}
Ok 'Compile success'

# ---- 5. Locate executable jar ----
Step 'Locate executable jar'
$jar = Get-ChildItem "$BackendRoot\eb-service-task\target\*.jar" -ErrorAction SilentlyContinue |
       Where-Object { $_.Name -notlike '*-sources*' -and $_.Name -notlike '*-javadoc*' -and $_.Name -notlike '*.original' } |
       Select-Object -First 1
if (-not $jar) { Err 'No executable jar found'; exit 1 }
Ok "jar: $($jar.FullName)"

# ---- 6. Start backend in background (disable Nacos to run standalone) ----
Step "Start backend (port $Port, Nacos disabled)..."
$logFile = Join-Path $ToolsDir 'backend.log'
$errFile = Join-Path $ToolsDir 'backend.err.log'
if (Test-Path $logFile) { Remove-Item $logFile -Force }
if (Test-Path $errFile) { Remove-Item $errFile -Force }
$argList = @(
    '-jar', $jar.FullName,
    "--spring.main.lazy-initialization=true",
    "--spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV2Reactive",
    "--management.health.redis.enabled=false",
    "--spring.cloud.nacos.discovery.enabled=false",
    "--spring.cloud.nacos.config.enabled=false",
    "--spring.cloud.service-registry.auto-registration.enabled=false",
    "--spring.cloud.nacos.discovery.register-enabled=false",
    "--spring.data.redis.host=localhost",
    "--spring.data.redis.port=6379",
    "--spring.data.redis.timeout=500"
)
$proc = Start-Process -FilePath 'java' -ArgumentList $argList -PassThru `
    -RedirectStandardOutput $logFile -RedirectStandardError $errFile
Ok "Backend PID=$($proc.Id), log: $logFile"

# ---- 7. Wait for port ready ----
Step "Wait for port $Port (up to 120s)..."
$ready = $false
for ($i = 1; $i -le 60; $i++) {
    Start-Sleep -Seconds 2
    if ($proc.HasExited) {
        Err "Backend process exited (exit=$($proc.ExitCode)). Log tail:"
        if (Test-Path $logFile) { Get-Content $logFile -Tail 40 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray } }
        if (Test-Path $errFile) { Get-Content $errFile -Tail 20 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkRed } }
        exit 1
    }
    if (Test-Port 'localhost' $Port) { $ready = $true; Ok "Port $Port ready (${i}x2s)"; break }
    Write-Host "    ... waiting ($($i*2)s)" -ForegroundColor DarkGray
}
if (-not $ready) { Err 'Port wait timeout'; Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue; exit 1 }

# Extra wait for Spring full startup
Start-Sleep -Seconds 6

# ---- 8. Verify MES product task API ----
Step "Verify API: $ApiUrl"
try {
    $resp = Invoke-RestMethod -Uri $ApiUrl -Method Get -TimeoutSec 15
    if ($resp.code -eq 200) {
        $list = $resp.data
        Ok "API success, $($list.Count) production tasks returned"
        Write-Host ''
        Write-Host '    Task ID          Product Name          Qty    Workshop   Line       Planned Start' -ForegroundColor White
        Write-Host '    --------------- -------------------- ------ ---------- ---------- -------------------' -ForegroundColor DarkGray
        $list | Select-Object -First 15 | ForEach-Object {
            $pn = if ($_.product_name) { $_.product_name } else { '' }
            if ($pn.Length -gt 20) { $pn = $pn.Substring(0,20) }
            $line = '    {0,-15} {1,-20} {2,6} {3,-10} {4,-10} {5}' -f `
                $_.id, $pn, $_.quantity, $_.workshop, $_.line, $_.planned_start
            Write-Host $line
        }
        if ($list.Count -gt 15) { Warn2 "... and $($list.Count - 15) more" }
        Write-Host ''
        Ok 'Data fetched from MES mes_dwd_productOrder successfully'
    } else {
        Err "API non-200: code=$($resp.code) message=$($resp.message)"
    }
} catch {
    Err "API call failed: $_"
    Warn2 'Backend log tail:'
    if (Test-Path $logFile) { Get-Content $logFile -Tail 30 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray } }
}

# ---- 9. Info & wait ----
Step 'Backend service running'
Write-Host "    API:      http://localhost:$Port" -ForegroundColor White
Write-Host "    Swagger:  http://localhost:$Port/doc.html" -ForegroundColor White
Write-Host "    Log:      $logFile" -ForegroundColor White
Write-Host "    Frontend: http://localhost:3000 (Vite proxies /api -> 8081)" -ForegroundColor White
Write-Host ''
Write-Host '    Press Q to stop backend and exit...' -ForegroundColor Yellow
try {
    while ($true) {
        $key = [System.Console]::ReadKey($true).Key
        if ($key -eq 'Q') { break }
    }
} catch {
    Write-Host '    [headless mode] keeping backend running for 10 min, then auto-stop...' -ForegroundColor Yellow
    Start-Sleep -Seconds 600
}
Warn2 'Stopping backend...'
Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Id -ne $PID } | Stop-Process -Force -ErrorAction SilentlyContinue
Ok 'Stopped. Bye'
