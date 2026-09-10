# Kingdee probe5: PPBOM View entry JSON structure
$base = "http://192.168.1.16/k3cloud"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$kdUser = [string]([char]0x53F6 + [char]0x519B + [char]0x8425)
$loginJson = [System.Text.Encoding]::UTF8.GetBytes((@{acctID="6a4f210a7a84ea";username=$kdUser;password="hl123456.";lcid=2052}|ConvertTo-Json))
Invoke-WebRequest -Uri "$base/Kingdee.BOS.WebApi.ServicesStub.AuthService.ValidateUser.common.kdsvc" -Method Post -ContentType "application/json; charset=utf-8" -Body $loginJson -SessionVariable kd -UseBasicParsing | Out-Null
Write-Output "Login OK"
function Post($path, $obj) {
  $json = [System.Text.Encoding]::UTF8.GetBytes(($obj|ConvertTo-Json -Depth 12))
  (Invoke-WebRequest -Uri ($base+$path) -Method Post -ContentType "application/json; charset=utf-8" -Body $json -WebSession $kd -UseBasicParsing).Content
}
$v = Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.View.common.kdsvc" @{
  format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 21:30:00"; v="1.0";
  parameters=@("PRD_PPBOM", @{Number="PPBOM00896056"})
}
$vj = $v | ConvertFrom-Json
$r = $vj.Result.Result
$e = $r.PPBomEntry[0]
Write-Output "=== Entry[0] full JSON ==="
$e | ConvertTo-Json -Depth 6
Write-Output "`n=== Header keys ==="
($r | Get-Member -MemberType NoteProperty | Select-Object -ExpandProperty Name) -join ", "
