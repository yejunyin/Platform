# Probe6: user field set + WORKING org/stock/lot combo (no FEntity_Link) - check auto link
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
$desc = "apitest3-delete-me"

$entry = [ordered]@{
  FMaterialId = @{FNumber="6250104001"}
  FUnitID = @{FNumber="zhi1"}
  FBaseUnitId = @{FNumber="zhi1"}
  FStockUnitId = @{FNumber="zhi1"}
  FAppQty = 1
  FActualQty = 1
  FStockId = @{FNumber="0032"}
  FLot = @{FNumber="20260630"}
  FStockStatusId = @{FNumber="KCZT01_SYS"}
  FFeedReasonId = @{FNumber="BLYY01_SYS"}
  FEntrtyDescription = $desc
  FOwnerTypeId = "BD_OwnerOrg"
  FOwnerId = @{FNumber="100"}
  FKeeperTypeId = "BD_KeeperOrg"
  FKeeperId = @{FNumber="100"}
  FParentMaterialId = @{FNumber="1550337004"}
  FParentOwnerTypeId = "BD_OwnerOrg"
  FParentOwnerId = @{FNumber="100"}
  FMOBillNo = "WORK26071389881A"
  FMOId = 1000902
  FMOEntryId = 1064535
  FMOEntrySeq = 1
  FPPBomEntryId = 4594239
  FPPBomBillNo = "PPBOM00895413"
  FEntryWorkShopId = @{FNumber="1022016"}
  FConsome = "0"
  FReserveType = "1"
  FEntrySrcInterId = 1005511
  FEntrySrcBillType = "PRD_PPBOM"
  FEntrySrcEnteryId = 4594239
  FEntrySrcBillNo = "PPBOM00895413"
  FEntrySrcEntrySeq = 1
}
$model = [ordered]@{
  FBillType = @{FNumber="SCBLD01_SYS"}
  FDate = "2026-09-09"
  FStockOrgId = @{FNumber="100"}
  FStockId0 = @{FNumber="0032"}
  FPrdOrgId = @{FNumber="102"}
  FWorkShopId = @{FNumber="1022016"}
  FOwnerTypeId0 = "BD_OwnerOrg"
  FCurrId = @{FNumber="PRE001"}
  FIsCrossTrade = $false
  FVmiBusiness = $false
  FIsOwnerTInclOrg = $false
  FDescription = $desc
  FEntity = @($entry)
}
$packet = [ordered]@{ NeedUpDateFields=@(); NeedReturnFields=@("FBillNo"); IsDeleteEntry=$true; Model=$model }
$saveResp = Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Save.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@("PRD_FeedMtrl", ($packet|ConvertTo-Json -Depth 12)) }
Write-Output "SAVE: $($saveResp.Substring(0, [Math]::Min(600, $saveResp.Length)))"
$so = $saveResp | ConvertFrom-Json
if ($so.Result.ResponseStatus.IsSuccess) {
  $billNo = $so.Result.Number
  Write-Output "SAVED billNo=$billNo"
  $v = (View "PRD_FeedMtrl" $billNo | ConvertFrom-Json).Result.Result
  $re = $v.Entity[0]
  Write-Output "DocStatus=$($v.DocumentStatus) ActualQty=$($re.ActualQty) AppQty=$($re.AppQty) Stock=$($re.StockId.Number) Lot=$($re.Lot.Number)"
  Write-Output "EntrySrcInterId=$($re.EntrySrcInterId) EntrySrcEnteryId=$($re.EntrySrcEnteryId) EntrySrcEntrySeq=$($re.EntrySrcEntrySeq) PPBomEntryId=$($re.PPBomEntryId) SrcBillType=$($re.SrcBillType)"
  Write-Output ("Link: " + ($re.FEntity_Link | ConvertTo-Json -Compress -Depth 5))
} else {
  Write-Output "SAVE FAILED"
}
