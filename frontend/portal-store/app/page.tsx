import Link from 'next/link'
import { ArrowRight, Truck, Sparkles, Globe, Heart } from 'lucide-react'
import { products, palette, getByCategory } from '@/data/products'
import { realWeddings } from '@/data/content'
import { ProductCard } from '@/components/product/product-card'
import { SectionHeading, TextLink, Eyebrow } from '@/components/ui/primitives'

const themeCards = [
  { theme: 'Beach', image: '/competitor-refs/kissprom/wedding-beach-short-05.jpg', desc: 'Breezy & barefoot' },
  { theme: 'Garden', image: '/competitor-refs/davidsbridal/bridesmaid-sage-01.jpg', desc: 'Lush & romantic' },
  { theme: 'Vineyard', image: '/competitor-refs/kissprom/prom-champagne-lace-05.jpg', desc: 'Golden hour glow' },
  { theme: 'Forest', image: '/competitor-refs/kissprom/wedding-aline-longsleeve-06.jpg', desc: 'Woodland fairytale' }
]

export default function HomePage() {
  const newArrivals = products.filter((p) => p.isNew).concat(products.filter((p) => p.isBestSeller)).slice(0, 4)
  const dresses = getByCategory('wedding-dresses').slice(0, 4)

  return (
    <div>
      {/* HERO — editorial split (clean portrait, no baked-in competitor text) */}
      <section className="relative grid min-h-[600px] lg:grid-cols-2">
        <div className="order-2 flex items-center bg-canvas px-6 py-14 lg:order-1 lg:px-16">
          <div className="max-w-md animate-fadeup">
            <Eyebrow>The Outdoor Wedding Edit · 2026</Eyebrow>
            <h1 className="mt-4 font-display text-5xl font-medium leading-[1.02] text-ink sm:text-6xl lg:text-[4.25rem]">
              Dresses made<br />for golden hour
            </h1>
            <p className="mt-5 text-ink-soft">
              Effortless gowns, bridesmaid dresses, and accessories designed for beaches, gardens, and everywhere your love story takes you.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link href="/wedding-dresses" className="btn-primary">Shop the Collection</Link>
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
          <img src="/competitor-refs/kissprom/wedding-aline-tulle-01.jpg" alt="Bride in an A-line tulle gown" className="absolute inset-0 h-full w-full animate-kenburns object-cover object-top" />
        </div>
      </section>

      {/* SHOP BY COLOR — 核心差异化 */}
      <section className="container-luxe py-16 lg:py-24">
        <SectionHeading eyebrow="Find your palette" title="Shop by Color" description="Start with the shade that sets your scene. Our outdoor wedding palette is curated for every season and setting." />
        <div className="mt-10 grid grid-cols-4 gap-4 sm:grid-cols-8">
          {palette.map((c) => (
            <Link key={c.name} href={`/special-occasion?color=${encodeURIComponent(c.name)}`} className="group text-center">
              <div className="mx-auto aspect-square w-full rounded-full border border-line shadow-soft transition-transform duration-300 ease-luxe group-hover:scale-105 group-hover:shadow-card" style={{ backgroundColor: c.hex }} />
              <p className="mt-3 text-xs font-medium">{c.name}</p>
              <p className="text-[11px] text-ink-faint">{c.count} styles</p>
            </Link>
          ))}
        </div>
      </section>

      {/* OUTDOOR THEMES */}
      <section className="bg-muted py-16 lg:py-24">
        <div className="container-luxe">
          <SectionHeading eyebrow="By setting" title="Where will you say I do?" />
          <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {themeCards.map((t) => (
              <Link key={t.theme} href={`/outdoor-weddings?theme=${t.theme}`} className="group relative aspect-[3/4] overflow-hidden rounded-sm">
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

      {/* NEW ARRIVALS */}
      <section className="container-luxe py-16 lg:py-24">
        <div className="flex items-end justify-between">
          <SectionHeading align="left" eyebrow="Just landed" title="New Arrivals" />
          <TextLink href="/wedding-dresses?sort=newest" className="hidden sm:inline-flex">Shop all</TextLink>
        </div>
        <div className="mt-10 grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
          {newArrivals.map((p) => <ProductCard key={p.id} product={p} />)}
        </div>
      </section>

      {/* LOOKBOOK / EDITORIAL */}
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
            <Link href="/special-occasion?occasion=Bridesmaid" className="mt-8 inline-block btn-gold">Shop Bridesmaids</Link>
          </div>
        </div>
      </section>

      {/* COLOR PALETTE TOOL teaser */}
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

      {/* WEDDING DRESSES preview */}
      <section className="container-luxe pb-16 lg:pb-24">
        <div className="flex items-end justify-between">
          <SectionHeading align="left" eyebrow="The Bridal Collection" title="Gowns for the modern bride" />
          <TextLink href="/wedding-dresses" className="hidden sm:inline-flex">View all gowns</TextLink>
        </div>
        <div className="mt-10 grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
          {dresses.map((p) => <ProductCard key={p.id} product={p} />)}
        </div>
      </section>

      {/* REAL WEDDINGS */}
      <section className="bg-muted py-16 lg:py-24">
        <div className="container-luxe">
          <SectionHeading eyebrow="Real love stories" title="Real Dreamy Weddings" description="See how real couples styled their outdoor celebrations — and shop the looks." />
          <div className="mt-10 grid gap-6 lg:grid-cols-3">
            {realWeddings.map((w) => (
              <Link key={w.id} href={`/real-weddings/${w.slug}`} className="group">
                <div className="aspect-[4/3] overflow-hidden rounded-sm">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={w.cover} alt={`${w.couple} wedding`} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
                </div>
                <p className="eyebrow mt-4">{w.theme} · {w.location}</p>
                <h3 className="mt-1 font-display text-2xl font-medium">{w.couple}</h3>
                <p className="mt-1 text-sm text-ink-soft line-clamp-2">{w.excerpt}</p>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* VALUE PROPS */}
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
