<template>
  <div class="wms-picking-page">
    <!-- ===== 顶部查询栏 ===== -->
    <el-card class="panel search-panel" shadow="never">
      <div class="search-bar">
        <el-input
          v-model="searchNo"
          placeholder="输入生产订单号（如 WORK26101456359A）"
          clearable
          @keyup.enter="doQuery"
          class="search-input"
        >
          <template #prefix>
            <el-icon><Tickets /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" :loading="querying" @click="doQuery">
          <el-icon><Search /></el-icon>查询用料清单
        </el-button>
        <el-button @click="loadHistory()">历史记录</el-button>
      </div>
    </el-card>

    <!-- ===== 查询结果：金蝶 PRD_PPBOM 子项明细 ===== -->
    <el-card v-if="showResult" class="panel result-panel" shadow="never">
      <template #header>
        <div class="panel-header">
          <div class="panel-header-left">
            <span class="panel-title">
              生产订单 <b>{{ currentProdNo }}</b>
              <template v-if="ppbomRows.length > 0">
                <span class="product-meta">
                  · 产品编码 <b>{{ ppbomRows[0].productCode || '—' }}</b>
                  · 产品名称 <b>{{ ppbomRows[0].productName || '—' }}</b>
                </span>
              </template>
              <el-tag size="small" type="info" style="margin-left:8px">共 {{ filteredPpbomRows.length }}/{{ ppbomRows.length }} 条</el-tag>
            </span>
          </div>
          <div class="panel-header-right">
            <el-switch
              v-model="showOnlySmartStock"
              size="small"
              active-text="仅智能料箱库·有库存"
              inactive-text="显示全部"
              inline-prompt
              style="margin-right:12px"
            />
            <el-input
              v-model="ppbomKeyword"
              placeholder="🔍 物料编码/名称检索"
              clearable
              size="small"
              style="width:220px"
            />
          </div>
        </div>
      </template>

      <el-table
        :data="filteredPpbomRows"
        v-loading="querying"
        border
        stripe
        class="data-table"
        highlight-current-row
      >
        <el-table-column label="子项物料编码" prop="materialCode" width="160" />
        <el-table-column label="子项物料名称" prop="materialName" min-width="220" show-overflow-tooltip />
        <el-table-column label="应发数量" prop="needQty" width="110" align="right">
          <template #default="{ row }">
            <span class="mono">{{ row.needQty?.toFixed(4) ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="叫料已推" width="110" align="right">
          <template #default="{ row }">
            <span class="mono text-pushed">{{ row.pushedQty?.toFixed(4) ?? '0.0000' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="定时承诺" width="110" align="right">
          <template #default="{ row }">
            <span class="mono text-picktask">{{ row.pickTaskTotalQty?.toFixed(4) ?? '0.0000' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="剩余可领" width="120" align="right">
          <template #default="{ row }">
            <span class="mono" :class="remainingClass(row)">
              {{ row.remainingQty?.toFixed(4) ?? row.needQty?.toFixed(4) ?? '—' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="单位" prop="unitName" width="80" />
        <el-table-column label="计划跟踪号" prop="planCode" width="160">
          <template #default="{ row }">
            <span class="mono">{{ row.planCode || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发料仓库" prop="pickStock" width="140">
          <template #default="{ row }">{{ row.pickStock || '—' }}</template>
        </el-table-column>
        <el-table-column label="即时库存" width="110" align="right">
          <template #default="{ row }">
            <span class="mono text-stock">{{ row.immediateStockQty?.toFixed(4) ?? '0.0000' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="!isAdded(row)"
              type="primary"
              size="small"
              plain
              :disabled="Number(row.remainingQty ?? 0) <= 0"
              @click="addToPick(row)"
            >加入领料</el-button>
            <el-tag v-else type="success" size="small">已加入</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ===== 待发送领料单 ===== -->
    <el-card v-if="pickingItems.length > 0" class="panel picking-panel" shadow="never">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">
            待发送领料单
            <el-tag size="small" type="warning" style="margin-left:8px">{{ pickingItems.length }} 行</el-tag>
          </span>
          <el-button type="danger" size="small" plain @click="clearPick">清空全部</el-button>
        </div>
      </template>

      <el-table :data="pickingItems" border stripe class="data-table">
        <el-table-column label="#" type="index" width="50" align="center" />
        <el-table-column label="生产订单号" prop="productionNo" width="180">
          <template #default="{ row }">{{ row.productionNo || currentProdNo }}</template>
        </el-table-column>
        <el-table-column label="子项物料编码" prop="materialCode" width="160" />
        <el-table-column label="子项物料名称" prop="materialName" min-width="220" show-overflow-tooltip />
        <el-table-column label="应发数量" prop="needQty" width="110" align="right">
          <template #default="{ row }">
            <span class="mono">{{ row.needQty?.toFixed(4) ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="叫料已推" width="100" align="right">
          <template #default="{ row }"><span class="mono text-pushed">{{ row.pushedQty?.toFixed(4) ?? '0' }}</span></template>
        </el-table-column>
        <el-table-column label="定时承诺" width="100" align="right">
          <template #default="{ row }"><span class="mono text-picktask">{{ row.pickTaskTotalQty?.toFixed(4) ?? '0' }}</span></template>
        </el-table-column>
        <el-table-column label="剩余可领" width="100" align="right">
          <template #default="{ row }">
            <span class="mono" :class="remainingClass(row)">{{ row.remainingQty?.toFixed(4) ?? row.needQty?.toFixed(4) ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="领料数量" width="130" align="right">
          <template #default="{ row }">
            <el-input-number
              v-model="row._pickQty"
              :min="0"
              :max="Number(row.remainingQty ?? row.needQty ?? 99999)"
              :precision="4"
              size="small"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column label="单位" prop="unitName" width="80" />
        <el-table-column label="计划跟踪号" prop="planCode" width="160">
          <template #default="{ row }">
            <span class="mono">{{ row.planCode || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发料仓库" prop="pickStock" width="140">
          <template #default="{ row }">{{ row.pickStock || '—' }}</template>
        </el-table-column>
        <el-table-column label="即时库存" width="100" align="right">
          <template #default="{ row }">
            <span class="mono text-stock">{{ row.immediateStockQty?.toFixed(4) ?? '0.0000' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ $index }">
            <el-button type="danger" size="small" link @click="removePick($index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 底部发送按钮 -->
      <div class="send-bar">
        <el-button type="success" size="large" :loading="sending" @click="openSendDialog">
          <el-icon><Promotion /></el-icon>发送 WMS
        </el-button>
        <span class="send-hint">点击后会弹窗要求填写「生产线编码 LineCode」，确认后提交到 WMS ProductionPicking 接口</span>
      </div>
    </el-card>

    <!-- ===== 历史记录 ===== -->
    <el-card v-if="showHistory" class="panel history-panel" shadow="never">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">领料单历史</span>
          <el-radio-group v-model="historyStatus" size="small" @change="loadHistory()">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="pending">待发送</el-radio-button>
            <el-radio-button value="sent">已发送</el-radio-button>
            <el-radio-button value="failed">失败</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-table :data="historyList" border stripe class="data-table" v-loading="historyLoading">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 8px 24px 16px 48px">
              <div class="panel-sub" style="margin-bottom: 8px">
                产品编码：{{ row.productCode || '—' }} · 产品名称：{{ row.productName || '—' }}
              </div>
              <el-table :data="row.entries || []" border size="small">
                <el-table-column label="#" type="index" width="50" align="center" />
                <el-table-column label="子项物料编码" prop="materialCode" width="180">
                  <template #default="{ row: e }"><span class="mono">{{ e.materialCode }}</span></template>
                </el-table-column>
                <el-table-column label="子项物料名称" prop="materialName" min-width="240" show-overflow-tooltip />
                <el-table-column label="领料数量" prop="pickedQty" width="110" align="right">
                  <template #default="{ row: e }">
                    <span class="mono">{{ e.pickedQty?.toFixed(4) ?? '—' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="单位" prop="unitCode" width="80" />
                <el-table-column label="计划跟踪号" prop="planCode" width="160">
                  <template #default="{ row: e }"><span class="mono">{{ e.planCode || '—' }}</span></template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="MES单号" prop="billNo" width="240">
          <template #default="{ row }"><span class="mono">{{ row.billNo }}</span></template>
        </el-table-column>
        <el-table-column label="生产订单号" prop="productionNo" width="180" />
        <el-table-column label="生产线" prop="lineCode" width="100" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="WMS任务号" prop="wmsTaskNo" width="180">
          <template #default="{ row }">{{ row.wmsTaskNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="消息" prop="message" min-width="180" show-overflow-tooltip />
        <el-table-column label="创建时间" prop="createTime" width="170" />
      </el-table>
      <el-pagination
        class="pagination"
        v-if="historyTotal > 0"
        background
        layout="prev, pager, next, total"
        :total="historyTotal"
        :page-size="historyPageSize"
        :current-page="historyPageNum"
        @current-change="(page: number) => loadHistory(page)"
      />
    </el-card>

    <!-- ===== 空态 ===== -->
    <el-empty v-if="!showResult && !showHistory && pickingItems.length === 0"
      description="输入生产订单号（如 WORK26101456359A）查询金蝶生产用料清单，点击「加入领料」选好物料后发送到 WMS"
    />

    <!-- ===== 发送对话框 ===== -->
    <el-dialog v-model="sendDialogVisible" title="发送 WMS 领料申请" width="520px">
      <el-form label-width="120px">
        <el-form-item label="生产订单号">
          <el-input :model-value="currentProdNo" disabled />
        </el-form-item>
        <el-form-item label="物料行数">
          <el-input :model-value="String(pickingItems.length)" disabled />
        </el-form-item>
        <el-form-item label="生产线编码" required>
          <el-input
            v-model="sendForm.lineCode"
            placeholder="如 LB-CX-07"
            clearable
          />
        </el-form-item>
        <el-form-item label="组织编码">
          <el-input v-model="sendForm.orgCode" placeholder="102" />
        </el-form-item>
        <el-form-item label="部门编码">
          <el-input v-model="sendForm.deptCode" placeholder="1022016" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="sending" @click="confirmSend">确认发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Tickets, Search, Promotion } from '@element-plus/icons-vue'
import { wmsPickingApi, type PpbomRow, type PickingEntry } from '@/api/wmsPicking'

// ===== 查询 =====
const searchNo = ref('')
const currentProdNo = ref('')
const showResult = ref(false)
const querying = ref(false)
const ppbomRows = ref<PpbomRow[]>([])
const ppbomKeyword = ref('')
const showOnlySmartStock = ref(true)

const filteredPpbomRows = computed(() => {
  const kw = ppbomKeyword.value.trim().toLowerCase()
  let list = kw
    ? ppbomRows.value.filter(
        r => (r.materialCode ?? '').toLowerCase().includes(kw)
          || (r.materialName ?? '').toLowerCase().includes(kw)
      )
    : [...ppbomRows.value]
  if (showOnlySmartStock.value) {
    list = list.filter(
      r => (r.pickStock ?? '').includes('智能料箱')
        && Number(r.immediateStockQty ?? 0) > 0
    )
  }
  // 按物料编码升序排序
  list.sort((a, b) => (a.materialCode ?? '').localeCompare(b.materialCode ?? ''))
  return list
})

async function doQuery() {
  const no = searchNo.value.trim()
  if (!no) {
    ElMessage.warning('请输入生产订单号')
    return
  }
  querying.value = true
  showResult.value = true
  currentProdNo.value = no
  ppbomKeyword.value = ''
  pickingItems.value = []
  try {
    const res = await wmsPickingApi.queryPpbom(no)
    ppbomRows.value = (res as any).data ?? []
    if (ppbomRows.value.length === 0) {
      ElMessage.warning(`金蝶 PRD_PPBOM 未找到 ${no} 的用料明细`)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '查询金蝶失败')
    ppbomRows.value = []
  } finally {
    querying.value = false
  }
}

// ===== 待发送领料 =====
const pickingItems = ref<(PpbomRow & { _pickQty: number })[]>([])

function isAdded(row: PpbomRow) {
  return pickingItems.value.some(
    (p) => p.materialCode === row.materialCode
  )
}
function addToPick(row: PpbomRow) {
  if (isAdded(row)) return
  pickingItems.value.push({ ...row, _pickQty: Number(row.remainingQty ?? row.needQty ?? 0) })
}
function removePick(i: number) {
  pickingItems.value.splice(i, 1)
}
function clearPick() {
  pickingItems.value = []
}

// ===== 发送 WMS =====
const sendDialogVisible = ref(false)
const sending = ref(false)
const sendForm = reactive({
  lineCode: 'LB-CX-',
  orgCode: '102',
  deptCode: '1022016',
})

function openSendDialog() {
  if (pickingItems.value.length === 0) {
    ElMessage.warning('请先加入领料')
    return
  }
  sendForm.lineCode = 'LB-CX-'
  sendDialogVisible.value = true
}

async function confirmSend() {
  if (!sendForm.lineCode.trim()) {
    ElMessage.warning('请填写生产线编码 LineCode')
    return
  }
  sending.value = true
  try {
    const entries: PickingEntry[] = pickingItems.value.map((p, i) => ({
      itemNo: i + 1,
      materialCode: p.materialCode!,
      materialName: p.materialName,
      productCode: p.productCode,
      productName: p.productName,
      quantity: p._pickQty,
      unitCode: p.unitNumber,
      planCode: p.planCode,
    }))
    const res = await wmsPickingApi.push({
      productionNo: currentProdNo.value,
      lineCode: sendForm.lineCode.trim(),
      orgCode: sendForm.orgCode || '102',
      deptCode: sendForm.deptCode || '1022016',
      entries,
    })
    const head: any = (res as any).data
    sendDialogVisible.value = false
    ElMessage.success(`WMS 推送成功 · 单号 ${head?.billNo ?? ''} · 任务号 ${head?.wmsTaskNo ?? ''}`)
    pickingItems.value = []
  } catch (e: any) {
    sendDialogVisible.value = false
    ElMessage.error(e?.message || '推送 WMS 失败')
  } finally {
    sending.value = false
  }
}

// ===== 历史 =====
const showHistory = ref(false)
const historyStatus = ref('')
const historyList = ref<any[]>([])
const historyTotal = ref(0)
const historyPageNum = ref(1)
const historyPageSize = ref(20)
const historyLoading = ref(false)

async function loadHistory(pageNum = 1) {
  showHistory.value = true
  historyPageNum.value = pageNum
  historyLoading.value = true
  try {
    const res = await wmsPickingApi.page(pageNum, historyPageSize.value, historyStatus.value)
    const data: any = (res as any).data
    historyList.value = data?.records ?? []
    historyTotal.value = data?.total ?? 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载历史失败')
  } finally {
    historyLoading.value = false
  }
}

function statusLabel(s?: string) {
  return { pending: '待发送', sent: '已发送', failed: '失败' }[s ?? ''] || s
}
function statusTagType(s?: string): '' | 'success' | 'danger' | 'warning' {
  return ({ pending: 'warning', sent: 'success', failed: 'danger' } as any)[s ?? ''] || ''
}

/** 剩余可领数量的颜色 class：0 或负数 → 红色警告 */
function remainingClass(row: PpbomRow) {
  const rem = Number(row.remainingQty ?? row.needQty ?? 0)
  if (rem <= 0) return 'text-danger'
  if (rem < Number(row.needQty ?? 0)) return 'text-warning'
  return ''
}
</script>

<style lang="scss" scoped>
.wms-picking-page {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.panel { background: transparent; border: none; }
.panel :deep(.el-card__header) { padding: 12px 16px; border-bottom: 1px solid rgba(255,255,255,0.06); }
.panel-header {
  display: flex; align-items: center; justify-content: space-between;
}
.panel-title { font-weight: 600; font-size: 15px; }
.product-meta { color: #606266; font-weight: 400; margin-left: 6px; }
.product-meta b { color: #303133; font-weight: 600; }
.panel-sub { opacity: 0.6; font-size: 12px; }

.search-bar {
  display: flex; gap: 12px; align-items: center;
  padding: 4px 0;
}
.search-input { width: 360px; }

.data-table {
  :deep(.mono) { font-family: Consolas, 'SF Mono', Menlo, monospace; }
}

.text-pushed { color: #409eff; }
.text-picktask { color: #9b59b6; }
.text-danger { color: #f56c6c; font-weight: 600; }
.text-warning { color: #e6a23c; }
.text-stock { color: #67c23a; font-weight: 600; }

.send-bar {
  display: flex; gap: 14px; align-items: center;
  padding-top: 16px;
  border-top: 1px solid rgba(255,255,255,0.06);
  margin-top: 10px;
}
.send-hint { opacity: 0.55; font-size: 12px; }

.pagination {
  display: flex; justify-content: flex-end;
  margin-top: 12px;
}
</style>
