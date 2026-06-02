'use client'

/**
 * COMP-S09 <DeleteAccountDialog>：危险操作二次确认（FORM-S06 / FUNC-027）。
 * 要求键入 DELETE 才启用提交 → deleteAccount → 清 token → 跳首页（软删除 30 天宽限）。
 */

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAuthStore } from '@/lib/stores/auth-store'
import { useI18n } from '@/lib/i18n/i18n-context'
import { deleteAccount } from '@/lib/api/auth-api'
import { ApiError } from '@/lib/api/client'
import { errorText } from '@/lib/i18n/error-text'
import { Dialog } from './dialog'

export function DeleteAccountDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const router = useRouter()
  const { t, locale } = useI18n()
  const logout = useAuthStore((s) => s.logout)
  const [confirmText, setConfirmText] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const canDelete = confirmText.trim() === t.security.deleteAccountConfirmWord

  async function handleDelete() {
    if (!canDelete || submitting) return
    setSubmitting(true)
    setError('')
    try {
      await deleteAccount() // confirm:true 由 api 层固定传递
      logout() // 清 token
      router.replace('/')
    } catch (err) {
      if (err instanceof ApiError) setError(errorText(err, locale))
      else setError(errorText(new ApiError(50000, 'unknown', 500), locale))
      setSubmitting(false)
    }
  }

  function handleClose() {
    setConfirmText('')
    setError('')
    onClose()
  }

  return (
    <Dialog open={open} title={t.security.deleteAccountTitle} onClose={handleClose} danger>
      <p className="text-sm text-ink-soft">{t.security.deleteAccountWarning}</p>
      <label htmlFor="delete-confirm" className="eyebrow mb-1.5 mt-5 block">
        {t.security.deleteAccountConfirmLabel}
      </label>
      <input
        id="delete-confirm"
        type="text"
        value={confirmText}
        onChange={(e) => setConfirmText(e.target.value)}
        autoComplete="off"
        className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-blush"
      />
      {error && (
        <p role="alert" aria-live="assertive" className="mt-3 text-xs text-blush">{error}</p>
      )}
      <div className="mt-6 flex justify-end gap-3">
        <button
          type="button"
          onClick={handleClose}
          className="rounded-sm border border-line px-4 py-2 text-xs font-medium uppercase tracking-luxe text-ink-soft transition-colors hover:border-ink"
        >
          {t.common.cancel}
        </button>
        <button
          type="button"
          onClick={handleDelete}
          disabled={!canDelete || submitting}
          className="rounded-sm bg-blush px-4 py-2 text-xs font-medium uppercase tracking-luxe text-canvas transition-colors hover:bg-blush/80 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {submitting ? t.common.loading : t.security.deleteAccountSubmit}
        </button>
      </div>
    </Dialog>
  )
}
