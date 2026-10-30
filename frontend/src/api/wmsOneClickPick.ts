import request from '@/utils/request'
import type { AxiosPromise } from 'axios'
export type { PpbomRow } from './wmsPicking'

/** 一键领料任务主 */
export interface OneClickPickHead {
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

/** 一键领料任务明细 */
export interface OneClickPickEntry {
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
export interface OneClickPickLog {
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
export interface OneClickPickCreateEntry {
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

export interface OneClickPickCreateRequest {
  productionNo: string
  lineCode: string
  orgCode?: string
  deptCode?: string
  intervalSec: number
  entries: OneClickPickCreateEntry[]
}

export const wmsOneClickPickApi = {
  /** 复用 WMS 叫料的 PRD_PPBOM 查询 */
  queryPpbom(productionNo: string): AxiosPromise<PpbomRow[]> {
    return request.get('/wms-one-click-pick/ppbom/query', { params: { productionNo } })
  },

  /** 创建一键领料任务 */
  create(data: OneClickPickCreateRequest): AxiosPromise<OneClickPickHead> {
    return request.post('/wms-one-click-pick', data)
  },

  /** 启动任务 */
  start(id: number): AxiosPromise<OneClickPickHead> {
    return request.post(`/wms-one-click-pick/${id}/start`)
  },

  /** 暂停任务 */
  pause(id: number): AxiosPromise<OneClickPickHead> {
    return request.post(`/wms-one-click-pick/${id}/pause`)
  },

  /** 恢复任务 */
  resume(id: number): AxiosPromise<OneClickPickHead> {
    return request.post(`/wms-one-click-pick/${id}/resume`)
  },

  /** 取消任务 */
  cancel(id: number): AxiosPromise<OneClickPickHead> {
    return request.post(`/wms-one-click-pick/${id}/cancel`)
  },

  /** 分页查任务列表 */
  page(
    pageNum = 1,
    pageSize = 20,
    params?: { status?: string; productionNo?: string }
  ): AxiosPromise<{ records: OneClickPickHead[]; total: number }> {
    return request.get('/wms-one-click-pick/page', { params: { pageNum, pageSize, ...params } })
  },

  /** 任务详情 */
  detail(id: number): AxiosPromise<OneClickPickHead & { entries: OneClickPickEntry[] }> {
    return request.get(`/wms-one-click-pick/${id}`)
  },

  /** 任务明细 */
  entries(id: number): AxiosPromise<OneClickPickEntry[]> {
    return request.get(`/wms-one-click-pick/${id}/entries`)
  },

  /** 执行日志分页 */
  logs(id: number, pageNum = 1, pageSize = 20): AxiosPromise<{ records: OneClickPickLog[]; total: number }> {
    return request.get(`/wms-one-click-pick/${id}/logs`, { params: { pageNum, pageSize } })
  },
}
