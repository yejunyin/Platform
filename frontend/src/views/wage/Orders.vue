<template>
  <div class="page-container">
    <div class="card-box">
      <div class="industrial-panel__title">
        <span class="industrial-panel__bar" />
        生产任务单
        <span class="crud-tip">数据源：金蝶《生产用料清单》(PRD_PPBOM) 取 work 单号 → 查询 PRD_MO 工单头；列表仅显示在下方用料清单有对应 work 单号的任务单</span>
      </div>

      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="任务单号/产品/子物料" clearable class="ipt kw" @keyup.enter="reload" />
        <el-select v-model="query.workshop" placeholder="生产车间" clearable class="ipt">
          <el-option v-for="w in workshops" :key="w" :label="w" :value="w" />
        </el-select>
        <el-select v-model="query.groupId" placeholder="加工组别" clearable class="ipt">
          <el-option v-for="g in groups" :key="g.id" :label="g.groupName" :value="g.id" />
        </el-select>
        <el-select v-model="query.bizStatus" placeholder="业务状态" clearable class="ipt st">
          <el-option label="计划" value="计划" />
          <el-option label="开工" value="开工" />
          <el-option label="完工" value="完工" />
        </el-select>
        <el-select v-model="query.settleStatus" placeholder="结算状态" clearable class="ipt st">
          <el-option label="待结算" value="待结算" />
          <el-option label="已提交" value="已提交" />
          <el-option label="已结算" value="已结算" />
        </el-select>
        <el-button class="btn-primary" @click="reload">查询</el-button>
      </div>

      <el-table
        :data="rows"
        v-loading="loading"
        border
        stripe
        class="data-table order-table"
        highlight-current-row
        @row-click="selectTask"
      >
        <el-table-column label="生产任务单号" prop="taskNo" width="180" fixed />
        <el-table-column label="产品名称" prop="productName" min-width="200" show-overflow-tooltip />
        <el-table-column label="物料代码" prop="materialCode" width="140">
          <template #default="{ row }">{{ row.materialCode || '—' }}</template>
        </el-table-column>
        <el-table-column label="产品数量" prop="productQty" width="100" align="right">
          <template #default="{ row }">{{ row.productQty ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="工序" prop="processName" width="80">
          <template #default="{ row }">{{ row.processName || '—' }}</template>
        </el-table-column>
        <el-table-column label="生产车间" prop="workshop" width="110">
          <template #default="{ row }">{{ row.workshop || '—' }}</template>
        </el-table-column>
        <el-table-column label="加工组别" prop="groupName" width="120">
          <template #default="{ row }">{{ row.groupName || '—' }}</template>
        </el-table-column>
        <el-table-column label="业务状态" width="90">
          <template #default="{ row }">
            <span :class="['status-tag', bizClass(row.bizStatus)]">{{ row.bizStatus }}</span>
          </template>
        </el-table-column>
        <el-table-column label="开工时间" prop="startTime" width="160">
          <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="完工时间" prop="finishTime" width="160">
          <template #default="{ row }">{{ fmtTime(row.finishTime) }}</template>
        </el-table-column>
        <el-table-column label="完工入库数" prop="stockInQty" width="100" align="right">
          <template #default="{ row }">{{ row.stockInQty ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="结算状态" prop="settleStatus" width="90" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.bizStatus !== '计划'" @click.stop="openStart(row as WageOrder)">开工提交</el-button>
            <el-button link type="success" :disabled="row.bizStatus !== '开工'" @click.stop="openDone(row as WageOrder)">确认完工</el-button>
            <el-button link type="warning" :disabled="row.bizStatus === '完工'" @click.stop="openGroup(row as WageOrder)">改组别</el-button>
            <el-button link type="danger" @click.stop="onDelete(row as WageOrder)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page.pageNum"
          v-model:page-size="page.pageSize"
          :page-sizes="[20, 50, 100]"
          :total="page.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadOrders"
          @current-change="loadOrders"
        />
      </div>

      <!-- 当前任务单的用料清单 -->
      <div class="sub-panel">
        <div class="sub-title">
          生产用料清单
          <span v-if="current" class="sub-key">当前任务单：{{ current.taskNo }}</span>
          <span v-else class="sub-key">点击上方任务单查看用料明细</span>
        </div>
        <el-table :data="materials" v-loading="matLoading" border stripe size="small" class="data-table">
          <el-table-column label="物料代码" prop="materialCode" width="160" />
          <el-table-column label="物料名称" prop="materialName" min-width="240" show-overflow-tooltip />
          <el-table-column label="单位" prop="unit" width="90">
            <template #default="{ row }">{{ row.unit || '—' }}</template>
          </el-table-column>
          <el-table-column label="数量" prop="qty" width="120" align="right" />
          <el-table-column label="业务状态" prop="bizStatus" width="100" />
        </el-table>
      </div>
    </div>

    <!-- 开工提交 -->
    <el-dialog v-model="startDialog.visible" title="开工提交" width="440px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="任务单号">
          <el-input :model-value="startDialog.taskNo" disabled />
        </el-form-item>
        <el-form-item label="加工组别" required>
          <el-select v-model="startDialog.groupId" placeholder="必须选择加工组别" class="full">
            <el-option v-for="g in groups" :key="g.id" :label="g.groupName" :value="g.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="confirmStart">确认开工</el-button>
      </template>
    </el-dialog>

    <!-- 确认完工 -->
    <el-dialog v-model="doneDialog.visible" title="确认完工" width="440px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="任务单号">
          <el-input :model-value="doneDialog.taskNo" disabled />
        </el-form-item>
        <el-form-item label="完工入库数">
          <el-input-number v-model="doneDialog.stockInQty" :min="0" :step="1" class="full" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="doneDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="confirmDone">确认完工</el-button>
      </template>
    </el-dialog>

    <!-- 修改组别 -->
    <el-dialog v-model="groupDialog.visible" title="修改加工组别" width="440px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="任务单号">
          <el-input :model-value="groupDialog.taskNo" disabled />
        </el-form-item>
        <el-form-item label="加工组别" required>
          <el-select v-model="groupDialog.groupId" placeholder="选择加工组别" class="full">
            <el-option v-for="g in groups" :key="g.id" :label="g.groupName" :value="g.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="confirmGroup">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  pageOrders,
  syncOrdersFromMaterials,
  startOrder,
  doneOrder,
  changeOrderGroup,
  deleteOrder,
  listMaterials,
  listWorkshops,
} from '@/api/wage/orders'
import { listGroups } from '@/api/wage/groups'
import type { WageGroup, WageOrder, WageMaterial } from '@/api/wage/types'

