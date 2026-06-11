/** 订单状态徽章样式映射（COMP-TRD-S05/S06 共用；token 复用既有 badge 风格） */
export function statusBadgeClass(status: string): string {
  switch (status) {
    case 'completed':
      return 'bg-sage/15 text-sage-deep'
    case 'shipped':
    case 'paid':
      return 'bg-gold/15 text-gold-deep'
    case 'cancelled':
    case 'refunded':
      return 'bg-muted text-ink-faint'
    case 'refunding':
      return 'bg-blush/15 text-blush'
    default:
      return 'bg-muted text-ink-soft'
  }
}
