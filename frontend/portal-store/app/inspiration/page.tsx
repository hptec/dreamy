import type { Metadata } from 'next'
import Link from 'next/link'
import { palette } from '@/data/products'
import { realWeddings } from '@/data/content'
import { SectionHeading, Eyebrow } from '@/components/ui/primitives'

export const metadata: Metadata = {
  title: 'Wedding Inspiration & Lookbook',
  description: 'Outdoor wedding inspiration, lookbooks, and color palettes to bring your vision to life.'
}

const lookbooks = [
  { title: 'Coastal Romance', theme: 'Beach', image: '/competitor-refs/davidsbridal/wedding-dress-04.jpg', count: 12 },
  { title: 'Garden Party', theme: 'Garden', image: '/competitor-refs/davidsbridal/bridesmaid-sage-01.jpg', count: 9 },
  { title: 'Golden Vineyard', theme: 'Vineyard', image: '/competitor-refs/kissprom/prom-champagne-lace-05.jpg', count: 14 },
  { title: 'Boho Bridesmaids', theme: 'Boho', image: '/competitor-refs/birdygrey/bridesmaid-pink-bella-01.jpg', count: 8 },
  { title: 'Forest Fairytale', theme: 'Forest', image: '/competitor-refs/kissprom/wedding-aline-longsleeve-06.jpg', count: 10 },
  { title: 'Prom & Soirée', theme: 'Evening', image: '/competitor-refs/kissprom/prom-champagne-lace-05.jpg', count: 11 }
]

export default function InspirationPage() {
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
        <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {lookbooks.map((l) => (
            <Link key={l.title} href={`/real-weddings`} className="group">
              <div className="relative aspect-[4/5] overflow-hidden rounded-sm">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={l.image} alt={l.title} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
                <div className="absolute inset-0 bg-gradient-to-t from-ink/60 to-transparent" />
                <div className="absolute bottom-5 left-5 text-canvas">
                  <p className="eyebrow text-gold-light">{l.theme}</p>
                  <h3 className="font-display text-2xl font-medium">{l.title}</h3>
                  <p className="text-xs text-canvas/70">{l.count} looks</p>
                </div>
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* Color palette tool */}
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
            <Link href="/special-occasion?occasion=Bridesmaid" className="btn-primary">Shop Bridesmaid Colors</Link>
          </div>
        </div>
      </section>

      {/* Real weddings teaser */}
      <section className="container-luxe py-16">
        <SectionHeading eyebrow="Real love stories" title="Real Dreamy Weddings" />
        <div className="mt-10 grid gap-6 lg:grid-cols-3">
          {realWeddings.map((w) => (
            <Link key={w.id} href={`/real-weddings/${w.slug}`} className="group">
              <div className="aspect-[4/3] overflow-hidden rounded-sm">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={w.cover} alt={w.couple} className="h-full w-full object-cover transition-transform duration-700 ease-luxe group-hover:scale-105" />
              </div>
              <h3 className="mt-3 font-display text-2xl font-medium">{w.couple}</h3>
              <p className="text-sm text-ink-soft">{w.location}</p>
            </Link>
          ))}
        </div>
      </section>
    </div>
  )
}
