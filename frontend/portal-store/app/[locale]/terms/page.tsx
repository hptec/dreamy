import type { Metadata } from 'next'
import { Eyebrow } from '@/components/ui/primitives'
import { buildAlternates, SUPPORTED_LOCALES } from '@/lib/i18n/seo'
import type { Locale } from '@/lib/api/types'

/** 正文为 EN 法务文本（翻译待法务 review），canonical/hreflang 仍按路由 locale 生成 */
export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }): Promise<Metadata> {
  const { locale } = await params
  const current = (SUPPORTED_LOCALES as string[]).includes(locale) ? (locale as Locale) : 'en'
  return {
    title: 'Terms of Service',
    description: 'Dreamy terms of service — orders, payments, custom sizing, shipping and returns.',
    alternates: buildAlternates('/terms', current)
  }
}

const sections = [
  {
    h: '1. Orders & Payment',
    p: [
      'By placing an order on Dreamy you agree to the prices, taxes, and payment method shown at checkout. Payment is collected through our payment providers (Stripe, Apple Pay, Google Pay, Klarna, Afterpay). Order confirmation emails are sent to the address on your account.',
      'Custom (made-to-measure) orders enter production with your submitted measurements. Please double-check all measurements before confirming a custom order — production starts immediately and measurements cannot be changed once cutting begins.'
    ]
  },
  {
    h: '2. Shipping & Delivery',
    p: [
      'In-stock items ship within 1–2 weeks; custom orders take 4–6 weeks. Estimated delivery dates are provided at checkout but are not guaranteed. Title and risk pass to you upon delivery to the carrier.',
      'International orders are shipped DDU (Delivered Duty Unpaid) — import duties and taxes, where applicable, are collected by the carrier on delivery.'
    ]
  },
  {
    h: '3. Returns & Refunds',
    p: [
      'Ready-to-ship items can be returned within 30 days of delivery in original, unworn condition with tags attached. Custom-made gowns are made to your measurements and are final sale, except in the case of a quality issue.',
      'Refund requests can be submitted from My Orders. Approved refunds are returned to the original payment method.'
    ]
  },
  {
    h: '4. Acceptable Use',
    p: [
      'You agree to use our store lawfully, provide accurate account information, and not to misuse, scrape, or disrupt our services. We may suspend accounts that violate these terms.'
    ]
  },
  {
    h: '5. Contact',
    p: [
      'Questions about these terms? Reach us via the contact page or the email address on your order confirmation.'
    ]
  }
]

export default function TermsPage() {
  return (
    <div className="container-luxe max-w-3xl py-16">
      <div className="text-center">
        <Eyebrow>Legal</Eyebrow>
        <h1 className="mt-2 font-display text-5xl font-medium">Terms of Service</h1>
        <p className="mt-3 text-sm text-ink-faint">Last updated: September 2026</p>
      </div>
      <div className="mt-12 space-y-10">
        {sections.map((s) => (
          <section key={s.h}>
            <h2 className="font-display text-2xl font-medium">{s.h}</h2>
            <div className="mt-3 space-y-3">
              {s.p.map((para, i) => <p key={i} className="text-sm leading-relaxed text-ink-soft">{para}</p>)}
            </div>
          </section>
        ))}
      </div>
    </div>
  )
}
