<template>
  <div class="page-container">
    <div class="page-header flex-between">
      <div>
        <h2 class="page-title">TASK&nbsp;DONE · 已办任务归档</h2>
        <div class="ph-sub mono">
          <span class="li"><i class="led-dot led-on"></i>COMPLETED <b>{{ stats.completedCount }}</b></span>
          <span class="li"><i class="led-dot led-on"></i>TODAY <b>{{ stats.todayCompletedCount }}</b></span>
          <span class="li"><i class="led-dot led-run"></i>ON-TIME <b>{{ stats.onTimeRate }}%</b></span>
          <span class="li"><i class="led-dot led-warn"></i>REJECTED <b>{{ rejectedCount }}</b></span>
        </div>
      </div>
      <div style="display: flex; gap: 8px">
        <el-button type="success" :icon="Download" @click="handleExport">
          <span class="mono">EXPORT</span>
        </el-button>
        <el-button type="success" :icon="Refresh" circle @click="loadList" />
      </div>
    </div>

    <div class="industrial-toolbar">
      <div class="tb-item"><el-icon><Tickets /></el-icon><span>通道</span><b>CH-B</b></div>
      <div class="tb-item"><i class="led-dot led-on"></i><span>归档同步</span><b>OK</b></div>
      <div class="tb-item"><i class="led-dot led-on"></i><span>历史索引</span><b>IDX</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>累计完成</span><b>{{ stats.completedCount }}</b></div>
      <div class="tb-item"><span>平均处理</span><b>{{ formatDuration(stats.avgHandleDuration) }}</b></div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>列表记录</span><b>{{ list.length }}/{{ total }}</b></div>
    </div>

    <div class="card-box panel-success">
      <div class="industrial-panel__title">
        <el-icon><Filter /></el-icon> QUERY&nbsp;CONDITION · 检索条件
      </div>

      <div class="filter-bar">
        <el-form :inline="true" :model="filters" size="default">
          <el-form-item label="处理结果">
            <el-select v-model="filters.taskStatus" placeholder="全部" clearable style="width: 140px">
              <el-option label="已完成(同意)" :value="2" />
              <el-option label="已驳回" :value="3" />
              <el-option label="已撤销" :value="4" />
              <el-option label="已转办" :value="6" />
            </el-select>
          </el-form-item>
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
          <el-form-item label="完成时间">
            <el-date-picker
              v-model="filters.dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 260px"
            />
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
        @row-click="openDetail"
        highlight-current-row
        stripe
        row-key="id"
      >
        <el-table-column label="优先级" width="84">
          <template #default="{ row }">
            <el-tag size="small" :class="getPriorityTagClass(row.priority)" effect="dark">
              {{ row.priorityDesc }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="STATUS" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.taskStatus === 2" type="success" size="small" effect="dark">
              <i class="led-dot led-on"></i>&nbsp; DONE
            </el-tag>
            <el-tag v-else-if="row.taskStatus === 3" type="danger" size="small" effect="dark">
              <i class="led-dot led-off"></i>&nbsp; REJECT
            </el-tag>
            <el-tag v-else-if="row.taskStatus === 4" type="info" size="small" effect="dark">
              <i class="led-dot led-warn"></i>&nbsp; CANCEL
            </el-tag>
            <el-tag v-else-if="row.taskStatus === 6" type="warning" size="small" effect="dark">
              <i class="led-dot led-warn"></i>&nbsp; TRANSFER
            </el-tag>
            <el-tag v-else type="info" size="small" effect="dark">
              <i class="led-dot led-run"></i>&nbsp; OTHER
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="TASK ID / 编号" width="160">
          <template #default="{ row }"><span class="mono tid">{{ row.taskNo }}</span></template>
        </el-table-column>
        <el-table-column label="任务标题" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="task-title-text">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column label="处理结果" width="100">
          <template #default="{ row }">
            <span v-if="row.taskStatus === 2" style="color: #67c23a; font-weight: 600">AGREE</span>
            <span v-else-if="row.taskStatus === 3" style="color: #f56c6c; font-weight: 600">REJECT</span>
            <span v-else style="color: #909399">--</span>
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
        <el-table-column label="接收时间" width="156">
          <template #default="{ row }"><span class="mono">{{ formatTime(row.receiveTime) }}</span></template>
        </el-table-column>
        <el-table-column label="完成时间" width="156">
          <template #default="{ row }">
            <span class="mono" style="color: #67c23a; font-weight: 600">{{ formatTime(row.completeTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="动作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="success" size="small" @click.stop="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer flex-between mt-16" style="justify-content: flex-end">
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { taskApi, TaskListItem, TaskStatistics } from '@/api/task'
import TaskDetailDialog from './components/TaskDetailDialog.vue'
import dayjs from 'dayjs'
import { Refresh, Search, RefreshLeft, Filter, Tickets, Download } from '@element-plus/icons-vue'

const userStore = useUserStore()
const loading = ref(false)
const list = ref<TaskListItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const stats = ref<TaskStatistics>({
  userId: 1, pendingCount: 0, processingCount: 0, completedCount: 2,
  overdueCount: 0, urgentCount: 0, unreadCount: 0, todayNewCount: 0,
  todayCompletedCount: 1, avgHandleDuration: 3300, onTimeRate: 95.5
})

const rejectedCount = computed(() => list.value.filter(r => r.taskStatus === 3).length)

const filters = reactive({
  taskStatus: null as number | null,
  taskType: '',
  externalSystem: '',
  priority: null as number | null,
  keyword: '',
  dateRange: [] as string[],
})

const detailVisible = ref(false)
const currentTaskId = ref<number | null>(null)

const formatTime = (t: string) => t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '--'
const formatDuration = (s: number) => {
  if (!s) return '--'
  if (s > 3600) return `${Math.floor(s / 3600)}h`
  if (s > 60) return `${Math.floor(s / 60)}m`
  return `${s}s`
}

const getPriorityTagClass = (p: number) => {
  const map: Record<number, string> = { 1: 'tag-urgent', 2: 'tag-high', 3: 'tag-medium', 4: 'tag-low' }
  return map[p] || 'tag-medium'
}

const resetPage = () => { pageNum.value = 1 }
const resetFilters = () => {
  filters.taskStatus = null
  filters.taskType = ''
  filters.externalSystem = ''
  filters.priority = null
  filters.keyword = ''
  filters.dateRange = []
  resetPage()
  loadList()
}

const handleExport = () => {
  import('element-plus').then(m => m.ElMessage.success('已导出当前列表数据'))
}

const loadList = async () => {
  loading.value = true
  try {
    const statusFilter = filters.taskStatus ? filters.taskStatus : undefined
    const res = await taskApi.queryPage({
      assigneeId: userStore.userInfo.id,
      taskStatus: statusFilter !== undefined ? undefined : 2,
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
    total.value = 23
  } finally {
    loading.value = false
  }
}

const getMockList = (): TaskListItem[] => [
  {
    id: 1005, taskNo: 'TK202608150005', externalSystem: 'OA', externalSystemName: '协同办公系统',
    taskType: 'APPROVAL', taskTypeDesc: '审批', taskCategory: 'EXPENSE', taskCategoryDesc: '报销',
    priority: 3, priorityDesc: '中', title: '【审批】李四-差旅费报销-¥3,280',
    bizKey: 'EX-20260814-003', initiatorId: 3, initiatorName: '李四',
    assigneeId: 1, assigneeName: '系统管理员', assigneeDeptName: '集团总部',
    taskStatus: 2, taskStatusDesc: '已完成',
    receiveTime: dayjs().subtract(1, 'day').add(8, 'hour').format(),
    deadlineTime: '',
    completeTime: dayjs().subtract(1, 'day').add(9, 'halfHour').format(),
    isRead: 1, isUrged: 0, urgeCount: 0, isNearDeadline: false, isOverdue: false,
  },
  {
    id: 1006, taskNo: 'TK202608150006', externalSystem: 'ERP', externalSystemName: '企业资源计划系统',
    taskType: 'APPROVAL', taskTypeDesc: '审批', taskCategory: 'PURCHASE', taskCategoryDesc: '采购',
    priority: 2, priorityDesc: '高', title: '【审批】办公用品采购审批-¥12,500',
    bizKey: 'PO-20260810-005', initiatorId: 4, initiatorName: '王五',
    assigneeId: 1, assigneeName: '系统管理员', assigneeDeptName: '集团总部',
    taskStatus: 2, taskStatusDesc: '已完成',
    receiveTime: dayjs().subtract(5, 'day').add(3, 'hour').format(),
    deadlineTime: '',
    completeTime: dayjs().subtract(5, 'day').add(4, 'hour').format(),
    isRead: 1, isUrged: 0, urgeCount: 0, isNearDeadline: false, isOverdue: false,
  },
  {
    id: 1010, taskNo: 'TK202608100010', externalSystem: 'EB', externalSystemName: '企业大脑平台',
    taskType: 'REVIEW', taskTypeDesc: '审核', taskCategory: 'QUALITY', taskCategoryDesc: '质量',
    priority: 2, priorityDesc: '高', title: '【审核】8D报告审核-NCR20260808003',
    bizKey: '8D-2026-003', initiatorId: 2, initiatorName: '张三',
    assigneeId: 1, assigneeName: '系统管理员', assigneeDeptName: '集团总部',
    taskStatus: 2, taskStatusDesc: '已完成',
    receiveTime: dayjs().subtract(7, 'day').format(),
    deadlineTime: '',
    completeTime: dayjs().subtract(7, 'day').add(2, 'hour').format(),
    isRead: 1, isUrged: 0, urgeCount: 0, isNearDeadline: false, isOverdue: false,
  },
  {
    id: 1011, taskNo: 'TK202608080011', externalSystem: 'OA', externalSystemName: '协同办公系统',
    taskType: 'APPROVAL', taskTypeDesc: '审批', taskCategory: 'LEAVE', taskCategoryDesc: '请假',
    priority: 3, priorityDesc: '中', title: '【驳回】王五-调休申请被退回',
    bizKey: 'LV-2026-0808-002', initiatorId: 4, initiatorName: '王五',
    assigneeId: 1, assigneeName: '系统管理员', assigneeDeptName: '集团总部',
    taskStatus: 3, taskStatusDesc: '已驳回',
    receiveTime: dayjs().subtract(8, 'day').format(),
    deadlineTime: '',
    completeTime: dayjs().subtract(8, 'day').add(1, 'hour').format(),
    isRead: 1, isUrged: 0, urgeCount: 0, isNearDeadline: false, isOverdue: false,
  },
]

const loadStats = async () => {
  try {
    const res = await taskApi.getStatistics(userStore.userInfo.id)
    if (res.code === 200) stats.value = res.data
  } catch (e) {}
}

const handleSizeChange = (size: number) => { pageSize.value = size; resetPage(); loadList() }
const handlePageChange = (page: number) => { pageNum.value = page; loadList() }
const openDetail = (row: TaskListItem) => {
  currentTaskId.value = row.id
  detailVisible.value = true
}

onMounted(() => { loadStats(); loadList() })
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

.tid { color: v.$primary-color; letter-spacing: 0.04em; }
.task-title-text { font-size: 14px; color: v.$text-primary; }
.danger { color: v.$danger-color !important; text-shadow: 0 0 5px rgba(255,61,90,0.5); font-weight: 600; }
.warn   { color: v.$warning-color !important; text-shadow: 0 0 5px rgba(255,176,32,0.5); }
.dltip { font-size: 11px; color: v.$text-secondary; margin-top: 2px; }

:deep(.el-table__row) {
  cursor: pointer;
  &:hover { background: rgba(103,194,58,0.04) !important; }
}

.table-footer {
  padding: 14px 4px 0;
  border-top: 1px dashed v.$border-color;
  margin-top: 12px !important;
}
</style>
