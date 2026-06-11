// trading 域退款工单 API（PAGE-TRD-A03；approve/reject/patch returnTrackingNo——决策 24/31）
import { get, post, patch } from './client'
import type { AdminRefund, PageResult } from './types'

export function listRefunds(params: {
  page?: number
  pageSize?: number
  status?: string
  search?: string
}): Promise<PageResult<AdminRefund>> {
  return get<PageResult<AdminRefund>>('/api/admin/refunds', { params })
}

export function approveRefund(id: number, returnTrackingNo?: string): Promise<AdminRefund> {
  return post<AdminRefund>(`/api/admin/refunds/${id}/approve`, { returnTrackingNo: returnTrackingNo || null })
}

export function rejectRefund(id: number, reason: string): Promise<AdminRefund> {
  return post<AdminRefund>(`/api/admin/refunds/${id}/reject`, { reason })
}

export function patchRefund(id: number, returnTrackingNo: string): Promise<AdminRefund> {
  return patch<AdminRefund>(`/api/admin/refunds/${id}`, { returnTrackingNo })
}
