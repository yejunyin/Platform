# Kingdee probe: FEntryWorkShopId test + return order full chain
$base = "http://192.168.1.16/k3cloud"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$kdUser = [string]([char]0x53F6 + [char]0x519B + [char]0x8425)
$descFeed = [string]([char]0x751F + [char]0x4EA7 + [char]0x8865 + [char]0x6599)
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

# ---------- FEED: entry FEntryWorkShopId ----------
Write-Output "`n=== FEED save with FEntryWorkShopId=1022016 ==="
$entry = [ordered]@{
  FMaterialId = @{FNumber="6250104001"}; FUnitID = @{FNumber="zhi1"}; FAPPQty = 1; FQty = 1
  FStockId = @{FNumber="0032"}; FLot = @{FNumber="20260630"}; FStockStatusId = @{FNumber="KCZT01_SYS"}
  FOwnerTypeId = "BD_OwnerOrg"; FOwnerId = @{FNumber="100"}; FKeeperTypeId = "BD_KeeperOrg"; FKeeperId = @{FNumber="100"}
  FMOBillNo = "WORK26071389881A"; FMOId = 1000902; FMOEntryId = 1064535; FMOEntrySeq = 1
  FSrcBillType = "PRD_PPBOM"; FSrcBillNo = "PPBOM00895413"; FPPBomEntryId = 4594239; FPPBomBillNo = "PPBOM00895413"
  FEntrtyDescription = $descFeed; FEntrySrcBillNo = "PPBOM00895413"
  FParentOwnerId = @{FNumber="100"}; FParentMaterialId = @{FNumber="1550337004"}
  FEntryWorkShopId = @{FNumber="1022016"}
  FEntity_Link = @([ordered]@{ "FEntity_Link_FRuleId"="PRD_PPBOM2FEEDMTRL"; "FEntity_Link_FSBillId"="1005511"; "FEntity_Link_FSId"="4594239"; "FEntity_Link_FSTableName"="T_PRD_PPBOMENTRY" })
}
$packet = [ordered]@{ NeedUpDateFields=@(); NeedReturnFields=@("FBillNo"); IsDeleteEntry=$true
  Model=[ordered]@{ FBillType=@{FNumber="SCBLD01_SYS"}; FDate="2026-09-09"; FStockOrgId=@{FNumber="100"}; FPrdOrgId=@{FNumber="102"}
    FOwnerTypeId="BD_OwnerOrg"; FOwnerId=@{FNumber="100"}; FDescription="linktest-feed-delete-me"; FEntity=@($entry) } }
$saveResp = Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Save.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@("PRD_FeedMtrl", ($packet|ConvertTo-Json -Depth 12)) }
Write-Output "FEED SAVE: $($saveResp.Substring(0, [Math]::Min(600, $saveResp.Length)))"
$so = $saveResp | ConvertFrom-Json
if (-not $so.Result.ResponseStatus.IsSuccess) { Write-Output "FEED SAVE FAILED"; exit 0 }
$feedNo = $so.Result.Number; $feedId = $so.Result.Id
Write-Output "FEED billNo=$feedNo id=$feedId"
$sub = Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Submit.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@("PRD_FeedMtrl", @{Numbers=@($feedNo)}) }
Write-Output "FEED SUBMIT: $($sub.Substring(0, [Math]::Min(300, $sub.Length)))"
$aud = Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Audit.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@("PRD_FeedMtrl", @{Numbers=@($feedNo)}) }
Write-Output "FEED AUDIT: $($aud.Substring(0, [Math]::Min(500, $aud.Length)))"
$v = (View "PRD_FeedMtrl" $feedNo | ConvertFrom-Json).Result.Result
$re = $v.Entity[0]
Write-Output "FEED View: EntryWS=$($re.WorkShopId.Number)(id=$($re.WorkShopId_Id)) PPBomEntryId=$($re.PPBomEntryId) SrcBillType=$($re.SrcBillType) DocStatus=$($v.DocumentStatus)"
Write-Output ("FEED Link: " + ($re.FEntity_Link | ConvertTo-Json -Compress -Depth 5))

