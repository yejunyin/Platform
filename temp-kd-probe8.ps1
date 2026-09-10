# Probe8: FINAL format validation - feed full chain + return with FEntrySrc*
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
function Chain($formId, $packet, $tag) {
  $saveResp = Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Save.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@($formId, ($packet|ConvertTo-Json -Depth 12)) }
  $so = $saveResp | ConvertFrom-Json
  if (-not $so.Result.ResponseStatus.IsSuccess) {
    Write-Output "$tag SAVE FAIL: $($saveResp.Substring(0, [Math]::Min(500, $saveResp.Length)))"
    return $null
  }
  $no = $so.Result.Number
  Write-Output "$tag SAVED $no"
  $sub = Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Submit.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@($formId, @{Numbers=@($no)}) }
  Write-Output "$tag SUBMIT: $($sub.Substring(0, [Math]::Min(200, $sub.Length)))"
  $aud = Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Audit.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@($formId, @{Numbers=@($no)}) }
  Write-Output "$tag AUDIT: $($aud.Substring(0, [Math]::Min(300, $aud.Length)))"
  $v = (View $formId $no | ConvertFrom-Json).Result.Result
  $re = $v.Entity[0]
  Write-Output "$tag VIEW: DocStatus=$($v.DocumentStatus) SrcInterId=$($re.SrcInterId) SrcEnteryId=$($re.SrcEnteryId) SrcEntrySeq=$($re.SrcEntrySeq) SrcBillType=$($re.SrcBillType) PPBomEntryId=$($re.PPBomEntryId)"
  Write-Output "$tag LINK: $($re.FEntity_Link | ConvertTo-Json -Compress -Depth 5)"
  return $no
}
$desc = "apitest4-delete-me"

# ---------- FEED: user format + FEntity_Link ----------
$fentry = [ordered]@{
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
  FEntity_Link = @([ordered]@{
    "FEntity_Link_FRuleId"="PRD_PPBOM2FEEDMTRL"
    "FEntity_Link_FSBillId"="1005511"
    "FEntity_Link_FSId"="4594239"
    "FEntity_Link_FSTableName"="T_PRD_PPBOMENTRY"
  })
}
$fmodel = [ordered]@{
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
  FEntity = @($fentry)
}
$feedNo = Chain "PRD_FeedMtrl" ([ordered]@{ NeedUpDateFields=@(); NeedReturnFields=@("FBillNo"); IsDeleteEntry=$true; Model=$fmodel }) "FEED"

# ---------- RETURN: original + FEntrySrc* test ----------
$rbase = [ordered]@{
  FMaterialId = @{FNumber="6250104001"}
  FUnitID = @{FNumber="zhi1"}
  FAPPQty = 1
  FQty = 1
  FReturnType = 1
  FStockId = @{FNumber="0032"}
  FLot = @{FNumber="20260630"}
  FStockStatusId = @{FNumber="KCZT01_SYS"}
  FOwnerTypeId = "BD_OwnerOrg"
  FOwnerId = @{FNumber="100"}
  FKeeperTypeId = "BD_KeeperOrg"
  FKeeperId = @{FNumber="100"}
  FMOBillNo = "WORK26071389881A"
  FMOId = 1000902
  FMOEntryId = 1064535
  FMOEntrySeq = 1
  FSrcBillType = "PRD_PPBOM"
  FSrcBillNo = "PPBOM00895413"
  FPPBomEntryId = 4594239
  FPPBomBillNo = "PPBOM00895413"
  FParentOwnerId = @{FNumber="100"}
  FParentMaterialId = @{FNumber="1550337004"}
  FWorkShopId1 = @{FNumber="1022016"}
  FEntity_Link = @([ordered]@{
    "FEntity_Link_FRuleId"="PRD_PPBOM2RETURNMTRL"
    "FEntity_Link_FSBillId"="1005511"
    "FEntity_Link_FSId"="4594239"
    "FEntity_Link_FSTableName"="T_PRD_PPBOMENTRY"
  })
}
$rmodel = [ordered]@{
  FBillType = @{FNumber="SCTLD01_SYS"}
  FDate = "2026-09-09"
  FStockOrgId = @{FNumber="100"}
  FPrdOrgId = @{FNumber="102"}
  FOwnerTypeId = "BD_OwnerOrg"
  FOwnerId = @{FNumber="100"}
  FDescription = $desc
  FEntity = @($rbase)
}
Write-Output "`n--- RETURN V1: with FEntrySrc* ---"
$v1o = [ordered]@{}
foreach ($k in @("FMaterialId","FUnitID","FAPPQty","FQty","FReturnType","FStockId","FLot","FStockStatusId","FOwnerTypeId","FOwnerId","FKeeperTypeId","FKeeperId","FMOBillNo","FMOId","FMOEntryId","FMOEntrySeq","FSrcBillType","FSrcBillNo","FPPBomEntryId","FPPBomBillNo")) { $v1o[$k] = $rbase[$k] }
$v1o["FEntrySrcInterId"] = 1005511
$v1o["FEntrySrcBillType"] = "PRD_PPBOM"
$v1o["FEntrySrcEnteryId"] = 4594239
$v1o["FEntrySrcBillNo"] = "PPBOM00895413"
$v1o["FEntrySrcEntrySeq"] = 1
$v1o["FParentOwnerId"] = $rbase["FParentOwnerId"]
$v1o["FParentMaterialId"] = $rbase["FParentMaterialId"]
$v1o["FWorkShopId1"] = $rbase["FWorkShopId1"]
$v1o["FEntity_Link"] = $rbase["FEntity_Link"]
$rmodel1 = [ordered]@{ FBillType=@{FNumber="SCTLD01_SYS"}; FDate="2026-09-09"; FStockOrgId=@{FNumber="100"}; FPrdOrgId=@{FNumber="102"}; FOwnerTypeId="BD_OwnerOrg"; FOwnerId=@{FNumber="100"}; FDescription=$desc; FEntity=@($v1o) }
$retNo = Chain "PRD_ReturnMtrl" ([ordered]@{ NeedUpDateFields=@(); NeedReturnFields=@("FBillNo"); IsDeleteEntry=$true; Model=$rmodel1 }) "RET-V1"
if (-not $retNo) {
  Write-Output "`n--- RETURN V2: without FEntrySrc* ---"
  $retNo = Chain "PRD_ReturnMtrl" ([ordered]@{ NeedUpDateFields=@(); NeedReturnFields=@("FBillNo"); IsDeleteEntry=$true; Model=$rmodel }) "RET-V2"
}
Write-Output "`nRESULT: FEED=$feedNo RET=$retNo"

# ---------- cleanup SCBL00003853 (status A) ----------
Write-Output "`n--- cleanup probe6 bill ---"
try {
  $del = Post "/Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Delete.common.kdsvc" @{ format="1"; useragent="ApiClient"; rid="356831840"; timestamp="2026-09-09 11:00:00"; v="1.0"; parameters=@("PRD_FeedMtrl", @{Numbers=@("SCBL00003853")}) }
  Write-Output "DELETE: $($del.Substring(0, [Math]::Min(300, $del.Length)))"
} catch { Write-Output "DELETE ERROR: $($_.Exception.Message)" }
