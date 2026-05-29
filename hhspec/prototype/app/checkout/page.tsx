'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Check, Lock, CreditCard, Truck } from 'lucide-react'
import { useStore } from '@/components/store-provider'
import { formatPrice, cn } from '@/lib/utils'

const steps = ['Address', 'Shipping', 'Payment', 'Review'] as const

const shippingOptions = [
  { id: 'fedex', name: 'FedEx International', eta: '3–5 business days', price: 0 },
  { id: 'ups', name: 'UPS Worldwide Saver', eta: '2–4 business days', price: 18 },
  { id: 'dhl', name: 'DHL Express', eta: '1–2 business days', price: 35 }
]

const payments = [
  { id: 'card', name: 'Credit / Debit Card', desc: 'Visa, Mastercard, Amex' },
  { id: 'paypal', name: 'PayPal', desc: 'Pay with your PayPal balance' },
  { id: 'apple', name: 'Apple Pay', desc: 'Fast checkout with Face ID' },
  { id: 'google', name: 'Google Pay', desc: 'Pay with Google' },
  { id: 'klarna', name: 'Klarna', desc: 'Pay in 4 interest-free' },
  { id: 'afterpay', name: 'Afterpay', desc: 'Pay in 4 interest-free' }
]

export default function CheckoutPage() {
  const router = useRouter()
  const { cart, cartSubtotal, currency, clearCart } = useStore()
  const [step, setStep] = useState(0)
  const [shipMethod, setShipMethod] = useState('fedex')
  const [payMethod, setPayMethod] = useState('card')

  const shipCost = shippingOptions.find((s) => s.id === shipMethod)?.price ?? 0
  const total = cartSubtotal + shipCost

  if (cart.length === 0) {
    return (
      <div className="container-luxe py-24 text-center">
        <h1 className="font-display text-3xl">Your bag is empty</h1>
        <Link href="/wedding-dresses" className="btn-primary mt-6 inline-flex">Continue Shopping</Link>
      </div>
    )
  }

  const placeOrder = () => { clearCart(); router.push('/order-success') }

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
              <h2 className="font-display text-2xl font-medium">Contact & Shipping Address</h2>
              <Field label="Email address" type="email" placeholder="you@email.com" />
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="First name" placeholder="Jane" />
                <Field label="Last name" placeholder="Doe" />
              </div>
              <Field label="Address" placeholder="123 Coastal Ave" />
              <div className="grid gap-4 sm:grid-cols-3">
                <Field label="City" placeholder="Santa Barbara" />
                <Field label="State" placeholder="CA" />
                <Field label="ZIP" placeholder="93101" />
              </div>
              <div>
                <label className="eyebrow mb-1.5 block">Country</label>
                <select className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold">
                  <option>United States</option><option>Canada</option><option>Australia</option><option>United Kingdom</option>
                </select>
              </div>
              <button onClick={() => setStep(1)} className="btn-primary mt-4 w-full sm:w-auto">Continue to Shipping</button>
            </div>
          )}

          {step === 1 && (
            <div className="space-y-4">
              <h2 className="flex items-center gap-2 font-display text-2xl font-medium"><Truck className="h-6 w-6 text-gold" /> Shipping Method</h2>
              {shippingOptions.map((o) => (
                <label key={o.id} className={cn('flex cursor-pointer items-center justify-between rounded-sm border p-4 transition-colors', shipMethod === o.id ? 'border-gold bg-gold/5' : 'border-line hover:border-ink')}>
                  <div className="flex items-center gap-3">
                    <input type="radio" name="ship" checked={shipMethod === o.id} onChange={() => setShipMethod(o.id)} className="accent-gold" />
                    <div><p className="text-sm font-medium">{o.name}</p><p className="text-xs text-ink-soft">{o.eta}</p></div>
                  </div>
                  <span className="text-sm font-medium">{o.price === 0 ? 'Free' : formatPrice(o.price, currency)}</span>
                </label>
              ))}
              <label className="mt-2 flex items-center gap-2 text-sm text-ink-soft"><input type="checkbox" className="accent-gold" /> Add gift wrapping (+$15)</label>
              <div className="flex gap-3 pt-2">
                <button onClick={() => setStep(0)} className="btn-outline">Back</button>
                <button onClick={() => setStep(2)} className="btn-primary">Continue to Payment</button>
              </div>
            </div>
          )}

          {step === 2 && (
            <div className="space-y-4">
              <h2 className="flex items-center gap-2 font-display text-2xl font-medium"><CreditCard className="h-6 w-6 text-gold" /> Payment</h2>
              <div className="grid gap-3 sm:grid-cols-2">
                {payments.map((p) => (
                  <label key={p.id} className={cn('flex cursor-pointer items-center gap-3 rounded-sm border p-4 transition-colors', payMethod === p.id ? 'border-gold bg-gold/5' : 'border-line hover:border-ink')}>
                    <input type="radio" name="pay" checked={payMethod === p.id} onChange={() => setPayMethod(p.id)} className="accent-gold" />
                    <div><p className="text-sm font-medium">{p.name}</p><p className="text-xs text-ink-soft">{p.desc}</p></div>
                  </label>
                ))}
              </div>
              {payMethod === 'card' && (
                <div className="space-y-4 rounded-sm bg-muted p-5">
                  <Field label="Card number" placeholder="4242 4242 4242 4242" />
                  <div className="grid grid-cols-2 gap-4">
                    <Field label="Expiry" placeholder="MM / YY" />
                    <Field label="CVC" placeholder="123" />
                  </div>
                </div>
              )}
              {(payMethod === 'klarna' || payMethod === 'afterpay') && (
                <p className="rounded-sm bg-sage/10 p-4 text-sm text-sage-deep">4 interest-free payments of {formatPrice(total / 4, currency)}. You&apos;ll be redirected to {payMethod === 'klarna' ? 'Klarna' : 'Afterpay'} to complete.</p>
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
                {cart.map((line, i) => (
                  <div key={i} className="flex items-center gap-4 border-b border-line/60 p-4 last:border-0">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={line.image} alt={line.name} className="h-20 w-14 rounded-sm object-cover" />
                    <div className="flex-1"><p className="text-sm font-medium">{line.name}</p><p className="text-xs text-ink-soft">{line.color} · {line.size} · Qty {line.qty}</p></div>
                    <span className="text-sm font-medium">{formatPrice(line.price * line.qty, currency)}</span>
                  </div>
                ))}
              </div>
              <div className="grid gap-4 text-sm sm:grid-cols-3">
                <div className="rounded-sm bg-muted p-4"><p className="eyebrow mb-1">Ship to</p><p className="text-ink-soft">Jane Doe<br />123 Coastal Ave<br />Santa Barbara, CA</p></div>
                <div className="rounded-sm bg-muted p-4"><p className="eyebrow mb-1">Shipping</p><p className="text-ink-soft">{shippingOptions.find((s) => s.id === shipMethod)?.name}</p></div>
                <div className="rounded-sm bg-muted p-4"><p className="eyebrow mb-1">Payment</p><p className="text-ink-soft">{payments.find((p) => p.id === payMethod)?.name}</p></div>
              </div>
              <div className="flex gap-3">
                <button onClick={() => setStep(2)} className="btn-outline">Back</button>
                <button onClick={placeOrder} className="btn-primary flex-1"><Lock className="h-4 w-4" /> Place Order · {formatPrice(total, currency)}</button>
              </div>
            </div>
          )}
        </div>

        {/* Order summary */}
        <div>
          <div className="rounded-sm border border-line bg-surface p-6 lg:sticky lg:top-28">
            <h3 className="font-display text-xl font-medium">Summary</h3>
            <div className="mt-4 max-h-48 space-y-3 overflow-y-auto">
              {cart.map((line, i) => (
                <div key={i} className="flex items-center gap-3">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={line.image} alt={line.name} className="h-14 w-10 rounded-sm object-cover" />
                  <div className="flex-1 text-xs"><p className="font-medium">{line.name}</p><p className="text-ink-soft">{line.size} · Qty {line.qty}</p></div>
                  <span className="text-xs font-medium">{formatPrice(line.price * line.qty, currency)}</span>
                </div>
              ))}
            </div>
            <dl className="mt-4 space-y-2 border-t border-line pt-4 text-sm">
              <div className="flex justify-between"><dt className="text-ink-soft">Subtotal</dt><dd>{formatPrice(cartSubtotal, currency)}</dd></div>
              <div className="flex justify-between"><dt className="text-ink-soft">Shipping</dt><dd>{shipCost === 0 ? 'Free' : formatPrice(shipCost, currency)}</dd></div>
              <div className="flex justify-between border-t border-line pt-2 font-medium"><dt>Total</dt><dd className="font-display text-lg">{formatPrice(total, currency)}</dd></div>
            </dl>
          </div>
        </div>
      </div>
    </div>
  )
}

function Field({ label, type = 'text', placeholder }: { label: string; type?: string; placeholder?: string }) {
  const id = label.toLowerCase().replace(/\s/g, '-')
  return (
    <div>
      <label htmlFor={id} className="eyebrow mb-1.5 block">{label}</label>
      <input id={id} type={type} placeholder={placeholder} className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold" />
    </div>
  )
}
