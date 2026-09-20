// 认证配置 API（PAGE-A06）— 对接 AuthConfigController /api/admin/auth-config
import { get, put } from './client'
import type { AuthConfig, AuthConfigUpdatePayload } from './types'

export function getAuthConfig(): Promise<AuthConfig> {
  return get<AuthConfig>('/api/admin/auth-config')
}

// 后端 updateAuthConfig 接收可变配置字段；email/google/apple 登录方式均可开闭（允许全关），
// 这里提交可编辑字段（camel→snake 由 client 处理）。
export function updateAuthConfig(payload: AuthConfigUpdatePayload): Promise<AuthConfig> {
  return put<AuthConfig>('/api/admin/auth-config', payload)
}
