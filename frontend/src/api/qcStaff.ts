import { request } from '@/utils/request'

const baseUrl = '/qc/staff'

/** 质检人员（sys_user：username=工号，real_name=姓名，dingdingid=钉钉ID） */
export interface QcStaff {
  id: number
  /** 工号 */
  username: string
  /** 姓名 */
  realName: string
  /** 钉钉ID（sys_user.dingdingid，未绑定时为空串） */
  dingdingId?: string
}

export const qcStaffApi = {
  /** 质检人员列表；keyword 可按工号/姓名模糊检索 */
  list: (keyword?: string) =>
    request.get<QcStaff[]>(baseUrl, {
      params: keyword ? { keyword } : undefined,
    }),

  /** 新增质检人员（工号唯一；同工号曾删除时后端自动恢复；默认密码 123456；钉钉ID选填） */
  add: (data: { username: string; realName: string; dingdingId?: string }) =>
    request.post<QcStaff>(baseUrl, data),

  /** 维护钉钉ID（dingdingId 传空串可清空绑定） */
  updateDingding: (id: number, dingdingId: string) =>
    request.put<QcStaff>(`${baseUrl}/${id}/dingding-id`, { dingdingId }),

  /** 删除质检人员（软删除） */
  remove: (id: number) => request.delete<boolean>(`${baseUrl}/${id}`),
}
