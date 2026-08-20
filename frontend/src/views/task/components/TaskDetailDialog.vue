<template>
  <el-dialog
    v-model="visible"
    :title="'TASK DETAIL · 任务详情'"
    width="960px"
    custom-class="task-detail-dialog"
    :close-on-click-modal="false"
    @open="handleOpen"
    destroy-on-close
    append-to-body
  >
    <div v-loading="loading" class="task-detail">
      <div v-if="taskDetail" class="detail-body">
        <!-- 顶部工业头：编号铭牌 -->
        <div class="detail-header">
          <div class="dh-top">
            <div class="dh-status-group">
              <el-tag size="large" :type="getPriorityType(taskDetail.priority)" effect="dark" class="mono">
                <i class="led-dot" :class="priorityLedClass(taskDetail.priority)"></i>
                {{ priorityCode(taskDetail.priority) }} · {{ taskDetail.priorityDesc }}
              </el-tag>
              <el-tag size="large" :type="getStatusType(taskDetail.taskStatus)" effect="dark" class="mono status-tag">
                <i class="led-dot" :class="statusLedClass(taskDetail.taskStatus)"></i>
                {{ statusCode(taskDetail.taskStatus) }} · {{ taskDetail.taskStatusDesc }}
              </el-tag>
              <el-tag v-if="taskDetail.isOverdue" type="danger" effect="dark" class="mono">
                <i class="led-dot led-off"></i> OVERDUE · 已超时
              </el-tag>
              <el-tag v-else-if="taskDetail.isNearDeadline" type="warning" effect="dark" class="mono">
                <i class="led-dot led-warn"></i> NEAR · 即将到期
              </el-tag>
            </div>
            <div class="dh-plate mono">
              <span class="plate-label">TASK ID</span>
              <span class="plate-no">{{ taskDetail.taskNo }}</span>
            </div>
          </div>
          <div class="dh-taskmeta mono">
            <span class="tm-item"><i class="led-dot led-run"></i> SOURCE <b>{{ taskDetail.externalSystem }}</b></span>
            <span class="tm-item"><i class="led-dot led-on"></i> TYPE <b>{{ taskDetail.taskType }}</b></span>
            <span class="tm-item"><i class="led-dot led-warn"></i> URGES <b>{{ taskDetail.urgeCount }}</b></span>
            <span class="tm-item"><i class="led-dot led-on"></i> BIZ <b>{{ taskDetail.bizKey || '—' }}</b></span>
          </div>
          <h2 class="task-title">{{ taskDetail.title }}</h2>
        </div>

        <!-- 元信息：descriptions -->
        <div class="card-box panel-primary mb-16">
          <div class="industrial-panel__title">
            <Tickets /> METADATA · 任务元信息
          </div>
          <el-descriptions :column="3" border size="default" class="ind-desc">
            <el-descriptions-item label="任务类型" class="mono-label">
              {{ taskDetail.taskTypeDesc }}
              <el-tag size="small" class="mono" style="margin-left:6px">{{ taskDetail.taskCategory }}</el-tag>
              <span style="margin-left:6px;color:rgba(184,198,217,0.6)">{{ taskDetail.taskCategoryDesc }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="来源系统" class="mono-label">{{ taskDetail.externalSystemName }}</el-descriptions-item>
            <el-descriptions-item label="业务单号" class="mono-label">
              <a v-if="taskDetail.bizKey" @click="openBiz" class="mono biz-link">{{ taskDetail.bizKey }}</a>
              <span v-else style="color:#7a8da6" class="mono">—</span>
            </el-descriptions-item>
            <el-descriptions-item label="发起人" class="mono-label">{{ taskDetail.initiatorName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="当前处理人" class="mono-label">
              <el-tag type="primary" effect="dark" class="mono">{{ taskDetail.assigneeName }}</el-tag>
              <span style="color:#7a8da6; margin-left:6px; font-size:12px">{{ taskDetail.assigneeDeptName }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="催办情况" class="mono-label">
              <span v-if="taskDetail.urgeCount > 0" class="danger-text mono">URGED × {{ taskDetail.urgeCount }}</span>
              <span v-else class="mono" style="color:#7a8da6">NONE</span>
            </el-descriptions-item>
            <el-descriptions-item label="接收时间" class="mono-label"><span class="mono">{{ formatTime(taskDetail.receiveTime) }}</span></el-descriptions-item>
            <el-descriptions-item label="截止时间" class="mono-label">
              <span class="mono" :class="{ 'danger-text': taskDetail.isOverdue, 'warn-text': !taskDetail.isOverdue && taskDetail.isNearDeadline }">
                {{ formatTime(taskDetail.deadlineTime) }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="处理时长" class="mono-label">
              <el-tag v-if="taskDetail.handleDurationDisplay" type="success" effect="dark" class="mono">{{ taskDetail.handleDurationDisplay }}</el-tag>
              <span v-else style="color:#7a8da6" class="mono">—</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 任务内容 -->
        <div v-if="taskDetail.content" class="card-box mb-16">
          <div class="industrial-panel__title">
            <Promotion /> CONTENT · 任务内容
          </div>
          <div class="content-text">{{ taskDetail.content }}</div>
        </div>

        <!-- 处理结果 -->
        <div v-if="taskDetail.actionComment && (taskDetail.taskStatus === 2 || taskDetail.taskStatus === 3)"
             class="card-box mb-16"
             :class="taskDetail.actionResult === 'AGREE' ? 'panel-success' : 'panel-danger'">
          <div class="industrial-panel__title" :class="taskDetail.actionResult === 'AGREE' ? 'success' : 'danger'">
            <ChatDotRound /> ACTION&nbsp;RESULT · 处理结果
            <div style="flex:1"></div>
            <el-tag :type="taskDetail.actionResult === 'AGREE' ? 'success' : 'danger'" effect="dark" class="mono">
              <i class="led-dot" :class="taskDetail.actionResult === 'AGREE' ? 'led-on' : 'led-off'"></i>
              {{ taskDetail.actionResult }} · {{ taskDetail.actionResultDesc }}
            </el-tag>
          </div>
          <div class="content-text">{{ taskDetail.actionComment }}</div>
        </div>

        <!-- 流转记录 -->
        <div class="card-box">
          <div class="industrial-panel__title">
            <View /> FLOW&nbsp;LOG · 审批流转记录
            <div style="flex:1"></div>
            <span class="mono" style="font-size:11px; color:#7a8da6; letter-spacing:0.1em">
              STEPS = {{ taskDetail.historyList?.length || 0 }}
            </span>
          </div>
          <div class="timeline-box">
            <el-timeline>
              <el-timeline-item
                v-for="(h, i) in taskDetail.historyList"
                :key="h.id"
                :timestamp="formatFullTime(h.createTime)"
                placement="top"
                :type="getTimelineType(h.actionType)"
                :icon="getTimelineIcon(h.actionType)"
                size="large"
              >
                <div class="timeline-item">
                  <div class="timeline-header">
                    <strong>{{ h.handlerName }}</strong>
                    <span class="mono dept-name">{{ h.handlerDeptName || '' }}</span>
                    <el-tag size="small" :type="getTimelineType(h.actionType)" effect="dark" class="mono">
                      {{ h.actionType || 'WAIT' }} · {{ h.actionTypeDesc }}
                    </el-tag>
                    <el-tag v-if="h.actionResultDesc" size="small" effect="dark" class="mono res-tag">
                      {{ h.actionResultDesc }}
                    </el-tag>
                    <span v-if="h.handleDurationDisplay" class="mono duration-tag">
                      T+{{ h.handleDurationDisplay }}
                    </span>
                  </div>
                  <div v-if="h.actionComment" class="timeline-comment">
                    {{ h.actionComment }}
                  </div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </div>
      </div>
    </div>

    <template #footer v-if="taskDetail && showActions">
      <div class="detail-footer">
        <div class="footer-label mono">
          <i class="led-dot led-run"></i> ACTION · 操作区 &nbsp;|&nbsp; 处理意见
        </div>
        <el-input
          v-model="handleForm.comment"
          placeholder="▶ 请输入处理意见（选填）"
          type="textarea"
          :rows="2"
          style="margin-bottom: 12px"
          class="action-area"
        />
        <div class="btn-row">
          <el-button @click="handleAction('CLAIM')" v-if="taskDetail.taskStatus === 0" :icon="Check">
            <span class="mono">CLAIM · 领取</span>
          </el-button>
          <el-button type="success" @click="handleAction('APPROVE')" :icon="CircleCheck">
            <span class="mono">AGREE · 同意</span>
          </el-button>
          <el-button type="danger" @click="handleAction('REJECT')" :icon="CircleClose">
            <span class="mono">REJECT · 驳回</span>
          </el-button>
          <el-button type="warning" @click="showTransfer = true" :icon="Switch">
            <span class="mono">TRANSFER · 转办</span>
          </el-button>
          <el-button @click="handleAction('URGE')" v-if="taskDetail.taskStatus !== 2 && taskDetail.taskStatus !== 3" :icon="BellFilled">
            <span class="mono">URGE · 催办</span>
          </el-button>
        </div>
      </div>
    </template>

    <!-- 转办子窗 -->
    <el-dialog
      v-model="showTransfer"
      title="TRANSFER · 转办任务"
      width="540px"
      append-to-body
      destroy-on-close
      custom-class="task-detail-dialog"
    >
      <el-form label-width="90px" class="mono-form">
        <el-form-item label="转办给" class="mono-label">
          <el-select v-model="handleForm.transferToUserId" placeholder="→ 请选择处理人" style="width: 100%" class="mono">
            <el-option label="U002  张三 (质量管理部)" :value="2" />
            <el-option label="U003  李四 (生产制造部)" :value="3" />
            <el-option label="U004  王五 (采购供应链部)" :value="4" />
            <el-option label="U005  赵六 (技术研发部)" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="转办原因" class="mono-label">
          <el-input
            v-model="handleForm.transferReason"
            type="textarea"
            :rows="3"
            placeholder="▶ 请输入转办原因（必填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTransfer = false"><span class="mono">CANCEL</span></el-button>
        <el-button type="primary" :loading="actionLoading" @click="confirmTransfer">
          <span class="mono">CONFIRM · 确认转办</span>
        </el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { taskApi, TaskDetail, TaskHandleParams } from '@/api/task'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Check, CircleCheck, CircleClose, Switch, BellFilled,
  Plus, ChatDotRound, Promotion, Warning, View, Tickets
} from '@element-plus/icons-vue'
import type { Component } from 'vue'

const props = defineProps<{ modelValue: boolean; taskId: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; 'refresh': [] }>()

const userStore = useUserStore()
const visible = computed({ get: () => props.modelValue, set: (v) => emit('update:modelValue', v) })
const loading = ref(false)
const actionLoading = ref(false)
const taskDetail = ref<TaskDetail | null>(null)
const showTransfer = ref(false)

const handleForm = reactive({
  comment: '',
  transferToUserId: null as number | null,
  transferToUserName: '',
  transferReason: '',
})

const showActions = computed(() => {
  if (!taskDetail.value) return false
  return taskDetail.value.taskStatus === 0 || taskDetail.value.taskStatus === 1
})

const handleOpen = async () => {
  if (props.taskId) {
    await loadDetail(props.taskId)
  }
}

watch(() => props.taskId, (id) => { if (id && visible.value) loadDetail(id) })

const loadDetail = async (id: number) => {
  loading.value = true
  try {
    const res = await taskApi.getDetail(id, userStore.userInfo.id)
    if (res.code === 200) {
      taskDetail.value = res.data
    }
  } catch (e) {
    taskDetail.value = getMockDetail(id)
  } finally {
    loading.value = false
  }
}

const getMockDetail = (id: number): TaskDetail => {
  const isMock = id === 1001 || id === 1002 || id === 1004
  return {
    id, taskNo: `TK20260815${String(id).slice(-4)}`,
    externalTaskId: id === 1001 ? 'OA-2026-00881' : '',
    externalSystem: id === 1002 ? 'ERP' : id === 1001 ? 'OA' : 'EB',
    externalSystemName: id === 1002 ? '企业资源计划系统' : id === 1001 ? '协同办公系统' : '企业大脑平台',
    taskType: 'APPROVAL', taskTypeDesc: '审批',
    taskCategory: id === 1001 ? 'LEAVE' : id === 1002 ? 'PURCHASE' : 'QUALITY',
    taskCategoryDesc: id === 1001 ? '请假' : id === 1002 ? '采购' : '质量',
    priority: id === 1002 ? 1 : 2,
    priorityDesc: id === 1002 ? '紧急' : '高',
    title: isMock
      ? (id === 1001 ? '【审批】张三-年假申请-5天' : id === 1002 ? '【紧急审批】原材料采购订单审批-¥580,000' : '【审批】不合格品处理申请-NCR20260815001')
      : '审批任务标题',
    content: isMock
      ? (id === 1001 ? '申请日期：2026-08-20 至 2026-08-24，共5天年假，请审批。原因：个人事务处理。' :
        id === 1002 ? '采购供应商：XX材料科技有限公司，采购A类原材料10吨，单价58,000元/吨，合计580,000元。生产紧急需求，请尽快审批。' :
        '质量异常报告：IQC来料检验发现批次B2026081401的电子元件不合格，申请退货处理，涉及金额¥35,000。')
      : '任务内容详情',
    bizKey: `BIZ-${id}`, bizUrl: '', ccUserIds: '',
    initiatorId: 2, initiatorName: id === 1002 ? '王五' : '张三',
    assigneeId: 1, assigneeName: '系统管理员', assigneeDeptId: 1, assigneeDeptName: '集团总部',
    taskStatus: 0, taskStatusDesc: '待处理',
    receiveTime: dayjs().subtract(2, 'hour').format(),
    deadlineTime: dayjs().add(1, 'day').format(),
    startProcessTime: '', completeTime: '', handleDuration: 0, handleDurationDisplay: '',
    isRead: 0, isUrged: 0, urgeCount: 0, actionResult: '', actionResultDesc: '', actionComment: '',
    isNearDeadline: id === 1002, isOverdue: false,
    historyList: [
      {
        id: 1, taskId: id, nodeCode: 'START', nodeName: '任务创建',
        handlerId: 2, handlerName: id === 1002 ? '王五' : '张三',
        handlerDeptId: 3, handlerDeptName: id === 1002 ? '采购供应链部' : '质量管理部',
        actionType: 'CREATE', actionTypeDesc: '创建', actionResult: '', actionResultDesc: '',
        actionComment: '提交审批申请', attachments: '', handleDuration: 0, handleDurationDisplay: '',
        createTime: dayjs().subtract(2, 'hour').format()
      },
      {
        id: 2, taskId: id, nodeCode: 'APPROVE', nodeName: '审批节点',
        handlerId: 1, handlerName: '系统管理员',
        handlerDeptId: 1, handlerDeptName: '集团总部',
        actionType: '', actionTypeDesc: '待处理', actionResult: '', actionResultDesc: '',
        actionComment: '等待处理中...', attachments: '', handleDuration: 0, handleDurationDisplay: '',
        createTime: dayjs().subtract(2, 'hour').add(1, 'minute').format()
      }
    ]
  }
}

const formatTime = (t: string) => t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '--'
const formatFullTime = (t: string) => t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '--'

const getPriorityType = (p: number): 'primary' | 'success' | 'warning' | 'danger' | 'info' => {
  const map: Record<number, any> = { 1: 'danger', 2: 'warning', 3: 'primary', 4: 'info' }
  return map[p] || 'info'
}

const getStatusType = (s: number): 'primary' | 'success' | 'warning' | 'danger' | 'info' => {
  const map: Record<number, any> = { 0: 'warning', 1: 'primary', 2: 'success', 3: 'danger', 5: 'danger', 6: 'info' }
  return map[s] || 'info'
}

const getTimelineType = (type: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' => {
  switch (type) {
    case 'CREATE': return 'primary'
    case 'APPROVE': return 'success'
    case 'REJECT': return 'danger'
    case 'TRANSFER': return 'warning'
    case 'CLAIM': return 'primary'
    case 'URGE': return 'danger'
    case 'COMMENT': return 'info'
    default: return 'info'
  }
}

const getTimelineIcon = (type: string): Component | undefined => {
  switch (type) {
    case 'CREATE': return Plus
    case 'APPROVE': return CircleCheck
    case 'REJECT': return CircleClose
    case 'TRANSFER': return Switch
    case 'CLAIM': return View
    case 'URGE': return Warning
    case 'COMMENT': return ChatDotRound
    default: return Promotion
  }
}

// 工业风状态与优先级代码 + 指示灯 class
const priorityCode = (p: number) => ({ 1: 'P0', 2: 'P1', 3: 'P2', 4: 'P3' } as any)[p] ?? 'P3'
const priorityLedClass = (p: number) =>
  p === 1 ? 'led-off' : p === 2 ? 'led-warn' : p === 3 ? 'led-run' : 'led-on'
const statusCode = (s: number) =>
  ({ 0: 'PEND', 1: 'PROC', 2: 'DONE', 3: 'RJCT', 5: 'CNCL', 6: 'FIN' } as any)[s] ?? 'WAIT'
const statusLedClass = (s: number) =>
  s === 2 ? 'led-on' : s === 0 || s === 6 ? 'led-warn' : s === 3 || s === 5 ? 'led-off' : 'led-run'

const openBiz = () => ElMessage.info('跳转至业务系统详情页')

const handleAction = async (type: string) => {
  if (!taskDetail.value) return
  if (type === 'APPROVE' || type === 'REJECT') {
    const confirmMsg = type === 'APPROVE' ? '确认同意此任务？' : '确认驳回此任务？'
    await ElMessageBox.confirm(confirmMsg, '提示', { type: 'warning' })
  }

  const params: TaskHandleParams = {
    taskId: taskDetail.value.id,
    handlerId: userStore.userInfo.id,
    handlerName: userStore.userInfo.realName,
    handlerDeptId: userStore.userInfo.deptId,
    handlerDeptName: userStore.userInfo.deptName,
    actionType: type,
    actionResult: type === 'APPROVE' ? 'AGREE' : type === 'REJECT' ? 'REJECT' : '',
    actionComment: handleForm.comment,
  }

  actionLoading.value = true
  try {
    const res = await taskApi.handle(params)
    if (res.code === 200) {
      ElMessage.success(type === 'URGE' ? '催办成功' : type === 'CLAIM' ? '已领取任务' : '操作成功')
      handleForm.comment = ''
      emit('refresh')
      visible.value = false
    }
  } catch (e) {
    ElMessage.success('操作成功（演示环境）')
    handleForm.comment = ''
    emit('refresh')
    visible.value = false
  } finally {
    actionLoading.value = false
  }
}

const confirmTransfer = async () => {
  if (!taskDetail.value || !handleForm.transferToUserId) {
    ElMessage.warning('请选择转办对象')
    return
  }
  const userMap: Record<number, string> = { 2: '张三', 3: '李四', 4: '王五', 5: '赵六' }
  const params: TaskHandleParams = {
    taskId: taskDetail.value.id,
    handlerId: userStore.userInfo.id,
    handlerName: userStore.userInfo.realName,
    handlerDeptId: userStore.userInfo.deptId,
    handlerDeptName: userStore.userInfo.deptName,
    actionType: 'TRANSFER',
    actionComment: handleForm.comment,
    transferToUserId: handleForm.transferToUserId,
    transferToUserName: userMap[handleForm.transferToUserId],
    transferReason: handleForm.transferReason,
  }
  actionLoading.value = true
  try {
    const res = await taskApi.handle(params)
    if (res.code === 200) {
      ElMessage.success('转办成功')
      showTransfer.value = false
      Object.assign(handleForm, { transferToUserId: null, transferToUserName: '', transferReason: '', comment: '' })
      emit('refresh')
      visible.value = false
    }
  } catch (e) {
    ElMessage.success('转办成功（演示环境）')
    showTransfer.value = false
    Object.assign(handleForm, { transferToUserId: null, transferToUserName: '', transferReason: '', comment: '' })
    emit('refresh')
    visible.value = false
  } finally {
    actionLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as v;

.mono { font-family: v.$font-mono; letter-spacing: 0.04em; }

.led-dot {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  margin-right: 6px; background: v.$text-secondary;
  &.led-on   { background: v.$success-color; box-shadow: v.$glow-success; }
  &.led-warn { background: v.$warning-color; box-shadow: v.$glow-warn; }
  &.led-off  { background: v.$danger-color;  box-shadow: v.$glow-danger; }
  &.led-run  { background: v.$primary-color; box-shadow: v.$glow-primary; animation: pulse 1.2s infinite; }
}
@keyframes pulse { 0%,100% { opacity:1; } 50% { opacity: 0.4; } }

.task-detail {
  .detail-header {
    position: relative;
    padding: 16px 20px 18px;
    background: v.$panel-bg-deep;
    border: 1px solid v.$border-color;
    border-left: 4px solid v.$primary-color;
    margin-bottom: 18px;
    &::before, &::after {
      content: ''; position: absolute; width: 12px; height: 12px;
      border-color: v.$primary-color;
    }
    &::before { top: -1px; right: -1px; border-top: 1px solid; border-right: 1px solid; }
    &::after  { bottom: -1px; left: -1px; border-bottom: 1px solid; border-left: 1px solid; }

    .dh-top {
      display: flex; align-items: center; justify-content: space-between;
      margin-bottom: 12px; flex-wrap: wrap; gap: 12px;
    }
    .dh-status-group { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    .status-tag { margin-left: 0; }

    .dh-plate {
      display: inline-flex; align-items: center; gap: 10px;
      padding: 6px 14px; background: v.$bg-color;
      border: 1px solid v.$primary-color;
      border-radius: 2px;
      box-shadow: inset 0 0 20px rgba(26,179,255,0.15);
      .plate-label {
        font-size: 10px; letter-spacing: 0.2em; color: v.$text-secondary;
        padding: 2px 6px; border-right: 1px solid v.$border-color;
      }
      .plate-no { color: v.$primary-color; font-size: 14px; font-weight: 700; text-shadow: 0 0 6px rgba(26,179,255,0.5); }
    }

    .dh-taskmeta {
      margin: 0 0 12px; padding: 8px 12px;
      background: rgba(10,16,25,0.6);
      border: 1px dashed v.$border-color;
      font-size: 12px; letter-spacing: 0.14em;
      color: v.$text-secondary;
      display: flex; gap: 22px; flex-wrap: wrap;
      .tm-item { display: inline-flex; align-items: center; b { color: v.$text-primary; font-weight: 700; } }
    }

    .task-title {
      margin: 0;
      font-size: 20px; font-weight: 700;
      color: #eaf3ff;
      letter-spacing: 0.02em;
      text-shadow: 0 0 1px rgba(234,243,255,0.4);
    }
  }

  .ind-desc {
    :deep(.el-descriptions__label) {
      background: rgba(10,16,25,0.6) !important;
      color: v.$text-secondary !important;
      border-color: v.$border-color !important;
      width: 120px;
    }
    :deep(.el-descriptions__body) {
      background: v.$panel-bg !important;
      color: v.$text-primary !important;
      border-color: v.$border-color !important;
    }
    :deep(.el-descriptions__cell) { border-color: v.$border-color !important; }
  }
  .mono-label :deep(.el-descriptions__label) { font-family: v.$font-mono !important; letter-spacing: 0.1em; }

  .biz-link {
    color: v.$primary-color; text-decoration: none; font-weight: 600;
    &:hover { text-shadow: 0 0 6px rgba(26,179,255,0.7); }
  }
  .danger-text { color: v.$danger-color !important; font-weight: 600; text-shadow: 0 0 4px rgba(255,61,90,0.5); }
  .warn-text   { color: v.$warning-color !important; text-shadow: 0 0 4px rgba(255,176,32,0.5); }

  .content-text {
    color: v.$text-primary;
    line-height: 1.85;
    white-space: pre-wrap;
    padding: 14px 16px;
    background: rgba(10,16,25,0.55);
    border-left: 3px solid v.$primary-color;
    border-radius: 2px;
  }

  .timeline-box {
    padding: 6px 4px 4px;
    :deep(.el-timeline) { padding-left: 10px; }
    :deep(.el-timeline-item__timestamp.is-top) { color: v.$text-secondary !important; font-family: v.$font-mono; margin-bottom: 8px; }
    :deep(.el-timeline-item__tail) { border-color: v.$border-color !important; border-left-style: dashed !important; }
    :deep(.el-timeline-item__node) { background: v.$bg-color !important; border-width: 2px !important; }
  }

  .timeline-item {
    padding: 10px 12px;
    background: rgba(19,28,40,0.7);
    border: 1px solid v.$border-color;
    border-radius: 2px;
    .timeline-header {
      display: flex; align-items: center; flex-wrap: wrap; gap: 8px;
      margin-bottom: 6px;
      strong { color: v.$text-primary; }
      .dept-name {
        color: v.$text-secondary; font-size: 12px;
        padding: 1px 6px; border-left: 1px dashed v.$border-color; border-right: 1px dashed v.$border-color;
      }
      .res-tag { background: rgba(255,255,255,0.05) !important; }
      .duration-tag {
        margin-left: auto;
        color: v.$success-color; font-size: 12px;
        padding: 2px 8px; border: 1px solid rgba(0,255,148,0.3);
      }
    }
    .timeline-comment {
      color: #c7d4e8;
      background: rgba(10,16,25,0.7);
      padding: 10px 14px;
      border-left: 3px solid v.$primary-color;
      line-height: 1.7;
      margin-top: 6px;
    }
  }
}

.detail-footer {
  width: 100%;
  .footer-label {
    padding: 4px 10px; margin-bottom: 10px;
    font-size: 12px; letter-spacing: 0.14em; color: v.$text-secondary;
    border-bottom: 1px dashed v.$border-color;
    display: inline-flex; align-items: center;
  }
  .action-area :deep(.el-textarea__inner) { font-family: v.$font-mono; }
  .btn-row { display: flex; gap: 10px; justify-content: flex-end; flex-wrap: wrap; }
}
</style>

<style lang="scss">
/* 全局弹层覆盖 */
.task-detail-dialog {
  background: #0e141c !important;
  :deep(.el-dialog__header) {
    background: linear-gradient(90deg, #0a1019 0%, #131c28 100%);
    border-bottom: 1px solid #1f2c3f;
    padding: 14px 22px;
    margin-right: 0;
    .el-dialog__title {
      font-family: 'Rajdhani', 'JetBrains Mono', 'Microsoft YaHei', sans-serif;
      color: #eaf3ff; letter-spacing: 0.2em; font-weight: 700; font-size: 16px;
      text-shadow: 0 0 2px rgba(234,243,255,0.4);
    }
  }
  :deep(.el-dialog__body) {
    background: #0e141c;
    padding: 18px 22px;
    color: #eaf3ff;
  }
  :deep(.el-dialog__footer) {
    background: #0a1019;
    border-top: 1px solid #1f2c3f;
    padding: 14px 22px;
  }
}
.mono-form :deep(.el-form-item__label) {
  font-family: 'JetBrains Mono', monospace;
  letter-spacing: 0.08em;
}
</style>
