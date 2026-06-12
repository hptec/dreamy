'use client'

/**
 * 订单详情（PAGE-TRD-S05 / COMP-TRD-S06，原列表 Details 链接指向页，按订单卡片同 token 风格构建）：
 * - 状态徽章 + 时间线（createdAt→paidAt→shippedAt→completedAt）、行列表（定制行展示 customSizeData）、
 *   地址快照、支付摘要、金额拆分（决策 28）。
 * - 动作区按状态渲染：pending →「Pay now」（retryPaymentIntent → PaymentElementPanel）+「Cancel order」二次确认；
 *   paid/shipped →「Request refund」（refundEligible=false 置灰 + refundBlockReasonCode 三语政策说明，决策 24）。
 * - refunds[] 工单状态条；410601 → Order expired 提示态（FORM-TRD-S04/S05）。
 */

import { useCallback, useEffect, useState, use } from 'react'
import Link from 'next/link'
import { Check, Truck, Clock, X } from 'lucide-react'
import type { StoreOrderDetail } from '@/lib/api/store-types'
import { OrderStatus, RefundStatus } from '@/lib/api/store-types'
import { getStoreOrder, cancelStoreOrder, retryOrderPayment, applyStoreRefund } from '@/lib/api/trading-api'
import { ApiError } from '@/lib/api/client'
import { useI18n } from '@/lib/i18n/i18n-context'
import { PaymentElementPanel } from '@/components/cart/payment-element-panel'
import { formatAmount, formatDateTimeLong, cn } from '@/lib/utils'
import { statusBadgeClass, orderStatusLabel, paymentStatusLabel, refundStatusLabel } from '@/lib/order-ui'

