'use client'

/**
 * 结算页（PAGE-TRD-S02 / COMP-TRD-S02，四步结构保持 Address/Shipping/Payment/Review）：
 * - 登录守卫：未登录 → /account/login?returnTo=/checkout。
 * - Address 步：地址簿卡片单选 + Add new address 内联表单（createAddress）。
 * - Shipping 步：radio 改 quote.shippingOptions 渲染（carrier 文案以 API 为准，F-036）；
 *   gift wrapping 费用 = quote.giftWrapFee 动态金额；wedding date 选填（决策 20.6，Showroom 婚期自动带入）
 *   + leadTimeWarning 交期复核提示条；coupon code Apply（E-MKT-10，valid=false reason_code 行内不阻断）。
 * - Payment 步：六卡片视觉保留，PayPal 置灰 Coming soon（决策 25）；Place Order 时才 createOrder 取 clientSecret。
 * - Review 步：金额拆分以 quote 为准（Subtotal/Shipping/Gift Wrapping/Discount/Total，决策 28）+ DDU 关税说明（决策 15）。
 * - 下单（FORM-TRD-S03）：idempotencyKey 进入结算生成一次，失败重试沿用；409603 → 静默跳既有订单；
 *   409601 → 回购物车提示；502601/504601 → 可重试。
 * - 报价（FORM-TRD-S02）：地址/承运商/币种/礼品包装/券码/婚期变化 → 防抖 400ms requestQuote；
 *   422605 → 币种回退 USD + 提示。
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Check, Lock, CreditCard, Truck, Plus, AlertTriangle } from 'lucide-react'
import { useStore } from '@/components/store-provider'
import { useAuthStore } from '@/lib/stores/auth-store'
import { useI18n } from '@/lib/i18n/i18n-context'
import { ApiError } from '@/lib/api/client'
import * as tradingApi from '@/lib/api/trading-api'
import { validateCoupon } from '@/lib/api/marketing-api'
import { getDefaultWeddingDate } from '@/lib/stores/showroom-store'
import { trackBeginCheckout } from '@/lib/analytics/gtag'
import type { Address, CheckoutQuoteResponse, CouponValidateResponse, CurrencyCode, PaymentCredential, PaymentMethod } from '@/lib/api/store-types'
import { PaymentElementPanel } from '@/components/cart/payment-element-panel'
import { formatAmount, cn } from '@/lib/utils'

const steps = ['Address', 'Shipping', 'Payment', 'Review'] as const

const payments: { id: string; name: string; desc: string; method: PaymentMethod | null }[] = [
  { id: 'card', name: 'Credit / Debit Card', desc: 'Visa, Mastercard, Amex', method: 'Stripe' },
  { id: 'paypal', name: 'PayPal', desc: 'Coming soon', method: null },
  { id: 'apple', name: 'Apple Pay', desc: 'Fast checkout with Face ID', method: 'Apple Pay' },
  { id: 'google', name: 'Google Pay', desc: 'Pay with Google', method: 'Google Pay' },
  { id: 'klarna', name: 'Klarna', desc: 'Pay in 4 interest-free', method: 'Klarna' },
  { id: 'afterpay', name: 'Afterpay', desc: 'Pay in 4 interest-free', method: 'Afterpay' }
]

export default function CheckoutPage() {
  const router = useRouter()
  const { cart, currency, cartSubtotal } = useStore()
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const hydrated = useAuthStore((s) => s.hydrated)
  const hydrate = useAuthStore((s) => s.hydrate)
  const { locale, te } = useI18n()

  const [step, setStep] = useState(0)

  // Address 步
  const [addresses, setAddresses] = useState<Address[] | null>(null)
  const [addressId, setAddressId] = useState<number | null>(null)
  const [addingAddress, setAddingAddress] = useState(false)
  const [addressError, setAddressError] = useState<string | null>(null)

  // Shipping 步
  const [carrier, setCarrier] = useState<string | null>(null)
  const [giftWrap, setGiftWrap] = useState(false)
  const [weddingDate, setWeddingDate] = useState('')
  const [couponInput, setCouponInput] = useState('')
  const [couponCode, setCouponCode] = useState<string | undefined>(undefined)
  const [couponResult, setCouponResult] = useState<CouponValidateResponse | null>(null)
  const [couponApplying, setCouponApplying] = useState(false)
  const [couponMessage, setCouponMessage] = useState<string | null>(null)

  // 报价
  const [quote, setQuote] = useState<CheckoutQuoteResponse | null>(null)
  const [quoting, setQuoting] = useState(false)
  const [quoteError, setQuoteError] = useState<string | null>(null)
  const [quoteCurrency, setQuoteCurrency] = useState<CurrencyCode>((currency as CurrencyCode) ?? 'USD')

  // Payment / 下单
  const [payMethod, setPayMethod] = useState('card')
  const idempotencyKey = useRef<string>('')
  const [placing, setPlacing] = useState(false)
  const [placeError, setPlaceError] = useState<string | null>(null)
  const [payment, setPayment] = useState<(PaymentCredential & { orderId: number }) | null>(null)

  // idempotencyKey：进入结算流程生成一次（FORM-TRD-S03 / STORE-TRD-S02）
  useEffect(() => {
    if (!idempotencyKey.current) {
      idempotencyKey.current = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`
    }
  }, [])

  useEffect(() => { void hydrate() }, [hydrate])
  useEffect(() => {
    if (hydrated && !isAuthenticated) router.replace('/account/login?returnTo=/checkout')
  }, [hydrated, isAuthenticated, router])

  // GA4 begin_checkout（决策 19）：进入结算且购物车非空时上报一次（consent denied 时 no-op）
  const beginCheckoutTracked = useRef(false)
  useEffect(() => {
    if (beginCheckoutTracked.current || !hydrated || !isAuthenticated || cart.length === 0) return
    beginCheckoutTracked.current = true
    trackBeginCheckout({
      currency: 'USD',
      value: cartSubtotal,
      items: cart.map((l) => ({
        item_id: String(l.productId),
        item_name: l.name,
        price: l.priceUsd,
        quantity: l.qty,
        item_variant: [l.color, l.size].filter(Boolean).join(' / ') || undefined
      }))
    })
  }, [hydrated, isAuthenticated, cart, cartSubtotal])

  // 币种跟随全站切换器
  useEffect(() => { setQuoteCurrency(currency as CurrencyCode) }, [currency])

  // 地址簿装载
  useEffect(() => {
    if (!hydrated || !isAuthenticated) return
    tradingApi.listAddresses()
      .then((items) => {
        setAddresses(items)
        const def = items.find((a) => a.isDefault) ?? items[0]
        if (def) setAddressId((prev) => prev ?? def.id)
        if (items.length === 0) setAddingAddress(true)
      })
      .catch(() => setAddresses([]))
  }, [hydrated, isAuthenticated])

  // Showroom 婚期自动带入（FORM-SHR-S10 / 决策 20.6）
  useEffect(() => {
    if (!hydrated || !isAuthenticated) return
    void getDefaultWeddingDate().then((d) => {
      if (d) setWeddingDate((prev) => prev || d)
    })
  }, [hydrated, isAuthenticated])

  // requestQuote（防抖 400ms，输入变化整体替换 quote）
  const quoteSeq = useRef(0)
  const requestQuote = useCallback(() => {
    if (!isAuthenticated || cart.length === 0) return
    const seq = ++quoteSeq.current
    setQuoting(true)
    setQuoteError(null)
    tradingApi.quoteCheckout({
      addressId: addressId ?? undefined,
      currency: quoteCurrency,
      carrier: carrier ?? undefined,
      couponCode,
      giftWrap,
      weddingDate: weddingDate || undefined
    })
      .then((res) => {
        if (seq !== quoteSeq.current) return
        setQuote(res)
        if (!carrier) {
          const selected = res.shippingOptions.find((o) => o.selected) ?? res.shippingOptions[0]
          if (selected) setCarrier(selected.carrier)
        }
      })
      .catch((err: unknown) => {
        if (seq !== quoteSeq.current) return
        if (err instanceof ApiError && err.code === 422605) {
          // 币种不支持 → 回退 USD（FORM-TRD-S02）
          setQuoteCurrency('USD')
          setQuoteError(te(422605))
        } else {
          setQuoteError(err instanceof ApiError ? te(err.code) : te(50000))
        }
      })
      .finally(() => {
        if (seq === quoteSeq.current) setQuoting(false)
      })
  }, [isAuthenticated, cart.length, addressId, quoteCurrency, carrier, couponCode, giftWrap, weddingDate, te])

  useEffect(() => {
    if (step < 1) return
    const t = setTimeout(requestQuote, 400)
    return () => clearTimeout(t)
  }, [step, requestQuote])

  const applyCoupon = async () => {
    const code = couponInput.trim().toUpperCase()
    if (!code) return
    setCouponApplying(true)
    setCouponMessage(null)
    try {
      const res = await validateCoupon(code, cartSubtotal)
      setCouponResult(res)
      if (res.valid) {
        setCouponCode(code)
        setCouponMessage(null)
      } else {
        setCouponCode(undefined)
        setCouponMessage(res.reasonCode ? te(res.reasonCode) : te(422701))
      }
    } catch (err) {
      if (err instanceof ApiError && (err.code === 40100 || err.code === 40102)) {
        router.push('/account/login?returnTo=/checkout')
        return
      }
      setCouponMessage(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setCouponApplying(false)
    }
  }

  const removeCoupon = () => {
    setCouponCode(undefined)
    setCouponResult(null)
    setCouponInput('')
    setCouponMessage(null)
  }

  const placeOrder = async () => {
    if (!addressId || !carrier) return
    const method = payments.find((p) => p.id === payMethod)?.method ?? 'Stripe'
    setPlacing(true)
    setPlaceError(null)
    try {
      const res = await tradingApi.createOrder({
        idempotencyKey: idempotencyKey.current,
        addressId,
        currency: quoteCurrency,
        carrier,
        couponCode,
        giftWrap,
        weddingDate: weddingDate || undefined,
        paymentMethod: method,
        locale
      })
      setPayment({ ...res.payment, orderId: res.order.id })
    } catch (err) {
      if (err instanceof ApiError && err.code === 409603) {
        // 重复提交 → 静默跳既有订单支付页（error-strategy）
        const existingId = (err.details as Record<string, unknown> | null)?.orderId ?? (err.details as Record<string, unknown> | null)?.order_id
        if (existingId) {
          router.push(`/account/orders/${existingId}`)
          return
        }
      }
      if (err instanceof ApiError && err.code === 409601) {
        setPlaceError(`${te(409601)} `)
      } else if (err instanceof ApiError && (err.code === 502601 || err.code === 504601)) {
        setPlaceError(te(err.code))
      } else if (err instanceof ApiError && err.code === 422703) {
        // 券失效 → 去券重报价
        removeCoupon()
        setPlaceError(te(422703))
      } else {
        setPlaceError(err instanceof ApiError ? te(err.code) : te(50000))
      }
    } finally {
      setPlacing(false)
    }
  }

  if (!hydrated || !isAuthenticated) {
    return <div className="container-luxe py-24 text-center text-ink-soft">Loading…</div>
  }

  if (cart.length === 0 && !payment) {
    return (
      <div className="container-luxe py-24 text-center">
        <h1 className="font-display text-3xl">Your bag is empty</h1>
        <Link href="/wedding-dresses" className="btn-primary mt-6 inline-flex">Continue Shopping</Link>
      </div>
    )
  }

  const cur = quote?.currency ?? quoteCurrency
  const selectedAddress = addresses?.find((a) => a.id === addressId)

  return (
    <div className="container-luxe py-10">
      {/* Stepper */}
      <div className="mx-auto mb-10 flex max-w-2xl items-center justify-between">
        {steps.map((s, i) => (
          <div key={s} className="flex flex-1 items-center">
            <div className="flex flex-col items-center">
              <div className={cn('flex h-9 w-9 items-center justify-center rounded-full border text-sm transition-colors', i < step ? 'border-gold bg-gold text-white' : i === step ? 'border-ink bg-ink text-canvas' : 'border-line text-ink-faint')}>
                {i < step ? <Check className="h-4 w-4" /> : i + 1}
              </div>
              <span className={cn('mt-1.5 text-[11px] uppercase tracking-luxe', i <= step ? 'text-ink' : 'text-ink-faint')}>{s}</span>
            </div>
            {i < steps.length - 1 && <div className={cn('mx-2 h-px flex-1', i < step ? 'bg-gold' : 'bg-line')} />}
          </div>
        ))}
      </div>

      <div className="grid gap-12 lg:grid-cols-3">
        <div className="lg:col-span-2">
          {step === 0 && (
            <div className="space-y-4">
              <h2 className="font-display text-2xl font-medium">Shipping Address</h2>
              {addresses === null ? (
                <p className="text-sm text-ink-soft">Loading addresses…</p>
              ) : (
                <>
                  <div className="grid gap-3 sm:grid-cols-2">
                    {addresses.map((a) => (
                      <label key={a.id} className={cn('flex cursor-pointer items-start gap-3 rounded-sm border p-4 transition-colors', addressId === a.id ? 'border-gold bg-gold/5' : 'border-line hover:border-ink')}>
                        <input type="radio" name="address" checked={addressId === a.id} onChange={() => setAddressId(a.id)} className="mt-1 accent-gold" />
                        <span className="text-sm">
                          <span className="font-medium">{a.receiver}</span>
                          {a.isDefault && <span className="ml-2 rounded-full bg-gold/15 px-2 py-0.5 text-[10px] text-gold-deep">Default</span>}
                          <span className="mt-1 block text-ink-soft">{a.line}<br />{a.city}{a.state ? `, ${a.state}` : ''} {a.zip}<br />{a.country}{a.phone ? ` · ${a.phone}` : ''}</span>
                        </span>
                      </label>
                    ))}
                  </div>
                  {addingAddress ? (
                    <AddressForm
                      onCancel={addresses.length > 0 ? () => setAddingAddress(false) : undefined}
                      onSaved={(created) => {
                        setAddresses((prev) => [...(prev ?? []), created])
                        setAddressId(created.id)
                        setAddingAddress(false)
                      }}
                    />
                  ) : (
                    <button onClick={() => setAddingAddress(true)} className="flex cursor-pointer items-center gap-2 rounded-sm border border-dashed border-gold/50 px-4 py-3 text-[12px] font-medium uppercase tracking-luxe text-gold-deep transition-colors hover:border-gold hover:bg-gold/5">
                      <Plus className="h-4 w-4" /> Add new address
                    </button>
                  )}
                  {addressError && <p className="text-xs text-blush">{addressError}</p>}
                  <button
                    onClick={() => {
                      if (!addressId) { setAddressError('Please select or add a shipping address.'); return }
                      setAddressError(null)
                      setStep(1)
                    }}
                    className="btn-primary mt-4 w-full sm:w-auto"
                  >
                    Continue to Shipping
                  </button>
                </>
              )}
            </div>
          )}

          {step === 1 && (
            <div className="space-y-4">
              <h2 className="flex items-center gap-2 font-display text-2xl font-medium"><Truck className="h-6 w-6 text-gold" /> Shipping Method</h2>
              {quoteError && <p className="rounded-sm bg-blush/10 px-4 py-3 text-sm text-blush">{quoteError}</p>}
              {quote === null && !quoteError ? (
                <div className="space-y-3" aria-hidden="true">
                  {[0, 1, 2].map((i) => <div key={i} className="h-16 animate-pulse rounded-sm bg-muted" />)}
                </div>
              ) : (
                (quote?.shippingOptions ?? []).map((o) => (
                  <label key={o.carrier} className={cn('flex cursor-pointer items-center justify-between rounded-sm border p-4 transition-colors', carrier === o.carrier ? 'border-gold bg-gold/5' : 'border-line hover:border-ink')}>
                    <div className="flex items-center gap-3">
                      <input type="radio" name="ship" checked={carrier === o.carrier} onChange={() => setCarrier(o.carrier)} className="accent-gold" />
                      <div><p className="text-sm font-medium">{o.carrier}</p>{o.leadTime && <p className="text-xs text-ink-soft">{o.leadTime}</p>}</div>
                    </div>
                    <span className="text-sm font-medium">{o.fee === 0 ? 'Free' : formatAmount(o.fee, cur)}</span>
                  </label>
                ))
              )}

              <label className="mt-2 flex items-center gap-2 text-sm text-ink-soft">
                <input type="checkbox" checked={giftWrap} onChange={(e) => setGiftWrap(e.target.checked)} className="accent-gold" />
                Add gift wrapping{quote && giftWrap && quote.giftWrapFee > 0 ? ` (+${formatAmount(quote.giftWrapFee, cur)})` : ''}
              </label>

              {/* wedding date 选填（决策 20.6） */}
              <div className="max-w-xs">
                <label htmlFor="wedding-date" className="eyebrow mb-1.5 block">Wedding date (optional)</label>
                <input id="wedding-date" type="date" value={weddingDate} onChange={(e) => setWeddingDate(e.target.value)} className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold" />
              </div>
              {quote?.leadTimeWarning && (
                <p className="flex items-start gap-2 rounded-sm bg-gold/10 px-4 py-3 text-sm text-gold-deep">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                  Heads up — production for this order may take up to {quote.maxLeadTimeDays ?? '—'} days, which is close to your wedding date. Consider rush options or contact a stylist.
                </p>
              )}

              {/* 券码（COMP-MKT-S11 / FORM-MKT-S03） */}
              <div>
                <p className="eyebrow mb-1.5">Promo code</p>
                <div className="flex max-w-sm gap-2">
                  <input
                    value={couponInput}
                    onChange={(e) => setCouponInput(e.target.value.toUpperCase())}
                    onKeyDown={(e) => { if (e.key === 'Enter') void applyCoupon() }}
                    placeholder="Promo code"
                    className="flex-1 rounded-sm border border-line bg-surface px-3 py-2.5 text-sm uppercase outline-none focus:border-gold"
                    aria-label="Promo code"
                  />
                  <button onClick={() => void applyCoupon()} disabled={couponApplying} className="cursor-pointer rounded-sm border border-ink px-4 py-2.5 text-xs uppercase tracking-luxe transition-colors hover:bg-ink hover:text-canvas disabled:opacity-60">
                    {couponApplying ? '…' : 'Apply'}
                  </button>
                </div>
                {couponCode && couponResult?.valid && (
                  <p className="mt-2 flex items-center gap-2 text-sm text-sage-deep">
                    <Check className="h-4 w-4" /> {couponCode} applied{couponResult.coupon?.name ? ` — ${couponResult.coupon.name}` : ''}
                    <button onClick={removeCoupon} className="cursor-pointer text-xs text-ink-soft underline">Remove</button>
                  </p>
                )}
                {couponMessage && <p className="mt-2 text-sm text-blush">{couponMessage}</p>}
                {quote && couponCode && quote.couponValid === false && quote.couponReasonCode && (
                  <p className="mt-2 text-sm text-blush">{te(quote.couponReasonCode)}</p>
                )}
              </div>

              <div className="flex gap-3 pt-2">
                <button onClick={() => setStep(0)} className="btn-outline">Back</button>
                <button onClick={() => setStep(2)} disabled={!carrier} className="btn-primary disabled:opacity-60">Continue to Payment</button>
              </div>
            </div>
          )}

          {step === 2 && (
            <div className="space-y-4">
              <h2 className="flex items-center gap-2 font-display text-2xl font-medium"><CreditCard className="h-6 w-6 text-gold" /> Payment</h2>
              <div className="grid gap-3 sm:grid-cols-2">
                {payments.map((p) => (
                  <label
                    key={p.id}
                    className={cn(
                      'flex items-center gap-3 rounded-sm border p-4 transition-colors',
                      p.method === null ? 'cursor-not-allowed opacity-50' : 'cursor-pointer',
                      payMethod === p.id ? 'border-gold bg-gold/5' : 'border-line', p.method !== null && payMethod !== p.id && 'hover:border-ink'
                    )}
                  >
                    <input type="radio" name="pay" disabled={p.method === null} checked={payMethod === p.id} onChange={() => setPayMethod(p.id)} className="accent-gold" />
                    <div>
                      <p className="text-sm font-medium">{p.name}{p.method === null && <span className="ml-2 rounded-full bg-muted px-2 py-0.5 text-[10px] uppercase tracking-luxe text-ink-faint">Coming soon</span>}</p>
                      <p className="text-xs text-ink-soft">{p.desc}</p>
                    </div>
                  </label>
                ))}
              </div>
              <p className="rounded-sm bg-muted p-4 text-sm text-ink-soft">
                You&apos;ll enter your payment details securely on the next step — powered by Stripe.
              </p>
              {(payMethod === 'klarna' || payMethod === 'afterpay') && quote && (
                <p className="rounded-sm bg-sage/10 p-4 text-sm text-sage-deep">4 interest-free payments of {formatAmount(quote.totalAmount / 4, cur)}. You&apos;ll be redirected to {payMethod === 'klarna' ? 'Klarna' : 'Afterpay'} to complete.</p>
              )}
              <div className="flex gap-3 pt-2">
                <button onClick={() => setStep(1)} className="btn-outline">Back</button>
                <button onClick={() => setStep(3)} className="btn-primary">Review Order</button>
              </div>
            </div>
          )}

          {step === 3 && (
            <div className="space-y-5">
              <h2 className="font-display text-2xl font-medium">Review Your Order</h2>
              <div className="rounded-sm border border-line">
                {cart.map((line) => (
                  <div key={line.key} className="flex items-center gap-4 border-b border-line/60 p-4 last:border-0">
                    {line.image ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={line.image} alt={line.name} className="h-20 w-14 rounded-sm object-cover" />
                    ) : (
                      <div className="h-20 w-14 rounded-sm bg-muted" />
                    )}
                    <div className="flex-1"><p className="text-sm font-medium">{line.name}</p><p className="text-xs text-ink-soft">{[line.color, line.customSizeData ? 'Custom' : line.size, `Qty ${line.qty}`].filter(Boolean).join(' · ')}</p></div>
                  </div>
                ))}
              </div>
              <div className="grid gap-4 text-sm sm:grid-cols-3">
                <div className="rounded-sm bg-muted p-4">
                  <p className="eyebrow mb-1">Ship to</p>
                  <p className="text-ink-soft">{selectedAddress ? <>{selectedAddress.receiver}<br />{selectedAddress.line}<br />{selectedAddress.city}{selectedAddress.state ? `, ${selectedAddress.state}` : ''}</> : '—'}</p>
                </div>
                <div className="rounded-sm bg-muted p-4"><p className="eyebrow mb-1">Shipping</p><p className="text-ink-soft">{carrier ?? '—'}</p></div>
                <div className="rounded-sm bg-muted p-4"><p className="eyebrow mb-1">Payment</p><p className="text-ink-soft">{payments.find((p) => p.id === payMethod)?.name}</p></div>
              </div>
              {/* DDU 关税说明（决策 15，静态 i18n 文案） */}
              <p className="rounded-sm bg-muted px-4 py-3 text-xs text-ink-soft">
                International orders are shipped DDU (Delivered Duty Unpaid) — import duties and taxes, where applicable, are collected by the carrier on delivery.
              </p>
              {placeError && (
                <p className="rounded-sm bg-blush/10 px-4 py-3 text-sm text-blush">
                  {placeError}
                  {placeError.startsWith(te(409601)) && <Link href="/cart" className="ml-1 underline">Adjust quantities</Link>}
                </p>
              )}
              {payment ? (
                <PaymentElementPanel
                  clientSecret={payment.clientSecret}
                  orderId={payment.orderId}
                  amountLabel={quote ? formatAmount(quote.totalAmount, cur) : ''}
                />
              ) : (
                <div className="flex gap-3">
                  <button onClick={() => setStep(2)} className="btn-outline">Back</button>
                  <button onClick={() => void placeOrder()} disabled={placing || !quote} className="btn-primary flex-1 disabled:opacity-60">
                    <Lock className="h-4 w-4" /> {placing ? 'Placing order…' : `Place Order${quote ? ` · ${formatAmount(quote.totalAmount, cur)}` : ''}`}
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Order summary（金额拆分以 quote 为准，决策 28） */}
        <div>
          <div className="rounded-sm border border-line bg-surface p-6 lg:sticky lg:top-28">
            <h3 className="font-display text-xl font-medium">Summary</h3>
            <div className="mt-4 max-h-48 space-y-3 overflow-y-auto">
              {cart.map((line) => (
                <div key={line.key} className="flex items-center gap-3">
                  {line.image ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={line.image} alt={line.name} className="h-14 w-10 rounded-sm object-cover" />
                  ) : (
                    <div className="h-14 w-10 rounded-sm bg-muted" />
                  )}
                  <div className="flex-1 text-xs"><p className="font-medium">{line.name}</p><p className="text-ink-soft">{line.customSizeData ? 'Custom' : line.size} · Qty {line.qty}</p></div>
                </div>
              ))}
            </div>
            <dl className="mt-4 space-y-2 border-t border-line pt-4 text-sm">
              {quote ? (
                <>
                  <div className="flex justify-between"><dt className="text-ink-soft">Subtotal</dt><dd>{formatAmount(quote.subtotal, cur)}</dd></div>
                  <div className="flex justify-between"><dt className="text-ink-soft">Shipping</dt><dd>{quote.shippingFee === 0 ? 'Free' : formatAmount(quote.shippingFee, cur)}</dd></div>
                  {quote.giftWrapFee > 0 && <div className="flex justify-between"><dt className="text-ink-soft">Gift Wrapping</dt><dd>{formatAmount(quote.giftWrapFee, cur)}</dd></div>}
                  {quote.discountAmount > 0 && <div className="flex justify-between text-sage-deep"><dt>Discount</dt><dd>-{formatAmount(quote.discountAmount, cur)}</dd></div>}
                  <div className="flex justify-between border-t border-line pt-2 font-medium"><dt>Total</dt><dd className="font-display text-lg">{formatAmount(quote.totalAmount, cur)}{quoting && <span className="ml-1 text-xs text-ink-faint">…</span>}</dd></div>
                </>
              ) : (
                <div className="flex justify-between"><dt className="text-ink-soft">Total</dt><dd className="text-ink-soft">Calculated at shipping step</dd></div>
              )}
            </dl>
          </div>
        </div>
      </div>
    </div>
  )
}

