<template>
  <div class="page-container">
    <!-- 工作台状态头 -->
    <div class="page-header flex-between">
      <div class="ph-left">
        <h2 class="page-title">OPERATION&nbsp;CONSOLE · 工作台总控</h2>
        <div class="ph-sub mono">SYS-ID: EB-1000 &nbsp;·&nbsp; SHIFT: A 班 &nbsp;·&nbsp; OPR: {{ userStore.userInfo.realName || 'ADMIN' }}</div>
      </div>
      <div class="ph-right mono">
        <span class="ph-tag"><i class="led-dot led-on"></i>SYS ONLINE</span>
        <span class="ph-tag warn"><i class="led-dot led-warn"></i>ALM {{ stats.overdueCount }}</span>
        <span class="ph-tag date">{{ currentTime }}</span>
      </div>
    </div>

    <!-- 状态灯工具栏 -->
    <div class="industrial-toolbar">
      <div class="tb-item">
        <el-icon><Cpu /></el-icon><span>节点</span>
        <b>MASTER-01</b>
      </div>
      <div class="tb-divider"></div>
      <div class="tb-item">
        <i class="led-dot led-on"></i><span>网关</span>
        <b>OK</b>
      </div>
      <div class="tb-item">
        <i class="led-dot led-run"></i><span>MQ</span>
        <b>RUN</b>
      </div>
      <div class="tb-item">
        <i class="led-dot led-on"></i><span>Redis</span>
        <b>OK</b>
      </div>
      <div class="tb-item">
        <i class="led-dot led-on"></i><span>DB</span>
        <b>OK</b>
      </div>
      <div class="tb-divider"></div>
      <div class="tb-item"><span>待办吞吐</span><b>{{ throughPut }}/h</b></div>
      <div class="tb-item"><span>调度节拍</span><b>15&nbsp;s</b></div>
    </div>

    <!-- KPI 数据卡 -->
    <el-row :gutter="16" class="mb-16">
      <el-col :span="6">
        <div class="stat-card card-primary">
          <div class="stat-label">PENDING&nbsp;/&nbsp;待办任务</div>
          <div class="stat-value">{{ stats.pendingCount }}</div>
          <div class="stat-footer"><i class="led-dot led-run"></i>今日新增 <b>{{ stats.todayNewCount }}</b> 条</div>
          <el-icon class="stat-icon"><Tickets /></el-icon>
          <!-- 底部角刻度 -->
          <div class="sc-corner sc-tl">SC-01</div>
          <div class="sc-corner sc-br">CH-A</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-danger">
          <div class="stat-label">URGENT&nbsp;/&nbsp;紧急任务</div>
          <div class="stat-value">{{ stats.urgentCount }}</div>
          <div class="stat-footer"><i class="led-dot led-off"></i>超时 <b>{{ stats.overdueCount }}</b> 条待处置</div>
          <el-icon class="stat-icon"><Warning /></el-icon>
          <div class="sc-corner sc-tl">SC-02</div>
          <div class="sc-corner sc-br">CH-B</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-success">
          <div class="stat-label">DONE&nbsp;/&nbsp;今日完成</div>
          <div class="stat-value">{{ stats.todayCompletedCount }}</div>
          <div class="stat-footer"><i class="led-dot led-on"></i>累计完成 <b>{{ stats.completedCount }}</b> 条</div>
          <el-icon class="stat-icon"><Finished /></el-icon>
          <div class="sc-corner sc-tl">SC-03</div>
          <div class="sc-corner sc-br">CH-C</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card card-warn">
          <div class="stat-label">ONTIME&nbsp;/&nbsp;按期完成率</div>
          <div class="stat-value">{{ stats.onTimeRate }}<small>%</small></div>
          <div class="stat-footer"><i class="led-dot led-warn"></i>平均处理 <b>{{ formatDuration(stats.avgHandleDuration) }}</b></div>
          <el-icon class="stat-icon"><TrendCharts /></el-icon>
          <div class="sc-corner sc-tl">SC-04</div>
          <div class="sc-corner sc-br">CH-D</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 左：任务流水表 -->
      <el-col :span="16">
        <div class="card-box panel-primary">
          <div class="industrial-panel__title">
            <List /> TASK QUEUE · 最新待办队列
            <div style="flex:1"></div>
            <div class="toolbar-tags">
              <span class="tt-item"><i class="led-dot led-run"></i>ACTIVE</span>
              <span class="tt-item"><i class="led-dot led-on"></i>SYNC OK</span>
            </div>
            <el-button type="primary" size="small" @click="$router.push('/task/pending')">查看全部</el-button>
          </div>

          <el-table :data="pendingTasks" class="ind-table" @row-click="goDetail" highlight-current-row stripe>
            <el-table-column label="优先级" width="96">
              <template #default="{ row }">
                <el-tag size="small" :class="getPriorityTagClass(row.priority)" effect="dark">
                  {{ row.priorityDesc }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="taskNo" label="TASK ID" width="170">
              <template #default="{ row }"><span class="mono">{{ row.taskNo }}</span></template>
            </el-table-column>
            <el-table-column prop="title" label="任务标题" min-width="240" show-overflow-tooltip />
            <el-table-column prop="taskTypeDesc" label="类型" width="72" />
            <el-table-column prop="externalSystemName" label="来源系统" width="130" />
            <el-table-column prop="initiatorName" label="发起人" width="88" />
            <el-table-column label="DEADLINE" width="160">
              <template #default="{ row }">
                <span class="mono" :class="{ danger: row.isOverdue, warn: row.isNearDeadline && !row.isOverdue }">
                  {{ row.deadlineTime ? formatTime(row.deadlineTime) : '--:--' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="STATUS" width="110" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isOverdue" type="danger" size="small" effect="dark">
                  <i class="led-dot led-off"></i> &nbsp;OVERDUE
                </el-tag>
                <el-tag v-else-if="row.isNearDeadline" type="warning" size="small" effect="dark">
                  <i class="led-dot led-warn"></i> &nbsp;NEAR
                </el-tag>
                <el-tag v-else type="success" size="small" effect="dark">
                  <i class="led-dot led-on"></i> &nbsp;IDLE
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <!-- 右：快捷入口 + 消息 -->
      <el-col :span="8">
        <div class="card-box panel-success mb-16">
          <div class="industrial-panel__title success">
            <User /> QUICK&nbsp;ENTRY · 快捷入口
          </div>
          <div class="quick-grid">
            <div class="quick-item q-primary" @click="$router.push('/task/pending')">
              <el-icon size="26"><Clock /></el-icon>
              <span>待办任务</span>
            </div>
            <div class="quick-item q-success" @click="$router.push('/task/done')">
              <el-icon size="26"><Finished /></el-icon>
              <span>已办任务</span>
            </div>
            <div class="quick-item q-warn" @click="$router.push('/task/statistics')">
              <el-icon size="26"><DataAnalysis /></el-icon>
              <span>任务统计</span>
            </div>
            <div class="quick-item q-info" @click="$router.push('/qms/report')">
              <el-icon size="26"><TrendCharts /></el-icon>
              <span>质量报表</span>
            </div>
          </div>
        </div>

        <div class="card-box panel-warn">
          <div class="industrial-panel__title warn">
            <Bell /> ALERT&nbsp;·&nbsp;消息提醒
            <div style="flex:1"></div>
            <el-badge :value="stats.unreadCount" class="nb">
              <el-icon size="16"><Bell /></el-icon>
            </el-badge>
          </div>
          <el-empty v-if="pendingTasks.length === 0" description="暂无新消息" :image-size="80" />
          <div v-else class="notice-list">
            <div v-for="task in pendingTasks.slice(0, 4)" :key="task.id" class="notice-item" @click="goDetail(task)">
              <div class="ni-left">
                <div class="ni-avatar" :style="{ background: avatarBg(task.priority), borderColor: avatarColor(task.priority) }">
                  {{ task.initiatorName?.charAt(0) }}
                </div>
                <div class="ni-prio" :class="getPriorityTagClass(task.priority)">{{ task.priorityDesc }}</div>
              </div>
              <div class="ni-right">
                <div class="ni-title">{{ task.title }}</div>
                <div class="ni-meta mono">
                  <span>{{ task.externalSystem }}</span>
                  <span>·</span>
                  <span>{{ formatTime(task.receiveTime) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { taskApi, TaskListItem, TaskStatistics } from '@/api/task'
import dayjs from 'dayjs'
import {
  Tickets, Warning, Finished, TrendCharts, List, User, Clock, DataAnalysis, Bell, Cpu
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const currentTime = ref('')
const stats = ref<TaskStatistics>({
  userId: 1, pendingCount: 4, processingCount: 1, completedCount: 2,
  overdueCount: 0, urgentCount: 1, unreadCount: 4, todayNewCount: 2,
  todayCompletedCount: 1, avgHandleDuration: 3300, onTimeRate: 95.5
})
const pendingTasks = ref<TaskListItem[]>([])
const throughPut = computed(() => Math.round((stats.value.todayCompletedCount * 8 + 4)))

let timer: any

const loadStats = async () => {
  try {
    const res = await taskApi.getStatistics(userStore.userInfo.id)
    if (res.code === 200) stats.value = res.data
  } catch (e) {}
}

const loadPending = async () => {
  try {
    const res = await taskApi.queryPage({
      assigneeId: userStore.userInfo.id,
      taskStatus: 0,
      pageNum: 1,
      pageSize: 5,
    })
    if (res.code === 200) pendingTasks.value = res.data.list
  } catch (e) {
    pendingTasks.value = getMockPending()
  }
}

const getMockPending = (): TaskListItem[] => {
  return [
    {
      id: 1002, taskNo: 'TK202608150002', externalSystem: 'ERP', externalSystemName: 'ERP 资源计划',
      taskType: 'APPROVAL', taskTypeDesc: '审批', taskCategory: 'PURCHASE', taskCategoryDesc: '采购',
      priority: 1, priorityDesc: '紧急', title: '【紧急审批】原材料采购订单审批-¥580,000',
      bizKey: 'PO-20260815-001', initiatorId: 4, initiatorName: '王五',
      assigneeId: 1, assigneeName: '系统管理员', assigneeDeptName: '集团总部',
      taskStatus: 0, taskStatusDesc: '待处理',
      receiveTime: dayjs().subtract(2, 'hour').format(),
      deadlineTime: dayjs().add(6, 'hour').format(),
      completeTime: '', isRead: 0, isUrged: 0, urgeCount: 0, isNearDeadline: true, isOverdue: false
    },
    {
      id: 1001, taskNo: 'TK202608150001', externalSystem: 'OA', externalSystemName: 'OA 协同办公',
      taskType: 'APPROVAL', taskTypeDesc: '审批', taskCategory: 'LEAVE', taskCategoryDesc: '请假',
      priority: 2, priorityDesc: '高', title: '【审批】张三-年假申请-5天',
      bizKey: 'LV-2026-0815-001', initiatorId: 2, initiatorName: '张三',
      assigneeId: 1, assigneeName: '系统管理员', assigneeDeptName: '集团总部',
      taskStatus: 0, taskStatusDesc: '待处理',
      receiveTime: dayjs().subtract(4, 'hour').format(),
      deadlineTime: dayjs().add(1, 'day').format(),
      completeTime: '', isRead: 0, isUrged: 0, urgeCount: 0, isNearDeadline: false, isOverdue: false
    },
    {
      id: 1004, taskNo: 'TK202608150004', externalSystem: 'EB', externalSystemName: '企业大脑',
      taskType: 'APPROVAL', taskTypeDesc: '审批', taskCategory: 'QUALITY', taskCategoryDesc: '质量',
      priority: 2, priorityDesc: '高', title: '【审批】不合格品处理申请-NCR20260815001',
      bizKey: 'NCR-20260815-001', initiatorId: 2, initiatorName: '张三',
      assigneeId: 1, assigneeName: '系统管理员', assigneeDeptName: '集团总部',
      taskStatus: 0, taskStatusDesc: '待处理',
      receiveTime: dayjs().subtract(6, 'hour').format(),
      deadlineTime: dayjs().add(2, 'day').format(),
      completeTime: '', isRead: 1, isUrged: 0, urgeCount: 0, isNearDeadline: false, isOverdue: false
    },
  ]
}

const updateTime = () => {
  currentTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
}

const formatTime = (t: string) => t ? dayjs(t).format('MM-DD HH:mm') : '--'
const formatDuration = (sec: number) => {
  if (!sec) return '--'
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  return h > 0 ? `${h}h ${m}m` : `${m}分钟`
}

const getPriorityTagClass = (p: number) => {
  const map: Record<number, string> = { 1: 'tag-urgent', 2: 'tag-high', 3: 'tag-medium', 4: 'tag-low' }
  return map[p] || 'tag-medium'
}
const avatarBg = (p: number) => {
  const map: Record<number, string> = {
    1: 'rgba(255,61,90,0.15)',
    2: 'rgba(255,138,26,0.15)',
    3: 'rgba(26,179,255,0.15)',
    4: 'rgba(90,107,128,0.20)',
  }
  return map[p] || '#409eff'
}
const avatarColor = (p: number) => {
  const map: Record<number, string> = { 1: '#ff3d5a', 2: '#ff8a1a', 3: '#1ab3ff', 4: '#5a6b80' }
  return map[p] || '#1ab3ff'
}

const goDetail = (row: TaskListItem) => {
  router.push({ path: '/task/pending', query: { id: row.id } })
}

onMounted(() => {
  updateTime()
  timer = setInterval(updateTime, 1000)
  loadStats()
  loadPending()
})

onUnmounted(() => clearInterval(timer))
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as v;

.mono { font-family: v.$font-mono; }

/* 页面头 */
.ph-left { display: flex; flex-direction: column; gap: 4px; }
.ph-sub { font-size: 12px; color: v.$text-secondary; letter-spacing: 0.14em; }
.ph-right { display: inline-flex; gap: 10px; }
.ph-tag {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 10px;
  background: v.$panel-bg-deep;
  border: 1px solid v.$border-color;
  border-radius: 2px;
  font-family: v.$font-mono; font-size: 11px; letter-spacing: 0.1em;
  color: v.$text-primary;
  &.warn  { color: v.$warning-color; border-color: rgba(255,176,32,0.4); background: rgba(255,176,32,0.06); }
  &.date  { color: v.$primary-color; border-color: rgba(26,179,255,0.4); background: rgba(26,179,255,0.06); text-shadow: 0 0 4px rgba(26,179,255,0.4); }
}

/* KPI 数据卡 增强 */
.stat-card {
  .stat-value small { font-size: 18px; opacity: 0.8; margin-left: 2px; }
  .stat-footer b { color: v.$text-primary; font-family: v.$font-mono; }
}
.sc-corner {
  position: absolute;
  font-family: v.$font-mono;
  font-size: 10px;
  letter-spacing: 0.1em;
  color: rgba(26,179,255,0.5);
  pointer-events: none;
}
.sc-tl { top: 6px; left: 10px; }
.sc-br { right: 10px; bottom: 6px; }

/* 面板标题右侧工具条 */
.toolbar-tags { display: inline-flex; gap: 8px; margin-right: 12px; }
.tt-item {
  display: inline-flex; align-items: center; gap: 4px;
  font-family: v.$font-mono; font-size: 10px; letter-spacing: 0.12em;
  color: v.$text-secondary; padding: 2px 6px;
  border: 1px dashed v.$border-color; border-radius: 2px;
}

.ind-table { font-size: 13px; }
.danger { color: v.$danger-color !important; text-shadow: 0 0 5px rgba(255,61,90,0.55); }
.warn   { color: v.$warning-color !important; text-shadow: 0 0 5px rgba(255,176,32,0.5); }

/* 快捷入口 */
.quick-grid {
  display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px;
}
.quick-item {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 18px 10px;
  background: linear-gradient(180deg, #152236 0%, #0e1726 100%);
  border: 1px solid v.$border-color;
  cursor: pointer;
  color: v.$text-regular;
  clip-path: polygon(0 6px, 6px 0, 100% 0, 100% calc(100% - 6px), calc(100% - 6px) 100%, 0 100%);
  transition: all .18s;
  position: relative;
  &::before {
    content: '';
    position: absolute; top: 0; left: 0; right: 0; height: 2px;
    background: v.$primary-color; opacity: 0; transition: opacity .18s;
  }
  &:hover {
    color: v.$text-primary;
    transform: translateY(-2px);
    &::before { opacity: 1; }
  }
  span { margin-top: 8px; font-family: v.$font-display; letter-spacing: 0.08em; font-size: 13px; }
}
.q-primary { color: v.$primary-color;  border-color: rgba(26,179,255,0.35); &::before { background: v.$primary-color; } }
.q-success { color: v.$success-color;  border-color: rgba(0,255,148,0.35); &::before { background: v.$success-color; } }
.q-warn    { color: v.$warning-color;  border-color: rgba(255,176,32,0.35); &::before { background: v.$warning-color; } }
.q-info    { color: v.$info-color;     border-color: rgba(122,184,255,0.35); &::before { background: v.$info-color; } }

/* 消息提醒 */
.nb { :deep(.el-badge__content) { background: v.$danger-color; border: 1px solid v.$danger-color; box-shadow: v.$glow-danger; } }
.notice-list { display: flex; flex-direction: column; gap: 2px; }
.notice-item {
  display: flex; align-items: stretch; gap: 12px;
  padding: 10px 8px;
  border: 1px solid transparent;
  border-radius: 2px;
  cursor: pointer;
  transition: all .15s;
  &:hover {
    background: rgba(26,179,255,0.06);
    border-color: rgba(26,179,255,0.25);
  }
}
.ni-left {
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  flex-shrink: 0;
}
.ni-avatar {
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 4px;
  border: 1px solid;
  font-weight: 700; font-family: v.$font-display;
  color: v.$text-primary;
}
.ni-prio {
  font-size: 10px; padding: 1px 6px;
  font-family: v.$font-mono; letter-spacing: 0.1em;
}
.ni-right { min-width: 0; flex: 1; display: flex; flex-direction: column; gap: 4px; justify-content: center; }
.ni-title {
  font-size: 13px; color: v.$text-primary; font-weight: 600;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.ni-meta {
  display: inline-flex; align-items: center; gap: 6px;
  color: v.$text-secondary; font-size: 11px; letter-spacing: 0.06em;
}
</style>
