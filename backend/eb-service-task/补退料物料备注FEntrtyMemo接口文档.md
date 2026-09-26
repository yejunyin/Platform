# 补退料物料行备注（FEntrtyMemo）后端接口文档

| 项 | 内容 |
| --- | --- |
| 文档版本 | v1.0 |
| 更新日期 | 2026-09-26 |
| 涉及服务 | 补退料独立后端服务（`http://{host}:8081/DBMaterialCall/*`） |
| 关联前端 | 质检审核页 `pages/material-audit/material-audit` |
| 字段标识 | `FEntrtyMemo`（与金蝶 K3Cloud 生产退料单分录"备注"字段同名） |

## 1. 需求背景

质检审核人在审核退料申请时，需要针对**每个物料行**填写备注（选填），备注随审核请求提交后端：

1. 落库保存到申请单物料明细行，便于追溯；
2. 在 M6 生成金蝶 K3Cloud 生产退料单（`PRB_ReturnStock`）时，写入退料单分录的备注字段 `FEntrtyMemo`。

## 2. 变更概览

| 接口 | 方法 | 变更内容 | 是否必填 |
| --- | --- | --- | --- |
| `/DBMaterialCall/getAuditList` | GET | 响应中每个物料行新增 `FEntrtyMemo`（历史数据为空串） | - |
| `/DBMaterialCall/audit` | POST | 请求体新增 `materialMemos` 物料行备注数组 | 选填 |
| 数据库 | - | 物料明细表新增 `FEntrtyMemo` 列 | - |

## 3. 数据库变更

在**物料明细表**（表名以后端实际为准，如 `MaterialCallDetail` / `T_MaterialCall_Detail`）新增列：

| 列名 | 类型 | 长度 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `FEntrtyMemo` | NVARCHAR / VARCHAR | 200 | 是 | `''` | 物料行备注，对应金蝶生产退料单分录备注 |

参考 SQL（SQL Server，表名按实际替换）：

```sql
ALTER TABLE MaterialCallDetail ADD FEntrtyMemo NVARCHAR(200) NULL CONSTRAINT DF_MaterialCallDetail_FEntrtyMemo DEFAULT('');
```

## 4. 接口变更一：获取待审核列表

### `GET /DBMaterialCall/getAuditList?username={username}`

响应结构不变，仅在 `orderList[].materials[]` 的每个物料行上新增 `FEntrtyMemo` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| FEntrtyMemo | String | 物料行备注；无备注或历史数据返回空串 `''`，不返回 null |

响应片段示例：

```json
{
  "status": 0,
  "msg": "ok",
  "data": [
    {
      "id": 1001,
      "callNo": "TL20260926001",
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
              "FEntrtyMemo": ""
            }
          ]
        }
      ]
    }
  ]
}
```

## 5. 接口变更二：质检审核

### `POST /DBMaterialCall/audit?username={username}`

请求体在原有字段基础上新增 `materialMemos`：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| materialMemos | Array&lt;Object&gt; | 否 | 物料行备注数组，审核通过（`auditResult=1`）时使用；驳回时忽略 |
| materialMemos[].orderCode | String | 是 | 生产订单编码，分录匹配键之一 |
| materialMemos[].materialCode | String | 是 | 物料编码，分录匹配键之一 |
| materialMemos[].qty | Number | 是 | 本物料行退料数量，用于同一订单下相同物料多行的区分 |
| materialMemos[].FEntrtyMemo | String | 否 | 物料行备注，最长 200 字符；后端入库前做 trim 与长度截断 |

### 5.1 分录匹配规则

后端以 `orderCode + materialCode + qty` 作为联合键，将备注定位到具体物料明细行：

- 匹配成功：更新明细行 `FEntrtyMemo`；
- 匹配不到（理论上不应发生）：跳过该行备注，不中断审核主流程，并记录日志；
- 同一订单下存在相同物料编码的多行时，`qty` 参与区分；如数量也相同，则按申请单明细行序号顺序匹配。

### 5.2 请求示例（审核通过）

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
      "FEntrtyMemo": "表面有划痕, 按不良品退"
    },
    {
      "orderCode": "MO2026090001",
      "materialCode": "1.001.0002",
      "qty": 20,
      "FEntrtyMemo": ""
    }
  ]
}
```

响应沿用现有契约，不新增字段：

```json
{ "status": 0, "msg": "审核完成", "data": null }
```

库存不足部分匹配时仍按原契约返回 `status=2` / `code=PARTIAL_MATCH`；前端带 `forceFlag=1` 重试时会原样回传 `materialMemos`，后端无需额外处理。

## 6. 后端处理逻辑

1. **参数校验**：`materialMemos` 缺省视为空数组；逐项校验字段类型，`FEntrtyMemo` trim 后超过 200 字符按 200 截断。
2. **落库**：审核通过事务内，按第 5.1 节匹配规则更新物料明细行 `FEntrtyMemo`；备注更新失败只记录日志，不阻断审核与金蝶单据生成。
3. **金蝶生产退料单保存（M6）**：调用 K3Cloud `executeOperation` / 保存接口组装 `FEntity` 分录时，在对应物料分录上写入：

```json
{
  "FMaterialId": { "FNumber": "1.001.0001" },
  "FRealQty": 100,
  "FEntrtyMemo": "表面有划痕, 按不良品退"
}
```

4. **异常重试**：`/DBMaterialCall/retryErpOrder` 重新生成金蝶退料单时，备注从物料明细表读取，不依赖前端再次提交。

## 7. 兼容性说明

- `materialMemos` 为选填字段，旧版本小程序不上传时后端按空备注处理，审核流程不受影响；
- `FEntrtyMemo` 在所有响应中保证为字符串（空值返回 `''`）；
- 驳回（`auditResult=2`）时后端忽略 `materialMemos`，不更新备注。
