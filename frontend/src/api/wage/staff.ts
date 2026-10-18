import request from '@/utils/request'
import type { WageStaff, WageStaffSave, WageStaffUpdate } from './types'

const BASE = '/wage/staff'

export interface StaffQuery {
  keyword?: string
  groupId?: number
  status?: number
}

export function listStaff(params?: StaffQuery) {
  return request.get<unknown, R<WageStaff[]>>(BASE, { params })
}

export function addStaff(data: WageStaffSave) {
  return request.post<unknown, R<WageStaff>>(BASE, data)
}

export function updateStaff(userId: number, data: WageStaffUpdate) {
  return request.put<unknown, R<WageStaff>>(`${BASE}/${userId}`, data)
}

export function deleteStaff(userId: number) {
  return request.delete<unknown, R<boolean>>(`${BASE}/${userId}`)
}
