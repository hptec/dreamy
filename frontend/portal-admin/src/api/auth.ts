// 鉴权 API（PAGE-A01 / STORE-A01）— 对接 AdminAuthController
import { get, post } from './client'
import type { AdminLoginResult, AdminMe } from './types'

// 后端 /api/admin/auth/login 返回 { token, admin, permission_keys } → camel 化后 { token, admin, permissionKeys }
export function adminLogin(payload: {
  email: string
  password: string
  redirect?: string
}): Promise<AdminLoginResult> {
  return post<AdminLoginResult>('/api/admin/auth/login', payload)
}

export function adminLogout(): Promise<void> {
  return post<void>('/api/admin/auth/logout')
}

// /api/admin/auth/me → { admin, role_name, is_super, permission_keys }
export function adminMe(): Promise<AdminMe> {
  return get<AdminMe>('/api/admin/auth/me')
}
