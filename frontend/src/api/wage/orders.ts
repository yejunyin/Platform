import request from '@/utils/request'
import type { WageOrder, WageMaterial, WagePage, PpbomRow } from './types'

const BASE = '/wage/orders'

export interface OrderQuery {
  workshop?: string
  groupId?: string
  settleStatus?: string
  bizStatus?: string
  keyword?: string
  pageNum?: number
  pageSize?: number
}

export function pageOrders(params: OrderQuery) {
  return request.get<unknown, R<WagePage<WageOrder>>>(`${BASE}/page`, { params })
}

export interface MaterialQuery {
  taskNo?: string
  bizStatus?: string
  keyword?: string
}

export function listMaterials(params?: MaterialQuery) {
  return request.get<unknown, R<WageMaterial[]>>(`${BASE}/materials`, { params })
}

export function listTaskNos() {
  return request.get<unknown, R<string[]>>(`${BASE}/task-nos`)
}

export function listWorkshops() {
  return request.get<unknown, R<string[]>>(`${BASE}/workshops`)
}

/** 从 MES 增量同步近一年生产订单 */
export function syncOrders() {
  return request.post<unknown, R<{ added: number }>>(`${BASE}/sync`)
}

/**
 * 从本地 wage_material.task_no → 金蝶 PRD_PPBOM → 补 wage_order
 * 用于"金蝶标准接口查询"保存用料清单后自动补齐任务单
 */
export function syncOrdersFromMaterials() {
  return request.post<unknown, R<{ added: number }>>(`${BASE}/sync-from-materials`)
}

export function startOrder(id: string, data: { groupId: string; confirmerNo?: string; confirmerName?: string }) {
  return request.post<unknown, R<boolean>>(`${BASE}/${id}/start`, data)
}

export function doneOrder(id: string, data: { stockInQty?: number; finishUser?: string }) {
  return request.post<unknown, R<boolean>>(`${BASE}/${id}/done`, data)
}

export function changeOrderGroup(id: string, groupId: string) {
  return request.put<unknown, R<boolean>>(`${BASE}/${id}/group`, { groupId })
}

export function deleteOrder(id: string) {
  return request.delete<unknown, R<boolean>>(`${BASE}/${id}`)
}

/**
 * 查询金蝶《生产用料清单》PRD_PPBOM（子项物料名称关键词 + 创建日期范围）
 * @param keyword 关键词，多个用逗号分隔（如 "丝印,移印"）；空时不加关键词条件
 * @param startDate 创建日期起始 yyyy-MM-dd；空则默认近3个月
 * @param limit 最大返回条数；空或 0 取默认 2000
 */
export function queryPpbom(params: { keyword?: string; startDate?: string; limit?: number }) {
  return request.get<unknown, R<PpbomRow[]>>(`${BASE}/ppbom/query`, { params })
}

/**
 * 将金蝶 PRD_PPBOM 查询结果保存到本地 wage_material
 * 幂等：按 (task_no, material_code) 去重，已存在跳过
 * @returns { added, received }
 */
export function savePpbomRows(rows: PpbomRow[]) {
  return request.post<unknown, R<{ added: number; received: number }>>(`${BASE}/ppbom/save`, rows)
}
