import request from '@/utils/request'
import type { WagePayStat } from './types'

const BASE = '/wage/paystat'

export interface PayStatQuery {
  month?: string
  groupId?: number
  keyword?: string
}

export function listPayStat(params?: PayStatQuery) {
  return request.get<unknown, R<WagePayStat[]>>(BASE, { params })
}

export function listMonths() {
  return request.get<unknown, R<string[]>>(`${BASE}/months`)
}

/** 重新计算（全量删旧重算） */
export function recalcPayStat(operator?: string) {
  return request.post<unknown, R<{ count: number }>>(`${BASE}/recalc`, { operator })
}

/** 单条复核 */
export function reviewPayStat(id: number, data: { wageMonth: string; amount?: number; remark?: string; reviewer: string }) {
  return request.put<unknown, R<WagePayStat>>(`${BASE}/${id}`, data)
}

/** 批量录入工资月份 */
export function batchUpdateMonth(data: { ids: number[]; wageMonth: string; remark?: string }) {
  return request.put<unknown, R<{ count: number }>>(`${BASE}/batch-month`, data)
}
