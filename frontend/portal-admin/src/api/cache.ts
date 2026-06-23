import { http } from './client'
import type { PageResult } from './types'

/**
 * 缓存失效日志 DTO
 */
export interface CacheInvalidationLog {
  id: number
  eventType: string
  resourceType: string
  resourceId?: number
  slug?: string
  oldSlug?: string
  affectedPaths?: string[]
  locales?: string[]
  triggeredBy: string
  triggeredAt: string
  status: number
  completedAt?: string
  errorMessage?: string
  createdAt: string
}

/**
 * 获取缓存失效日志列表
 */
export async function getInvalidationLogs(params?: {
  page?: number
  pageSize?: number
  eventType?: string
  resourceType?: string
  status?: number
}): Promise<PageResult<CacheInvalidationLog>> {
  const { data } = await http.get<PageResult<CacheInvalidationLog>>('/api/admin/cache/invalidation-logs', { params })
  return data
}

/**
 * 手动触发缓存失效
 */
export async function manualInvalidate(payload: {
  paths?: string[]
  slug?: string
  resourceType?: string
}): Promise<string> {
  const { data } = await http.post<string>('/api/admin/cache/invalidate', payload)
  return data
}
