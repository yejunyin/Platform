<template>
  <div class="page-container">
    <div class="card-box">
      <div class="industrial-panel__title">
        <span class="industrial-panel__bar" />
        用料清单查询
        <span class="crud-tip">开工提交时由金蝶《生产用料清单》下发，业务状态随任务单联动</span>
      </div>

      <el-tabs v-model="activeTab" class="page-tabs" @tab-change="onTabChange">
        <!-- =============== Tab1：数据库用料清单 =============== -->
        <el-tab-pane label="本地用料清单" name="db">
          <div class="toolbar">
            <el-select
              v-model="dbQuery.taskNo"
              placeholder="任务单号"
              clearable
              filterable
              class="ipt task"
              @change="reloadDb"
            >
              <el-option v-for="t in taskNos" :key="t" :label="t" :value="t" />
            </el-select>
            <el-select v-model="dbQuery.bizStatus" placeholder="业务状态" clearable class="ipt" @change="reloadDb">
              <el-option label="计划" value="计划" />
              <el-option label="开工" value="开工" />
              <el-option label="完工" value="完工" />
            </el-select>
            <el-input
              v-model="dbQuery.keyword"
              placeholder="单号/投料单/物料代码/名称"
              clearable
              class="ipt kw"
              @keyup.enter="reloadDb"
            />
            <el-button class="btn-primary" @click="reloadDb">查询</el-button>
          </div>

          <el-table :data="dbPageRows" v-loading="dbLoading" border stripe class="data-table">
            <el-table-column label="任务单号" prop="taskNo" width="180" />
            <el-table-column label="投料单号" prop="feedNo" width="160">
              <template #default="{ row }">{{ row.feedNo || '—' }}</template>
            </el-table-column>
            <el-table-column label="物料代码" prop="materialCode" width="160" />
            <el-table-column label="物料名称" prop="materialName" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialName || '—' }}</template>
            </el-table-column>
            <el-table-column label="单位" prop="unit" width="90">
              <template #default="{ row }">{{ row.unit || '—' }}</template>
            </el-table-column>
            <el-table-column label="数量" prop="qty" width="120" align="right" />
            <el-table-column label="业务状态" width="100">
              <template #default="{ row }">
                <span :class="['status-tag', bizClass(row.bizStatus)]">{{ row.bizStatus }}</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="dbPageNum"
              v-model:page-size="dbPageSize"
              :page-sizes="[20, 50, 100, 200]"
              :total="dbRows.length"
              layout="total, sizes, prev, pager, next, jumper"
            />
          </div>
        </el-tab-pane>

        <!-- =============== Tab2：金蝶标准接口查询 =============== -->
        <el-tab-pane label="金蝶标准接口查询" name="kd">
          <div class="kd-tip">
            调用金蝶《生产用料清单》(PRD_PPBOM) ExecuteBillQuery，按子项物料名称关键词 + 创建日期范围查询
          </div>

          <div class="toolbar">
            <el-input
              v-model="kdQuery.keyword"
              placeholder="关键词，多个用逗号/分号分隔，如：丝印,移印"
              clearable
              class="ipt kw-lg"
              @keyup.enter="reloadKd"
            />
            <el-date-picker
              v-model="kdStartDate"
              type="date"
              placeholder="起始日期"
              value-format="YYYY-MM-DD"
              class="ipt date"
            />
            <el-button class="btn-primary" :loading="kdLoading" @click="reloadKd">
              查询金蝶
            </el-button>
            <el-button @click="resetKd">重置</el-button>
            <span class="kd-hint" v-if="kdRows.length > 0">共 {{ kdRows.length }} 条</span>

            <el-divider direction="vertical" />
            <el-button
              type="success"
              :disabled="kdRows.length === 0"
              :loading="saveLoading"
              @click="handleSaveAll"
            >
              保存全部到本地用料清单
            </el-button>
            <el-button
              type="success"
              plain
              :disabled="kdSelectedIds.length === 0"
              :loading="saveLoading"
              @click="handleSaveSelected"
            >
              保存选中 ({{ kdSelectedIds.length }})
            </el-button>
          </div>

          <el-table
            ref="kdTableRef"
            :data="kdPageRows"
            v-loading="kdLoading"
            border
            stripe
            height="560"
            class="data-table"
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="48" />
            <el-table-column label="用料清单编号" prop="ppbomBillNo" width="160">
              <template #default="{ row }">{{ row.ppbomBillNo || '—' }}</template>
            </el-table-column>
            <el-table-column label="生产订单号" prop="moBillNo" width="160">
              <template #default="{ row }">{{ row.moBillNo || '—' }}</template>
            </el-table-column>
            <el-table-column label="父项产品物料编码" prop="productCode" width="160">
              <template #default="{ row }">{{ row.productCode || '—' }}</template>
            </el-table-column>
            <el-table-column label="父项产品物料名称" prop="productName" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.productName || '—' }}</template>
            </el-table-column>
            <el-table-column label="子项物料编码" prop="materialCode" width="160">
              <template #default="{ row }">{{ row.materialCode || '—' }}</template>
            </el-table-column>
            <el-table-column label="子项物料名称" prop="materialName" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialName || '—' }}</template>
            </el-table-column>
            <el-table-column label="规格型号" prop="model" width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.model || '—' }}</template>
            </el-table-column>
            <el-table-column label="应发数量" prop="needQty" width="110" align="right" />
            <el-table-column label="单位" prop="unitName" width="80">
              <template #default="{ row }">{{ row.unitName || '—' }}</template>
            </el-table-column>
            <el-table-column label="创建日期" prop="createDate" width="130" />
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="kdPageNum"
              v-model:page-size="kdPageSize"
              :page-sizes="[20, 50, 100, 200]"
              :total="kdRows.length"
              layout="total, sizes, prev, pager, next, jumper"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type ElTable } from 'element-plus'
