'use client'

/**
 * 订单详情（PAGE-TRD-S05 / COMP-TRD-S06，原列表 Details 链接指向页，按订单卡片同 token 风格构建）：
 * - 状态徽章 + 6 步时间线（Placed → Paid → In production → Shipped → Delivered → Completed，order-flow-complete D）；
 *   PAID 态显示制作阶段 4 小步；Cancelled/Refunding/Refunded 分支沿用（时间线隐藏或按已到达步渲染）。
 * - 行列表（定制行展示 customSizeData）、地址快照、支付摘要、金额拆分（决策 28；amount_version=2 才显示 Tax；
 *   refunded_amount>0 显示 Refunded）。
 * - 「Shipments」包裹卡（承运商/单号/tracking_url/状态/行明细/轨迹倒序）+「Order activity」customer_visible 事件。
 * - 动作区按状态渲染：pending →「Pay now」（retryPaymentIntent → PaymentElementPanel）+「Cancel order」二次确认
 *   + expires_at 倒计时（mm:ss，到期显示已过期）；shipped/delivered →「Confirm delivery」二次确认；
 *   paid/shipped/delivered/completed →「Buy again」（reorder → toast added_count/skipped）；
 *   paid/shipped/delivered/completed →「Request refund」（refundEligible=false 置灰 + refundBlockReasonCode 说明，决策 24）。
 * - refunds[] 工单状态条；410601 → Order expired 提示态（FORM-TRD-S04/S05）。
 */

import { useCallback, useEffect, useState, use } from 'react'
import Link from 'next/link'
import { Clock, X, PackageCheck, RefreshCw, ChevronDown } from 'lucide-react'
import type { StoreOrderDetail } from '@/lib/api/store-types'
import { OrderStatus, PaymentStatus, RefundStatus } from '@/lib/api/store-types'
import { getStoreOrder, cancelStoreOrder, retryOrderPayment, applyStoreRefund, confirmDelivery, reorderStoreOrder } from '@/lib/api/trading-api'
import { ApiError } from '@/lib/api/client'
import { useI18n } from '@/lib/i18n/i18n-context'
import { useCartStore } from '@/lib/stores/cart-store'
import { PaymentElementPanel } from '@/components/cart/payment-element-panel'
import { OrderTimeline, ProductionStages, ShipmentCard, OrderActivity } from '@/components/orders/order-progress'
import { formatAmount, formatDateTimeLong, formatDateLong, cn } from '@/lib/utils'
import { statusBadgeClass, orderStatusLabel, paymentStatusLabel, refundStatusLabel, enumLabels } from '@/lib/order-ui'

/** expires_at 倒计时（mm:ss；到期 expired=true） */
function useCountdown(expiresAt?: string | null, active = true): { label: string; expired: boolean } | null {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!active || !expiresAt) return
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [active, expiresAt])
  if (!active || !expiresAt) return null
  const end = new Date(expiresAt).getTime()
  if (Number.isNaN(end)) return null
  const remain = Math.max(0, Math.floor((end - now) / 1000))
  const mm = String(Math.floor(remain / 60)).padStart(2, '0')
  const ss = String(remain % 60).padStart(2, '0')
  return { label: `${mm}:${ss}`, expired: remain <= 0 }
}

