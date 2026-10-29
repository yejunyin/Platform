<template>
  <div class="page-container">
    <div class="card-box">
      <div class="industrial-panel__title">
        <span class="industrial-panel__bar" />
        生产任务单
        <span class="crud-tip">点击任务单，在下方查看/操作该单的用料明细（每条丝印/移印物料独立维护状态）</span>
      </div>

      <!-- 工具栏（单据头级过滤） -->
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="任务单号/产品/物料代码" clearable class="ipt kw" @keyup.enter="reload" />
        <el-select v-model="query.workshop" placeholder="生产车间" clearable class="ipt">
          <el-option v-for="w in workshops" :key="w" :label="w" :value="w" />
        </el-select>
        <el-button class="btn-primary" @click="reload">查询</el-button>
      </div>

      <!-- ===== 主表：只含单据头 5 列 ===== -->
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
        <el-table-column label="生产车间" prop="workshop" width="110">
          <template #default="{ row }">{{ row.workshop || '—' }}</template>
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

      <!-- ===== 下方面板：用料清单（每条明细带完整状态 + 操作） ===== -->
      <div class="sub-panel" v-if="current">
        <div class="sub-title">
          生产用料清单
          <span class="sub-key">任务单 {{ current.taskNo }} · 共 {{ materials.length }} 条</span>
          <!-- 明细级过滤 -->
          <span class="mat-filters">
            <el-select v-model="matQuery.bizStatus" placeholder="业务状态" clearable class="mat-st">
              <el-option label="计划" value="计划" />
              <el-option label="开工" value="开工" />
              <el-option label="完工" value="完工" />
            </el-select>
            <el-input v-model="matQuery.keyword" placeholder="搜物料" clearable class="mat-kw" />
            <el-button class="btn-primary" size="small" @click="reloadMats">筛选</el-button>
          </span>
        </div>

        <el-table :data="materials" v-loading="matLoading" border stripe size="small" class="data-table">
          <el-table-column label="物料代码" prop="materialCode" width="140" />
          <el-table-column label="工序" prop="processName" width="70">
            <template #default="{ row }">{{ row.processName || '—' }}</template>
          </el-table-column>
          <el-table-column label="物料名称" prop="materialName" min-width="200" show-overflow-tooltip />
          <el-table-column label="单位" prop="unit" width="70">
            <template #default="{ row }">{{ row.unit || '—' }}</template>
          </el-table-column>
          <el-table-column label="数量" prop="qty" width="90" align="right">
            <template #default="{ row }">{{ row.qty ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="加工组别" prop="groupName" width="110">
            <template #default="{ row }">{{ row.groupName || '未分配' }}</template>
          </el-table-column>
          <el-table-column label="业务状态" width="80">
            <template #default="{ row }">
              <span :class="['status-tag', bizClass(row.bizStatus)]">{{ row.bizStatus || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="开工时间" prop="startTime" width="150">
            <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="完工时间" prop="finishTime" width="150">
            <template #default="{ row }">{{ fmtTime(row.finishTime) }}</template>
          </el-table-column>
          <el-table-column label="完工入库" prop="stockInQty" width="90" align="right">
            <template #default="{ row }">{{ row.stockInQty ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="结算状态" prop="settleStatus" width="80" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="row.bizStatus !== '计划'" @click="openStart(row)">开工</el-button>
              <el-button link type="success" :disabled="row.bizStatus !== '开工'" @click="openDone(row)">完工</el-button>
              <el-button link type="warning" :disabled="row.bizStatus === '完工'" @click="openGroup(row)">改组别</el-button>
              <el-button link type="danger" @click="onDeleteMat(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="sub-panel empty-hint" v-else>
        👆 请先点击上方任务单查看用料明细
      </div>
    </div>

    <!-- 开工 -->
    <el-dialog v-model="startDialog.visible" title="开工提交" width="440px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="明细">
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

    <!-- 完工 -->
    <el-dialog v-model="doneDialog.visible" title="确认完工" width="440px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="明细">
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

    <!-- 改组别 -->
    <el-dialog v-model="groupDialog.visible" title="修改加工组别" width="440px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="明细">
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
  startMaterial,
  doneMaterial,
  changeMaterialGroup,
  deleteMaterial,
  listMaterials,
  listWorkshops,
} from '@/api/wage/orders'
import { listGroups } from '@/api/wage/groups'
import type { WageGroup, WageOrder, WageMaterial } from '@/api/wage/types'

const userStore = useUserStore()

onMounted(async () => {
  try { await syncOrdersFromMaterials() } catch (e) { console.warn(e) }
  await Promise.all([loadGroups(), loadWorkshopOptions(), loadOrders()])
})

const loading = ref(false)
const acting = ref(false)
const matLoading = ref(false)
const rows = ref<WageOrder[]>([])
const groups = ref<WageGroup[]>([])
const workshops = ref<string[]>([])
const materials = ref<WageMaterial[]>([])
const current = ref<WageOrder | null>(null)

const query = reactive({ keyword: '', workshop: '' })
const matQuery = reactive({ bizStatus: '', keyword: '' })
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
      pageNum: page.pageNum, pageSize: page.pageSize,
    })
    rows.value = data?.list || []
    page.total = data?.total || 0
  } finally { loading.value = false }
}
function reload() { page.pageNum = 1; loadOrders() }

