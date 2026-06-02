// 角色 / 权限 API（PAGE-A05）— 对接 RoleController
import { get, post, put, del } from './client'
import type { Permission, Role } from './types'

// listRoles 返回 { items }（无分页）
export function listRoles(): Promise<{ items: Role[] }> {
  return get<{ items: Role[] }>('/api/admin/roles')
}

export function createRole(name: string): Promise<Role> {
  return post<Role>('/api/admin/roles', { name })
}

// updateRole 后端字段 { name, permissionKeys }（record UpdateRoleRequest(name, permissionKeys)→snake permission_keys）
export function updateRole(
  id: string,
  payload: { name?: string; permissionKeys?: string[] },
): Promise<Role> {
  return put<Role>(`/api/admin/roles/${id}`, payload)
}

export function deleteRole(id: string): Promise<void> {
  return del<void>(`/api/admin/roles/${id}`)
}

export function listPermissions(): Promise<{ items: Permission[] }> {
  return get<{ items: Permission[] }>('/api/admin/permissions')
}
