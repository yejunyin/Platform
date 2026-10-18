import request from '@/utils/request'
import type { WageOrder, WageMaterial, WagePage } from './types'

const BASE = '/wage/orders'

export interface OrderQuery {
  workshop?: string
  groupId?: number
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

export function startOrder(id: number, data: { groupId: number; confirmerNo?: string; confirmerName?: string }) {
  return request.post<unknown, R<boolean>>(`${BASE}/${id}/start`, data)
}

export function doneOrder(id: number, data: { stockInQty?: number; finishUser?: string }) {
  return request.post<unknown, R<boolean>>(`${BASE}/${id}/done`, data)
}

export function changeOrderGroup(id: number, groupId: number) {
  return request.put<unknown, R<boolean>>(`${BASE}/${id}/group`, { groupId })
}

export function deleteOrder(id: number) {
  return request.delete<unknown, R<boolean>>(`${BASE}/${id}`)
}
