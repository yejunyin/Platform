# 补退料单 ERP 头字段改造说明（Forg / FGroup / FInspector）

> 适用服务：补退料独立后端 `http://<host>:8081/DBMaterialCall/*`
> 关联前端页面：`pages/material-call/material-call`（发起人提交）、`pages/material-audit/material-audit`（质检审核）
> 改造日期：2026-10-10

## 1. 需求概述

金蝶「生产退料单」新增 **3 个单据头字段**（注意：均为**单头字段**，不是分录字段；分录备注仍为 `FEntrtyMemo`，本次不涉及）：

| ERP 字段 | 含义 | 来源 | 取值 | 必填 | 写入时机 |
| --- | --- | --- | --- | --- | --- |
| `Forg` | 归属组织 | 发起人提交申请时选择 | 枚举：`华丽` / `桐琴` / `无刷` | 是 | 审核通过生成金蝶退料单时 |
| `FGroup` | 组别 | 发起人提交申请时手填 | 文本，最长 50 | 否 | 审核通过生成金蝶退料单时 |
| `FInspector` | 质检员 | 后端自动取数 | 申请单头表 `qc_staff_name` | 是 | 质检员审核通过（`auditResult=1`）生成金蝶退料单时 |

关键说明：

- `Forg`、`FGroup` 随 `POST /DBMaterialCall/submit` 请求上送，后端需落库到申请单头表，并在后续生成金蝶退料单时原样写入单头。
- `FInspector` **不由前端传参**，在质检员审核通过、后端生成金蝶生产退料单时，由后端取**该申请单的 `qc_staff_name`**（发起人提交时指定的质检员姓名）写入。**不是**取审核动作的操作人 `auditStaffName`（正常情况下二者为同一人，但取数口径必须是申请单上的 `qc_staff_name`）。
- 一张申请单按生产订单分组可能生成多张金蝶退料单，每张退料单单头都写入相同的 `Forg` / `FGroup` / `FInspector`。

## 2. 接口变更

### 2.1 `POST /DBMaterialCall/submit`（发起人提交）

请求体在现有字段基础上新增：

```json
{
  "applicantStaffId": "...",
  "applicantName": "张三",
  "Forg": "华丽",
  "FGroup": "A组",
  "returnTypeId": "...",
  "returnTypeName": "良品退料",
  "qcStaffId": "Q001",
  "qcStaffName": "李四",
  "orderList": [ ... ]
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `Forg` | string | 是 | 归属组织，仅允许 `华丽` / `桐琴` / `无刷` 三个枚举值 |
| `FGroup` | string | 否 | 组别，手填文本，建议 trim 后落库，最长 50，超长返回参数错误 |

后端处理要求：

1. **参数校验**
   - `Forg` 为空或不在枚举内：返回 `status != 0`，msg 提示「归属组织不合法」。
   - `FGroup` 做 trim；长度 > 50 时返回参数错误。
2. **持久化**：写入申请单头表新增列（见 2.2）。
3. 驳回后申请人重新提交、或草稿编辑提交时，以本次上送值覆盖。

### 2.2 数据库变更（申请单头表）

在补退料申请单头表（存储 `qc_staff_name`、`return_type_id` 等字段的同一张表）新增两列，参考 DDL（列名可按团队命名规范调整，请求体/ERP 字段名保持 `Forg`、`FGroup` 不变）：

```sql
ALTER TABLE material_call_application
  ADD COLUMN forg      VARCHAR(20) NULL COMMENT '归属组织(华丽/桐琴/无刷), 金蝶退料单头 Forg',
  ADD COLUMN f_group   VARCHAR(50) NULL COMMENT '组别, 金蝶退料单头 FGroup';