async function selectTask(row: WageOrder) {
  current.value = row
  matQuery.bizStatus = ''
  matQuery.keyword = ''
  await reloadMats()
}
async function reloadMats() {
  if (!current.value) return
  matLoading.value = true
  try {
    const { data } = await listMaterials({
      taskNo: current.value.taskNo,
      bizStatus: matQuery.bizStatus || undefined,
      keyword: matQuery.keyword || undefined,
    })
    materials.value = data || []
  } finally { matLoading.value = false }
}

function openStart(row: WageMaterial) {
  startDialog.id = row.id
  startDialog.taskNo = `${row.materialCode} ${row.materialName ?? ''}`
  startDialog.groupId = row.groupId
  startDialog.visible = true
}
async function confirmStart() {
  if (!startDialog.groupId) { ElMessage.warning('请选择加工组别'); return }
  acting.value = true
  try {
    await startMaterial(startDialog.id, {
      groupId: startDialog.groupId,
      confirmerNo: userStore.userInfo.username,
      confirmerName: userStore.userInfo.realName,
    })
    ElMessage.success('开工成功')
    startDialog.visible = false
    await reloadMats()
  } finally { acting.value = false }
}

function openDone(row: WageMaterial) {
  doneDialog.id = row.id
  doneDialog.taskNo = `${row.materialCode} ${row.materialName ?? ''}`
  doneDialog.stockInQty = Number(row.stockInQty ?? row.qty ?? 0)
  doneDialog.visible = true
}
async function confirmDone() {
  acting.value = true
  try {
    await doneMaterial(doneDialog.id, {
      stockInQty: doneDialog.stockInQty,
      finishUser: userStore.userInfo.realName,
    })
    ElMessage.success('已确认完工')
    doneDialog.visible = false
    await reloadMats()
  } finally { acting.value = false }
}

function openGroup(row: WageMaterial) {
  groupDialog.id = row.id
  groupDialog.taskNo = `${row.materialCode} ${row.materialName ?? ''}`
  groupDialog.groupId = row.groupId
  groupDialog.visible = true
}
async function confirmGroup() {
  if (!groupDialog.groupId) { ElMessage.warning('请选择加工组别'); return }
  acting.value = true
  try {
    await changeMaterialGroup(groupDialog.id, groupDialog.groupId)
    ElMessage.success('修改成功')
    groupDialog.visible = false
    await reloadMats()
  } finally { acting.value = false }
}

async function onDeleteMat(row: WageMaterial) {
  await ElMessageBox.confirm(`确认删除明细「${row.materialName}」？`, '删除确认', {
    type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
  })
  await deleteMaterial(row.id)
  ElMessage.success('已删除')
  await reloadMats()
}
</script>

<style scoped lang="scss">
.crud-tip { margin-left: 12px; font-size: 12px; color: $text-secondary; font-weight: normal; }
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.ipt { width: 150px; }
.kw { width: 220px; }
.full { width: 100%; }
.pager { display: flex; justify-content: flex-end; margin-top: 12px; }

.sub-panel {
  margin-top: 18px;
  border-top: 1px dashed $border-color;
  padding-top: 12px;
}
.empty-hint { text-align: center; color: $text-secondary; font-size: 14px; padding: 28px 0; }

.sub-title {
  font-size: 14px; font-weight: 700; color: $text-primary;
  margin-bottom: 10px; display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
}
.sub-key { font-size: 12px; color: $text-secondary; font-weight: normal; }
.mat-filters { margin-left: auto; display: flex; gap: 8px; align-items: center; }
.mat-st { width: 110px; }
.mat-kw { width: 160px; }

.status-tag { display: inline-block; padding: 2px 10px; font-size: 12px; border: 1px solid; }
.biz-plan { color: $text-secondary; border-color: $border-light; }
.biz-run { color: $warning-color; border-color: $warning-color; background: rgba(255, 176, 32, 0.08); }
.biz-done { color: $success-color; border-color: $success-color; background: rgba(0, 255, 148, 0.08); }

.order-table {
  :deep(td.el-table-fixed-column--left), :deep(th.el-table-fixed-column--left) { z-index: 4 !important; }
  :deep(td.el-table-fixed-column--right), :deep(th.el-table-fixed-column--right) { z-index: 5 !important; }
  :deep(th.el-table-fixed-column--left), :deep(th.el-table-fixed-column--right) {
    background: linear-gradient(180deg, #121e2e 0%, #0c1522 100%) !important;
    background-clip: padding-box !important;
  }
  :deep(tbody td.el-table-fixed-column--left), :deep(tbody td.el-table-fixed-column--right) {
    background-color: #131c28 !important; background-clip: padding-box !important;
  }
  :deep(tbody tr.el-table__row--striped td.el-table-fixed-column--left),
  :deep(tbody tr.el-table__row--striped td.el-table-fixed-column--right) {
    background-color: #172230 !important;
  }
  :deep(tbody tr.current-row td.el-table-fixed-column--left),
  :deep(tbody tr.current-row td.el-table-fixed-column--right) {
    background-color: rgba(26,179,255,0.10) !important;
  }
  :deep(tbody td.el-table-fixed-column--left)  { border-right: 1px solid rgba(255,255,255,0.08); }
  :deep(tbody td.el-table-fixed-column--right) { border-left:  1px solid rgba(255,255,255,0.08); }
}
</style>
