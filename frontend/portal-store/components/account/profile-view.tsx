'use client'

/**
 * COMP-S10 <ProfileView>：只读资料展示（PAGE-S02/S03）。
 * 数据来自 authStore.user（getProfile / FUNC-007）。字段：name/email/tier/avatar/phone/joinedAt。
 */

import type { UserProfile } from '@/lib/api/types'
import { useI18n } from '@/lib/i18n/i18n-context'

function initials(name: string | null, email: string | null): string {
  const source = (name ?? email ?? '?').trim()
  const parts = source.split(/\s+/).filter(Boolean)
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase()
  return source.slice(0, 2).toUpperCase()
}

export function ProfileView({ user }: { user: UserProfile }) {
  const { t, locale } = useI18n()
  const joined = (() => {
    if (!user.joinedAt) return null
    const d = new Date(user.joinedAt)
    return Number.isNaN(d.getTime()) ? null : d.toLocaleDateString(locale, { year: 'numeric', month: 'long' })
  })()

  return (
    <div className="rounded-sm border border-line bg-surface p-6">
      <div className="flex items-center gap-4">
        {user.avatar ? (
          /* eslint-disable-next-line @next/next/no-img-element */
          <img src={user.avatar} alt="" className="h-16 w-16 rounded-full object-cover" />
        ) : (
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-muted font-display text-xl font-medium text-ink">
            {initials(user.name, user.email)}
          </span>
        )}
        <div className="min-w-0">
          <p className="font-display text-2xl font-medium">{user.name ?? t.account.notProvided}</p>
          <p className="truncate text-sm text-ink-soft">{user.email ?? t.account.notProvided}</p>
        </div>
        <span className="ml-auto rounded-full bg-ink/85 px-3 py-1 text-[10px] uppercase tracking-luxe text-canvas">
          {user.tier}
        </span>
      </div>

      <dl className="mt-6 grid gap-4 sm:grid-cols-2">
        <Detail term={t.account.name} value={user.name ?? t.account.notProvided} />
        <Detail term={t.account.email} value={user.email ?? t.account.notProvided} />
        <Detail term={t.account.phone} value={user.phone ?? t.account.notProvided} />
        <Detail term={t.account.tier} value={user.tier} />
        {joined && <Detail term={t.account.memberSince} value={joined} />}
      </dl>
    </div>
  )
}

function Detail({ term, value }: { term: string; value: string }) {
  return (
    <div>
      <dt className="eyebrow mb-1">{term}</dt>
      <dd className="text-sm text-ink">{value}</dd>
    </div>
  )
}
