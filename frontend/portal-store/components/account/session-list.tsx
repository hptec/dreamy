'use client'

/**
 * COMP-S07 <SessionList>：会话卡片列表（FUNC-011/013）。
 * device/browser/location/is_new_device 徽章/current 标记；
 * "登出此设备"（revokeSession）/"登出所有其他设备"（revokeOtherSessions，二次确认）。
 */

import { Laptop, Smartphone, Tablet, LogOut } from 'lucide-react'
import { useI18n } from '@/lib/i18n/i18n-context'
import type { Session } from '@/lib/api/types'

function deviceIcon(device: string | null) {
  const d = device ?? ''
  if (/iphone|pixel|galaxy|phone/i.test(d)) return Smartphone
  if (/ipad|tab/i.test(d)) return Tablet
  return Laptop
}

interface Props {
  sessions: Session[]
  pendingId: string | null
  onRevoke: (session: Session) => void
  onRevokeOthers: () => void
}

export function SessionList({ sessions, pendingId, onRevoke, onRevokeOthers }: Props) {
  const { t } = useI18n()
  const hasOthers = sessions.some((s) => !s.isCurrent)

  return (
    <div>
      {hasOthers && (
        <div className="mb-4 flex justify-end">
          <button
            type="button"
            onClick={onRevokeOthers}
            className="inline-flex items-center gap-2 rounded-sm border border-line px-4 py-2 text-xs font-medium uppercase tracking-luxe text-ink transition-colors hover:border-blush hover:text-blush"
          >
            <LogOut className="h-3.5 w-3.5" /> {t.security.signOutOthers}
          </button>
        </div>
      )}

      <div className="space-y-3">
        {sessions.map((s) => {
          const Icon = deviceIcon(s.device)
          const isPending = pendingId === s.id
          return (
            <div key={s.id} className="flex items-center gap-4 rounded-sm border border-line bg-surface p-4">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-sm bg-muted">
                <Icon className="h-5 w-5 text-ink" />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="text-sm font-medium">{s.device ?? '—'}</p>
                  {s.isCurrent && (
                    <span className="rounded-full bg-sage/15 px-2 py-0.5 text-[10px] font-medium uppercase tracking-luxe text-sage">
                      {t.security.thisDevice}
                    </span>
                  )}
                  {s.isNewDevice && !s.isCurrent && (
                    <span className="rounded-full bg-gold/15 px-2 py-0.5 text-[10px] font-medium uppercase tracking-luxe text-gold-deep">
                      New
                    </span>
                  )}
                </div>
                <p className="mt-0.5 truncate text-xs text-ink-soft">
                  {[s.browser, s.location].filter(Boolean).join(' · ') || '—'}
                </p>
                {s.lastActiveAt && <p className="text-xs text-ink-faint">{formatDateTime(s.lastActiveAt)}</p>}
              </div>
              {!s.isCurrent && (
                <button
                  type="button"
                  disabled={isPending}
                  onClick={() => onRevoke(s)}
                  className="shrink-0 rounded-sm border border-line px-4 py-2 text-xs font-medium uppercase tracking-luxe text-ink transition-colors hover:border-blush hover:text-blush disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {t.security.signOutDevice}
                </button>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

function formatDateTime(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString()
}
