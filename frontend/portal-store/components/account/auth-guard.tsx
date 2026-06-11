'use client'

/**
 * <AuthGuard>：受保护账户页守卫（UIS-S02 未登录重定向 /account/login）。
 * 启动时 hydrate（用 refresh 续期 + 拉 profile）；未认证 → router.replace 登录页。
 */

import { useEffect, type ReactNode } from 'react'
import { useRouter, usePathname } from 'next/navigation'
import { useAuthStore } from '@/lib/stores/auth-store'
import { useI18n } from '@/lib/i18n/i18n-context'

export function AuthGuard({ children }: { children: ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const { t } = useI18n()
  const status = useAuthStore((s) => s.status)
  const hydrated = useAuthStore((s) => s.hydrated)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const hydrate = useAuthStore((s) => s.hydrate)

  useEffect(() => {
    void hydrate()
  }, [hydrate])

  useEffect(() => {
    if (hydrated && !isAuthenticated) {
      router.replace(`/account/login?returnTo=${encodeURIComponent(pathname || '/account')}`)
    }
  }, [hydrated, isAuthenticated, router, pathname])

  if (!hydrated || status === 'loading' || status === 'idle') {
    return (
      <div className="flex min-h-[40vh] items-center justify-center text-sm text-ink-soft">
        {t.common.loading}
      </div>
    )
  }

  if (!isAuthenticated) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center text-sm text-ink-soft">
        {t.common.loading}
      </div>
    )
  }

  return <>{children}</>
}
