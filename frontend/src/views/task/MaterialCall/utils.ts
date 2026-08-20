// 物料呼叫系统 · 纯工具函数
import type { TagType } from './types'

/** 当前时间字符串（本地化，24h） */
export const nowStr = () => new Date().toLocaleString('zh-CN', { hour12: false })

/** 状态中文标签 */
export const statusLabel = (s: string) =>
  ({ pending: '待备料', preparing: '备料中', ready: '已备齐', shortage: '缺料', calling: '叫料中', delivering: '配送中', delivered: '已送达', resolved: '已解决', active: '活跃' } as Record<string, string>)[s] || s

/** 优先级中文标签 */
export const priorityLabel = (p: string) =>
  ({ urgent: '紧急', high: '高', normal: '普通', low: '低' } as Record<string, string>)[p] || p

/** 优先级对应的 el-tag type */
export const priorityTagType = (p: string): TagType =>
  (({ urgent: 'danger', high: 'warning', normal: 'primary', low: 'info' } as Record<string, TagType>)[p]) || 'info'

/** schedulepriority 数字 → CSS key（按数值范围分档：1-99紧急 / 100-999高 / 1000-9999普通 / 10000+低） */
export const priorityKey = (n: number | null | undefined): string => {
  if (n == null) return 'normal'
  if (n <= 100000) return 'normal'
  if (n <= 500000) return 'high'
  if (n <= 999999) return 'urgent'
  return 'low'
}

/** schedulepriority 数字 → 直接显示原数值（1-999999） */
export const priorityLabelByNum = (n: number | null | undefined): string =>
  n == null ? '-' : String(n)

/** schedulepriority 数字 → el-tag type（低数值=高优先级=暖色） */
export const priorityTagTypeByNum = (n: number | null | undefined): TagType => {
  if (n == null) return 'info'
  if (n <= 100000) return 'primary'
  if (n <= 500000) return 'warning'
  if (n <= 9999999) return 'danger'
  return 'info'
}

/** 状态对应的 el-tag type */
export const statusTagType = (s: string): TagType =>
  (({ pending: 'info', preparing: 'warning', ready: 'success', shortage: 'danger', calling: 'danger', delivering: 'primary', delivered: 'success', resolved: 'info', active: 'warning' } as Record<string, TagType>)[s]) || 'info'

/** 状态对应的 LED 点类 */
export const statusDotClass = (s: string): string =>
  (({ pending: 'led-off', preparing: 'led-warn', ready: 'led-on', shortage: 'led-off', calling: 'led-off', delivering: 'led-run', delivered: 'led-on', resolved: '', active: 'led-warn' } as Record<string, string>)[s]) || ''

/** 备料进度颜色 */
export const progressColor = (p: number) =>
  p >= 80 ? '#00ff94' : p >= 40 ? '#ffb020' : '#ff3d5a'

/** 备料进度等级 class */
export const progressClass = (p: number) =>
  p >= 80 ? 'high' : p >= 40 ? 'mid' : 'low'

/** 预警等级图标颜色 */
export const alertIconColor = (l: string) =>
  ({ danger: '#ff3d5a', warning: '#ffb020', info: '#7ab8ff' } as Record<string, string>)[l] || '#7ab8ff'

/** 预警类型中文标签 */
export const alertTypeLabel = (t: string) =>
  ({ shortage: '缺料', low_stock: '库存不足', overtime: '超时' } as Record<string, string>)[t] || t

/** 将 Date 格式化为 HH:MM:SS */
export const formatTime = (d: Date) => {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
