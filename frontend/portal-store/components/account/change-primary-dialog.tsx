'use client'

/**
 * COMP-S08 <ChangePrimaryDialog>：更换主邮箱（FORM-S05 / FUNC-026 / EDGE-020）。
 * 流程：输入 new_email → 发 OTP（sendOtp）→ 输入验证码 → changePrimaryEmail；
 * 409 占用 → 提示该邮箱已被使用（40901）。
 */

import { useEffect, useState } from 'react'
import { useI18n } from '@/lib/i18n/i18n-context'
import { changePrimaryEmail, sendOtp } from '@/lib/api/auth-api'
import { ApiError } from '@/lib/api/client'
import { errorText, resendSecondsFrom } from '@/lib/i18n/error-text'
import type { Identity } from '@/lib/api/types'
import { Dialog } from './dialog'
import { OtpInput } from './otp-input'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

interface Props {
  open: boolean
  onClose: () => void
  onChanged: (identities: Identity[]) => void
}

export function ChangePrimaryDialog({ open, onClose, onChanged }: Props) {
  const { t, locale } = useI18n()
  const [phase, setPhase] = useState<'email' | 'code'>('email')
  const [newEmail, setNewEmail] = useState('')
  const [otpLength, setOtpLength] = useState(6)
  const [code, setCode] = useState<string[]>(Array(6).fill(''))
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [resendIn, setResendIn] = useState(0)

  useEffect(() => {
    if (resendIn <= 0) return
    const timer = setInterval(() => setResendIn((s) => (s > 0 ? s - 1 : 0)), 1000)
    return () => clearInterval(timer)
  }, [resendIn])

  function reset() {
    setPhase('email')
    setNewEmail('')
    setCode(Array(6).fill(''))
    setError('')
    setResendIn(0)
  }

  function handleClose() {
    reset()
    onClose()
  }

  function showErr(err: unknown) {
    if (err instanceof ApiError) {
      const secs = resendSecondsFrom(err)
      if (secs != null && secs > 0) setResendIn(secs)
      setError(errorText(err, locale))
    } else {
      setError(errorText(new ApiError(50000, 'unknown', 500), locale))
    }
  }

  async function handleSendCode(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    const normalized = newEmail.trim().toLowerCase()
    if (!EMAIL_RE.test(normalized)) {
      setError(errorText(new ApiError(40001, '', 422), locale))
      return
    }
    setBusy(true)
    try {
      const res = await sendOtp(normalized, locale)
      setOtpLength(res.otpLength)
      setCode(Array(res.otpLength).fill(''))
      setResendIn(res.resendAfterSeconds)
      setPhase('code')
    } catch (err) {
      showErr(err)
    } finally {
      setBusy(false)
    }
  }

  async function handleSubmit() {
    if (busy) return
    const joined = code.join('')
    if (joined.length !== otpLength || code.some((d) => !d)) {
      setError(t.login.enterAllDigits)
      return
    }
    setBusy(true)
    setError('')
    try {
      const identities = await changePrimaryEmail(newEmail.trim().toLowerCase(), joined)
      onChanged(identities)
      handleClose()
    } catch (err) {
      setCode(Array(otpLength).fill(''))
      showErr(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} title={t.security.changePrimaryTitle} onClose={handleClose}>
      {phase === 'email' ? (
        <form onSubmit={handleSendCode} className="space-y-4">
          <div>
            <label htmlFor="new-primary-email" className="eyebrow mb-1.5 block">{t.security.newEmailLabel}</label>
            <input
              id="new-primary-email"
              type="email"
              value={newEmail}
              onChange={(e) => setNewEmail(e.target.value)}
              required
              autoComplete="email"
              className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold"
            />
          </div>
          {error && <p role="alert" aria-live="assertive" className="text-xs text-blush">{error}</p>}
          <button type="submit" disabled={busy} className="btn-primary w-full">
            {busy ? t.common.loading : t.security.sendCode}
          </button>
        </form>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault()
            void handleSubmit()
          }}
          className="space-y-4"
        >
          <p className="text-sm text-ink-soft">
            {t.login.checkEmailSubtitle} <span className="font-medium text-ink">{newEmail}</span>.
          </p>
          <div>
            <label className="eyebrow mb-2 block">{t.security.codeLabel}</label>
            <OtpInput
              length={otpLength}
              value={code}
              onChange={setCode}
              onComplete={() => void handleSubmit()}
              ariaLabelPrefix={t.security.codeLabel}
            />
          </div>
          {error && <p role="alert" aria-live="assertive" className="text-xs text-blush">{error}</p>}
          <button type="submit" disabled={busy} className="btn-primary w-full">
            {busy ? t.common.loading : t.security.changePrimarySubmit}
          </button>
        </form>
      )}
    </Dialog>
  )
}
