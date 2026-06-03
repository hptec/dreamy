// 角色 / 权限 API（PAGE-A05）— 对接 RoleController
import { get, post, put, del } from './client'
import type { Permission, Role } from './types'

// listRoles 返回 R<List<RoleDTO>>，解包后为裸数组
export function listRoles(): Promise<Role[]> {
  return get<Role[]>('/api/admin/roles')
}

export function createRole(name: string): Promise<Role> {
  return post<Role>('/api/admin/roles', { name })
}

// updateRole 后端字段 { name, permissionKeys }（record UpdateRoleRequest(name, permissionKeys)→snake permission_keys）
export function updateRole(
  id: number,
  payload: { name?: string; permissionKeys?: string[] },
): Promise<Role> {
  return put<Role>(`/api/admin/roles/${id}`, payload)
}

export function deleteRole(id: number): Promise<void> {
  return del<void>(`/api/admin/roles/${id}`)
}

export function listPermissions(): Promise<Permission[]> {
  return get<Permission[]>('/api/admin/permissions')
}
