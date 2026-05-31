'use client'

import { useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { cn } from '@/lib/utils'

function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]">
      <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.76h3.56c2.08-1.92 3.28-4.74 3.28-8.09Z" />
      <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.56-2.76c-.98.66-2.24 1.06-3.72 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84A11 11 0 0 0 12 23Z" />
      <path fill="#FBBC05" d="M5.84 14.1a6.6 6.6 0 0 1 0-4.2V7.06H2.18a11 11 0 0 0 0 9.88l3.66-2.84Z" />
      <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84C6.71 7.3 9.14 5.38 12 5.38Z" />
    </svg>
  )
}

function AppleIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-[18px] w-[18px] fill-ink">
      <path d="M16.36 12.78c-.02-2.3 1.88-3.4 1.96-3.46-1.07-1.56-2.73-1.78-3.32-1.8-1.41-.14-2.76.83-3.47.83-.72 0-1.82-.81-2.99-.79-1.54.02-2.96.9-3.75 2.27-1.6 2.78-.41 6.9 1.15 9.16.76 1.1 1.67 2.34 2.86 2.3 1.15-.05 1.58-.74 2.97-.74 1.38 0 1.77.74 2.98.72 1.23-.02 2.01-1.12 2.76-2.23.87-1.28 1.23-2.52 1.25-2.58-.03-.01-2.4-.92-2.42-3.65ZM14.09 5.8c.63-.77 1.06-1.83.94-2.9-.91.04-2.02.61-2.67 1.37-.58.68-1.09 1.77-.95 2.81 1.02.08 2.05-.52 2.68-1.28Z" />
    </svg>
  )
}

const RESEND_SECONDS = 30

export default function LoginPage() {
  const router = useRouter()
  const [step, setStep] = useState<'email' | 'code'>('email')
  const [email, setEmail] = useState('jane@email.com')
  const [code, setCode] = useState(['', '', '', '', '', ''])
  const [error, setError] = useState('')
  const inputs = useRef<(HTMLInputElement | null)[]>([])

  function sendCode(e: React.FormEvent) {
    e.preventDefault()
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setError('Please enter a valid email address')
      return
    }
    setError('')
    setCode(['', '', '', '', '', ''])
    setStep('code')
    setTimeout(() => inputs.current[0]?.focus(), 50)
  }

  function setDigit(i: number, val: string) {
    const v = val.replace(/\D/g, '').slice(-1)
    const next = [...code]
    next[i] = v
    setCode(next)
    if (v && i < 5) inputs.current[i + 1]?.focus()
  }

  function onKeyDown(i: number, e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Backspace' && !code[i] && i > 0) inputs.current[i - 1]?.focus()
  }

  function onPaste(e: React.ClipboardEvent) {
    const text = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6)
    if (!text) return
    e.preventDefault()
    const next = text.split('')
    while (next.length < 6) next.push('')
    setCode(next)
    inputs.current[Math.min(text.length, 5)]?.focus()
  }

  function verify(e: React.FormEvent) {
    e.preventDefault()
    if (code.some((d) => !d)) {
      setError('Enter all 6 digits')
      return
    }
    router.push('/account')
  }

  return (
    <div className="grid min-h-[80vh] lg:grid-cols-2">
      {/* Visual */}
      <div className="relative hidden lg:block">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/competitor-refs/davidsbridal/wedding-dress-04.jpg" alt="Outdoor bride" className="h-full w-full object-cover" />
        <div className="absolute inset-0 bg-ink/25" />
        <div className="absolute bottom-12 left-12 text-canvas">
          <p className="font-display text-4xl font-medium leading-tight">Your story<br />starts here</p>
        </div>
      </div>

      {/* Form */}
      <div className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <Link href="/" className="font-display text-2xl font-semibold">Dreamy</Link>

          {step === 'email' && (
            <>
              <h1 className="mt-8 font-display text-3xl font-medium">Sign in or create account</h1>
              <p className="mt-2 text-sm text-ink-soft">Enter your email and we&apos;ll send you a 6-digit code. No password needed.</p>

              {/* Social */}
              <div className="mt-7 space-y-3">
                <button
                  type="button"
                  onClick={() => router.push('/account')}
                  className="flex w-full items-center justify-center gap-3 rounded-sm border border-line bg-surface py-3 text-sm font-medium transition-colors hover:border-ink"
                >
                  <GoogleIcon /> Continue with Google
                </button>
                <button
                  type="button"
                  onClick={() => router.push('/account')}
                  className="flex w-full items-center justify-center gap-3 rounded-sm border border-line bg-surface py-3 text-sm font-medium transition-colors hover:border-ink"
                >
                  <AppleIcon /> Continue with Apple
                </button>
              </div>

              <div className="my-6 flex items-center gap-4">
                <span className="h-px flex-1 bg-line" />
                <span className="text-[11px] uppercase tracking-luxe text-ink-faint">or</span>
                <span className="h-px flex-1 bg-line" />
              </div>

              <form onSubmit={sendCode} className="space-y-4">
                <div>
                  <label htmlFor="email" className="eyebrow mb-1.5 block">Email</label>
                  <input
                    id="email"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                    className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold"
                  />
                </div>
                {error && <p className="text-xs text-blush">{error}</p>}
                <button type="submit" className="btn-primary w-full">Email me a code</button>
              </form>

              <p className="mt-6 text-center text-xs text-ink-faint">
                Apple may hide your email with a private relay address — you can still sign in.
              </p>
            </>
          )}

          {step === 'code' && (
            <>
              <h1 className="mt-8 font-display text-3xl font-medium">Check your email</h1>
              <p className="mt-2 text-sm text-ink-soft">
                We sent a 6-digit code to <span className="font-medium text-ink">{email}</span>.{' '}
                <button onClick={() => { setStep('email'); setError('') }} className="cursor-pointer text-gold-deep underline">Change</button>
              </p>

              <form onSubmit={verify} className="mt-8">
                <label className="eyebrow mb-2 block">Verification code</label>
                <div className="flex gap-2" onPaste={onPaste}>
                  {code.map((d, i) => (
                    <input
                      key={i}
                      ref={(el) => { inputs.current[i] = el }}
                      inputMode="numeric"
                      maxLength={1}
                      value={d}
                      onChange={(e) => setDigit(i, e.target.value)}
                      onKeyDown={(e) => onKeyDown(i, e)}
                      className="h-14 w-full rounded-sm border border-line bg-surface text-center font-display text-2xl outline-none focus:border-gold"
                    />
                  ))}
                </div>
                {error && <p className="mt-3 text-xs text-blush">{error}</p>}
                <button type="submit" className="btn-primary mt-6 w-full">Verify & continue</button>
              </form>

              <p className="mt-6 text-center text-xs text-ink-faint">
                Didn&apos;t get it? <button className="cursor-pointer text-gold-deep underline">Resend in {RESEND_SECONDS}s</button>
              </p>
            </>
          )}

          <p className={cn('mt-8 text-center text-[11px] leading-relaxed text-ink-faint')}>
            By continuing you agree to Dreamy&apos;s <Link href="/faq" className="underline">Terms</Link> and{' '}
            <Link href="/faq" className="underline">Privacy Policy</Link>.
          </p>
        </div>
      </div>
    </div>
  )
}
