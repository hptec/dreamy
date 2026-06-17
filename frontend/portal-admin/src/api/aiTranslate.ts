// ai 翻译域 API（仅 translate 后端代理；日志/术语表已下线）
import { post } from './client'
import type { TranslateRequest, TranslateResponse } from './types'

/** AI 翻译请求（后端代理外部网关，超时 30s） */
export function translate(body: TranslateRequest): Promise<TranslateResponse> {
  return post<TranslateResponse>('/api/admin/ai/translate', body)
}
