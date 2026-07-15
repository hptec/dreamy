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

/** Backend business schedule/task timestamps are stored as UTC LocalDateTime (without a JSON zone suffix). */
export function formatUtcDateTime(value?: string | null): string {
  if (!value) return '—'
  const explicitZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)
  return formatDateTime(explicitZone ? value : `${value}Z`)
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

// ===== portal-api-integration 增补：金额 / 币种 / datetime-local =====

/** 币种符号（决策 14 五币种） */
export const CURRENCY_SYMBOL: Record<string, string> = {
  USD: '$',
  EUR: '€',
  CAD: 'C$',
  AUD: 'A$',
  GBP: '£',
}

export function currencySymbol(currency?: string | null): string {
  if (!currency) return '$'
  return CURRENCY_SYMBOL[currency] ?? currency + ' '
}

/** 金额（千分位 + 两位小数），cur 缺省 USD */
export function formatMoney(amount?: number | string | null, currency?: string | null): string {
  if (amount == null || amount === '') return '—'
  const n = Number(amount)
  if (Number.isNaN(n)) return '—'
  return currencySymbol(currency) + n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** ISO（LocalDateTime）→ datetime-local 控件值（YYYY-MM-DDTHH:mm） */
export function toDatetimeLocal(iso?: string | null): string {
  if (!iso) return ''
  const explicitZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(iso)
  const instant = new Date(explicitZone ? iso : `${iso}Z`)
  if (Number.isNaN(instant.getTime())) return iso.slice(0, 16)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${instant.getFullYear()}-${pad(instant.getMonth() + 1)}-${pad(instant.getDate())}`
    + `T${pad(instant.getHours())}:${pad(instant.getMinutes())}`
}
