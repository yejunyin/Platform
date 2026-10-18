import type { ApiResponse } from '@/utils/request'

/** 后端统一响应包装（接口文件中泛型简写） */
declare global {
  type R<T = unknown> = ApiResponse<T>
}

export {}
