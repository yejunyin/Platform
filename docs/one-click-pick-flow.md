# 一键领料调度流程模拟推演

> **场景**：生产订单 `MO20261030001`，共 **45 种物料**，每种物料的发料总数 / 单次发料数（FBoxStandardQty）都不同
> 
> **参数**：`max-round-materials=40`，`intervalSec=15` 分钟
> 
> **三种模式**：首轮无限制 → 正常模式（≤40 种）→ relax 动态框位分配

---

## 一、物料清单（45 种）

| # | 物料编码 | 物料名称 | totalQty | perBoxQty | 需推框数 ⌈total/per⌉ |
|---|---------|---------|---------|----------|---------------------|
| 1 | M001 | 轴承 6201 | 200 | 50 | 4 |
| 2 | M002 | 螺丝 M4×20 | 500 | 100 | 5 |
| 3 | M003 | 垫片 φ8 | 800 | 200 | 4 |
| 4 | M004 | 电机固定座 | 30 | 15 | 2 |
| 5 | M005 | 铜轴套 | 120 | 40 | 3 |
| 6 | M006 | 皮带 A 型 | 60 | 30 | 2 |
| 7 | M007 | 电容 100uF | 300 | 50 | 6 |
| 8 | M008 | 电阻 1kΩ | 1000 | 200 | 5 |
| 9 | M009 | 继电器 24V | 80 | 20 | 4 |
| 10 | M010 | 开关按钮 | 50 | 25 | 2 |
| 11 | M011 | 指示灯 绿 | 40 | 20 | 2 |
| 12 | M012 | 指示灯 红 | 40 | 20 | 2 |
| 13 | M013 | 蜂鸣器 | 30 | 15 | 2 |
| 14 | M014 | 急停按钮 | 10 | 5 | 2 |
| 15 | M015 | 接线端子 | 200 | 100 | 2 |
| 16 | M016 | 线槽 2m | 30 | 15 | 2 |
| 17 | M017 | 扎带 3×100 | 600 | 200 | 3 |
| 18 | M018 | 自攻螺丝 | 400 | 100 | 4 |
| 19 | M019 | 角码 | 100 | 50 | 2 |
| 20 | M020 | 橡胶脚垫 | 80 | 40 | 2 |
| 21 | M021 | 铝合金外壳 | 10 | 10 | 1 |
| 22 | M022 | 亚克力面板 | 5 | 5 | 1 |
| 23 | M023 | PCB 主板 | 3 | 3 | 1 |
| 24 | M024 | PCB 子板 | 6 | 6 | 1 |
| 25 | M025 | 电源模块 | 4 | 4 | 1 |
| 26 | M026 | 散热片 | 20 | 10 | 2 |
| 27 | M027 | 风扇 80mm | 12 | 6 | 2 |
| 28 | M028 | USB 接口 | 8 | 4 | 2 |
| 29 | M029 | 网线 | 30 | 10 | 3 |
| 30 | M030 | 电源线 | 10 | 5 | 2 |
| 31 | M031 | 锡丝 0.8mm | 500g | 100g | 5 |
| 32 | M032 | 助焊剂 | 200ml | 100ml | 2 |
| 33 | M033 | 洗板水 | 500ml | 250ml | 2 |
| 34 | M034 | 海绵刷 | 20 | 10 | 2 |
| 35 | M035 | 棉签 | 200 | 100 | 2 |
| 36 | M036 | 无水乙醇 | 1000ml | 250ml | 4 |
| 37 | M037 | 润滑油 | 500g | 125g | 4 |
| 38 | M038 | 防锈油 | 300g | 150g | 2 |
| 39 | M039 | 硅脂 | 100g | 50g | 2 |
| 40 | M040 | 密封胶 | 80g | 40g | 2 |
| 41 | M041 | 胶带 黑 | 20 | 10 | 2 |
| 42 | M042 | 胶带 红 | 20 | 10 | 2 |
| 43 | M043 | 标签纸 | 500 | 250 | 2 |
| 44 | M044 | 打包带 | 100m | 50m | 2 |
| 45 | M045 | 纸箱 A 型 | 3 | 3 | 1 |
| **合计** | | **~23,441** | | **~131 框** |

