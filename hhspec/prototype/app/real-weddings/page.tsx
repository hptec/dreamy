import type { Metadata } from 'next'
import Link from 'next/link'
import { realWeddings } from '@/data/content'
import { SectionHeading } from '@/components/ui/primitives'

export const metadata: Metadata = {
  title: 'Real Weddings',
  description: 'Real outdoor weddings styled in Dreamy gowns and dresses. Shop the looks.'
}

export default function RealWeddingsPage() {
  return (
    <div className="container-luxe py-12">
      <SectionHeading eyebrow="Real love stories" title="Real Dreamy Weddings" description="See how real couples styled their outdoor celebrations — and shop every look." />
      <div className="mt-12 space-y-16">
        {realWeddings.map((w, i) => (
          <Link key={w.id} href={`/real-weddings/${w.slug}`} className={`group grid items-center gap-8 lg:grid-cols-2 ${i % 2 ? 'lg:[direction:rtl]' : ''}`}>
            <div className="aspect-[4/3] overflow-hidden rounded-sm [direction:ltr]">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={w.cover} alt={w.couple} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
            </div>
            <div className="[direction:ltr]">
              <p className="eyebrow">{w.theme} · {w.location} · {w.date}</p>
              <h2 className="mt-2 font-display text-4xl font-medium">{w.couple}</h2>
              <p className="mt-3 text-ink-soft">{w.excerpt}</p>
              <span className="mt-5 inline-block text-sm font-medium uppercase tracking-luxe text-gold-deep underline">Read their story →</span>
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}
