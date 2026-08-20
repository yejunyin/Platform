# 读取 UTF-8 编码的 .sql 文件，按行累积，遇独占 GO 触发执行
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName 'System.Data'

$cs = 'Server=192.168.1.228;Database=HLEIMS2026082008001;User Id=sa;Password=Hltest@123;Encrypt=False;TrustServerCertificate=True;'
$sqlFile = 'e:\Project\Platform\backend\scripts\sql\material_detail_only.sql'

# 用 UTF8 逐行读取
$lines = Get-Content -Path $sqlFile -Encoding UTF8

$conn = New-Object System.Data.SqlClient.SqlConnection($cs)
$conn.Open()
Write-Host "[*] Connected to $($conn.Database)"

$buf = New-Object System.Text.StringBuilder
$idx = 0
$goPattern = '^\s*GO\s*$'

foreach ($line in $lines) {
    if ($line -match $goPattern) {
        $b = $buf.ToString().Trim()
        if (-not [string]::IsNullOrWhiteSpace($b)) {
            $idx++
            $cmd = $conn.CreateCommand()
            $cmd.CommandText = $b
            $cmd.CommandTimeout = 60
            $rows = $cmd.ExecuteNonQuery()
            $preview = $b -replace '\s+', ' '
            if ($preview.Length -gt 80) { $preview = $preview.Substring(0, 80) + '...' }
            Write-Host "    [batch $idx] rows=$rows | $preview"
        }
        $buf.Clear() | Out-Null
    } else {
        [void]$buf.AppendLine($line)
    }
}
# 处理末尾残余
$b = $buf.ToString().Trim()
if (-not [string]::IsNullOrWhiteSpace($b)) {
    $idx++
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = $b
    $cmd.CommandTimeout = 60
    $rows = $cmd.ExecuteNonQuery()
    $preview = $b -replace '\s+', ' '
    if ($preview.Length -gt 80) { $preview = $preview.Substring(0, 80) + '...' }
    Write-Host "    [batch $idx] rows=$rows | $preview"
}
Write-Host "[*] Executed $idx batches."

# 验证
$cmd = $conn.CreateCommand()
$cmd.CommandText = "SELECT COUNT(*) AS cnt, COUNT(DISTINCT task_id) AS tasks FROM dbo.mes_dwd_material_detail;"
$r = $cmd.ExecuteReader()
if ($r.Read()) { Write-Host "[VERIFY] rows=$($r['cnt']) distinct_tasks=$($r['tasks'])" }
$r.Close()

$cmd2 = $conn.CreateCommand()
$cmd2.CommandText = "SELECT status, COUNT(*) AS c FROM dbo.mes_dwd_material_detail GROUP BY status ORDER BY status;"
$r2 = $cmd2.ExecuteReader()
Write-Host "[STATUS DIST]"
while ($r2.Read()) { Write-Host "    $($r2['status']) = $($r2['c'])" }
$r2.Close()

# 中文验证（前 3 条）
$cmd3 = $conn.CreateCommand()
$cmd3.CommandText = "SELECT TOP 3 id, task_id, material_name, specification, status FROM dbo.mes_dwd_material_detail ORDER BY id;"
$r3 = $cmd3.ExecuteReader()
Write-Host "[SAMPLE 3 ROWS]"
while ($r3.Read()) { Write-Host "    id=$($r3['id']) task=$($r3['task_id']) name=$($r3['material_name']) spec=$($r3['specification']) status=$($r3['status'])" }
$r3.Close()

$conn.Close()
Write-Host "[*] Done."
