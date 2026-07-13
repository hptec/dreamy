import Link from 'next/link'
import { cookies } from 'next/headers'
import type { Metadata } from 'next'
import { Eye, X, Truck, Sparkles, Globe, Heart } from 'lucide-react'
import { palette } from '@/data/products'
import { fetchStoreCollections } from '@/lib/api/catalog-server'
import { fetchStoreFlashSales } from '@/lib/api/marketing-server'
import { fetchStoreHome, fetchStoreHomePreview } from '@/lib/api/site-builder-server'
import { RecommendationRail } from '@/components/product/recommendation-rail'
import { FlashSaleRail } from '@/components/marketing/flash-sale-rail'
import { SectionHeading, Eyebrow } from '@/components/ui/primitives'

/**
 * 首页（KD-5 动态渲染版本）：
 * - homeSections 按 sectionType 动态渲染（Hero/ThemeCards/ProductRail/EditorialFeature/Newsletter/Custom）
 * - 保留 FlashSaleRail/ShopByColor/Lookbook/ValueProps 静态区块
 */

export const dynamic = 'force-dynamic'

export async function generateMetadata({
}: {
  params: Promise<{ locale: string }>
}): Promise<Metadata> {
  const previewToken = (await cookies()).get('dreamy_home_preview')?.value
  if (!previewToken) return {}
  return {
    robots: { index: false, follow: false, nocache: true },
    referrer: 'no-referrer',
  }
}

