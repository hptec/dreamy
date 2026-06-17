// ai 翻译域 API（COMP-006 翻译弹窗；FUNC-008~013）
// 契约: ai-translation-api.openapi.yml（后端代理模式，决策 2）
import { get, post } from './client'
import type { PageResult, TranslateRequest, TranslateResponse, TranslationLog } from './types'

/** FUNC-008~010（决策 2/4/6）：AI 翻译请求（后端代理外部网关，超时 30s） */
export function translate(body: TranslateRequest): Promise<TranslateResponse> {
  return post<TranslateResponse>('/api/admin/ai/translate', body)
}

/** FUNC-012：AI 调用记录查询（分页 + 状态/业务筛选；权限 /system/gateways） */
export function listTranslationLogs(params: {
  bizType?: string
  bizRef?: string
  status?: number
  page?: number
  pageSize?: number
}): Promise<PageResult<TranslationLog>> {
  return get<PageResult<TranslationLog>>('/api/admin/ai/translation-logs', { params })
}
