# 编译后端验证（不启动）
# 用法:
#   .\compile_backend.ps1                 # 打包 eb-service-task 及其依赖模块
#   .\compile_backend.ps1 -AllModules     # 打包全部模块(含 eb-gateway)
param(
    [switch]$AllModules
)

$ErrorActionPreference = 'Continue'
# 脚本所在目录即 backend 根目录（不依赖固定盘符路径）
$BackendRoot   = $PSScriptRoot
$MavenDir      = Join-Path $BackendRoot '.tools\apache-maven-3.9.9'
$settingsFile  = Join-Path $MavenDir 'conf\settings.xml'
$LombokVersion = '1.18.46'

# 设置 JAVA_HOME：需要 JDK 17+（Spring Boot 3），系统默认可能是 JDK 8
$javaHomeOk = $false
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $verLine = & "$env:JAVA_HOME\bin\java.exe" -version 2>&1 | Select-Object -First 1
    if ($verLine -match 'version "(17|18|19|2[0-9])') { $javaHomeOk = $true }
}
if (-not $javaHomeOk) {
    $candidates = @()
    # 常见 JDK 安装根目录（仅扫描直接子目录，避免整盘递归）
    $searchRoots = @(
        (Join-Path $env:USERPROFILE '.jdks'),
        'C:\Program Files\Java',
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Microsoft\jdk*',
        'D:\'
    )
    foreach ($root in $searchRoots) {
        $candidates += Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path "$($_.FullName)\bin\java.exe" } |
            ForEach-Object { $_.FullName }
    }
    $jdk = $candidates | Sort-Object -Unique | ForEach-Object {
        $v = & "$_\bin\java.exe" -version 2>&1 | Select-Object -First 1
        if ($v -match 'version "(\d+)') { [pscustomobject]@{ Path = $_; Major = [int]$Matches[1] } }
    } | Where-Object { $_.Major -ge 17 } | Sort-Object Major -Descending | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.Path }
}

if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    Write-Host "[X] 未找到 JDK 17+，请先安装或手动设置 JAVA_HOME" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path (Join-Path $MavenDir 'bin\mvn.cmd'))) {
    Write-Host "[X] 未找到 Maven: $MavenDir" -ForegroundColor Red
    exit 1
}

$env:PATH = "$env:JAVA_HOME\bin;$MavenDir\bin;$env:PATH"

Write-Host "[*] BackendRoot=$BackendRoot"
Write-Host "[*] JAVA_HOME=$env:JAVA_HOME"
Write-Host "[*] Maven=$MavenDir"

$moduleArgs = if ($AllModules) { @() } else { @('-pl', 'eb-service-task', '-am') }

Push-Location $BackendRoot
try {
    & mvn -s $settingsFile clean install @moduleArgs `
        "-Dmaven.test.skip=true" `
        "-Dlombok.version=$LombokVersion" `
        "-Dmaven.javadoc.skip=true" `
        "-Djacoco.skip=true" 2>&1 | Tee-Object -Variable mvnLog
    $mvnExit = $LASTEXITCODE
} finally {
    Pop-Location
}
Write-Host ""
Write-Host "[*] mvn exit=$mvnExit"
if ($mvnExit -eq 0) {
    Write-Host "[OK] 编译成功" -ForegroundColor Green
    if (-not $AllModules) {
        Write-Host "    产物: $BackendRoot\eb-service-task\target\eb-service-task-1.0.0.jar"
    }
} else {
    Write-Host "[X] 编译失败" -ForegroundColor Red
}
exit $mvnExit
