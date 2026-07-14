// gateway 域 API（PAGE-001 网关配置；FUNC-004~007/021）
// 契约: gateway-api.openapi.yml（6 端点：CRUD + test + sync-models）
import { get, post, put, del } from './client'
import type {
  GatewayConfigDetail,
  GatewayConfigUpsert,
  GatewayTestResult,
  GatewayType,
  PageResult,
} from './types'

/** FUNC-004：网关配置列表（分页，API Key 掩码展示） */
export function listConfigs(params: {
  gatewayType?: GatewayType
  page?: number
  pageSize?: number
}): Promise<PageResult<GatewayConfigDetail>> {
  return get<PageResult<GatewayConfigDetail>>('/api/admin/gateway/configs', { params })
}

/** 网关配置详情（API Key 掩码展示） */
export function getConfig(id: number): Promise<GatewayConfigDetail> {
  return get<GatewayConfigDetail>(`/api/admin/gateway/configs/${id}`)
}

/** FUNC-005：创建配置（AI 网关保存时自动拉取模型列表） */
export function createConfig(body: GatewayConfigUpsert): Promise<GatewayConfigDetail> {
  return post<GatewayConfigDetail>('/api/admin/gateway/configs', body)
}

/** FUNC-005/007：更新配置（api_key 传掩码则保持原密文） */
export function updateConfig(id: number, body: GatewayConfigUpsert): Promise<GatewayConfigDetail> {
  return put<GatewayConfigDetail>(`/api/admin/gateway/configs/${id}`, body)
}

/** 物理删除配置。 */
export function deleteConfig(id: number): Promise<void> {
  return del<void>(`/api/admin/gateway/configs/${id}`)
}

/** FUNC-021（决策 14）：测试连接（成功/失败均 200，reachable 区分；EDGE-023 具体错误） */
export function testConnection(id: number): Promise<GatewayTestResult> {
  return post<GatewayTestResult>(`/api/admin/gateway/configs/${id}/test`)
}

/** FUNC-006（决策 5）：手动刷新模型列表（仅 AI 网关；EDGE-014 拉取失败提示） */
export function syncModels(id: number): Promise<GatewayConfigDetail> {
  return post<GatewayConfigDetail>(`/api/admin/gateway/configs/${id}/sync-models`)
}
