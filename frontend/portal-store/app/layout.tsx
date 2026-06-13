import type { Metadata } from 'next'
import './globals.css'
import { SiteHeader } from '@/components/layout/site-header'
import { SiteFooter } from '@/components/layout/site-footer'
import { NewsletterModal } from '@/components/marketing/newsletter-modal'
import { CookieConsent } from '@/components/marketing/cookie-consent'
import { StoreProvider } from '@/components/store-provider'
import { I18nProvider } from '@/lib/i18n/i18n-context'
import { fetchStoreBanners } from '@/lib/api/marketing-server'
import { BannerPosition } from '@/lib/api/store-types'

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:5173'),
  title: {
    default: 'Dreamy — Outdoor Wedding Atelier',
    template: '%s · Dreamy'
  },
  description:
    'Luxury wedding gowns, bridesmaid & special occasion dresses, and accessories designed for the modern outdoor bride. Bright, airy, effortlessly elegant.',
  keywords: ['wedding dresses', 'outdoor wedding', 'bridesmaid dresses', 'bridal accessories', 'beach wedding gown'],
  openGraph: {
    title: 'Dreamy — Outdoor Wedding Atelier',
    description: 'Luxury gowns & dresses for the modern outdoor bride.',
    type: 'website'
  }
}

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  // PAGE-MKT-S02：layout 级 topbar 公告条（E-MKT-01 position=topbar；空回退静态文案）
  const topbarBanners = await fetchStoreBanners(BannerPosition.TOPBAR)
  const announcements = topbarBanners.map((b) => b.title).filter((t): t is string => !!t)

  return (
    <html lang="en" data-scroll-behavior="smooth">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Cormorant+Garamond:ital,wght@0,400;0,500;0,600;0,700;1,400&family=Jost:wght@300;400;500;600&display=swap"
          rel="stylesheet"
        />
      </head>
      <body>
        <I18nProvider>
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
      </body>
    </html>
  )
}
