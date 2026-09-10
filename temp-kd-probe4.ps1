# Probe4: FSeq test + user packet save (auto-link check) - fixed path via $PSScriptRoot
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
function BillQuery($formId, $fieldKeys, $filter) {
  Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.ExecuteBillQuery.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@(@{FormId=$formId;FieldKeys=$fieldKeys;FilterString=$filter;OrderString="";TopRowCount=0;StartRow=0;Limit=50}) }
}
function View($formId, $number) {
  Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.View.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@($formId, @{Number=$number}) }
}

Write-Output "`n=== 1. FSeq candidate ==="
try {
  $r = BillQuery "PRD_PPBOM" "FID,FEntity_FEntryID,FEntity_FSeq,FBillNo,FMOBillNO,FMaterialID2.fnumber" "FMOBillNO='WORK26071390517A'"
  Write-Output ($r.Substring(0, [Math]::Min(500, $r.Length)))
} catch { Write-Output "ERROR: $($_.Exception.Message)" }

Write-Output "`n=== 2. Save user packet (no FEntity_Link) ==="
$packetJson = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'temp-kd-feed-packet.json'), [System.Text.Encoding]::UTF8)
$saveResp = Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Save.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@("PRD_FeedMtrl", $packetJson) }
Write-Output ($saveResp.Substring(0, [Math]::Min(500, $saveResp.Length)))
$so = $saveResp | ConvertFrom-Json
if ($so.Result.ResponseStatus.IsSuccess) {
  $billNo = $so.Result.Number
  Write-Output "SAVED billNo=$billNo"
  Write-Output "`n=== 3. View saved bill - check auto Link ==="
  $v = (View "PRD_FeedMtrl" $billNo | ConvertFrom-Json).Result.Result
  $re = $v.Entity[0]
  Write-Output "DocStatus=$($v.DocumentStatus)"
  Write-Output "EntrySrcInterId=$($re.EntrySrcInterId) EntrySrcEnteryId=$($re.EntrySrcEnteryId) EntrySrcEntrySeq=$($re.EntrySrcEntrySeq) PPBomEntryId=$($re.PPBomEntryId)"
  Write-Output ("Link: " + ($re.FEntity_Link | ConvertTo-Json -Compress -Depth 5))

  Write-Output "`n=== 4. Try delete test bill ==="
  try {
    $del = Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Delete.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@("PRD_FeedMtrl", @{Numbers=@($billNo)}) }
    Write-Output ($del.Substring(0, [Math]::Min(300, $del.Length)))
  } catch { Write-Output "DELETE ERROR: $($_.Exception.Message)" }
} else {
  Write-Output "SAVE FAILED"
}