import { listMaterials, listTaskNos, queryPpbom, savePpbomRows } from '@/api/wage/orders'
import type { PpbomRow, WageMaterial } from '@/api/wage/types'

onMounted(() => {
  loadTaskNos()
  reloadDb()
})

// ============== Tab 状态 ==============
const activeTab = ref<'db' | 'kd'>('db')

function onTabChange(name: string) {
  if (name === 'kd' && kdRows.value.length === 0) {
    reloadKd()
  }
}

// ============== 通用：任务单号下拉 ==============
const taskNos = ref<string[]>([])
async function loadTaskNos() {
  const { data } = await listTaskNos()
  taskNos.value = data || []
}

// ============== Tab1：本地用料清单 ==============
const dbLoading = ref(false)
const dbRows = ref<WageMaterial[]>([])
const dbPageNum = ref(1)
const dbPageSize = ref(20)
const dbQuery = reactive({ taskNo: '', bizStatus: '', keyword: '' })

const dbPageRows = computed(() => {
  const from = (dbPageNum.value - 1) * dbPageSize.value
  return dbRows.value.slice(from, from + dbPageSize.value)
})

async function reloadDb() {
  dbPageNum.value = 1
  dbLoading.value = true
  try {
    const { data } = await listMaterials({
      taskNo: dbQuery.taskNo || undefined,
      bizStatus: dbQuery.bizStatus || undefined,
      keyword: dbQuery.keyword || undefined,
    })
    dbRows.value = data || []
  } finally {
    dbLoading.value = false
  }
}

function bizClass(s: string) {
  if (s === '开工') return 'biz-run'
  if (s === '完工') return 'biz-done'
  return 'biz-plan'
}

// ============== Tab2：金蝶标准接口 ==============
const kdLoading = ref(false)
const kdRows = ref<PpbomRow[]>([])
const kdPageNum = ref(1)
const kdPageSize = ref(20)
const kdSelectedIds = ref<number[]>([])  // 按源数组索引保存选中
const saveLoading = ref(false)

// 默认：关键词"丝印,移印"，起始日期 = 今天 - 1个月
const today = new Date()
const oneMonthAgo = new Date(today.getFullYear(), today.getMonth() - 1, today.getDate())
const fmt = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

const kdQuery = reactive({ keyword: '丝印,移印' })
const kdStartDate = ref<string>(fmt(oneMonthAgo))

const kdPageRows = computed(() => {
  const from = (kdPageNum.value - 1) * kdPageSize.value
  return kdRows.value.slice(from, from + kdPageSize.value)
})

function onSelectionChange(selection: PpbomRow[]) {
  kdSelectedIds.value = selection.map((row) => kdRows.value.indexOf(row))
}

async function reloadKd() {
  kdPageNum.value = 1
  kdLoading.value = true
  try {
    const { data } = await queryPpbom({
      keyword: kdQuery.keyword || undefined,
      startDate: kdStartDate.value || undefined,
    })
    kdRows.value = data || []
    kdSelectedIds.value = []
  } finally {
    kdLoading.value = false
  }
}

async function doSave(rows: PpbomRow[], label: string) {
  if (rows.length === 0) {
    ElMessage.warning('没有可保存的行')
    return
  }
  saveLoading.value = true
  try {
    const { data } = await savePpbomRows(rows)
    ElMessage.success(`${label}：新增 ${data?.added ?? 0} / 接收 ${data?.received ?? rows.length}`)
    // 回到本地 Tab 刷新
    activeTab.value = 'db'
    reloadDb()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message || e))
  } finally {
    saveLoading.value = false
  }
}

function handleSaveAll() {
  doSave(kdRows.value, '保存全部')
}

function handleSaveSelected() {
  const selectedRows = kdSelectedIds.value
    .map((idx) => kdRows.value[idx])
    .filter((r): r is PpbomRow => !!r)
  doSave(selectedRows, `保存选中(${selectedRows.length})`)
}

function resetKd() {
  kdQuery.keyword = '丝印,移印'
  kdStartDate.value = fmt(oneMonthAgo)
  kdRows.value = []
  kdSelectedIds.value = []
  kdPageNum.value = 1
}
</script>

<style scoped lang="scss">
.crud-tip {
  margin-left: 12px;
  font-size: 12px;
  color: $text-secondary;
  font-weight: normal;
}
.page-tabs {
  margin-top: -4px;
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
  align-items: center;
}
.ipt { width: 150px; }
.task { width: 200px; }
.kw { width: 260px; }
.kw-lg { width: 320px; }
.date { width: 180px; }
.kd-tip {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 13px;
  color: $text-secondary;
  margin-bottom: 10px;
}
.kd-hint {
  color: $text-secondary;
  font-size: 13px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.status-tag {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  border: 1px solid;
}
.biz-plan {
  color: $text-secondary;
  border-color: $border-light;
}
.biz-run {
  color: $warning-color;
  border-color: $warning-color;
  background: rgba(255, 176, 32, 0.08);
}
.biz-done {
  color: $success-color;
  border-color: $success-color;
  background: rgba(0, 255, 148, 0.08);
}
</style>
