'use client'

/**
 * SiteFooter（COMP-MKT-S09，data-swap）：Newsletter 输入接 E-MKT-11（source=footer）。
 * 成功行内确认文案（sage 信息条风格复用）；email 预校验不通过红框不发请求（FORM-MKT-S01）。
 */

import { useState } from 'react'
import Link from 'next/link'
import { footerNav } from '@/data/navigation'
import { Instagram, Facebook, Twitter } from 'lucide-react'
import { subscribeNewsletter } from '@/lib/api/marketing-api'
import { NewsletterSource } from '@/lib/api/store-types'
import { ApiError } from '@/lib/api/client'
import { useI18n } from '@/lib/i18n/i18n-context'
import { cn } from '@/lib/utils'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function SiteFooter() {
  const { locale, te } = useI18n()
  const [email, setEmail] = useState('')
  const [done, setDone] = useState(false)
  const [invalid, setInvalid] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    const trimmed = email.trim()
    if (!EMAIL_RE.test(trimmed)) {
      setInvalid(true)
      return
    }
    setInvalid(false)
    setSubmitting(true)
    setError(null)
    try {
      await subscribeNewsletter(trimmed, NewsletterSource.FOOTER, locale)
      setDone(true)
    } catch (err) {
      setError(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <footer className="mt-24 bg-ink text-canvas">
      {/* Newsletter */}
      <div className="border-b border-white/10">
        <div className="container-luxe grid gap-8 py-14 lg:grid-cols-2 lg:items-center">
          <div>
            <p className="eyebrow mb-2 text-gold-light">Join the Atelier</p>
            <h3 className="font-display text-3xl font-medium lg:text-4xl">Be the first to know</h3>
            <p className="mt-2 max-w-md text-sm text-canvas/70">Sign up for early access to new collections and outdoor wedding inspiration.</p>
          </div>
          <div className="w-full max-w-md lg:ml-auto">
            {done ? (
              <p className="rounded-sm bg-sage/15 px-4 py-3 text-sm text-sage-deep">You&apos;re on the list — welcome to the atelier.</p>
            ) : (
              <form onSubmit={submit} className="flex w-full gap-3" noValidate>
                <label htmlFor="footer-email" className="sr-only">Email address</label>
                <input
                  id="footer-email"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="Your email address"
                  className={cn('flex-1 rounded-sm border bg-transparent px-4 py-3 text-sm outline-none placeholder:text-canvas/40 focus:border-gold-light', invalid ? 'border-blush' : 'border-white/20')}
                />
                <button type="submit" disabled={submitting} className="cursor-pointer rounded-sm bg-gold px-6 py-3 text-[13px] font-medium uppercase tracking-luxe text-white transition-colors hover:bg-gold-deep disabled:opacity-60">
                  {submitting ? '…' : 'Subscribe'}
                </button>
              </form>
            )}
            {invalid && !done && <p className="mt-2 text-xs text-blush">Please enter a valid email address.</p>}
            {error && !done && <p className="mt-2 text-xs text-blush">{error}</p>}
          </div>
        </div>
      </div>

      {/* Links */}
      <div className="container-luxe grid grid-cols-2 gap-8 py-14 sm:grid-cols-4">
        {footerNav.map((col) => (
          <div key={col.title}>
            <p className="eyebrow mb-4 text-gold-light">{col.title}</p>
            <ul className="space-y-2.5">
              {col.links.map((l) => (
                <li key={l.label}>
                  <Link href={l.href} className="text-sm text-canvas/70 transition-colors hover:text-canvas">{l.label}</Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>

      {/* Bottom bar */}
      <div className="border-t border-white/10">
        <div className="container-luxe flex flex-col items-center justify-between gap-4 py-6 text-xs text-canvas/60 sm:flex-row">
          <div className="flex items-center gap-4">
            <span className="font-display text-lg text-canvas">Dreamy</span>
            <span>© 2026 Dreamy Atelier.</span>
          </div>
          <div className="flex items-center gap-4">
            <span className="hidden sm:inline">We accept</span>
            <div className="flex items-center gap-2">
              {['Visa', 'MC', 'Amex', 'Klarna', 'Afterpay'].map((p) => (
                <span key={p} className="rounded border border-white/20 px-2 py-1 text-[10px] tracking-wide">{p}</span>
              ))}
            </div>
          </div>
          <div className="flex items-center gap-3">
            <a href="#" aria-label="Instagram" className="transition-colors hover:text-canvas"><Instagram className="h-4 w-4" /></a>
            <a href="#" aria-label="Facebook" className="transition-colors hover:text-canvas"><Facebook className="h-4 w-4" /></a>
            <a href="#" aria-label="Twitter" className="transition-colors hover:text-canvas"><Twitter className="h-4 w-4" /></a>
          </div>
        </div>
      </div>
    </footer>
  )
}
