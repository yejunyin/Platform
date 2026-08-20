<template>
  <div class="page-container">
    <!-- 页面头 -->
    <div class="page-header flex-between">
      <div>
        <h2 class="page-title">TASK&nbsp;PENDING · 待办任务队列</h2>
        <div class="ph-sub mono">
          <span class="li"><i class="led-dot led-run"></i>PENDING <b>{{ stats.pendingCount }}</b></span>
          <span class="li"><i class="led-dot led-off"></i>URGENT <b>{{ stats.urgentCount }}</b></span>
          <span class="li"><i class="led-dot led-off"></i>OVERDUE <b>{{ stats.overdueCount }}</b></span>
          <span class="li"><i class="led-dot led-warn"></i>UNREAD <b>{{ stats.unreadCount }}</b></span>
        </div>
      </div>
      <div style="display: flex; gap: 8px">
        <el-button type="primary" :icon="Check" @click="markAllRead" :disabled="stats.unreadCount === 0">
          <span class="mono">BATCH·READ</span>
        </el-button>
        <el-button type="success" :icon="Refresh" circle @click="loadList" />
      </div>
    </div>

    <!-- 工具栏 · 状态指示 -->
    <div class="industrial-toolbar">
      <div class="tb-item"><el-icon><Tickets /></el-icon><span>通道</span><b>CH-A</b></div>
      <div class="tb-item"><i class="led-dot led-on"></i><span>队列同步</span><b>OK</b></div>
      <div class="tb-item"><i class="led-dot led-run"></i><span>派发器</span><b>RUN</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>今日派发</span><b>{{ stats.todayNewCount }}</b></div>
      <div class="tb-item"><span>按期率</span><b>{{ stats.onTimeRate }}%</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>已选择</span><b>{{ selectedIds.length }}/{{ list.length }}</b></div>
    </div>

    <div class="card-box panel-primary">
      <div class="industrial-panel__title">
        <el-icon><Filter /></el-icon> QUERY&nbsp;CONDITION · 检索条件
      </div>

      <div class="filter-bar">
        <el-form :inline="true" :model="filters" size="default">
          <el-form-item label="任务类型">
            <el-select v-model="filters.taskType" placeholder="全部" clearable style="width: 140px">
              <el-option label="审批" value="APPROVAL" />
              <el-option label="通知" value="NOTICE" />
              <el-option label="待办" value="TODO" />
              <el-option label="审核" value="REVIEW" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源系统">
            <el-select v-model="filters.externalSystem" placeholder="全部" clearable style="width: 160px">
              <el-option label="OA办公系统" value="OA" />
              <el-option label="ERP资源计划" value="ERP" />
              <el-option label="MES制造系统" value="MES" />
              <el-option label="企业大脑" value="EB" />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级">
            <el-select v-model="filters.priority" placeholder="全部" clearable style="width: 120px">
              <el-option label="紧急" :value="1" />
              <el-option label="高" :value="2" />
              <el-option label="中" :value="3" />
              <el-option label="低" :value="4" />
            </el-select>
          </el-form-item>
          <el-form-item label="读取状态">
            <el-select v-model="filters.isRead" placeholder="全部" clearable style="width: 120px">
              <el-option label="未读" :value="0" />
              <el-option label="已读" :value="1" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-input v-model="filters.keyword" placeholder="搜索标题/编号/单号" clearable style="width: 240px" :prefix-icon="Search" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="resetPage(); loadList()"><span class="mono">▶ GO</span></el-button>
            <el-button :icon="RefreshLeft" @click="resetFilters">
              <span class="mono">RST</span>
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        class="ind-table"
        @selection-change="handleSelectionChange"
        @row-click="openDetail"
        highlight-current-row
        stripe
        row-key="id"
        :row-class-name="rowClassName"
      >
        <el-table-column type="selection" width="48" @click.stop />
        <el-table-column label="优先级" width="84">
          <template #default="{ row }">
            <el-tag size="small" :class="getPriorityTagClass(row.priority)" effect="dark">
              {{ row.priorityDesc }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="STATUS" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isOverdue" type="danger" size="small" effect="dark">
              <i class="led-dot led-off"></i>&nbsp; OVERDUE
            </el-tag>
            <el-tag v-else-if="row.isNearDeadline" type="warning" size="small" effect="dark">
              <i class="led-dot led-warn"></i>&nbsp; NEAR
            </el-tag>
            <el-tag v-else-if="row.taskStatus === 1" type="primary" size="small" effect="dark">
              <i class="led-dot led-run"></i>&nbsp; PROC
            </el-tag>
            <el-tag v-else type="success" size="small" effect="dark">
              <i class="led-dot led-on"></i>&nbsp; IDLE
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="TASK ID / 编号" width="160">
          <template #default="{ row }"><span class="mono tid">{{ row.taskNo }}</span></template>
        </el-table-column>
        <el-table-column label="任务标题" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">
            <el-badge v-if="row.isRead === 0" is-dot :offset="[-4, 2]" class="unread-dot" />
            <el-icon v-if="row.isUrged === 1" color="#ff3d5a"><BellFilled /></el-icon>
            <span class="task-title-text">{{ row.title }}</span>
            <span v-if="row.urgeCount > 0" class="urge-n">URGE&nbsp;×{{ row.urgeCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="taskTypeDesc" label="类型" width="72" />
        <el-table-column prop="taskCategoryDesc" label="分类" width="72" />
        <el-table-column prop="externalSystemName" label="来源系统" width="130">
          <template #default="{ row }">
            <el-tag effect="plain" size="small">{{ row.externalSystemName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="initiatorName" label="发起人" width="90" />
        <el-table-column label="DEADLINE" width="170">
          <template #default="{ row }">
            <div>
              <span class="mono" :class="{ danger: row.isOverdue, warn: row.isNearDeadline && !row.isOverdue }">
                {{ row.deadlineTime ? formatTime(row.deadlineTime) : '--' }}
              </span>
              <div v-if="row.deadlineTime" class="dltip">
                {{ getDeadlineTip(row.deadlineTime, row.isOverdue) }}
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="接收时间" width="156">
          <template #default="{ row }"><span class="mono">{{ formatTime(row.receiveTime) }}</span></template>
        </el-table-column>
        <el-table-column label="动作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click.stop="openDetail(row)">查看</el-button>
            <el-button type="success" size="small" @click.stop="quickApprove(row)" v-if="row.taskStatus === 0 || row.taskStatus === 1">
              同意
            </el-button>
            <el-button type="danger" size="small" @click.stop="quickReject(row)" v-if="row.taskStatus === 0 || row.taskStatus === 1">
              驳回
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer flex-between mt-16">
        <div>
          <el-checkbox v-model="allSelected" :indeterminate="isIndeterminate" @change="handleCheckAllChange">
            <span class="mono">SELECT&nbsp;ALL</span>
          </el-checkbox>
          <el-button v-if="selectedIds.length > 0" type="primary" size="small" @click="markSelectedRead" style="margin-left: 12px">
            <span class="mono">MARK READ ({{ selectedIds.length }})</span>
          </el-button>
        </div>
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <TaskDetailDialog
      v-model="detailVisible"
      :task-id="currentTaskId"
      @refresh="loadList; loadStats()"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { taskApi, TaskListItem, TaskStatistics } from '@/api/task'
import TaskDetailDialog from './components/TaskDetailDialog.vue'
import dayjs from 'dayjs'
import {
  Check, Refresh, Search, RefreshLeft, BellFilled, Filter
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const list = ref<TaskListItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const selectedIds = ref<number[]>([])
const allSelected = ref(false)
const isIndeterminate = ref(false)

const stats = ref<TaskStatistics>({
  userId: 1, pendingCount: 0, processingCount: 0, completedCount: 0,
  overdueCount: 0, urgentCount: 0, unreadCount: 0, todayNewCount: 0,
  todayCompletedCount: 0, avgHandleDuration: 0, onTimeRate: 0
})

const filters = reactive({
  taskType: '',
  externalSystem: '',
  priority: null as number | null,
  isRead: null as number | null,
  keyword: '',
})

const detailVisible = ref(false)
const currentTaskId = ref<number | null>(null)

const formatTime = (t: string) => t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '--'

const getPriorityTagClass = (p: number) => {
  const map: Record<number, string> = { 1: 'tag-urgent', 2: 'tag-high', 3: 'tag-medium', 4: 'tag-low' }
  return map[p] || 'tag-medium'
}

const getDeadlineTip = (t: string, isOverdue: boolean) => {
  const diff = dayjs(t).diff(dayjs(), 'minute')
  if (isOverdue) {
    const abs = Math.abs(diff)
    if (abs > 1440) return `已超时 ${Math.floor(abs / 1440)} 天`
    if (abs > 60) return `已超时 ${Math.floor(abs / 60)} 小时`
    return `已超时 ${abs} 分钟`
  }
  if (diff < 60) return `剩 ${diff} 分钟`
  if (diff < 1440) return `剩 ${Math.floor(diff / 60)} 小时`
  return `剩 ${Math.floor(diff / 1440)} 天`
}

const rowClassName = ({ row }: { row: TaskListItem }) => {
  const cls = ['task-row']
  if (row.isRead === 0) cls.push('row-unread')
  if (row.isOverdue) cls.push('row-overdue')
  else if (row.isNearDeadline) cls.push('row-near')
  if (row.priority === 1) cls.push('row-urgent')
  return cls.join(' ')
}

const resetPage = () => { pageNum.value = 1 }

const resetFilters = () => {
  filters.taskType = ''
  filters.externalSystem = ''
  filters.priority = null
  filters.isRead = null
  filters.keyword = ''
  resetPage()
  loadList()
}

const loadList = async () => {
  loading.value = true
  try {
    const res = await taskApi.queryPage({
      assigneeId: userStore.userInfo.id,
      taskStatus: 0,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...filters,
    })
    if (res.code === 200) {
      list.value = res.data.list
      total.value = res.data.total
    }
  } catch (e) {
    list.value = getMockList()
    total.value = 15
  } finally {
    loading.value = false
  }
}

const getMockList = (): TaskListItem[] => {
  const arr: TaskListItem[] = []
  const data = [
    { id: 1002, p: 1, title: '【紧急审批】原材料采购订单审批-¥580,000', sys: 'ERP', sysName: '企业资源计划系统', type: 'APPROVAL', typeName: '审批', cat: '采购', initiator: '王五', deadline: dayjs().add(6, 'hour'), urged: true, urgeCount: 2, isRead: 0 },
    { id: 1001, p: 2, title: '【审批】张三-年假申请-5天', sys: 'OA', sysName: '协同办公系统', type: 'APPROVAL', typeName: '审批', cat: '请假', initiator: '张三', deadline: dayjs().add(1, 'day'), urged: false, urgeCount: 0, isRead: 0 },
    { id: 1004, p: 2, title: '【审批】不合格品处理申请-NCR20260815001', sys: 'EB', sysName: '企业大脑平台', type: 'APPROVAL', typeName: '审批', cat: '质量', initiator: '张三', deadline: dayjs().add(2, 'day'), urged: false, urgeCount: 0, isRead: 1 },
    { id: 1007, p: 2, title: '【审批】采购合同审批-XX服务合同', sys: 'ERP', sysName: '企业资源计划系统', type: 'APPROVAL', typeName: '审批', cat: '合同', initiator: '王五', deadline: dayjs().add(3, 'day'), urged: false, urgeCount: 0, isRead: 0 },
    { id: 1008, p: 3, title: '【审核】月度财务报表复核', sys: 'ERP', sysName: '企业资源计划系统', type: 'REVIEW', typeName: '审核', cat: '其他', initiator: '系统', deadline: dayjs().add(5, 'day'), urged: false, urgeCount: 0, isRead: 1 },
    { id: 1009, p: 4, title: '【通知】年度培训计划通知，请查阅', sys: 'OA', sysName: '协同办公系统', type: 'NOTICE', typeName: '通知', cat: '其他', initiator: 'HR', deadline: null, urged: false, urgeCount: 0, isRead: 0 },
  ]
  for (let i = 0; i < 4; i++) {
    const d = data[i]
    const overdue = i === 0 && false
    arr.push({
      id: d.id, taskNo: `TK20260815${d.id}`, externalSystem: d.sys, externalSystemName: d.sysName,
      taskType: d.type, taskTypeDesc: d.typeName, taskCategory: '', taskCategoryDesc: d.cat,
      priority: d.p, priorityDesc: d.p === 1 ? '紧急' : d.p === 2 ? '高' : d.p === 3 ? '中' : '低',
      title: d.title, bizKey: `BIZ-${d.id}`,
      initiatorId: 1, initiatorName: d.initiator,
      assigneeId: 1, assigneeName: '系统管理员', assigneeDeptName: '集团总部',
      taskStatus: i === 2 ? 1 : 0, taskStatusDesc: i === 2 ? '处理中' : '待处理',
      receiveTime: dayjs().subtract(i + 1, 'hour').format(),
      deadlineTime: d.deadline ? d.deadline.format() : '',
      completeTime: '', isRead: d.isRead, isUrged: d.urged ? 1 : 0, urgeCount: d.urgeCount,
      isOverdue: overdue, isNearDeadline: i === 0,
    })
  }
  return arr
}

const loadStats = async () => {
  try {
    const res = await taskApi.getStatistics(userStore.userInfo.id)
    if (res.code === 200) stats.value = res.data
  } catch (e) {
    stats.value = {
      userId: 1, pendingCount: 4, processingCount: 1, completedCount: 2,
      overdueCount: 0, urgentCount: 1, unreadCount: 3, todayNewCount: 2,
      todayCompletedCount: 1, avgHandleDuration: 3300, onTimeRate: 95.5
    }
  }
}

const handleSelectionChange = (rows: TaskListItem[]) => {
  selectedIds.value = rows.map((r) => r.id)
  isIndeterminate.value = selectedIds.value.length > 0 && selectedIds.value.length < list.value.length
  allSelected.value = selectedIds.value.length === list.value.length && list.value.length > 0
}

const handleCheckAllChange = (val: boolean) => {
  selectedIds.value = val ? list.value.map((r) => r.id) : []
  isIndeterminate.value = false
}

const markSelectedRead = async () => {
  if (selectedIds.value.length === 0) return
  try {
    const res = await taskApi.markReadBatch(selectedIds.value, userStore.userInfo.id)
    if (res.code === 200) ElMessage.success(`已将 ${res.data} 条标记为已读`)
  } catch (e) {
    ElMessage.success(`已将 ${selectedIds.value.length} 条标记为已读（演示）`)
  }
  selectedIds.value = []
  loadList()
  loadStats()
}

const markAllRead = async () => {
  await ElMessageBox.confirm('确认将所有任务标记为已读？', '提示', { type: 'info' })
  try {
    const res = await taskApi.markAllRead(userStore.userInfo.id)
    if (res.code === 200) ElMessage.success(`已将 ${res.data} 条标记为已读`)
  } catch (e) {
    ElMessage.success('已全部标记为已读（演示）')
  }
  loadList()
  loadStats()
}

const handleSizeChange = (size: number) => { pageSize.value = size; resetPage(); loadList() }
const handlePageChange = (page: number) => { pageNum.value = page; loadList() }

const openDetail = (row: TaskListItem) => {
  currentTaskId.value = row.id
  detailVisible.value = true
}

const quickApprove = async (row: TaskListItem) => {
  await ElMessageBox.confirm(`确认同意【${row.title}】？`, '快速审批', { type: 'success' })
  try {
    const res = await taskApi.handle({
      taskId: row.id, handlerId: userStore.userInfo.id, handlerName: userStore.userInfo.realName,
      actionType: 'APPROVE', actionResult: 'AGREE',
    })
    if (res.code === 200) ElMessage.success('审批成功')
  } catch (e) {
    ElMessage.success('审批成功（演示）')
  }
  loadList(); loadStats()
}

const quickReject = async (row: TaskListItem) => {
  await ElMessageBox.confirm(`确认驳回【${row.title}】？`, '快速驳回', { type: 'warning' })
  try {
    const res = await taskApi.handle({
      taskId: row.id, handlerId: userStore.userInfo.id, handlerName: userStore.userInfo.realName,
      actionType: 'REJECT', actionResult: 'REJECT',
    })
    if (res.code === 200) ElMessage.success('已驳回')
  } catch (e) {
    ElMessage.success('已驳回（演示）')
  }
  loadList(); loadStats()
}

let routeWatch: any
onMounted(() => {
  loadStats()
  loadList()
  if (route.query.id) {
    currentTaskId.value = Number(route.query.id)
    detailVisible.value = true
  }
})

onUnmounted(() => routeWatch && routeWatch())
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as v;

.mono { font-family: v.$font-mono; }
.led-dot {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  background: v.$text-secondary;
  &.led-on   { background: v.$success-color; box-shadow: v.$glow-success; }
  &.led-warn { background: v.$warning-color; box-shadow: v.$glow-warn; }
  &.led-off  { background: v.$danger-color;  box-shadow: v.$glow-danger; }
  &.led-run  { background: v.$primary-color; box-shadow: v.$glow-primary; animation: pulse 1.2s infinite; }
}
@keyframes pulse { 0%,100% { opacity:1; } 50% { opacity: 0.4; } }

/* 页面头子信息 */
.ph-sub {
  margin-top: 6px;
  font-size: 12px;
  color: v.$text-secondary;
  letter-spacing: 0.14em;
  display: flex; align-items: center; gap: 18px;
  .li { display: inline-flex; align-items: center; gap: 6px; }
  .li b { color: v.$text-primary; font-weight: 700; }
}

.filter-bar {
  margin-bottom: 14px;
  padding: 12px 14px;
  background: v.$panel-bg-deep;
  border: 1px solid v.$border-color;
  border-radius: 2px;
  :deep(.el-form-item) {
    margin-bottom: 8px;
    margin-right: 10px;
    :deep(.el-form-item__label) { font-size: 12px; letter-spacing: 0.04em; }
  }
}

/* 表格样式增强 */
.tid { color: v.$primary-color; letter-spacing: 0.04em; }
.unread-dot {
  :deep(.el-badge__content) {
    background: v.$primary-color !important; border-color: v.$primary-color !important;
    box-shadow: v.$glow-primary;
  }
  margin-right: 6px;
}
.urge-n {
  color: v.$danger-color;
  font-family: v.$font-mono;
  font-size: 11px;
  margin-left: 6px;
  padding: 1px 6px;
  border: 1px dashed rgba(255,61,90,0.4);
  border-radius: 2px;
}
.task-title-text { font-size: 14px; color: v.$text-primary; }
.danger { color: v.$danger-color !important; text-shadow: 0 0 5px rgba(255,61,90,0.5); font-weight: 600; }
.warn   { color: v.$warning-color !important; text-shadow: 0 0 5px rgba(255,176,32,0.5); }
.dltip { font-size: 11px; color: v.$text-secondary; margin-top: 2px; }

/* 表格行类 */
.task-row {
  cursor: pointer;
  &.row-unread :deep(.task-title-text) { font-weight: 700; color: #fff; }
  &.row-overdue :deep(td:first-child) { box-shadow: inset 3px 0 0 v.$danger-color; }
  &.row-near    :deep(td:first-child) { box-shadow: inset 3px 0 0 v.$warning-color; }
  &.row-unread  :deep(td:first-child) { box-shadow: inset 3px 0 0 v.$primary-color; }
  &.row-urgent:hover :deep(td) { background: rgba(255,61,90,0.06) !important; }
}

.table-footer {
  padding: 14px 4px 0;
  border-top: 1px dashed v.$border-color;
  margin-top: 12px !important;
}
</style>