export default function OrderDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const orderId = Number(id)
  const { t, te } = useI18n()
  const refreshCart = useCartStore((s) => s.refresh)
  const statusLabels = enumLabels(OrderStatus, t.orders.status)
  const paymentLabels = enumLabels(PaymentStatus, t.orders.paymentStatus)
  const refundLabels = enumLabels(RefundStatus, t.orders.refundStatus)

  const [order, setOrder] = useState<StoreOrderDetail | null>(null)
  const [state, setState] = useState<'loading' | 'ready' | 'not-found' | 'error'>('loading')
  const [actionError, setActionError] = useState<string | null>(null)
  const [expired, setExpired] = useState(false)
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [paySecret, setPaySecret] = useState<string | null>(null)
  const [payMode, setPayMode] = useState<'stub' | 'real' | null>(null)
  const [payLoading, setPayLoading] = useState(false)
  const [refundOpen, setRefundOpen] = useState(false)
  const [confirmDeliver, setConfirmDeliver] = useState(false)
  const [delivering, setDelivering] = useState(false)
  const [reordering, setReordering] = useState(false)
  const [toast, setToast] = useState<{ text: string; cartLink?: boolean } | null>(null)
  const [taxOpen, setTaxOpen] = useState(false)

  const load = useCallback(async () => {
    try {
      const o = await getStoreOrder(orderId)
      setOrder(o)
      setState('ready')
    } catch (err) {
      if (err instanceof ApiError && err.httpStatus === 404) setState('not-found')
      else setState('error')
    }
  }, [orderId])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!toast) return
    const id = setTimeout(() => setToast(null), 5000)
    return () => clearTimeout(id)
  }, [toast])

  const countdown = useCountdown(order?.expiresAt, order?.status === OrderStatus.PENDING)

  if (state === 'loading') {
    return <div className="space-y-4" aria-hidden="true"><div className="h-10 w-64 animate-pulse rounded-sm bg-muted" /><div className="h-48 animate-pulse rounded-sm bg-muted" /></div>
  }

  if (state === 'not-found' || state === 'error' || !order) {
    // 404 防探测：通用「不存在或无权访问」（error-strategy store 约定）
    return (
      <div className="py-16 text-center">
        <h1 className="font-display text-3xl font-medium">{state === 'error' ? t.orders.detail.somethingWrong : t.orders.detail.notFound}</h1>
        <p className="mt-2 text-sm text-ink-soft">{state === 'error' ? te(50000) : te(404601)}</p>
        <div className="mt-6 flex justify-center gap-3">
          {state === 'error' && <button onClick={() => { setState('loading'); void load() }} className="btn-primary">{t.common.retry}</button>}
          <Link href="/account/orders" className="btn-outline">{t.orders.detail.backToOrders}</Link>
        </div>
      </div>
    )
  }

  const payNow = async () => {
    setPayLoading(true)
    setActionError(null)
    try {
      const cred = await retryOrderPayment(order.id)
      setPaySecret(cred.clientSecret)
      setPayMode(cred.mode ?? null)
    } catch (err) {
      if (err instanceof ApiError && err.code === 410601) {
        setExpired(true)
        void load()
      } else {
        setActionError(err instanceof ApiError ? te(err.code) : te(50000))
      }
    } finally {
      setPayLoading(false)
    }
  }

  const cancelOrder = async () => {
    setCancelling(true)
    setActionError(null)
    try {
      const updated = await cancelStoreOrder(order.id)
      setOrder(updated)
    } catch (err) {
      if (err instanceof ApiError && err.code === 409602) {
        setActionError(te(409602))
        void load()
      } else {
        setActionError(err instanceof ApiError ? te(err.code) : te(50000))
      }
    } finally {
      setCancelling(false)
      setConfirmCancel(false)
    }
  }

  const doConfirmDelivery = async () => {
    setDelivering(true)
    setActionError(null)
    try {
      const updated = await confirmDelivery(order.id)
      setOrder(updated)
    } catch (err) {
      if (err instanceof ApiError && err.code === 409602) {
        setActionError(te(409602))
        void load()
      } else {
        setActionError(err instanceof ApiError ? te(err.code) : te(50000))
      }
    } finally {
      setDelivering(false)
      setConfirmDeliver(false)
    }
  }

  const buyAgain = async () => {
    setReordering(true)
    setActionError(null)
    try {
      const res = await reorderStoreOrder(order.id)
      void refreshCart().catch(() => undefined)
      const parts = [t.orders.detail.buyAgainAdded.replace('{count}', String(res.addedCount))]
      if (res.skipped.length > 0) parts.push(t.orders.detail.buyAgainSkipped.replace('{count}', String(res.skipped.length)))
      setToast({ text: parts.join(' '), cartLink: res.addedCount > 0 })
    } catch (err) {
      setActionError(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setReordering(false)
    }
  }

  const isPending = order.status === OrderStatus.PENDING
  const canConfirmDelivery = order.status === OrderStatus.SHIPPED || order.status === OrderStatus.DELIVERED
  const canBuyAgain = ([OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.COMPLETED] as OrderStatus[]).includes(order.status)
  const canRefund = canBuyAgain
  const showTimeline = order.status !== OrderStatus.CANCELLED
  const showTax = (order.amountVersion ?? 1) >= 2 && (order.taxAmount ?? 0) > 0
  const carrierLine = order.carrier ? `${order.carrier}${order.trackingNo ? ` · ${order.trackingNo}` : ''}` : null

  return (
    <div>
      <Link href="/account/orders" className="text-sm text-gold-deep underline">← {t.orders.detail.backToOrders}</Link>
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-display text-3xl font-medium">{t.orders.orderNo.replace('{no}', order.orderNo)}</h1>
          <p className="text-sm text-ink-soft">{t.orders.placed.replace('{date}', formatDateTimeLong(order.createdAt))}</p>
          {order.estimatedDeliveryFrom && order.estimatedDeliveryTo && (order.status === OrderStatus.PAID || order.status === OrderStatus.SHIPPED) && (
            <p className="mt-1 text-xs text-ink-faint" data-testid="order-eta">{t.orders.detail.eta.replace('{from}', formatDateLong(order.estimatedDeliveryFrom)).replace('{to}', formatDateLong(order.estimatedDeliveryTo))}</p>
          )}
        </div>
        <span className={cn('rounded-full px-4 py-1.5 text-sm capitalize', statusBadgeClass(order.status))} data-testid="order-status-badge" data-status={order.status}>{orderStatusLabel(order.status, statusLabels)}</span>
      </div>

      {expired && (
        <p className="mt-4 flex items-center gap-2 rounded-sm bg-muted px-4 py-3 text-sm text-ink-soft">
          <Clock className="h-4 w-4 text-ink-faint" /> {te(410601)}
        </p>
      )}
      {actionError && <p className="mt-4 rounded-sm bg-blush/10 px-4 py-3 text-sm text-blush">{actionError}</p>}

      {/* 支付倒计时（PENDING 态） */}
      {isPending && countdown && (
        <p className={cn('mt-4 flex items-center gap-2 rounded-sm px-4 py-3 text-sm', countdown.expired ? 'bg-muted text-ink-soft' : 'bg-gold/10 text-gold-deep')} data-testid="payment-countdown" data-expired={countdown.expired}>
          <Clock className="h-4 w-4" />
          {countdown.expired ? t.orders.detail.countdownExpired : t.orders.detail.countdown.replace('{time}', countdown.label)}
        </p>
      )}

      {/* 状态时间线 + 制作阶段 */}
      {showTimeline && (
        <div className="mt-8 space-y-4">
          <OrderTimeline order={order} carrierLine={carrierLine} />
          {order.status === OrderStatus.PAID && order.productionStage && <ProductionStages stage={order.productionStage} />}
        </div>
      )}

      {/* 动作区（按状态渲染） */}
      <div className="mt-6 flex flex-wrap items-center gap-3">
        {isPending && !paySecret && (
          <>
            <button onClick={() => void payNow()} disabled={payLoading || countdown?.expired} className="btn-primary disabled:opacity-60">{payLoading ? t.common.loading : t.orders.detail.payNow}</button>
            {confirmCancel ? (
              <span className="flex items-center gap-2 text-sm">
                <span className="text-ink-soft">{t.orders.detail.cancelConfirm}</span>
                <button onClick={() => void cancelOrder()} disabled={cancelling} className="cursor-pointer font-medium text-blush underline">{cancelling ? t.orders.detail.cancelling : t.orders.detail.yesCancel}</button>
                <button onClick={() => setConfirmCancel(false)} className="cursor-pointer text-ink-soft underline">{t.orders.detail.keepOrder}</button>
              </span>
            ) : (
              <button onClick={() => setConfirmCancel(true)} className="btn-outline"><X className="h-4 w-4" /> {t.orders.detail.cancelOrder}</button>
            )}
          </>
        )}
        {canConfirmDelivery && (
          confirmDeliver ? (
            <span className="flex items-center gap-2 text-sm" data-testid="confirm-delivery-prompt">
              <span className="text-ink-soft">{t.orders.detail.confirmDeliveryQuestion}</span>
              <button onClick={() => void doConfirmDelivery()} disabled={delivering} className="cursor-pointer font-medium text-sage-deep underline">{delivering ? t.orders.detail.confirmingDelivery : t.orders.detail.confirmDeliveryYes}</button>
              <button onClick={() => setConfirmDeliver(false)} className="cursor-pointer text-ink-soft underline">{t.orders.detail.confirmDeliveryNo}</button>
            </span>
          ) : (
            <button onClick={() => setConfirmDeliver(true)} className="btn-primary" data-testid="confirm-delivery"><PackageCheck className="h-4 w-4" /> {t.orders.detail.confirmDelivery}</button>
          )
        )}
        {canBuyAgain && (
          <button onClick={() => void buyAgain()} disabled={reordering} className="btn-outline disabled:opacity-60" data-testid="buy-again"><RefreshCw className={cn('h-4 w-4', reordering && 'animate-spin')} /> {reordering ? t.orders.detail.buyAgainBusy : t.orders.detail.buyAgain}</button>
        )}
        {canRefund && (
          order.refundEligible ? (
            <button onClick={() => setRefundOpen(true)} className="btn-outline">{t.orders.detail.requestRefund}</button>
          ) : (
            <span className="flex items-center gap-2">
              <button disabled className="btn-outline cursor-not-allowed opacity-40">{t.orders.detail.requestRefund}</button>
              {order.refundBlockReasonCode && <span className="text-xs text-ink-soft">{te(order.refundBlockReasonCode)}</span>}
            </span>
          )
        )}
      </div>

      {paySecret && (
        <div className="mt-6 max-w-lg">
          <PaymentElementPanel clientSecret={paySecret} mode={payMode} orderId={order.id} amountLabel={formatAmount(order.totalAmount, order.currency)} />
        </div>
      )}

      {/* 退款工单状态条 */}
      {(order.refunds ?? []).length > 0 && (
        <div className="mt-6 space-y-2">
          {(order.refunds ?? []).map((r) => (
            <div key={r.id} className="rounded-sm border border-line bg-surface px-4 py-3 text-sm">
              <div className="flex items-center justify-between">
                <span className="text-ink-soft">{t.orders.detail.refundNo.replace('{no}', r.refundNo)} · {formatDateTimeLong(r.appliedAt)}</span>
                <span className="flex items-center gap-3">
                  <span className="font-medium">{formatAmount(r.amount, r.currency)}</span>
                  <span className={cn('rounded-full px-3 py-0.5 text-xs capitalize', r.status === RefundStatus.APPROVED ? 'bg-sage/15 text-sage-deep' : r.status === RefundStatus.REJECTED ? 'bg-blush/15 text-blush' : 'bg-gold/15 text-gold-deep')}>{refundStatusLabel(r.status, refundLabels)}</span>
                </span>
              </div>
              {r.status === RefundStatus.REJECTED && r.rejectReason && <p className="mt-1 text-xs text-ink-soft">{r.rejectReason}</p>}
            </div>
          ))}
        </div>
      )}

      {/* 包裹（order-flow-complete D） */}
      {(order.shipments ?? []).length > 0 && (
        <div className="mt-8" data-testid="shipments-section">
          <h2 className="mb-4 font-display text-xl font-medium">{t.orders.detail.shipments}</h2>
          <div className="grid gap-4 lg:grid-cols-2">
            {(order.shipments ?? []).map((s) => <ShipmentCard key={s.id} shipment={s} />)}
          </div>
        </div>
      )}

      {/* Items + Summary */}
      <div className="mt-8 grid gap-8 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <h2 className="mb-4 font-display text-xl font-medium">{t.orders.detail.items}</h2>
          <div className="rounded-sm border border-line">
            {order.lines.map((it) => (
              <div key={it.id} className="flex items-center gap-4 border-b border-line/60 p-4 last:border-0">
                {it.img ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={it.img} alt={it.productName} className="h-20 w-14 rounded-sm object-cover" />
                ) : (
                  <div className="h-20 w-14 rounded-sm bg-muted" />
                )}
                <div className="flex-1">
                  <p className="text-sm font-medium">{it.productName} {it.customSizeData && <span className="ml-1 rounded-full bg-gold/10 px-2 py-0.5 text-[10px] uppercase tracking-luxe text-gold-deep">{t.checkout.custom}</span>}</p>
                  <p className="text-xs text-ink-soft">{[it.color, it.customSizeData ? undefined : it.size, t.checkout.qty.replace('{count}', String(it.qty))].filter(Boolean).join(' · ')}</p>
                  {it.customSizeData && (
                    <p className="mt-0.5 text-xs text-ink-faint">
                      {t.cart.page.bust} {it.customSizeData.bust}″ · {t.cart.page.waist} {it.customSizeData.waist}″ · {t.cart.page.hips} {it.customSizeData.hips}″ · {t.cart.page.hollowToFloor} {it.customSizeData.hollowToFloor}″
                    </p>
                  )}
                </div>
                <span className="text-sm font-medium">{formatAmount(it.unitPrice * it.qty, order.currency)}</span>
              </div>
            ))}
          </div>

          {(order.events ?? []).length > 0 && (
            <div className="mt-8">
              <OrderActivity events={order.events ?? []} />
            </div>
          )}
        </div>

        <div>
          <h2 className="mb-4 font-display text-xl font-medium">{t.checkout.summary}</h2>
          <dl className="space-y-2 rounded-sm border border-line bg-surface p-5 text-sm">
            <div className="flex justify-between"><dt className="text-ink-soft">{t.checkout.subtotal}</dt><dd>{formatAmount(order.subtotal, order.currency)}</dd></div>
            <div className="flex justify-between"><dt className="text-ink-soft">{t.checkout.shipping}</dt><dd>{(order.shippingFee ?? 0) === 0 ? t.checkout.free : formatAmount(order.shippingFee ?? 0, order.currency)}</dd></div>
            {order.giftWrap && <div className="flex justify-between"><dt className="text-ink-soft">{t.checkout.giftWrappingLabel}</dt><dd>{formatAmount(order.giftWrapFee ?? 0, order.currency)}</dd></div>}
            {showTax && (
              <div data-testid="order-tax-row">
                <div className="flex justify-between">
                  <dt className="text-ink-soft">
                    {(order.taxBreakdown ?? []).length > 0 ? (
                      <button type="button" onClick={() => setTaxOpen((v) => !v)} className="inline-flex cursor-pointer items-center gap-1 underline decoration-dotted underline-offset-2" aria-expanded={taxOpen} aria-label={t.checkout.taxDetails}>
                        {t.orders.detail.tax} <ChevronDown className={cn('h-3 w-3 transition-transform', taxOpen && 'rotate-180')} />
                      </button>
                    ) : t.orders.detail.tax}
                  </dt>
                  <dd>{formatAmount(order.taxAmount ?? 0, order.currency)}</dd>
                </div>
                {taxOpen && (order.taxBreakdown ?? []).length > 0 && (
                  <ul className="mt-1 space-y-0.5 pl-3 text-xs text-ink-faint">
                    {(order.taxBreakdown ?? []).map((tb, i) => (
                      <li key={`${tb.type}-${i}`} className="flex justify-between"><span>{tb.label ?? `${(tb.rateScaled / 100).toFixed(1)}%`}</span><span>{formatAmount(tb.amount, order.currency)}</span></li>
                    ))}
                  </ul>
                )}
              </div>
            )}
            {(order.discountAmount ?? 0) > 0 && <div className="flex justify-between text-sage-deep"><dt>{t.checkout.discount}</dt><dd>-{formatAmount(order.discountAmount ?? 0, order.currency)}</dd></div>}
            <div className="flex justify-between border-t border-line pt-2 font-medium"><dt>{t.checkout.total}</dt><dd className="font-display text-lg">{formatAmount(order.totalAmount, order.currency)}</dd></div>
            {(order.refundedAmount ?? 0) > 0 && (
              <div className="flex justify-between text-blush" data-testid="order-refunded-row"><dt>{t.orders.detail.refunded}</dt><dd>-{formatAmount(order.refundedAmount ?? 0, order.currency)}</dd></div>
            )}
          </dl>

          <h2 className="mb-4 mt-8 font-display text-xl font-medium">{t.checkout.shippingAddress}</h2>
          <div className="rounded-sm border border-line bg-surface p-5 text-sm text-ink-soft">
            <p className="font-medium text-ink">{order.addressSnapshot.receiver}</p>
            <p className="mt-1">{order.addressSnapshot.line}<br />{order.addressSnapshot.city}{order.addressSnapshot.state ? `, ${order.addressSnapshot.state}` : ''} {order.addressSnapshot.zip}<br />{order.addressSnapshot.country}</p>
            {order.addressSnapshot.phone && <p className="mt-1">{order.addressSnapshot.phone}</p>}
          </div>

          {order.payment && (
            <>
              <h2 className="mb-4 mt-8 font-display text-xl font-medium">{t.checkout.payment}</h2>
              <div className="rounded-sm border border-line bg-surface p-5 text-sm text-ink-soft">
                <p>{order.payment.cardSummary ?? order.paymentMethod ?? 'Stripe'}</p>
                <p className="mt-1 capitalize">{t.orders.detail.statusLabel} {paymentStatusLabel(order.payment.status, paymentLabels)}</p>
                {order.payment.paidAt && <p className="mt-1">{t.orders.detail.paidAt.replace('{date}', formatDateTimeLong(order.payment.paidAt))}</p>}
              </div>
            </>
          )}
        </div>
      </div>

      {refundOpen && (
        <RefundModal
          onClose={() => setRefundOpen(false)}
          onSubmit={async (reason) => {
            await applyStoreRefund(order.id, reason)
            setRefundOpen(false)
            void load()
          }}
        />
      )}

      {toast && (
        <div role="status" className="fixed bottom-6 left-1/2 z-50 flex -translate-x-1/2 items-center gap-3 animate-fadeup rounded-sm bg-ink px-5 py-3 text-sm text-canvas shadow-lift" data-testid="reorder-toast">
          <span>{toast.text}</span>
          {toast.cartLink && <Link href="/cart" className="font-medium text-gold-light underline">{t.orders.detail.viewCart}</Link>}
        </div>
      )}
    </div>
  )
}

