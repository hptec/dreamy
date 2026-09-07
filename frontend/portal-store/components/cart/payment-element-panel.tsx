'use client'

/**
 * PaymentElementPanel（COMP-TRD-S03，新组件 token 同源）：
 * - loadStripe + <Elements clientSecret> + <PaymentElement> + confirmPayment(return_url=/order-success?order_id=)。
 * - BNPL（Klarna/Afterpay）重定向流由 Payment Element 承载；失败 error.message 行内展示可重试。
 * - stub 模式（order-flow-complete A / §4.1）：publishable key 缺失或 clientSecret 非真实 Stripe 凭据格式
 *   → 渲染测试态面板（不加载 Stripe JS），「Continue」调用 POST /orders/{id}/payment/confirm（服务端合成
 *   payment_intent.succeeded 走 webhook 链），成功后跳 order-success（首帧即 paid）。
 *   409602（已支付/非 PENDING）→ 直接跳成功页；404（real 模式端点不存在）→ 提示改走真实支付。
 */

import { useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Lock, ShieldCheck } from 'lucide-react'
import { loadStripe, type Stripe } from '@stripe/stripe-js'
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js'
import { ApiError } from '@/lib/api/client'
import { confirmStubPayment } from '@/lib/api/trading-api'
import { useI18n } from '@/lib/i18n/i18n-context'

const PUBLISHABLE_KEY = process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY ?? ''

let stripePromise: Promise<Stripe | null> | null = null
function getStripe(): Promise<Stripe | null> {
  if (!stripePromise) stripePromise = loadStripe(PUBLISHABLE_KEY)
  return stripePromise
}

/** 真实 Stripe client_secret 形如 `pi_xxx_secret_yyy`；stub 后端返回的 mock 凭证不匹配该格式 */
function isStubSecret(clientSecret: string): boolean {
  return !PUBLISHABLE_KEY || !/_secret_/.test(clientSecret) || /stub|mock|test_fake/i.test(clientSecret)
}

export function PaymentElementPanel({
  clientSecret,
  orderId,
  amountLabel,
  mode
}: {
  clientSecret: string
  orderId: number
  amountLabel: string
  mode?: 'stub' | 'real' | null
}) {
  // 后端显式 mode 优先；缺失时才按 clientSecret 形状回退判定
  const stub = useMemo(() => (mode ? mode === 'stub' : isStubSecret(clientSecret)), [mode, clientSecret])
  const { t } = useI18n()

  if (stub) {
    return <StubPaymentForm orderId={orderId} amountLabel={amountLabel} />
  }
  if (!PUBLISHABLE_KEY) {
    // real 模式但前端未配置 publishable key：显式暴露配置错误，而不是退化成模拟面板
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
        {t.paymentPanel.misconfigured}
      </div>
    )
  }

  return (
    <Elements stripe={getStripe()} options={{ clientSecret }}>
      <PaymentForm orderId={orderId} amountLabel={amountLabel} />
    </Elements>
  )
}

function StubPaymentForm({ orderId, amountLabel }: { orderId: number; amountLabel: string }) {
  const router = useRouter()
  const { t, te } = useI18n()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const confirm = async () => {
    if (submitting) return
    setSubmitting(true)
    setError(null)
    try {
      await confirmStubPayment(orderId)
      router.push(`/order-success?order_id=${orderId}`)
    } catch (err) {
      if (err instanceof ApiError && err.code === 409602) {
        // 已支付 / 非 PENDING（重复点击、并发确认）→ 直接进成功页轮询
        router.push(`/order-success?order_id=${orderId}`)
        return
      }
      if (err instanceof ApiError && err.httpStatus === 404) {
        setError(t.paymentPanel.stubUnavailable)
      } else {
        setError(err instanceof ApiError ? te(err.code) : te(50000))
      }
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-4 rounded-sm border border-line bg-surface p-5" data-testid="stub-payment-panel">
      <p className="flex items-center gap-2 text-sm font-medium"><ShieldCheck className="h-4 w-4 text-gold" /> {t.paymentPanel.testModeTitle}</p>
      <p className="text-sm text-ink-soft">{t.paymentPanel.testModeBody}</p>
      {error && <p className="text-sm text-blush">{error}</p>}
      <button onClick={() => void confirm()} disabled={submitting} className="btn-primary w-full disabled:opacity-60" data-testid="stub-pay-continue">
        <Lock className="h-4 w-4" /> {submitting ? t.paymentPanel.processing : `${t.paymentPanel.continueLabel} · ${amountLabel}`}
      </button>
    </div>
  )
}

function PaymentForm({ orderId, amountLabel }: { orderId: number; amountLabel: string }) {
  const { t } = useI18n()
  const stripe = useStripe()
  const elements = useElements()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const confirm = async () => {
    if (!stripe || !elements) return
    setSubmitting(true)
    setError(null)
    const { error: confirmError } = await stripe.confirmPayment({
      elements,
      confirmParams: {
        return_url: `${window.location.origin}/order-success?order_id=${orderId}`
      }
    })
    // 成功场景 Stripe 会重定向；走到这里即失败（行内展示可重试）
    if (confirmError) {
      setError(confirmError.message ?? t.paymentPanel.failed)
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-4 rounded-sm border border-line bg-surface p-5">
      <PaymentElement />
      {error && <p className="text-sm text-blush">{error}</p>}
      <button onClick={confirm} disabled={!stripe || submitting} className="btn-primary w-full disabled:opacity-60">
        <Lock className="h-4 w-4" /> {submitting ? t.paymentPanel.processing : t.paymentPanel.pay.replace('{amount}', amountLabel)}
      </button>
    </div>
  )
}