export default async function HomePage({
  params,
}: {
  params: Promise<{ locale: string }>
}) {
  const { locale } = await params
  const activeLocale = (['en', 'es', 'fr'] as const).includes(locale as any) ? locale : 'en'
  const previewToken = (await cookies()).get('dreamy_home_preview')?.value
  const homePagePromise = previewToken
    ? fetchStoreHomePreview(activeLocale, previewToken).then((result) => result.page)
    : fetchStoreHome(activeLocale)

  const [flashSales, collectionGroups, homePage] = await Promise.all([
    fetchStoreFlashSales(),
    fetchStoreCollections(),
    homePagePromise,
  ])

  const colorGroup = collectionGroups.find((g) => /color/i.test(g.name))
  const colorTags = colorGroup?.collections ?? []
  const homeSections = homePage?.sections ?? []

  return (
    <div>
      {homePage?.preview && (
        <div className="sticky top-0 z-50 border-b border-gold-deep/20 bg-gold px-4 py-2 text-white shadow-soft">
          <div className="container-luxe flex items-center justify-between gap-4 text-sm">
            <span className="flex items-center gap-2 font-medium"><Eye className="h-4 w-4" />Private homepage preview</span>
            <a href={`/api/home-preview/exit?locale=${activeLocale}`} className="inline-flex items-center gap-1 text-xs font-medium uppercase tracking-luxe hover:text-canvas">
              <X className="h-4 w-4" />Exit preview
            </a>
          </div>
        </div>
      )}
      {/* 动态区块渲染（site_builder 域控制） */}
      {homeSections.map((section, idx) => {
        const data = section.data || {}
        switch (section.sectionType) {
          case 'hero':
            return (
              <section key={idx} className="relative grid min-h-[600px] lg:grid-cols-2">
                <div className="order-2 flex items-center bg-canvas px-6 py-14 lg:order-1 lg:px-16">
                  <div className="max-w-md animate-fadeup">
                    <Eyebrow>{data.subtitle ?? 'The Outdoor Wedding Edit · 2026'}</Eyebrow>
                    <h1 className="mt-4 font-display text-5xl font-medium leading-[1.02] text-ink sm:text-6xl lg:text-[4.25rem]">
                      {data.title ?? 'Dresses made for golden hour'}
                    </h1>
                    <p className="mt-5 text-ink-soft">
                      Effortless gowns, bridesmaid dresses, and accessories designed for beaches, gardens, and everywhere your love story takes you.
                    </p>
                    <div className="mt-8 flex flex-wrap gap-3">
                      <Link href={data.ctaLink ?? '/wedding-dresses'} className="btn-primary">
                        {data.ctaText ?? 'Shop the Collection'}
                      </Link>
                      {data.ctaLinkSecondary && (
                        <Link href={data.ctaLinkSecondary} className="btn-outline">
                          {data.ctaTextSecondary ?? 'Learn More'}
                        </Link>
                      )}
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
                    src={data.imageUrl ?? '/competitor-refs/kissprom/wedding-aline-tulle-01.jpg'}
                    alt={data.title ?? 'Hero image'}
                    className="absolute inset-0 h-full w-full animate-kenburns object-cover object-top"
                  />
                </div>
              </section>
            )

          case 'theme_cards':
            const themeCards = data.cards || []
            if (themeCards.length === 0) return null
            return (
              <section key={idx} className="container-luxe py-16 lg:py-24">
                <SectionHeading
                  eyebrow={data.eyebrow || 'Explore'}
                  title={data.heading || 'Shop by Theme'}
                  description={data.description}
                />
                <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                  {themeCards.map((card: any) => (
                    <Link
                      key={card.id}
                      href={`/categories/${card.id}`}
                      className="group relative aspect-[3/4] overflow-hidden rounded-sm bg-muted"
                    >
                      <div className="absolute inset-0 bg-gradient-to-t from-ink/70 via-ink/10 to-transparent" />
                      <div className="absolute inset-x-0 bottom-0 p-5 text-canvas">
                        <h3 className="font-display text-2xl font-medium">{card.name}</h3>
                        {card.productCount > 0 && (
                          <p className="text-xs text-canvas/80">{card.productCount} styles</p>
                        )}
                      </div>
                    </Link>
                  ))}
                </div>
              </section>
            )

          case 'product_rail':
            const products = data.products || []
            if (products.length === 0) return null
            return (
              <section key={idx} className="container-luxe py-16 lg:py-24">
                <SectionHeading
                  eyebrow={data.eyebrow}
                  title={data.heading || 'Featured Products'}
                  description={data.description}
                />
                <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
                  {products.map((product: any) => (
                    <Link key={product.id} href={`/products/${product.slug}`} className="group">
                      <div className="aspect-[3/4] overflow-hidden rounded-sm bg-muted">
                        {product.imageUrl && (
                          // eslint-disable-next-line @next/next/no-img-element
                          <img
                            src={product.imageUrl}
                            alt={product.name}
                            className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105"
                          />
                        )}
                      </div>
                      <div className="mt-3">
                        <h3 className="font-medium">{product.name}</h3>
                        <p className="mt-1 text-sm text-ink-soft">${product.price}</p>
                      </div>
                    </Link>
                  ))}
                </div>
              </section>
            )

          case 'editorial_feature':
            const stories = data.stories || []
            if (stories.length === 0) return null
            return (
              <section key={idx} className="bg-muted py-16 lg:py-24">
                <div className="container-luxe">
                  <SectionHeading
                    eyebrow={data.eyebrow || 'Real love stories'}
                    title={data.heading || 'Real Weddings'}
                    description={data.description}
                  />
                  <div className="mt-10 grid gap-6 lg:grid-cols-3">
                    {stories.map((story: any) => (
                      <Link key={story.id} href={`/real-weddings/${story.id}`} className="group">
                        <div className="aspect-[4/3] overflow-hidden rounded-sm bg-canvas">
                          {story.cover && (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img
                              src={story.cover}
                              alt={`${story.couple} wedding`}
                              className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105"
                            />
                          )}
                        </div>
                        <p className="eyebrow mt-4">
                          {story.theme}
                          {story.location ? ` · ${story.location}` : ''}
                        </p>
                        <h3 className="mt-1 font-display text-2xl font-medium">{story.couple}</h3>
                        {story.title && <p className="mt-1 text-sm text-ink-soft line-clamp-2">{story.title}</p>}
                      </Link>
                    ))}
                  </div>
                </div>
              </section>
            )

          case 'newsletter':
            return (
              <section key={idx} className="container-luxe py-16 lg:py-24">
                <div className="mx-auto max-w-2xl text-center">
                  <Eyebrow>{data.eyebrow || 'Stay in touch'}</Eyebrow>
                  <h2 className="mt-3 font-display text-4xl font-medium">
                    {data.heading || 'Join the Dreamy List'}
                  </h2>
                  {data.description && <p className="mt-4 text-ink-soft">{data.description}</p>}
                  <form className="mt-8 flex flex-col gap-3 sm:flex-row">
                    <input
                      type="email"
                      placeholder={data.placeholder || 'Your email'}
                      className="flex-1 rounded-sm border border-line bg-canvas px-4 py-3 text-sm focus:border-gold focus:outline-none"
                    />
                    <button type="submit" className="btn-primary">
                      {data.cta || 'Subscribe'}
                    </button>
                  </form>
                </div>
              </section>
            )

          case 'custom':
            return (
              <section key={idx} className="container-luxe py-16 lg:py-24">
                <div className={data.imageUrl ? 'grid items-center gap-10 lg:grid-cols-2' : 'mx-auto max-w-3xl text-center'}>
                  {data.imageUrl && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={data.imageUrl} alt={data.heading || ''} className="aspect-[16/10] w-full rounded-sm object-cover" />
                  )}
                  <div>
                    {data.heading && <h2 className="font-display text-4xl font-medium">{data.heading}</h2>}
                    {data.subtitle && <p className="mt-3 text-lg text-ink-soft">{data.subtitle}</p>}
                    {data.content && <p className="mt-5 whitespace-pre-line text-ink-soft">{data.content}</p>}
                    {data.ctaText && data.ctaLink && <Link href={data.ctaLink} className="btn-primary mt-7">{data.ctaText}</Link>}
                  </div>
                </div>
              </section>
            )

          default:
            return null
        }
      })}

      {/* 静态区块（保留原有） */}
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
