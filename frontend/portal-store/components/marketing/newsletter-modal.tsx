'use client'

/**
 * NewsletterModal（COMP-MKT-S08，data-swap + copy-change）：
 * - 提交接 E-MKT-11（source=modal）。
 * - 折扣码话术（Take 10% off / Reveal My Code）移除 → 纯订阅确认文案（决策 26 显式功能降级）。
 * - 触发（首访双遮罩修复）：与 cookie 条互斥——banner 展示期间（consent 未选择）不装订触发器，
 *   选择完成后（CONSENT_CHOSEN_EVENT / 回访已有 consent）延迟 15s 或滚动 40% 再弹；
 *   sessionStorage（dreamy_newsletter_seen）记忆已关闭，同会话不重弹。
 * - FORM-MKT-S01：email 前端格式预校验；提交防重；成功 sessionStorage 标记不再弹。
 */

import { useState, useEffect, useRef } from 'react'
import { usePathname } from 'next/navigation'
import { X } from 'lucide-react'
import { subscribeNewsletter } from '@/lib/api/marketing-api'
import { NewsletterSource } from '@/lib/api/store-types'
import { ApiError } from '@/lib/api/client'
import { getStoredConsent } from '@/lib/analytics/gtag'
import { CONSENT_CHOSEN_EVENT } from './cookie-consent'
import { useI18n } from '@/lib/i18n/i18n-context'

const SEEN_KEY = 'dreamy_newsletter_seen'
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
/** 延迟触发（ms）与滚动深度阈值（可滚动高度的百分比） */
const SHOW_DELAY_MS = 15_000
const SCROLL_RATIO = 0.4

export function NewsletterModal() {
  const { locale, te } = useI18n()
  const pathname = usePathname()
  // 退订落地页不弹订阅弹窗（用户正在退订，上下文冲突）
  const suppressed = pathname.endsWith('/unsubscribe')
  const [open, setOpen] = useState(false)
  /** cookie 条互斥解除（consent 已选择）后才允许装订触发器 */
  const [armed, setArmed] = useState(false)
  const [email, setEmail] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const sourceRef = useRef<NewsletterSource>(NewsletterSource.MODAL)

  // 互斥：cookie 条展示期间（无持久化 consent）不弹；已有 consent（回访）直接装订
  useEffect(() => {
    if (suppressed) return
    if (sessionStorage.getItem(SEEN_KEY)) return
    if (getStoredConsent() !== null) {
      setArmed(true)
      return
    }
    const onConsentChosen = () => setArmed(true)
    window.addEventListener(CONSENT_CHOSEN_EVENT, onConsentChosen)
    return () => window.removeEventListener(CONSENT_CHOSEN_EVENT, onConsentChosen)
  }, [suppressed])

  // 触发器：15s 延迟 或 滚动超过 40%（先到先得；已关闭不重弹）
  useEffect(() => {
    if (suppressed || !armed) return
    if (sessionStorage.getItem(SEEN_KEY)) return
    let fired = false
    const openModal = () => {
      if (fired || sessionStorage.getItem(SEEN_KEY)) return
      fired = true
      setOpen(true)
    }
    const t = setTimeout(openModal, SHOW_DELAY_MS)
    const onScroll = () => {
      const scrollable = document.documentElement.scrollHeight - window.innerHeight
      if (scrollable > 0 && window.scrollY / scrollable >= SCROLL_RATIO) openModal()
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => {
      clearTimeout(t)
      window.removeEventListener('scroll', onScroll)
    }
  }, [suppressed, armed])

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
        <img src="/photography/newsletter-bride.jpg" alt="Bride in a veil" className="hidden h-full w-full object-cover object-[center_30%] sm:block" />
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
