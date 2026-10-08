# 补退料物料行备注（FEntrtyMemo）后端接口文档

| 项 | 内容 |
| --- | --- |
| 文档版本 | v1.1 |
| 更新日期 | 2026-10-07 |
| 涉及服务 | 补退料独立后端服务（`http://{host}:8081/DBMaterialCall/*`） |
| 关联前端 | 退料申请页 `pages/material-call`、质检审核页 `pages/material-audit` |
| 字段标识 | `FEntrtyMemo`（与金蝶 K3Cloud 生产退料单分录"备注"字段同名） |

## 1. 需求背景

物料行备注（`FEntrtyMemo`）在两个环节流转：

1. **申请人**在退料清单中针对每个物料行选填备注，随申请提交；
2. **质检审核人**打开待审单据时，备注框直接显示申请人填写的备注，审核人可直接修改；
3. 审核通过后，最终备注（申请人原值或审核人修改后的值）落库，并在 M6 生成金蝶 K3Cloud 生产退料单（`PRB_ReturnStock`）时写入退料单分录备注字段 `FEntrtyMemo`。

## 2. 变更概览

| 接口 | 方法 | 变更内容 | 是否必填 |
| --- | --- | --- | --- |
| `/DBMaterialCall/submit` | POST | 请求体每个物料行新增 `FEntrtyMemo`（申请人填写，选填） | 选填 |
| `/DBMaterialCall/getAuditList` | GET | 响应中每个物料行新增 `FEntrtyMemo`（回显申请人备注） | - |
| `/DBMaterialCall/audit` | POST | `materialMemos` 中携带审核页当前备注（初值为申请人备注，审核人可改），后端覆盖落库 | 选填 |
| 数据库 | - | 物料明细表新增 `FEntrtyMemo` 列 | - |

## 3. 数据库变更

在**物料明细表**（表名以后端实际为准，如 `MaterialCallDetail` / `T_MaterialCall_Detail`）新增列：

| 列名 | 类型 | 长度 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `FEntrtyMemo` | NVARCHAR / VARCHAR | 200 | 是 | `''` | 物料行备注；申请人提交时写入初值，审核通过时按审核页值覆盖 |

参考 SQL（SQL Server，表名按实际替换）：

```sql
ALTER TABLE MaterialCallDetail ADD FEntrtyMemo NVARCHAR(200) NULL CONSTRAINT DF_MaterialCallDetail_FEntrtyMemo DEFAULT('');
```

## 4. 接口变更一：提交退料申请

### `POST /DBMaterialCall/submit?username={username}`

请求体结构不变，仅在 `orderList[].materials[]` 的每个物料行上新增 `FEntrtyMemo`：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderList[].materials[].FEntrtyMemo | String | 否 | 申请人填写的物料行备注，最长 200 字符；后端入库前 trim，超长按 200 截断 |

请求片段示例：

```json
{
  "applicantStaffId": "10086",
  "applicantName": "李四",
  "returnTypeId": 3,
  "returnTypeName": "不良品退料",
  "orderList": [
    {
      "orderCode": "MO2026090001",
      "materials": [
        {
          "materialCode": "1.001.0001",
          "materialName": "六角螺丝M6",
          "spec": "M6*20",
          "unit": "个",
          "qty": 100,
          "FEntrtyMemo": "装配时发现滑丝"
        }
      ]
    }
  ]
}
```

后端在创建申请单及物料明细行时，将 `FEntrtyMemo` 一并写入物料明细表。

## 5. 接口变更二：获取待审核列表

### `GET /DBMaterialCall/getAuditList?username={username}`

响应结构不变，仅在 `orderList[].materials[]` 的每个物料行上新增 `FEntrtyMemo`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| FEntrtyMemo | String | 申请人提交时填写的物料行备注；未填写或历史数据返回空串 `''`，不返回 null |

前端审核页备注框绑定该字段直接回显，审核人可在原内容上修改。

响应片段示例：

```json
{
  "status": 0,
  "msg": "ok",
  "data": [
    {
      "id": 1001,
      "callNo": "TL20261007001",
      "orderList": [
        {
          "orderCode": "MO2026090001",
          "materials": [
            {
              "materialCode": "1.001.0001",
              "materialName": "六角螺丝M6",
              "spec": "M6*20",
              "unit": "个",
              "qty": 100,
              "FEntrtyMemo": "装配时发现滑丝"
            }
          ]
        }
      ]
    }
  ]
}
```

