'use client'

/**
 * COMP-S01 <LoginCard>：两步登录（step='email'|'code'）。
 * COMP-S02 EmailStep + COMP-S03 OtpStep + COMP-S04 OAuthButtons 组合。
 * FORM-S01 发码（正则预校验→sendOtp→切 code 步+倒计时；429 显剩余秒禁用重发）。
 * FORM-S02 校验（满 6 位自动 verifyOtp；401 剩余次数；410 引导重发；成功存 token 跳 /account）。
 * 错误码本地化（lib/i18n）；OIDC 502/504→提示改 OTP。
 */

import { useCallback, useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useI18n } from '@/lib/i18n/i18n-context'
import { useAuthStore } from '@/lib/stores/auth-store'
import { useAuthConfigStore } from '@/lib/stores/auth-config-store'
import { sendOtp, verifyOtp } from '@/lib/api/auth-api'
import { ApiError } from '@/lib/api/client'
import { errorText, resendSecondsFrom } from '@/lib/i18n/error-text'
import type { UiMessages } from '@/lib/i18n/messages'
import { OtpInput } from './otp-input'
import { OAuthButtons } from './oauth-buttons'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** 登录成功跳转目标：?returnTo=（站内相对路径白名单）缺省 /account */
export function readReturnTo(): string {
  if (typeof window === 'undefined') return '/account'
  try {
    const raw = new URLSearchParams(window.location.search).get('returnTo')
    if (raw && raw.startsWith('/') && !raw.startsWith('//')) return raw
  } catch {
    /* ignore */
  }
  return '/account'
}

export function LoginCard() {
  const router = useRouter()
  const { t, locale } = useI18n()
  const login = useAuthStore((s) => s.login)
  const { config, load } = useAuthConfigStore()

  const [step, setStep] = useState<'email' | 'code'>('email')
  const [email, setEmail] = useState('')
  const [otpLength, setOtpLength] = useState(6)
  const [code, setCode] = useState<string[]>(Array(6).fill(''))
  const [error, setError] = useState('')
  const [sending, setSending] = useState(false)
  const [verifying, setVerifying] = useState(false)
  const [resendIn, setResendIn] = useState(0)

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    if (resendIn <= 0) return
    const timer = setInterval(() => setResendIn((s) => (s > 0 ? s - 1 : 0)), 1000)
    return () => clearInterval(timer)
  }, [resendIn])

  const handleApiError = useCallback(
    (err: unknown) => {
      if (err instanceof ApiError) {
        const secs = resendSecondsFrom(err)
        if (secs != null && secs > 0) setResendIn(secs)
        setError(errorText(err, locale))
      } else {
        setError(errorText(new ApiError(50000, 'unknown', 500), locale))
      }
    },
    [locale]
  )

  const doSend = useCallback(
    async (normalized: string) => {
      setSending(true)
      setError('')
      try {
        const res = await sendOtp(normalized, locale)
        setOtpLength(res.otpLength)
        setCode(Array(res.otpLength).fill(''))
        setStep('code')
        setResendIn(res.resendAfterSeconds)
      } catch (err) {
        handleApiError(err)
      } finally {
        setSending(false)
      }
    },
    [locale, handleApiError]
  )

  function handleSubmitEmail(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    const normalized = email.trim().toLowerCase()
    if (!EMAIL_RE.test(normalized)) {
      setError(errorText(new ApiError(40001, '', 422), locale)) // V-001
      return
    }
    void doSend(normalized)
  }

  const doVerify = useCallback(async () => {
    if (verifying) return
    const joined = code.join('')
    if (joined.length !== otpLength || code.some((d) => !d)) {
      setError(t.login.enterAllDigits)
      return
    }
    setVerifying(true)
    setError('')
    try {
      const result = await verifyOtp(email.trim().toLowerCase(), joined)
      login(result) // 存 token + user
      router.push(readReturnTo())
    } catch (err) {
      setCode(Array(otpLength).fill(''))
      handleApiError(err)
    } finally {
      setVerifying(false)
    }
  }, [verifying, code, otpLength, email, login, router, handleApiError, t.login.enterAllDigits])

  function handleResend() {
    if (resendIn > 0) return
    const normalized = email.trim().toLowerCase()
    if (EMAIL_RE.test(normalized)) void doSend(normalized)
  }

  const googleEnabled = config?.googleEnabled ?? false
  const appleEnabled = config?.appleEnabled ?? false

  return (
    <div className="grid min-h-[80vh] lg:grid-cols-2">
      <div className="relative hidden lg:block">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/competitor-refs/davidsbridal/wedding-dress-04.jpg" alt="Outdoor bride" className="h-full w-full object-cover" />
        <div className="absolute inset-0 bg-ink/25" />
        <div className="absolute bottom-12 left-12 text-canvas">
          <p className="font-display text-4xl font-medium leading-tight">Your story<br />starts here</p>
        </div>
      </div>

      <div className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <Link href="/" className="font-display text-2xl font-semibold">{t.brand}</Link>
          {step === 'email' ? (
            <EmailStep
              t={t}
              email={email}
              setEmail={setEmail}
              error={error}
              sending={sending}
              googleEnabled={googleEnabled}
              appleEnabled={appleEnabled}
              onSubmit={handleSubmitEmail}
              onOauthError={(err) => handleApiError(err)}
            />
          ) : (
            <CodeStep
              t={t}
              email={email}
              code={code}
              otpLength={otpLength}
              error={error}
              verifying={verifying}
              resendIn={resendIn}
              onChange={setCode}
              onComplete={doVerify}
              onResend={handleResend}
              onChangeEmail={() => {
                setStep('email')
                setError('')
              }}
              onSubmit={doVerify}
            />
          )}
          <p className="mt-8 text-center text-[11px] leading-relaxed text-ink-faint">
            {t.login.agreePrefix}{' '}
            <Link href="/faq" className="underline">{t.login.terms}</Link> {t.login.agreeAnd}{' '}
            <Link href="/faq" className="underline">{t.login.privacy}</Link>.
          </p>
        </div>
      </div>
    </div>
  )
}

