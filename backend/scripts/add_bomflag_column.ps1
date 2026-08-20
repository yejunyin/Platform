# 为 mes_dwd_productOrder 添加 BOMflag 列
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName 'System.Data'

$cs = 'Server=192.168.1.228;Database=HLEIMS2026082008001;User Id=sa;Password=Hltest@123;Encrypt=False;TrustServerCertificate=True;'
$sqlFile = 'e:\Project\Platform\backend\scripts\sql\add_bomflag_column.sql'

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
            try {
                $rows = $cmd.ExecuteNonQuery()
                Write-Host "    [batch $idx] rows=$rows"
            } catch {
                Write-Host "    [batch $idx] ERROR: $($_.Exception.Message)"
            }
        }
        $buf.Clear() | Out-Null
    } else {
        [void]$buf.AppendLine($line)
    }
}
$b = $buf.ToString().Trim()
if (-not [string]::IsNullOrWhiteSpace($b)) {
    $idx++
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = $b
    $cmd.CommandTimeout = 60
    try {
        $rows = $cmd.ExecuteNonQuery()
        Write-Host "    [batch $idx] rows=$rows"
    } catch {
        Write-Host "    [batch $idx] ERROR: $($_.Exception.Message)"
    }
}
Write-Host "[*] Executed $idx batches."

# 验证
$cmd = $conn.CreateCommand()
$cmd.CommandText = "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'mes_dwd_productOrder' AND COLUMN_NAME = 'BOMflag';"
$r = $cmd.ExecuteReader()
Write-Host "[VERIFY BOMflag column]"
while ($r.Read()) {
    Write-Host "    name=$($r['COLUMN_NAME']) type=$($r['DATA_TYPE']) nullable=$($r['IS_NULLABLE']) default=$($r['COLUMN_DEFAULT'])"
}
$r.Close()

# 统计当前 BOMflag 分布
$cmd2 = $conn.CreateCommand()
$cmd2.CommandText = "SELECT BOMflag, COUNT(*) AS c FROM mes_dwd_productOrder GROUP BY BOMflag;"
$r2 = $cmd2.ExecuteReader()
Write-Host "[BOMflag distribution]"
while ($r2.Read()) { Write-Host "    BOMflag=$($r2['BOMflag']) count=$($r2['c'])" }
$r2.Close()

$conn.Close()
Write-Host "[*] Done."
