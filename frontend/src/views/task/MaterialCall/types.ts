// 物料呼叫系统 · 类型定义

export type TaskStatus = 'pending' | 'preparing' | 'ready' | 'calling'
export type MaterialStatus = 'pending' | 'preparing' | 'ready' | 'shortage'
export type CallStatus = 'pending' | 'delivering' | 'delivered'
export type AlertLevel = 'danger' | 'warning' | 'info'
export type Priority = 'urgent' | 'high' | 'normal' | 'low'
export type ViewKey = 'dashboard' | 'tasks' | 'materials' | 'calls' | 'alerts'
export type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'

export interface DashboardStats {
  tasks: { total: number; pending: number; preparing: number; ready: number; calling: number }
  materials: { total: number; ready: number; preparing: number; pending: number; shortage: number }
  calls: { active: number }
  alerts: { active: number }
  completionRate: number
}

export interface Task {
  id: string
  order_no: string
  product_code: string
  product_name: string
  quantity: number
  unit: string
  workshop: string
  line: string
  planned_start: string
  planned_end: string
  status: TaskStatus
  schedulepriority: number | null
  computed_status?: TaskStatus
  progress?: number
  material_count?: number
  shortage_count?: number
  materials?: Material[]
}

export interface TaskComputed extends Task {
  computed_status: TaskStatus
  progress: number
  material_count: number
  shortage_count: number
}

export interface Material {
  id: number
  task_id: string
  material_code: string
  material_name: string
  specification: string
  unit: string
  required_qty: number
  available_qty: number
  prepared_qty: number
  storage_location: string
  status: MaterialStatus
  preparer: string | null
  prepare_time: string | null
  remark: string | null
}

export interface MaterialWithMeta extends Material {
  canAutoFill: boolean
  shortage: number
}

export interface Call {
  id: number
  task_id: string
  material_code: string
  material_name: string
  required_qty: number
  call_qty: number
  call_type: 'normal' | 'urgent'
  caller: string
  call_time: string
  status: CallStatus
  responder: string | null
  response_time: string | null
  deliver_time: string | null
  remark: string | null
}

export interface Alert {
  id: number
  task_id: string
  type: 'shortage' | 'low_stock' | 'overtime'
  level: AlertLevel
  message: string
  status: 'active' | 'resolved'
  created_at: string
}

export interface MaterialCallDB {
  tasks: Task[]
  materials: Material[]
  calls: Call[]
  alerts: Alert[]
  nextCallId: number
  nextAlertId: number
}
