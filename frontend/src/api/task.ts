import { request, PageResult } from '@/utils/request'

export interface TaskQueryParams {
  pageNum?: number
  pageSize?: number
  assigneeId?: number
  taskStatus?: number
  externalSystem?: string
  taskType?: string
  taskCategory?: string
  priority?: number
  keyword?: string
  isRead?: number
  assigneeDeptId?: number
  initiatorId?: number
}

export interface TaskCreateParams {
  externalTaskId?: string
  externalSystem: string
  taskType: string
  taskCategory?: string
  priority?: number
  title: string
  content?: string
  bizKey?: string
  bizUrl?: string
  initiatorId?: number
  initiatorName?: string
  assigneeId: number
  assigneeName: string
  assigneeDeptId?: number
  assigneeDeptName?: string
  ccUserIds?: string
  deadlineTime?: string
  sortWeight?: number
  extData?: string
}

export interface TaskHandleParams {
  taskId: number
  handlerId: number
  handlerName: string
  handlerDeptId?: number
  handlerDeptName?: string
  actionType: string
  actionResult?: string
  actionComment?: string
  transferToUserId?: number
  transferToUserName?: string
  transferReason?: string
}

export interface TaskListItem {
  id: number
  taskNo: string
  externalSystem: string
  externalSystemName: string
  taskType: string
  taskTypeDesc: string
  taskCategory: string
  taskCategoryDesc: string
  priority: number
  priorityDesc: string
  title: string
  bizKey: string
  initiatorId: number
  initiatorName: string
  assigneeId: number
  assigneeName: string
  assigneeDeptName: string
  taskStatus: number
  taskStatusDesc: string
  receiveTime: string
  deadlineTime: string
  completeTime: string
  isRead: number
  isUrged: number
  urgeCount: number
  isNearDeadline: boolean
  isOverdue: boolean
}

export interface TaskHistory {
  id: number
  taskId: number
  nodeCode: string
  nodeName: string
  handlerId: number
  handlerName: string
  handlerDeptId: number
  handlerDeptName: string
  actionType: string
  actionTypeDesc: string
  actionResult: string
  actionResultDesc: string
  actionComment: string
  attachments: string
  handleDuration: number
  handleDurationDisplay: string
  createTime: string
}

export interface TaskDetail extends TaskListItem {
  externalTaskId: string
  content: string
  bizUrl: string
  ccUserIds: string
  startProcessTime: string
  handleDuration: number
  handleDurationDisplay: string
  actionResult: string
  actionResultDesc: string
  actionComment: string
  historyList: TaskHistory[]
}

export interface TaskStatistics {
  userId: number
  pendingCount: number
  processingCount: number
  completedCount: number
  overdueCount: number
  urgentCount: number
  unreadCount: number
  todayNewCount: number
  todayCompletedCount: number
  avgHandleDuration: number
  onTimeRate: number
}

const baseUrl = '/task'

export const taskApi = {
  create: (data: TaskCreateParams) => request.post<number>(baseUrl, data),

  queryPage: (params: TaskQueryParams) =>
    request.get<PageResult<TaskListItem>>(`${baseUrl}/page`, { params }),

  getDetail: (taskId: number, userId?: number) =>
    request.get<TaskDetail>(`${baseUrl}/${taskId}`, { params: { userId } }),

  handle: (data: TaskHandleParams) => request.post<boolean>(`${baseUrl}/handle`, data),

  markReadBatch: (taskIds: number[], userId: number) =>
    request.post<number>(`${baseUrl}/read/batch`, taskIds, { params: { userId } }),

  markAllRead: (assigneeId: number) =>
    request.post<number>(`${baseUrl}/read/all`, null, { params: { assigneeId } }),

  getStatistics: (userId: number) =>
    request.get<TaskStatistics>(`${baseUrl}/statistics`, { params: { userId } }),
}
