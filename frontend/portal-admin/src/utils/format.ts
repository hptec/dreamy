// 展示格式化 + 枚举标签映射（中文 UI）
// 约束: enum_values（shared-contracts）：tier[vip,regular] / account_status / provider / login_result

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

export const TIER_LABEL: Record<string, string> = {
  vip: 'VIP',
  regular: '常规',
}
export function tierLabel(t?: string | null): string {
  if (!t) return '—'
  return TIER_LABEL[t] || t
}

export const ACCOUNT_STATUS_LABEL: Record<string, string> = {
  active: '正常',
  disabled: '已禁用',
  deleted: '已注销',
  anonymized: '已匿名',
}
export function accountStatusLabel(s?: string | null): string {
  if (!s) return '—'
  return ACCOUNT_STATUS_LABEL[s] || s
}
export function accountStatusTone(s?: string | null): string {
  switch (s) {
    case 'active':
      return 'ok'
    case 'disabled':
      return 'danger'
    case 'deleted':
    case 'anonymized':
      return 'neutral'
    default:
      return 'neutral'
  }
}

export const PROVIDER_LABEL: Record<string, string> = {
  email: '邮箱',
  google: 'Google',
  apple: 'Apple',
}
export function providerLabel(p?: string | null): string {
  if (!p) return '—'
  return PROVIDER_LABEL[p] || p
}

export const LOGIN_RESULT_LABEL: Record<string, string> = {
  success: '成功',
  failed: '失败',
}
export function loginResultLabel(r?: string | null): string {
  if (!r) return '—'
  return LOGIN_RESULT_LABEL[r] || r
}

export function initialsOf(name?: string | null, fallback = '?'): string {
  if (!name) return fallback
  return name.trim().charAt(0).toUpperCase()
}
