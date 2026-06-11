// trading 域退款工单 API（PAGE-TRD-A03；行内审批 approve/reject——决策 7；patch returnTrackingNo 事后登记——决策 31）
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

export function approveRefund(id: number): Promise<AdminRefund> {
  // STORE-TRD-R01：returnTrackingNo 契约为选填，approve 请求体省略该字段（退货单号事后经 patchRefund 登记）
  return post<AdminRefund>(`/api/admin/refunds/${id}/approve`, {})
}

export function rejectRefund(id: number, reason: string): Promise<AdminRefund> {
  return post<AdminRefund>(`/api/admin/refunds/${id}/reject`, { reason })
}

export function patchRefund(id: number, returnTrackingNo: string): Promise<AdminRefund> {
  return patch<AdminRefund>(`/api/admin/refunds/${id}`, { returnTrackingNo })
}
