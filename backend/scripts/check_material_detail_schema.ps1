# 查看 mes_dwd_material_detail 表结构 + 测试金蝶接口的实际返回数据
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName 'System.Data'

$cs = 'Server=192.168.1.228;Database=HLEIMS2026082008001;User Id=sa;Password=Hltest@123;Encrypt=False;TrustServerCertificate=True;'
$conn = New-Object System.Data.SqlClient.SqlConnection($cs)
$conn.Open()
Write-Host "[*] Connected"

# 1. 表结构
$cmd = $conn.CreateCommand()
$cmd.CommandText = "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'mes_dwd_material_detail' ORDER BY ORDINAL_POSITION;"
$r = $cmd.ExecuteReader()
Write-Host ""
Write-Host "[mes_dwd_material_detail schema]"
Write-Host "    COLUMN_NAME | DATA_TYPE | MAX_LEN | NULLABLE"
while ($r.Read()) {
    Write-Host "    $($r['COLUMN_NAME']) | $($r['DATA_TYPE']) | $($r['CHARACTER_MAXIMUM_LENGTH']) | $($r['IS_NULLABLE'])"
}
$r.Close()

# 2. 调用后端 sync 接口，让后端从金蝶拉数据。但 insert 失败。
# 我们绕过 DB，直接调金蝶 API 看返回数据长什么样
Write-Host ""
Write-Host "[*] Calling Kingdee login API directly..."

# 金蝶登录
$loginBody = @{
    password = 'hl123456.'
    acctID = '692fb7433ff3c1'
    lcid = '2052'
    username = '叶军营'
} | ConvertTo-Json

try {
    $loginResp = Invoke-RestMethod -Uri 'http://192.168.1.16/k3cloud/Kingdee.BOS.WebApi.ServicesStub.AuthService.ValidateUser.common.kdsvc' -Method Post -Body $loginBody -ContentType 'application/json' -TimeoutSec 30
    Write-Host "    Login OK"
    Write-Host "    LoginResultType=$($loginResp.LoginResultType)"

    # 提取 KDSVCSessionId 和 Context.SessionId
    $kdsessId = $null
    $ctxSessId = $null
    if ($loginResp.PSObject.Properties.Name -contains 'KDSVCSessionId') {
        $kdsessId = $loginResp.KDSVCSessionId
    }
    if ($loginResp.Context -and $loginResp.Context.PSObject.Properties.Name -contains 'SessionId') {
        $ctxSessId = $loginResp.Context.SessionId
    }
    Write-Host "    KDSVCSessionId(FromBody)=$kdsessId"
    Write-Host "    Context.SessionId(FromBody)=$ctxSessId"

    # 如果 body 里没有，从响应头取
    # Invoke-RestMethod 不直接暴露 headers；改用 Invoke-WebRequest
    $loginResp2 = Invoke-WebRequest -Uri 'http://192.168.1.16/k3cloud/Kingdee.BOS.WebApi.ServicesStub.AuthService.ValidateUser.common.kdsvc' -Method Post -Body $loginBody -ContentType 'application/json' -TimeoutSec 30 -UseBasicParsing
    Write-Host ""
    Write-Host "[*] Login response headers (Set-Cookie):"
    foreach ($c in $loginResp2.Headers['Set-Cookie']) {
        Write-Host "    $c"
    }

    if (-not $kdsessId -or -not $ctxSessId) {
        # 从 cookie 取
        $cookies = $loginResp2.Headers['Set-Cookie']
        foreach ($c in $cookies) {
            if ($c -match 'kdservice-sessionid=([^;]+)') { if (-not $kdsessId) { $kdsessId = $matches[1] } }
            if ($c -match 'ASP\.NET_SessionId=([^;]+)') { if (-not $ctxSessId) { $ctxSessId = $matches[1] } }
        }
        Write-Host "    [from cookie] KDSVCSessionId=$kdsessId"
        Write-Host "    [from cookie] ASP.NET_SessionId=$ctxSessId"
    }

    if (-not $kdsessId -or -not $ctxSessId) {
        Write-Host "[X] Cannot extract session IDs"
        $conn.Close()
        exit 1
    }

    # 3. 调用 ExecuteBillQuery
    $cookieHeader = "kdservice-sessionid=$kdsessId; ASP.NET_SessionId=$ctxSessId"

    $queryBody = @{
        format = '1'
        useragent = 'ApiClient'
        rid = '356831840'
        parameters = @(@{
            FormId = 'PRD_PPBOM'
            FieldKeys = 'FMOBillNO,FMaterialID2.fnumber,FMaterialID2.fname,FMaterialModel1,FNeedQty2,FUnitID2.fname,FInventoryQty,FStockLOCID'
            FilterString = "FMaterialID.fnumber='1550482001' and FMOBillNO='WORK26061372664A'"
        })
        timestamp = '2022-01-04 13:30:213'
        v = '1.0'
    } | ConvertTo-Json -Depth 10 -Compress

    Write-Host ""
    Write-Host "[*] Calling ExecuteBillQuery..."
    $queryResp = Invoke-RestMethod -Uri 'http://192.168.1.16/K3Cloud/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.ExecuteBillQuery.common.kdsvc' -Method Post -Body $queryBody -ContentType 'application/json' -TimeoutSec 60 -Headers @{ Cookie = $cookieHeader }
    Write-Host "    Response type: $($queryResp.GetType().Name)"
    Write-Host "    Raw response (first 2000 chars):"
    $raw = $queryResp | ConvertTo-Json -Depth 20
    Write-Host $raw.Substring(0, [Math]::Min(2000, $raw.Length))

    if ($queryResp -is [array]) {
        Write-Host ""
        Write-Host "[*] Response is array of $($queryResp.Count) rows"
        $queryResp | Select-Object -First 5 | ForEach-Object {
            $row = $_
            Write-Host "    Row (type=$($row.GetType().Name), count=$($row.Count)):"
            for ($i = 0; $i -lt $row.Count; $i++) {
                $v = $row[$i]
                $vlen = if ($v) { $v.ToString().Length } else { 0 }
                Write-Host "      [$i] len=$vlen value=$v"
            }
        }
    } elseif ($queryResp.PSObject.Properties.Name -contains 'value' -and $queryResp.value -is [array]) {
        Write-Host ""
        Write-Host "[*] Response.value is array of $($queryResp.value.Count) rows"
    }
} catch {
    Write-Host "[X] Error: $($_.Exception.Message)"
    Write-Host $_.Exception.StackTrace
}

$conn.Close()
Write-Host "[*] Done"
