// 操作日志 API（PAGE-A07，只读 + 导出）— 对接 AuthConfigController /api/admin/operation-logs
import { get, getToken } from './client'
import type { OperationLog, PageResult } from './types'

export interface OperationLogQuery {
  page?: number
  pageSize?: number
  action?: string
  operatorId?: number
  from?: string
  to?: string
}

export function listOperationLogs(params: OperationLogQuery): Promise<PageResult<OperationLog>> {
  return get<PageResult<OperationLog>>('/api/admin/operation-logs', { params })
}

// 导出走原生下载：后端流式 CSV，需带 Authorization。用 fetch + blob 触发浏览器下载。
export async function exportOperationLogs(
  params: Omit<OperationLogQuery, 'page' | 'pageSize'>,
): Promise<void> {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const query = new URLSearchParams()
  if (params.action) query.set('action', params.action)
  if (params.operatorId) query.set('operator_id', String(params.operatorId))
  if (params.from) query.set('from', params.from)
  if (params.to) query.set('to', params.to)
  const url = `${base}/api/admin/operation-logs/export?${query.toString()}`
  const token = getToken()
  const res = await fetch(url, {
    headers: {
      Authorization: token ? `Bearer ${token}` : '',
      'Accept-Language': 'zh',
    },
  })
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new Error(body?.message || '导出失败，请稍后重试')
  }
  const blob = await res.blob()
  const objectUrl = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = objectUrl
  a.download = `operation-logs-${new Date().toISOString().slice(0, 10)}.csv`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(objectUrl)
}
