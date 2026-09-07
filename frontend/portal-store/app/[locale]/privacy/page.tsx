import type { Metadata } from 'next'
import { Eyebrow } from '@/components/ui/primitives'
import { buildAlternates, SUPPORTED_LOCALES } from '@/lib/i18n/seo'
import type { Locale } from '@/lib/api/types'

/** 正文为 EN 法务文本（翻译待法务 review），canonical/hreflang 仍按路由 locale 生成 */
export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }): Promise<Metadata> {
  const { locale } = await params
  const current = (SUPPORTED_LOCALES as string[]).includes(locale) ? (locale as Locale) : 'en'
  return {
    title: 'Privacy Policy',
    description: 'How Dreamy collects, uses, and protects your personal information.',
    alternates: buildAlternates('/privacy', current)
  }
}

const sections = [
  {
    h: '1. Information We Collect',
    p: [
      'Account information: your name, email address, and language preference. You can sign in with an email one-time code, Google, or Apple — we never store passwords.',
      'Order information: shipping addresses, order contents, and payment status. Card numbers are handled entirely by our payment providers and never reach our servers.',
      'Usage information: anonymous analytics (only after you accept cookies) that help us understand how the store is used.'
    ]
  },
  {
    h: '2. How We Use It',
    p: [
      'To process orders, arrange shipping, and provide customer service; to send transactional emails (order confirmations, shipping updates); and — with your consent — marketing emails you can unsubscribe from at any time.'
    ]
  },
  {
    h: '3. Data Sharing',
    p: [
      'We share personal data only with the service providers needed to run the store: payment processors, shipping carriers, and email delivery. We never sell your personal information.'
    ]
  },
  {
    h: '4. Data Retention & Your Rights',
    p: [
      'Order records are kept as long as required for accounting and warranty purposes. You can request a copy of your data or delete your account from Settings — deletion is permanent after a 30-day grace period.',
      'California residents: we do not sell or share personal information as defined by the CCPA.'
    ]
  },
  {
    h: '5. Cookies',
    p: [
      'We use a strictly necessary cookie to remember your language preference and sign-in session. Analytics cookies are set only after you accept them in the consent banner, and you can change your choice anytime.'
    ]
  },
  {
    h: '6. Contact',
    p: [
      'Privacy questions? Reach us via the contact page or the email address on your order confirmation.'
    ]
  }
]

export default function PrivacyPage() {
  return (
    <div className="container-luxe max-w-3xl py-16">
      <div className="text-center">
        <Eyebrow>Legal</Eyebrow>
        <h1 className="mt-2 font-display text-5xl font-medium">Privacy Policy</h1>
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