---

## 二、三种模式定义

| | 首轮 `firstRound` | 正常模式 | relax 模式 |
|---|---|---|---|
| **触发** | `currentRound==0` | `!firstRound && candidates.size() > 40` | `!firstRound && candidates.size() ≤ 40` |
| **种类上限** | 不限 | 前 40 种 | 不限（都要发的） |
| **每种推多少** | 1 箱（perBoxQty） | 1 箱（perBoxQty） | **还需要的全部框数**（动态分配） |
| **总量控制** | 无 | 本轮 ≤ 40 种 × 1 箱 | `在途 + 新推 ≤ 40` 框 |
| **sent_times 增量** | +1 | +1 | **+actualBoxes** |
| **Phase A 释放逻辑** | 不变 | 不变 | 不变 |
| **freeBoxes 剩余** | — | — | 下轮继续用 |

### relax 模式核心公式

```
freeBoxes       = maxRoundMaterials(40) − totalInFlight
totalInFlight   = Σ (sent_times − released_times)   所有未发满物料
needBoxes(物料) = ⌈(totalQty − sentQty) / perBoxQty⌉  向上取整
canTake(物料)   = min(needBoxes, freeBoxes)
roundQty(物料)  = perBoxQty × canTake（最后截断到 left）
```

### 排序优先级（选料顺序）

```
1. inFlight 少的优先（优先清在途）
2. sent_times 少的优先（轮询均衡）
3. current_round 少的优先
```

---

## 三、时间线模拟

> ⏱ 每轮间隔 15 分钟，共模拟约 **12 轮 ≈ 3 小时**
> 
> 📦 WMS 行为假设：上料 ≈ 15-30 分钟，领走（释放）≈ 30-60 分钟

---

### 🔵 第 1 轮 `currentRound=0`（首轮，45 种全推）

**时间**：10:00 | **模式**：首轮无限制 | **目标**：料架先铺满

#### Phase A — 料架查询
```
料架空（刚启动）→ 无事发生
```

#### Phase B — 检查未发满
```
全部 45 种未发满，candidates.size()=45
```

#### Phase C — 选料（firstRound=true）
```
✅ 不查料架，不限制种类数和框数
→ 45 种全部推 1 箱

pickedMaterials  = 45
pickedInFlightSum = 0 + 45 = 45
```

#### Phase D — 推 WMS ✅
```
BillCode: MES-OCP-20261030100000-ABCD
Entries: 45 行
```

#### Phase E — 更新
```
sent_times 全部 +1
inFlight   全部 = 1
```

#### 第 1 轮后状态

| 指标 | 值 |
|------|----|
| totalInFlight | **45** |
| 每种 sentQty | 0 |
| WMS 料架 | 还没来得及上料 |

---

### 🔵 第 2 轮 `currentRound=1`（正常模式）

**时间**：10:15 | **模式**：正常（45 > 40） | **上限**：本轮 ≤ 40 种

#### Phase A — 料架查询
```
WMS 上料：M001~M030（30 种刚上料）
  → 30 种 confirmed_times++, last_on_shelf_qty 更新
```

#### Phase C — 选料（relax=false）
```
排序后所有 45 种 inFlight=1, sent_times=1 → 原顺序
前 40 种入选（M001~M040）
→ 每种推 1 箱

pickedMaterials  = 40
pickedInFlightSum = 45 + 40 = 85
```

#### 第 2 轮后状态
```
M001~M040: sent_times=2
totalInFlight = 85（继续走高）
```

---

### 🔵 第 3 轮 `currentRound=2`（正常模式）

**时间**：10:30

#### Phase A — 料架查询
```
释放（领走）：M001, M002, M003
  → sentQty += perBoxQty, released_times++, confirmed_times++
上料：M031~M045（之前没上的都上了）

totalInFlight: 85 → 82
M001 sentQty=50, inFlight=2-1=1
M002 sentQty=100, inFlight=2-1=1
M003 sentQty=40,  inFlight=2-1=1
```

