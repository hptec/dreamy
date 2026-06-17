import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { SiteHeader } from '@/components/layout/site-header'
import { SiteFooter } from '@/components/layout/site-footer'
import { NewsletterModal } from '@/components/marketing/newsletter-modal'
import { CookieConsent } from '@/components/marketing/cookie-consent'
import { StoreProvider } from '@/components/store-provider'
import { I18nProvider } from '@/lib/i18n/i18n-context'
import { HtmlLangSync } from '@/components/html-lang-sync'
import { fetchStoreBanners } from '@/lib/api/marketing-server'
import { BannerPosition } from '@/lib/api/store-types'
import { buildAlternates } from '@/lib/i18n/seo'
import type { Locale } from '@/lib/api/types'

const SUPPORTED: Locale[] = ['en', 'es', 'fr']

export function generateStaticParams() {
  return SUPPORTED.map((locale) => ({ locale }))
}

/** hreflang/alternates（FUNC-015）：locale 布局根级输出三语 + x-default。 */
export async function generateMetadata({
  params
}: {
  params: Promise<{ locale: string }>
}): Promise<Metadata> {
  const { locale } = await params
  const lang = (SUPPORTED as string[]).includes(locale) ? (locale as Locale) : 'en'
  return {
    keywords: ['wedding dresses', 'outdoor wedding', 'bridesmaid dresses', 'bridal accessories', 'beach wedding gown'],
    alternates: buildAlternates('/', lang),
    openGraph: {
      title: 'Dreamy — Outdoor Wedding Atelier',
      description: 'Luxury gowns & dresses for the modern outdoor bride.',
      type: 'website',
      locale: lang
    }
  }
}

export default async function LocaleLayout({
  children,
  params
}: {
  children: React.ReactNode
  params: Promise<{ locale: string }>
}) {
  const { locale } = await params
  if (!(SUPPORTED as string[]).includes(locale)) notFound()
  const activeLocale = locale as Locale

  // PAGE-MKT-S02：layout 级 topbar 公告条（E-MKT-01 position=topbar；空回退静态文案）
  const topbarBanners = await fetchStoreBanners(BannerPosition.TOPBAR)
  const announcements = topbarBanners.map((b) => b.title).filter((t): t is string => !!t)

  return (
    <I18nProvider initialLocale={activeLocale}>
      <HtmlLangSync locale={activeLocale} />
      <StoreProvider>
        <div className="flex min-h-screen flex-col">
          <SiteHeader announcements={announcements} />
          <main className="flex-1">{children}</main>
          <SiteFooter />
        </div>
        <NewsletterModal />
        <CookieConsent />
      </StoreProvider>
    </I18nProvider>
  )
}
