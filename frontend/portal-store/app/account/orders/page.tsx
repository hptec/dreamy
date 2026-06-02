'use client'

import { useState } from 'react'
import Link from 'next/link'
import { ChevronRight } from 'lucide-react'
import { orders } from '@/data/account'
import { formatPrice, cn } from '@/lib/utils'

const filters = ['All', 'Processing', 'In Production', 'Shipped', 'Delivered'] as const

export default function OrdersPage() {
  const [filter, setFilter] = useState<(typeof filters)[number]>('All')
  const list = filter === 'All' ? orders : orders.filter((o) => o.status === filter)

  return (
    <div>
      <h1 className="font-display text-3xl font-medium">My Orders</h1>
      <div className="mt-6 flex flex-wrap gap-2">
        {filters.map((f) => (
          <button key={f} onClick={() => setFilter(f)} className={cn('cursor-pointer rounded-full px-4 py-1.5 text-xs uppercase tracking-luxe transition-colors', filter === f ? 'bg-ink text-canvas' : 'border border-line text-ink-soft hover:border-ink')}>{f}</button>
        ))}
      </div>

      <div className="mt-6 space-y-4">
        {list.length === 0 ? (
          <p className="py-16 text-center text-ink-soft">No {filter.toLowerCase()} orders.</p>
        ) : (
          list.map((o) => (
            <div key={o.id} className="rounded-sm border border-line bg-surface p-5">
              <div className="flex items-center justify-between border-b border-line/60 pb-3">
                <div>
                  <p className="text-sm font-medium">Order #{o.id}</p>
                  <p className="text-xs text-ink-soft">Placed {o.date}</p>
                </div>
                <span className={cn('rounded-full px-3 py-1 text-xs', o.status === 'Delivered' ? 'bg-sage/15 text-sage-deep' : o.status === 'Shipped' ? 'bg-gold/15 text-gold-deep' : 'bg-muted text-ink-soft')}>{o.status}</span>
              </div>
              <div className="mt-3 flex items-center gap-4">
                <div className="flex -space-x-3">
                  {o.items.map((it, i) => (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img key={i} src={it.image} alt="" className="h-16 w-12 rounded-sm border-2 border-surface object-cover" />
                  ))}
                </div>
                <div className="flex-1 text-sm text-ink-soft">{o.items.length} item(s)</div>
                <span className="font-medium">{formatPrice(o.total)}</span>
                <Link href={`/account/orders/${o.id}`} className="flex items-center gap-1 text-sm text-gold-deep underline">Details <ChevronRight className="h-3.5 w-3.5" /></Link>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
