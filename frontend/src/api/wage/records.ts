import request from '@/utils/request'
import type { WageSummary } from './types'

const BASE = '/wage/records'

export interface SummaryQuery {
  month?: string
  keyword?: string
  auditStatus?: string
}

export function listSummary(params?: SummaryQuery) {
  return request.get<unknown, R<WageSummary[]>>(`${BASE}/summary`, { params })
}

/** 单条审核 / 驳回 */
export function auditOne(data: { month: string; staffNo: string; approved: boolean; auditor: string }) {
  return request.post<unknown, R<unknown>>(`${BASE}/audit`, data)
}

/** 批量审核通过（当前筛选范围） */
export function auditBatch(data: { month?: string; keyword?: string; auditStatus?: string; auditor: string }) {
  return request.post<unknown, R<{ count: number }>>(`${BASE}/audit-batch`, data)
}