function AddressForm({ onSaved, onCancel }: { onSaved: (a: Address) => void; onCancel?: () => void }) {
  const { te } = useI18n()
  const [form, setForm] = useState({ receiver: '', phone: '', line: '', city: '', state: '', zip: '', country: 'United States', isDefault: false })
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((p) => ({ ...p, [key]: e.target.value }))

  const save = async () => {
    if (!form.receiver.trim() || !form.line.trim() || !form.city.trim() || !form.zip.trim() || !form.country.trim()) {
      setError('Please fill in all required fields.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      const created = await tradingApi.createAddress({
        receiver: form.receiver.trim(),
        phone: form.phone.trim() || undefined,
        line: form.line.trim(),
        city: form.city.trim(),
        state: form.state.trim() || undefined,
        zip: form.zip.trim(),
        country: form.country.trim(),
        isDefault: form.isDefault
      })
      onSaved(created)
    } catch (err) {
      setError(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-4 rounded-sm bg-muted p-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Full name" value={form.receiver} onChange={set('receiver')} placeholder="Jane Doe" />
        <Field label="Phone (optional)" value={form.phone} onChange={set('phone')} placeholder="+1 555 0100" />
      </div>
      <Field label="Address" value={form.line} onChange={set('line')} placeholder="123 Coastal Ave" />
      <div className="grid gap-4 sm:grid-cols-3">
        <Field label="City" value={form.city} onChange={set('city')} placeholder="Santa Barbara" />
        <Field label="State" value={form.state} onChange={set('state')} placeholder="CA" />
        <Field label="ZIP" value={form.zip} onChange={set('zip')} placeholder="93101" />
      </div>
      <div>
        <label className="eyebrow mb-1.5 block" htmlFor="addr-country">Country</label>
        <select id="addr-country" value={form.country} onChange={set('country')} className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold">
          <option>United States</option><option>Canada</option><option>Australia</option><option>United Kingdom</option><option>France</option><option>Spain</option><option>Germany</option>
        </select>
      </div>
      <label className="flex items-center gap-2 text-sm text-ink-soft">
        <input type="checkbox" checked={form.isDefault} onChange={(e) => setForm((p) => ({ ...p, isDefault: e.target.checked }))} className="accent-gold" />
        Set as default address
      </label>
      {error && <p className="text-xs text-blush">{error}</p>}
      <div className="flex gap-2">
        <button onClick={() => void save()} disabled={saving} className="btn-primary px-5 py-2.5 text-xs disabled:opacity-60">{saving ? 'Saving…' : 'Save Address'}</button>
        {onCancel && <button onClick={onCancel} className="btn-outline px-5 py-2.5 text-xs">Cancel</button>}
      </div>
    </div>
  )
}

function Field({ label, value, onChange, type = 'text', placeholder }: { label: string; value: string; onChange: (e: React.ChangeEvent<HTMLInputElement>) => void; type?: string; placeholder?: string }) {
  const id = label.toLowerCase().replace(/[^a-z]+/g, '-')
  return (
    <div>
      <label htmlFor={id} className="eyebrow mb-1.5 block">{label}</label>
      <input id={id} type={type} value={value} onChange={onChange} placeholder={placeholder} className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold" />
    </div>
  )
}
