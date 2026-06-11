'use client'

/**
 * PaymentElementPanel（COMP-TRD-S03，新组件 token 同源）：
 * - loadStripe + <Elements clientSecret> + <PaymentElement> + confirmPayment(return_url=/order-success?order_id=)。
 * - BNPL（Klarna/Afterpay）重定向流由 Payment Element 承载；失败 error.message 行内展示可重试。
 * - stub 模式降级（L2 设计/决策 25 测试态）：publishable key 缺失或 clientSecret 非真实 Stripe 凭据格式
 *   → 渲染测试态面板（不加载 Stripe JS），「Continue」直接进入 order-success 轮询（webhook stub 落账）。
 */

import { useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Lock, ShieldCheck } from 'lucide-react'
import { loadStripe, type Stripe } from '@stripe/stripe-js'
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js'

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
  amountLabel
}: {
  clientSecret: string
  orderId: number
  amountLabel: string
}) {
  const router = useRouter()
  const stub = useMemo(() => isStubSecret(clientSecret), [clientSecret])

  if (stub) {
    return (
      <div className="space-y-4 rounded-sm border border-line bg-surface p-5">
        <p className="flex items-center gap-2 text-sm font-medium"><ShieldCheck className="h-4 w-4 text-gold" /> Payment (test mode)</p>
        <p className="text-sm text-ink-soft">
          The payment service is running in test mode — no real card is required. Your order has been created and will be confirmed automatically.
        </p>
        <button
          onClick={() => router.push(`/order-success?order_id=${orderId}`)}
          className="btn-primary w-full"
        >
          <Lock className="h-4 w-4" /> Continue · {amountLabel}
        </button>
      </div>
    )
  }

  return (
    <Elements stripe={getStripe()} options={{ clientSecret }}>
      <PaymentForm orderId={orderId} amountLabel={amountLabel} />
    </Elements>
  )
}

function PaymentForm({ orderId, amountLabel }: { orderId: number; amountLabel: string }) {
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
      setError(confirmError.message ?? 'Payment failed. Please try again.')
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-4 rounded-sm border border-line bg-surface p-5">
      <PaymentElement />
      {error && <p className="text-sm text-blush">{error}</p>}
      <button onClick={confirm} disabled={!stripe || submitting} className="btn-primary w-full disabled:opacity-60">
        <Lock className="h-4 w-4" /> {submitting ? 'Processing…' : `Pay ${amountLabel}`}
      </button>
    </div>
  )
}
