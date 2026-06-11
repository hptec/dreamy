// trading 域后台订单 API（PAGE-TRD-A01/A02；listAdminOrders/getAdminOrder/ship/patchStatus/createRefund）
import { get, post, patch } from './client'
import type { AdminOrderDetail, AdminOrderListItem, AdminRefund, PageResult } from './types'

export function listOrders(params: {
  page?: number
  pageSize?: number
  status?: string
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

export function patchOrderStatus(id: number, status: string): Promise<AdminOrderDetail> {
  return patch<AdminOrderDetail>(`/api/admin/orders/${id}/status`, { status })
}

export function createRefund(id: number, body: { amount: number | string; reason: string }): Promise<AdminRefund> {
  return post<AdminRefund>(`/api/admin/orders/${id}/refunds`, body)
}
