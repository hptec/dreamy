// 管理员 CRUD API（PAGE-A04）— 对接 AdminAuthController /api/admin/admins/*
import { get, post, put, patch, del } from './client'
import type { Admin, AdminCreatePayload, AdminUpdatePayload, PageResult } from './types'

export function listAdmins(params: {
  page?: number
  pageSize?: number
  status?: string
  roleId?: string
}): Promise<PageResult<Admin>> {
  return get<PageResult<Admin>>('/api/admin/admins', { params })
}

export function createAdmin(payload: AdminCreatePayload): Promise<Admin> {
  return post<Admin>('/api/admin/admins', payload)
}

export function updateAdmin(id: number, payload: AdminUpdatePayload): Promise<Admin> {
  return put<Admin>(`/api/admin/admins/${id}`, payload)
}

export function deleteAdmin(id: number): Promise<void> {
  return del<void>(`/api/admin/admins/${id}`)
}

export function toggleAdminStatus(id: number, status: string | number): Promise<Admin> {
  return patch<Admin>(`/api/admin/admins/${id}/status`, { status })
}

// 后端 ResetPasswordRequest = { new_password }（camel newPassword 经 client 转 snake）
export function resetAdminPassword(id: number, newPassword: string): Promise<void> {
  return patch<void>(`/api/admin/admins/${id}/password`, { newPassword })
}
