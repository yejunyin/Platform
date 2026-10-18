import request from '@/utils/request'
import type { WageProcess } from './types'

const BASE = '/wage/process'

export function listProcess(keyword?: string) {
  return request.get<unknown, R<WageProcess[]>>(BASE, { params: { keyword } })
}

export function saveProcess(data: Partial<WageProcess>) {
  if (data.id) {
    return request.put<unknown, R<WageProcess>>(`${BASE}/${data.id}`, data)
  }
  return request.post<unknown, R<WageProcess>>(BASE, data)
}

export function deleteProcess(id: number) {
  return request.delete<unknown, R<boolean>>(`${BASE}/${id}`)
}