/** COMP-S02 <EmailStep> */
function EmailStep({
  t,
  email,
  setEmail,
  error,
  sending,
  googleEnabled,
  appleEnabled,
  onSubmit,
  onOauthError
}: {
  t: UiMessages
  email: string
  setEmail: (v: string) => void
  error: string
  sending: boolean
  googleEnabled: boolean
  appleEnabled: boolean
  onSubmit: (e: React.FormEvent) => void
  onOauthError: (err: ApiError) => void
}) {
  return (
    <>
      <h1 className="mt-8 font-display text-3xl font-medium">{t.login.signInTitle}</h1>
      <p className="mt-2 text-sm text-ink-soft">{t.login.signInSubtitle}</p>

      <OAuthButtons
        googleEnabled={googleEnabled}
        appleEnabled={appleEnabled}
        onError={onOauthError}
      />

      <form onSubmit={onSubmit} className="space-y-4">
        <div>
          <label htmlFor="email" className="eyebrow mb-1.5 block">{t.login.emailLabel}</label>
          <input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
            className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold"
          />
        </div>
        {error && (
          <p role="alert" aria-live="assertive" className="text-xs text-blush">{error}</p>
        )}
        <button type="submit" disabled={sending} className="btn-primary w-full">
          {sending ? t.common.loading : t.login.emailMeCode}
        </button>
      </form>
    </>
  )
}

/** COMP-S03 <OtpStep> */
function CodeStep({
  t,
  email,
  code,
  otpLength,
  error,
  verifying,
  resendIn,
  onChange,
  onComplete,
  onResend,
  onChangeEmail,
  onSubmit
}: {
  t: UiMessages
  email: string
  code: string[]
  otpLength: number
  error: string
  verifying: boolean
  resendIn: number
  onChange: (next: string[]) => void
  onComplete: () => void
  onResend: () => void
  onChangeEmail: () => void
  onSubmit: () => void
}) {
  return (
    <>
      <h1 className="mt-8 font-display text-3xl font-medium">{t.login.checkEmailTitle}</h1>
      <p className="mt-2 text-sm text-ink-soft">
        {t.login.checkEmailSubtitle} <span className="font-medium text-ink">{email}</span>.{' '}
        <button onClick={onChangeEmail} className="cursor-pointer text-gold-deep underline">{t.login.change}</button>
      </p>

      <form
        onSubmit={(e) => {
          e.preventDefault()
          onSubmit()
        }}
        className="mt-8"
      >
        <label className="eyebrow mb-2 block">{t.login.verificationCode}</label>
        <OtpInput
          length={otpLength}
          value={code}
          onChange={onChange}
          onComplete={onComplete}
          ariaLabelPrefix={t.login.verificationCode}
        />
        {error && (
          <p role="alert" aria-live="assertive" className="mt-3 text-xs text-blush">{error}</p>
        )}
        <button type="submit" disabled={verifying} className="btn-primary mt-6 w-full">
          {verifying ? t.common.loading : t.login.verifyContinue}
        </button>
      </form>

      <p className="mt-6 text-center text-xs text-ink-faint">
        {t.login.didntGetIt}{' '}
        {resendIn > 0 ? (
          <button disabled className="cursor-not-allowed text-ink-faint underline">
            {t.login.resendIn} {resendIn}s
          </button>
        ) : (
          <button onClick={onResend} className="cursor-pointer text-gold-deep underline">
            {t.login.resend}
          </button>
        )}
      </p>
    </>
  )
}
