# 补退料物料行备注（FEntrtyMemo）/ 行拆分 / 责任归属后端接口文档

| 项 | 内容 |
| --- | --- |
| 文档版本 | v1.2 |
| 更新日期 | 2026-10-09 |
| 涉及服务 | 补退料独立后端服务（`http://{host}:8081/DBMaterialCall/*`） |
| 关联前端 | 退料申请页 `pages/material-call`、质检审核页 `pages/material-audit` |
| 字段标识 | `FEntrtyMemo`（与金蝶 K3Cloud 生产退料单分录"备注"字段同名）、`responsible`（责任归属，写入金蝶退料单分录自定义字段 `Fresponsible`） |

## 1. 需求背景

### 1.1 物料行备注（FEntrtyMemo，v1.1）

物料行备注（`FEntrtyMemo`）在两个环节流转：

1. **申请人**在退料清单中针对每个物料行选填备注，随申请提交；
2. **质检审核人**打开待审单据时，备注框直接显示申请人填写的备注，审核人可直接修改；
3. 审核通过后，最终备注（申请人原值或审核人修改后的值）落库，并在 M6 生成金蝶 K3Cloud 生产退料单（`PRB_ReturnStock`）时写入退料单分录备注字段 `FEntrtyMemo`。

### 1.2 物料行拆分（v1.2 新增）

申请人在退料清单中可将**同一物料行按数量拆分为多行**（如可退 7 个，拆成 3 个 + 4 个两行），每行独立填写备注。拆分后：

- 提交报文 `orderList[].materials[]` 中允许出现**同订单、同物料编码（`materialCode`）的多条记录**，各行 `qty` 独立、`FEntrtyMemo` 独立；
- 后端必须按报文数组**逐行插入物料明细**，严禁按 `materialCode` 去重、合并数量或覆盖备注；
- 同一订单下同物料各行 `qty` 合计不超过该物料可退上限（前端已校验，后端建议复核）。

### 1.3 责任归属（responsible，v1.2 新增）

质检审核人在审核页可针对**每个物料行**手填"责任归属"文本（选填，自由文本，最长 100 字符）：

- 申请人侧不采集该字段，提交时为空（默认 `''`）；
- 审核页通过 `getAuditList` 回显当前值（历史单据为空串），审核人填写/修改后随 `/audit` 的 `materialMemos` 按行覆盖落库；
- 该字段审核通过后落库，并在 M6 生成金蝶 K3Cloud 生产退料单（`PRB_ReturnStock`）时写入退料单分录自定义字段 `Fresponsible`（金蝶侧需预先配置该文本自定义字段）。

## 2. 变更概览

| 接口 | 方法 | 变更内容 | 是否必填 |
| --- | --- | --- | --- |
| `/DBMaterialCall/submit` | POST | 请求体每个物料行携带 `FEntrtyMemo`（申请人填写，选填）；允许同订单同物料编码多行（行拆分），后端逐行入库 | 选填 |
| `/DBMaterialCall/getAuditList` | GET | 响应中每个物料行返回 `FEntrtyMemo`（回显申请人备注）与 `responsible`（回显责任归属）；拆分物料返回为多行 | - |
| `/DBMaterialCall/audit` | POST | `materialMemos` 每行携带 `FEntrtyMemo`（审核人可改）与 `responsible`（审核人手填），后端按行覆盖落库 | 选填 |
| 数据库 | - | 物料明细表新增 `FEntrtyMemo` 列（v1.1）、`responsible` 列（v1.2） | - |

## 3. 数据库变更

在**物料明细表**（表名以后端实际为准，如 `MaterialCallDetail` / `T_MaterialCall_Detail`）新增列：

| 列名 | 类型 | 长度 | 可空 | 默认值 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `FEntrtyMemo` | NVARCHAR / VARCHAR | 200 | 是 | `''` | 物料行备注；申请人提交时写入初值，审核通过时按审核页值覆盖 |
| `responsible` | NVARCHAR / VARCHAR | 100 | 是 | `''` | 责任归属；仅审核环节由审核人手填，审核通过时按行覆盖，M6 写入金蝶退料单分录 `Fresponsible` |

参考 SQL（SQL Server，表名按实际替换）：

```sql
ALTER TABLE MaterialCallDetail ADD FEntrtyMemo NVARCHAR(200) NULL CONSTRAINT DF_MaterialCallDetail_FEntrtyMemo DEFAULT('');
ALTER TABLE MaterialCallDetail ADD responsible NVARCHAR(100) NULL CONSTRAINT DF_MaterialCallDetail_responsible DEFAULT('');
```