## 6. 接口变更三：质检审核

### `POST /DBMaterialCall/audit?username={username}`

请求体在原有字段基础上新增 `materialMemos`：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| materialMemos | Array&lt;Object&gt; | 否 | 物料行备注数组；审核页打开时初值为申请人备注，审核人修改后提交，后端按行覆盖 |
| materialMemos[].orderCode | String | 是 | 生产订单编码，分录匹配键之一 |
| materialMemos[].materialCode | String | 是 | 物料编码，分录匹配键之一 |
| materialMemos[].qty | Number | 是 | 本物料行退料数量，用于同一订单下相同物料多行的区分 |
| materialMemos[].FEntrtyMemo | String | 否 | 审核页当前备注内容（可能与申请人原值不同），最长 200 字符；后端 trim、超长截断 |

### 6.1 分录匹配规则

后端以 `orderCode + materialCode + qty` 作为联合键，将备注定位到具体物料明细行：

- 匹配成功：用提交值**覆盖**明细行 `FEntrtyMemo`（审核人未修改则覆盖为相同的申请人原值）；
- 匹配不到（理论上不应发生）：跳过该行备注，不中断审核主流程，并记录日志；
- 同一订单下存在相同物料编码的多行时，`qty` 参与区分；如数量也相同，则按申请单明细行序号顺序匹配。

### 6.2 请求示例（审核通过，备注经审核人修改）

```json
{
  "id": 1001,
  "returnTypeId": 3,
  "reasonId": 12,
  "reasonText": "生产不良",
  "auditStaffId": "QC001",
  "auditStaffName": "张三",
  "auditResult": 1,
  "forceFlag": 0,
  "materialMemos": [
    {
      "orderCode": "MO2026090001",
      "materialCode": "1.001.0001",
      "qty": 100,
      "FEntrtyMemo": "经复检确认为滑丝, 按不良品退"
    }
  ]
}
```

响应沿用现有契约，不新增字段：

```json
{ "status": 0, "msg": "审核完成", "data": null }
```

库存不足部分匹配时仍按原契约返回 `status=2` / `code=PARTIAL_MATCH`；前端带 `forceFlag=1` 重试时会原样回传 `materialMemos`，后端无需额外处理。

## 7. 后端处理逻辑

1. **提交环节（/submit）**：创建物料明细行时写入申请人填写的 `FEntrtyMemo`；字段缺省视为空串。
2. **待审列表（/getAuditList）**：从物料明细表读取 `FEntrtyMemo` 原样返回，保证字符串类型（null 转 `''`）。
3. **审核环节（/audit）**：
   - 参数校验：`materialMemos` 缺省视为空数组；逐项校验字段类型，`FEntrtyMemo` trim 后超过 200 字符按 200 截断；
   - 审核通过事务内按第 6.1 节匹配规则覆盖更新明细行 `FEntrtyMemo`；
   - 备注更新失败只记录日志，不阻断审核与金蝶单据生成；
   - 驳回（`auditResult=2`）时忽略 `materialMemos`，不更新备注，申请人原备注保留。
4. **金蝶生产退料单保存（M6）**：调用 K3Cloud 保存接口组装 `FEntity` 分录时，在对应物料分录上写入：

```json
{
  "FMaterialId": { "FNumber": "1.001.0001" },
  "FRealQty": 100,
  "FEntrtyMemo": "经复检确认为滑丝, 按不良品退"
}
```

5. **异常重试**：`/DBMaterialCall/retryErpOrder` 重新生成金蝶退料单时，备注从物料明细表读取（即审核后的最终值），不依赖前端再次提交。

## 8. 兼容性说明

- 所有接口中 `FEntrtyMemo` / `materialMemos` 均为选填，旧版本小程序不上传时按空备注处理，主流程不受影响；
- `FEntrtyMemo` 在所有响应中保证为字符串（空值返回 `''`）；
- 历史申请单备注为空，审核页备注框显示空占位，审核人可直接填写，效果等同于新增备注。
