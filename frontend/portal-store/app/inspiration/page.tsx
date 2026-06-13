import type { Metadata } from 'next'
import Link from 'next/link'
import { palette } from '@/data/products'
import { fetchStoreLookbooks, fetchStoreWeddings } from '@/lib/api/marketing-server'
import { LookbookGrid } from '@/components/marketing/lookbook-grid'
import { SectionHeading, Eyebrow } from '@/components/ui/primitives'

/** /inspiration（PAGE-MKT-S07，layout-keep + data-swap）：mock lookbooks → E-MKT-06/07；卡片展开拉关联商品。 */

export const dynamic = 'force-dynamic'

export const metadata: Metadata = {
  title: 'Wedding Inspiration & Lookbook',
  description: 'Outdoor wedding inspiration, lookbooks, and color palettes to bring your vision to life.'
}

export default async function InspirationPage() {
  const [lookbooks, weddingsPage] = await Promise.all([
    fetchStoreLookbooks(),
    fetchStoreWeddings({ page: 1, pageSize: 3 })
  ])
  const weddings = weddingsPage?.data ?? []

  return (
    <div>
      <section className="relative h-[50vh] min-h-[360px] overflow-hidden">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/competitor-refs/birdygrey/bridesmaid-pink-bryten-02.jpg" alt="Inspiration" className="absolute inset-0 h-full w-full object-cover" />
        <div className="absolute inset-0 bg-ink/30" />
        <div className="container-luxe relative flex h-full flex-col items-center justify-center text-center text-canvas">
          <Eyebrow className="text-gold-light">Lookbook</Eyebrow>
          <h1 className="mt-3 font-display text-5xl font-medium lg:text-6xl">Wedding Inspiration</h1>
        </div>
      </section>

      <section className="container-luxe py-16">
        <SectionHeading eyebrow="Curated edits" title="Explore by mood" />
        <div className="mt-10">
          {lookbooks.length === 0 ? (
            <p className="py-16 text-center text-ink-soft">Lookbooks are being curated — check back soon.</p>
          ) : (
            <LookbookGrid lookbooks={lookbooks} />
          )}
        </div>
      </section>

      {/* Color palette tool（静态编辑区块保持） */}
      <section className="bg-muted py-16">
        <div className="container-luxe">
          <SectionHeading eyebrow="Free tool" title="Build your moodboard" description="Order fabric swatches to see your wedding colors in person — on us." />
          <div className="mt-10 flex flex-wrap justify-center gap-4">
            {palette.map((c) => (
              <div key={c.name} className="text-center">
                <div className="h-24 w-24 rounded-sm shadow-card" style={{ backgroundColor: c.hex }} />
                <p className="mt-2 text-sm">{c.name}</p>
              </div>
            ))}
          </div>
          <div className="mt-8 text-center">
            <Link href="/special-occasion" className="btn-primary">Shop Bridesmaid Colors</Link>
          </div>
        </div>
      </section>

      {/* Real weddings teaser（E-MKT-04） */}
      {weddings.length > 0 && (
        <section className="container-luxe py-16">
          <SectionHeading eyebrow="Real love stories" title="Real Dreamy Weddings" />
          <div className="mt-10 grid gap-6 lg:grid-cols-3">
            {weddings.map((w) => (
              <Link key={w.id} href={`/real-weddings/${w.id}`} className="group">
                <div className="aspect-[4/3] overflow-hidden rounded-sm bg-muted">
                  {w.cover && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={w.cover} alt={w.couple} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
                  )}
                </div>
                <h3 className="mt-3 font-display text-2xl font-medium">{w.couple}</h3>
                {w.location && <p className="text-sm text-ink-soft">{w.location}</p>}
              </Link>
            ))}
          </div>
        </section>
      )}
    </div>
  )
}
