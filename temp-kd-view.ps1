# Compare Link structure: GUI-pushed bill vs API-created bill
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
function View($formId, $number) {
  Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.View.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@($formId, @{Number=$number}) }
}

foreach ($no in @("SCBL00003848","SCBL00003852")) {
  Write-Output "`n=== FEED $no ==="
  $v = (View "PRD_FeedMtrl" $no | ConvertFrom-Json).Result.Result
  $re = $v.Entity[0]
  Write-Output "DocStatus=$($v.DocumentStatus) SrcBillType=$($re.SrcBillType) PPBomEntryId=$($re.PPBomEntryId) PPBomBillNo=$($re.PPBomBillNo)"
  Write-Output ("Link: " + ($re.FEntity_Link | ConvertTo-Json -Compress -Depth 5))
}
foreach ($no in @("SCTL00005025","SCTL00005540")) {
  Write-Output "`n=== RETURN $no ==="
  $v = (View "PRD_ReturnMtrl" $no | ConvertFrom-Json).Result.Result
  $re = $v.Entity[0]
  Write-Output "DocStatus=$($v.DocumentStatus) SrcBillType=$($re.SrcBillType) PPBomEntryId=$($re.PPBomEntryId)"
  Write-Output ("Link: " + ($re.FEntity_Link | ConvertTo-Json -Compress -Depth 5))
}
