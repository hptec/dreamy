// 用户身份运营 API（PAGE-A02 / A03）— 对接 UserOpsController /api/admin/users/*
import { get, post, patch } from './client'
import type { PageResult, UserDetail, UserListItem } from './types'

export function listUsers(params: {
  page?: number
  pageSize?: number
  status?: string
  tier?: string
  email?: string
}): Promise<PageResult<UserListItem>> {
  return get<PageResult<UserListItem>>('/api/admin/users', { params })
}

export function getUserDetail(id: number): Promise<UserDetail> {
  return get<UserDetail>(`/api/admin/users/${id}`)
}

export function toggleUserStatus(id: number, status: string): Promise<UserListItem> {
  return patch<UserListItem>(`/api/admin/users/${id}/status`, { status })
}

// scope: 'single' | 'all'；single 时需 sessionId
export function forceLogout(
  id: number,
  payload: { scope: 'single' | 'all'; sessionId?: string },
): Promise<void> {
  return post<void>(`/api/admin/users/${id}/sessions/force-logout`, payload)
}
