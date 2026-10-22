<template>
  <div class="page-container">
    <div class="card-box">
      <div class="industrial-panel__title">
        <span class="industrial-panel__bar" />
        计件复核
        <span class="crud-tip">明细按「任务单 × 用料 × 成员」自动生成，工资月份按开工时间归集；重算为全量删旧重建</span>
      </div>

      <div class="toolbar">
        <el-date-picker
          v-model="query.month"
          type="month"
          value-format="YYYY-MM"
          placeholder="工资月份"
          class="ipt month"
          :clearable="true"
        />
        <el-select v-model="query.groupId" placeholder="小组" clearable class="ipt">
          <el-option v-for="g in groups" :key="g.id" :label="g.groupName" :value="g.id" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="姓名/工号/单号/工种/物料" clearable class="ipt kw" @keyup.enter="load" />
        <el-button class="btn-primary" @click="load">查询</el-button>
        <el-button class="btn-primary" :loading="recalcing" @click="onRecalc">重新计算</el-button>
        <el-button class="btn-primary" :disabled="selected.size === 0" @click="openBatch">
          批量录入月份<span v-if="selected.size">（{{ selected.size }}）</span>
        </el-button>
      </div>

      <el-table
        :data="pageRows"
        v-loading="loading"
        border
        stripe
        size="small"
        class="data-table wide"
        row-key="id"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="42" reserve-selection />
        <el-table-column label="工资月份" prop="wageMonth" width="90" fixed />
        <el-table-column label="生产任务单号" prop="taskNo" width="150" fixed />
        <el-table-column label="小组" prop="groupName" width="110">
          <template #default="{ row }">{{ row.groupName || '—' }}</template>
        </el-table-column>
        <el-table-column label="员工姓名" prop="staffName" width="90" fixed />
        <el-table-column label="工号" prop="staffNo" width="90" />
        <el-table-column label="工种" prop="jobType" width="70">
          <template #default="{ row }">{{ row.jobType || '—' }}</template>
        </el-table-column>
        <el-table-column label="品名" prop="productName" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.productName || '—' }}</template>
        </el-table-column>
        <el-table-column label="单位" prop="unit" width="60">
          <template #default="{ row }">{{ row.unit || '—' }}</template>
        </el-table-column>
        <el-table-column label="工价" prop="workPrice" width="90" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.workPrice, 4) }}</template>
        </el-table-column>
        <el-table-column label="数量" prop="qty" width="90" align="right" sortable>
          <template #default="{ row }">{{ fmt(row.qty, 2) }}</template>
        </el-table-column>
        <el-table-column label="个人金额" prop="amount" width="100" align="right" sortable>
          <template #default="{ row }">
            <span class="amount">{{ fmt(row.amount, 2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="开工日期" prop="startTime" width="110" sortable>
          <template #default="{ row }">{{ row.startTime ? row.startTime.slice(0, 10) : '—' }}</template>
        </el-table-column>
        <el-table-column label="工序" prop="processName" width="70">
          <template #default="{ row }">{{ row.processName || '—' }}</template>
        </el-table-column>
        <el-table-column label="物料代码" prop="materialCode" width="130">
          <template #default="{ row }">{{ row.materialCode || '—' }}</template>
        </el-table-column>
        <el-table-column label="子项物料名称" prop="subMaterialName" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.subMaterialName || '—' }}</template>
        </el-table-column>
        <el-table-column label="子项数量" prop="subQty" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.subQty, 2) }}</template>
        </el-table-column>
        <el-table-column label="子项单价" prop="subPrice" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.subPrice, 4) }}</template>
        </el-table-column>
        <el-table-column label="明细金额" prop="detailAmount" width="100" align="right">
          <template #default="{ row }">{{ fmt(row.detailAmount, 2) }}</template>
        </el-table-column>
        <el-table-column label="工单组级金额" prop="groupAmount" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.groupAmount, 2) }}</template>
        </el-table-column>
        <el-table-column label="系数" prop="coef" width="70" align="right">
          <template #default="{ row }">{{ fmt(row.coef, 2) }}</template>
        </el-table-column>
        <el-table-column label="分摊比例" prop="shareRatio" width="80" align="right">
          <template #default="{ row }">{{ row.shareRatio == null ? '—' : (Number(row.shareRatio) * 100).toFixed(2) + '%' }}</template>
        </el-table-column>
        <el-table-column label="录入日期" width="100">
          <template #default="{ row }">{{ row.createTime ? row.createTime.slice(0, 10) : '—' }}</template>
        </el-table-column>
        <el-table-column label="录入人" prop="entryUser" width="90">
          <template #default="{ row }">{{ row.entryUser || '系统' }}</template>
        </el-table-column>
        <el-table-column label="复核人" prop="reviewer" width="90">
          <template #default="{ row }">{{ row.reviewer || '—' }}</template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="140">
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openReview(row as WagePayStat)">复核</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[50, 100, 200]"
          :total="rows.length"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </div>

    <!-- 单条复核 -->
    <el-dialog v-model="reviewDialog.visible" title="计件明细复核" width="460px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="任务单号">
          <el-input :model-value="reviewDialog.taskNo" disabled />
        </el-form-item>
        <el-form-item label="员工">
          <el-input :model-value="`${reviewDialog.staffName}（${reviewDialog.staffNo}）`" disabled />
        </el-form-item>
        <el-form-item label="工资月份" required>
          <el-date-picker v-model="reviewDialog.wageMonth" type="month" value-format="YYYY-MM" placeholder="选择月份" class="full" />
        </el-form-item>
        <el-form-item label="个人金额">
          <el-input-number v-model="reviewDialog.amount" :min="0" :step="0.01" :precision="2" class="full" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="reviewDialog.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="复核人">
          <el-input :model-value="currentName" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="confirmReview">确认复核</el-button>
      </template>
    </el-dialog>

    <!-- 批量录入月份 -->
    <el-dialog v-model="batchDialog.visible" title="批量录入工资月份" width="420px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="已选明细">
          <el-input :model-value="`${selected.size} 条`" disabled />
        </el-form-item>
        <el-form-item label="工资月份" required>
          <el-date-picker v-model="batchDialog.wageMonth" type="month" value-format="YYYY-MM" placeholder="选择月份" class="full" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="batchDialog.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="confirmBatch">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { listPayStat, recalcPayStat, reviewPayStat, batchUpdateMonth } from '@/api/wage/paystat'
