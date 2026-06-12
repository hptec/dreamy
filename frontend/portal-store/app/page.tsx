import Link from 'next/link'
import { ArrowRight, Truck, Sparkles, Globe, Heart } from 'lucide-react'
import { palette } from '@/data/products'
import { fetchStoreTags } from '@/lib/api/catalog-server'
import { fetchStoreBanners, fetchStoreFlashSales, fetchStoreWeddings } from '@/lib/api/marketing-server'
import { BannerPosition } from '@/lib/api/store-types'
import { RecommendationRail } from '@/components/product/recommendation-rail'
import { FlashSaleRail } from '@/components/marketing/flash-sale-rail'
import { SectionHeading, Eyebrow } from '@/components/ui/primitives'

/**
 * 首页（PAGE-CAT-S04 + PAGE-MKT-S01，layout-keep + data-swap，revalidate=300）：
 * - hero ← E-MKT-01 position=hero 首条（空回退现有静态 hero——冷启动安全）
 * - FlashSaleRail ← E-MKT-09（空 items 整段不渲染）
 * - Shop by Color ← E-CAT-07 色板标签（空回退静态 palette）
 * - New Arrivals / Best Sellers ← E-CAT-03 推荐位（空整段不渲染）
 * - Real Weddings ← E-MKT-04（空整段不渲染）
 */

export const revalidate = 300

const themeCards = [
  { theme: 'Beach', image: '/competitor-refs/kissprom/wedding-beach-short-05.jpg', desc: 'Breezy & barefoot' },
  { theme: 'Garden', image: '/competitor-refs/davidsbridal/bridesmaid-sage-01.jpg', desc: 'Lush & romantic' },
  { theme: 'Vineyard', image: '/competitor-refs/kissprom/prom-champagne-lace-05.jpg', desc: 'Golden hour glow' },
  { theme: 'Forest', image: '/competitor-refs/kissprom/wedding-aline-longsleeve-06.jpg', desc: 'Woodland fairytale' }
]

