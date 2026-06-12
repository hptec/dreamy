// trading 域后台订单 API（PAGE-TRD-A01/A02；listAdminOrders/getAdminOrder/ship/patchStatus/createRefund/export）
import { get, post, patch } from './client'
import { downloadCsv } from '@/utils/download'
import type { AdminOrderDetail, AdminOrderListItem, AdminRefund, OrderStatus, PageResult } from './types'

export function listOrders(params: {
  page?: number
  pageSize?: number
  status?: OrderStatus
  search?: string
  currency?: string
  from?: string
  to?: string
}): Promise<PageResult<AdminOrderListItem>> {
  return get<PageResult<AdminOrderListItem>>('/api/admin/orders', { params })
}

export function getOrder(id: number): Promise<AdminOrderDetail> {
  return get<AdminOrderDetail>(`/api/admin/orders/${id}`)
}

export function shipOrder(id: number, body: { carrier: string; trackingNo: string }): Promise<AdminOrderDetail> {
  return post<AdminOrderDetail>(`/api/admin/orders/${id}/ship`, body)
}

export function patchOrderStatus(id: number, status: OrderStatus): Promise<AdminOrderDetail> {
  return patch<AdminOrderDetail>(`/api/admin/orders/${id}/status`, { status })
}

export function createRefund(id: number, body: { amount: number | string; reason: string }): Promise<AdminRefund> {
  return post<AdminRefund>(`/api/admin/orders/${id}/refunds`, body)
}

export interface OrderExportQuery {
  status?: OrderStatus
  search?: string
  currency?: string
  from?: string
  to?: string
}

/**
 * FORM-TRD-O01（ALIGN-012，API-TRD-02）：导出订单 CSV。
 * 走原生下载（需带 Authorization，统一委托 utils/download.ts downloadCsv，FND-REUSE-001）；
 * 筛选参数与列表一致（不含分页）。
 * 文件名优先取后端 Content-Disposition（orders-{yyyyMMdd}.csv），返回 X-Export-Truncated 截断标记。
 */
export function exportOrders(params: OrderExportQuery): Promise<{ truncated: boolean }> {
  const query = new URLSearchParams()
  if (params.status != null) query.set('status', String(params.status))
  if (params.search) query.set('search', params.search)
  if (params.currency) query.set('currency', params.currency)
  if (params.from) query.set('from', params.from)
  if (params.to) query.set('to', params.to)
  return downloadCsv('/api/admin/orders/export', query, 'orders')
}