#### Phase C — 选料
```
排序后前 40 种入选：
  M001/M002/M003（inFlight=1, sent_times=2）排最前
  M041~M045（inFlight=1, sent_times=1）也靠前
→ 每种 1 箱

totalInFlight = 82 → 122
```

---

### 🔵 第 4~8 轮（正常模式，看趋势）

**时间**：10:45 ~ 11:45

```
每轮推 40 种 × 1 箱
每轮释放约 5-10 种

到第 8 轮结束：
  ✅ 发满 1 次的物料（21~25, 45）全部发完
  remainingCount = 45 - 6 = 39
  totalInFlight 峰值 ≈ 180
```

---

### 🟡 第 9 轮（还在正常模式，39 > 40？）

**时间**：12:00

```
等等... candidates.size() = 39 不就是 ≤ maxRoundMaterials(40) 吗？
→ relax=true！🎊 正式进入 relax 动态分配模式！
```

#### Phase A — 料架查询
```
WMS 持续释放。
发满了 20 种（之前只发满需要 1 框的，现在又发满了 14 种）
totalInFlight = 35
```

#### Phase B — 检查未发满
```
candidates.size() = 45 - 20 = 25 ≤ 40 → relax=true
```

#### Phase C — relax 动态框位分配（**关键变化！**）
```
freeBoxes       = 40 - 35 = 5（只剩 5 个空框！）

剩余 25 种物料，每种还需框数（needBoxes）：
  M001: 还需 3 框  (200-50)/50 = 3
  M002: 还需 4 框
  M007: 还需 5 框
  ...
  M040: 还需 1 框
  合计 ≈ 90 框

排序后按顺序贪心分配：
  M040: canTake = min(1, 5) = 1 → freeBoxes = 4, sent_times += 1
  M038: canTake = min(1, 4) = 1 → freeBoxes = 3, sent_times += 1
  M039: canTake = min(1, 3) = 1 → freeBoxes = 2, sent_times += 1
  M026: canTake = min(1, 2) = 1 → freeBoxes = 1, sent_times += 1
  M027: canTake = min(1, 1) = 1 → freeBoxes = 0, sent_times += 1
  M028: freeBoxes=0 → 跳过
  ... 后面全部跳过

本轮推了 5 种，每种 1 箱，刚好用完 5 个空框 ✅
```

---

### 🟡 第 10 轮（relax 模式继续）

**时间**：12:15

#### Phase A — 料架查询
```
WMS 释放了 10 种（在途从 35 降到 25）
totalInFlight = 25
freeBoxes = 40 - 25 = 15
```

#### Phase C — 动态分配
```
剩余 25 种，freeBoxes=15

排序后按顺序分配：
  M040（刚推 1 箱就发满了，跳过）
  M028: 还需 1 框, canTake=min(1,15)=1 → freeBoxes=14
  M029: 还需 2 框, canTake=min(2,14)=2 → freeBoxes=12
  M030: 还需 2 框, canTake=min(2,12)=2 → freeBoxes=10
  M032: 还需 2 框, canTake=min(2,10)=2 → freeBoxes=8
  M033: 还需 2 框, canTake=min(2,8)=2 → freeBoxes=6
  M034: 还需 2 框, canTake=min(2,6)=2 → freeBoxes=4
  M035: 还需 2 框, canTake=min(2,4)=2 → freeBoxes=2
  M036: 还需 3 框, canTake=min(3,2)=2 → freeBoxes=0 ← 截断！
  M037: freeBoxes=0 → 跳过
  ...

本轮推了 9 种，共 15 箱，把 15 个空框全填满 ✅
```

---

### 🟡 第 11 轮（relax 模式，物料越来越少）

**时间**：12:30

#### Phase A — 料架查询
```
WMS 释放得更快了（最后一批 WMS 知道快完了）。
发满了 35 种。
totalInFlight = 15
freeBoxes = 40 - 15 = 25
```