export default function OrderDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const orderId = Number(id)
  const { te } = useI18n()

  const [order, setOrder] = useState<StoreOrderDetail | null>(null)
  const [state, setState] = useState<'loading' | 'ready' | 'not-found' | 'error'>('loading')
  const [actionError, setActionError] = useState<string | null>(null)
  const [expired, setExpired] = useState(false)
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [paySecret, setPaySecret] = useState<string | null>(null)
  const [payLoading, setPayLoading] = useState(false)
  const [refundOpen, setRefundOpen] = useState(false)

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

  if (state === 'loading') {
    return <div className="space-y-4" aria-hidden="true"><div className="h-10 w-64 animate-pulse rounded-sm bg-muted" /><div className="h-48 animate-pulse rounded-sm bg-muted" /></div>
  }

  if (state === 'not-found' || state === 'error' || !order) {
    // 404 防探测：通用「不存在或无权访问」（error-strategy store 约定）
    return (
      <div className="py-16 text-center">
        <h1 className="font-display text-3xl font-medium">{state === 'error' ? 'Something went wrong' : 'Order not found'}</h1>
        <p className="mt-2 text-sm text-ink-soft">{state === 'error' ? te(50000) : te(404601)}</p>
        <div className="mt-6 flex justify-center gap-3">
          {state === 'error' && <button onClick={() => { setState('loading'); void load() }} className="btn-primary">Try Again</button>}
          <Link href="/account/orders" className="btn-outline">Back to orders</Link>
        </div>
      </div>
    )
  }

  const timeline = [
    { label: 'Placed', date: order.createdAt },
    { label: 'Paid', date: order.paidAt },
    { label: 'Shipped', date: order.shippedAt },
    { label: 'Completed', date: order.completedAt }
  ]
  const doneCount = timeline.filter((s) => !!s.date).length

  const payNow = async () => {
    setPayLoading(true)
    setActionError(null)
    try {
      const cred = await retryOrderPayment(order.id)
      setPaySecret(cred.clientSecret)
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

  return (
    <div>
      <Link href="/account/orders" className="text-sm text-gold-deep underline">← Back to orders</Link>
      <div className="mt-4 flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl font-medium">Order {order.orderNo}</h1>
          <p className="text-sm text-ink-soft">Placed {formatDateTimeLong(order.createdAt)}</p>
        </div>
        <span className={cn('rounded-full px-4 py-1.5 text-sm capitalize', statusBadgeClass(order.status))}>{orderStatusLabel(order.status)}</span>
      </div>

      {expired && (
        <p className="mt-4 flex items-center gap-2 rounded-sm bg-muted px-4 py-3 text-sm text-ink-soft">
          <Clock className="h-4 w-4 text-ink-faint" /> {te(410601)}
        </p>
      )}
      {actionError && <p className="mt-4 rounded-sm bg-blush/10 px-4 py-3 text-sm text-blush">{actionError}</p>}

      {/* 状态时间线 */}
      {order.status !== OrderStatus.CANCELLED && (
        <div className="mt-8 rounded-sm border border-line bg-surface p-6">
          {order.carrier && (
            <div className="mb-5 flex items-center gap-2">
              <Truck className="h-5 w-5 text-gold" />
              <p className="text-sm font-medium">{order.carrier}{order.trackingNo ? ` · ${order.trackingNo}` : ''}</p>
            </div>
          )}
          <div className="relative flex justify-between">
            <div className="absolute left-0 right-0 top-3 h-0.5 bg-line" />
            <div className="absolute left-0 top-3 h-0.5 bg-gold" style={{ width: `${Math.max(0, doneCount - 1) / (timeline.length - 1) * 100}%` }} />
            {timeline.map((s) => (
              <div key={s.label} className="relative z-10 flex flex-1 flex-col items-center text-center">
                <div className={cn('flex h-6 w-6 items-center justify-center rounded-full border-2 bg-surface', s.date ? 'border-gold' : 'border-line')}>
                  {s.date && <Check className="h-3 w-3 text-gold" />}
                </div>
                <p className={cn('mt-2 text-[11px]', s.date ? 'font-medium text-ink' : 'text-ink-faint')}>{s.label}</p>
                {s.date && <p className="text-[10px] text-ink-faint">{formatDateTimeLong(s.date)}</p>}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 动作区（按状态渲染） */}
      <div className="mt-6 flex flex-wrap items-center gap-3">
        {order.status === OrderStatus.PENDING && !paySecret && (
          <>
            <button onClick={() => void payNow()} disabled={payLoading} className="btn-primary disabled:opacity-60">{payLoading ? 'Loading…' : 'Pay now'}</button>
            {confirmCancel ? (
              <span className="flex items-center gap-2 text-sm">
                <span className="text-ink-soft">Cancel this order?</span>
                <button onClick={() => void cancelOrder()} disabled={cancelling} className="cursor-pointer font-medium text-blush underline">{cancelling ? 'Cancelling…' : 'Yes, cancel'}</button>
                <button onClick={() => setConfirmCancel(false)} className="cursor-pointer text-ink-soft underline">Keep order</button>
              </span>
            ) : (
              <button onClick={() => setConfirmCancel(true)} className="btn-outline"><X className="h-4 w-4" /> Cancel order</button>
            )}
          </>
        )}
        {(order.status === OrderStatus.PAID || order.status === OrderStatus.SHIPPED || order.status === OrderStatus.COMPLETED) && (
          order.refundEligible ? (
            <button onClick={() => setRefundOpen(true)} className="btn-outline">Request refund</button>
          ) : (
            <span className="flex items-center gap-2">
              <button disabled className="btn-outline cursor-not-allowed opacity-40">Request refund</button>
              {order.refundBlockReasonCode && <span className="text-xs text-ink-soft">{te(order.refundBlockReasonCode)}</span>}
            </span>
          )
        )}
      </div>

      {paySecret && (
        <div className="mt-6 max-w-lg">
          <PaymentElementPanel clientSecret={paySecret} orderId={order.id} amountLabel={formatAmount(order.totalAmount, order.currency)} />
        </div>
      )}

      {/* 退款工单状态条 */}
      {(order.refunds ?? []).length > 0 && (
        <div className="mt-6 space-y-2">
          {(order.refunds ?? []).map((r) => (
            <div key={r.id} className="flex items-center justify-between rounded-sm border border-line bg-surface px-4 py-3 text-sm">
              <span className="text-ink-soft">Refund {r.refundNo} · {formatDateTimeLong(r.appliedAt)}</span>
              <span className="flex items-center gap-3">
                <span className="font-medium">{formatAmount(r.amount, r.currency)}</span>
                <span className={cn('rounded-full px-3 py-0.5 text-xs capitalize', r.status === RefundStatus.APPROVED ? 'bg-sage/15 text-sage-deep' : r.status === RefundStatus.REJECTED ? 'bg-blush/15 text-blush' : 'bg-gold/15 text-gold-deep')}>{refundStatusLabel(r.status)}</span>
              </span>
            </div>
          ))}
        </div>
      )}

      {/* Items + Summary */}
      <div className="mt-8 grid gap-8 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <h2 className="mb-4 font-display text-xl font-medium">Items</h2>
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
                  <p className="text-sm font-medium">{it.productName} {it.customSizeData && <span className="ml-1 rounded-full bg-gold/10 px-2 py-0.5 text-[10px] uppercase tracking-luxe text-gold-deep">Custom</span>}</p>
                  <p className="text-xs text-ink-soft">{[it.color, it.customSizeData ? undefined : it.size, `Qty ${it.qty}`].filter(Boolean).join(' · ')}</p>
                  {it.customSizeData && (
                    <p className="mt-0.5 text-xs text-ink-faint">
                      Bust {it.customSizeData.bust}″ · Waist {it.customSizeData.waist}″ · Hips {it.customSizeData.hips}″ · Hollow-to-floor {it.customSizeData.hollowToFloor}″
                    </p>
                  )}
                </div>
                <span className="text-sm font-medium">{formatAmount(it.unitPrice * it.qty, order.currency)}</span>
              </div>
            ))}
          </div>
        </div>

        <div>
          <h2 className="mb-4 font-display text-xl font-medium">Summary</h2>
          <dl className="space-y-2 rounded-sm border border-line bg-surface p-5 text-sm">
            <div className="flex justify-between"><dt className="text-ink-soft">Subtotal</dt><dd>{formatAmount(order.subtotal, order.currency)}</dd></div>
            <div className="flex justify-between"><dt className="text-ink-soft">Shipping</dt><dd>{(order.shippingFee ?? 0) === 0 ? 'Free' : formatAmount(order.shippingFee ?? 0, order.currency)}</dd></div>
            {order.giftWrap && <div className="flex justify-between"><dt className="text-ink-soft">Gift Wrapping</dt><dd>{formatAmount(order.giftWrapFee ?? 0, order.currency)}</dd></div>}
            {(order.discountAmount ?? 0) > 0 && <div className="flex justify-between text-sage-deep"><dt>Discount</dt><dd>-{formatAmount(order.discountAmount ?? 0, order.currency)}</dd></div>}
            <div className="flex justify-between border-t border-line pt-2 font-medium"><dt>Total</dt><dd className="font-display text-lg">{formatAmount(order.totalAmount, order.currency)}</dd></div>
          </dl>

          <h2 className="mb-4 mt-8 font-display text-xl font-medium">Shipping Address</h2>
          <div className="rounded-sm border border-line bg-surface p-5 text-sm text-ink-soft">
            <p className="font-medium text-ink">{order.addressSnapshot.receiver}</p>
            <p className="mt-1">{order.addressSnapshot.line}<br />{order.addressSnapshot.city}{order.addressSnapshot.state ? `, ${order.addressSnapshot.state}` : ''} {order.addressSnapshot.zip}<br />{order.addressSnapshot.country}</p>
            {order.addressSnapshot.phone && <p className="mt-1">{order.addressSnapshot.phone}</p>}
          </div>

          {order.payment && (
            <>
              <h2 className="mb-4 mt-8 font-display text-xl font-medium">Payment</h2>
              <div className="rounded-sm border border-line bg-surface p-5 text-sm text-ink-soft">
                <p>{order.payment.cardSummary ?? order.paymentMethod ?? 'Stripe'}</p>
                <p className="mt-1 capitalize">Status: {paymentStatusLabel(order.payment.status)}</p>
                {order.payment.paidAt && <p className="mt-1">Paid {formatDateTimeLong(order.payment.paidAt)}</p>}
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
    </div>
  )
}

/** 申请退款弹窗（FORM-TRD-S05：reason 必填 ≤255；422602 政策说明；409605 已有工单） */
function RefundModal({ onClose, onSubmit }: { onClose: () => void; onSubmit: (reason: string) => Promise<void> }) {
  const { te } = useI18n()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [graceDeadline, setGraceDeadline] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    const trimmed = reason.trim()
    if (!trimmed || trimmed.length > 255) { setError('Please tell us briefly why you want a refund (max 255 characters).'); return }
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
        <button onClick={onClose} className="absolute right-4 top-4 cursor-pointer p-1" aria-label="Close"><X className="h-5 w-5" /></button>
        <h2 className="font-display text-2xl font-medium">Request a Refund</h2>
        <p className="mt-2 text-sm text-ink-soft">Tell us why you&apos;d like a refund and our team will review your request.</p>
        <div className="mt-4">
          <label htmlFor="refund-reason" className="eyebrow mb-1.5 block">Reason</label>
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
            {graceDeadline && <p className="mt-1 text-xs">Refund window ended {formatDateTimeLong(graceDeadline)}.</p>}
          </div>
        )}
        <button onClick={() => void submit()} disabled={busy} className="btn-primary mt-4 w-full disabled:opacity-60">{busy ? 'Submitting…' : 'Submit Request'}</button>
      </div>
    </div>
  )
}
