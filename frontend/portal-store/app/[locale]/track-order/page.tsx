'use client'

/**
 * 游客查单页 /track-order（order-flow-complete D/I，PAGE 新增）：
 * - 订单号 + 邮箱表单 → POST /api/store/orders/track（公开，IP 频控 10 次/小时）。
 * - 成功：状态徽章、6 步时间线、制作阶段（PAID）、预计送达、脱敏收件人 + 国家、包裹与轨迹、customer_visible 活动。
 * - 404601 → 「未找到匹配订单」；429601 → 频控提示；其余走 te(code)。
 * - 支持 ?order_no= 预填（邮件跳转）。
 */

import { Suspense, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { Search, PackageSearch } from 'lucide-react'
import { LocalizedLink as Link } from '@/components/localized-link'
import type { OrderTrackView } from '@/lib/api/store-types'
import { OrderStatus } from '@/lib/api/store-types'
import { trackOrder, listShippingCountries } from '@/lib/api/trading-api'
import { ApiError } from '@/lib/api/client'
import { useI18n } from '@/lib/i18n/i18n-context'
import { Eyebrow } from '@/components/ui/primitives'
import { OrderTimeline, ProductionStages, ShipmentCard, OrderActivity } from '@/components/orders/order-progress'
import { enumLabels, orderStatusLabel, statusBadgeClass } from '@/lib/order-ui'
import { cn, formatAmount, formatDateLong, formatDateTimeLong } from '@/lib/utils'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function TrackOrderInner() {
  const { t, te } = useI18n()
  const params = useSearchParams()
  const statusLabels = enumLabels(OrderStatus, t.orders.status)
  const [orderNo, setOrderNo] = useState(params.get('order_no') ?? '')
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<OrderTrackView | null>(null)
  const [countryName, setCountryName] = useState<string | null>(null)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    const no = orderNo.trim().toUpperCase()
    const mail = email.trim()
    if (!no || !EMAIL_RE.test(mail)) {
      setError(t.trackOrder.invalid)
      return
    }
    setBusy(true)
    setError(null)
    try {
      const view = await trackOrder(no, mail)
      setResult(view)
      if (view.countryCode) {
        listShippingCountries()
          .then((items) => setCountryName(items.find((c) => c.code === view.countryCode)?.name ?? view.countryCode ?? null))
          .catch(() => setCountryName(view.countryCode ?? null))
      }
    } catch (err) {
      if (err instanceof ApiError && err.httpStatus === 404) setError(t.trackOrder.notFound)
      else if (err instanceof ApiError && (err.code === 429601 || err.httpStatus === 429)) setError(t.trackOrder.rateLimited)
      else setError(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="container-luxe max-w-3xl py-16">
      <div className="text-center">
        <Eyebrow>{t.trackOrder.eyebrow}</Eyebrow>
        <h1 className="mt-2 font-display text-5xl font-medium">{t.trackOrder.title}</h1>
        <p className="mx-auto mt-3 max-w-xl text-ink-soft">{t.trackOrder.body}</p>
      </div>

      {!result && (
        <form onSubmit={submit} noValidate className="mx-auto mt-10 max-w-lg space-y-4 rounded-sm border border-line bg-surface p-6" data-testid="track-form">
          <div>
            <label htmlFor="track-order-no" className="eyebrow mb-1.5 block">{t.trackOrder.orderNo}</label>
            <input
              id="track-order-no"
              value={orderNo}
              onChange={(e) => setOrderNo(e.target.value)}
              placeholder={t.trackOrder.orderNoPlaceholder}
              autoComplete="off"
              className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm uppercase outline-none focus:border-gold"
            />
          </div>
          <div>
            <label htmlFor="track-email" className="eyebrow mb-1.5 block">{t.trackOrder.email}</label>
            <input
              id="track-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder={t.trackOrder.emailPlaceholder}
              autoComplete="email"
              className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold"
            />
          </div>
          {error && <p className="rounded-sm bg-blush/10 px-4 py-3 text-sm text-blush" data-testid="track-error">{error}</p>}
          <button type="submit" disabled={busy} className="btn-primary w-full disabled:opacity-60" data-testid="track-submit">
            <Search className="h-4 w-4" /> {busy ? t.trackOrder.searching : t.trackOrder.submit}
          </button>
          <p className="text-center text-xs text-ink-soft">
            {t.trackOrder.signInHint} <Link href="/account/login?returnTo=/account/orders" className="text-gold-deep underline">{t.trackOrder.signIn}</Link>
          </p>
        </form>
      )}

      {result && (
        <div className="mt-10 space-y-6" data-testid="track-result">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="font-display text-2xl font-medium">{t.trackOrder.orderLabel.replace('{no}', result.orderNo)}</h2>
              <p className="text-sm text-ink-soft">{t.trackOrder.placed.replace('{date}', formatDateTimeLong(result.createdAt))}</p>
              {result.estimatedDeliveryFrom && result.estimatedDeliveryTo && (result.status === OrderStatus.PAID || result.status === OrderStatus.SHIPPED) && (
                <p className="mt-1 text-xs text-ink-faint">{t.trackOrder.eta.replace('{from}', formatDateLong(result.estimatedDeliveryFrom)).replace('{to}', formatDateLong(result.estimatedDeliveryTo))}</p>
              )}
            </div>
            <span className={cn('rounded-full px-4 py-1.5 text-sm capitalize', statusBadgeClass(result.status))} data-testid="track-status" data-status={result.status}>{orderStatusLabel(result.status, statusLabels)}</span>
          </div>

          {result.status !== OrderStatus.CANCELLED && <OrderTimeline order={result} />}
          {result.status === OrderStatus.PAID && result.productionStage && <ProductionStages stage={result.productionStage} />}

          <div className="grid gap-4 text-sm sm:grid-cols-2">
            <div className="rounded-sm bg-muted p-4">
              <p className="eyebrow mb-1">{t.trackOrder.shipTo}</p>
              <p className="text-ink-soft">{result.receiverMasked ?? '—'}{countryName ? ` · ${countryName}` : result.countryCode ? ` · ${result.countryCode}` : ''}</p>
            </div>
            <div className="rounded-sm bg-muted p-4">
              <p className="eyebrow mb-1">{t.checkout.total}</p>
              <p className="font-display text-lg">{formatAmount(result.totalAmount, result.currency)}</p>
            </div>
          </div>

          {(result.shipments ?? []).length > 0 && (
            <div>
              <h3 className="mb-3 flex items-center gap-2 font-display text-xl font-medium"><PackageSearch className="h-5 w-5 text-gold" /> {t.trackOrder.packages}</h3>
              <div className="grid gap-4 lg:grid-cols-2">
                {result.shipments.map((s) => <ShipmentCard key={s.id} shipment={s} />)}
              </div>
            </div>
          )}

          {(result.events ?? []).length > 0 && <OrderActivity events={result.events} />}

          <div className="flex flex-wrap justify-center gap-3 pt-2">
            <button onClick={() => { setResult(null); setCountryName(null); setError(null) }} className="btn-outline" data-testid="track-again">{t.trackOrder.searchAgain}</button>
            <Link href="/account/login?returnTo=/account/orders" className="btn-primary">{t.trackOrder.signIn}</Link>
          </div>
        </div>
      )}
    </div>
  )
}

export default function TrackOrderPage() {
  return (
    <Suspense fallback={<div className="container-luxe py-24 text-center text-ink-soft">…</div>}>
      <TrackOrderInner />
    </Suspense>
  )
}