```

- `FInspector` **不需要新建列**：直接复用申请单头表已有的 `qc_staff_name`。
- 历史数据两列为 NULL，兼容策略见第 4 节。

### 2.3 查询接口回显

以下接口的申请单头信息中补充返回 `Forg`、`FGroup`（历史单可能为 `null`/空串），便于申请记录、审核页展示及问题追溯：

- `GET /DBMaterialCall/getMyApplications`
- `GET /DBMaterialCall/getApplicationDetail`
- `GET /DBMaterialCall/getAuditList`

> 前端当前版本暂不展示这两个回显字段，后端先行返回，后续前端展示无需再改接口。

### 2.4 `POST /DBMaterialCall/audit`（质检审核 → 生成 ERP 退料单）

接口入参**不变**。后端在审核通过（`auditResult=1`）后生成金蝶生产退料单（现有 M6 环节）时，在金蝶保存报文的 **Model 单头** 增加：

| 金蝶字段 | 取值 |
| --- | --- |
| `Forg` | 申请单头表 `forg`（发起人选择值：华丽/桐琴/无刷） |
| `FGroup` | 申请单头表 `f_group`（可为空，为空时传空串或不下发该字段，按金蝶接口要求处理） |
| `FInspector` | 申请单头表 `qc_staff_name` |

同样适用于 `POST /DBMaterialCall/retryErpOrder`（退料单生成异常后的重试）：重试报文必须与首次生成保持一致，带上上述三个单头字段。

## 3. 金蝶保存报文示例

在现有生产退料单 save 报文的 `Model` 节点上单头追加（示意，字段类型以金蝶云星空实际元数据为准）：

```json
{
  "FormId": "PRD_RETURNSTOCK",
  "Data": {
    "Model": {
      "Forg": "华丽",
      "FGroup": "A组",
      "FInspector": "李四",
      "FBillType": { "FNUMBER": "..." },
      "FStockOrgId": { "FNUMBER": "..." },
      "FEntity": [
        { "FEntrtyMemo": "...", "FMustQty": 10 }
      ]
    }
  }
}
```

说明：

1. `Forg` 在金蝶侧是**普通文本字段**，直接下发发起人选择的文字值（`华丽` / `桐琴` / `无刷`），无需组织内码/编码映射，也不要包成基础资料结构。
2. `FGroup` 同为文本字段，为空时传空串或不下发该字段（按金蝶接口要求处理）。
3. `FInspector` 按现有金蝶字段类型处理：若为文本字段直接传 `qc_staff_name`；若实际为职员/基础资料字段，需按姓名解析为对应基础资料内码后下发（解析不到时记录日志并按现有金蝶保存失败流程处理，确保可通过 `retryErpOrder` 重试）。

## 4. 历史数据与异常兼容

- 2026-10-10 之前产生的申请单 `forg` / `f_group` 为 NULL：
  - 这些单据走审核生成金蝶退料单时，`Forg` 不应静默传空导致金蝶报错。建议策略二选一（推荐 A）：
    - A. 配置默认归属组织，历史单按默认值下发并打日志；
    - B. 阻断生成，申请单置「退料单生成异常」状态，由管理员补录归属组织后在申请记录页重试。
- `qc_staff_name` 在现有流程中为必填，理论上不存在空值；若生成时发现为空，按金蝶保存失败处理并保留异常状态，禁止传空串写入 `FInspector`。
- 枚举扩展：后续若新增归属组织，由后端字典/配置维护；前端当前为固定三选项，新增时需同步发版。

## 5. 前端联调信息

- 前端已在提交页「退料类型」上方新增：归属组织下拉（必填，华丽/桐琴/无刷）、组别文本框（选填，maxlength=50）。
- `/submit` 请求体已携带 `Forg`、`FGroup`，示例：

```json
{
  "Forg": "桐琴",
  "FGroup": "B组",
  "returnTypeId": "xxx",
  "returnTypeName": "良品退料",
  "qcStaffId": "Q001",
  "qcStaffName": "李四",
  "orderList": [
    { "orderCode": "MO20261010001", "materials": [ { "materialCode": "M001", "qty": 5, "FEntrtyMemo": "" } ] }
  ]
}
```

- 审核请求 `POST /DBMaterialCall/audit` 前端无改动，`FInspector` 由后端取申请单 `qc_staff_name` 写入。

## 6. 验收清单

- [ ] `/submit` 校验 `Forg` 必填且为三个枚举之一，`FGroup` 长度 ≤ 50，两字段落库。
- [ ] 审核通过生成金蝶退料单，单头包含 `Forg`、`FGroup`、`FInspector`，其中 `FInspector = qc_staff_name`。
- [ ] 一张申请单生成多张退料单时，每张单单头三字段均正确。
- [ ] `retryErpOrder` 重试报文同样包含三字段。
- [ ] 查询接口（我的申请/详情/待审列表）返回 `Forg`、`FGroup`，历史单返回 null/空串不报错。
- [ ] 历史无归属组织单据按第 4 节策略处理，不出现金蝶静默保存空组织。
