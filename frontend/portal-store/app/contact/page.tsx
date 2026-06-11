'use client'

/**
 * /contact（PAGE-MKT-S09 / COMP-MKT-S10 / FORM-MKT-S02）：
 * preventDefault 模拟 → E-MKT-12 受控提交；必填 name/email/message 前端预校验（不通过红框不发请求）；
 * 422704 details.fields 字段红框；5xx → 错误提示 + 重试；成功态「Thank you」面板保持。
 */

import { useState } from 'react'
import { Mail, MessageCircle, MapPin, Phone } from 'lucide-react'
import { submitContactMessage } from '@/lib/api/marketing-api'
import { ApiError } from '@/lib/api/client'
import { useI18n } from '@/lib/i18n/i18n-context'
import { Eyebrow } from '@/components/ui/primitives'
import { cn } from '@/lib/utils'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export default function ContactPage() {
  const { te } = useI18n()
  const [sent, setSent] = useState(false)
  const [form, setForm] = useState({ name: '', email: '', subject: 'Sizing & Fit', message: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, boolean>>({})
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setForm((p) => ({ ...p, [key]: e.target.value }))

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    const errors: Record<string, boolean> = {
      name: !form.name.trim() || form.name.trim().length > 100,
      email: !EMAIL_RE.test(form.email.trim()),
      message: !form.message.trim() || form.message.length > 5000
    }
    setFieldErrors(errors)
    if (Object.values(errors).some(Boolean)) return
    setSubmitting(true)
    setError(null)
    try {
      await submitContactMessage({
        name: form.name.trim(),
        email: form.email.trim(),
        subject: form.subject,
        message: form.message.trim()
      })
      setSent(true)
    } catch (err) {
      if (err instanceof ApiError && err.code === 422704) {
        const fields = (err.details as Record<string, unknown> | null)?.fields
        if (fields && typeof fields === 'object') {
          setFieldErrors(Object.fromEntries(Object.keys(fields).map((k) => [k, true])))
        }
        setError(te(422704))
      } else {
        setError(err instanceof ApiError ? te(err.code) : te(50000))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="container-luxe py-16">
      <div className="grid gap-12 lg:grid-cols-2">
        <div>
          <Eyebrow>Get in touch</Eyebrow>
          <h1 className="mt-2 font-display text-4xl font-medium lg:text-5xl">We&apos;re here to help</h1>
          <p className="mt-4 text-ink-soft">Have a question about sizing, custom orders, or your wedding timeline? Our stylists are happy to help you find the perfect look.</p>
          <div className="mt-8 space-y-4">
            {[
              { icon: Mail, label: 'Email', value: 'hello@dreamy-atelier.com' },
              { icon: Phone, label: 'Phone', value: '+1 (800) 555-DREAM' },
              { icon: MessageCircle, label: 'Live Chat', value: 'Mon–Fri, 9am–6pm PT' },
              { icon: MapPin, label: 'Atelier', value: 'Santa Barbara, California' }
            ].map((c) => (
              <div key={c.label} className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-muted"><c.icon className="h-4 w-4 text-gold" /></div>
                <div><p className="text-xs uppercase tracking-luxe text-ink-soft">{c.label}</p><p className="text-sm font-medium">{c.value}</p></div>
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-sm border border-line bg-surface p-7">
          {sent ? (
            <div className="flex h-full flex-col items-center justify-center py-12 text-center">
              <p className="font-display text-2xl">Thank you!</p>
              <p className="mt-2 text-ink-soft">We&apos;ll be in touch within 24 hours.</p>
            </div>
          ) : (
            <form onSubmit={submit} className="space-y-4" noValidate>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Name" value={form.name} onChange={set('name')} invalid={fieldErrors.name} />
                <Field label="Email" type="email" value={form.email} onChange={set('email')} invalid={fieldErrors.email} />
              </div>
              <div>
                <label htmlFor="subject" className="eyebrow mb-1.5 block">Subject</label>
                <select id="subject" value={form.subject} onChange={set('subject')} className="w-full rounded-sm border border-line bg-canvas px-4 py-3 text-sm outline-none focus:border-gold">
                  <option>Sizing & Fit</option><option>Custom Order</option><option>Order Status</option><option>Wholesale</option><option>Other</option>
                </select>
              </div>
              <div>
                <label htmlFor="message" className="eyebrow mb-1.5 block">Message</label>
                <textarea id="message" rows={5} value={form.message} onChange={set('message')} className={cn('w-full rounded-sm border bg-canvas px-4 py-3 text-sm outline-none focus:border-gold', fieldErrors.message ? 'border-blush' : 'border-line')} />
                {fieldErrors.message && <p className="mt-1 text-xs text-blush">Please enter a message (max 5000 characters).</p>}
              </div>
              {error && <p className="rounded-sm bg-blush/10 px-4 py-3 text-sm text-blush">{error}</p>}
              <button type="submit" disabled={submitting} className="btn-primary w-full disabled:opacity-60">{submitting ? 'Sending…' : 'Send Message'}</button>
            </form>
          )}
        </div>
      </div>
    </div>
  )
}

function Field({ label, type = 'text', value, onChange, invalid }: { label: string; type?: string; value: string; onChange: (e: React.ChangeEvent<HTMLInputElement>) => void; invalid?: boolean }) {
  const id = label.toLowerCase()
  return (
    <div>
      <label htmlFor={id} className="eyebrow mb-1.5 block">{label}</label>
      <input id={id} type={type} value={value} onChange={onChange} className={cn('w-full rounded-sm border bg-canvas px-4 py-3 text-sm outline-none focus:border-gold', invalid ? 'border-blush' : 'border-line')} />
      {invalid && <p className="mt-1 text-xs text-blush">{type === 'email' ? 'Please enter a valid email address.' : `Please enter your ${label.toLowerCase()}.`}</p>}
    </div>
  )
}
