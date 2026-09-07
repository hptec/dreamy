/**
 * 订单/支付/退款/制作阶段/包裹 状态展示映射（COMP-TRD-S05/S06 + order-flow-complete I 共用；token 复用既有 badge 风格）。
 * 状态为后端 IntEnum 整数契约，展示文案在此集中映射；label 函数接受可选本地化标签
 * （messages.ts orders.status / paymentStatus / refundStatus / productionStage / shipmentStatus），缺省回退 EN。
 */
import { OrderStatus, PaymentStatus, ProductionStage, RefundStatus, ShipmentStatus } from '@/lib/api/store-types'

/** 订单状态徽章样式映射 */
export function statusBadgeClass(status: OrderStatus): string {
  switch (status) {
    case OrderStatus.COMPLETED:
    case OrderStatus.DELIVERED:
      return 'bg-sage/15 text-sage-deep'
    case OrderStatus.SHIPPED:
    case OrderStatus.PAID:
      return 'bg-gold/15 text-gold-deep'
    case OrderStatus.CANCELLED:
    case OrderStatus.REFUNDED:
      return 'bg-muted text-ink-faint'
    case OrderStatus.REFUNDING:
      return 'bg-blush/15 text-blush'
    default:
      return 'bg-muted text-ink-soft'
  }
}

const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  [OrderStatus.PENDING]: 'Pending',
  [OrderStatus.PAID]: 'Paid',
  [OrderStatus.SHIPPED]: 'Shipped',
  [OrderStatus.COMPLETED]: 'Completed',
  [OrderStatus.CANCELLED]: 'Cancelled',
  [OrderStatus.REFUNDING]: 'Refunding',
  [OrderStatus.REFUNDED]: 'Refunded',
  [OrderStatus.DELIVERED]: 'Delivered'
}

export function orderStatusLabel(status: OrderStatus, labels?: Partial<Record<OrderStatus, string>>): string {
  return labels?.[status] ?? ORDER_STATUS_LABEL[status] ?? 'Unknown'
}

const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  [PaymentStatus.CREATED]: 'Created',
  [PaymentStatus.PROCESSING]: 'Processing',
  [PaymentStatus.SUCCEEDED]: 'Succeeded',
  [PaymentStatus.FAILED]: 'Failed',
  [PaymentStatus.REFUNDED]: 'Refunded',
  [PaymentStatus.PARTIALLY_REFUNDED]: 'Partially refunded'
}

export function paymentStatusLabel(status: PaymentStatus, labels?: Partial<Record<PaymentStatus, string>>): string {
  return labels?.[status] ?? PAYMENT_STATUS_LABEL[status] ?? 'Unknown'
}

const REFUND_STATUS_LABEL: Record<RefundStatus, string> = {
  [RefundStatus.PENDING]: 'Pending',
  [RefundStatus.APPROVED]: 'Approved',
  [RefundStatus.REJECTED]: 'Rejected'
}

export function refundStatusLabel(status: RefundStatus, labels?: Partial<Record<RefundStatus, string>>): string {
  return labels?.[status] ?? REFUND_STATUS_LABEL[status] ?? 'Unknown'
}

// ===== order-flow-complete：制作阶段 / 包裹状态 =====

const PRODUCTION_STAGE_LABEL: Record<ProductionStage, string> = {
  [ProductionStage.PENDING_REVIEW]: 'Pending review',
  [ProductionStage.IN_PRODUCTION]: 'In production',
  [ProductionStage.QUALITY_CHECK]: 'Quality check',
  [ProductionStage.READY_TO_SHIP]: 'Ready to ship'
}

/** 制作阶段顺序（PAID 态 4 小步） */
export const PRODUCTION_STAGES: ProductionStage[] = [
  ProductionStage.PENDING_REVIEW,
  ProductionStage.IN_PRODUCTION,
  ProductionStage.QUALITY_CHECK,
  ProductionStage.READY_TO_SHIP
]

