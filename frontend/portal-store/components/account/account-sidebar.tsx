'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { LayoutDashboard, Package, MapPin, Heart, ShieldCheck, Settings, LogOut, PartyPopper, Star } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/lib/stores/auth-store'
import { useI18n } from '@/lib/i18n/i18n-context'

const links = [
  { href: '/account', label: 'Dashboard', icon: LayoutDashboard },
  { href: '/account/orders', label: 'Orders', icon: Package },
  { href: '/account/addresses', label: 'Addresses', icon: MapPin },
  { href: '/account/wishlist', label: 'Wishlist', icon: Heart },
  { href: '/account/reviews', label: 'My Reviews', icon: Star },
  { href: '/showroom', label: 'Showrooms', icon: PartyPopper },
  { href: '/account/security', label: 'Login & Security', icon: ShieldCheck },
  { href: '/account/settings', label: 'Settings', icon: Settings }
]

export function AccountSidebar() {
  const pathname = usePathname()
  const router = useRouter()
  const { t } = useI18n()
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)

  async function handleSignOut() {
    // 登出当前设备：清本地 token 并跳登录。
    // 当前会话由后端会话有效性缓存（TTL 30s）自然过期，前端清 token 即终止本端访问。
    logout()
    router.replace('/account/login')
  }

  return (
    <aside>
      <div className="mb-6 rounded-sm bg-muted p-5">
        <p className="font-display text-xl font-medium">{user?.name ?? t.account.dashboardTitle}</p>
        <p className="truncate text-xs text-ink-soft">{user?.email ?? ''}</p>
      </div>
      <nav className="space-y-1">
        {links.map((l) => {
          const active = pathname === l.href
          return (
            <Link
              key={l.href}
              href={l.href}
              className={cn(
                'flex items-center gap-3 rounded-sm px-4 py-2.5 text-sm transition-colors',
                active ? 'bg-ink text-canvas' : 'text-ink-soft hover:bg-muted'
              )}
            >
              <l.icon className="h-4 w-4" /> {l.label}
            </Link>
          )
        })}
        <button
          type="button"
          onClick={handleSignOut}
          className="flex w-full items-center gap-3 rounded-sm px-4 py-2.5 text-left text-sm text-ink-soft transition-colors hover:bg-muted"
        >
          <LogOut className="h-4 w-4" /> {t.common.signOut}
        </button>
      </nav>
    </aside>
  )
}
