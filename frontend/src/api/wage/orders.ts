import request from '@/utils/request'
import type { WageOrder, WageMaterial, WagePage, PpbomRow } from './types'

const BASE = '/wage/orders'

export interface OrderQuery {
  workshop?: string
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

export function syncOrders() {
  return request.post<unknown, R<{ added: number }>>(`${BASE}/sync`)
}

export function syncOrdersFromMaterials() {
  return request.post<unknown, R<{ added: number }>>(`${BASE}/sync-from-materials`)
}

// ============== 明细级操作（核心） ==============

/** 开工：按 wage_material.id */
export function startMaterial(materialId: string, data: { groupId: string; confirmerNo?: string; confirmerName?: string }) {
  return request.post<unknown, R<boolean>>(`${BASE}/materials/${materialId}/start`, data)
}

/** 完工：按 wage_material.id */
export function doneMaterial(materialId: string, data: { stockInQty?: number; finishUser?: string }) {
  return request.post<unknown, R<boolean>>(`${BASE}/materials/${materialId}/done`, data)
}

/** 改组别：按 wage_material.id */
export function changeMaterialGroup(materialId: string, groupId: string) {
  return request.put<unknown, R<boolean>>(`${BASE}/materials/${materialId}/group`, { groupId })
}

/** 删除明细：按 wage_material.id */
export function deleteMaterial(materialId: string) {
  return request.delete<unknown, R<boolean>>(`${BASE}/materials/${materialId}`)
}

// ============== 兼容旧接口（按单据头） ==============
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

export function queryPpbom(params: { keyword?: string; startDate?: string; limit?: number }) {
  return request.get<unknown, R<PpbomRow[]>>(`${BASE}/ppbom/query`, { params })
}
export function savePpbomRows(rows: PpbomRow[]) {
  return request.post<unknown, R<{ added: number; received: number }>>(`${BASE}/ppbom/save`, rows)
}
