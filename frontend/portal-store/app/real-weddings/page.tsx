import type { Metadata } from 'next'
import Link from 'next/link'
import { fetchStoreWeddings } from '@/lib/api/marketing-server'

import { SectionHeading } from '@/components/ui/primitives'

/** /real-weddings（PAGE-MKT-S05，layout-keep + data-swap）：E-MKT-04 + ISR；链接 href=/real-weddings/{id}。 */

export const revalidate = 300

export const metadata: Metadata = {
  title: 'Real Weddings',
  description: 'Real outdoor weddings styled in Dreamy gowns and dresses. Shop the looks.'
}

export default async function RealWeddingsPage() {
  const result = await fetchStoreWeddings({ page: 1, pageSize: 12 })
  const weddings = result?.data ?? []

  return (
    <div className="container-luxe py-12">
      <SectionHeading eyebrow="Real love stories" title="Real Dreamy Weddings" description="See how real couples styled their outdoor celebrations — and shop every look." />
      {weddings.length === 0 ? (
        <p className="py-24 text-center text-ink-soft">Real wedding stories are coming soon.</p>
      ) : (
        <div className="mt-12 space-y-16">
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
  )
}
