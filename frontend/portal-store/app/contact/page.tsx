'use client'

import { useState } from 'react'
import { Mail, MessageCircle, MapPin, Phone } from 'lucide-react'
import { Eyebrow } from '@/components/ui/primitives'

export default function ContactPage() {
  const [sent, setSent] = useState(false)
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
            <form onSubmit={(e) => { e.preventDefault(); setSent(true) }} className="space-y-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Name" />
                <Field label="Email" type="email" />
              </div>
              <div>
                <label htmlFor="subject" className="eyebrow mb-1.5 block">Subject</label>
                <select id="subject" className="w-full rounded-sm border border-line bg-canvas px-4 py-3 text-sm outline-none focus:border-gold">
                  <option>Sizing & Fit</option><option>Custom Order</option><option>Order Status</option><option>Wholesale</option><option>Other</option>
                </select>
              </div>
              <div>
                <label htmlFor="message" className="eyebrow mb-1.5 block">Message</label>
                <textarea id="message" rows={5} className="w-full rounded-sm border border-line bg-canvas px-4 py-3 text-sm outline-none focus:border-gold" />
              </div>
              <button type="submit" className="btn-primary w-full">Send Message</button>
            </form>
          )}
        </div>
      </div>
    </div>
  )
}

function Field({ label, type = 'text' }: { label: string; type?: string }) {
  const id = label.toLowerCase()
  return (
    <div>
      <label htmlFor={id} className="eyebrow mb-1.5 block">{label}</label>
      <input id={id} type={type} className="w-full rounded-sm border border-line bg-canvas px-4 py-3 text-sm outline-none focus:border-gold" />
    </div>
  )
}
