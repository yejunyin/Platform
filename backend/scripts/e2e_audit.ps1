$dir = $PSScriptRoot
$auditBody = @{
    id = '2097575412505874433'
    auditStaffId = 'Q001'; auditStaffName = 'QC-TEST'
    auditResult = 1
    returnTypeId = 1
    forceFlag = 0
} | ConvertTo-Json -Depth 5 -Compress
$f = Join-Path $dir 'audit_body.json'
[System.IO.File]::WriteAllText($f, $auditBody, [System.Text.UTF8Encoding]::new($false))
"=== AUDIT ==="
$resp = curl.exe -s -X POST "http://localhost:8081/DBMaterialCall/audit?username=Q001" -H "Content-Type: application/json" -d "@$f" --max-time 300
$resp
