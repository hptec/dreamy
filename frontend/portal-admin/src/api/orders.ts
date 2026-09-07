// trading 域后台订单 API（PAGE-TRD-A01/A02；listAdminOrders/getAdminOrder/ship/patchStatus/createRefund/export）
// order-flow-complete J：制作阶段 / 备注 / 包裹（创建・修改・轨迹・签收・作废・同步）/ 列表新筛选
import { get, post, patch } from './client'
import { downloadCsv } from '@/utils/download'
import type {
  AdminOrderDetail,
  AdminOrderListItem,
  AdminRefund,
  OrderEvent,
  OrderStatus,
  PageResult,
  ProductionStage,
  Shipment,
  ShipmentCreateRequest,
  ShipmentEventCreateRequest,
} from './types'

export interface OrderListQuery {
  page?: number
  pageSize?: number
  status?: OrderStatus
  search?: string
  currency?: string
  from?: string
  to?: string
  /** order-flow-complete §3.2 新筛选 */
  productionStage?: ProductionStage
  /** YYYY-MM-DD：婚期早于该日 */
  weddingBefore?: string
  hasShipment?: boolean
}

export function listOrders(params: OrderListQuery): Promise<PageResult<AdminOrderListItem>> {
  return get<PageResult<AdminOrderListItem>>('/api/admin/orders', { params })
}

export function getOrder(id: number): Promise<AdminOrderDetail> {
  return get<AdminOrderDetail>(`/api/admin/orders/${id}`)
}

/** 兼容别名：= 全部未发行建一个包裹（保留给旧调用） */
export function shipOrder(id: number, body: { carrier: string; trackingNo: string }): Promise<AdminOrderDetail> {
  return post<AdminOrderDetail>(`/api/admin/orders/${id}/ship`, body)
}

export function patchOrderStatus(id: number, status: OrderStatus): Promise<AdminOrderDetail> {
  return patch<AdminOrderDetail>(`/api/admin/orders/${id}/status`, { status })
}

/** 仅 PAID；1→2→3→4 递进，允许回退一档；跳级 409602 */
export function patchProductionStage(id: number, stage: ProductionStage): Promise<AdminOrderDetail> {
  return patch<AdminOrderDetail>(`/api/admin/orders/${id}/production-stage`, { stage })
}

export function addOrderNote(id: number, body: { content: string; customerVisible: boolean }): Promise<OrderEvent> {
  return post<OrderEvent>(`/api/admin/orders/${id}/notes`, body)
}

/** 部分退款：amount ≤ total − refunded_amount（422908）；已有挂起工单 409907 */
export function createRefund(id: number, body: { amount: number | string; reason: string }): Promise<AdminRefund> {
  return post<AdminRefund>(`/api/admin/orders/${id}/refunds`, body)
}

// ---- 包裹（AdminShipmentController） ----

/** lines 省略 = 全部未发行；422906 超量、409908 重复单号、409906 锁冲突 */
export function createShipment(orderId: number, body: ShipmentCreateRequest): Promise<Shipment> {
  return post<Shipment>(`/api/admin/orders/${orderId}/shipments`, body)
}

export function patchShipment(id: number, body: { carrierCode?: string; trackingNo?: string }): Promise<Shipment> {
  return patch<Shipment>(`/api/admin/shipments/${id}`, body)
}

export function addShipmentEvent(id: number, body: ShipmentEventCreateRequest): Promise<Shipment> {
  return post<Shipment>(`/api/admin/shipments/${id}/events`, body)
}

export function deliverShipment(id: number): Promise<Shipment> {
  return post<Shipment>(`/api/admin/shipments/${id}/deliver`, {})
}

export function cancelShipment(id: number): Promise<Shipment> {
  return post<Shipment>(`/api/admin/shipments/${id}/cancel`, {})
}

/** provider=stub → 204（data=null） */
export function syncShipment(id: number): Promise<Shipment | null> {
  return post<Shipment | null>(`/api/admin/shipments/${id}/sync`, {})
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
