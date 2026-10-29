import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

/** 金蝶 PRD_PPBOM 查询行（WMS 叫料页面使用） */
export interface PpbomRow {
  ppbomBillNo?: string
  productionNo?: string
  productCode?: string
  productName?: string
  materialCode?: string
  materialName?: string
  needQty?: number
  /** 已领数量（FPickedQty，金蝶 PRD_PPBOM 累计已领） */
  pickedQty?: number
  /** 单位编码（推 WMS UnitCode 用，如 PCS/EA/KG） */
  unitNumber?: string
  /** 单位名称（展示用，如：个/台/千克） */
  unitName?: string
  /** 计划跟踪号（PRD_PPBOM.FMtoNo，推 WMS PlanCode 用） */
  planCode?: string
  /** 发料仓库（FPickStockId，基础资料属性，中文仓库名） */
  pickStock?: string

  // ============ 本地累计数据（后端查询时填充） ============
  /** 叫料已推送 WMS 数量（wms_picking_entry.pickedQty 汇总，status=sent） */
  pushedQty?: number
  /** 定时领料任务中该物料的发料总数量（wms_pick_task_entry.totalQty 汇总，排除 cancelled） */
  pickTaskTotalQty?: number
  /** 定时领料任务中该物料已发数量（wms_pick_task_entry.sentQty 汇总） */
  pickTaskSentQty?: number
  /** 剩余可领数量 = needQty - pushedQty - pickTaskTotalQty */
  remainingQty?: number

  /** 即时库存数量（金蝶 STK_Inventory，仓库 FStockId.FNumber='2010'） */
  immediateStockQty?: number
}

/** 领料单头 */
export interface PickingHead {
  id: number
  billNo: string
  productionNo: string
  productCode?: string
  productName?: string
  lineCode: string
  status: 'pending' | 'sent' | 'failed'
  wmsTaskNo?: string
  message?: string
  sendTime?: string
  createTime?: string
}

/** 领料明细 */
export interface PickingEntry {
  id?: number
  pickingId?: number
  itemNo?: number
  materialCode: string
  materialName?: string
  productCode?: string
  productName?: string
  planQty?: number
  quantity?: number
  unitCode?: string
  planCode?: string
  productionStationCode?: string
  outWarehouseCode?: string
}

/** 发送请求体 */
export interface PickingSendRequest {
  productionNo: string
  lineCode: string
  orgCode?: string
  deptCode?: string
  entries: PickingEntry[]
}

export const wmsPickingApi = {
  /** 按生产订单号查金蝶 PRD_PPBOM */
  queryPpbom(productionNo: string): AxiosPromise<PpbomRow[]> {
    return request.get('/wms-picking/ppbom/query', { params: { productionNo } })
  },

  /** 直接推送 WMS 领料申请（一步到位，不存本地库） */
  push(data: PickingSendRequest): AxiosPromise<any> {
    return request.post('/wms-picking/push', data)
  },

  /** 保存领料单（pending） */
  save(data: PickingSendRequest): AxiosPromise<PickingHead> {
    return request.post('/wms-picking/save', data)
  },

  /** 发送 WMS */
  send(id: number): AxiosPromise<PickingHead> {
    return request.post(`/wms-picking/${id}/send`)
  },

  /** 分页查历史 */
  page(pageNum = 1, pageSize = 20, status?: string): AxiosPromise<{ records: PickingHead[]; total: number }> {
    return request.get('/wms-picking/page', { params: { pageNum, pageSize, status } })
  },

  /** 查明细 */
  entries(id: number): AxiosPromise<PickingEntry[]> {
    return request.get(`/wms-picking/${id}/entries`)
  },
}
