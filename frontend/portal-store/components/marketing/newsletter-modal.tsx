'use client'

/**
 * NewsletterModal（COMP-MKT-S08，data-swap + copy-change）：
 * - 提交接 E-MKT-11（source=modal / exit_intent）。
 * - 折扣码话术（Take 10% off / Reveal My Code）移除 → 纯订阅确认文案（决策 26 显式功能降级）。
 * - 新增 exit-intent 触发（document mouseleave 顶缘；sessionStorage 同 key 防重弹）。
 * - FORM-MKT-S01：email 前端格式预校验；提交防重；成功 sessionStorage 标记不再弹。
 */

import { useState, useEffect, useRef } from 'react'
import { X } from 'lucide-react'
import { subscribeNewsletter } from '@/lib/api/marketing-api'
import { ApiError } from '@/lib/api/client'
import { useI18n } from '@/lib/i18n/i18n-context'

const SEEN_KEY = 'dreamy_newsletter_seen'
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function NewsletterModal() {
  const { locale, te } = useI18n()
  const [open, setOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const sourceRef = useRef<'modal' | 'exit_intent'>('modal')

  useEffect(() => {
    if (sessionStorage.getItem(SEEN_KEY)) return
    const t = setTimeout(() => {
      if (!sessionStorage.getItem(SEEN_KEY)) {
        sourceRef.current = 'modal'
        setOpen(true)
      }
    }, 4000)
    // exit-intent：鼠标驶出视口顶缘（同 key 防重弹）
    const onLeave = (e: MouseEvent) => {
      if (e.clientY > 8) return
      if (sessionStorage.getItem(SEEN_KEY)) return
      sourceRef.current = 'exit_intent'
      setOpen(true)
    }
    document.addEventListener('mouseleave', onLeave)
    return () => {
      clearTimeout(t)
      document.removeEventListener('mouseleave', onLeave)
    }
  }, [])

  const close = () => {
    setOpen(false)
    sessionStorage.setItem(SEEN_KEY, '1')
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    const trimmed = email.trim()
    if (!EMAIL_RE.test(trimmed)) {
      setError('Please enter a valid email address.')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await subscribeNewsletter(trimmed, sourceRef.current, locale)
      setDone(true)
      sessionStorage.setItem(SEEN_KEY, '1')
    } catch (err) {
      setError(err instanceof ApiError ? te(err.code) : te(50000))
    } finally {
      setSubmitting(false)
    }
  }

  if (!open) return null

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-ink/50 backdrop-blur-sm" onClick={close} />
      <div className="relative grid w-full max-w-3xl animate-fadeup overflow-hidden rounded-sm bg-canvas shadow-lift sm:grid-cols-2">
        <button onClick={close} className="absolute right-3 top-3 z-10 cursor-pointer rounded-full bg-canvas/80 p-1.5" aria-label="Close"><X className="h-4 w-4" /></button>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/competitor-refs/davidsbridal/wedding-dress-04.jpg" alt="Outdoor bride" className="hidden h-full w-full object-cover sm:block" />
        <div className="flex flex-col justify-center p-8 sm:p-10">
          <p className="eyebrow mb-2">Welcome to Dreamy</p>
          <h2 className="font-display text-3xl font-medium leading-tight">Join the Dreamy<br />atelier list</h2>
          <p className="mt-3 text-sm text-ink-soft">Be the first to see new collections, outdoor wedding inspiration, and styling tips from our atelier.</p>
          {done ? (
            <p className="mt-6 rounded-sm bg-sage/15 px-4 py-3 text-sm text-sage-deep">You&apos;re on the list! Watch your inbox for new collections and inspiration.</p>
          ) : (
            <form onSubmit={submit} className="mt-6 space-y-3" noValidate>
              <label htmlFor="modal-email" className="sr-only">Email</label>
              <input
                id="modal-email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="Your email address"
                className="w-full rounded-sm border border-line bg-surface px-4 py-3 text-sm outline-none focus:border-gold"
              />
              {error && <p className="text-xs text-blush">{error}</p>}
              <button type="submit" disabled={submitting} className="btn-primary w-full disabled:opacity-60">{submitting ? 'Subscribing…' : 'Subscribe'}</button>
            </form>
          )}
          <button onClick={close} className="mt-3 cursor-pointer text-xs text-ink-faint underline">No thanks</button>
        </div>
      </div>
    </div>
  )
}
