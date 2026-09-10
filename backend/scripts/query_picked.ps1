$dir = $PSScriptRoot
$base = 'http://192.168.1.16/k3cloud'
$jar = Join-Path $dir 'kd_cookies.txt'
$user = [string]([char]0x53F6 + [char]0x519B + [char]0x8425)
$loginFile = Join-Path $dir 'kd_login.json'
$qFile = Join-Path $dir 'kd_query.json'

@{ password = 'hl123456.'; acctID = '6a4f210a7a84ea'; lcid = 2052; username = $user } |
    ConvertTo-Json -Compress | ForEach-Object { [System.IO.File]::WriteAllText($loginFile, $_, [System.Text.UTF8Encoding]::new($false)) }
curl.exe -s -c $jar -X POST "$base/Kingdee.BOS.WebApi.ServicesStub.AuthService.ValidateUser.common.kdsvc" -H "Content-Type: application/json" -d "@$loginFile" | Out-Null
"--- login done ---"

@{
    format = 1; useragent = 'ApiClient'; rid = '356831840'; timestamp = '2022-01-04 13:30:213'; v = '1.0'
    parameters = @(@{
        FormId = 'PRD_PPBOM'
        FieldKeys = 'FMOBillNO,FMaterialID2.fnumber,FMaterialModel1,FNeedQty2,FPickedQty'
        FilterString = 'FPickedQty>0'
        OrderString = 'FMOBillNO desc'
        TopRowCount = 0; StartRow = 0; Limit = 15
    })
} | ConvertTo-Json -Depth 5 -Compress | Set-Content -Path $qFile -Encoding ASCII
"--- query result ---"
curl.exe -s -b $jar -X POST "$base/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.ExecuteBillQuery.common.kdsvc" -H "Content-Type: application/json" -d "@$qFile"
