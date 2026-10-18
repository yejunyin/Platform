<template>
  <div class="page-container">
    <div class="card-box">
      <div class="industrial-panel__title">
        <span class="industrial-panel__bar" />
        工资管理
        <span class="crud-tip">按「工资月份 + 工号」实时汇总计件明细，审核通过后生成工资表记录（WG+月份+工号）</span>
      </div>

      <div class="toolbar">
        <el-date-picker
          v-model="query.month"
          type="month"
          value-format="YYYY-MM"
          placeholder="工资月份"
          class="ipt month"
          clearable
        />
        <el-input v-model="query.keyword" placeholder="姓名/工号/小组/工种" clearable class="ipt kw" @keyup.enter="load" />
        <el-select v-model="query.auditStatus" placeholder="审核状态" clearable class="ipt">
          <el-option label="未生成" value="未生成" />
          <el-option label="待审核" value="待审核" />
          <el-option label="已审核" value="已审核" />
          <el-option label="已驳回" value="已驳回" />
        </el-select>
        <el-button class="btn-primary" @click="load">查询</el-button>
        <el-button class="btn-primary" :loading="acting" @click="onBatchAudit">批量审核通过</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe class="data-table" show-summary :summary-method="summary">
        <el-table-column label="工资月份" prop="wageMonth" width="110" />
        <el-table-column label="员工姓名" prop="staffName" width="110" />
        <el-table-column label="工号" prop="staffNo" width="110" />
        <el-table-column label="小组" prop="groupName" min-width="140">
          <template #default="{ row }">{{ row.groupName || '—' }}</template>
        </el-table-column>
        <el-table-column label="工种" prop="jobType" width="90">
          <template #default="{ row }">{{ row.jobType || '—' }}</template>
        </el-table-column>
        <el-table-column label="明细条数" prop="detailCount" width="100" align="right" />
        <el-table-column label="计件金额" prop="pieceAmount" width="140" align="right">
          <template #default="{ row }">
            <span class="amount">{{ fmt(row.pieceAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="110">
          <template #default="{ row }">
            <span :class="['status-tag', auditClass(row.auditStatus)]">{{ row.auditStatus }}</span>
          </template>
        </el-table-column>
        <el-table-column label="工资单号" prop="wageNo" width="200" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row as WageSummary)">查看明细</el-button>
            <el-button
              link
              type="success"
              :disabled="row.auditStatus === '已审核'"
              @click="onAudit(row as WageSummary, true)"
            >审核</el-button>
            <el-button
              link
              type="danger"
              :disabled="row.auditStatus === '已审核'"
              @click="onAudit(row as WageSummary, false)"
            >驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { listSummary, auditOne, auditBatch } from '@/api/wage/records'
import type { WageSummary } from '@/api/wage/types'

const router = useRouter()
const userStore = useUserStore()
const auditor = userStore.userInfo.realName || userStore.userInfo.username

onMounted(load)

const loading = ref(false)
const acting = ref(false)
const rows = ref<WageSummary[]>([])
const query = reactive({ month: '', keyword: '', auditStatus: '' })

function fmt(v?: number) {
  if (v === null || v === undefined) return '—'
  return Number(v).toFixed(2)
}

function auditClass(s: string) {
  if (s === '已审核') return 'aud-ok'
  if (s === '已驳回') return 'aud-reject'
  if (s === '待审核') return 'aud-wait'
  return 'aud-none'
}

function summary({ columns }: { columns: unknown[] }) {
  const sums: string[] = []
  columns.forEach((_, idx) => {
    if (idx === 0) {
      sums[idx] = '合计'
    } else {
      sums[idx] = ''
    }
  })
  // 计件金额列为第 6 列（索引随列顺序固定）
  const amountIdx = rows.value.length ? 6 : -1
  if (amountIdx >= 0) {
    sums[amountIdx] = rows.value.reduce((s, r) => s + Number(r.pieceAmount || 0), 0).toFixed(2)
  }
  return sums
}

async function load() {
  loading.value = true
  try {
    const { data } = await listSummary({
      month: query.month || undefined,
      keyword: query.keyword || undefined,
      auditStatus: query.auditStatus || undefined,
    })
    rows.value = data || []
  } finally {
    loading.value = false
  }
}

function viewDetail(row: WageSummary) {
  router.push({ name: 'WagePayStat', query: { month: row.wageMonth, staffNo: row.staffNo } })
}

async function onAudit(row: WageSummary, approved: boolean) {
  if (approved) {
    await ElMessageBox.confirm(
      `确认审核通过「${row.staffName}（${row.staffNo}）」${row.wageMonth} 工资 ¥${fmt(row.pieceAmount)}？`,
      '审核确认',
      { type: 'warning', confirmButtonText: '确认通过', cancelButtonText: '取消' },
    )
  } else {
    await ElMessageBox.confirm(
      `确认驳回「${row.staffName}（${row.staffNo}）」${row.wageMonth} 工资？`,
      '驳回确认',
      { type: 'warning', confirmButtonText: '确认驳回', cancelButtonText: '取消' },
    )
  }
  await auditOne({ month: row.wageMonth, staffNo: row.staffNo, approved, auditor })
  ElMessage.success(approved ? '已审核通过' : '已驳回')
  load()
}

async function onBatchAudit() {
  await ElMessageBox.confirm('将对当前筛选范围内所有非「已审核」记录批量审核通过，确认继续？', '批量审核', {
    type: 'warning',
    confirmButtonText: '确认批量审核',
    cancelButtonText: '取消',
  })
  acting.value = true
  try {
    const { data } = await auditBatch({
      month: query.month || undefined,
      keyword: query.keyword || undefined,
      auditStatus: query.auditStatus || undefined,
      auditor,
    })
    ElMessage.success(`已审核 ${data?.count ?? 0} 条`)
    load()
  } finally {
    acting.value = false
  }
}
</script>

<style scoped lang="scss">
.crud-tip {
  margin-left: 12px;
  font-size: 12px;
  color: $text-secondary;
  font-weight: normal;
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.ipt { width: 160px; }
.kw { width: 220px; }
.amount {
  color: $warning-color;
  font-weight: 700;
}
.status-tag {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  border: 1px solid;
}
.aud-ok {
  color: $success-color;
  border-color: $success-color;
  background: rgba(0, 255, 148, 0.08);
}
.aud-reject {
  color: $danger-color;
  border-color: $danger-color;
  background: rgba(255, 61, 90, 0.08);
}
.aud-wait {
  color: $warning-color;
  border-color: $warning-color;
  background: rgba(255, 176, 32, 0.08);
}
.aud-none {
  color: $text-secondary;
  border-color: $border-light;
}
</style>
