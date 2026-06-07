// 展示格式化 + 枚举标签映射（中文 UI）
// 约束: 后端 IntEnum，API 返回整数键值

import { AdminStatus, UserStatus, UserTier, AuthProvider, LoginOutcome, RoleType } from '../api/types'

export function formatDateTime(iso?: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function formatDate(iso?: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const TIER_LABEL: Record<number, string> = {
  [UserTier.REGULAR]: '常规',
  [UserTier.VIP]: 'VIP',
}
export function tierLabel(t?: number | null): string {
  if (t == null) return '—'
  return TIER_LABEL[t] ?? '—'
}

const ACCOUNT_STATUS_LABEL: Record<number, string> = {
  [UserStatus.ACTIVE]: '正常',
  [UserStatus.DISABLED]: '已禁用',
  [UserStatus.DELETED]: '已注销',
  [UserStatus.ANONYMIZED]: '已匿名',
}
export function accountStatusLabel(s?: number | null): string {
  if (s == null) return '—'
  return ACCOUNT_STATUS_LABEL[s] ?? '—'
}
export function accountStatusTone(s?: number | null): string {
  switch (s) {
    case UserStatus.ACTIVE: return 'ok'
    case UserStatus.DISABLED: return 'danger'
    default: return 'neutral'
  }
}

const PROVIDER_LABEL: Record<number, string> = {
  [AuthProvider.EMAIL]: '邮箱',
  [AuthProvider.GOOGLE]: 'Google',
  [AuthProvider.APPLE]: 'Apple',
}
export function providerLabel(p?: number | null): string {
  if (p == null) return '—'
  return PROVIDER_LABEL[p] ?? '—'
}

const LOGIN_RESULT_LABEL: Record<number, string> = {
  [LoginOutcome.SUCCESS]: '成功',
  [LoginOutcome.FAILED]: '失败',
}
export function loginResultLabel(r?: number | null): string {
  if (r == null) return '—'
  return LOGIN_RESULT_LABEL[r] ?? '—'
}

export function roleTypeLabel(t?: number | null): string {
  return t === RoleType.PRESET ? '系统预设' : '自定义'
}

export function adminStatusLabel(s?: number | null): string {
  return s === AdminStatus.ACTIVE ? '正常' : '已禁用'
}

export function initialsOf(name?: string | null, fallback = '?'): string {
  if (!name) return fallback
  return name.trim().charAt(0).toUpperCase()
}
