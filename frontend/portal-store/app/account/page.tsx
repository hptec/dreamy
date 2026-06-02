'use client'

/**
 * PAGE-S02 /account — 账户主页（getProfile / FUNC-007）。
 * UIS-S02：已登录展示用户资料(name/email/tier/avatar)；未登录由 AuthGuard 重定向 /account/login。
 * 资料数据来自 authStore.user（AuthGuard hydrate 已拉取）。
 */

import { useAuthStore } from '@/lib/stores/auth-store'
import { useI18n } from '@/lib/i18n/i18n-context'
import { ProfileView } from '@/components/account/profile-view'

export default function AccountDashboard() {
  const { t } = useI18n()
  const user = useAuthStore((s) => s.user)

  if (!user) {
    return <div className="text-sm text-ink-soft">{t.common.loading}</div>
  }

  return (
    <div>
      <h1 className="font-display text-3xl font-medium">{t.account.dashboardTitle}</h1>
      <p className="mt-1 text-ink-soft">
        {t.account.welcome}{user.name ? `, ${user.name}` : ''}.
      </p>

      <div className="mt-8">
        <ProfileView user={user} />
      </div>
    </div>
  )
}
