<template>
  <div class="wms-one-click-pick-page">
    <el-tabs v-model="activeTab" class="main-tabs">
      <!-- ==================== Tab 1: 一键领料操作 ==================== -->
      <el-tab-pane label="一键领料操作" name="create">
        <!-- 查询栏 -->
        <el-card class="panel search-panel" shadow="never">
          <div class="search-bar">
            <el-input
              v-model="searchNo"
              placeholder="输入生产订单号（如 WORK26101456359A）"
              clearable
              @keyup.enter="doQuery"
              class="search-input"
            >
              <template #prefix><el-icon><Tickets /></el-icon></template>
            </el-input>
            <el-button type="primary" :loading="querying" @click="doQuery">
              <el-icon><Search /></el-icon>查询用料清单
            </el-button>
          </div>
        </el-card>

        <!-- 用料清单 -->
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
          <el-table :data="filteredPpbomRows" v-loading="querying" border stripe>
            <el-table-column label="子项物料编码" prop="materialCode" width="160" />
            <el-table-column label="子项物料名称" prop="materialName" min-width="220" show-overflow-tooltip />
            <el-table-column label="应发数量" width="110" align="right">
              <template #default="{ row }">
                <span class="mono">{{ row.needQty?.toFixed(4) ?? '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已领数量" width="110" align="right">
              <template #default="{ row }">
                <span class="mono text-picked">{{ row.pickedQty?.toFixed(4) ?? '0.0000' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="叫料已推" width="110" align="right">
              <template #default="{ row }">
                <span class="mono text-pushed">{{ row.pushedQty?.toFixed(4) ?? '0.0000' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已有一键承诺" width="120" align="right">
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
            <el-table-column label="操作" width="200" fixed="right">
              <template #header>
                <el-button
                  type="success" size="small" plain
                  :disabled="batchAddableCount === 0"
                  @click="addAllToTask"
                >
                  <el-icon><Promotion /></el-icon>一键加入全部 ({{ batchAddableCount }})
                </el-button>
              </template>
              <template #default="{ row }">
                <el-button
                  v-if="!isAdded(row)"
                  type="primary" size="small"
                  :disabled="Number(row.remainingQty ?? 0) <= 0"
                  @click="addToTask(row)"
                >加入一键任务</el-button>
                <el-tag v-else type="success" size="small">已加入</el-tag>
                <el-button
                  type="danger"
                  size="small"
                  link
                  style="margin-left:6px"
                  :loading="clearingMaterial === row.materialCode"
                  @click="handleClear(currentProdNo, row.materialCode!, row.materialName)"
                >清除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 一键任务物料列表 -->
        <el-card v-if="taskItems.length > 0" class="panel task-items-panel" shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">
                一键任务物料
                <el-tag size="small" type="warning" style="margin-left:8px">{{ taskItems.length }} 行</el-tag>
              </span>
              <div class="panel-header-right">
                <span class="batch-label">批量工位</span>
                <el-select
                  v-model="batchStation"
                  placeholder="统一改工位"
                  clearable
                  size="small"
                  style="width:140px;margin-right:12px"
                  @change="applyBatchStation"
                >
                  <el-option v-for="s in STATION_OPTIONS" :key="s" :value="s" :label="s" />
                </el-select>
                <el-button type="danger" size="small" plain @click="taskItems = []">清空全部</el-button>
              </div>
            </div>
          </template>
          <el-table :data="taskItems" border stripe>
            <el-table-column label="#" type="index" width="50" align="center" />
            <el-table-column label="物料编码" prop="materialCode" width="160" />
            <el-table-column label="物料名称" prop="materialName" min-width="220" show-overflow-tooltip />
            <el-table-column label="应发数量" width="100" align="right">
              <template #default="{ row }"><span class="mono">{{ row.needQty?.toFixed(2) ?? '—' }}</span></template>
            </el-table-column>
            <el-table-column label="发料总数量" width="150" align="right">
              <template #default="{ row }">
                <el-input-number
                  v-model="row._totalQty"
                  :min="0.01"
                  :max="999999"
                  :precision="2"
                  size="small"
                  controls-position="right"
                  style="width:120px"
                />
              </template>
            </el-table-column>
            <el-table-column label="单次数量" width="150" align="right">
              <template #default="{ row }">
                <el-input-number
                  v-model="row._perBox"
                  :min="0.01"
                  :precision="2"
                  size="small"
                  controls-position="right"
                  style="width:120px"
                />
              </template>
            </el-table-column>
            <el-table-column label="发料次数" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ calcRounds(row._totalQty, row._perBox) }} 次</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="计划跟踪号" prop="planCode" width="160">
              <template #default="{ row }">
                <span class="mono">{{ row.planCode || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="工位" width="130">
              <template #default="{ row }">
                <el-select v-model="row._stationCode" size="small" style="width:110px">
                  <el-option v-for="s in STATION_OPTIONS" :key="s" :value="s" :label="s" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="发料仓库" prop="pickStock" width="140">
              <template #default="{ row }">{{ row.pickStock || '—' }}</template>
            </el-table-column>
            <el-table-column label="即时库存" width="100" align="right">
              <template #default="{ row }">
                <span class="mono text-stock">{{ row.immediateStockQty?.toFixed(2) ?? '0.00' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ $index }">
                <el-button type="danger" size="small" link @click="removeFromTask($index)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 全局参数 + 提交 -->
          <div class="submit-bar">
            <div class="params-row">
              <div class="param-item">
                <span class="param-label">生产线编码 <span class="required">*</span></span>
                <el-input v-model="submitForm.lineCode" placeholder="如 LB-CX-07" style="width:160px" />
              </div>
              <div class="param-item">
                <span class="param-label">间隔时间(分钟) <span class="required">*</span></span>
                <el-input-number v-model="submitForm.intervalSec" :min="1" :precision="0"
                  controls-position="right" style="width:120px" />
              </div>
              <div class="param-item">
                <span class="param-label">组织编码</span>
                <el-input v-model="submitForm.orgCode" placeholder="102" style="width:100px" />
              </div>
              <div class="param-item">
                <span class="param-label">部门编码</span>
                <el-input v-model="submitForm.deptCode" placeholder="1022016" style="width:120px" />
              </div>
            </div>
            <div class="submit-actions">
              <el-button type="primary" size="large" :loading="submitting" @click="confirmCreate">
                <el-icon><Promotion /></el-icon>创建并启动任务
              </el-button>
              <span class="submit-hint">
                总轮次：<b class="total-rounds">{{ maxRounds }}</b> 轮 · 
                任务创建后自动开始循环推送 WMS，所有物料发满自动完成
              </span>
            </div>
          </div>
        </el-card>

        <el-empty
          v-if="!showResult && taskItems.length === 0"
          description="输入生产订单号查询用料清单，选择物料后设置发料总数量/单次数量，创建一键领料任务"
        />
      </el-tab-pane>

      <!-- ==================== Tab 2: 历史记录 ==================== -->
      <el-tab-pane label="历史记录" name="list">
        <el-card class="panel search-panel" shadow="never">
          <div class="search-bar">
            <el-input v-model="listProdNo" placeholder="生产订单号" clearable style="width:260px" />
            <el-select v-model="listStatus" placeholder="全部状态" clearable style="width:140px">
              <el-option value="pending" label="待启动" />
              <el-option value="running" label="运行中" />
              <el-option value="paused" label="已暂停" />
              <el-option value="completed" label="已完成" />
              <el-option value="cancelled" label="已取消" />
            </el-select>
            <el-button type="primary" :loading="listLoading" @click="loadList(1)">
              <el-icon><Search /></el-icon>查询
            </el-button>
          </div>
        </el-card>

        <el-card class="panel task-list-panel" shadow="never">
          <el-table :data="list" border stripe v-loading="listLoading">
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="detail-wrap">
                  <div class="detail-header">
                    <span class="mono">任务单号 {{ row.taskNo }}</span>
                    <span class="detail-sub">
                      单次 {{ row.perBoxQty }} · 间隔 {{ row.intervalSec }}分钟 ·
                      当前 {{ row.currentRound }}/{{ row.totalRounds }} 轮 ·
                      成功 {{ row.successCount }} · 失败 {{ row.failCount }}
                    </span>
                  </div>
                  <!-- 明细表 -->
                  <el-table :data="row.entries || []" border size="small" style="margin-top:10px">
                    <el-table-column label="#" type="index" width="50" align="center" />
                    <el-table-column label="物料编码" prop="materialCode" width="160">
                      <template #default="{ row: e }"><span class="mono">{{ e.materialCode }}</span></template>
                    </el-table-column>
                    <el-table-column label="物料名称" prop="materialName" min-width="180" show-overflow-tooltip />
                    <el-table-column label="计划跟踪号" prop="planCode" width="160">
                      <template #default="{ row: e }"><span class="mono">{{ e.planCode || '—' }}</span></template>
                    </el-table-column>
                    <el-table-column label="发料总数" width="100" align="right">
                      <template #default="{ row: e }"><span class="mono">{{ e.totalQty?.toFixed(2) }}</span></template>
                    </el-table-column>
                    <el-table-column label="单次" width="80" align="right">
                      <template #default="{ row: e }">{{ e.perBoxQty }}</template>
                    </el-table-column>
                    <el-table-column label="已发" width="100" align="right">
                      <template #default="{ row: e }">
                        <span :class="{ 'text-success': e.sentQty >= e.totalQty }">{{ e.sentQty?.toFixed(2) }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="进度" width="180">
                      <template #default="{ row: e }">
                        <el-progress
                          :percentage="Math.min(100, Math.round((e.sentQty / e.totalQty) * 100))"
                          :status="e.sentQty >= e.totalQty ? 'success' : ''"
                        />
                      </template>
                    </el-table-column>
                    <el-table-column label="轮次" width="120" align="center">
                      <template #default="{ row: e }">{{ e.currentRound }}/{{ e.totalRounds }}</template>
                    </el-table-column>
                  </el-table>

                  <!-- 最近日志（前 5 条） -->
                  <div class="log-section">
                    <div class="log-title">最近执行日志</div>
                    <el-table :data="row._logs || []" border size="small">
                      <el-table-column label="时间" prop="createTime" width="170" />
                      <el-table-column label="轮次" prop="roundNo" width="70" align="center" />
                      <el-table-column label="状态" width="80" align="center">
                        <template #default="{ row: l }">
                          <el-tag :type="l.status === 'sent' ? 'success' : l.status === 'failed' ? 'danger' : 'info'" size="small">
                            {{ l.status === 'sent' ? '成功' : l.status === 'failed' ? '失败' : '跳过' }}
                          </el-tag>
                        </template>
                      </el-table-column>
                      <el-table-column label="MES单号" prop="billNo" width="240">
                        <template #default="{ row: l }"><span class="mono">{{ l.billNo }}</span></template>
                      </el-table-column>
                      <el-table-column label="WMS任务号" prop="wmsTaskNo" width="180">
                        <template #default="{ row: l }">{{ l.wmsTaskNo || '—' }}</template>
                      </el-table-column>
                      <el-table-column label="消息" prop="message" min-width="250" show-overflow-tooltip />
                    </el-table>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="任务单号" prop="taskNo" width="240">
              <template #default="{ row }"><span class="mono">{{ row.taskNo }}</span></template>
            </el-table-column>
            <el-table-column label="生产订单号" prop="productionNo" width="180" />
            <el-table-column label="生产线" prop="lineCode" width="100" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进度" width="200">
              <template #default="{ row }">
                <el-progress
                  :percentage="Math.min(100, row.totalRounds ? Math.round((row.currentRound / row.totalRounds) * 100) : 0)"
                  :status="row.status === 'completed' ? 'success' : ''"
                />
                <div class="progress-text">{{ row.currentRound }}/{{ row.totalRounds }} 轮</div>
              </template>
            </el-table-column>
            <el-table-column label="成功/失败" width="100" align="center">
              <template #default="{ row }">
                <span class="text-success">{{ row.successCount }}</span>
                <span style="opacity:.3"> / </span>
                <span class="text-danger">{{ row.failCount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="下次触发" prop="nextFireTime" width="170">
              <template #default="{ row }">{{ row.nextFireTime || '—' }}</template>
            </el-table-column>
            <el-table-column label="最近消息" prop="message" min-width="200" show-overflow-tooltip />
            <el-table-column label="创建时间" prop="createTime" width="170" />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'pending'" type="success" size="small" @click="doStart(row.id)">启动</el-button>
                <el-button v-if="row.status === 'running'" type="warning" size="small" @click="doPause(row.id)">暂停</el-button>
                <el-button v-if="row.status === 'paused'" type="success" size="small" @click="doResume(row.id)">恢复</el-button>
                <el-button
                  v-if="row.status !== 'completed' && row.status !== 'cancelled'"
                  type="danger" size="small" plain
                  @click="doCancel(row.id)"
                >取消</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="pagination"
            v-if="listTotal > 0"
            background
            layout="prev, pager, next, total"
            :total="listTotal"
            :page-size="listPageSize"
            :current-page="listPageNum"
            @current-change="(p) => loadList(p)"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Tickets, Search, Promotion } from '@element-plus/icons-vue'
import { wmsOneClickPickApi, type PpbomRow, type OneClickPickHead, type OneClickPickCreateEntry } from '@/api/wmsOneClickPick'
import { wmsPickingApi } from '@/api/wmsPicking'

// ============== Tab 切换 + Tab2 lazy load ==============
const activeTab = ref('create')
let listLoadedOnce = false
watch(activeTab, (tab) => {
  if (tab === 'list' && !listLoadedOnce) {
    listLoadedOnce = true
    loadList(1)
  }
})

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
  list.sort((a, b) => (a.materialCode ?? '').localeCompare(b.materialCode ?? ''))
  return list
})

async function doQuery() {
  const no = searchNo.value.trim()
  if (!no) { ElMessage.warning('请输入生产订单号'); return }
  querying.value = true
  showResult.value = true
  currentProdNo.value = no
  ppbomKeyword.value = ''
  taskItems.value = []
  try {
    const res = await wmsOneClickPickApi.queryPpbom(no)
    ppbomRows.value = (res as any).data ?? []
    if (ppbomRows.value.length === 0) ElMessage.warning(`金蝶未找到 ${no} 的用料明细`)
  } catch (e: any) {
    ElMessage.error(e?.message || '查询金蝶失败')
    ppbomRows.value = []
  } finally { querying.value = false }
}

// 任务物料
/** WMS ProductionStationCode 可选值 */
const STATION_OPTIONS = ['3SC01', '3LC01', '3LC02', '3LC03', '3LC04']
interface TaskItem extends PpbomRow {
  _totalQty: number
  _perBox: number
  _stationCode: string
}
const taskItems = ref<TaskItem[]>([])

function isAdded(row: PpbomRow) {
  return taskItems.value.some(p => p.materialCode === row.materialCode)
}
function addToTask(row: PpbomRow) {
  if (isAdded(row)) return
  taskItems.value.push({
    ...row,
    _totalQty: Number(row.remainingQty ?? row.needQty ?? 0),
    _perBox: Number(row.boxStandardQty ?? 1),
    _stationCode: '3SC01',
  })
}
function removeFromTask(i: number) { taskItems.value.splice(i, 1) }

// ===== 批量工位 =====
const batchStation = ref<string>('')
function applyBatchStation() {
  if (!batchStation.value) return
  taskItems.value.forEach(i => { i._stationCode = batchStation.value! })
}

// ===== 清除物料本地累计 =====
const clearingMaterial = ref<string | null>(null)

async function handleClear(productionNo: string, materialCode: string, materialName?: string) {
  if (!productionNo || !materialCode) return
  const label = `${materialCode}${materialName ? '（' + materialName + '）' : ''}`
  try {
    await ElMessageBox.confirm(
      `确认清除物料 ${label} 的所有「叫料已推」和「定时承诺」记录？`,
      '二次确认',
      { type: 'warning', confirmButtonText: '确认清除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
  } catch { return }

  clearingMaterial.value = materialCode
  try {
    const res: any = await wmsPickingApi.clearMaterial(productionNo, materialCode)
    const cleared = Number(res?.data ?? 0)
    ElMessage.success(`已清除 ${label} 的 ${cleared} 条本地累计记录`)

    // 只刷新查询结果表里这一行，不做整页 doQuery
    try {
      const sumRes: any = await wmsPickingApi.getMaterialSummary(productionNo, materialCode)
      const d = sumRes?.data ?? {}
      const row = ppbomRows.value.find(r => r.materialCode === materialCode)
      if (row) {
        row.pushedQty = Number(d.pushedQty ?? 0)
        row.pickTaskTotalQty = Number(d.pickTaskTotalQty ?? 0)
        row.pickTaskSentQty = Number(d.pickTaskSentQty ?? 0)
        const need = Number(row.needQty ?? 0)
        row.remainingQty = Math.max(0, need - Number(row.pushedQty) - Number(row.pickTaskTotalQty))
      }
    } catch (e2) { console.warn('清除后刷新单物料汇总失败', e2) }
  } catch (e: any) {
    ElMessage.error(e?.message || '清除失败')
  } finally { clearingMaterial.value = null }
}

/** 批量可加入数量：剩余可领 > 0 且未加入的物料种类数 */
const batchAddableCount = computed(() => {
  let c = 0
  for (const r of filteredPpbomRows.value) {
    if (!isAdded(r) && Number(r.remainingQty ?? 0) > 0) c++
  }
  return c
})

/** 一键加入所有 剩余可领 > 0 的物料 */
function addAllToTask() {
  let added = 0
  for (const r of filteredPpbomRows.value) {
    if (!isAdded(r) && Number(r.remainingQty ?? 0) > 0) {
      addToTask(r)
      added++
    }
  }
  if (added > 0) {
    ElMessage.success(`已一键加入 ${added} 种物料`)
  } else {
    ElMessage.info('没有可加入的物料（剩余可领均为 0 或已加入）')
  }
}

function calcRounds(total: number, perBox: number) {
  if (!perBox || perBox <= 0 || !total || total <= 0) return 0
  return Math.ceil(total / perBox)
}

const maxRounds = computed(() => {
  let m = 0
  for (const t of taskItems.value) {
    const r = calcRounds(t._totalQty, t._perBox)
    if (r > m) m = r
  }
  return m
})

// 全局参数
const submitForm = reactive({
  lineCode: 'LB-CX-',
  intervalSec: 1,
  orgCode: '102',
  deptCode: '1022016',
})
const submitting = ref(false)

async function confirmCreate() {
  if (!submitForm.lineCode.trim()) { ElMessage.warning('请填写生产线编码'); return }
  if (taskItems.value.length === 0) { ElMessage.warning('请先加入一键任务物料'); return }

  submitting.value = true
  try {
    const entries: OneClickPickCreateEntry[] = taskItems.value.map((p) => ({
      materialCode: p.materialCode!,
      materialName: p.materialName,
      productCode: p.productCode,
      productName: p.productName,
      totalQty: p._totalQty,
      perBoxQty: p._perBox,
      unitCode: p.unitNumber,
      planCode: p.planCode,
      productionStationCode: p._stationCode,
      outWarehouseCode: '2010',
      planQty: p.needQty,
    }))
    const res = await wmsOneClickPickApi.create({
      productionNo: currentProdNo.value,
      lineCode: submitForm.lineCode.trim(),
      orgCode: submitForm.orgCode || '102',
      deptCode: submitForm.deptCode || '1022016',
      intervalSec: submitForm.intervalSec,
      entries,
    })
    const head: any = (res as any).data
    ElMessage.success(`任务创建成功并已立即推送第一次 · taskNo=${head.taskNo}`)

    taskItems.value = []
    activeTab.value = 'list'
    loadList(1)
  } catch (e: any) {
    ElMessage.error(e?.message || '创建任务失败')
  } finally {
    submitting.value = false
  }
}

// ============== Tab 2: 任务列表 ==============
const listProdNo = ref('')
const listStatus = ref('')
const list = ref<any[]>([])
const listTotal = ref(0)
const listPageNum = ref(1)
const listPageSize = ref(20)
const listLoading = ref(false)

async function loadList(pageNum = 1) {
  listPageNum.value = pageNum
  listLoading.value = true
  try {
    const res = await wmsOneClickPickApi.page(pageNum, listPageSize.value, {
      status: listStatus.value || undefined,
      productionNo: listProdNo.value.trim() || undefined,
    })
    const data: any = (res as any).data
    list.value = data?.records ?? []
    listTotal.value = data?.total ?? 0

    for (const t of list.value) {
      try {
        const d: any = await wmsOneClickPickApi.detail(t.id)
        t.entries = d?.data?.entries ?? []
      } catch { /* ignore */ }
      try {
        const lg: any = await wmsOneClickPickApi.logs(t.id, 1, 5)
        t._logs = lg?.data?.records ?? []
      } catch { /* ignore */ }
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载任务列表失败')
  } finally { listLoading.value = false }
}

async function doStart(id: number) {
  try {
    await wmsOneClickPickApi.start(id)
    ElMessage.success('任务已启动')
    loadList(listPageNum.value)
  } catch (e: any) { ElMessage.error(e?.message || '启动失败') }
}
async function doPause(id: number) {
  try {
    await wmsOneClickPickApi.pause(id)
    ElMessage.success('任务已暂停')
    loadList(listPageNum.value)
  } catch (e: any) { ElMessage.error(e?.message || '暂停失败') }
}
async function doResume(id: number) {
  try {
    await wmsOneClickPickApi.resume(id)
    ElMessage.success('任务已恢复')
    loadList(listPageNum.value)
  } catch (e: any) { ElMessage.error(e?.message || '恢复失败') }
}
async function doCancel(id: number) {
  try {
    await ElMessageBox.confirm('确认取消该一键任务？取消后不可恢复。', '提示', { type: 'warning' })
    await wmsOneClickPickApi.cancel(id)
    ElMessage.success('任务已取消')
    loadList(listPageNum.value)
  } catch (e: any) { if ((e as any) !== 'cancel') ElMessage.error((e as any)?.message || '取消失败') }
}

function statusLabel(s?: string) {
  const map: Record<string, string> = {
    pending: '待启动', running: '运行中', paused: '已暂停',
    completed: '已完成', cancelled: '已取消', failed: '失败',
  }
  return map[s ?? ''] || s
}
function statusTagType(s?: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, any> = {
    pending: 'warning', running: 'success', paused: 'info',
    completed: 'success', cancelled: 'info', failed: 'danger',
  }
  return map[s ?? ''] || ''
}

/** 剩余可领数量的颜色 class */
function remainingClass(row: PpbomRow) {
  const rem = Number(row.remainingQty ?? row.needQty ?? 0)
  if (rem <= 0) return 'text-danger'
  if (rem < Number(row.needQty ?? 0)) return 'text-warning'
  return ''
}
</script>

<style lang="scss" scoped>
.wms-one-click-pick-page { padding: 12px; display: flex; flex-direction: column; gap: 12px; }
.panel { background: transparent; border: none; }
.panel :deep(.el-card__header) { padding: 12px 16px; border-bottom: 1px solid rgba(255,255,255,0.06); }
.panel-header { display: flex; align-items: center; justify-content: space-between; }
.panel-title { font-weight: 600; font-size: 15px; }
.product-meta { color: #606266; font-weight: 400; margin-left: 6px; }
.product-meta b { color: #303133; font-weight: 600; }
.panel-sub { opacity: 0.6; font-size: 12px; }

.search-bar { display: flex; gap: 12px; align-items: center; padding: 4px 0; flex-wrap: wrap; }
.search-input { width: 360px; }

:deep(.mono) { font-family: Consolas, 'SF Mono', Menlo, monospace; }
.text-success { color: #67c23a; }
.text-danger { color: #f56c6c; font-weight: 600; }
.text-warning { color: #e6a23c; }
.text-pushed { color: #409eff; }
.text-picked { color: #e6a23c; font-weight: 600; }
.text-picktask { color: #9b59b6; }
.text-stock { color: #67c23a; font-weight: 600; }

:deep(.el-button.is-disabled),
:deep(.el-button.is-disabled:hover),
:deep(.el-button.is-disabled:focus) {
  background-color: #c0c4cc !important;
  border-color: #c0c4cc !important;
  color: #fff !important;
}

.submit-bar {
  padding: 20px 16px; margin-top: 10px;
  border-top: 1px solid rgba(255,255,255,0.06);
  display: flex; flex-direction: column; gap: 14px;
}
.params-row { display: flex; gap: 24px; flex-wrap: wrap; align-items: center; }
.param-item { display: flex; gap: 8px; align-items: center; }
.param-label { opacity: 0.7; font-size: 13px; }
.required { color: #f56c6c; }
.submit-actions { display: flex; gap: 14px; align-items: center; }
.submit-hint { opacity: 0.55; font-size: 12px; }
.total-rounds { color: #409eff; font-size: 15px; }

.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }

.detail-wrap { padding: 8px 0; }
.detail-header { display: flex; gap: 20px; align-items: center; margin-bottom: 10px; }
.detail-sub { opacity: 0.6; font-size: 12px; }
.log-section { margin-top: 14px; }
.log-title { font-weight: 600; font-size: 13px; margin-bottom: 6px; opacity: 0.85; }
.progress-text { font-size: 12px; opacity: 0.6; text-align: right; }
</style>
