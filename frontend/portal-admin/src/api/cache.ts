import { http } from './client'
import type { PageResult } from './types'

export const CacheTaskStatus = {
  PENDING: 0,
  SUCCEEDED: 1,
  FAILED: 2,
  SCHEDULED: 3,
  RUNNING: 4,
  PARTIAL: 5,
  CANCELLED: 6,
  SKIPPED: 7,
  RETRYING: 8,
} as const

export interface CacheInvalidationStep {
  id: number
  stepType: string
  target: string
  status: number
  attempt: number
  startedAt: string
  completedAt?: string
  resultDetail?: string
  errorMessage?: string
}

export interface CacheInvalidationTask {
  id: number
  correlationId: string
  triggerMode: string
  triggerPoint: string
  resourceType: string
  resourceId?: string
  resourceLabel?: string
  targets: string[]
  details: Record<string, unknown>
  triggeredBy: string
  triggeredAt: string
  scheduledAt: string
  startedAt?: string
  completedAt?: string
  nextRetryAt?: string
  status: number
  attemptCount: number
  maxAttempts: number
  errorMessage?: string
  steps: CacheInvalidationStep[]
}

export interface CacheTaskSummary {
  scheduled: number
  pending: number
  running: number
  retrying: number
  succeeded: number
  partial: number
  failed: number
}

export async function getTasks(params?: {
  page?: number
  pageSize?: number
  triggerMode?: string
  resourceType?: string
  status?: number
}): Promise<PageResult<CacheInvalidationTask>> {
  const { data } = await http.get<PageResult<CacheInvalidationTask>>('/api/admin/cache/tasks', { params })
  return data
}

export async function getSummary(): Promise<CacheTaskSummary> {
  const { data } = await http.get<CacheTaskSummary>('/api/admin/cache/summary')
  return data
}

export async function getTargets(): Promise<string[]> {
  const { data } = await http.get<string[]>('/api/admin/cache/targets')
  return data
}

export async function createManualTask(payload: { targets: string[]; reason?: string }): Promise<{ taskId: number; targetCount: number }> {
  const { data } = await http.post<{ taskId: number; targetCount: number }>('/api/admin/cache/tasks', payload)
  return data
}

export async function retryTask(id: number): Promise<void> {
  await http.post(`/api/admin/cache/tasks/${id}/retry`)
}
