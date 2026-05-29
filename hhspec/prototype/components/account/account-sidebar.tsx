'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { LayoutDashboard, Package, MapPin, Heart, Star, Settings, LogOut } from 'lucide-react'
import { cn } from '@/lib/utils'

const links = [
  { href: '/account', label: 'Dashboard', icon: LayoutDashboard },
  { href: '/account/orders', label: 'Orders', icon: Package },
  { href: '/account/addresses', label: 'Addresses', icon: MapPin },
  { href: '/account/wishlist', label: 'Wishlist', icon: Heart },
  { href: '/account/settings', label: 'Settings & Reviews', icon: Settings }
]

export function AccountSidebar() {
  const pathname = usePathname()
  return (
    <aside>
      <div className="mb-6 rounded-sm bg-muted p-5">
        <p className="font-display text-xl font-medium">Hi, Jane</p>
        <p className="text-xs text-ink-soft">jane@email.com</p>
      </div>
      <nav className="space-y-1">
        {links.map((l) => {
          const active = pathname === l.href
          return (
            <Link key={l.href} href={l.href} className={cn('flex items-center gap-3 rounded-sm px-4 py-2.5 text-sm transition-colors', active ? 'bg-ink text-canvas' : 'text-ink-soft hover:bg-muted')}>
              <l.icon className="h-4 w-4" /> {l.label}
            </Link>
          )
        })}
        <Link href="/account/login" className="flex items-center gap-3 rounded-sm px-4 py-2.5 text-sm text-ink-soft transition-colors hover:bg-muted">
          <LogOut className="h-4 w-4" /> Sign Out
        </Link>
      </nav>
    </aside>
  )
}
