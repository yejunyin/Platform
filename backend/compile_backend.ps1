# 编译后端验证（不启动）
$ErrorActionPreference = 'Continue'
$BackendRoot   = 'E:\Project\Platform\backend'
$MavenDir      = Join-Path $BackendRoot '.tools\apache-maven-3.9.9'
$settingsFile  = Join-Path $MavenDir 'conf\settings.xml'
$LombokVersion = '1.18.46'

# 设置 JAVA_HOME
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $candidates = @(
        'C:\Users\Lenovo\.jdks\temurin-24',
        'C:\Program Files\Eclipse Adoptium\jdk-17',
        'C:\Program Files\Java\jdk-17'
    ) | Where-Object { Test-Path "$_\bin\java.exe" }
    if ($candidates) { $env:JAVA_HOME = $candidates[0] }
}
$env:PATH = "$env:JAVA_HOME\bin;$MavenDir\bin;$env:PATH"

Write-Host "[*] JAVA_HOME=$env:JAVA_HOME"
Write-Host "[*] Maven=$MavenDir"

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
Write-Host ""
Write-Host "[*] mvn exit=$mvnExit"
if ($mvnExit -eq 0) {
    Write-Host "[OK] 编译成功" -ForegroundColor Green
} else {
    Write-Host "[X] 编译失败" -ForegroundColor Red
}
exit $mvnExit
