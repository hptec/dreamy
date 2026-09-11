import type { Metadata } from 'next'
import Link from 'next/link'
import { fetchStoreWeddings } from '@/lib/api/marketing-server'

import { SectionHeading } from '@/components/ui/primitives'
import type { Locale } from '@/lib/api/types'

/** /real-weddings（PAGE-MKT-S05，layout-keep + data-swap）：E-MKT-04；链接 href=/real-weddings/{id}。 */

export const dynamic = 'force-dynamic'

export const metadata: Metadata = {
  title: 'Real Weddings',
  description: 'Real outdoor weddings styled in Dreamy gowns and dresses. Shop the looks.'
}

type PageParams = { locale: string }

export default async function RealWeddingsPage({ params }: { params: Promise<PageParams> }) {
  const { locale } = await params
  const activeLocale = (locale as Locale) ?? 'en'
  const result = await fetchStoreWeddings({ page: 1, pageSize: 12, locale: activeLocale })
  const weddings = result?.data ?? []
  // hero 大片用首个带封面的故事（无封面则回退原 SectionHeading 头）
  const heroWedding = weddings.find((w) => w.cover)

  return (
    <div>
      {heroWedding && (
        <section className="relative flex min-h-[70vh] items-center justify-center overflow-hidden bg-ink text-canvas">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={heroWedding.cover} alt={heroWedding.couple} className="absolute inset-0 h-full w-full object-cover" />
          <div className="absolute inset-0 bg-gradient-to-b from-ink/35 via-ink/45 to-ink/70" aria-hidden="true" />
          <div className="container-luxe relative py-24 text-center">
            <p className="eyebrow mb-3 text-gold-light">Real love stories</p>
            <h1 className="heading-display text-4xl text-canvas sm:text-5xl lg:text-6xl">Real Outdoor Weddings</h1>
            <p className="mx-auto mt-4 max-w-xl text-canvas/85">See how real couples styled their outdoor celebrations — and shop every look.</p>
          </div>
        </section>
      )}

      <div className="container-luxe py-12">
        {!heroWedding && (
          <SectionHeading eyebrow="Real love stories" title="Real Dreamy Weddings" description="See how real couples styled their outdoor celebrations — and shop every look." />
        )}
        {weddings.length === 0 ? (
          <p className="py-24 text-center text-ink-soft">Real wedding stories are coming soon.</p>
        ) : (
          <div className={heroWedding ? 'space-y-16' : 'mt-12 space-y-16'}>
            {weddings.map((w, i) => (
              <Link key={w.id} href={`/real-weddings/${w.id}`} className={`group grid items-center gap-8 lg:grid-cols-2 ${i % 2 ? 'lg:[direction:rtl]' : ''}`}>
                <div className="aspect-[4/3] overflow-hidden rounded-sm bg-muted [direction:ltr]">
                  {w.cover && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={w.cover} alt={w.couple} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
                  )}
                </div>
                <div className="[direction:ltr]">
                  <p className="eyebrow">{[w.theme, w.location, w.weddingDate].filter(Boolean).join(' · ')}</p>
                  <h2 className="mt-2 font-display text-4xl font-medium">{w.couple}</h2>
                  {w.title && <p className="mt-3 text-ink-soft">{w.title}</p>}
                  <span className="mt-5 inline-block text-sm font-medium uppercase tracking-luxe text-gold-deep underline">Read their story →</span>
                </div>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