/** 申请退款弹窗（FORM-TRD-S05：reason 必填 ≤255；422602 政策说明；409605/409907 已有工单） */
function RefundModal({ onClose, onSubmit }: { onClose: () => void; onSubmit: (reason: string) => Promise<void> }) {
  const { t, te } = useI18n()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [graceDeadline, setGraceDeadline] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    const trimmed = reason.trim()
    if (!trimmed || trimmed.length > 255) { setError(t.orders.detail.refundReasonError); return }
    setBusy(true)
    setError(null)
    try {
      await onSubmit(trimmed)
    } catch (err) {
      if (err instanceof ApiError && err.code === 422602) {
        const deadline = (err.details as Record<string, unknown> | null)?.graceDeadline ?? (err.details as Record<string, unknown> | null)?.grace_deadline
        setGraceDeadline(typeof deadline === 'string' ? deadline : null)
        setError(te(422602))
      } else {
        setError(err instanceof ApiError ? te(err.code) : te(50000))
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-ink/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-md animate-fadeup rounded-sm bg-canvas p-7 shadow-lift">
        <button onClick={onClose} className="absolute right-4 top-4 cursor-pointer p-1" aria-label={t.common.close}><X className="h-5 w-5" /></button>
        <h2 className="font-display text-2xl font-medium">{t.orders.detail.refundTitle}</h2>
        <p className="mt-2 text-sm text-ink-soft">{t.orders.detail.refundBody}</p>
        <div className="mt-4">
          <label htmlFor="refund-reason" className="eyebrow mb-1.5 block">{t.orders.detail.refundReason}</label>
          <textarea
            id="refund-reason"
            rows={4}
            maxLength={255}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold"
          />
          <p className="mt-1 text-right text-[11px] text-ink-faint">{reason.length}/255</p>
        </div>
        {error && (
          <div className="mt-2 rounded-sm bg-blush/10 px-4 py-3 text-sm text-blush">
            <p>{error}</p>
            {graceDeadline && <p className="mt-1 text-xs">{t.orders.detail.refundWindowEnded.replace('{date}', formatDateTimeLong(graceDeadline))}</p>}
          </div>
        )}
        <button onClick={() => void submit()} disabled={busy} className="btn-primary mt-4 w-full disabled:opacity-60">{busy ? t.orders.detail.refundSubmitting : t.orders.detail.refundSubmit}</button>
      </div>
    </div>
  )
}
