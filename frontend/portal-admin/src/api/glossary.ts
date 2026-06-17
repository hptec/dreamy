// glossary 域 API（PAGE-002 术语表；FUNC-022）
// 契约: glossary-api.openapi.yml（5 端点：CRUD）
import { get, post, put, del } from './client'
import type { GlossaryTerm, GlossaryTermUpsert, PageResult } from './types'

/** FUNC-022：术语列表（category/enabled/keyword 筛选 + 分页，term_en 字母序） */
export function listTerms(params: {
  category?: string
  enabled?: boolean
  keyword?: string
  page?: number
  pageSize?: number
}): Promise<PageResult<GlossaryTerm>> {
  return get<PageResult<GlossaryTerm>>('/api/admin/glossary/terms', { params })
}

/** 术语详情 */
export function getTerm(id: number): Promise<GlossaryTerm> {
  return get<GlossaryTerm>(`/api/admin/glossary/terms/${id}`)
}

/** FUNC-022：新增术语（term_en 唯一，冲突返回 409401） */
export function createTerm(body: GlossaryTermUpsert): Promise<GlossaryTerm> {
  return post<GlossaryTerm>('/api/admin/glossary/terms', body)
}

/** FUNC-022：更新术语 */
export function updateTerm(id: number, body: GlossaryTermUpsert): Promise<GlossaryTerm> {
  return put<GlossaryTerm>(`/api/admin/glossary/terms/${id}`, body)
}

/** FUNC-022：删除术语（硬删除，无引用约束） */
export function deleteTerm(id: number): Promise<void> {
  return del<void>(`/api/admin/glossary/terms/${id}`)
}
