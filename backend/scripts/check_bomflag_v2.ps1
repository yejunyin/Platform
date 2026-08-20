# 详细检查 BOMflag 列
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName 'System.Data'

$cs = 'Server=192.168.1.228;Database=HLEIMS2026082008001;User Id=sa;Password=Hltest@123;Encrypt=False;TrustServerCertificate=True;'
$sqlFile = 'e:\Project\Platform\backend\scripts\sql\check_bomflag_full.sql'

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
            $r = $cmd.ExecuteReader()
            Write-Host "    [batch $idx]"
            $cols = @()
            for ($i = 0; $i -lt $r.FieldCount; $i++) { $cols += $r.GetName($i) }
            Write-Host "      cols: $($cols -join ' | ')"
            while ($r.Read()) {
                $vals = @()
                for ($i = 0; $i -lt $r.FieldCount; $i++) { $vals += "[$($r.GetValue($i))]" }
                Write-Host "      $($vals -join ' | ')"
            }
            $r.Close()
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
    $r = $cmd.ExecuteReader()
    Write-Host "    [batch $idx]"
    while ($r.Read()) {
        $vals = @()
        for ($i = 0; $i -lt $r.FieldCount; $i++) { $vals += "[$($r.GetValue($i))]" }
        Write-Host "      $($vals -join ' | ')"
    }
    $r.Close()
}
Write-Host "[*] Executed $idx batches."

$conn.Close()
Write-Host "[*] Done."