const userStore = useUserStore()

onMounted(async () => {
  // 先从本地用料清单 (wage_material) 里的 task_no → 调金蝶 PRD_MO 同步工单头到 wage_order
  // 确保任务单列表始终从最新的用料清单派生
  try {
    await syncOrdersFromMaterials()
  } catch (e) {
    // 同步失败不阻断页面加载，列表会在已有的 wage_order 数据上展示
    console.warn('syncOrdersFromMaterials failed:', e)
  }
  // 三个本地查询并行，秒出
  await Promise.all([
    loadGroups(),
    loadWorkshopOptions(),
    loadOrders(),
  ])
})

const loading = ref(false)
const acting = ref(false)
const matLoading = ref(false)
const rows = ref<WageOrder[]>([])
const groups = ref<WageGroup[]>([])
const workshops = ref<string[]>([])
const materials = ref<WageMaterial[]>([])
const current = ref<WageOrder | null>(null)

const query = reactive({
  keyword: '',
  workshop: '',
  groupId: undefined as string | undefined,
  bizStatus: '',
  settleStatus: '',
})
const page = reactive({ pageNum: 1, pageSize: 20, total: 0 })

const startDialog = reactive({ visible: false, id: '' as string, taskNo: '', groupId: undefined as string | undefined })
const doneDialog = reactive({ visible: false, id: '' as string, taskNo: '', stockInQty: 0 })
const groupDialog = reactive({ visible: false, id: '' as string, taskNo: '', groupId: undefined as string | undefined })

function fmtTime(t?: string) {
  if (!t) return '—'
  return t.replace('T', ' ').slice(0, 16)
}

function bizClass(s: string) {
  if (s === '开工') return 'biz-run'
  if (s === '完工') return 'biz-done'
  return 'biz-plan'
}

async function loadGroups() {
  const { data } = await listGroups()
  groups.value = data || []
}

async function loadWorkshopOptions() {
  const { data } = await listWorkshops()
  workshops.value = data || []
}

