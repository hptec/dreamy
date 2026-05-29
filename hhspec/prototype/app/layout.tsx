import type { Metadata } from 'next'
import './globals.css'
import { SiteHeader } from '@/components/layout/site-header'
import { SiteFooter } from '@/components/layout/site-footer'
import { NewsletterModal } from '@/components/marketing/newsletter-modal'
import { CookieNotice } from '@/components/marketing/cookie-notice'
import { StoreProvider } from '@/components/store-provider'

export const metadata: Metadata = {
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

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Cormorant+Garamond:ital,wght@0,400;0,500;0,600;0,700;1,400&family=Jost:wght@300;400;500;600&display=swap"
          rel="stylesheet"
        />
      </head>
      <body>
        <StoreProvider>
          <div className="flex min-h-screen flex-col">
            <SiteHeader />
            <main className="flex-1">{children}</main>
            <SiteFooter />
          </div>
          <NewsletterModal />
          <CookieNotice />
        </StoreProvider>
      </body>
    </html>
  )
}