export function productionStageLabel(stage: ProductionStage, labels?: Partial<Record<ProductionStage, string>>): string {
  return labels?.[stage] ?? PRODUCTION_STAGE_LABEL[stage] ?? 'Unknown'
}

const SHIPMENT_STATUS_LABEL: Record<ShipmentStatus, string> = {
  [ShipmentStatus.PENDING]: 'Awaiting pickup',
  [ShipmentStatus.IN_TRANSIT]: 'In transit',
  [ShipmentStatus.OUT_FOR_DELIVERY]: 'Out for delivery',
  [ShipmentStatus.DELIVERED]: 'Delivered',
  [ShipmentStatus.EXCEPTION]: 'Exception',
  [ShipmentStatus.CANCELLED]: 'Cancelled'
}

export function shipmentStatusLabel(status: ShipmentStatus, labels?: Partial<Record<ShipmentStatus, string>>): string {
  return labels?.[status] ?? SHIPMENT_STATUS_LABEL[status] ?? 'Unknown'
}

export function shipmentStatusBadgeClass(status: ShipmentStatus): string {
  switch (status) {
    case ShipmentStatus.DELIVERED:
      return 'bg-sage/15 text-sage-deep'
    case ShipmentStatus.IN_TRANSIT:
    case ShipmentStatus.OUT_FOR_DELIVERY:
      return 'bg-gold/15 text-gold-deep'
    case ShipmentStatus.EXCEPTION:
      return 'bg-blush/15 text-blush'
    case ShipmentStatus.CANCELLED:
      return 'bg-muted text-ink-faint'
    default:
      return 'bg-muted text-ink-soft'
  }
}

/**
 * 从本地化词典对象（key 为枚举名小驼峰，如 pending / partiallyRefunded / inProduction）
 * 构造 IntEnum 数值 → 文案映射：词典 key 转 UPPER_SNAKE 后与枚举对象 key 匹配。
 */
export function enumLabels<E extends Record<string, number>>(
  enumObj: E,
  dict: Record<string, string>
): Partial<Record<E[keyof E], string>> {
  const out: Partial<Record<E[keyof E], string>> = {}
  for (const [k, label] of Object.entries(dict)) {
    const upper = k.replace(/([A-Z])/g, '_$1').toUpperCase()
    const value = enumObj[upper]
    if (value !== undefined) out[value as E[keyof E]] = label
  }
  return out
}

/** 6 步主时间线（Placed → Paid → In production → Shipped → Delivered → Completed）的步 key */
export type OrderTimelineStep = 'placed' | 'paid' | 'production' | 'shipped' | 'delivered' | 'completed'
export const ORDER_TIMELINE_STEPS: OrderTimelineStep[] = ['placed', 'paid', 'production', 'shipped', 'delivered', 'completed']

/**
 * 计算主时间线各步的到达时间（null 表示未到达）。
 * - production：已发货 → 用 shippedAt 兜底（制作已结束）；PAID 且阶段 ≥ IN_PRODUCTION → 用 paidAt 表示已进入制作。
 * - delivered：deliveredAt；COMPLETED 但无 deliveredAt（用户从 SHIPPED 直接确认收货）→ 用 completedAt。
 */
export function orderTimelineDates(o: {
  status: OrderStatus
  productionStage?: ProductionStage | null
  createdAt: string
  paidAt?: string | null
  shippedAt?: string | null
  deliveredAt?: string | null
  completedAt?: string | null
}): Record<OrderTimelineStep, string | null> {
  const paid = o.paidAt ?? null
  const shipped = o.shippedAt ?? null
  const completed = o.completedAt ?? null
  const delivered = o.deliveredAt ?? (completed && shipped ? completed : null)
  const inProduction = shipped ?? (o.status === OrderStatus.PAID && (o.productionStage ?? 0) >= ProductionStage.IN_PRODUCTION ? paid : null)
  return { placed: o.createdAt, paid, production: inProduction, shipped, delivered, completed }
}
