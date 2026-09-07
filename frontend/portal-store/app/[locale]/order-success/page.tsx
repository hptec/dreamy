'use client'

/**
 * order-success（COMP-TRD-S04，data-swap）：按 ?order_id= getOrder 轮询（2s ×15）等待落账。
 * - order-flow-complete A：stub 确认端点同步返回 paid → 首帧 status≥PAID 立即显示成功态（不等待轮询）。
 * - 超时仍 pending → 「Payment is being confirmed」中间态（BNPL 异步确认场景）+「重新支付」入口（跳订单详情 Pay now）。
 * - 文案全部走 t.orderSuccess（i18n）。
 */

import { useEffect, useRef, useState, Suspense } from 'react'
import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { Check, Package, Clock } from 'lucide-react'
import type { StoreOrderDetail } from '@/lib/api/store-types'
import { OrderStatus } from '@/lib/api/store-types'
import { getStoreOrder } from '@/lib/api/trading-api'
import { useCartStore } from '@/lib/stores/cart-store'
import { useI18n } from '@/lib/i18n/i18n-context'
import { trackPurchase } from '@/lib/analytics/gtag'
import { formatAmount, formatDateLong } from '@/lib/utils'

const POLL_INTERVAL = 2000
const POLL_MAX = 15

function OrderSuccessInner() {
  const { t } = useI18n()
  const params = useSearchParams()
  const orderId = Number(params.get('order_id') ?? '0')
  const [order, setOrder] = useState<StoreOrderDetail | null>(null)
  const [state, setState] = useState<'polling' | 'paid' | 'pending-timeout' | 'error'>('polling')
  const attempts = useRef(0)
  const refreshCart = useCartStore((s) => s.refresh)

  useEffect(() => {
    if (!orderId) {
      setState('error')
      return
    }
    let cancelled = false
    // 下单已清空服务端购物车 → 刷新本地态
    void refreshCart().catch(() => undefined)

    const poll = async () => {
      if (cancelled) return
      attempts.current += 1
      try {
        const o = await getStoreOrder(orderId)
        if (cancelled) return
        setOrder(o)
        if (o.status !== OrderStatus.PENDING) {
          setState('paid')
          return
        }
      } catch {
        /* 轮询期容错继续 */
      }
      if (attempts.current >= POLL_MAX) {
        setState('pending-timeout')
        return
      }
      setTimeout(poll, POLL_INTERVAL)
    }
    void poll()
    return () => {
      cancelled = true
    }
  }, [orderId, refreshCart])

  // GA4 purchase（决策 19）：落账确认（paid）后上报；localStorage 按 orderNo 去重防刷新重复计数
  useEffect(() => {
    if (state !== 'paid' || !order) return
    const dedupeKey = `dreamy_ga4_purchase_${order.orderNo}`
    try {
      if (localStorage.getItem(dedupeKey)) return
      localStorage.setItem(dedupeKey, '1')
    } catch {
      /* 存储不可用时仍上报（单次会话语义） */
    }
    trackPurchase({
      transactionId: order.orderNo,
      currency: order.currency,
      value: order.totalAmount,
      items: (order.lines ?? []).map((l) => ({
        item_id: String(l.productId),
        item_name: l.productName,
        price: l.unitPrice,
        quantity: l.qty,
        item_variant: [l.color, l.size].filter(Boolean).join(' / ') || undefined
      }))
    })
  }, [state, order])

  return (
    <div className="container-luxe py-16" data-state={state}>
      <div className="mx-auto max-w-lg text-center">
        {state === 'polling' && (
          <>
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-gold/15">
              <Clock className="h-8 w-8 animate-pulse text-gold-deep" />
            </div>
            <h1 className="mt-6 font-display text-4xl font-medium">{t.orderSuccess.confirmingTitle}</h1>
            <p className="mt-3 text-ink-soft">{t.orderSuccess.confirmingBody}</p>
          </>
        )}

        {state === 'paid' && (
          <>
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-sage/15">
              <Check className="h-8 w-8 text-sage-deep" />
            </div>
            <h1 className="mt-6 font-display text-4xl font-medium">{t.orderSuccess.paidTitle}</h1>
            <p className="mt-3 text-ink-soft">{t.orderSuccess.paidBody}</p>
          </>
        )}

        {state === 'pending-timeout' && (
          <>
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-gold/15">
              <Clock className="h-8 w-8 text-gold-deep" />
            </div>
            <h1 className="mt-6 font-display text-4xl font-medium">{t.orderSuccess.pendingTitle}</h1>
            <p className="mt-3 text-ink-soft">{t.orderSuccess.pendingBody}</p>
          </>
        )}

        {state === 'error' && (
          <>
            <h1 className="mt-6 font-display text-4xl font-medium">{t.orderSuccess.notFoundTitle}</h1>
            <p className="mt-3 text-ink-soft">{t.orderSuccess.notFoundBody}</p>
          </>
        )}

        {order && (
          <div className="mt-8 rounded-sm border border-line bg-surface p-6 text-left">
            <div className="flex items-center justify-between">
              <div><p className="eyebrow">{t.orderSuccess.orderNumber}</p><p className="font-display text-xl">{order.orderNo}</p></div>
              <Package className="h-8 w-8 text-gold" strokeWidth={1.5} />
            </div>
            <div className="mt-4 border-t border-line pt-4 text-sm text-ink-soft">
              <p>{t.orderSuccess.total} <span className="font-medium text-ink">{formatAmount(order.totalAmount, order.currency)}</span></p>
              {order.estimatedDeliveryFrom && order.estimatedDeliveryTo && (
                <p className="mt-1">{t.orderSuccess.etaNote.replace('{from}', formatDateLong(order.estimatedDeliveryFrom)).replace('{to}', formatDateLong(order.estimatedDeliveryTo))}</p>
              )}
              <p className="mt-1">{t.orderSuccess.trackingNote}</p>
            </div>
          </div>
        )}

        <div className="mt-6 flex flex-wrap justify-center gap-3">
          {state === 'pending-timeout' && order ? (
            <>
              <Link href={`/account/orders/${order.id}`} className="btn-primary">{t.orderSuccess.retryPayment}</Link>
              <Link href="/account/orders" className="btn-outline">{t.orderSuccess.viewOrder}</Link>
            </>
          ) : (
            <>
              <Link href={order ? `/account/orders/${order.id}` : '/account/orders'} className="btn-primary">{t.orderSuccess.trackOrder}</Link>
              <Link href="/wedding-dresses" className="btn-outline">{t.common.continueShopping}</Link>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

export default function OrderSuccessPage() {
  return (
    <Suspense fallback={<div className="container-luxe py-24 text-center text-ink-soft">…</div>}>
      <OrderSuccessInner />
    </Suspense>
  )
}