#### Phase C — 动态分配
```
剩余 10 种物料：
  A=2 框（perBox=10, total=20, sent=0）
  B=3 框
  C=4 框
  D=5 框
  E=2 框
  F=3 框
  G=2 框
  H=1 框
  I=2 框
  J=1 框
 合计 ≈ 25 框

排序后分配：
  H: canTake=1  → freeBoxes=24
  J: canTake=1  → freeBoxes=23
  A: canTake=2  → freeBoxes=21
  E: canTake=2  → freeBoxes=19
  G: canTake=2  → freeBoxes=17
  I: canTake=2  → freeBoxes=15
  B: canTake=3  → freeBoxes=12
  F: canTake=3  → freeBoxes=9
  C: canTake=4  → freeBoxes=5
  D: canTake=5  → canTake=min(5,5)=5 → freeBoxes=0
  （刚好 25 框，全部分完！）

10 种全部推了，总共 25 箱，刚好 = freeBoxes ✅
sent_times 分别 +1/+1/+2/+2/+2/+2/+3/+3/+4/+5
```

---

### ✅ 第 12 轮（complete）

**时间**：12:45

#### Phase A — 料架查询
```
WMS 全部领走！
totalInFlight → 0
每种 sentQty >= totalQty
```

#### Phase B — 检查
```
全部 45 种发满 → completeTask 🎉
```

---

## 四、三阶段核心对比

| 对比项 | 首轮 | 正常模式 | relax 模式 |
|--------|------|---------|-----------|
| **触发** | `currentRound==0` | 剩余 > 40 种 | 剩余 ≤ 40 种 |
| **每种推多少** | 1 箱 | 1 箱 | **还需要的全部框数** |
| **总量控制** | 无 | ≤ 40 种 × 1 箱 | `在途 + 新推 ≤ 40` 框 |
| **选料依据** | 全部入选 | 前 40 种（按排序） | 按排序贪心塞满空框 |
| **sent_times 增量** | +1 | +1 | **+actualBoxes（多箱）** |
| **inFlight 增量** | +1 | +1 | **+actualBoxes（多箱）** |
| **典型 totalInFlight** | 45 | 80~180 | **≤ 40**（硬性约束） |
| **Phase A 释放** | — | 不变 | 不变 |

---

## 五、核心字段关系（始终严格正确）

```
实体表: wms_pick_task_entry（定时领料 + 一键领料 共用）

sent_times      本地推 WMS 的次数（每推 1 箱 +1，relax 模式一次推 5 箱就 +5）
released_times  WMS 料架释放成功次数（料架上 有→无 每箱 +1）
confirmed_times WMS 料架上料确认次数（料架上 无→有 每箱 +1）
inFlightTimes   计算属性 = sent_times − released_times

sentQty         真正发出去的数量（由 released_times 闭环驱动）
                每释放 1 箱 → sentQty += perBoxQty

保证式（永远成立）:
  inFlightTimes = sent_times − released_times    ✅ 严格等式
  sentQty ≈ released_times × perBoxQty           ✅ 近似（最后不足 1 箱时有小误差）
```

### 字段联动图示

```
推 WMS 5 箱（relax 模式）:
  sent_times    += 5
  inFlightTimes += 5
  sentQty       不变！（等 WMS 领走才算）

Phase A 料架释放 3 箱:
  sentQty       += perBoxQty × 3
  released_times += 3
  inFlightTimes -= 3

最终状态:
  sent_times    +5
  released_times +3
  inFlightTimes = +2（还有 2 箱在 WMS 那边没领走）
  sentQty       = 3 × perBoxQty（已发出去的 3 箱）
```

---

## 六、relax 动态分配公式（伪代码）

```java
// Phase C relax 模式
int freeBoxes = maxRoundMaterials - totalInFlight;  // 当前剩余空框
if (freeBoxes < 0) freeBoxes = 0;

// 1. 算每种物料还需要几框
Map<String, Integer> needBoxes = new LinkedHashMap<>();
for (e : candidates) {
    int nb = ceil((e.totalQty - e.sentQty) / e.perBoxQty);
    needBoxes.put(e.materialCode, max(1, nb));
}

// 2. 按排序顺序（inFlight↑ → sent_times↑ → current_round↑）贪心分配
for (e : sortedCandidates) {
    if (freeBoxes <= 0) break;
    
    int canTake = min(needBoxes.get(e.materialCode), freeBoxes);
    
    roundQty[e]     = perBoxQty × canTake（最后截断到 left）
    sent_times[e]  += actualBoxes
    inFlight[e]    += actualBoxes
    freeBoxes      -= actualBoxes;
}
```

