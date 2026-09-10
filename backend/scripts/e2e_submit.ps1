$dir = $PSScriptRoot
# applicant name = YeJunYing, QC = TestQualityInspector
$appName = [string]([char]0x53F6 + [char]0x519B + [char]0x8425)
$dept = [string]([char]0x751F + [char]0x4EA7 + [char]0x90E8)
$reason = [string]([char]0x751F + [char]0x4EA7 + [char]0x8D85 + [char]0x635F + [char]0x8017)
$matName = [string]([char]0x5C0F + [char]0x9F7F + [char]0x8F6E)
$spec = '1045'
$unit = [string]([char]0x53EA)

$submitBody = @{
    applicantStaffId = 'E001'; applicantName = $appName; applicantDept = $dept
    reasonId = 1; reasonText = $reason
    qcStaffId = 'Q001'; qcStaffCode = 'Q001'; qcStaffName = 'QC-TEST'
    orderList = @(@{
        orderCode = 'WORK26071389881A'
        materials = @(@{ materialCode = '6250104001'; materialName = $matName; spec = $spec; unit = $unit; qty = 2 })
    })
} | ConvertTo-Json -Depth 5 -Compress
$f = Join-Path $dir 'submit_body.json'
[System.IO.File]::WriteAllText($f, $submitBody, [System.Text.UTF8Encoding]::new($false))
"=== SUBMIT ==="
$resp = curl.exe -s -X POST "http://localhost:8081/DBMaterialCall/submit?username=E001" -H "Content-Type: application/json; charset=utf-8" -d "@$f" --max-time 120
$resp
