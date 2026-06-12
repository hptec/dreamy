/**
 * 订单/支付/退款状态展示映射（COMP-TRD-S05/S06 共用；token 复用既有 badge 风格）。
 * 状态为后端 IntEnum 整数契约，展示文案在此集中映射。
 */
import { OrderStatus, PaymentStatus, RefundStatus } from '@/lib/api/store-types'

/** 订单状态徽章样式映射 */
export function statusBadgeClass(status: OrderStatus): string {
  switch (status) {
    case OrderStatus.COMPLETED:
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
  [OrderStatus.REFUNDED]: 'Refunded'
}

export function orderStatusLabel(status: OrderStatus): string {
  return ORDER_STATUS_LABEL[status] ?? 'Unknown'
}

const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  [PaymentStatus.CREATED]: 'Created',
  [PaymentStatus.PROCESSING]: 'Processing',
  [PaymentStatus.SUCCEEDED]: 'Succeeded',
  [PaymentStatus.FAILED]: 'Failed',
  [PaymentStatus.REFUNDED]: 'Refunded'
}

export function paymentStatusLabel(status: PaymentStatus): string {
  return PAYMENT_STATUS_LABEL[status] ?? 'Unknown'
}

const REFUND_STATUS_LABEL: Record<RefundStatus, string> = {
  [RefundStatus.PENDING]: 'Pending',
  [RefundStatus.APPROVED]: 'Approved',
  [RefundStatus.REJECTED]: 'Rejected'
}

export function refundStatusLabel(status: RefundStatus): string {
  return REFUND_STATUS_LABEL[status] ?? 'Unknown'
}
