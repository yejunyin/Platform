import { request } from '@/utils/request'
import type { Task, Material, Call, Alert } from '@/views/task/MaterialCall/types'

const baseUrl = '/material-call'

export const materialCallApi = {
  /** 从 MES mes_dwd_productOrder 拉取生产任务列表 */
  listTasks: () => request.get<Task[]>(`${baseUrl}/tasks`),

  /** 拉取备料明细；taskId 可空（空=查全部） */
  listMaterials: (taskId?: string) =>
    request.get<Material[]>(`${baseUrl}/materials`, { params: taskId ? { taskId } : undefined }),

  /** 同步备料明细：BOMflag=1 直接返回；BOMflag=0 调用金蝶 ERP 拉取后写入并返回 */
  syncMaterials: (taskId: string) =>
    request.get<Material[]>(`${baseUrl}/materials/sync`, { params: { taskId } }),

  /** 备料（标记备料中）：更新 mes_dwd_material_detail 状态为 preparing */
  startPrepare: (id: number) =>
    request.post<Material>(`${baseUrl}/materials/${id}/start-prepare`),

  /** 备齐：累加 prepared_qty，按是否满足需求置 ready/shortage */
  prepareMaterial: (id: number, data: { qty: number; preparer: string }) =>
    request.post<Material>(`${baseUrl}/materials/${id}/prepare`, data),

  /** 拉取叫料记录；status: all/pending/delivering/delivered */
  listCalls: (status: string = 'all') =>
    request.get<Call[]>(`${baseUrl}/calls`, { params: { status } }),

  /** 叫料：写入一条 pending 叫料记录，返回创建后的记录（含真实 id） */
  createCall: (data: {
    taskId: string
    materialCode: string
    materialName: string
    requiredQty: number
    callQty: number
    callType: 'normal' | 'urgent'
    caller: string
    remark?: string | null
  }) => request.post<Call>(`${baseUrl}/calls`, data),

  /** 响应叫料：状态 pending -> delivering */
  respondCall: (id: number, data: { responder: string }) =>
    request.post<Call>(`${baseUrl}/calls/${id}/respond`, data),

  /** 确认送达：状态 delivering -> delivered，同时累加备料数量、解决预警 */
  deliverCall: (id: number) =>
    request.post<Call>(`${baseUrl}/calls/${id}/deliver`),

  /** 拉取预警列表；status: all/active/resolved */
  listAlerts: (status: string = 'all') =>
    request.get<Alert[]>(`${baseUrl}/alerts`, { params: { status } }),
}

