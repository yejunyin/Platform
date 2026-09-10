$ErrorActionPreference = 'Stop'
$base = 'http://192.168.1.16/k3cloud'
$user = [string]([char]0x53F6 + [char]0x519B + [char]0x8425)  # Ye Junying
$loginBody = @{ password = 'hl123456.'; acctID = '6a4f210a7a84ea'; lcid = 2052; username = $user } | ConvertTo-Json
$loginResp = Invoke-WebRequest -Uri "$base/Kingdee.BOS.WebApi.ServicesStub.AuthService.ValidateUser.common.kdsvc" -Method Post -Body ([System.Text.Encoding]::UTF8.GetBytes($loginBody)) -ContentType 'application/json' -UseBasicParsing
"LOGIN BODY (first 600): " + $loginResp.Content.Substring(0, [Math]::Min(600, $loginResp.Content.Length))
$cookies = $loginResp.Headers['Set-Cookie']
"SET-COOKIE: " + ($cookies -join ' || ')

$kd = $null; $asp = $null
foreach ($c in $cookies) {
    if ($c -match 'kdservice-sessionid=([^;]+)') { $kd = $Matches[1] }
    if ($c -match 'ASP\.NET_SessionId=([^;]+)') { $asp = $Matches[1] }
}
if (-not $kd -or -not $asp) { try { $j = $loginResp.Content | ConvertFrom-Json; if ($j.KDSVCSessionId) { $kd = $j.KDSVCSessionId }; if ($j.Context.SessionId) { $asp = $j.Context.SessionId } } catch {} }
"kd=$kd asp=$asp"
if (-not $kd -or -not $asp) { throw 'session ids missing' }
$cookieHeader = "kdservice-sessionid=$kd; ASP.NET_SessionId=$asp"

$qBody = @{
    format = '1'; useragent = 'ApiClient'; rid = '356831840'; timestamp = '2022-01-04 13:30:213'; v = '1.0'
    parameters = @(@{
        FormId = 'STK_Inventory'
        FieldKeys = 'FMaterialId.FNumber,FStockId.FNumber,FLot.FNumber,FSTOCKLOCID,FBaseQty'
        FilterString = "FMaterialId.FNumber='6570007001' and FBaseQty>0"
        OrderString = ''
        TopRowCount = 0; StartRow = 0; Limit = 50
    })
} | ConvertTo-Json -Depth 5
$resp = Invoke-WebRequest -Uri "$base/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.ExecuteBillQuery.common.kdsvc" -Method Post -Body ([System.Text.Encoding]::UTF8.GetBytes($qBody)) -ContentType 'application/json' -Headers @{ Cookie = $cookieHeader } -UseBasicParsing
"QUERY RESPONSE: " + $resp.Content.Substring(0, [Math]::Min(800, $resp.Content.Length))
