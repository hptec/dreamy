import type { Metadata } from 'next'
import './globals.css'

/**
 * 根布局（App Router 要求顶层 app/layout 渲染 html/body）。
 * locale-aware 的 chrome（header/footer/providers）下沉到 app/[locale]/layout.tsx，
 * 其中 HtmlLangSync 客户端组件按当前 locale 同步 <html lang>（决策 11）。
 */

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:5173'),
  title: {
    default: 'Dreamy — Outdoor Wedding Atelier',
    template: '%s · Dreamy'
  },
  description:
    'Luxury wedding gowns, bridesmaid & special occasion dresses, and accessories designed for the modern outdoor bride.',
  icons: { icon: '/icon.svg' }
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
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
      <body>{children}</body>
    </html>
  )
}