> 行拆分不引入新列：拆分行与普通行一样是物料明细表中的独立记录，依赖明细行自身主键/行序号区分；禁止以 `orderCode + materialCode` 作为明细表唯一键。

## 4. 接口变更一：提交退料申请

### `POST /DBMaterialCall/submit?username={username}`

请求体结构不变，在 `orderList[].materials[]` 的每个物料行上携带 `FEntrtyMemo`，并允许行拆分产生同物料编码多行：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| orderList[].materials[].materialCode | String | 是 | 物料编码；同一订单的 materials 数组中允许重复出现（行拆分） |
| orderList[].materials[].qty | Number | 是 | 本行退料数量；拆分行各自独立，同物料各行合计 ≤ 可退上限 |
| orderList[].materials[].FEntrtyMemo | String | 否 | 申请人填写的本行备注，最长 200 字符；后端入库前 trim，超长按 200 截断；拆分行各自独立 |
| orderList[].materials[].responsible | String | 否 | 责任归属；申请人侧不采集，缺省即空串，由审核环节填写 |

请求片段示例（物料 `1.001.0001` 可退 7 个，申请人拆为 3 + 4 两行，备注一对一）：

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
          "qty": 3,
          "FEntrtyMemo": "3个滑丝, 装配班组责任"
        },
        {
          "materialCode": "1.001.0001",
          "materialName": "六角螺丝M6",
          "spec": "M6*20",
          "unit": "个",
          "qty": 4,
          "FEntrtyMemo": "4个来料不良"
        },
        {
          "materialCode": "1.001.0002",
          "materialName": "平垫",
          "spec": "Φ6",
          "unit": "个",
          "qty": 10,
          "FEntrtyMemo": ""
        }
      ]
    }
  ]
}
```

后端处理要求：

1. **按数组顺序逐行插入物料明细表**，每个数组元素对应一条明细记录，`FEntrtyMemo` 写入对应行；
2. 严禁按 `materialCode` 去重或合并 `qty`；同物料编码多行在明细中就是多条记录；
3. 建议复核同一订单下同物料各行 `qty` 合计不超过该物料可退数量，超出时整单报错（错误信息需说明物料编码与超退数量）；
4. 插入顺序即后续 `getAuditList` 的行返回顺序，也是数量相同时审核备注/责任归属的匹配顺序，须稳定保留。

## 5. 接口变更二：获取待审核列表

### `GET /DBMaterialCall/getAuditList?username={username}`

响应结构不变，在 `orderList[].materials[]` 的每个物料行上返回 `FEntrtyMemo` 与 `responsible`；申请人拆分的物料返回为数组中的多行：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| FEntrtyMemo | String | 申请人提交时填写的物料行备注；未填写或历史数据返回空串 `''`，不返回 null |
| responsible | String | 责任归属（审核人填写）；未审核/未填写/历史数据返回空串 `''`，不返回 null |

前端审核页备注框/责任归属输入框分别绑定两个字段直接回显；同一物料编码的多个拆分行须作为独立数组元素返回，**行顺序与 submit 入库顺序一致**（前端依赖该顺序在 qty 也相同时区分行）。

响应片段示例（对应第 4 节的拆分提交，责任归属尚未填写）：

```json
{
  "status": 0,
  "msg": "ok",
  "data": [
    {
      "id": 1001,
      "callNo": "TL20261009001",
      "orderList": [
        {
          "orderCode": "MO2026090001",
          "materials": [
            {
              "materialCode": "1.001.0001",
              "materialName": "六角螺丝M6",
              "spec": "M6*20",
              "unit": "个",
              "qty": 3,
              "FEntrtyMemo": "3个滑丝, 装配班组责任",
              "responsible": ""
            },
            {
              "materialCode": "1.001.0001",
              "materialName": "六角螺丝M6",
              "spec": "M6*20",
              "unit": "个",
              "qty": 4,
              "FEntrtyMemo": "4个来料不良",
              "responsible": ""
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

请求体在原有字段基础上携带 `materialMemos`，每个元素对应审核页的一个物料行（含拆分行）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| materialMemos | Array&lt;Object&gt; | 否 | 物料行数组；审核页打开时初值为申请人备注/责任归属当前值，审核人修改后提交，后端按行覆盖 |
| materialMemos[].orderCode | String | 是 | 生产订单编码，分录匹配键之一 |
| materialMemos[].materialCode | String | 是 | 物料编码，分录匹配键之一 |
| materialMemos[].qty | Number | 是 | 本物料行退料数量，用于同一订单下相同物料多行（拆分）的区分 |
| materialMemos[].FEntrtyMemo | String | 否 | 审核页当前备注内容（可能与申请人原值不同），最长 200 字符；后端 trim、超长截断 |
| materialMemos[].responsible | String | 否 | 审核人手填的责任归属，最长 100 字符；后端 trim、超长截断；缺省视为空串；M6 写入金蝶退料单分录 `Fresponsible` |

### 6.1 分录匹配规则

后端以 `orderCode + materialCode + qty` 作为联合键，将 `FEntrtyMemo` 与 `responsible` 定位到具体物料明细行：

- 匹配成功：用提交值**同时覆盖**明细行 `FEntrtyMemo` 与 `responsible`（审核人未修改则覆盖为相同原值）；
- 匹配不到（理论上不应发生）：跳过该行，不中断审核主流程，并记录日志；
- 同一订单下存在相同物料编码的多行（申请人拆分）时，`qty` 参与区分；如拆分后各行数量也相同（如 10 拆成 5+5），则**按申请单明细行序号顺序（即 submit 入库顺序）依次匹配**，每个明细行只匹配一次。

### 6.2 请求示例（审核通过，拆分行各自备注与责任归属）

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
      "qty": 3,
      "FEntrtyMemo": "3个滑丝, 装配班组责任",
      "responsible": "装配一班"
    },
    {
      "orderCode": "MO2026090001",
      "materialCode": "1.001.0001",
      "qty": 4,
      "FEntrtyMemo": "4个来料不良",
      "responsible": "供应商-XX五金"
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

1. **提交环节（/submit）**：
   - 按 `materials` 数组逐行创建物料明细，写入申请人填写的 `FEntrtyMemo`（缺省视为空串）；同物料编码多行必须生成多条明细，保留数组顺序（建议以自增行号/明细主键稳定排序）；
   - 复核同订单同物料各行数量合计不超过可退上限。
2. **待审列表（/getAuditList）**：从物料明细表逐行读取 `FEntrtyMemo`、`responsible` 原样返回，保证字符串类型（null 转 `''`），行顺序与入库顺序一致，不做同物料合并。
3. **审核环节（/audit）**：
   - 参数校验：`materialMemos` 缺省视为空数组；逐项校验字段类型，`FEntrtyMemo` trim 后超过 200 字符按 200 截断，`responsible` trim 后超过 100 字符按 100 截断；
   - 审核通过事务内按第 6.1 节匹配规则**同时**覆盖更新明细行 `FEntrtyMemo` 与 `responsible`；
   - 备注/责任归属更新失败只记录日志，不阻断审核与金蝶单据生成；
   - 驳回（`auditResult=2`）时忽略 `materialMemos`，不更新两个字段，申请人原备注保留。
4. **金蝶生产退料单保存（M6）**：调用 K3Cloud 保存接口组装 `FEntity` 分录时，**每条物料明细对应一个分录行（含拆分行）**，禁止把同物料的拆分行合并为一条分录，否则各行备注会丢失；`FEntrtyMemo` 写入备注、`responsible` 写入分录自定义字段 `Fresponsible`。分录示例：

```json
{
  "FMaterialId": { "FNumber": "1.001.0001" },
  "FRealQty": 3,
  "FEntrtyMemo": "3个滑丝, 装配班组责任",
  "Fresponsible": "装配一班"
}
```

5. **异常重试**：`/DBMaterialCall/retryErpOrder` 重新生成金蝶退料单时，`FEntrtyMemo` 与 `responsible` 均从物料明细表按行读取（即审核后的最终值），不依赖前端再次提交。

## 8. 兼容性说明

- 所有接口中 `FEntrtyMemo`、`responsible`、`materialMemos` 均为选填，旧版本小程序不上传时按空值处理，主流程不受影响；
- `FEntrtyMemo`、`responsible` 在所有响应中保证为字符串（空值返回 `''`）；
- 历史申请单两字段为空，审核页输入框显示空占位，审核人可直接填写，效果等同于新增；
- 行拆分对后端报文格式无破坏：仅是同一 `materialCode` 在 `materials` 数组中出现多次；任何既有"按物料编码唯一"的入库/更新/金蝶组装逻辑都需改为"按数组行逐条"处理。
