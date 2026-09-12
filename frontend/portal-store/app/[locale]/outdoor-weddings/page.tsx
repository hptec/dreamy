import type { Metadata } from 'next'
import Link from 'next/link'
import { fetchStoreProducts } from '@/lib/api/catalog-server'
import { fetchStoreWeddings } from '@/lib/api/marketing-server'
import { ProductCard } from '@/components/product/product-card'
import { SectionHeading, Eyebrow, TextLink } from '@/components/ui/primitives'
import { EditorialHero } from '@/components/marketing/editorial-hero'

/**
 * PAGE-CAT-S02（outdoor-weddings 聚合页，layout-keep + data-swap）：
 * 编辑区块（hero/sub-themes）静态保持；精选商品 ← E-CAT-01（recommended）；Real Weddings ← E-MKT-04。
 */

export const dynamic = 'force-dynamic'

export const metadata: Metadata = {
  title: 'Outdoor Weddings',
  description: 'Beach, garden, boho, forest, and vineyard wedding dresses curated by setting.'
}

const subThemes = [
  { theme: 'Beach', image: '/photography/tile-beach.jpg', blurb: 'Light fabrics & barefoot ease for sand and sea.' },
  { theme: 'Garden', image: '/photography/tile-garden.jpg', blurb: 'Romantic florals & lush greenery tones.' },
  { theme: 'Boho', image: '/photography/tile-boho.jpg', blurb: 'Free-spirited silhouettes & earthy palettes.' },
  { theme: 'Forest', image: '/photography/tile-forest.jpg', blurb: 'Woodland fairytale gowns with sleeves & layers.' },
  { theme: 'Vineyard', image: '/photography/tile-vineyard.jpg', blurb: 'Golden-hour glamour in warm, sun-kissed shades.' }
]

export default async function OutdoorWeddingsPage() {
  const [featuredPage, weddingsPage] = await Promise.all([
    fetchStoreProducts({ sort: 'recommended', page: 1, pageSize: 8 }),
    fetchStoreWeddings({ page: 1, pageSize: 3 })
  ])
  const featured = featuredPage?.data ?? []
  const weddings = weddingsPage?.data ?? []

  return (
    <div>
      {/* Hero */}
      <EditorialHero
        variant="wide"
        image="/photography/outdoor-hero.jpg"
        alt="Bride on a coastal rock"
        eyebrow="Curated by setting"
        title="Outdoor Weddings"
        description="From windswept beaches to candlelit vineyards — find the gown made for your view."
        objectPosition="center 35%"
      />

      {/* Sub-themes */}
      <section className="container-luxe py-16 lg:py-20">
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-5">
          {subThemes.map((t) => (
            <Link key={t.theme} href={`/wedding-dresses?color=${encodeURIComponent(t.theme)}`} className="group">
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

      {/* Featured products（E-CAT-01） */}
      {featured.length > 0 && (
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
      )}

      {/* Real weddings（E-MKT-04） */}
      {weddings.length > 0 && (
        <section className="container-luxe py-16 lg:py-20">
          <SectionHeading eyebrow="Get inspired" title="Real Outdoor Weddings" />
          <div className="mt-10 grid gap-6 lg:grid-cols-3">
            {weddings.map((w) => (
              <Link key={w.id} href={`/real-weddings/${w.id}`} className="group">
                <div className="aspect-[4/3] overflow-hidden rounded-sm bg-muted">
                  {w.cover && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={w.cover} alt={w.couple} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
                  )}
                </div>
                <p className="eyebrow mt-4">{w.theme}</p>
                <h3 className="mt-1 font-display text-2xl font-medium">{w.couple}</h3>
              </Link>
            ))}
          </div>
        </section>
      )}
    </div>
  )
}
