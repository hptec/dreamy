'use client'

/**
 * COMP-S05 <SecurityPanel>（PAGE-S04）。
 * 组合 IdentityList(COMP-S06)/ChangePrimaryDialog(COMP-S08)。
 * 加载 listIdentities；解绑（FORM-S04）/换主邮箱（FORM-S05）。
 * 错误码本地化（40304/40305/40300/40901 等）。
 */

import { useCallback, useEffect, useState } from 'react'
import { ShieldCheck } from 'lucide-react'
import { useI18n } from '@/lib/i18n/i18n-context'
import type { Identity } from '@/lib/api/types'
import {
  listIdentities,
  unbindIdentity
} from '@/lib/api/auth-api'
import { ApiError } from '@/lib/api/client'
import { errorText } from '@/lib/i18n/error-text'
import { IdentityList } from './identity-list'
import { ChangePrimaryDialog } from './change-primary-dialog'

export function SecurityPanel() {
  const { t, locale } = useI18n()
  const [identities, setIdentities] = useState<Identity[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [pendingIdentity, setPendingIdentity] = useState<number | null>(null)
  const [changeOpen, setChangeOpen] = useState(false)

  const showErr = useCallback(
    (err: unknown) => {
      if (err instanceof ApiError) setError(errorText(err, locale))
      else setError(errorText(new ApiError(50000, 'unknown', 500), locale))
    },
    [locale]
  )

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const ids = await listIdentities()
      setIdentities(ids)
    } catch (err) {
      showErr(err)
    } finally {
      setLoading(false)
    }
  }, [showErr])

  useEffect(() => {
    void load()
  }, [load])

  const connectedCount = identities.length

  async function handleUnbind(identity: Identity) {
    setPendingIdentity(identity.id)
    setError('')
    try {
      await unbindIdentity(identity.id)
      setIdentities((prev) => prev.filter((i) => i.id !== identity.id))
    } catch (err) {
      showErr(err)
    } finally {
      setPendingIdentity(null)
    }
  }

  return (
    <div>
      <h1 className="font-display text-3xl font-medium">{t.security.title}</h1>
      <p className="mt-1 text-ink-soft">{t.security.subtitle}</p>

      {error && (
        <p role="alert" aria-live="assertive" className="mt-4 rounded-sm border border-blush/40 bg-blush/10 px-4 py-2 text-xs text-blush">
          {error}
        </p>
      )}

      {/* Login methods（凭证） */}
      <section className="mt-8">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <ShieldCheck className="h-4 w-4 text-gold-deep" />
            <h2 className="font-display text-xl font-medium">{t.security.loginMethods}</h2>
          </div>
          <button
            type="button"
            onClick={() => setChangeOpen(true)}
            className="rounded-sm border border-line px-4 py-2 text-xs font-medium uppercase tracking-luxe text-ink transition-colors hover:border-gold"
          >
            {t.security.changePrimaryCta}
          </button>
        </div>
        <p className="mt-1 text-sm text-ink-soft">{t.security.loginMethodsHint}</p>

        {loading ? (
          <p className="mt-4 text-sm text-ink-soft">{t.common.loading}</p>
        ) : (
          <IdentityList
            identities={identities}
            connectedCount={connectedCount}
            pendingId={pendingIdentity}
            onUnbind={handleUnbind}
          />
        )}
        <p className="mt-3 text-xs text-ink-faint">{t.security.keepAtLeastOne}</p>
      </section>

      <ChangePrimaryDialog
        open={changeOpen}
        identities={identities}
        onClose={() => setChangeOpen(false)}
        onChanged={(ids) => setIdentities(ids)}
      />
    </div>
  )
}
