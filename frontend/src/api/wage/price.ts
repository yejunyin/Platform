import request from '@/utils/request'
import type { WagePrice } from './types'

const BASE = '/wage/price'

export interface PriceQuery {
  processName?: string
  status?: string
  keyword?: string
}

export function listPrice(params?: PriceQuery) {
  return request.get<unknown, R<WagePrice[]>>(BASE, { params })
}

export function savePrice(data: Partial<WagePrice>) {
  if (data.id) {
    return request.put<unknown, R<WagePrice>>(`${BASE}/${data.id}`, data)
  }
  return request.post<unknown, R<WagePrice>>(BASE, data)
}

export function deletePrice(id: number) {
  return request.delete<unknown, R<boolean>>(`${BASE}/${id}`)
}
