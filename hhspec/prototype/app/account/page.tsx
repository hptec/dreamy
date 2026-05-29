import Link from 'next/link'
import { ChevronRight } from 'lucide-react'
import { orders } from '@/data/account'
import { formatPrice } from '@/lib/utils'

export const metadata = { title: 'My Account' }

export default function AccountDashboard() {
  const recent = orders.slice(0, 2)
  return (
    <div>
      <h1 className="font-display text-3xl font-medium">Dashboard</h1>
      <p className="mt-1 text-ink-soft">Welcome back, Jane. Here&apos;s what&apos;s happening with your orders.</p>

      <div className="mt-8 grid gap-4 sm:grid-cols-3">
        {[
          { label: 'Total Orders', value: orders.length },
          { label: 'In Production', value: orders.filter((o) => o.status === 'In Production').length },
          { label: 'Wishlist Items', value: 4 }
        ].map((s) => (
          <div key={s.label} className="rounded-sm border border-line bg-surface p-5">
            <p className="font-display text-3xl font-medium">{s.value}</p>
            <p className="text-xs uppercase tracking-luxe text-ink-soft">{s.label}</p>
          </div>
        ))}
      </div>

      <div className="mt-10 flex items-center justify-between">
        <h2 className="font-display text-2xl font-medium">Recent Orders</h2>
        <Link href="/account/orders" className="text-sm text-gold-deep underline">View all</Link>
      </div>
      <div className="mt-4 space-y-3">
        {recent.map((o) => (
          <Link key={o.id} href={`/account/orders/${o.id}`} className="flex items-center gap-4 rounded-sm border border-line bg-surface p-4 transition-colors hover:border-gold">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={o.items[0].image} alt="" className="h-16 w-12 rounded-sm object-cover" />
            <div className="flex-1">
              <p className="text-sm font-medium">Order #{o.id}</p>
              <p className="text-xs text-ink-soft">{o.date} · {o.items.length} item(s)</p>
            </div>
            <span className="rounded-full bg-muted px-3 py-1 text-xs">{o.status}</span>
            <span className="text-sm font-medium">{formatPrice(o.total)}</span>
            <ChevronRight className="h-4 w-4 text-ink-faint" />
          </Link>
        ))}
      </div>
    </div>
  )
}