# ---------- RETURN: full chain with link ----------
Write-Output "`n=== RETURN save + submit + audit ==="
$rentry = [ordered]@{
  FMaterialId = @{FNumber="6250104001"}; FUnitID = @{FNumber="zhi1"}; FAPPQty = 1; FQty = 1
  FReturnType = 1
  FStockId = @{FNumber="0032"}; FLot = @{FNumber="20260630"}; FStockStatusId = @{FNumber="KCZT01_SYS"}
  FOwnerTypeId = "BD_OwnerOrg"; FOwnerId = @{FNumber="100"}; FKeeperTypeId = "BD_KeeperOrg"; FKeeperId = @{FNumber="100"}
  FMOBillNo = "WORK26071389881A"; FMOId = 1000902; FMOEntryId = 1064535; FMOEntrySeq = 1
  FSrcBillType = "PRD_PPBOM"; FSrcBillNo = "PPBOM00895413"; FPPBomEntryId = 4594239; FPPBomBillNo = "PPBOM00895413"
  FParentOwnerId = @{FNumber="100"}; FParentMaterialId = @{FNumber="1550337004"}; FWorkShopId1 = @{FNumber="1022016"}
  FEntity_Link = @([ordered]@{ "FEntity_Link_FRuleId"="PRD_PPBOM2RETURNMTRL"; "FEntity_Link_FSBillId"="1005511"; "FEntity_Link_FSId"="4594239"; "FEntity_Link_FSTableName"="T_PRD_PPBOMENTRY" })
}
$rpacket = [ordered]@{ NeedUpDateFields=@(); NeedReturnFields=@("FBillNo"); IsDeleteEntry=$true
  Model=[ordered]@{ FBillType=@{FNumber="SCTLD01_SYS"}; FDate="2026-09-09"; FStockOrgId=@{FNumber="100"}; FPrdOrgId=@{FNumber="102"}
    FOwnerTypeId="BD_OwnerOrg"; FOwnerId=@{FNumber="100"}; FDescription="linktest-ret-delete-me"; FEntity=@($rentry) } }
$rsaveResp = Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Save.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@("PRD_ReturnMtrl", ($rpacket|ConvertTo-Json -Depth 12)) }
Write-Output "RET SAVE: $($rsaveResp.Substring(0, [Math]::Min(600, $rsaveResp.Length)))"
$rso = $rsaveResp | ConvertFrom-Json
if ($rso.Result.ResponseStatus.IsSuccess) {
  $retNo = $rso.Result.Number; $retId = $rso.Result.Id
  Write-Output "RET billNo=$retNo id=$retId"
  $rsub = Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Submit.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@("PRD_ReturnMtrl", @{Numbers=@($retNo)}) }
  Write-Output "RET SUBMIT: $($rsub.Substring(0, [Math]::Min(300, $rsub.Length)))"
  $raud = Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Audit.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@("PRD_ReturnMtrl", @{Numbers=@($retNo)}) }
  Write-Output "RET AUDIT: $($raud.Substring(0, [Math]::Min(500, $raud.Length)))"
  $rv = (View "PRD_ReturnMtrl" $retNo | ConvertFrom-Json).Result.Result
  $rre = $rv.Entity[0]
  Write-Output "RET View: WS1=$($rre.WorkShopId1.Number) PPBOMEntryId=$($rre.PPBOMEntryId) SrcBillType=$($rre.SrcBillType) DocStatus=$($rv.DocumentStatus)"
  Write-Output ("RET Link: " + ($rre.FEntity_Link | ConvertTo-Json -Compress -Depth 5))
  Write-Output "BILLIDS: FEED=$feedId RET=$retId"
} else {
  Write-Output "RET SAVE FAILED; BILLIDS: FEED=$feedId RET=0"
}