async function loadOrders() {
  loading.value = true
  try {
    const { data } = await pageOrders({
      keyword: query.keyword || undefined,
      workshop: query.workshop || undefined,
      groupId: query.groupId,
      bizStatus: query.bizStatus || undefined,
      settleStatus: query.settleStatus || undefined,
      pageNum: page.pageNum,
      pageSize: page.pageSize,
    })
    rows.value = data?.list || []
    page.total = data?.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  page.pageNum = 1
  loadOrders()
}

async function selectTask(row: WageOrder) {
  current.value = row
  matLoading.value = true
  try {
    const { data } = await listMaterials({ taskNo: row.taskNo })
    materials.value = data || []
  } finally {
    matLoading.value = false
  }
}

function openStart(row: WageOrder) {
  startDialog.id = row.id
  startDialog.taskNo = row.taskNo
  startDialog.groupId = row.groupId
  startDialog.visible = true
}

async function confirmStart() {
  if (!startDialog.groupId) {
    ElMessage.warning('请选择加工组别')
    return
  }
  acting.value = true
  try {
    await startOrder(startDialog.id, {
      groupId: startDialog.groupId,
      confirmerNo: userStore.userInfo.username,
      confirmerName: userStore.userInfo.realName,
    })
    ElMessage.success('开工提交成功，用料清单下发中')
    startDialog.visible = false
    loadOrders()
  } finally {
    acting.value = false
  }
}

function openDone(row: WageOrder) {
  doneDialog.id = row.id
  doneDialog.taskNo = row.taskNo
  doneDialog.stockInQty = Number(row.stockInQty ?? row.productQty ?? 0)
  doneDialog.visible = true
}

async function confirmDone() {
  acting.value = true
  try {
    await doneOrder(doneDialog.id, {
      stockInQty: doneDialog.stockInQty,
      finishUser: userStore.userInfo.realName,
    })
    ElMessage.success('已确认完工')
    doneDialog.visible = false
    loadOrders()
  } finally {
    acting.value = false
  }
}

function openGroup(row: WageOrder) {
  groupDialog.id = row.id
  groupDialog.taskNo = row.taskNo
  groupDialog.groupId = row.groupId
  groupDialog.visible = true
}

async function confirmGroup() {
  if (!groupDialog.groupId) {
    ElMessage.warning('请选择加工组别')
    return
  }
  acting.value = true
  try {
    await changeOrderGroup(groupDialog.id, groupDialog.groupId)
    ElMessage.success('修改成功')
    groupDialog.visible = false
    loadOrders()
  } finally {
    acting.value = false
  }
}

async function onDelete(row: WageOrder) {
  await ElMessageBox.confirm(`确认删除任务单「${row.taskNo}」？该操作不影响 MES 原始数据。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
  })
  await deleteOrder(row.id)
  ElMessage.success('已删除')
  if (current.value?.id === row.id) {
    current.value = null
    materials.value = []
  }
  reload()
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
.kw { width: 220px; }
.full { width: 100%; }
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.sub-panel {
  margin-top: 18px;
  border-top: 1px dashed $border-color;
  padding-top: 12px;
}
.sub-title {
  font-size: 14px;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 10px;
}
.sub-key {
  margin-left: 10px;
  font-size: 12px;
  color: $text-secondary;
  font-weight: normal;
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
// 操作列 / 生产任务单号列 fixed（sticky 实现）层级与背景修正
// Element Plus stripe/current-row 背景加在 tr 上，fixed 列 td 必须用**完全不透明**背景
// 才能彻底遮挡横向滚动过来的中间行内容，否则会出现文字/颜色穿透
.order-table {
  // ========= z-index =========
  :deep(td.el-table-fixed-column--left),
  :deep(th.el-table-fixed-column--left) { z-index: 4 !important; }
  :deep(td.el-table-fixed-column--right),
  :deep(th.el-table-fixed-column--right) { z-index: 5 !important; }

  // ========= 表头 fixed 列 =========
  :deep(th.el-table-fixed-column--left),
  :deep(th.el-table-fixed-column--right) {
    background: linear-gradient(180deg, #121e2e 0%, #0c1522 100%) !important;
    background-clip: padding-box !important;
  }

  // ========= body fixed 列（行背景必须不透明才能遮挡 stripe/current-row） =========
  :deep(tbody td.el-table-fixed-column--left),
  :deep(tbody td.el-table-fixed-column--right) {
    background-color: #131c28 !important; // 与卡片底色完全一致
    background-clip: padding-box !important;
  }
  // stripe 斑马纹行 fixed 列
  :deep(tbody tr.el-table__row--striped td.el-table-fixed-column--left),
  :deep(tbody tr.el-table__row--striped td.el-table-fixed-column--right) {
    background-color: #172230 !important; // 比卡片底稍亮，与 stripe 一致但固定列独立不透明
  }
  // current-row 选中行 fixed 列（选中底色独立覆盖）
  :deep(tbody tr.current-row td.el-table-fixed-column--left),
  :deep(tbody tr.current-row td.el-table-fixed-column--right) {
    background-color: rgba(26,179,255,0.10) !important;
  }

  // ========= 固定列边界细分割线 =========
  :deep(tbody td.el-table-fixed-column--left)  { border-right: 1px solid rgba(255,255,255,0.08); }
  :deep(tbody td.el-table-fixed-column--right) { border-left:  1px solid rgba(255,255,255,0.08); }
}
</style>
