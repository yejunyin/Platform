# 详细检查 BOMflag 列
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName 'System.Data'

$cs = 'Server=192.168.1.228;Database=HLEIMS2026082008001;User Id=sa;Password=Hltest@123;Encrypt=False;TrustServerCertificate=True;'

$conn = New-Object System.Data.SqlClient.SqlConnection($cs)
$conn.Open()

# 完整列信息
$cmd = $conn.CreateCommand()
$cmd.CommandText = "SELECT ORDINAL_POSITION, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, IS_NULLABLE, COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'mes_dwd_productOrder' AND COLUMN_NAME = 'BOMflag';"
$r = $cmd.ExecuteReader()
Write-Host "[BOMflag full info]"
while ($r.Read()) {
    Write-Host "    pos=$($r['ORDINAL_POSITION']) name=$($r['COLUMN_NAME']) type=$($r['DATA_TYPE']) maxlen=$($r['CHARACTER_MAXIMUM_LENGTH']) precision=$($r['NUMERIC_PRECISION']) nullable=$($r['IS_NULLABLE']) default=$($r['COLUMN_DEFAULT'])"
}
$r.Close()

# 取前 3 行样本
$cmd2 = $conn.CreateCommand()
$cmd2.CommandText = "SELECT TOP 3 id, pcode, materialid, BOMflag FROM mes_dwd_productOrder;"
$r2 = $cmd2.ExecuteReader()
Write-Host "[Sample 3 rows]"
while ($r2.Read()) {
    Write-Host "    id=$($r2['id']) pcode=$($r2['pcode']) materialid=$($r2['materialid']) BOMflag=[$($r2['BOMflag'])]"
}
$r2.Close()

$conn.Close()
