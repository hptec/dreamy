'use client'

/**
 * PAGE-S03 /account/settings — 账户设置（getProfile 读 / FUNC-007）。
 * passwordless：无修改密码入口（设计禁止）。含账户注销/删除入口（软删除 30 天宽限，FUNC-027）。
 * 删除走 <DeleteAccountDialog>（COMP-S09，FORM-S06）。
 */

import { useState } from 'react'
import Link from 'next/link'
import { useAuthStore } from '@/lib/stores/auth-store'
import { useI18n } from '@/lib/i18n/i18n-context'
import { DeleteAccountDialog } from '@/components/account/delete-account-dialog'

export default function SettingsPage() {
  const { t } = useI18n()
  const user = useAuthStore((s) => s.user)
  const [deleteOpen, setDeleteOpen] = useState(false)

  if (!user) {
    return <div className="text-sm text-ink-soft">{t.common.loading}</div>
  }

  return (
    <div>
      <h1 className="font-display text-3xl font-medium">{t.settings.title}</h1>

      {/* Profile（只读资料 + email 再验证说明） */}
      <div className="mt-8 max-w-md space-y-4">
        <ReadField label={t.settings.fullName} value={user.name ?? t.account.notProvided} />
        <div>
          <ReadField label={t.settings.emailField} value={user.email ?? t.account.notProvided} />
          <p className="mt-1.5 text-xs text-ink-faint">{t.settings.emailChangeNote}</p>
        </div>
        <ReadField label={t.settings.phone} value={user.phone ?? t.account.notProvided} />

        {/* Passwordless：无修改密码入口 */}
        <div className="rounded-sm border border-line bg-muted/50 p-4">
          <p className="text-sm font-medium">{t.settings.passwordlessTitle}</p>
          <p className="mt-1 text-xs text-ink-soft">
            {t.settings.passwordlessBody}{' '}
            <Link href="/account/security" className="text-gold-deep underline">{t.settings.manageSecurity}</Link>.
          </p>
        </div>
      </div>

      {/* Danger zone：注销/删除账户 */}
      <section className="mt-12 max-w-md">
        <h2 className="eyebrow text-blush">{t.settings.dangerZone}</h2>
        <div className="mt-3 rounded-sm border border-blush/40 bg-surface p-5">
          <p className="text-sm font-medium">{t.settings.deleteAccountTitle}</p>
          <p className="mt-1 text-xs text-ink-soft">{t.settings.deleteAccountBody}</p>
          <button
            type="button"
            onClick={() => setDeleteOpen(true)}
            className="mt-4 rounded-sm border border-blush px-4 py-2 text-xs font-medium uppercase tracking-luxe text-blush transition-colors hover:bg-blush hover:text-canvas"
          >
            {t.settings.deleteAccountCta}
          </button>
        </div>
      </section>

      <DeleteAccountDialog open={deleteOpen} onClose={() => setDeleteOpen(false)} />
    </div>
  )
}

function ReadField({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="eyebrow mb-1.5 block">{label}</p>
      <p className="w-full rounded-sm border border-line bg-muted/40 px-4 py-3 text-sm text-ink">{value}</p>
    </div>
  )
}