export default async function HomePage() {
  const [heroBanners, flashSales, tagGroups, weddingsPage] = await Promise.all([
    fetchStoreBanners(BannerPosition.HERO),
    fetchStoreFlashSales(),
    fetchStoreTags(),
    fetchStoreWeddings({ page: 1, pageSize: 3 })
  ])

  const hero = heroBanners[0]
  const colorGroup = tagGroups.find((g) => /color/i.test(g.name))
  const colorTags = colorGroup?.tags ?? []
  const weddings = weddingsPage?.data ?? []

  return (
    <div>
      {/* HERO — editorial split（COMP-MKT-S01：banner.title 空回退静态文案，视觉零改动） */}
      <section className="relative grid min-h-[600px] lg:grid-cols-2">
        <div className="order-2 flex items-center bg-canvas px-6 py-14 lg:order-1 lg:px-16">
          <div className="max-w-md animate-fadeup">
            <Eyebrow>{hero?.subtitle ?? 'The Outdoor Wedding Edit · 2026'}</Eyebrow>
            <h1 className="mt-4 font-display text-5xl font-medium leading-[1.02] text-ink sm:text-6xl lg:text-[4.25rem]">
              {hero?.title ?? 'Dresses made for golden hour'}
            </h1>
            <p className="mt-5 text-ink-soft">
              Effortless gowns, bridesmaid dresses, and accessories designed for beaches, gardens, and everywhere your love story takes you.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link href="/wedding-dresses" className="btn-primary">{hero?.ctaText ?? 'Shop the Collection'}</Link>
              <Link href="/outdoor-weddings" className="btn-outline">Explore Outdoor</Link>
            </div>
            <div className="mt-10 flex items-center gap-6 text-xs uppercase tracking-luxe text-ink-faint">
              <span>Free Worldwide Shipping</span>
              <span className="h-3 w-px bg-line" />
              <span>Pay in 4 with Klarna</span>
            </div>
          </div>
        </div>
        <div className="relative order-1 min-h-[420px] overflow-hidden lg:order-2">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={hero?.imageUrl ?? '/competitor-refs/kissprom/wedding-aline-tulle-01.jpg'}
            alt={hero?.title ?? 'Bride in an A-line tulle gown'}
            className="absolute inset-0 h-full w-full animate-kenburns object-cover object-top"
          />
        </div>
      </section>

      {/* FLASH SALE（E-MKT-09，空不渲染） */}
      <FlashSaleRail sales={flashSales} />

      {/* SHOP BY COLOR — 核心差异化（E-CAT-07 色板标签；空回退静态 palette） */}
      <section className="container-luxe py-16 lg:py-24">
        <SectionHeading eyebrow="Find your palette" title="Shop by Color" description="Start with the shade that sets your scene. Our outdoor wedding palette is curated for every season and setting." />
        <div className="mt-10 grid grid-cols-4 gap-4 sm:grid-cols-8">
          {colorTags.length > 0
            ? colorTags.slice(0, 8).map((t) => (
                <Link key={t.id} href={`/special-occasion?color=${encodeURIComponent(t.name)}`} className="group text-center">
                  {t.cover ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={t.cover} alt={t.name} className="mx-auto aspect-square w-full rounded-full border border-line object-cover shadow-soft transition-transform duration-300 ease-luxe group-hover:scale-105 group-hover:shadow-card" />
                  ) : (
                    <div className="mx-auto flex aspect-square w-full items-center justify-center rounded-full border border-line bg-muted shadow-soft transition-transform duration-300 ease-luxe group-hover:scale-105 group-hover:shadow-card">
                      <span className="px-1 text-[10px] uppercase tracking-luxe text-ink-soft">{t.name}</span>
                    </div>
                  )}
                  <p className="mt-3 text-xs font-medium">{t.name}</p>
                  {typeof t.productCount === 'number' && <p className="text-[11px] text-ink-faint">{t.productCount} styles</p>}
                </Link>
              ))
            : palette.map((c) => (
                <Link key={c.name} href={`/special-occasion?color=${encodeURIComponent(c.name)}`} className="group text-center">
                  <div className="mx-auto aspect-square w-full rounded-full border border-line shadow-soft transition-transform duration-300 ease-luxe group-hover:scale-105 group-hover:shadow-card" style={{ backgroundColor: c.hex }} />
                  <p className="mt-3 text-xs font-medium">{c.name}</p>
                  <p className="text-[11px] text-ink-faint">{c.count} styles</p>
                </Link>
              ))}
        </div>
      </section>

      {/* OUTDOOR THEMES（静态编辑区块） */}
      <section className="bg-muted py-16 lg:py-24">
        <div className="container-luxe">
          <SectionHeading eyebrow="By setting" title="Where will you say I do?" />
          <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {themeCards.map((t) => (
              <Link key={t.theme} href={`/outdoor-weddings`} className="group relative aspect-[3/4] overflow-hidden rounded-sm">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={t.image} alt={t.theme} className="absolute inset-0 h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
                <div className="absolute inset-0 bg-gradient-to-t from-ink/70 via-ink/10 to-transparent" />
                <div className="absolute inset-x-0 bottom-0 p-5 text-canvas">
                  <h3 className="font-display text-2xl font-medium">{t.theme}</h3>
                  <p className="text-xs text-canvas/80">{t.desc}</p>
                  <span className="mt-2 inline-flex items-center gap-1 text-[11px] uppercase tracking-luxe opacity-0 transition-opacity group-hover:opacity-100">Shop now <ArrowRight className="h-3 w-3" /></span>
                </div>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* NEW ARRIVALS（E-CAT-03 block=new_arrivals，空不渲染） */}
      <RecommendationRail
        block="new_arrivals"
        eyebrow="Just landed"
        title="New Arrivals"
        href="/wedding-dresses?sort=newest"
        hrefLabel="Shop all"
      />

      {/* LOOKBOOK / EDITORIAL（静态） */}
      <section className="relative overflow-hidden bg-ink py-20 text-canvas lg:py-28">
        <div className="container-luxe grid items-center gap-12 lg:grid-cols-2">
          <div className="relative">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/competitor-refs/birdygrey/bridesmaid-pink-bryten-02.jpg" alt="Bridesmaids editorial" className="aspect-[4/5] w-full rounded-sm object-cover" />
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/competitor-refs/birdygrey/bridesmaid-pink-bella-01.jpg" alt="Bridesmaid detail" className="absolute -bottom-8 -right-4 hidden aspect-[3/4] w-40 rounded-sm border-4 border-ink object-cover shadow-lift lg:block" />
          </div>
          <div>
            <Eyebrow className="text-gold-light">The Bridesmaid Edit</Eyebrow>
            <h2 className="mt-3 font-display text-4xl font-medium leading-tight lg:text-5xl">Dress your whole party in one palette</h2>
            <p className="mt-4 max-w-md text-canvas/75">Eighteen shades, every size from US 00 to Plus, and a luxe knit your bridesmaids will actually re-wear. Order swatches to find your perfect match before you commit.</p>
            <Link href="/special-occasion" className="mt-8 inline-block btn-gold">Shop Bridesmaids</Link>
          </div>
        </div>
      </section>

      {/* COLOR PALETTE TOOL teaser（静态） */}
      <section className="container-luxe py-16 lg:py-24">
        <div className="grid items-center gap-10 rounded-sm bg-gradient-to-br from-muted to-blush-light/40 p-8 lg:grid-cols-2 lg:p-14">
          <div>
            <Eyebrow>Free Service</Eyebrow>
            <h2 className="mt-3 font-display text-4xl font-medium leading-tight">Build your wedding moodboard</h2>
            <p className="mt-4 max-w-md text-ink-soft">Mix and match gowns, bridesmaid colors, and accessories into one shareable palette. Order fabric swatches to see your colors in person — on us.</p>
            <div className="mt-6 flex gap-3">
              <Link href="/inspiration" className="btn-primary">Start a Moodboard</Link>
              <Link href="/wedding-guides" className="btn-outline">Planning Guides</Link>
            </div>
          </div>
          <div className="flex flex-wrap justify-center gap-3">
            {palette.slice(0, 6).map((c) => (
              <div key={c.name} className="text-center">
                <div className="h-20 w-20 rounded-sm shadow-card" style={{ backgroundColor: c.hex }} />
                <p className="mt-1.5 text-[11px]">{c.name}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* BEST SELLERS preview（E-CAT-03 block=best_sellers，空不渲染） */}
      <RecommendationRail
        block="best_sellers"
        eyebrow="The Bridal Collection"
        title="Gowns for the modern bride"
        href="/wedding-dresses"
        hrefLabel="View all gowns"
      />

      {/* REAL WEDDINGS（E-MKT-04，空不渲染） */}
      {weddings.length > 0 && (
        <section className="bg-muted py-16 lg:py-24">
          <div className="container-luxe">
            <SectionHeading eyebrow="Real love stories" title="Real Dreamy Weddings" description="See how real couples styled their outdoor celebrations — and shop the looks." />
            <div className="mt-10 grid gap-6 lg:grid-cols-3">
              {weddings.map((w) => (
                <Link key={w.id} href={`/real-weddings/${w.id}`} className="group">
                  <div className="aspect-[4/3] overflow-hidden rounded-sm bg-canvas">
                    {w.cover && (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={w.cover} alt={`${w.couple} wedding`} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
                    )}
                  </div>
                  <p className="eyebrow mt-4">{w.theme}{w.location ? ` · ${w.location}` : ''}</p>
                  <h3 className="mt-1 font-display text-2xl font-medium">{w.couple}</h3>
                  {w.title && <p className="mt-1 text-sm text-ink-soft line-clamp-2">{w.title}</p>}
                </Link>
              ))}
            </div>
          </div>
        </section>
      )}

      {/* VALUE PROPS（静态） */}
      <section className="container-luxe py-16">
        <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
          {[
            { icon: Truck, title: 'Free Worldwide Shipping', desc: 'On all orders over $200, delivered to your door.' },
            { icon: Sparkles, title: 'Order Fabric Swatches', desc: 'See your colors in person before you commit.' },
            { icon: Globe, title: 'Custom Sizing', desc: 'Made-to-measure available on every gown.' },
            { icon: Heart, title: 'Pay in 4', desc: 'Interest-free with Klarna & Afterpay.' }
          ].map((v) => (
            <div key={v.title} className="text-center">
              <v.icon className="mx-auto h-7 w-7 text-gold" strokeWidth={1.5} />
              <h3 className="mt-4 text-sm font-medium uppercase tracking-luxe">{v.title}</h3>
              <p className="mt-2 text-sm text-ink-soft">{v.desc}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
