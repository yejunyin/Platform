import request from '@/utils/request'
import type { AxiosPromise } from 'axios'
import type { PpbomRow } from './wmsPicking'

/** 定时领料任务主 */
export interface PickTaskHead {
  id: number
  taskNo: string
  productionNo: string
  productCode?: string
  productName?: string
  lineCode: string
  orgCode?: string
  deptCode?: string
  perBoxQty?: number
  intervalSec?: number
  status: 'pending' | 'running' | 'paused' | 'completed' | 'cancelled' | 'failed'
  totalRounds: number
  currentRound: number
  nextFireTime?: string
  lastFireTime?: string
  successCount: number
  failCount: number
  message?: string
  createTime?: string
  updateTime?: string
}

/** 定时领料任务明细 */
export interface PickTaskEntry {
  id: number
  taskId: number
  itemNo: number
  materialCode: string
  materialName?: string
  planQty?: number
  totalQty: number
  perBoxQty: number
  totalRounds: number
  currentRound: number
  sentQty: number
  unitCode?: string
  planCode?: string
  stationCode?: string
  warehouseCode?: string
}

/** 执行日志 */
export interface PickTaskLog {
  id: number
  taskId: number
  roundNo: number
  billNo: string
  wmsTaskNo?: string
  status: 'sent' | 'failed' | 'skipped'
  requestId?: string
  message?: string
  createTime?: string
}

/** 创建任务请求 */
export interface PickTaskCreateEntry {
  materialCode: string
  materialName?: string
  productCode?: string
  productName?: string
  totalQty: number
  perBoxQty: number
  unitCode?: string
  planCode?: string
  productionStationCode?: string
  outWarehouseCode?: string
  planQty?: number
}

export interface PickTaskCreateRequest {
  productionNo: string
  lineCode: string
  orgCode?: string
  deptCode?: string
  intervalSec: number
  entries: PickTaskCreateEntry[]
}

export const wmsPickTaskApi = {
  /** 复用 WMS 叫料的 PRD_PPBOM 查询 */
  queryPpbom(productionNo: string): AxiosPromise<PpbomRow[]> {
    return request.get('/wms-pick-task/ppbom/query', { params: { productionNo } })
  },

  /** 创建定时领料任务（pending 状态） */
  create(data: PickTaskCreateRequest): AxiosPromise<PickTaskHead> {
    return request.post('/wms-pick-task', data)
  },

  /** 启动任务 */
  start(id: number): AxiosPromise<PickTaskHead> {
    return request.post(`/wms-pick-task/${id}/start`)
  },

  /** 暂停任务 */
  pause(id: number): AxiosPromise<PickTaskHead> {
    return request.post(`/wms-pick-task/${id}/pause`)
  },

  /** 恢复任务 */
  resume(id: number): AxiosPromise<PickTaskHead> {
    return request.post(`/wms-pick-task/${id}/resume`)
  },

  /** 取消任务 */
  cancel(id: number): AxiosPromise<PickTaskHead> {
    return request.post(`/wms-pick-task/${id}/cancel`)
  },

  /** 分页查任务列表 */
  page(
    pageNum = 1,
    pageSize = 20,
    params?: { status?: string; productionNo?: string }
  ): AxiosPromise<{ records: PickTaskHead[]; total: number }> {
    return request.get('/wms-pick-task/page', { params: { pageNum, pageSize, ...params } })
  },

  /** 任务详情 */
  detail(id: number): AxiosPromise<PickTaskHead & { entries: PickTaskEntry[] }> {
    return request.get(`/wms-pick-task/${id}`)
  },

  /** 任务明细 */
  entries(id: number): AxiosPromise<PickTaskEntry[]> {
    return request.get(`/wms-pick-task/${id}/entries`)
  },

  /** 执行日志分页 */
  logs(id: number, pageNum = 1, pageSize = 20): AxiosPromise<{ records: PickTaskLog[]; total: number }> {
    return request.get(`/wms-pick-task/${id}/logs`, { params: { pageNum, pageSize } })
  },
}