import { listGroups } from '@/api/wage/groups'
import type { WageGroup, WagePayStat } from '@/api/wage/types'

const userStore = useUserStore()
const route = useRoute()
const currentName = userStore.userInfo.realName || userStore.userInfo.username

onMounted(() => {
  // 支持从「工资管理-查看明细」带参跳入
  if (route.query.month) query.month = String(route.query.month)
  if (route.query.staffNo) query.keyword = String(route.query.staffNo)
  loadGroups()
  load()
})

const loading = ref(false)
const acting = ref(false)
const recalcing = ref(false)
const rows = ref<WagePayStat[]>([])
const groups = ref<WageGroup[]>([])
const pageNum = ref(1)
const pageSize = ref(50)
const query = reactive({ month: '', groupId: undefined as number | undefined, keyword: '' })

const selected = reactive(new Set<number>())

const pageRows = computed(() => {
  const from = (pageNum.value - 1) * pageSize.value
  return rows.value.slice(from, from + pageSize.value)
})

const reviewDialog = reactive({
  visible: false,
  id: 0,
  taskNo: '',
  staffName: '',
  staffNo: '',
  wageMonth: '',
  amount: 0,
  remark: '',
})

const batchDialog = reactive({ visible: false, wageMonth: '', remark: '' })

function fmt(v?: number | string | null, digits = 2) {
  if (v === null || v === undefined || v === '') return '—'
  return Number(v).toFixed(digits)
}

async function loadGroups() {
  const { data } = await listGroups()
  groups.value = data || []
}

async function load() {
  pageNum.value = 1
  loading.value = true
  try {
    const { data } = await listPayStat({
      month: query.month || undefined,
      groupId: query.groupId,
      keyword: query.keyword || undefined,
    })
    rows.value = data || []
  } finally {
    loading.value = false
  }
}

function onSelectionChange(sel: WagePayStat[]) {
  selected.clear()
  sel.forEach((r) => selected.add(r.id))
}

async function onRecalc() {
  await ElMessageBox.confirm(
    '重新计算将清空全部已生成明细（包括人工复核修改），按当前订单、用料、单价、成员重新生成，确认继续？',
    '重新计算确认',
    { type: 'warning', confirmButtonText: '确认重算', cancelButtonText: '取消' },
  )
  recalcing.value = true
  try {
    const { data } = await recalcPayStat(currentName)
    ElMessage.success(`重新计算完成，生成 ${data?.count ?? 0} 条明细`)
    load()
  } finally {
    recalcing.value = false
  }
}

function openReview(row: WagePayStat) {
  reviewDialog.id = row.id
  reviewDialog.taskNo = row.taskNo
  reviewDialog.staffName = row.staffName
  reviewDialog.staffNo = row.staffNo
  reviewDialog.wageMonth = row.wageMonth
  reviewDialog.amount = Number(row.amount ?? 0)
  reviewDialog.remark = row.remark || ''
  reviewDialog.visible = true
}

async function confirmReview() {
  if (!reviewDialog.wageMonth) {
    ElMessage.warning('请选择工资月份')
    return
  }
  acting.value = true
  try {
    await reviewPayStat(reviewDialog.id, {
      wageMonth: reviewDialog.wageMonth,
      amount: reviewDialog.amount,
      remark: reviewDialog.remark || undefined,
      reviewer: currentName,
    })
    ElMessage.success('复核完成')
    reviewDialog.visible = false
    load()
  } finally {
    acting.value = false
  }
}

function openBatch() {
  batchDialog.wageMonth = query.month || ''
  batchDialog.remark = ''
  batchDialog.visible = true
}

async function confirmBatch() {
  if (!batchDialog.wageMonth) {
    ElMessage.warning('请选择工资月份')
    return
  }
  acting.value = true
  try {
    const { data } = await batchUpdateMonth({
      ids: Array.from(selected),
      wageMonth: batchDialog.wageMonth,
      remark: batchDialog.remark || undefined,
    })
    ElMessage.success(`已更新 ${data?.count ?? 0} 条`)
    batchDialog.visible = false
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
.ipt { width: 150px; }
.month { width: 150px; }
.kw { width: 260px; }
.full { width: 100%; }
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.wide {
  :deep(.el-table__cell) {
    padding: 4px 0;
  }
}
.amount {
  color: $warning-color;
  font-weight: 700;
}
</style>
