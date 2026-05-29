import type { Metadata } from 'next'
import Link from 'next/link'
import { products } from '@/data/products'
import { realWeddings } from '@/data/content'
import { ProductCard } from '@/components/product/product-card'
import { SectionHeading, Eyebrow, TextLink } from '@/components/ui/primitives'

export const metadata: Metadata = {
  title: 'Outdoor Weddings',
  description: 'Beach, garden, boho, forest, and vineyard wedding dresses curated by setting.'
}

const subThemes = [
  { theme: 'Beach', image: '/competitor-refs/kissprom/wedding-beach-short-05.jpg', blurb: 'Light fabrics & barefoot ease for sand and sea.' },
  { theme: 'Garden', image: '/competitor-refs/davidsbridal/bridesmaid-sage-01.jpg', blurb: 'Romantic florals & lush greenery tones.' },
  { theme: 'Boho', image: '/competitor-refs/birdygrey/bridesmaid-pink-bella-01.jpg', blurb: 'Free-spirited silhouettes & earthy palettes.' },
  { theme: 'Forest', image: '/competitor-refs/kissprom/wedding-aline-longsleeve-06.jpg', blurb: 'Woodland fairytale gowns with sleeves & layers.' },
  { theme: 'Vineyard', image: '/competitor-refs/kissprom/prom-champagne-lace-05.jpg', blurb: 'Golden-hour glamour in warm, sun-kissed shades.' }
]

export default function OutdoorWeddingsPage() {
  const featured = products.filter((p) => p.themes && p.themes.length > 0).slice(0, 8)

  return (
    <div>
      {/* Hero */}
      <section className="relative h-[60vh] min-h-[420px] overflow-hidden">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/competitor-refs/davidsbridal/wedding-dress-04.jpg" alt="Outdoor wedding" className="absolute inset-0 h-full w-full object-cover" />
        <div className="absolute inset-0 bg-ink/35" />
        <div className="container-luxe relative flex h-full flex-col items-center justify-center text-center text-canvas">
          <Eyebrow className="text-gold-light">Curated by setting</Eyebrow>
          <h1 className="mt-3 font-display text-5xl font-medium lg:text-6xl">Outdoor Weddings</h1>
          <p className="mt-4 max-w-xl text-canvas/85">From windswept beaches to candlelit vineyards — find the gown made for your view.</p>
        </div>
      </section>

      {/* Sub-themes */}
      <section className="container-luxe py-16 lg:py-20">
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-5">
          {subThemes.map((t) => (
            <Link key={t.theme} href={`/wedding-dresses?theme=${t.theme}`} className="group">
              <div className="aspect-[3/4] overflow-hidden rounded-sm">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={t.image} alt={t.theme} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
              </div>
              <h3 className="mt-3 font-display text-xl font-medium">{t.theme}</h3>
              <p className="mt-1 text-xs text-ink-soft">{t.blurb}</p>
            </Link>
          ))}
        </div>
      </section>

      {/* Featured products */}
      <section className="bg-muted py-16 lg:py-20">
        <div className="container-luxe">
          <div className="flex items-end justify-between">
            <SectionHeading align="left" eyebrow="Shop the edit" title="Made for the outdoors" />
            <TextLink href="/wedding-dresses" className="hidden sm:inline-flex">View all</TextLink>
          </div>
          <div className="mt-10 grid grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
            {featured.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </div>
      </section>

      {/* Real weddings */}
      <section className="container-luxe py-16 lg:py-20">
        <SectionHeading eyebrow="Get inspired" title="Real Outdoor Weddings" />
        <div className="mt-10 grid gap-6 lg:grid-cols-3">
          {realWeddings.map((w) => (
            <Link key={w.id} href={`/real-weddings/${w.slug}`} className="group">
              <div className="aspect-[4/3] overflow-hidden rounded-sm">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={w.cover} alt={w.couple} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
              </div>
              <p className="eyebrow mt-4">{w.theme}</p>
              <h3 className="mt-1 font-display text-2xl font-medium">{w.couple}</h3>
            </Link>
          ))}
        </div>
      </section>
    </div>
  )
}
