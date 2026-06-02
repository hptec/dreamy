import { notFound } from 'next/navigation'
import Link from 'next/link'
import { Check, Truck } from 'lucide-react'
import { getOrder, orders } from '@/data/account'
import { formatPrice, cn } from '@/lib/utils'

export function generateStaticParams() {
  return orders.map((o) => ({ id: o.id }))
}

export default async function OrderDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  const order = getOrder(id)
  if (!order) notFound()

  const subtotal = order.items.reduce((s, i) => s + i.price * i.qty, 0)

  return (
    <div>
      <Link href="/account/orders" className="text-sm text-gold-deep underline">← Back to orders</Link>
      <div className="mt-4 flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl font-medium">Order #{order.id}</h1>
          <p className="text-sm text-ink-soft">Placed {order.date}</p>
        </div>
        <span className="rounded-full bg-muted px-4 py-1.5 text-sm">{order.status}</span>
      </div>

      {/* Tracking timeline */}
      {order.tracking && (
        <div className="mt-8 rounded-sm border border-line bg-surface p-6">
          <div className="mb-5 flex items-center gap-2">
            <Truck className="h-5 w-5 text-gold" />
            <p className="text-sm font-medium">{order.tracking.carrier} · {order.tracking.number}</p>
          </div>
          <div className="relative flex justify-between">
            <div className="absolute left-0 right-0 top-3 h-0.5 bg-line" />
            <div className="absolute left-0 top-3 h-0.5 bg-gold" style={{ width: `${(order.tracking.steps.filter((s) => s.done).length - 1) / (order.tracking.steps.length - 1) * 100}%` }} />
            {order.tracking.steps.map((s, i) => (
              <div key={i} className="relative z-10 flex flex-1 flex-col items-center text-center">
                <div className={cn('flex h-6 w-6 items-center justify-center rounded-full border-2 bg-surface', s.done ? 'border-gold' : 'border-line')}>
                  {s.done && <Check className="h-3 w-3 text-gold" />}
                </div>
                <p className={cn('mt-2 text-[11px]', s.done ? 'font-medium text-ink' : 'text-ink-faint')}>{s.label}</p>
                {s.date && <p className="text-[10px] text-ink-faint">{s.date}</p>}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Items */}
      <div className="mt-8 grid gap-8 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <h2 className="mb-4 font-display text-xl font-medium">Items</h2>
          <div className="rounded-sm border border-line">
            {order.items.map((it, i) => (
              <div key={i} className="flex items-center gap-4 border-b border-line/60 p-4 last:border-0">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={it.image} alt={it.name} className="h-20 w-14 rounded-sm object-cover" />
                <div className="flex-1">
                  <Link href={`/product/${it.slug}`} className="text-sm font-medium hover:text-gold-deep">{it.name}</Link>
                  <p className="text-xs text-ink-soft">{it.color} · {it.size} · Qty {it.qty}</p>
                </div>
                <span className="text-sm font-medium">{formatPrice(it.price * it.qty)}</span>
              </div>
            ))}
          </div>
          <div className="mt-4 flex gap-3">
            <button className="btn-outline">Buy Again</button>
            <button className="btn-outline">Need Help?</button>
          </div>
        </div>

        <div>
          <h2 className="mb-4 font-display text-xl font-medium">Summary</h2>
          <dl className="space-y-2 rounded-sm border border-line bg-surface p-5 text-sm">
            <div className="flex justify-between"><dt className="text-ink-soft">Subtotal</dt><dd>{formatPrice(subtotal)}</dd></div>
            <div className="flex justify-between"><dt className="text-ink-soft">Shipping</dt><dd>Free</dd></div>
            <div className="flex justify-between border-t border-line pt-2 font-medium"><dt>Total</dt><dd className="font-display text-lg">{formatPrice(order.total)}</dd></div>
          </dl>
        </div>
      </div>
    </div>
  )
}
