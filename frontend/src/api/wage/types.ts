// 工资核算模块类型定义（字段名与后端/数据库一致，使用规范命名）
// 注意：后端 Jackson 全局配置 Long → String，所有主键/外键 ID 都按 string 处理，
// 避免 JS Number 精度丢失（雪花 ID 19 位 > Number.MAX_SAFE_INTEGER ≈ 9e15）

/** 人员小组 */
export interface WageGroup {
  id: string
  groupName: string
  leaderNo?: string
  defaultProcess?: string
  coefSum: number
  memberCount: number
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 员工工资资料（sys_user 左联 wage_staff_ext / wage_group） */
export interface WageStaff {
  userId: string
  username: string
  realName: string
  /** 在职状态 1在职 0离职（sys_user.status） */
  status: number
  extId?: string
  jobType: string
  groupId?: string
  groupName?: string
  coef: number
  hireDate?: string
}

export interface WageStaffSave {
  username: string
  realName: string
  jobType: string
  groupId?: string
  coef: number
  hireDate?: string
}

export type WageStaffUpdate = Omit<WageStaffSave, 'username'>

/** 工序基础资料 */
export interface WageProcess {
  id: string
  processCode: string
  processName: string
  workshop?: string
  remark?: string
}

/** 单价工序资料 */
export interface WagePrice {
  id: string
  materialCode: string
  materialName?: string
  processName: string
  unitPrice: number
  effectiveDate?: string
  expiryDate?: string
  status: string
}

/** 生产任务单 */
export interface WageOrder {
  id: string
  taskNo: string
  feedNo?: string
  productName?: string
  materialCode?: string
  subMaterialName?: string
  productQty?: number
  processName?: string
  workshop?: string
  unitPrice: number
  groupId?: string
  groupName?: string
  confirmStatus: string
  bizStatus: string
  startTime?: string
  finishUser?: string
  finishQty?: number
  finishTime?: string
  stockInQty?: number
  settleStatus: string
  syncTime?: string
}

/** 生产用料清单 */
export interface WageMaterial {
  id: string
  taskNo: string
  feedNo?: string
  bizStatus: string
  materialCode: string
  materialName?: string
  unit?: string
  qty: number
}

/** 计件统计明细 */
export interface WagePayStat {
  id: string
  wageMonth: string
  taskNo: string
  groupId?: string
  groupName?: string
  staffName: string
  staffNo: string
  jobType?: string
  productName?: string
  unit?: string
  workPrice?: number
  qty?: number
  amount?: number
  startTime?: string
  processName?: string
  materialCode?: string
  subMaterialName?: string
  subQty?: number
  subPrice?: number
  detailAmount?: number
  groupAmount?: number
  coef?: number
  shareRatio?: number
  reviewer?: string
  reviewTime?: string
  entryUser?: string
  remark?: string
}

/** 工资汇总行 */
export interface WageSummary {
  wageMonth: string
  staffNo: string
  staffName: string
  groupId?: string
  groupName?: string
  jobType?: string
  detailCount: number
  pieceAmount: number
  auditStatus: string
  wageNo: string
}

/** 后端内存分页结果 */
export interface WagePage<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  totalPages: number
}

/** 金蝶《生产用料清单》(PRD_PPBOM) 专项查询行 */
export interface PpbomRow {
  ppbomBillNo?: string
  moBillNo?: string
  productCode?: string
  productName?: string
  materialCode?: string
  materialName?: string
  model?: string
  needQty?: number
  unitName?: string
  createDate?: string
}
