'use client'

/**
 * COMP-S06 <IdentityList>：凭证卡片列表（FUNC-010）。
 * provider 图标 / identifier / is_primary 徽章 / verified / relay 失效提示。
 * 绑定/解绑按钮（FORM-S04）：is_primary 前端禁用解绑（EDGE-007 预判）；
 * 解绑后低于 min_methods → 后端 403 40305（错误文案展示）。
 */

import { Check } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useI18n } from '@/lib/i18n/i18n-context'
import type { Identity } from '@/lib/api/types'
import { ProviderMark } from './provider-icons'

interface Props {
  identities: Identity[]
  connectedCount: number
  pendingId: string | null
  onUnbind: (identity: Identity) => void
}

function providerLabel(provider: Identity['provider']): string {
  if (provider === 'google') return 'Google'
  if (provider === 'apple') return 'Apple'
  return 'Email'
}

export function IdentityList({ identities, connectedCount, pendingId, onUnbind }: Props) {
  const { t } = useI18n()

  return (
    <div className="mt-4 space-y-3">
      {identities.map((a) => {
        // EDGE-007 主邮箱禁解绑；EDGE-008 最后一种方式禁解绑（前端预判）
        const locked = a.isPrimary || connectedCount <= 1
        const isPending = pendingId === a.id
        const title = a.isPrimary
          ? t.security.primaryCannotRemove
          : connectedCount <= 1
            ? t.security.keepOneMethod
            : undefined
        return (
          <div key={a.id} className="flex items-center gap-4 rounded-sm border border-line bg-surface p-4">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-sm bg-muted">
              <ProviderMark provider={a.provider} />
            </span>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <p className="text-sm font-medium">{providerLabel(a.provider)}</p>
                {a.isPrimary && (
                  <span className="rounded-full bg-ink/85 px-2 py-0.5 text-[10px] uppercase tracking-luxe text-canvas">
                    {t.security.primary}
                  </span>
                )}
                {a.verified && (
                  <span className="inline-flex items-center gap-0.5 rounded-full bg-sage/15 px-2 py-0.5 text-[10px] font-medium uppercase tracking-luxe text-sage">
                    <Check className="h-3 w-3" /> {t.security.verified}
                  </span>
                )}
              </div>
              <p className="mt-0.5 truncate text-xs text-ink-soft">
                {a.identifier || t.security.notConnected}
                {a.lastLoginAt && (
                  <span className="text-ink-faint"> · {t.security.lastUsed} {formatDate(a.lastLoginAt)}</span>
                )}
              </p>
              {a.provider === 'apple' && a.hiddenEmail && (
                <p className="mt-1 text-[11px] text-ink-faint">
                  {a.relayValid ? t.security.appleRelayNote : t.security.relayInvalid}
                </p>
              )}
            </div>
            <button
              type="button"
              disabled={locked || isPending}
              onClick={() => onUnbind(a)}
              title={title}
              className={cn(
                'shrink-0 rounded-sm border px-4 py-2 text-xs font-medium uppercase tracking-luxe transition-colors',
                locked
                  ? 'cursor-not-allowed border-line text-ink-faint'
                  : 'border-line text-ink hover:border-blush hover:text-blush'
              )}
            >
              {a.isPrimary ? t.security.primary : t.security.disconnect}
            </button>
          </div>
        )
      })}
    </div>
  )
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleDateString()
}
