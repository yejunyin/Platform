import request from '@/utils/request'
import type { WageGroup } from './types'

const BASE = '/wage/groups'

/** 小组下拉（新增/编辑用） */
export function listGroups() {
  return request.get<unknown, R<WageGroup[]>>(BASE)
}

export function saveGroup(data: Partial<WageGroup>) {
  if (data.id) {
    return request.put<unknown, R<WageGroup>>(`${BASE}/${data.id}`, data)
  }
  return request.post<unknown, R<WageGroup>>(BASE, data)
}

export function deleteGroup(id: number) {
  return request.delete<unknown, R<boolean>>(`${BASE}/${id}`)
}
