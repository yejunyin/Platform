$dir = $PSScriptRoot
$base = 'http://192.168.1.16/k3cloud'
$jar = Join-Path $dir 'kd_cookies.txt'
$user = [string]([char]0x53F6 + [char]0x519B + [char]0x8425)
$loginFile = Join-Path $dir 'kd_login.json'
$qFile = Join-Path $dir 'kd_query.json'

# fresh login
@{ password = 'hl123456.'; acctID = '6a4f210a7a84ea'; lcid = 2052; username = $user } |
    ConvertTo-Json -Compress | ForEach-Object { [System.IO.File]::WriteAllText($loginFile, $_, [System.Text.UTF8Encoding]::new($false)) }
curl.exe -s -c $jar -X POST "$base/Kingdee.BOS.WebApi.ServicesStub.AuthService.ValidateUser.common.kdsvc" -H "Content-Type: application/json" -d "@$loginFile" | Out-Null

function View-Bill($formId, $billNo) {
    @{
        format = 1; useragent = 'ApiClient'; rid = '356831840'; timestamp = '2022-01-04 13:30:213'; v = '1.0'
        parameters = @($formId, @{ Number = $billNo })
    } | ConvertTo-Json -Depth 5 -Compress | ForEach-Object { [System.IO.File]::WriteAllText($qFile, $_, [System.Text.UTF8Encoding]::new($false)) }
    curl.exe -s -b $jar -X POST "$base/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.View.common.kdsvc" -H "Content-Type: application/json" -d "@$qFile"
}

"=== FEED BILL SCBL00003855 (PRD_FeedMtrl) ==="
$feed = View-Bill 'PRD_FeedMtrl' 'SCBL00003855'
[System.IO.File]::WriteAllText((Join-Path $dir 'feed_view.json'), $feed, [System.Text.UTF8Encoding]::new($false))

"=== RETURN BILL SCTL00005546 (PRD_ReturnMtrl) ==="
$ret = View-Bill 'PRD_ReturnMtrl' 'SCTL00005546'
[System.IO.File]::WriteAllText((Join-Path $dir 'ret_view.json'), $ret, [System.Text.UTF8Encoding]::new($false))
"saved"