### 分配举例

```
maxRoundMaterials = 40
totalInFlight     = 10
freeBoxes         = 40 - 10 = 30

剩余 3 种:
  A: 需 15 框
  B: 需 10 框
  C: 需 5 框

排序后贪心:
  A: canTake = min(15, 30) = 15 → freeBoxes = 15
  B: canTake = min(10, 15) = 10 → freeBoxes = 5
  C: canTake = min(5,  5)  = 5  → freeBoxes = 0

结果: A 发 15 箱, B 发 10 箱, C 发 5 箱 = 30 箱刚好用完
```

---

## 七、流程合理性自检

| 检查点 | 结论 | 说明 |
|--------|------|------|
| 首轮会不会爆货架 | ✅ 不会 | WMS 还没上料，料架全空 |
| 在途会不会无限涨 | ✅ 不会 | Phase A 持续释放；到 relax 阶段硬卡 ≤ 40 |
| relax 会不会超 40 框 | ✅ 不会 | freeBoxes 严格卡 `在途+新推 ≤ 40` |
| 轮询是否均衡 | ✅ 均衡 | sent_times 少的优先 |
| 发料数是否准确 | ✅ 准确 | sentQty 完全由 WMS 料架释放闭环驱动 |
| 会不会有物料被饿死 | ✅ 不会 | inFlight 少的优先；freeBoxes 够了就会分到 |
| 接口挂了怎么办 | ✅ 降级 | Phase A 跳过；Phase C 正常模式继续推 |
| relax 会不会一口气推太多 | ✅ 不会 | 仍然受 `在途 + 新推 ≤ 40` 硬约束 |

---

## 八、潜在风险 & 待优化

| # | 风险 | 影响 | 建议 |
|---|------|------|------|
| 1 | **WMS 不上料**（料架接口一直返回空） | Phase A 无事发生 → sentQty 永远不增加 | 加超时机制：连续 N 轮 confirmed_times=0 且 sent_times>5 则告警 |
| 2 | **WMS 上料但不领走**（料架一直有货） | inFlight 一直涨 → 提前进入 relax 模式但 freeBoxes=0，永远推不出去 | 加在途硬上限（比如 200），超过暂停 |
| 3 | **料架接口延迟**（WMS 有 1 轮缓存） | 释放确认延迟 1~2 轮 | 可接受，最坏 relax 多等一轮 |
| 4 | **45 种全只推 1 框** | 首轮 45 种推完 + WMS 领完 → 第 2 轮 complete | ✅ 流程自然结束 |
| 5 | **WMS 接口超时但实际成功** | 本地标记 failed → 任务停止 | Phase E 已做兜底（failed 自动停止，可手动重启） |
| 6 | **relax 模式下 freeBoxes 算出来比 needBoxes 小** | 部分物料这轮没分到，等下一轮 WMS 释放后 freeBoxes 增加自然轮到 | ✅ 设计如此 |

---

## 九、配置项一览

```yaml
# 定时领料 / 一键领料 共用同一套逻辑
pick-task:
  enabled: true
  shelf-url: http://192.168.15.20:8081/prod-api/api/upperSystem/shelf/inventory
  shelf-timeout-ms: 3000
  max-round-materials: 40    # 既是首轮/正常模式的种类上限，也是 relax 模式的框位上限

one-click-pick:
  enabled: true
  shelf-url: http://192.168.15.20:8081/prod-api/api/upperSystem/shelf/inventory
  shelf-timeout-ms: 3000
  max-round-materials: 40
```

### 唯一核心参数：`max-round-materials`

| 值 | 含义 |
|----|------|
| 40 | 5 货架 × 40 框 上限（当前配置） |
| 60 | 如果 WMS 升级到 7.5 货架 × 60 框 |
| 20 | 测试环境模拟小规模 |

**不再需要**：`relax-threshold`（用 candidates.size() ≤ max-round-materials 自动判断）、`relax-multiplier`（改为动态框位分配）。
